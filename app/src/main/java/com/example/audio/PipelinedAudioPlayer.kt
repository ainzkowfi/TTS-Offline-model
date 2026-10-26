package com.example.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.util.Log
import com.example.data.model.VoiceProfileEntity
import com.example.domain.SentenceChunk
import com.example.engine.TtsSynthesisEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface PlaybackState {
    data object Idle : PlaybackState
    data class Buffering(val sentenceIndex: Int) : PlaybackState
    data class Playing(val sentenceIndex: Int, val audioDurationMs: Int) : PlaybackState
    data class Paused(val sentenceIndex: Int) : PlaybackState
    data object Completed : PlaybackState
    data class Error(val message: String) : PlaybackState
}

sealed interface PreGenState {
    data object Idle : PreGenState
    data class PreGenerating(val sentenceIndex: Int) : PreGenState
    data class PreGenReady(val sentenceIndex: Int) : PreGenState
}

class PipelinedAudioPlayer(
    private val context: Context,
    private val ttsEngine: TtsSynthesisEngine
) {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaPlayer: MediaPlayer? = null

    private var activeJob: Job? = null
    private var preGenJob: Job? = null

    private var sentenceChunks: List<SentenceChunk> = emptyList()
    private var currentVoice: VoiceProfileEntity? = null
    private var currentSpeed: Float = 1.0f

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _preGenState = MutableStateFlow<PreGenState>(PreGenState.Idle)
    val preGenState: StateFlow<PreGenState> = _preGenState.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0)
    val currentPositionMs: StateFlow<Int> = _currentPositionMs.asStateFlow()

    private val _isReplayFromCache = MutableStateFlow(false)
    val isReplayFromCache: StateFlow<Boolean> = _isReplayFromCache.asStateFlow()

    private val _lookaheadCount = MutableStateFlow(2)
    val lookaheadCount: StateFlow<Int> = _lookaheadCount.asStateFlow()

    fun setLookaheadCount(count: Int) {
        _lookaheadCount.value = count.coerceIn(1, 5)
    }

    fun updateContext(
        chunks: List<SentenceChunk>,
        voice: VoiceProfileEntity,
        speed: Float
    ) {
        this.sentenceChunks = chunks
        this.currentVoice = voice
        this.currentSpeed = speed
    }

    fun setSpeed(speed: Float) {
        this.currentSpeed = speed.coerceIn(0.5f, 2.5f)
        mediaPlayer?.let { player ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && player.isPlaying) {
                    val params = player.playbackParams ?: PlaybackParams()
                    player.playbackParams = params.setSpeed(currentSpeed)
                }
            } catch (e: Exception) {
                Log.w("PipelinedPlayer", "Failed to adjust speed on active player", e)
            }
        }
    }

    /**
     * Plays sentence at [index].
     * Implements parallel pipelined generation: as soon as sentence [index] starts playing,
     * it launches a parallel background worker to pre-generate sentence [index + 1].
     */
    fun playSentence(index: Int) {
        if (index !in sentenceChunks.indices) {
            _playbackState.value = PlaybackState.Completed
            return
        }

        activeJob?.cancel()
        activeJob = coroutineScope.launch {
            val chunk = sentenceChunks[index]
            val voice = currentVoice ?: return@launch
            val cacheKey = chunk.computeCacheKey(voice.id, currentSpeed)
            val alreadyCached = ttsEngine.audioCacheManager.isCached(cacheKey)

            _isReplayFromCache.value = alreadyCached
            _playbackState.value = PlaybackState.Buffering(index)

            try {
                // 1. Synthesize current sentence (or instant cache hit)
                val audioFile = ttsEngine.synthesizeSentence(chunk, voice, currentSpeed)

                // 2. Start audio playback
                startPlaybackForFile(audioFile, index)

                // 3. PARALLEL PIPELINE: Immediately pre-generate the next sentence in the background!
                val nextIndex = index + 1
                if (nextIndex < sentenceChunks.size) {
                    launchPreGeneration(nextIndex, voice)
                } else {
                    _preGenState.value = PreGenState.Idle
                }
            } catch (e: CancellationException) {
                // Job was cancelled (e.g. user jumped to another sentence) - exit cleanly
                throw e
            } catch (e: Exception) {
                Log.e("PipelinedPlayer", "Error synthesizing sentence $index", e)
                _playbackState.value = PlaybackState.Error(e.message ?: "Playback error")
            }
        }
    }

    /**
     * Parallel background worker that pre-generates up to [lookaheadCount] upcoming sentences
     * in the background while sentence [index] is playing.
     */
    private fun launchPreGeneration(startIndex: Int, voice: VoiceProfileEntity) {
        preGenJob?.cancel()
        preGenJob = coroutineScope.launch(Dispatchers.Default) {
            val count = _lookaheadCount.value
            for (offset in 0 until count) {
                val targetIdx = startIndex + offset
                if (targetIdx >= sentenceChunks.size) break

                val chunk = sentenceChunks[targetIdx]
                val cacheKey = chunk.computeCacheKey(voice.id, currentSpeed)

                if (!ttsEngine.audioCacheManager.isCached(cacheKey)) {
                    _preGenState.value = PreGenState.PreGenerating(targetIdx)
                    try {
                        ttsEngine.synthesizeSentence(chunk, voice, currentSpeed)
                        _preGenState.value = PreGenState.PreGenReady(targetIdx)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w("PipelinedPlayer", "Pre-generation failed for index $targetIdx", e)
                    }
                } else {
                    _preGenState.value = PreGenState.PreGenReady(targetIdx)
                }
            }
        }
    }

    private fun PreGenReadyState(index: Int): PreGenState = PreGenState.PreGenReady(index)

    private fun startPlaybackForFile(file: File, sentenceIndex: Int) {
        releasePlayer()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()

            // Apply playback speed
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    playbackParams = PlaybackParams().setSpeed(currentSpeed)
                } catch (e: Exception) {
                    Log.w("PipelinedPlayer", "Could not set speed params", e)
                }
            }

            setOnCompletionListener {
                // When Sentence N finishes, transition immediately to Sentence N + 1
                val next = sentenceIndex + 1
                if (next < sentenceChunks.size) {
                    playSentence(next)
                } else {
                    _playbackState.value = PlaybackState.Completed
                }
            }

            setOnErrorListener { _, what, extra ->
                Log.e("PipelinedPlayer", "MediaPlayer error: what=$what, extra=$extra")
                _playbackState.value = PlaybackState.Error("Media error $what")
                true
            }

            start()
            _playbackState.value = PlaybackState.Playing(sentenceIndex, duration)
        }
    }

    fun pause() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                val currentIdx = (playbackState.value as? PlaybackState.Playing)?.sentenceIndex ?: 0
                _playbackState.value = PlaybackState.Paused(currentIdx)
            }
        }
    }

    fun resume() {
        val pausedIdx = (playbackState.value as? PlaybackState.Paused)?.sentenceIndex
        if (pausedIdx != null && mediaPlayer != null) {
            mediaPlayer?.start()
            _playbackState.value = PlaybackState.Playing(pausedIdx, mediaPlayer?.duration ?: 0)
        } else if (pausedIdx != null) {
            playSentence(pausedIdx)
        }
    }

    /**
     * Replays the current sentence instantly from cache.
     */
    fun replayCurrentSentence() {
        val currentIndex = when (val state = playbackState.value) {
            is PlaybackState.Playing -> state.sentenceIndex
            is PlaybackState.Paused -> state.sentenceIndex
            is PlaybackState.Buffering -> state.sentenceIndex
            else -> 0
        }
        playSentence(currentIndex)
    }

    fun previousSentence() {
        val currentIndex = when (val state = playbackState.value) {
            is PlaybackState.Playing -> state.sentenceIndex
            is PlaybackState.Paused -> state.sentenceIndex
            is PlaybackState.Buffering -> state.sentenceIndex
            else -> 0
        }
        val prev = (currentIndex - 1).coerceAtLeast(0)
        playSentence(prev)
    }

    fun nextSentence() {
        val currentIndex = when (val state = playbackState.value) {
            is PlaybackState.Playing -> state.sentenceIndex
            is PlaybackState.Paused -> state.sentenceIndex
            is PlaybackState.Buffering -> state.sentenceIndex
            else -> 0
        }
        val next = (currentIndex + 1).coerceAtMost(sentenceChunks.lastIndex.coerceAtLeast(0))
        playSentence(next)
    }

    fun stop() {
        activeJob?.cancel()
        preGenJob?.cancel()
        releasePlayer()
        _playbackState.value = PlaybackState.Idle
        _preGenState.value = PreGenState.Idle
    }

    fun releasePlayer() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (ignored: Exception) {}
        mediaPlayer = null
    }
}

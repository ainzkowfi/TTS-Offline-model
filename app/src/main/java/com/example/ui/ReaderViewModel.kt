package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioCacheManager
import com.example.audio.PipelinedAudioPlayer
import com.example.audio.PlaybackState
import com.example.audio.PreGenState
import com.example.data.AppDatabase
import com.example.data.model.BookEntity
import com.example.data.model.TtsModelEntity
import com.example.data.model.VoiceProfileEntity
import com.example.domain.SentenceChunk
import com.example.domain.SentenceSegmenter
import com.example.engine.HuggingFaceDownloader
import com.example.engine.TtsSynthesisEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val bookDao = db.bookDao()
    private val modelDao = db.modelDao()
    private val voiceDao = db.voiceDao()

    val audioCacheManager = AudioCacheManager(application)
    val ttsEngine = TtsSynthesisEngine(application, audioCacheManager)
    val player = PipelinedAudioPlayer(application, ttsEngine)
    val downloader = HuggingFaceDownloader(application, modelDao, voiceDao)

    // Books, Models, Voices flows
    val books: StateFlow<List<BookEntity>> = bookDao.getAllBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val models: StateFlow<List<TtsModelEntity>> = modelDao.getAllModels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val voices: StateFlow<List<VoiceProfileEntity>> = voiceDao.getAllVoices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Selection States
    private val _currentBook = MutableStateFlow<BookEntity?>(null)
    val currentBook: StateFlow<BookEntity?> = _currentBook.asStateFlow()

    private val _sentenceChunks = MutableStateFlow<List<SentenceChunk>>(emptyList())
    val sentenceChunks: StateFlow<List<SentenceChunk>> = _sentenceChunks.asStateFlow()

    private val _selectedVoice = MutableStateFlow<VoiceProfileEntity?>(null)
    val selectedVoice: StateFlow<VoiceProfileEntity?> = _selectedVoice.asStateFlow()

    private val _selectedModel = MutableStateFlow<TtsModelEntity?>(null)
    val selectedModel: StateFlow<TtsModelEntity?> = _selectedModel.asStateFlow()

    // Playback and Reader Settings
    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _isDarkMode = MutableStateFlow(true)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _cacheSizeBytes = MutableStateFlow(0L)
    val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes.asStateFlow()

    val lookaheadCount: StateFlow<Int> = player.lookaheadCount
    val playbackState: StateFlow<PlaybackState> = player.playbackState
    val preGenState: StateFlow<PreGenState> = player.preGenState
    val isReplayFromCache: StateFlow<Boolean> = player.isReplayFromCache
    val downloadProgress = downloader.downloadProgress

    data class BatchPreRenderState(
        val isPreRendering: Boolean = false,
        val currentSentence: Int = 0,
        val totalSentences: Int = 0,
        val percent: Int = 0
    )

    private val _batchPreRenderState = MutableStateFlow(BatchPreRenderState())
    val batchPreRenderState: StateFlow<BatchPreRenderState> = _batchPreRenderState.asStateFlow()

    private var batchJob: kotlinx.coroutines.Job? = null

    init {
        // Observe and load initial selected book and voice
        viewModelScope.launch {
            voiceDao.getAllVoices().collect { voiceList ->
                if (_selectedVoice.value == null && voiceList.isNotEmpty()) {
                    val active = voiceList.find { it.isSelected } ?: voiceList.first()
                    _selectedVoice.value = active
                    updatePlayerContext()
                }
            }
        }

        viewModelScope.launch {
            modelDao.getAllModels().collect { modelList ->
                if (_selectedModel.value == null && modelList.isNotEmpty()) {
                    _selectedModel.value = modelList.find { it.isSelected } ?: modelList.first()
                }
            }
        }

        viewModelScope.launch {
            bookDao.getAllBooks().collect { bookList ->
                if (_currentBook.value == null && bookList.isNotEmpty()) {
                    loadBook(bookList.first())
                }
            }
        }

        refreshCacheSize()
    }

    fun loadBook(book: BookEntity) {
        _currentBook.value = book
        val chunks = SentenceSegmenter.segment(book.content)
        _sentenceChunks.value = chunks
        updatePlayerContext()

        // Update total sentences in DB if needed
        if (book.totalSentences != chunks.size) {
            viewModelScope.launch(Dispatchers.IO) {
                bookDao.updateBook(book.copy(totalSentences = chunks.size))
            }
        }
    }

    fun playSentence(index: Int) {
        val book = _currentBook.value ?: return
        val chunks = _sentenceChunks.value
        if (index !in chunks.indices) return

        updatePlayerContext()
        player.playSentence(index)

        // Save reading progress to database
        viewModelScope.launch(Dispatchers.IO) {
            bookDao.updateProgress(book.id, index)
            _currentBook.value = book.copy(currentSentenceIndex = index)
        }
    }

    fun togglePlayPause() {
        when (val state = playbackState.value) {
            is PlaybackState.Playing -> player.pause()
            is PlaybackState.Paused -> player.resume()
            is PlaybackState.Idle, is PlaybackState.Completed -> {
                val resumeIndex = _currentBook.value?.currentSentenceIndex ?: 0
                playSentence(resumeIndex)
            }
            is PlaybackState.Buffering -> player.pause()
            is PlaybackState.Error -> {
                val resumeIndex = _currentBook.value?.currentSentenceIndex ?: 0
                playSentence(resumeIndex)
            }
        }
    }

    fun replayCurrentSentence() {
        updatePlayerContext()
        player.replayCurrentSentence()
    }

    fun nextSentence() {
        val currentIdx = when (val s = playbackState.value) {
            is PlaybackState.Playing -> s.sentenceIndex
            is PlaybackState.Paused -> s.sentenceIndex
            is PlaybackState.Buffering -> s.sentenceIndex
            else -> _currentBook.value?.currentSentenceIndex ?: 0
        }
        val nextIdx = (currentIdx + 1).coerceAtMost(_sentenceChunks.value.lastIndex.coerceAtLeast(0))
        playSentence(nextIdx)
    }

    fun previousSentence() {
        val currentIdx = when (val s = playbackState.value) {
            is PlaybackState.Playing -> s.sentenceIndex
            is PlaybackState.Paused -> s.sentenceIndex
            is PlaybackState.Buffering -> s.sentenceIndex
            else -> _currentBook.value?.currentSentenceIndex ?: 0
        }
        val prevIdx = (currentIdx - 1).coerceAtLeast(0)
        playSentence(prevIdx)
    }

    fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.5f)
        _playbackSpeed.value = clamped
        player.setSpeed(clamped)
        updatePlayerContext()
    }

    fun selectVoice(voice: VoiceProfileEntity) {
        _selectedVoice.value = voice
        viewModelScope.launch(Dispatchers.IO) {
            voiceDao.setSelectedVoice(voice.id)
        }
        updatePlayerContext()
    }

    fun selectModel(model: TtsModelEntity) {
        _selectedModel.value = model
        viewModelScope.launch(Dispatchers.IO) {
            modelDao.setSelectedModel(model.id)
        }
    }

    fun downloadModel(model: TtsModelEntity) {
        viewModelScope.launch {
            downloader.downloadModel(model)
        }
    }

    fun importLocalModel(uri: Uri, displayName: String) {
        viewModelScope.launch {
            downloader.importLocalModel(uri, displayName)
        }
    }

    fun importCustomVoice(
        name: String,
        gender: String,
        accent: String,
        pitch: Float,
        speed: Float,
        energy: Float
    ) {
        viewModelScope.launch {
            downloader.importCustomVoice(name, gender, accent, pitch, speed, energy)
        }
    }

    fun setLookaheadCount(count: Int) {
        player.setLookaheadCount(count)
    }

    fun startBatchPreRender() {
        val chunks = _sentenceChunks.value
        val voice = _selectedVoice.value ?: return
        if (chunks.isEmpty()) return

        batchJob?.cancel()
        batchJob = viewModelScope.launch(Dispatchers.Default) {
            _batchPreRenderState.value = BatchPreRenderState(
                isPreRendering = true,
                currentSentence = 0,
                totalSentences = chunks.size,
                percent = 0
            )

            for ((index, chunk) in chunks.withIndex()) {
                if (!isActive) break
                try {
                    ttsEngine.synthesizeSentence(chunk, voice, _playbackSpeed.value)
                    val percent = ((index + 1) * 100) / chunks.size
                    _batchPreRenderState.value = BatchPreRenderState(
                        isPreRendering = true,
                        currentSentence = index + 1,
                        totalSentences = chunks.size,
                        percent = percent
                    )
                } catch (e: Exception) {
                    android.util.Log.w("ReaderViewModel", "Batch render error at sentence $index", e)
                }
            }
            refreshCacheSize()
            _batchPreRenderState.value = BatchPreRenderState(isPreRendering = false)
        }
    }

    fun cancelBatchPreRender() {
        batchJob?.cancel()
        _batchPreRenderState.value = BatchPreRenderState(isPreRendering = false)
    }

    fun importDocumentFile(uri: Uri, displayName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val parsed = com.example.domain.EbookParser.parseDocument(
                context = getApplication(),
                uri = uri,
                fileName = displayName
            )
            importEbook(
                title = parsed.title,
                author = parsed.author,
                content = parsed.content
            )
        }
    }

    fun importEbook(title: String, author: String, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val normalizedContent = com.example.domain.PunctuationNormalizer.normalize(content)
            val chunks = SentenceSegmenter.segment(normalizedContent)
            val newBook = BookEntity(
                title = title.ifBlank { "Untitled Document" },
                author = author.ifBlank { "Unknown Author" },
                content = normalizedContent,
                totalSentences = chunks.size,
                currentSentenceIndex = 0,
                lastReadAt = System.currentTimeMillis()
            )
            val id = bookDao.insertBook(newBook)
            val inserted = newBook.copy(id = id)
            _currentBook.value = inserted
            _sentenceChunks.value = chunks
            updatePlayerContext()
        }
    }

    fun deleteBook(bookId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            bookDao.deleteBook(bookId)
            if (_currentBook.value?.id == bookId) {
                player.stop()
                val remaining = bookDao.getAllBooks()
                // Will be picked up by flow
            }
        }
    }

    fun toggleDarkMode() {
        _isDarkMode.value = !_isDarkMode.value
    }

    fun clearAudioCache() {
        viewModelScope.launch(Dispatchers.IO) {
            audioCacheManager.clearCache()
            refreshCacheSize()
        }
    }

    fun refreshCacheSize() {
        viewModelScope.launch(Dispatchers.IO) {
            _cacheSizeBytes.value = audioCacheManager.getCacheSizeBytes()
        }
    }

    private fun updatePlayerContext() {
        val voice = _selectedVoice.value ?: return
        player.updateContext(_sentenceChunks.value, voice, _playbackSpeed.value)
    }

    override fun onCleared() {
        super.onCleared()
        player.stop()
        ttsEngine.shutdown()
    }
}

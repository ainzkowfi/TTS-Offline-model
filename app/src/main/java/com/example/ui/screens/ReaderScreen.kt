package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.PlaybackState
import com.example.audio.PreGenState
import com.example.data.model.BookEntity
import com.example.data.model.VoiceProfileEntity
import com.example.domain.SentenceChunk
import com.example.ui.components.AudioWaveform
import com.example.ui.components.PlaybackControlBar
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen

@Composable
fun ReaderScreen(
    currentBook: BookEntity?,
    sentenceChunks: List<SentenceChunk>,
    playbackState: PlaybackState,
    preGenState: PreGenState,
    isReplayFromCache: Boolean,
    currentVoice: VoiceProfileEntity?,
    currentSpeed: Float,
    lookaheadCount: Int = 2,
    batchPreRenderState: com.example.ui.ReaderViewModel.BatchPreRenderState = com.example.ui.ReaderViewModel.BatchPreRenderState(),
    flacExportProgress: com.example.audio.FlacExportProgress = com.example.audio.FlacExportProgress(),
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    onPlaySentence: (Int) -> Unit,
    onTogglePlayPause: () -> Unit,
    onReplayCurrent: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onLookaheadChange: (Int) -> Unit = {},
    onStartBatchPreRender: () -> Unit = {},
    onCancelBatchPreRender: () -> Unit = {},
    onExportFullAudioToFlac: () -> Unit = {},
    onCancelFlacExport: () -> Unit = {},
    onDismissFlacExportDialog: () -> Unit = {},
    onOpenVoiceStudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var readerFontSize by remember { mutableFloatStateOf(17f) }
    val listState = rememberLazyListState()

    val currentActiveIndex = when (playbackState) {
        is PlaybackState.Playing -> playbackState.sentenceIndex
        is PlaybackState.Paused -> playbackState.sentenceIndex
        is PlaybackState.Buffering -> playbackState.sentenceIndex
        else -> currentBook?.currentSentenceIndex ?: 0
    }

    // Auto-scroll to keep active sentence visible
    LaunchedEffect(currentActiveIndex) {
        if (currentActiveIndex in sentenceChunks.indices) {
            listState.animateScrollToItem(
                index = (currentActiveIndex - 1).coerceAtLeast(0)
            )
        }
    }

    val isSynthesizing = playbackState is PlaybackState.Buffering
    val isPreGenerating = preGenState is PreGenState.PreGenerating

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentBook?.title ?: "No Book Loaded",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentBook?.author ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Download Full Audio FLAC Button
                        IconButton(
                            onClick = onExportFullAudioToFlac,
                            modifier = Modifier.size(40.dp).testTag("download_flac_audio_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AudioFile,
                                contentDescription = "Download Full Audio (FLAC)",
                                tint = NeonCyan
                            )
                        }

                        // Pre-render Entire Book Button
                        IconButton(
                            onClick = onStartBatchPreRender,
                            modifier = Modifier.size(40.dp).testTag("pre_render_book_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DownloadForOffline,
                                contentDescription = "Pre-render Entire Book for Offline",
                                tint = if (batchPreRenderState.isPreRendering) NeonCyan else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Font Size Toggle (A- / A+)
                        IconButton(
                            onClick = {
                                readerFontSize = if (readerFontSize >= 21f) 15f else readerFontSize + 2f
                            },
                            modifier = Modifier.size(40.dp).testTag("font_size_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatSize,
                                contentDescription = "Adjust Font Size",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Dark Mode Toggle Button
                        IconButton(
                            onClick = onToggleDarkMode,
                            modifier = Modifier.size(40.dp).testTag("dark_mode_button")
                        ) {
                            Icon(
                                imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle Dark Mode",
                                tint = if (isDarkMode) StatusAmber else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Batch Pre-Rendering Progress Banner
                AnimatedVisibility(
                    visible = batchPreRenderState.isPreRendering,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Pre-rendering Book: ${batchPreRenderState.currentSentence}/${batchPreRenderState.totalSentences} sentences (${batchPreRenderState.percent}%)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            IconButton(
                                onClick = onCancelBatchPreRender,
                                modifier = Modifier.size(24.dp).testTag("cancel_batch_prerender")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cancel,
                                    contentDescription = "Cancel Pre-rendering",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { batchPreRenderState.percent / 100f },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                            color = NeonCyan,
                            trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                        )
                    }
                }

                // Ongoing Single-Sentence Generation Progress Bar Banner
                AnimatedVisibility(
                    visible = (isSynthesizing || isPreGenerating) && !batchPreRenderState.isPreRendering,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isSynthesizing) {
                                    "Synthesizing sentence #${currentActiveIndex + 1} • Kokoro 24kHz"
                                } else {
                                    val nextIdx = (preGenState as? PreGenState.PreGenerating)?.sentenceIndex ?: (currentActiveIndex + 1)
                                    "Parallel Pre-generating sentence #${nextIdx + 1}"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (isSynthesizing) NeonCyan else StatusAmber,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            Text(
                                text = "Lookahead Buffer: $lookaheadCount",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(3.dp),
                            color = if (isSynthesizing) NeonCyan else StatusAmber,
                            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        },
        bottomBar = {
            PlaybackControlBar(
                playbackState = playbackState,
                preGenState = preGenState,
                isReplayFromCache = isReplayFromCache,
                currentSentenceIndex = currentActiveIndex,
                totalSentences = sentenceChunks.size,
                currentVoice = currentVoice,
                currentSpeed = currentSpeed,
                lookaheadCount = lookaheadCount,
                onTogglePlayPause = onTogglePlayPause,
                onReplayCurrent = onReplayCurrent,
                onPrevious = onPrevious,
                onNext = onNext,
                onSpeedChange = onSpeedChange,
                onLookaheadChange = onLookaheadChange,
                onOpenVoiceStudio = onOpenVoiceStudio
            )
        }
    ) { innerPadding ->
        if (sentenceChunks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No document loaded. Import a book from the Library tab to begin reading.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.padding(32.dp)
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("reader_sentences_list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(sentenceChunks, key = { _, chunk -> chunk.index }) { index, chunk ->
                    val isCurrent = index == currentActiveIndex
                    val isPlayingThis = isCurrent && playbackState is PlaybackState.Playing
                    val isBufferingThis = isCurrent && playbackState is PlaybackState.Buffering
                    val isPreGeneratingThis = preGenState is PreGenState.PreGenerating && preGenState.sentenceIndex == index
                    val isPreGenReadyThis = preGenState is PreGenState.PreGenReady && preGenState.sentenceIndex == index

                    val cardBorder = when {
                        isCurrent -> BorderStroke(1.5.dp, NeonCyan)
                        isPreGeneratingThis -> BorderStroke(1.dp, StatusAmber)
                        isPreGenReadyThis -> BorderStroke(1.dp, StatusGreen.copy(alpha = 0.6f))
                        else -> null
                    }

                    val cardBackground by animateColorAsState(
                        targetValue = when {
                            isCurrent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            isPreGeneratingThis -> StatusAmber.copy(alpha = 0.1f)
                            else -> MaterialTheme.colorScheme.surface
                        },
                        label = "cardBg"
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlaySentence(index) }
                            .testTag("sentence_card_$index"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBackground),
                        border = cardBorder,
                        elevation = CardDefaults.cardElevation(defaultElevation = if (isCurrent) 4.dp else 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isCurrent) NeonCyan else MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = "#${index + 1}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = if (isCurrent) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            ),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    if (isPlayingThis) {
                                        AudioWaveform(
                                            isPlaying = true,
                                            modifier = Modifier.height(14.dp),
                                            barCount = 4
                                        )
                                    } else if (isBufferingThis) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(12.dp),
                                            strokeWidth = 2.dp,
                                            color = NeonCyan
                                        )
                                    } else if (isPreGeneratingThis) {
                                        Text(
                                            text = "Pre-generating...",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = StatusAmber,
                                                fontSize = 11.sp
                                            )
                                        )
                                    } else if (isPreGenReadyThis) {
                                        Text(
                                            text = "Buffered",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = StatusGreen,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // Individual Sentence Replay button
                                    IconButton(
                                        onClick = { onPlaySentence(index) },
                                        modifier = Modifier.size(32.dp).testTag("card_replay_$index")
                                    ) {
                                        Icon(
                                            imageVector = if (isCurrent) Icons.Default.Replay else Icons.Default.PlayArrow,
                                            contentDescription = "Play or Replay this sentence",
                                            tint = if (isCurrent) NeonCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = chunk.text,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = readerFontSize.sp,
                                    lineHeight = (readerFontSize * 1.45f).sp,
                                    color = if (isCurrent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                                    fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
                                    fontFamily = FontFamily.Serif
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    // FLAC Export in Progress Dialog
    if (flacExportProgress.isExporting) {
        AlertDialog(
            onDismissRequest = { /* Don't dismiss by clicking outside while exporting */ },
            title = {
                Text(
                    text = "Exporting Full Audio to FLAC",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Sentence ${flacExportProgress.currentSentence} of ${flacExportProgress.totalSentences} (${flacExportProgress.percent}%)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    LinearProgressIndicator(
                        progress = { flacExportProgress.percent / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = NeonCyan
                    )
                    Text(
                        text = "Lossless FLAC container • Standalone audio only",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = onCancelFlacExport,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
    }

    // FLAC Export Completed Dialog
    if (!flacExportProgress.isExporting && flacExportProgress.exportedUri != null) {
        val sizeMb = flacExportProgress.fileSizeBytes / (1024f * 1024f)
        AlertDialog(
            onDismissRequest = onDismissFlacExportDialog,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = StatusGreen
                    )
                    Text(
                        text = "FLAC Audiobook Ready",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "The entire book audio has been exported into lossless FLAC format (${String.format(java.util.Locale.US, "%.1f", sizeMb)} MB).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Saved to Downloads/KokoroVoice for offline listening in any external music or audiobook player.",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(flacExportProgress.exportedUri, "audio/flac")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Play FLAC Audio"))
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Audio saved to Downloads", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        onDismissFlacExportDialog()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.Black
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Play Audio", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissFlacExportDialog) {
                    Text("Done")
                }
            }
        )
    }
}

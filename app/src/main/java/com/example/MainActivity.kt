package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.SentenceChunk
import com.example.ui.ReaderViewModel
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.ModelManagerScreen
import com.example.ui.screens.ReaderScreen
import com.example.ui.screens.VoiceStudioScreen
import com.example.ui.theme.KokoroVoiceTheme
import com.example.ui.theme.NeonCyan

enum class AppTab {
    READER,
    LIBRARY,
    VOICES,
    MODELS
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: ReaderViewModel = viewModel()
            val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()

            KokoroVoiceTheme(darkTheme = isDarkMode) {
                MainAppScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainAppScreen(viewModel: ReaderViewModel) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(AppTab.READER) }

    // Navigation back press handler
    if (currentTab != AppTab.READER) {
        BackHandler {
            currentTab = AppTab.READER
        }
    }

    val currentBook by viewModel.currentBook.collectAsStateWithLifecycle()
    val sentenceChunks by viewModel.sentenceChunks.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val preGenState by viewModel.preGenState.collectAsStateWithLifecycle()
    val isReplayFromCache by viewModel.isReplayFromCache.collectAsStateWithLifecycle()
    val selectedVoice by viewModel.selectedVoice.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val lookaheadCount by viewModel.lookaheadCount.collectAsStateWithLifecycle()
    val batchPreRenderState by viewModel.batchPreRenderState.collectAsStateWithLifecycle()
    val flacExportProgress by viewModel.flacExportProgress.collectAsStateWithLifecycle()

    val books by viewModel.books.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val voices by viewModel.voices.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val cacheSizeBytes by viewModel.cacheSizeBytes.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.testTag("main_bottom_nav")
            ) {
                NavigationBarItem(
                    selected = currentTab == AppTab.READER,
                    onClick = { currentTab = AppTab.READER },
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = stringResource(R.string.tab_reader)
                        )
                    },
                    label = { Text(stringResource(R.string.tab_reader)) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = NeonCyan.copy(alpha = 0.25f),
                        selectedIconColor = NeonCyan,
                        selectedTextColor = NeonCyan
                    ),
                    modifier = Modifier.testTag("nav_reader")
                )

                NavigationBarItem(
                    selected = currentTab == AppTab.LIBRARY,
                    onClick = { currentTab = AppTab.LIBRARY },
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.LibraryBooks,
                            contentDescription = stringResource(R.string.tab_library)
                        )
                    },
                    label = { Text(stringResource(R.string.tab_library)) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = NeonCyan.copy(alpha = 0.25f),
                        selectedIconColor = NeonCyan,
                        selectedTextColor = NeonCyan
                    ),
                    modifier = Modifier.testTag("nav_library")
                )

                NavigationBarItem(
                    selected = currentTab == AppTab.VOICES,
                    onClick = { currentTab = AppTab.VOICES },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = stringResource(R.string.tab_voices)
                        )
                    },
                    label = { Text(stringResource(R.string.tab_voices)) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = NeonCyan.copy(alpha = 0.25f),
                        selectedIconColor = NeonCyan,
                        selectedTextColor = NeonCyan
                    ),
                    modifier = Modifier.testTag("nav_voices")
                )

                NavigationBarItem(
                    selected = currentTab == AppTab.MODELS,
                    onClick = { currentTab = AppTab.MODELS },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = stringResource(R.string.tab_models)
                        )
                    },
                    label = { Text(stringResource(R.string.tab_models)) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = NeonCyan.copy(alpha = 0.25f),
                        selectedIconColor = NeonCyan,
                        selectedTextColor = NeonCyan
                    ),
                    modifier = Modifier.testTag("nav_models")
                )
            }
        }
    ) { innerPadding ->
        Crossfade(
            targetState = currentTab,
            label = "tab_transition",
            modifier = Modifier.padding(innerPadding)
        ) { tab ->
            when (tab) {
                AppTab.READER -> {
                    ReaderScreen(
                        currentBook = currentBook,
                        sentenceChunks = sentenceChunks,
                        playbackState = playbackState,
                        preGenState = preGenState,
                        isReplayFromCache = isReplayFromCache,
                        currentVoice = selectedVoice,
                        currentSpeed = playbackSpeed,
                        lookaheadCount = lookaheadCount,
                        batchPreRenderState = batchPreRenderState,
                        flacExportProgress = flacExportProgress,
                        isDarkMode = isDarkMode,
                        onToggleDarkMode = { viewModel.toggleDarkMode() },
                        onPlaySentence = { idx -> viewModel.playSentence(idx) },
                        onTogglePlayPause = { viewModel.togglePlayPause() },
                        onReplayCurrent = { viewModel.replayCurrentSentence() },
                        onPrevious = { viewModel.previousSentence() },
                        onNext = { viewModel.nextSentence() },
                        onSpeedChange = { speed -> viewModel.setSpeed(speed) },
                        onLookaheadChange = { count -> viewModel.setLookaheadCount(count) },
                        onStartBatchPreRender = { viewModel.startBatchPreRender() },
                        onCancelBatchPreRender = { viewModel.cancelBatchPreRender() },
                        onExportFullAudioToFlac = { viewModel.exportFullAudioToFlac() },
                        onCancelFlacExport = { viewModel.cancelFlacExport() },
                        onDismissFlacExportDialog = { viewModel.dismissFlacExportDialog() },
                        onOpenVoiceStudio = { currentTab = AppTab.VOICES }
                    )
                }

                AppTab.LIBRARY -> {
                    LibraryScreen(
                        books = books,
                        currentBook = currentBook,
                        onSelectBook = { book ->
                            viewModel.loadBook(book)
                            currentTab = AppTab.READER
                        },
                        onImportBook = { title, author, content ->
                            viewModel.importEbook(title, author, content)
                            currentTab = AppTab.READER
                        },
                        onImportDocumentFile = { uri, name ->
                            viewModel.importDocumentFile(uri, name)
                            currentTab = AppTab.READER
                        },
                        onDeleteBook = { bookId -> viewModel.deleteBook(bookId) }
                    )
                }

                AppTab.VOICES -> {
                    VoiceStudioScreen(
                        voices = voices,
                        selectedVoice = selectedVoice,
                        onSelectVoice = { voice ->
                            viewModel.selectVoice(voice)
                            Toast.makeText(context, "${voice.name} activated", Toast.LENGTH_SHORT).show()
                        },
                        onImportCustomVoice = { name, gender, accent, pitch, speed, energy ->
                            viewModel.importCustomVoice(name, gender, accent, pitch, speed, energy)
                            Toast.makeText(context, "Voice profile $name created", Toast.LENGTH_SHORT).show()
                        },
                        onPreviewVoice = { voice ->
                            viewModel.selectVoice(voice)
                            viewModel.importEbook(
                                title = "Voice Preview",
                                author = voice.name,
                                content = "Hello! You are listening to high-fidelity on-device Kokoro neural voice synthesis."
                            )
                            currentTab = AppTab.READER
                            viewModel.playSentence(0)
                        },
                        onCloneVoiceFromUri = { uri, name, accent ->
                            viewModel.cloneVoiceFromAudioUri(uri, name, accent)
                            Toast.makeText(context, "Cloning $name from audio...", Toast.LENGTH_SHORT).show()
                        },
                        onCloneVoiceFromSamples = { samples, name, accent ->
                            viewModel.cloneVoiceFromAudioSamples(samples, name, accent)
                            Toast.makeText(context, "Voice $name cloned successfully!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                AppTab.MODELS -> {
                    ModelManagerScreen(
                        models = models,
                        selectedModel = selectedModel,
                        downloadProgress = downloadProgress,
                        cacheSizeBytes = cacheSizeBytes,
                        onSelectModel = { model ->
                            viewModel.selectModel(model)
                            Toast.makeText(context, "${model.name} activated", Toast.LENGTH_SHORT).show()
                        },
                        onDownloadModel = { model ->
                            viewModel.downloadModel(model)
                        },
                        onImportLocalModel = { uri, name ->
                            viewModel.importLocalModel(uri, name)
                            Toast.makeText(context, "Importing $name...", Toast.LENGTH_SHORT).show()
                        },
                        onClearCache = {
                            viewModel.clearAudioCache()
                            Toast.makeText(context, "Audio segment cache purged", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

package com.example.engine

import android.content.Context
import android.net.Uri
import com.example.data.dao.ModelDao
import com.example.data.dao.VoiceDao
import com.example.data.model.TtsModelEntity
import com.example.data.model.VoiceProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

data class DownloadProgress(
    val modelId: String,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val percent: Int = 0,
    val speedKbps: Float = 0f,
    val isDownloading: Boolean = false,
    val isComplete: Boolean = false,
    val error: String? = null
)

class HuggingFaceDownloader(
    private val context: Context,
    private val modelDao: ModelDao,
    private val voiceDao: VoiceDao
) {
    private val _downloadProgress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, DownloadProgress>> = _downloadProgress.asStateFlow()

    private val modelsDir = File(context.filesDir, "models").apply {
        if (!exists()) mkdirs()
    }

    /**
     * Download a TTS model from Hugging Face Hub.
     * Uses standard huggingface.co resolve endpoint.
     */
    /**
     * Download a TTS model from Hugging Face Hub.
     * Uses standard huggingface.co resolve endpoint with multi-hop redirect resolution.
     */
    suspend fun downloadModel(model: TtsModelEntity) = withContext(Dispatchers.IO) {
        val modelId = model.id
        updateProgress(
            modelId,
            DownloadProgress(modelId = modelId, isDownloading = true, totalBytes = model.sizeBytes)
        )

        val targetFile = File(modelsDir, "${model.repoId.replace('/', '_')}_${model.fileName}")
        val tempFile = File(modelsDir, "${model.repoId.replace('/', '_')}_${model.fileName}.tmp")
        val hfUrl = "https://huggingface.co/${model.repoId}/resolve/main/${model.fileName}"

        try {
            val (connection, contentLength) = openStreamWithRedirects(hfUrl)
            val expectedLength = if (contentLength > 0) contentLength else model.sizeBytes

            var bytesCopied = 0L
            val startTime = System.currentTimeMillis()
            var lastUpdateTime = startTime

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int

                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesCopied += read

                        val now = System.currentTimeMillis()
                        if (now - lastUpdateTime > 200) {
                            val percent = if (expectedLength > 0) {
                                ((bytesCopied * 100) / expectedLength).toInt().coerceIn(0, 99)
                            } else 50
                            val elapsedSec = (now - startTime) / 1000f
                            val speed = if (elapsedSec > 0) (bytesCopied / 1024f) / elapsedSec else 0f

                            updateProgress(
                                modelId,
                                DownloadProgress(
                                    modelId = modelId,
                                    bytesDownloaded = bytesCopied,
                                    totalBytes = expectedLength,
                                    percent = percent,
                                    speedKbps = speed,
                                    isDownloading = true
                                )
                            )
                            lastUpdateTime = now
                        }
                    }
                }
            }

            // Verify downloaded binary file size (must be at least 1MB for valid weights)
            if (tempFile.length() < 100 * 1024) {
                tempFile.delete()
                throw RuntimeException("Downloaded payload too small (${tempFile.length()} bytes) - file is corrupt or invalid.")
            }

            if (targetFile.exists()) targetFile.delete()
            tempFile.renameTo(targetFile)

            // Mark completed in database
            modelDao.updateModel(
                model.copy(
                    isDownloaded = true,
                    localFilePath = targetFile.absolutePath,
                    sizeBytes = targetFile.length()
                )
            )

            updateProgress(
                modelId,
                DownloadProgress(
                    modelId = modelId,
                    bytesDownloaded = targetFile.length(),
                    totalBytes = targetFile.length(),
                    percent = 100,
                    isDownloading = false,
                    isComplete = true
                )
            )
        } catch (e: Exception) {
            android.util.Log.e("HuggingFaceDownloader", "Download failed for ${model.name}", e)
            if (tempFile.exists()) tempFile.delete()

            // If an invalid stub exists from a previous bad download, delete it
            if (targetFile.exists() && targetFile.length() < 1024 * 1024) {
                targetFile.delete()
            }

            modelDao.updateModel(
                model.copy(
                    isDownloaded = false,
                    localFilePath = null
                )
            )

            updateProgress(
                modelId,
                DownloadProgress(
                    modelId = modelId,
                    percent = 0,
                    isDownloading = false,
                    isComplete = false,
                    error = e.message ?: "Download connection failed"
                )
            )
        }
    }

    private fun openStreamWithRedirects(initialUrl: String): Pair<HttpURLConnection, Long> {
        var currentUrl = initialUrl
        var redirects = 0
        val maxRedirects = 7

        while (redirects < maxRedirects) {
            val url = URL(currentUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 20000
                readTimeout = 45000
                instanceFollowRedirects = false // Handle cross-domain manually
                setRequestProperty("User-Agent", "KokoroVoice-Android/1.0")
                setRequestProperty("Accept-Encoding", "identity")
            }

            val code = connection.responseCode
            if (code in 300..399) {
                val newLocation = connection.getHeaderField("Location")
                connection.disconnect()
                if (newLocation != null) {
                    currentUrl = if (newLocation.startsWith("http")) {
                        newLocation
                    } else {
                        URL(url, newLocation).toString()
                    }
                    redirects++
                    continue
                }
            }

            if (code in 200..299) {
                val length = connection.contentLengthLong
                return Pair(connection, length)
            } else {
                val errorMsg = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                connection.disconnect()
                throw RuntimeException("HTTP $code ${connection.responseMessage}: $errorMsg")
            }
        }
        throw RuntimeException("Exceeded maximum redirects ($redirects) resolving $initialUrl")
    }

    /**
     * Import a local .onnx or model checkpoint via Android Storage Access Framework (SAF).
     */
    suspend fun importLocalModel(uri: Uri, displayName: String) = withContext(Dispatchers.IO) {
        val safeName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val targetFile = File(modelsDir, "imported_$safeName")

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }

        val modelEntity = TtsModelEntity(
            id = "local_${System.currentTimeMillis()}",
            name = displayName.removeSuffix(".onnx"),
            description = "Imported local ONNX model from device storage.",
            source = "LOCAL_IMPORT",
            repoId = "local",
            fileName = safeName,
            sizeBytes = targetFile.length(),
            isDownloaded = true,
            localFilePath = targetFile.absolutePath,
            sampleRate = 24000,
            isSelected = true
        )

        modelDao.insertModel(modelEntity)
        modelDao.setSelectedModel(modelEntity.id)
    }

    /**
     * Import a custom voice profile or JSON style vector.
     */
    suspend fun importCustomVoice(
        name: String,
        gender: String,
        accent: String,
        pitch: Float,
        speed: Float,
        energy: Float
    ) = withContext(Dispatchers.IO) {
        val voiceEntity = VoiceProfileEntity(
            id = "custom_${System.currentTimeMillis()}",
            name = name,
            language = "en-us",
            gender = gender,
            accent = accent,
            description = "Personalized voice profile ($accent $gender)",
            pitch = pitch,
            speed = speed,
            energy = energy,
            isCustomImport = true,
            isSelected = true
        )
        voiceDao.insertVoice(voiceEntity)
        voiceDao.setSelectedVoice(voiceEntity.id)
    }

    private fun updateProgress(modelId: String, progress: DownloadProgress) {
        val current = _downloadProgress.value.toMutableMap()
        current[modelId] = progress
        _downloadProgress.value = current
    }
}

package com.example.audio

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.data.model.VoiceProfileEntity
import com.example.domain.SentenceChunk
import com.example.engine.TtsSynthesisEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class FlacExportProgress(
    val isExporting: Boolean = false,
    val currentSentence: Int = 0,
    val totalSentences: Int = 0,
    val percent: Int = 0,
    val exportedUri: Uri? = null,
    val exportedFilePath: String? = null,
    val fileSizeBytes: Long = 0L,
    val errorMessage: String? = null
)

class FlacAudioExporter(
    private val context: Context,
    private val ttsEngine: TtsSynthesisEngine
) {
    companion object {
        private const val TAG = "FlacAudioExporter"
        const val BLOCK_SIZE = 4096
    }

    /**
     * Synthesizes (or retrieves from cache) all sentence audio segments for the book/pdf,
     * concatenates the PCM streams, encodes into standard lossless FLAC format,
     * and writes to the device's public Downloads / Audio storage.
     */
    suspend fun exportEbookToFlac(
        chunks: List<SentenceChunk>,
        voice: VoiceProfileEntity,
        speed: Float,
        bookTitle: String,
        author: String,
        onProgress: (FlacExportProgress) -> Unit
    ): FlacExportProgress = withContext(Dispatchers.IO) {
        if (chunks.isEmpty()) {
            return@withContext FlacExportProgress(errorMessage = "No text sentences to export")
        }

        val total = chunks.size
        onProgress(FlacExportProgress(isExporting = true, currentSentence = 0, totalSentences = total, percent = 0))

        val allPcmSamples = ArrayList<Short>()
        val sampleRate = TtsSynthesisEngine.SAMPLE_RATE

        try {
            // 1. Gather all sentence audio WAVs
            for ((index, chunk) in chunks.withIndex()) {
                ensureActive()
                try {
                    val wavFile = ttsEngine.synthesizeSentence(chunk, voice, speed)
                    val samples = extractPcmFromWav(wavFile)
                    for (s in samples) {
                        allPcmSamples.add(s)
                    }

                    // Insert 150ms inter-sentence natural breathing pause (silence)
                    val pauseSamples = (sampleRate * 0.15f).toInt()
                    for (p in 0 until pauseSamples) {
                        allPcmSamples.add(0)
                    }

                    val percent = ((index + 1) * 70) / total // 0-70% for synthesis
                    onProgress(
                        FlacExportProgress(
                            isExporting = true,
                            currentSentence = index + 1,
                            totalSentences = total,
                            percent = percent
                        )
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error synthesizing chunk $index during FLAC export", e)
                }
            }

            ensureActive()

            if (allPcmSamples.isEmpty()) {
                val err = FlacExportProgress(errorMessage = "Failed to synthesize audio segments")
                onProgress(err)
                return@withContext err
            }

            onProgress(FlacExportProgress(isExporting = true, currentSentence = total, totalSentences = total, percent = 75))

            // 2. Encode PCM samples into standard FLAC
            val flacBytes = encodePcmToFlac(
                samples = allPcmSamples.toShortArray(),
                sampleRate = sampleRate,
                title = bookTitle,
                artist = voice.name,
                album = "Kokoro Audiobook"
            )

            ensureActive()

            onProgress(FlacExportProgress(isExporting = true, currentSentence = total, totalSentences = total, percent = 90))

            // 3. Save to public Downloads directory and local cache for sharing
            val safeTitle = bookTitle.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(40)
            val fileName = "${safeTitle}_audiobook.flac"

            val localExportFile = File(context.cacheDir, fileName)
            FileOutputStream(localExportFile).use { it.write(flacBytes) }

            var outputUri: Uri? = null

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Audio.Media.MIME_TYPE, "audio/flac")
                        put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/KokoroVoice")
                        put(MediaStore.Audio.Media.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { out ->
                            out.write(flacBytes)
                        }
                        contentValues.clear()
                        contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                        resolver.update(uri, contentValues, null, null)
                        outputUri = uri
                    }
                } else {
                    val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val targetFile = File(publicDir, fileName)
                    FileOutputStream(targetFile).use { it.write(flacBytes) }
                    outputUri = Uri.fromFile(targetFile)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed writing to MediaStore Downloads, using app cache file", e)
                outputUri = Uri.fromFile(localExportFile)
            }

            val result = FlacExportProgress(
                isExporting = false,
                currentSentence = total,
                totalSentences = total,
                percent = 100,
                exportedUri = outputUri ?: Uri.fromFile(localExportFile),
                exportedFilePath = localExportFile.absolutePath,
                fileSizeBytes = flacBytes.size.toLong()
            )
            onProgress(result)
            result
        } catch (e: CancellationException) {
            Log.d(TAG, "FLAC export cancelled cleanly")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "FLAC export failed", e)
            val err = FlacExportProgress(errorMessage = e.message ?: "FLAC export failed")
            onProgress(err)
            err
        }
    }

    private fun extractPcmFromWav(wavFile: File): ShortArray {
        val bytes = wavFile.readBytes()
        if (bytes.size <= 44) return ShortArray(0)

        // Find "data" chunk
        var dataStart = 44
        for (i in 0 until bytes.size - 4) {
            if (bytes[i] == 'd'.code.toByte() &&
                bytes[i + 1] == 'a'.code.toByte() &&
                bytes[i + 2] == 't'.code.toByte() &&
                bytes[i + 3] == 'a'.code.toByte()
            ) {
                dataStart = i + 8
                break
            }
        }

        val pcmLength = bytes.size - dataStart
        val numSamples = pcmLength / 2
        val samples = ShortArray(numSamples)
        val buffer = ByteBuffer.wrap(bytes, dataStart, pcmLength).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until numSamples) {
            samples[i] = buffer.short
        }
        return samples
    }

    /**
     * Standard FLAC stream encoder:
     * Writes 4-byte 'fLaC' marker, STREAMINFO metadata block, Vorbis comment metadata block,
     * followed by standard FLAC audio frames with verbatim subframes and CRC checks.
     */
    fun encodePcmToFlac(
        samples: ShortArray,
        sampleRate: Int,
        title: String,
        artist: String,
        album: String
    ): ByteArray {
        val out = ByteArrayOutputStream()

        // 1. "fLaC" marker
        out.write("fLaC".toByteArray(Charsets.US_ASCII))

        val totalSamples = samples.size.toLong()
        val numChannels = 1
        val bitsPerSample = 16

        // 2. STREAMINFO metadata block (Header: 1 bit isLast, 7 bits type 0, 24 bits length 34)
        out.write(0x00) // Not last, type 0 (STREAMINFO)
        out.write(0x00)
        out.write(0x00)
        out.write(34) // Length = 34 bytes

        // Stream info body (34 bytes)
        val streamInfo = ByteArray(34)
        val siBuf = ByteBuffer.wrap(streamInfo).order(ByteOrder.BIG_ENDIAN)
        siBuf.putShort(BLOCK_SIZE.toShort()) // Min block size
        siBuf.putShort(BLOCK_SIZE.toShort()) // Max block size
        // Min frame size (3 bytes)
        siBuf.put(0.toByte()); siBuf.put(0.toByte()); siBuf.put(0.toByte())
        // Max frame size (3 bytes)
        siBuf.put(0.toByte()); siBuf.put(0.toByte()); siBuf.put(0.toByte())

        // Sample rate (20 bits), channels - 1 (3 bits), bits - 1 (5 bits), total samples (36 bits) = 64 bits (8 bytes)
        val b0 = ((sampleRate shr 12) and 0xFF).toByte()
        val b1 = ((sampleRate shr 4) and 0xFF).toByte()
        val b2 = (((sampleRate and 0x0F) shl 4) or ((numChannels - 1) shl 1) or (((bitsPerSample - 1) shr 4) and 0x01)).toByte()
        val b3 = ((((bitsPerSample - 1) and 0x0F) shl 4) or (((totalSamples shr 32) and 0x0F).toInt())).toByte()
        val b4 = ((totalSamples shr 24) and 0xFF).toByte()
        val b5 = ((totalSamples shr 16) and 0xFF).toByte()
        val b6 = ((totalSamples shr 8) and 0xFF).toByte()
        val b7 = (totalSamples and 0xFF).toByte()

        siBuf.put(b0); siBuf.put(b1); siBuf.put(b2); siBuf.put(b3)
        siBuf.put(b4); siBuf.put(b5); siBuf.put(b6); siBuf.put(b7)

        // MD5 signature (16 bytes = 128 bits, all 0)
        for (i in 0 until 16) siBuf.put(0)

        out.write(streamInfo)

        // 3. VORBIS_COMMENT metadata block (block type 4, isLast = true)
        val vorbisBytes = createVorbisCommentBlock(title, artist, album)
        out.write(0x84) // isLast = true (0x80), type = 4 (VORBIS_COMMENT)
        val vLen = vorbisBytes.size
        out.write((vLen shr 16) and 0xFF)
        out.write((vLen shr 8) and 0xFF)
        out.write(vLen and 0xFF)
        out.write(vorbisBytes)

        // 4. Audio frames
        var sampleOffset = 0
        var frameNumber = 0

        while (sampleOffset < samples.size) {
            val currentBlockSize = (samples.size - sampleOffset).coerceAtMost(BLOCK_SIZE)
            val frameBytes = encodeFlacFrame(samples, sampleOffset, currentBlockSize, frameNumber, sampleRate)
            out.write(frameBytes)
            sampleOffset += currentBlockSize
            frameNumber++
        }

        return out.toByteArray()
    }

    private fun createVorbisCommentBlock(title: String, artist: String, album: String): ByteArray {
        val out = ByteArrayOutputStream()
        val vendor = "Kokoro Voice Audio Engine"
        writeLittleEndianInt(out, vendor.length)
        out.write(vendor.toByteArray(Charsets.UTF_8))

        val comments = listOf(
            "TITLE=$title",
            "ARTIST=$artist",
            "ALBUM=$album",
            "COMMENT=Synthesized with Kokoro Neural TTS for Android"
        )

        writeLittleEndianInt(out, comments.size)
        for (c in comments) {
            val cBytes = c.toByteArray(Charsets.UTF_8)
            writeLittleEndianInt(out, cBytes.size)
            out.write(cBytes)
        }

        return out.toByteArray()
    }

    private fun encodeFlacFrame(
        samples: ShortArray,
        offset: Int,
        blockSize: Int,
        frameNumber: Int,
        sampleRate: Int
    ): ByteArray {
        val frameOut = ByteArrayOutputStream()

        // Frame header:
        // Sync code: 14 bits (11111111111110) = 0xFF, 0xF8
        // Blocking strategy: 0 (fixed)
        // Block size strategy: 1100 = get from end of header (16 bits)
        // Sample rate strategy: 0000 = get from STREAMINFO
        // Channel assignment: 0000 = mono
        // Sample size: 100 = 16 bits per sample
        // Reserved: 0
        val headerBytes = ByteArrayOutputStream()
        headerBytes.write(0xFF)
        headerBytes.write(0xF8)
        headerBytes.write(0xC8) // Block size in 16 bits after header, mono, 16 bits/sample
        headerBytes.write(0x00) // Sample rate in streaminfo

        // UTF-8 encoded frame number
        writeUtf8CodedNumber(headerBytes, frameNumber)

        // Block size (16 bits)
        headerBytes.write(((blockSize - 1) shr 8) and 0xFF)
        headerBytes.write((blockSize - 1) and 0xFF)

        // CRC-8 of header
        val headerArray = headerBytes.toByteArray()
        val crc8 = computeCrc8(headerArray)

        frameOut.write(headerArray)
        frameOut.write(crc8)

        // Subframe:
        // Zero bit (1 bit), subframe type: 000000 = Verbatim (6 bits), wasted bits flag: 0 (1 bit) -> 0x00
        frameOut.write(0x00)

        // Verbatim audio data (16-bit big-endian samples)
        for (i in 0 until blockSize) {
            val sample = samples[offset + i].toInt()
            frameOut.write((sample shr 8) and 0xFF)
            frameOut.write(sample and 0xFF)
        }

        // Frame footer: CRC-16 of entire frame
        val currentFrameData = frameOut.toByteArray()
        val crc16 = computeCrc16(currentFrameData)
        frameOut.write((crc16 shr 8) and 0xFF)
        frameOut.write(crc16 and 0xFF)

        return frameOut.toByteArray()
    }

    private fun writeLittleEndianInt(out: OutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
        out.write((value shr 16) and 0xFF)
        out.write((value shr 24) and 0xFF)
    }

    private fun writeUtf8CodedNumber(out: OutputStream, value: Int) {
        if (value < 0x80) {
            out.write(value)
        } else if (value < 0x800) {
            out.write(0xC0 or (value shr 6))
            out.write(0x80 or (value and 0x3F))
        } else {
            out.write(0xE0 or (value shr 12))
            out.write(0x80 or ((value shr 6) and 0x3F))
            out.write(0x80 or (value and 0x3F))
        }
    }

    private fun computeCrc8(data: ByteArray): Int {
        var crc = 0
        for (b in data) {
            crc = crc xor (b.toInt() and 0xFF)
            for (i in 0 until 8) {
                crc = if ((crc and 0x80) != 0) ((crc shl 1) xor 0x07) and 0xFF else (crc shl 1) and 0xFF
            }
        }
        return crc
    }

    private fun computeCrc16(data: ByteArray): Int {
        var crc = 0
        for (b in data) {
            crc = crc xor ((b.toInt() and 0xFF) shl 8)
            for (i in 0 until 8) {
                crc = if ((crc and 0x8000) != 0) ((crc shl 1) xor 0x1021) and 0xFFFF else (crc shl 1) and 0xFFFF
            }
        }
        return crc
    }
}

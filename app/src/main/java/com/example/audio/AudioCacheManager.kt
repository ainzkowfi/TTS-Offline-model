package com.example.audio

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioCacheManager(private val context: Context) {

    private val cacheDir = File(context.cacheDir, "tts_segments").apply {
        if (!exists()) mkdirs()
    }

    fun getAudioFile(cacheKey: String): File? {
        val file = File(cacheDir, "$cacheKey.wav")
        return if (file.exists() && file.length() > 44) file else null
    }

    fun isCached(cacheKey: String): Boolean {
        return getAudioFile(cacheKey) != null
    }

    fun saveWav(cacheKey: String, pcmSamples: ShortArray, sampleRate: Int = 24000): File {
        val file = File(cacheDir, "$cacheKey.wav")
        val numChannels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * numChannels * bitsPerSample / 8
        val dataSize = pcmSamples.size * 2
        val totalSize = 36 + dataSize

        FileOutputStream(file).use { out ->
            // RIFF header
            out.write("RIFF".toByteArray())
            out.write(intToByteArray(totalSize))
            out.write("WAVE".toByteArray())

            // "fmt " subchunk
            out.write("fmt ".toByteArray())
            out.write(intToByteArray(16)) // Subchunk1Size for PCM
            out.write(shortToByteArray(1.toShort())) // AudioFormat 1 = PCM
            out.write(shortToByteArray(numChannels.toShort()))
            out.write(intToByteArray(sampleRate))
            out.write(intToByteArray(byteRate))
            out.write(shortToByteArray((numChannels * bitsPerSample / 8).toShort())) // BlockAlign
            out.write(shortToByteArray(bitsPerSample.toShort()))

            // "data" subchunk
            out.write("data".toByteArray())
            out.write(intToByteArray(dataSize))

            // PCM 16-bit samples
            val byteBuffer = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
            for (sample in pcmSamples) {
                byteBuffer.putShort(sample)
            }
            out.write(byteBuffer.array())
        }
        return file
    }

    fun getCacheSizeBytes(): Long {
        return cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    fun clearCache(): Long {
        val freed = getCacheSizeBytes()
        cacheDir.listFiles()?.forEach { it.delete() }
        return freed
    }

    private fun intToByteArray(value: Int): ByteArray {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    }

    private fun shortToByteArray(value: Short): ByteArray {
        return ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value).array()
    }
}

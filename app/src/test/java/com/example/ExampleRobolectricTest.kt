package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Kokoro Voice", appName)
    }

    @Test
    fun flacAudioExporter_encodesValidFlacStream() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cacheManager = com.example.audio.AudioCacheManager(context)
        val ttsEngine = com.example.engine.TtsSynthesisEngine(context, cacheManager)
        val exporter = com.example.audio.FlacAudioExporter(context, ttsEngine)

        // Generate 1 second of 440Hz sine wave PCM
        val sampleRate = 24000
        val samples = ShortArray(sampleRate) { i ->
            (kotlin.math.sin(2 * kotlin.math.PI * 440 * i / sampleRate) * 16000).toInt().toShort()
        }

        val flacBytes = exporter.encodePcmToFlac(
            samples = samples,
            sampleRate = sampleRate,
            title = "Test Chapter",
            artist = "Kokoro Voice",
            album = "Test Audiobook"
        )

        // Check FLAC magic header "fLaC"
        assertEquals('f'.code.toByte(), flacBytes[0])
        assertEquals('L'.code.toByte(), flacBytes[1])
        assertEquals('a'.code.toByte(), flacBytes[2])
        assertEquals('C'.code.toByte(), flacBytes[3])
        org.junit.Assert.assertTrue("FLAC stream size should be substantial", flacBytes.size > 100)
    }

    @Test
    fun voiceCloneEngine_analyzesPcmMetricsAccurately() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = com.example.data.AppDatabase.getInstance(context)
        val cloneEngine = com.example.engine.VoiceCloneEngine(context, db.voiceDao())

        // 220Hz female pitch sine wave
        val sampleRate = com.example.engine.VoiceCloneEngine.SAMPLE_RATE
        val samples = ShortArray(sampleRate * 2) { i ->
            (kotlin.math.sin(2 * kotlin.math.PI * 220 * i / sampleRate) * 12000).toInt().toShort()
        }

        val metrics = cloneEngine.analyzeAcousticProfile(samples)
        org.junit.Assert.assertTrue("Detected pitch should be close to 220Hz", metrics.meanF0Hz in 200f..240f)
        assertEquals("Female", metrics.detectedGender)
        org.junit.Assert.assertTrue("Acoustic energy should be positive", metrics.acousticEnergy > 0f)
    }
}

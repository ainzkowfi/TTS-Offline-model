package com.example

import com.example.domain.PunctuationNormalizer
import com.example.domain.SentenceSegmenter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun sentenceSegmenter_splitsOnDotBoundaryCorrectly() {
        val text = "This is sentence one. This is sentence two! Is this sentence three? Yes, it is."
        val chunks = SentenceSegmenter.segment(text)

        assertEquals(4, chunks.size)
        assertEquals("This is sentence one.", chunks[0].text)
        assertEquals("This is sentence two!", chunks[1].text)
        assertEquals("Is this sentence three?", chunks[2].text)
        assertEquals("Yes, it is.", chunks[3].text)
    }

    @Test
    fun sentenceSegmenter_preservesAbbreviationsWithoutSplitting() {
        val text = "Dr. Smith met with Mr. Brown at 5 p.m. to discuss v1.0 of the software."
        val chunks = SentenceSegmenter.segment(text)

        assertEquals(1, chunks.size)
        assertTrue(chunks[0].text.contains("Dr. Smith"))
        assertTrue(chunks[0].text.contains("Mr. Brown"))
    }

    @Test
    fun sentenceSegmenter_computesDeterministicCacheKeys() {
        val text = "Neural speech synthesis is fast."
        val chunks = SentenceSegmenter.segment(text)
        assertEquals(1, chunks.size)

        val key1 = chunks[0].computeCacheKey("af_heart", 1.0f)
        val key2 = chunks[0].computeCacheKey("af_heart", 1.0f)
        val keyDifferentVoice = chunks[0].computeCacheKey("am_adam", 1.0f)
        val keyDifferentSpeed = chunks[0].computeCacheKey("af_heart", 1.25f)

        assertEquals(key1, key2)
        assertTrue(key1 != keyDifferentVoice)
        assertTrue(key1 != keyDifferentSpeed)
    }

    @Test
    fun punctuationNormalizer_cleansEbookPunctuationAndPageArtifacts() {
        val raw = "“Hello world,” he said—with a pause…\nPage 42 of 180\nIt was a break-\nthrough."
        val normalized = PunctuationNormalizer.normalize(raw)

        // Verifies curly quotes converted to straight quotes
        assertTrue(normalized.contains("\"Hello world,\""))
        // Verifies em-dash spacing
        assertTrue(normalized.contains(" — "))
        // Verifies hyphenated line break rejoined
        assertTrue(normalized.contains("breakthrough"))
        // Verifies page number stripped
        assertFalse(normalized.contains("Page 42 of 180"))
    }
}

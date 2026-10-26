package com.example.domain

import java.security.MessageDigest

data class SentenceChunk(
    val index: Int,
    val text: String,
    val wordCount: Int,
    val charStart: Int,
    val charEnd: Int
) {
    /**
     * Compute a deterministic cache key for this sentence segment
     * based on text content, voice ID, and speech speed rate.
     */
    fun computeCacheKey(voiceId: String, speed: Float): String {
        val speedBucket = String.format(java.util.Locale.US, "%.2f", speed)
        val raw = "v2:$voiceId:$speedBucket:${text.trim()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(24)
    }
}

object SentenceSegmenter {

    private val ABBREVIATIONS = setOf(
        "mr.", "mrs.", "ms.", "dr.", "prof.", "sr.", "jr.",
        "e.g.", "i.e.", "etc.", "vs.", "fig.", "vol.", "ch.",
        "p.", "pp.", "no.", "st.", "ave.", "rd.", "blvd.", "dept.",
        "a.m.", "p.m.", "am.", "pm.", "approx.", "est.", "inc.", "ltd.", "co."
    )

    /**
     * Splits long-form text into sentence chunks ending with a period/dot (.),
     * question mark (?), or exclamation mark (!), handling abbreviations, quotes,
     * initials, and multiline paragraph breaks.
     */
    fun segment(text: String): List<SentenceChunk> {
        if (text.isBlank()) return emptyList()

        val chunks = mutableListOf<SentenceChunk>()
        val length = text.length
        var start = 0
        var i = 0

        while (i < length) {
            val char = text[i]

            // Look for sentence terminators: . ! ? or double newline
            val isTerminator = char == '.' || char == '!' || char == '?'
            val isDoubleNewline = (char == '\n' && i + 1 < length && text[i + 1] == '\n')

            if (isTerminator) {
                // Check if this period is part of an abbreviation, initial, version, or decimal number
                val isAbbrev = if (char == '.') isAbbreviationOrNotSentenceEnd(text, start, i) else false
                val isDecimal = (char == '.' && i + 1 < length && text[i + 1].isDigit())

                if (!isAbbrev && !isDecimal) {
                    // Include any immediately following quotes or parentheses: e.g. "He said." or (bracket).
                    var end = i + 1
                    while (end < length && (text[end] == '"' || text[end] == '\'' || text[end] == '”' || text[end] == '’' || text[end] == ')' || text[end] == '»')) {
                        end++
                    }

                    val candidate = text.substring(start, end).trim()
                    if (candidate.isNotEmpty()) {
                        val wordCount = candidate.split(Regex("\\s+")).count { it.isNotBlank() }
                        chunks.add(
                            SentenceChunk(
                                index = chunks.size,
                                text = candidate,
                                wordCount = wordCount,
                                charStart = start,
                                charEnd = end
                            )
                        )
                    }

                    // Advance start to next non-whitespace character
                    start = end
                    while (start < length && text[start].isWhitespace()) {
                        start++
                    }
                    i = start
                    continue
                }
            } else if (isDoubleNewline) {
                val candidate = text.substring(start, i).trim()
                if (candidate.isNotEmpty()) {
                    val wordCount = candidate.split(Regex("\\s+")).count { it.isNotBlank() }
                    chunks.add(
                        SentenceChunk(
                            index = chunks.size,
                            text = candidate,
                            wordCount = wordCount,
                            charStart = start,
                            charEnd = i
                        )
                    )
                }
                while (i < length && text[i].isWhitespace()) {
                    i++
                }
                start = i
                continue
            }
            i++
        }

        // Remaining tail chunk
        if (start < length) {
            val tail = text.substring(start).trim()
            if (tail.isNotEmpty()) {
                val wordCount = tail.split(Regex("\\s+")).count { it.isNotBlank() }
                chunks.add(
                    SentenceChunk(
                        index = chunks.size,
                        text = tail,
                        wordCount = wordCount,
                        charStart = start,
                        charEnd = length
                    )
                )
            }
        }

        return chunks
    }

    private fun isAbbreviationOrNotSentenceEnd(text: String, sentenceStart: Int, dotIndex: Int): Boolean {
        val length = text.length

        // Find next non-whitespace char after dot (skipping quotes)
        var nextCharIdx = dotIndex + 1
        while (nextCharIdx < length && (text[nextCharIdx] == '"' || text[nextCharIdx] == '\'' || text[nextCharIdx] == '”' || text[nextCharIdx] == '’')) {
            nextCharIdx++
        }
        while (nextCharIdx < length && text[nextCharIdx].isWhitespace()) {
            nextCharIdx++
        }

        // If next word starts with a lowercase letter (e.g., "5 p.m. to discuss"), it's not a sentence end
        if (nextCharIdx < length && text[nextCharIdx].isLowerCase()) {
            return true
        }

        // Extract word preceding the dot
        var wordStart = dotIndex - 1
        while (wordStart >= sentenceStart && !text[wordStart].isWhitespace()) {
            wordStart--
        }
        wordStart++

        val candidate = text.substring(wordStart, dotIndex + 1).lowercase()
        if (ABBREVIATIONS.contains(candidate)) {
            return true
        }

        // Check single letter initials (e.g. H. G. Wells)
        if (dotIndex - wordStart == 1 && text[wordStart].isLetter()) {
            return true
        }

        // Check version or identifier (e.g. v1.0)
        if (candidate.startsWith("v") && candidate.drop(1).dropLast(1).all { it.isDigit() }) {
            return true
        }

        return false
    }
}

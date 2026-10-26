package com.example.domain

object PunctuationNormalizer {

    /**
     * Normalizes punctuation across an entire ebook or document so neural TTS engines
     * produce optimal acoustic prosody, human-like pauses, and clean intonation.
     */
    fun normalize(rawText: String): String {
        if (rawText.isBlank()) return ""

        var text = rawText

        // 1. Normalize line breaks and multiple spaces
        text = text.replace("\r\n", "\n").replace('\r', '\n')

        // 2. Normalize unicode quotes, primes, and accents
        text = text.replace(Regex("[\u2018\u2019\u201A\u201B`´]"), "'")
        text = text.replace(Regex("[\u201C\u201D\u201E\u201F«»]"), "\"")

        // 3. Normalize em-dashes and en-dashes to natural speaking pauses
        text = text.replace(Regex("[—–]"), " — ")
        text = text.replace(Regex("\\s*—\\s*"), " — ")

        // 4. Normalize ellipses (... or …) to proper cadence pause
        text = text.replace("…", "...")
        text = text.replace(Regex("\\.{4,}"), "...")
        text = text.replace(Regex("(?<=\\w)\\.{3}"), " ... ")

        // 5. Rejoin hyphenated line-broken words: e.g. "connec-\ntion" -> "connection"
        text = text.replace(Regex("([a-zA-Z]+)-\\n\\s*([a-zA-Z]+)"), "$1$2")

        // 6. Strip common document page header/footer artifacts
        text = stripHeaderAndFooterArtifacts(text)

        // 7. Ensure space after sentence-ending punctuation if followed by text
        text = text.replace(Regex("([.!?])([A-Z])"), "$1 $2")

        // 8. Clean excessive whitespace
        text = text.replace(Regex("[ \\t]+"), " ")
        text = text.replace(Regex("\\n{3,}"), "\n\n")

        return text.trim()
    }

    private fun stripHeaderAndFooterArtifacts(text: String): String {
        val lines = text.lines()
        val cleaned = mutableListOf<String>()

        val pageNumberPattern = Regex("^(page\\s+\\d+(\\s+of\\s+\\d+)?|\\d+\\s*(/|of)\\s*\\d+|\\d+)$", RegexOption.IGNORE_CASE)
        val copyrightPattern = Regex("^(copyright|all rights reserved|chapter\\s+\\d+$)", RegexOption.IGNORE_CASE)

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                cleaned.add("")
                continue
            }

            // Skip lines that are just page numbers
            if (pageNumberPattern.matches(trimmed)) {
                continue
            }

            cleaned.add(line)
        }

        return cleaned.joinToString("\n")
    }
}

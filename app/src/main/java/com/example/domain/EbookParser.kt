package com.example.domain

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

data class ParsedDocument(
    val title: String,
    val author: String,
    val content: String,
    val format: String
)

object EbookParser {

    /**
     * Parses a document Uri (.epub, .pdf, .txt, .md) into cleaned, punctuation-normalized prose.
     */
    fun parseDocument(context: Context, uri: Uri, fileName: String): ParsedDocument {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val baseTitle = fileName.substringBeforeLast('.')

        val rawText = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                when (extension) {
                    "epub" -> parseEpub(stream)
                    "pdf" -> parsePdf(stream)
                    else -> parsePlainText(stream)
                }
            } ?: ""
        } catch (e: Exception) {
            "Failed to parse document: ${e.message}"
        }

        // Run full punctuation normalization for neural TTS
        val normalized = PunctuationNormalizer.normalize(rawText)

        return ParsedDocument(
            title = baseTitle.replace('_', ' ').replace('-', ' '),
            author = when (extension) {
                "epub" -> "EPUB Book"
                "pdf" -> "PDF Document"
                else -> "Imported Text"
            },
            content = normalized,
            format = extension.uppercase()
        )
    }

    /**
     * Parses an EPUB file (ZIP container) by extracting and concatenating all XHTML/HTML chapter flows.
     */
    fun parseEpub(inputStream: InputStream): String {
        val contentBuilder = StringBuilder()
        val zip = ZipInputStream(inputStream)
        var entry = zip.nextEntry

        val chapterTexts = mutableListOf<Pair<String, String>>()

        while (entry != null) {
            val name = entry.name.lowercase()
            // Extract chapter HTML/XHTML files
            if ((name.endsWith(".xhtml") || name.endsWith(".html") || name.endsWith(".htm")) &&
                !name.contains("toc") && !name.contains("nav")
            ) {
                val bytes = readStreamBytes(zip)
                val html = String(bytes, Charsets.UTF_8)
                val text = stripHtmlTags(html)
                if (text.isNotBlank()) {
                    chapterTexts.add(Pair(entry.name, text))
                }
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }

        // Sort chapters alphabetically by entry name (standard epub chapter structure)
        chapterTexts.sortBy { it.first }
        for ((_, text) in chapterTexts) {
            contentBuilder.append(text).append("\n\n")
        }

        return if (contentBuilder.isNotBlank()) {
            contentBuilder.toString()
        } else {
            "No readable text chapters found in this EPUB archive."
        }
    }

    /**
     * Lightweight PDF text stream extractor parsing text blocks (BT ... ET, Tj, TJ)
     * and string literals without massive native library overhead.
     */
    fun parsePdf(inputStream: InputStream): String {
        val bytes = inputStream.readBytes()
        val text = String(bytes, Charsets.ISO_8859_1)
        val extractedBuilder = StringBuilder()

        // 1. Find text objects between BT (Begin Text) and ET (End Text)
        val btEtRegex = Regex("BT\\s+(.*?)\\s+ET", RegexOption.DOT_MATCHES_ALL)
        val textMatches = btEtRegex.findAll(text)

        for (match in textMatches) {
            val block = match.groupValues[1]

            // Match Tj strings: (Hello World) Tj
            val tjRegex = Regex("\\((.*?)\\)\\s*Tj")
            for (tjMatch in tjRegex.findAll(block)) {
                val str = unescapePdfString(tjMatch.groupValues[1])
                extractedBuilder.append(str).append(" ")
            }

            // Match TJ arrays: [(Hello) -10 (World)] TJ
            val arrayRegex = Regex("\\[(.*?)\\]\\s*TJ", RegexOption.DOT_MATCHES_ALL)
            for (arrMatch in arrayRegex.findAll(block)) {
                val arrayContent = arrMatch.groupValues[1]
                val subStringRegex = Regex("\\((.*?)\\)")
                for (subMatch in subStringRegex.findAll(arrayContent)) {
                    val str = unescapePdfString(subMatch.groupValues[1])
                    extractedBuilder.append(str)
                }
                extractedBuilder.append(" ")
            }

            if (block.contains("T*") || block.contains("TD") || block.contains("Td")) {
                extractedBuilder.append("\n")
            }
        }

        val result = extractedBuilder.toString().trim()
        return if (result.length > 50) {
            result
        } else {
            // Fallback: extract ASCII sequences with words
            extractReadableAsciiFallback(bytes)
        }
    }

    private fun parsePlainText(inputStream: InputStream): String {
        return inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun stripHtmlTags(html: String): String {
        // Strip script and style blocks
        var clean = html.replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        clean = clean.replace(Regex("<style[^>]*>.*?</style>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")

        // Convert block elements to newlines
        clean = clean.replace(Regex("</?(p|div|h[1-6]|li|blockquote|br)[^>]*>", RegexOption.IGNORE_CASE), "\n")

        // Strip remaining HTML tags
        clean = clean.replace(Regex("<[^>]+>"), "")

        // Unescape standard HTML entities
        clean = clean.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&mdash;", " — ")
            .replace("&ndash;", " – ")

        return clean.trim()
    }

    private fun unescapePdfString(str: String): String {
        return str.replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\(", "(")
            .replace("\\)", ")")
            .replace("\\\\", "\\")
    }

    private fun extractReadableAsciiFallback(bytes: ByteArray): String {
        val sb = StringBuilder()
        var runLength = 0
        val currentWord = StringBuilder()

        for (b in bytes) {
            val c = b.toInt().toChar()
            if (c in ' '..'~' || c == '\n') {
                currentWord.append(c)
                runLength++
            } else {
                if (runLength >= 4 && currentWord.contains(Regex("[a-zA-Z]"))) {
                    sb.append(currentWord).append(" ")
                }
                currentWord.clear()
                runLength = 0
            }
        }
        if (runLength >= 4) {
            sb.append(currentWord)
        }
        return sb.toString().trim()
    }

    private fun readStreamBytes(stream: InputStream): ByteArray {
        val buffer = ByteArray(8192)
        val baos = ByteArrayOutputStream()
        var read: Int
        while (stream.read(buffer).also { read = it } != -1) {
            baos.write(buffer, 0, read)
        }
        return baos.toByteArray()
    }
}

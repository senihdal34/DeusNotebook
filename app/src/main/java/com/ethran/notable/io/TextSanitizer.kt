package com.ethran.notable.io

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.text.Normalizer

/**
 * Cleans text that comes out of handwriting recognition, file names and note titles before it is
 * exported or shown (Home excerpts, Markdown, hashtags). Pure Kotlin, no Android dependencies.
 *
 * What it does, in this order:
 *  1. repairs UTF-8-read-as-Latin-1/cp1252 mojibake ("Ã§" -> "ç"), only when the decoded text is
 *     clearly Latin text or typographic punctuation;
 *  2. drops U+FFFD, control, zero-width, bidi, private-use and soft-hyphen characters, and
 *     unpaired surrogates (emoji joiners / variation selectors stay when they belong to an emoji);
 *  3. normalises to NFC ("s" + U+0327 -> "ş", "I" + U+0307 -> "İ");
 *  4. removes orphan combining marks and the dotted-i artefact "i" + U+0307 that default
 *     lowercasing of "İ" leaves behind;
 *  5. collapses stray whitespace (line structure and leading indentation are kept).
 *
 * It never changes letter case and never uses a locale, so Turkish İ/ı are safe. [[wiki links]],
 * #tags, Markdown syntax and real emoji pass through untouched. Every function is idempotent.
 */
object TextSanitizer {

    /** Multi-line text (recognition result, Markdown body). Newlines are kept. */
    fun clean(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        var s = text.replace("\r\n", "\n").replace('\r', '\n')
        s = repairMojibake(s)
        s = stripBadCharacters(s)
        s = Normalizer.normalize(s, Normalizer.Form.NFC)
        s = stripOrphanMarks(s)
        return collapseWhitespace(s)
    }

    /** Single-line text (title, file name, tag). All whitespace, newlines included, becomes one space. */
    private val whitespaceRun = Regex("\\s+")
    private val blankLines = Regex("\n{3,}")
    private val nonIndent = Regex("[^ \\t]")

    fun cleanLine(text: String?): String =
        clean(text).replace(whitespaceRun, " ").trim()

    /**
     * A note or file name turned into tag text: cleaned, no leading '#', spaces become '-'.
     * The caller adds the '#' (as [InboxSyncEngine] does for annotation tags).
     */
    fun tagFromName(name: String?): String =
        cleanLine(name).trimStart('#').trim().replace(' ', '-')

    // ---------------------------------------------------------------- mojibake

    // cp1252 maps bytes 0x80..0x9F to printable characters; mojibake from cp1252 readers uses them.
    private val cp1252Specials: Map<Char, Int> = buildMap {
        val cs = java.nio.charset.Charset.forName("windows-1252")
        for (b in 0x80..0x9F) {
            val c = String(byteArrayOf(b.toByte()), cs).firstOrNull() ?: continue
            if (c != '�' && c.code > 0xFF) put(c, b)
        }
    }

    private fun continuationByte(c: Char): Int? = when {
        c.code in 0x80..0xBF -> c.code
        else -> cp1252Specials[c]
    }

    private fun isLeadChar(c: Char) = c.code in 0xC2..0xF4

    /**
     * Finds runs "lead char + continuation chars" that are UTF-8 bytes shown as Latin-1/cp1252 and
     * decodes them. A run is replaced only if it decodes to valid UTF-8 and the result is Latin
     * letters or typographic punctuation; anything else is left alone.
     */
    internal fun repairMojibake(text: String): String {
        if (text.none { isLeadChar(it) }) return text
        var current = text
        // Text that was mangled twice needs two passes; stop as soon as nothing changes.
        repeat(2) {
            val next = repairOnce(current)
            if (next == current) return current
            current = next
        }
        return current
    }

    private fun repairOnce(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val lead = text[i]
            if (isLeadChar(lead)) {
                val need = when (lead.code) {
                    in 0xC2..0xDF -> 1
                    in 0xE0..0xEF -> 2
                    else -> 3
                }
                val decoded = if (i + need < text.length) tryDecode(text, i, need) else null
                if (decoded != null) {
                    sb.append(decoded)
                    i += need + 1
                    continue
                }
            }
            sb.append(lead)
            i++
        }
        return sb.toString()
    }

    private fun tryDecode(text: String, start: Int, need: Int): String? {
        val bytes = ByteArray(need + 1)
        bytes[0] = text[start].code.toByte()
        for (k in 1..need) {
            val b = continuationByte(text[start + k]) ?: return null
            bytes[k] = b.toByte()
        }
        val decoded = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            return null
        }
        return if (decoded.isNotEmpty() && decoded.all { isLatinOrTypographic(it) }) decoded else null
    }

    private fun isLatinOrTypographic(c: Char): Boolean = when (c.code) {
        in 0x00C0..0x024F -> c.code != 0x00D7 && c.code != 0x00F7
        in 0x1E00..0x1EFF -> true // Latin Extended Additional
        0x00A0, 0x00A7, 0x00A9, 0x00AB, 0x00AE, 0x00B0, 0x00B7, 0x00BB -> true
        in 0x2010..0x2015, in 0x2018..0x201E, 0x2020, 0x2021, 0x2022, 0x2026, 0x2030, 0x2039, 0x203A -> true
        0x20AC, 0x20BA, 0x2122 -> true
        else -> false
    }

    // ------------------------------------------------------------ stripping

    private fun stripBadCharacters(text: String): String {
        val sb = StringBuilder(text.length)
        var prev = -1 // previous kept code point
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val len = Character.charCount(cp)
            i += len
            if (!keep(cp, prev)) continue
            sb.appendCodePoint(cp)
            prev = cp
        }
        return sb.toString()
    }

    private fun keep(cp: Int, prev: Int): Boolean {
        when (cp) {
            '\n'.code, '\t'.code -> return true
            0x0B, 0x0C -> return true // turned into spaces later
            0x85, 0x2028, 0x2029 -> return true // line separators, turned into newlines later
            0xFFFD, 0xFFFE, 0xFFFF, 0xAD, 0x061C, 0x180E, 0xFEFF -> return false
            0x200D -> return isEmojiish(prev) // ZWJ only inside an emoji sequence
            0xFE0F, 0xFE0E -> return isEmojiish(prev) || isKeycapBase(prev)
            0x20E3 -> return prev == 0xFE0F || isKeycapBase(prev)
            in 0xE0020..0xE007F -> return prev == 0x1F3F4 || prev in 0xE0020..0xE007F // flag tags
            in 0x200B..0x200F, in 0x202A..0x202E, in 0x2060..0x206F -> return false
        }
        return when (Character.getType(cp)) {
            Character.CONTROL.toInt(),
            Character.PRIVATE_USE.toInt(),
            Character.SURROGATE.toInt() -> false
            Character.FORMAT.toInt() -> false
            else -> true
        }
    }

    private fun isKeycapBase(cp: Int) = cp == '#'.code || cp == '*'.code || cp in '0'.code..'9'.code

    private fun isEmojiish(cp: Int): Boolean = when (cp) {
        -1 -> false
        0xA9, 0xAE, 0x203C, 0x2049, 0x2122, 0x2139, 0x3030, 0x303D, 0x3297, 0x3299, 0xFE0F, 0x20E3 -> true
        in 0x2190..0x21FF, in 0x2300..0x23FF, in 0x2460..0x24FF, in 0x25A0..0x25FF,
        in 0x2600..0x27BF, in 0x2900..0x297F, in 0x2B00..0x2BFF,
        in 0x1F000..0x1FAFF -> true
        in 0xE0020..0xE007F -> true
        else -> false
    }

    // ------------------------------------------------------- marks, spacing

    private fun stripOrphanMarks(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        var prev = -1
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            val isMark = Character.getType(cp) == Character.NON_SPACING_MARK.toInt() &&
                cp != 0xFE0F && cp != 0xFE0E
            if (isMark) {
                val startOfWord = prev == -1 || Character.isWhitespace(prev)
                // "i" + dot above is what default lowercasing of "İ" leaves; plain "i" is the intent.
                val dottedLowerI = cp == 0x0307 && prev == 'i'.code
                if (startOfWord || dottedLowerI) continue
            }
            sb.appendCodePoint(cp)
            prev = cp
        }
        return sb.toString()
    }

    private fun isSpaceLike(c: Char): Boolean =
        c == ' ' || c == '\t' || c == '\u000B' || c == '\u000C' || c == ' ' || c == ' ' ||
            c in ' '..' ' || c == ' ' || c == ' ' || c == '　'

    private fun collapseWhitespace(text: String): String {
        val normalized = text.replace('\u0085', '\n').replace(' ', '\n').replace(' ', '\n')
        val lines = normalized.split('\n').map { line ->
            // keep leading indentation (Markdown lists/code), squeeze everything after it
            var indentEnd = 0
            while (indentEnd < line.length && isSpaceLike(line[indentEnd])) indentEnd++
            val indent = line.substring(0, indentEnd).replace(' ', ' ').replace(nonIndent, " ")
            val body = line.substring(indentEnd)
            val squeezed = StringBuilder(body.length)
            var lastSpace = false
            for (c in body) {
                if (isSpaceLike(c)) {
                    if (!lastSpace) squeezed.append(' ')
                    lastSpace = true
                } else {
                    squeezed.append(c)
                    lastSpace = false
                }
            }
            val trimmed = squeezed.toString().trimEnd()
            if (trimmed.isEmpty()) "" else indent + trimmed
        }
        return lines.joinToString("\n")
            .replace(blankLines, "\n\n")
            .trim('\n')
    }
}

package com.jizhang.domain

import java.util.Locale

/**
 * Version strings of release tags: "v0.5.1", "0.6.0-beta.1". Numeric segments compare as numbers
 * ("0.10.0" is newer than "0.9.0", a missing segment counts as 0), and a pre-release suffix puts
 * a version below the same version without one, like semver.
 */
object AppVersion {
    private class Parsed(val core: List<Long>, val pre: List<String>)

    /** "v0.5.1" → "0.5.1": what the UI shows after its own "v". */
    fun clean(raw: String): String = raw.trim().removePrefix("v").removePrefix("V")

    /** True when [candidate] is a higher version than [current]; false if either can't be read. */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = parse(candidate) ?: return false
        val b = parse(current) ?: return false
        return compare(a, b) > 0
    }

    private fun parse(raw: String): Parsed? {
        var s = clean(raw)
        val plus = s.indexOf('+')
        if (plus >= 0) s = s.substring(0, plus)
        val dash = s.indexOf('-')
        val corePart = if (dash >= 0) s.substring(0, dash) else s
        val prePart = if (dash >= 0) s.substring(dash + 1) else ""
        if (corePart.isEmpty()) return null
        val core = corePart.split('.').map { seg ->
            if (seg.isEmpty() || seg.length > 9 || !seg.all { it in '0'..'9' }) return null
            seg.toLong()
        }
        return Parsed(core, if (prePart.isEmpty()) emptyList() else prePart.split('.'))
    }

    private fun compare(a: Parsed, b: Parsed): Int {
        for (i in 0 until maxOf(a.core.size, b.core.size)) {
            val d = a.core.getOrElse(i) { 0 }.compareTo(b.core.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        // Same numbers: a release beats its pre-releases.
        if (a.pre.isEmpty() || b.pre.isEmpty()) return b.pre.size.compareTo(a.pre.size).coerceIn(-1, 1)
        for (i in 0 until minOf(a.pre.size, b.pre.size)) {
            val d = comparePre(a.pre[i], b.pre[i])
            if (d != 0) return d
        }
        return a.pre.size.compareTo(b.pre.size)
    }

    /** Numeric identifiers compare as numbers and sit below alphanumeric ones. */
    private fun comparePre(a: String, b: String): Int {
        val x = a.toLongOrNull()
        val y = b.toLongOrNull()
        return when {
            x != null && y != null -> x.compareTo(y)
            x != null -> -1
            y != null -> 1
            else -> a.compareTo(b)
        }
    }
}

/** GitHub release bodies are markdown; the update sheet shows them as plain text. */
object ReleaseNotes {
    private val rule = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")
    private val heading = Regex("""^\s{0,3}#{1,6}\s*""")
    private val headingTail = Regex("""\s+#+\s*$""")
    private val quote = Regex("""^\s*>+\s?""")
    private val bullet = Regex("""^(\s*)[-*+]\s+""")
    private val image = Regex("""!\[([^\]]*)]\([^)]*\)""")
    private val link = Regex("""\[([^\]]+)]\([^)]*\)""")
    private val bold = Regex("""\*\*(.+?)\*\*|__(.+?)__""")
    private val strike = Regex("""~~(.+?)~~""")
    private val italicStar = Regex("""(?<![*\w])\*(?!\s)(.+?)(?<!\s)\*(?![*\w])""")
    private val italicUnder = Regex("""(?<!\w)_(?!\s)(.+?)(?<!\s)_(?!\w)""")
    private val code = Regex("""`+([^`]+)`+""")
    private val tag = Regex("""</?[a-zA-Z][^>]*>""")
    private val blankRun = Regex("""\n{3,}""")

    /** Drops the markup (#, *, backticks, link targets …) and keeps the words; bullets become "• ". */
    fun plain(markdown: String): String {
        val out = ArrayList<String>()
        var fenced = false
        for (raw in markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            val trimmed = raw.trim()
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                fenced = !fenced
                continue
            }
            if (fenced) {
                out += raw.trimEnd()
                continue
            }
            if (rule.matches(raw)) {
                out += ""
                continue
            }
            var line = raw.replace(heading, "").replace(headingTail, "").replace(quote, "")
            line = line.replace(bullet) { it.groupValues[1] + "• " }
            line = line
                .replace(image, "$1")
                .replace(link, "$1")
                .replace(bold) { it.groupValues[1].ifEmpty { it.groupValues[2] } }
                .replace(strike, "$1")
                .replace(italicStar, "$1")
                .replace(italicUnder, "$1")
                .replace(code, "$1")
                .replace(tag, "")
            out += line.trimEnd()
        }
        return out.joinToString("\n").replace(blankRun, "\n\n").trim()
    }
}

/** 12_900_000 → "12.3 MB"; sizes under a megabyte show in KB. */
fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0)
    else -> String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0)
}

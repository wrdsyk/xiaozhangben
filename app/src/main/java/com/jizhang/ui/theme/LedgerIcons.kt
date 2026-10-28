package com.jizhang.ui.theme

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.jizhang.domain.Category

/**
 * The prototype's 24×24 line icons, rebuilt from the same SVG paths so app and design match.
 * Rects and circles are converted to path data with [rrect] and [circle].
 */
object LedgerIcons {
    private fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float) =
        "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 -$r ${r}h-${w - 2 * r}" +
            "a$r $r 0 0 1 -$r -${r}v-${h - 2 * r}a$r $r 0 0 1 $r -${r}z"

    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 -${2 * r} 0z"

    private val paths: Map<String, List<String>> = mapOf(
        "food" to listOf("M4 11h16a8 8 0 0 1-16 0Z", "M9 21h6", "M14 3l-2.5 6M19 4.5L14.5 9"),
        "transit" to listOf(rrect(5f, 3f, 14f, 14f, 3f), "M5 10h14", "M8.5 20.5L10 17M15.5 20.5L14 17", "M9 13.5h.01M15 13.5h.01"),
        "shop" to listOf("M5 8h14l-1.2 12H6.2L5 8Z", "M9 8V6.5a3 3 0 0 1 6 0V8"),
        "daily" to listOf("M3.5 10h17l-1.8 9.2a1 1 0 0 1-1 .8H6.3a1 1 0 0 1-1-.8L3.5 10Z", "M8 10l3-6M16 10l-3-6", "M9.5 13.5v3M14.5 13.5v3"),
        "fun" to listOf("M4 6h16v4a2 2 0 0 0 0 4v4H4v-4a2 2 0 0 0 0-4Z", "M15 7v2M15 11v2M15 15v2"),
        "home" to listOf("M4 10.5L12 4l8 6.5", "M6 9v11h12V9", "M10 20v-5h4v5"),
        "med" to listOf(rrect(4f, 4f, 16f, 16f, 4f), "M12 8.5v7M8.5 12h7"),
        "gift" to listOf(rrect(5f, 3f, 14f, 18f, 2f), "M5 8.5c2.3 1.8 4.6 2.7 7 2.7s4.7-.9 7-2.7", circle(12f, 12.3f, 1.6f)),
        "study" to listOf("M12 6.5C10 5 7 4.5 4 5v13.5c3-.5 6 0 8 1.5 2-1.5 5-2 8-1.5V5c-3-.5-6 0-8 1.5Z", "M12 6.5V20"),
        "other" to listOf(
            rrect(4f, 4f, 6.5f, 6.5f, 1.5f), rrect(13.5f, 4f, 6.5f, 6.5f, 1.5f),
            rrect(4f, 13.5f, 6.5f, 6.5f, 1.5f), rrect(13.5f, 13.5f, 6.5f, 6.5f, 1.5f),
        ),
        "salary" to listOf(rrect(3f, 6f, 18f, 13f, 3f), "M3 10h18", "M15.5 14.5h2.5"),
        "bonus" to listOf(
            rrect(4f, 10f, 16f, 10f, 2f), "M3 7h18v3H3Z", "M12 7v13",
            "M12 7C10.5 4 7.5 3.8 7.5 5.6 7.5 7 10 7 12 7Zm0 0c1.5-3 4.5-3.2 4.5-1.4C16.5 7 14 7 12 7Z",
        ),
        "invest" to listOf("M4 17l5-5 4 3 7-7", "M15 8h5v5"),
        "list" to listOf("M6 3h12v18l-3-2-3 2-3-2-3 2V3Z", "M9 8h6M9 12h6"),
        "chart" to listOf("M4 20h16", "M7 16v-5M12 16V6M17 16v-8"),
        "user" to listOf(circle(12f, 8f, 4f), "M4.5 20.5a7.5 7.5 0 0 1 15 0"),
        "plus" to listOf("M12 5v14M5 12h14"),
        "close" to listOf("M6 6l12 12M18 6L6 18"),
        "backspace" to listOf("M9 5h10a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H9l-6-7 6-7Z", "M12.5 9.5l5 5M17.5 9.5l-5 5"),
        "cal" to listOf(rrect(4f, 5f, 16f, 15f, 2.5f), "M4 10h16M9 3v4M15 3v4"),
        "note" to listOf("M5 19h3.5L19 8.5 15.5 5 5 15.5V19Z", "M13.5 7l3.5 3.5"),
        "chev" to listOf("M6 9l6 6 6-6"),
        "chevr" to listOf("M9 6l6 6-6 6"),
        "chevl" to listOf("M15 6l-6 6 6 6"),
        "check" to listOf("M5 12.5l4.5 4.5L19 7.5"),
        "target" to listOf(circle(12f, 12f, 8f), circle(12f, 12f, 4f), "M12 12h.01"),
        "backup" to listOf("M4 8h16v11a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V8Z", "M4 8l2-4h12l2 4", "M12 17v-6M9.5 13.5L12 11l2.5 2.5"),
        "info" to listOf(circle(12f, 12f, 8.5f), "M12 11v5.5M12 7.8h.01"),
        "trash" to listOf("M5 7h14", "M10 4h4", "M7 7l1 13h8l1-13", "M10.5 11v5M13.5 11v5"),
        "undo" to listOf("M9 7L5 11l4 4", "M5 11h9a5 5 0 0 1 0 10h-2"),
        "search" to listOf(circle(11f, 11f, 6.5f), "M16 16l4.5 4.5"),
        "sort" to listOf("M7 5v14M4 8l3-3 3 3", "M17 19V5M14 16l3 3 3-3"),
        "plus-circle" to listOf(circle(12f, 12f, 8.5f), "M12 8.5v7M8.5 12h7"),
        "trophy" to listOf("M8 4h8v5a4 4 0 0 1-8 0V4Z", "M8 6H5a3 3 0 0 0 3 4M16 6h3a3 3 0 0 1-3 4", "M12 13v4M8.5 20h7M10 17h4"),
        // v0.4: category edit mode, record photos, recurring records.
        "edit" to listOf("M12 20h9", "M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z"),
        "camera" to listOf("M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3l-2.5-3Z", circle(12f, 13f, 3.5f)),
        "image" to listOf(rrect(3f, 3f, 18f, 18f, 3f), circle(9f, 9f, 2f), "M21 15l-3.1-3.1a2 2 0 0 0-2.8 0L6 21"),
        "repeat" to listOf("M17 2l4 4-4 4", "M3 11v-1a4 4 0 0 1 4-4h14", "M7 22l-4-4 4-4", "M21 13v1a4 4 0 0 1-4 4H3"),
        "clock" to listOf(circle(12f, 12f, 9f), "M12 7v5l3 2"),
        "drag" to listOf("M9 6h.01M15 6h.01M9 12h.01M15 12h.01M9 18h.01M15 18h.01"),
        // In-app updates: an arrow down into a tray.
        "update" to listOf("M12 4v11M7.5 10.5L12 15l4.5-4.5", "M5 19.5h14"),
    )

    private val cache = HashMap<Pair<String, Float>, ImageVector>()

    /** Icons are cached per stroke width; the prototype uses 1.9 by default and 2.2–3 for emphasis. */
    fun get(name: String, stroke: Float = 1.9f): ImageVector = cache.getOrPut(name to stroke) {
        val builder = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        (paths[name] ?: IconLibrary.paths[name] ?: paths.getValue("other")).forEach { d ->
            builder.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                stroke = SolidColor(androidx.compose.ui.graphics.Color.Black),
                strokeLineWidth = stroke,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        builder.build()
    }

    fun of(category: Category, stroke: Float = 1.9f): ImageVector = get(category.icon, stroke)

    /** Shared with [IconLibrary] so library icons can use the same rect / circle helpers. */
    internal fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) = rrect(x, y, w, h, r)
    internal fun ring(cx: Float, cy: Float, r: Float) = circle(cx, cy, r)
}

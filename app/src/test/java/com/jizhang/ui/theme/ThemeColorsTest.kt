package com.jizhang.ui.theme

import androidx.compose.ui.graphics.Color
import com.jizhang.data.ThemeId
import com.jizhang.domain.Hue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class ThemeColorsTest {
    private fun channel(v: Float): Double = v.toDouble().let { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }

    private fun luminance(c: Color) = .2126 * channel(c.red) + .7152 * channel(c.green) + .0722 * channel(c.blue)

    /** [fg] drawn over an opaque [bg], the way a translucent text colour lands on the background. */
    private fun over(fg: Color, bg: Color) = Color(
        red = fg.red * fg.alpha + bg.red * (1 - fg.alpha),
        green = fg.green * fg.alpha + bg.green * (1 - fg.alpha),
        blue = fg.blue * fg.alpha + bg.blue * (1 - fg.alpha),
    )

    private fun contrast(fg: Color, bg: Color): Double {
        val a = luminance(over(fg, bg))
        val b = luminance(bg)
        return (maxOf(a, b) + .05) / (minOf(a, b) + .05)
    }

    private fun LedgerColors.backgrounds() = listOf(bg0, bg1, bg2, bg3)

    /** The three prototype palettes are approved as drawn (glacier's gold hue and green sit near 3.7); later themes must clear 4. */
    private fun floor(id: ThemeId) = if (id.ordinal < 3) 3.0 else 4.0

    @Test fun keysAreStableAndUnique() {
        assertEquals(listOf("pearl", "glacier", "dusk"), ThemeId.entries.take(3).map { it.key })
        assertEquals(ThemeId.entries.size, ThemeId.entries.map { it.key }.toSet().size)
        ThemeId.entries.forEach { assertEquals(it, ThemeId.of(it.key)) }
        assertEquals(ThemeId.Pearl, ThemeId.of("no-such-theme"))
        assertEquals(ThemeId.Pearl, ThemeId.of(null))
    }

    @Test fun everyThemeHasTheFullTokenSet() {
        ThemeId.entries.forEach { id ->
            val c = colorsFor(id)
            assertEquals("${id.key} hues", Hue.entries.size, c.hues.size)
            assertTrue("${id.key} darkness", c.darkness == 0f || c.darkness == 1f)
            assertTrue("${id.key} tint", c.tileTintAlpha in .05f..0.3f)
        }
    }

    @Test fun darknessMatchesTheBackground() {
        ThemeId.entries.forEach { id ->
            val c = colorsFor(id)
            val lum = c.backgrounds().map { luminance(it) }
            if (c.isDark) assertTrue("${id.key} should be dark", lum.all { it < .12 })
            else assertTrue("${id.key} should be light", lum.all { it > .6 })
        }
    }

    @Test fun textReadsOnEveryBackground() {
        ThemeId.entries.forEach { id ->
            val c = colorsFor(id)
            c.backgrounds().forEach { bg ->
                assertTrue("${id.key} ink", contrast(c.ink, bg) >= 7.0)
                assertTrue("${id.key} sub", contrast(c.sub, bg) >= 4.5)
                assertTrue("${id.key} income", contrast(c.income, bg) >= floor(id))
            }
        }
    }

    @Test fun accentAndTilesKeepTheirGlyphsReadable() {
        ThemeId.entries.forEach { id ->
            val c = colorsFor(id)
            assertTrue("${id.key} onAccent", contrast(c.onAccent, c.accent) >= 4.5)
            c.hues.forEachIndexed { i, hue ->
                assertTrue("${id.key} hue $i under onTile", contrast(c.onTile, hue) >= floor(id))
            }
        }
    }
}

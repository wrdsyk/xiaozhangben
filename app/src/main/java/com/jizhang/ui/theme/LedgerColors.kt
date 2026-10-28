package com.jizhang.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.jizhang.data.ThemeId
import com.jizhang.domain.Category
import com.jizhang.domain.Hue

/**
 * Every colour a screen needs, one set per theme. Pearl, glacier and dusk come straight from the
 * approved prototype (D-Theme); the rest follow the same structure. A theme switch swaps the whole
 * set in one step (see [LedgerTheme]); nothing interpolates between two of these.
 */
@Immutable
data class LedgerColors(
    val ink: Color,
    val sub: Color,
    val label: Color,
    val accent: Color,
    val onAccent: Color,
    val onTile: Color,
    val segOn: Color,
    val segOff: Color,
    val income: Color,
    val track: Color,
    val placeholder: Color,
    /** Background of the operator keys. */
    val opKey: Color,
    val hair: Color,
    val chip: Color,
    val chipStrong: Color,
    val chipEdge: Color,
    /** The nav bar's sliding lens. */
    val lens: Color,
    val sheet: Color,
    val glassTop: Color,
    val glassBottom: Color,
    val rimStrong: Color,
    val rimEnd: Color,
    val rimMid: Color,
    val glint: Color,
    val shadow: Color,
    val shadowSoft: Color,
    val accentShadow: Color,
    val sheen: Color,
    val ring1: Color,
    val ring2: Color,
    val rowGlow: Color,
    val bg0: Color,
    val bg1: Color,
    val bg2: Color,
    val bg3: Color,
    val orb1: Color,
    val orb2: Color,
    val orb3: Color,
    val silkStart: Color,
    val silkMid: Color,
    val silkEnd: Color,
    val hues: List<Color>,
    /**
     * The merged "其余 N 类" slice of the category donut: a muted [sub], so it never matches one of
     * [hues] (in particular not 其他's grey) and follows every theme without a value of its own.
     */
    val mergedSlice: Color = sub.copy(alpha = .55f),
    val tileTintAlpha: Float,
    /** 0 for light themes, 1 for dark ones; scales the few white highlights that must fade on dark. */
    val darkness: Float,
) {
    val isDark: Boolean get() = darkness > 0.5f

    fun hue(h: Hue): Color = hues[h.ordinal]

    fun tint(h: Hue): Color = hue(h).copy(alpha = tileTintAlpha)

    /** A category's colour: the one the user picked, else its theme hue. */
    fun hue(c: Category): Color = c.color?.let { Color(it) } ?: hue(c.hue)

    fun tint(c: Category): Color = hue(c).copy(alpha = tileTintAlpha)
}

private fun c(hex: Long, alpha: Float = 1f) = Color(0xFF000000 or hex).copy(alpha = alpha)

val Pearl = LedgerColors(
    ink = c(0x1C1C1E), sub = c(0x5B5B63), label = c(0x3A3A40), accent = c(0x1C1C1E),
    onAccent = c(0xFFFFFF), onTile = c(0xFFFFFF), segOn = c(0x1C1C1E), segOff = c(0x5B5B63),
    income = c(0x3E7A5E), track = c(0x1C1C1E, .08f), placeholder = c(0x1C1C1E, .2f),
    opKey = c(0x1C1C1E, .06f), hair = c(0x1C1C1E, .08f),
    chip = c(0xFFFFFF, .42f), chipStrong = c(0xFFFFFF, .5f), chipEdge = c(0xFFFFFF, .8f),
    lens = c(0xFFFFFF, .6f), sheet = c(0xF3F0EE, .6f),
    glassTop = c(0xFFFFFF, .36f), glassBottom = c(0xFFFFFF, .12f),
    rimStrong = c(0xFFFFFF), rimEnd = c(0xFFFFFF, .85f), rimMid = c(0xFFFFFF, .35f),
    glint = c(0xFFFFFF, .5f),
    shadow = c(0x191E32, .3f), shadowSoft = c(0x191E32, .12f), accentShadow = c(0x191E32, .6f),
    sheen = c(0xFFFFFF, .22f),
    ring1 = c(0xE2C899), ring2 = c(0x9C7A45), rowGlow = c(0xD8BC8C, .38f),
    bg0 = c(0xF5F2EE), bg1 = c(0xF0EDEF), bg2 = c(0xEBEBEF), bg3 = c(0xE6E9EF),
    orb1 = c(0xE8D3B5, .9f), orb2 = c(0xD8C6D3, .75f), orb3 = c(0xC7D3E3, .85f),
    silkStart = c(0xFFFFFF, 0f), silkMid = c(0xFFFFFF, .57f), silkEnd = c(0xFFFFFF, 0f),
    hues = listOf(
        c(0xA0653A), c(0x3F5E8C), c(0x8C4A5E), c(0x4F7A63), c(0x6B5191),
        c(0x8F7340), c(0x3E7C80), c(0x9A4A42), c(0x485A8F), c(0x6B6E75),
    ),
    tileTintAlpha = .12f,
    darkness = 0f,
)

val Glacier = Pearl.copy(
    ink = c(0x0F1B2D), sub = c(0x526070), label = c(0x2C3A4C), accent = c(0x0B4F8A),
    segOn = c(0x0F1B2D), segOff = c(0x526070), income = c(0x2F8068),
    track = c(0x0F1B2D, .08f), placeholder = c(0x0F1B2D, .2f), opKey = c(0x0F1B2D, .06f), hair = c(0x0F1B2D, .08f),
    sheet = c(0xECF3F9, .6f), glassTop = c(0xFFFFFF, .34f), glassBottom = c(0xF4FAFF, .1f),
    shadow = c(0x122642, .3f), shadowSoft = c(0x122642, .12f), accentShadow = c(0x0B3C6E, .6f),
    ring1 = c(0x8AD0E0), ring2 = c(0x0B4F8A), rowGlow = c(0x7CC6D8, .35f),
    bg0 = c(0xF0F5F9), bg1 = c(0xE9F0F7), bg2 = c(0xE3EBF4), bg3 = c(0xDEE7F1),
    orb1 = c(0xBEE4E8, .9f), orb2 = c(0xC8D2F4, .8f), orb3 = c(0xCDEBDD, .75f),
    silkMid = c(0xFFFFFF, .62f),
    hues = listOf(
        c(0xB0643A), c(0x2F6FB0), c(0xA2496B), c(0x2F8068), c(0x6A55B0),
        c(0xA07A2E), c(0x1F8794), c(0xB0473F), c(0x3D57A8), c(0x5C6675),
    ),
)

val Dusk = LedgerColors(
    ink = c(0xF4F5FA), sub = c(0xE4E8F5, .72f), label = c(0xF0F2FA, .86f), accent = c(0xF3D6A4),
    onAccent = c(0x1A1533), onTile = c(0x1A1533), segOn = c(0x1A1533), segOff = c(0xF0F2FA, .8f),
    income = c(0x8EE0BE), track = c(0xFFFFFF, .12f), placeholder = c(0xF4F5FA, .25f),
    opKey = c(0x05081E, .25f), hair = c(0xFFFFFF, .1f),
    chip = c(0xFFFFFF, .07f), chipStrong = c(0xFFFFFF, .08f), chipEdge = c(0xFFFFFF, .16f),
    lens = c(0xFFFFFF, .14f), sheet = c(0x101430, .55f),
    glassTop = c(0xFFFFFF, .14f), glassBottom = c(0xFFFFFF, .04f),
    rimStrong = c(0xFFFFFF, .75f), rimEnd = c(0xFFFFFF, .5f), rimMid = c(0xFFFFFF, .18f),
    glint = c(0xFFFFFF, .18f),
    shadow = c(0x000000, .6f), shadowSoft = c(0x000000, .3f), accentShadow = c(0xF3BE8C, .55f),
    sheen = c(0xFFFFFF, .3f),
    ring1 = c(0xF3D6A4), ring2 = c(0xE58A7A), rowGlow = c(0xF3D6A4, .22f),
    bg0 = c(0x0D1530), bg1 = c(0x1A2250), bg2 = c(0x33295A), bg3 = c(0x5A3A58),
    orb1 = c(0x3E5BE0, .55f), orb2 = c(0xC2668A, .5f), orb3 = c(0xE9A066, .5f),
    silkStart = c(0xFFD9B8, 0f), silkMid = c(0xFFE4CC, .3f), silkEnd = c(0xF5B3CF, 0f),
    hues = listOf(
        c(0xFFB98A), c(0x8FB8FF), c(0xFF9DBA), c(0x8EE0BE), c(0xC3A8FF),
        c(0xF7D58C), c(0x86DDE3), c(0xFF9E95), c(0xA9B4FF), c(0xC9CDD8),
    ),
    tileTintAlpha = .16f,
    darkness = 1f,
)

/** 抹茶: cream paper washed with soft matcha green; a deep leaf green carries the accent. */
val Matcha = LedgerColors(
    ink = c(0x1E2A22), sub = c(0x4F5E54), label = c(0x35443B), accent = c(0x2F6B4A),
    onAccent = c(0xFFFFFF), onTile = c(0xFFFFFF), segOn = c(0x1E2A22), segOff = c(0x4F5E54),
    income = c(0x2F7A4D), track = c(0x1E2A22, .08f), placeholder = c(0x1E2A22, .2f),
    opKey = c(0x1E2A22, .06f), hair = c(0x1E2A22, .08f),
    chip = c(0xFFFFFF, .42f), chipStrong = c(0xFFFFFF, .5f), chipEdge = c(0xFFFFFF, .8f),
    lens = c(0xFFFFFF, .6f), sheet = c(0xF1F4EA, .6f),
    glassTop = c(0xFFFFFF, .36f), glassBottom = c(0xFBFFF4, .12f),
    rimStrong = c(0xFFFFFF), rimEnd = c(0xFFFFFF, .85f), rimMid = c(0xFFFFFF, .35f),
    glint = c(0xFFFFFF, .5f),
    shadow = c(0x1B2A20, .3f), shadowSoft = c(0x1B2A20, .12f), accentShadow = c(0x1F4D33, .6f),
    sheen = c(0xFFFFFF, .22f),
    ring1 = c(0xC4DFA6), ring2 = c(0x3F7A52), rowGlow = c(0xB5D6A0, .38f),
    bg0 = c(0xF5F6EC), bg1 = c(0xEEF2E3), bg2 = c(0xE6EEDD), bg3 = c(0xDDE9D7),
    orb1 = c(0xD2E5B2, .9f), orb2 = c(0xEBDFBC, .75f), orb3 = c(0xB9DBC6, .85f),
    silkStart = c(0xFFFFFF, 0f), silkMid = c(0xFFFFFF, .57f), silkEnd = c(0xFFFFFF, 0f),
    hues = listOf(
        c(0xA35F2F), c(0x3B6A8C), c(0x9A4B62), c(0x3F7A4F), c(0x6A5A94),
        c(0x857330), c(0x2F7F78), c(0xA3473E), c(0x45609A), c(0x6B7068),
    ),
    tileTintAlpha = .12f,
    darkness = 0f,
)

/** 樱粉: blush and peach paper, lilac at the edges; a dusty rose accent. */
val Sakura = LedgerColors(
    ink = c(0x2B1D22), sub = c(0x625057), label = c(0x45333A), accent = c(0xA43C5E),
    onAccent = c(0xFFFFFF), onTile = c(0xFFFFFF), segOn = c(0x2B1D22), segOff = c(0x625057),
    income = c(0x2A745A), track = c(0x2B1D22, .08f), placeholder = c(0x2B1D22, .2f),
    opKey = c(0x2B1D22, .06f), hair = c(0x2B1D22, .08f),
    chip = c(0xFFFFFF, .42f), chipStrong = c(0xFFFFFF, .5f), chipEdge = c(0xFFFFFF, .8f),
    lens = c(0xFFFFFF, .6f), sheet = c(0xF9EEEF, .6f),
    glassTop = c(0xFFFFFF, .38f), glassBottom = c(0xFFF6F8, .12f),
    rimStrong = c(0xFFFFFF), rimEnd = c(0xFFFFFF, .85f), rimMid = c(0xFFFFFF, .35f),
    glint = c(0xFFFFFF, .5f),
    shadow = c(0x3A1F2B, .3f), shadowSoft = c(0x3A1F2B, .12f), accentShadow = c(0x8E2F4E, .6f),
    sheen = c(0xFFFFFF, .22f),
    ring1 = c(0xF6C3CF), ring2 = c(0xC4547A), rowGlow = c(0xF2B8C6, .4f),
    bg0 = c(0xFBF4F1), bg1 = c(0xF9EDEB), bg2 = c(0xF5E5E8), bg3 = c(0xF0DEE6),
    orb1 = c(0xFFD6C4, .9f), orb2 = c(0xF7BFD2, .7f), orb3 = c(0xE3D0F1, .8f),
    silkStart = c(0xFFFFFF, 0f), silkMid = c(0xFFFFFF, .57f), silkEnd = c(0xFFFFFF, 0f),
    hues = listOf(
        c(0xA85A33), c(0x4A6A9A), c(0xA2415F), c(0x457A5D), c(0x755699),
        c(0x8F7036), c(0x387A7D), c(0xA84343), c(0x5662A8), c(0x7A6E74),
    ),
    tileTintAlpha = .12f,
    darkness = 0f,
)

/** 深海: deep teal fading to sea-green, glass with a faint cyan cast, a bioluminescent aqua accent. */
val Abyss = LedgerColors(
    ink = c(0xEAF6F8), sub = c(0xCDE7EC, .8f), label = c(0xE2F2F4, .86f), accent = c(0x6EE0D2),
    onAccent = c(0x04242C), onTile = c(0x04242C), segOn = c(0x04242C), segOff = c(0xDCEEF2, .8f),
    income = c(0x9AEFB4), track = c(0xFFFFFF, .12f), placeholder = c(0xEAF6F8, .25f),
    opKey = c(0x01080F, .28f), hair = c(0xFFFFFF, .1f),
    chip = c(0xFFFFFF, .07f), chipStrong = c(0xFFFFFF, .08f), chipEdge = c(0xFFFFFF, .16f),
    lens = c(0xFFFFFF, .14f), sheet = c(0x06202B, .55f),
    glassTop = c(0xE4FAFF, .14f), glassBottom = c(0xE4FAFF, .04f),
    rimStrong = c(0xFFFFFF, .75f), rimEnd = c(0xFFFFFF, .5f), rimMid = c(0xFFFFFF, .18f),
    glint = c(0xFFFFFF, .18f),
    shadow = c(0x000000, .6f), shadowSoft = c(0x000000, .3f), accentShadow = c(0x3FD0C0, .5f),
    sheen = c(0xFFFFFF, .3f),
    ring1 = c(0x7FE8D8), ring2 = c(0x3E9BE0), rowGlow = c(0x6EE0D2, .2f),
    bg0 = c(0x04141D), bg1 = c(0x082A39), bg2 = c(0x0C3D4B), bg3 = c(0x135152),
    orb1 = c(0x1FA6BC, .45f), orb2 = c(0x2F63D6, .45f), orb3 = c(0x3CD9B4, .32f),
    silkStart = c(0xB8F4EE, 0f), silkMid = c(0xA6EEEA, .24f), silkEnd = c(0x8CD6F6, 0f),
    hues = listOf(
        c(0xFFB58A), c(0x7FB6FF), c(0xFF9FC0), c(0x7EE3B8), c(0xBBA6FF),
        c(0xF2D58A), c(0x6FDCE0), c(0xFF9C94), c(0x9DB0FF), c(0xB9CBD3),
    ),
    tileTintAlpha = .16f,
    darkness = 1f,
)

/**
 * 墨夜: neutral graphite on near-black. The gradient starts at almost pure black so OLED pixels
 * stay off, the accent is plain off-white (pearl's ink, inverted) and only the category hues
 * carry colour. Keys and chips lift with white instead of darkening, which would vanish on black.
 */
val Ink = LedgerColors(
    ink = c(0xF2F2F4), sub = c(0xD6D8DF, .68f), label = c(0xEDEDF1, .85f), accent = c(0xF2F2F4),
    onAccent = c(0x0A0A0C), onTile = c(0x0A0A0C), segOn = c(0x0A0A0C), segOff = c(0xEDEDF1, .78f),
    income = c(0x8FE0B4), track = c(0xFFFFFF, .12f), placeholder = c(0xF2F2F4, .25f),
    opKey = c(0xFFFFFF, .08f), hair = c(0xFFFFFF, .1f),
    chip = c(0xFFFFFF, .06f), chipStrong = c(0xFFFFFF, .09f), chipEdge = c(0xFFFFFF, .15f),
    lens = c(0xFFFFFF, .14f), sheet = c(0x0B0B0D, .6f),
    glassTop = c(0xFFFFFF, .12f), glassBottom = c(0xFFFFFF, .035f),
    rimStrong = c(0xFFFFFF, .65f), rimEnd = c(0xFFFFFF, .42f), rimMid = c(0xFFFFFF, .14f),
    glint = c(0xFFFFFF, .15f),
    shadow = c(0x000000, .7f), shadowSoft = c(0x000000, .4f), accentShadow = c(0xFFFFFF, .22f),
    sheen = c(0xFFFFFF, .3f),
    ring1 = c(0xF2F2F4), ring2 = c(0x8E909B), rowGlow = c(0xFFFFFF, .1f),
    bg0 = c(0x040405), bg1 = c(0x0F0F12), bg2 = c(0x16161A), bg3 = c(0x1E1E24),
    orb1 = c(0x555B6B, .32f), orb2 = c(0x3A3D49, .5f), orb3 = c(0x6A6470, .26f),
    silkStart = c(0xFFFFFF, 0f), silkMid = c(0xFFFFFF, .08f), silkEnd = c(0xFFFFFF, 0f),
    hues = listOf(
        c(0xF7B38A), c(0x8AB4F0), c(0xF29DB8), c(0x88D9B4), c(0xB9A6F2),
        c(0xE8CF8E), c(0x7ED3D6), c(0xF29C94), c(0xA2AEF2), c(0xBFC2CC),
    ),
    tileTintAlpha = .15f,
    darkness = 1f,
)

fun colorsFor(theme: ThemeId): LedgerColors = when (theme) {
    ThemeId.Pearl -> Pearl
    ThemeId.Glacier -> Glacier
    ThemeId.Dusk -> Dusk
    ThemeId.Matcha -> Matcha
    ThemeId.Sakura -> Sakura
    ThemeId.Abyss -> Abyss
    ThemeId.Ink -> Ink
}

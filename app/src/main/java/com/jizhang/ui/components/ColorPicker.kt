package com.jizhang.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jizhang.ui.theme.LedgerTheme
import com.jizhang.ui.theme.numberStyle

/** Thumb radius; slider values map onto the track between the two thumb-radius insets. */
private val Thumb = 11.dp

/**
 * Inline colour picker for a custom category colour: a saturation / brightness palette with
 * R / G / B sliders beside it, then a hue strip and a hex field. It keeps its own HSV so the
 * hue survives a drag into grey or black, and reports every change through [onChange].
 * The gradients are brushes on a picker, not a chart.
 */
@Composable
fun ColorPicker(color: Color, onChange: (Color) -> Unit, modifier: Modifier = Modifier) {
    val argb = color.toArgb()
    var held by remember { mutableStateOf(Hsv.of(argb)) }
    // A colour set from outside re-seeds the picker; our own echoes match and keep the held hue.
    val hsv = if (held.argb == argb) held else Hsv.of(argb)
    fun set(next: Hsv) {
        held = next
        onChange(Color(next.argb))
    }
    /** From RGB / hex: greys and black have no hue of their own, so keep the current one. */
    fun setArgb(value: Int) {
        val next = Hsv.of(value)
        set(
            next.copy(
                h = if (next.s == 0f || next.v == 0f) hsv.h else next.h,
                s = if (next.v == 0f) hsv.s else next.s,
            ),
        )
    }
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().height(144.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Palette(hsv, Modifier.weight(1f).fillMaxHeight()) { s, v -> set(hsv.copy(s = s, v = v)) }
            Column(Modifier.width(150.dp)) {
                Channel("R", r, { Color(it, g, b) }) { setArgb(rgb(it, g, b)) }
                Channel("G", g, { Color(r, it, b) }) { setArgb(rgb(r, it, b)) }
                Channel("B", b, { Color(r, g, it) }) { setArgb(rgb(r, g, it)) }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GradientSlider(
                value = hsv.h / 360f,
                colors = HueStops,
                thumb = Color.hsv(hsv.h.coerceIn(0f, 359.9f), 1f, 1f),
                label = "色相",
                modifier = Modifier.weight(1f),
            ) { set(hsv.copy(h = it * 360f)) }
            HexField(argb, Modifier.width(100.dp), ::setArgb)
        }
    }
}

/** Saturation left → right, brightness top → bottom, for the current hue. */
@Composable
private fun Palette(hsv: Hsv, modifier: Modifier, onPick: (s: Float, v: Float) -> Unit) {
    val c = LedgerTheme.colors
    val pure = Color.hsv(hsv.h.coerceIn(0f, 359.9f), 1f, 1f)
    Box(
        modifier
            .track { x, y -> onPick(x, 1f - y) }
            .semantics { contentDescription = "调色盘" }
            .drawBehind {
                val corner = CornerRadius(14.dp.toPx())
                drawRoundRect(Brush.horizontalGradient(listOf(Color.White, pure)), cornerRadius = corner)
                drawRoundRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)), cornerRadius = corner)
                drawRoundRect(c.hair, cornerRadius = corner, style = Stroke(1.dp.toPx()))
                thumb(Offset(hsv.s * size.width, (1f - hsv.v) * size.height), Color(hsv.argb))
            },
    )
}

/** One RGB channel: letter, a slider whose track shows what the channel does, the 0–255 value. */
@Composable
private fun Channel(name: String, value: Int, mix: (Int) -> Color, onChange: (Int) -> Unit) {
    val c = LedgerTheme.colors
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.sub, modifier = Modifier.width(14.dp))
        GradientSlider(
            value = value / 255f,
            colors = listOf(mix(0), mix(255)),
            thumb = mix(value),
            label = name,
            modifier = Modifier.weight(1f),
        ) { onChange((it * 255f + .5f).toInt().coerceIn(0, 255)) }
        Text(
            value.toString(),
            style = numberStyle(12.sp, FontWeight.Normal, 0f),
            color = c.label,
            textAlign = TextAlign.End,
            modifier = Modifier.width(28.dp),
        )
    }
}

/** A 48dp-tall slider: rounded gradient track, white-ringed thumb filled with [thumb]. */
@Composable
private fun GradientSlider(
    value: Float,
    colors: List<Color>,
    thumb: Color,
    label: String,
    modifier: Modifier,
    onChange: (Float) -> Unit,
) {
    val c = LedgerTheme.colors
    Box(
        modifier
            .height(48.dp)
            .track(insetX = Thumb) { x, _ -> onChange(x) }
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
                setProgress { onChange(it.coerceIn(0f, 1f)); true }
            }
            .drawBehind {
                val inset = Thumb.toPx()
                val h = 10.dp.toPx()
                val top = (size.height - h) / 2
                val left = inset - h / 2
                val corner = CornerRadius(h / 2)
                val track = Size(size.width - 2 * left, h)
                drawRoundRect(Brush.horizontalGradient(colors, startX = inset, endX = size.width - inset), Offset(left, top), track, corner)
                drawRoundRect(c.hair, Offset(left, top), track, corner, style = Stroke(1.dp.toPx()))
                thumb(Offset(inset + value.coerceIn(0f, 1f) * (size.width - 2 * inset), size.height / 2), thumb)
            },
    )
}

/** Six hex digits; a complete value applies at once, and drags elsewhere rewrite it. */
@Composable
private fun HexField(argb: Int, modifier: Modifier, onColor: (Int) -> Unit) {
    val c = LedgerTheme.colors
    val focus = LocalFocusManager.current
    val hex = "%06X".format(argb and 0xFFFFFF)
    var text by remember { mutableStateOf(hex) }
    // Only a change of colour rewrites the field, so half-typed digits survive recomposition.
    LaunchedEffect(hex) { if (text != hex) text = hex }
    val shape = RoundedCornerShape(14.dp)
    BasicTextField(
        value = text,
        onValueChange = { raw ->
            val t = raw.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }.take(6)
            text = t
            if (t.length == 6) onColor(0xFF shl 24 or t.toInt(16))
        },
        singleLine = true,
        textStyle = numberStyle(15.sp, FontWeight.Normal, .02f).copy(color = c.ink),
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        modifier = modifier.height(48.dp).semantics { contentDescription = "十六进制颜色" },
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize().padding(vertical = 4.dp), contentAlignment = Alignment.CenterStart) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .background(c.opKey)
                        .border(1.dp, c.hair, shape)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("#", style = numberStyle(15.sp, FontWeight.Normal, 0f), color = c.sub, modifier = Modifier.padding(end = 3.dp))
                    Box(Modifier.weight(1f)) { inner() }
                }
            }
        },
    )
}

/** Press-and-drag reporting the pointer as fractions of the bounds (x between the [insetX]s). */
@Composable
private fun Modifier.track(insetX: Dp = 0.dp, onPos: (x: Float, y: Float) -> Unit): Modifier {
    val latest by rememberUpdatedState(onPos)
    return pointerInput(insetX) {
        val inset = insetX.toPx()
        fun report(p: Offset) = latest(
            ((p.x - inset) / (size.width - 2 * inset).coerceAtLeast(1f)).coerceIn(0f, 1f),
            (p.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f),
        )
        awaitEachGesture {
            val down = awaitFirstDown()
            down.consume()
            report(down.position)
            // Consuming every move keeps a scrolling parent from taking the drag.
            drag(down.id) {
                it.consume()
                report(it.position)
            }
        }
    }
}

private fun DrawScope.thumb(center: Offset, fill: Color) {
    drawCircle(Color.Black.copy(alpha = .22f), Thumb.toPx() + .5.dp.toPx(), center, style = Stroke(1.dp.toPx()))
    drawCircle(Color.White, Thumb.toPx() - 1.5.dp.toPx(), center, style = Stroke(3.dp.toPx()))
    drawCircle(fill, Thumb.toPx() - 3.dp.toPx(), center)
}

private fun rgb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

private val HueStops = listOf(0f, 60f, 120f, 180f, 240f, 300f, 0f).map { Color.hsv(it, 1f, 1f) }

/** Hue 0–360, saturation and value 0–1. */
private data class Hsv(val h: Float, val s: Float, val v: Float) {
    val argb: Int get() = android.graphics.Color.HSVToColor(floatArrayOf(h.coerceIn(0f, 359.99f), s, v))

    companion object {
        fun of(argb: Int): Hsv {
            val out = FloatArray(3)
            android.graphics.Color.colorToHSV(argb, out)
            return Hsv(out[0], out[1], out[2])
        }
    }
}

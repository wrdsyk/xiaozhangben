package com.jizhang.ui.forms

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jizhang.domain.Category
import com.jizhang.domain.EntryKind
import com.jizhang.domain.Hue
import com.jizhang.ui.components.ButtonKind
import com.jizhang.ui.components.CategoryTile
import com.jizhang.ui.components.ColorPicker
import com.jizhang.ui.components.FormActions
import com.jizhang.ui.components.FormButton
import com.jizhang.ui.components.FormField
import com.jizhang.ui.components.FormLabel
import com.jizhang.ui.components.FormTitle
import com.jizhang.ui.components.Glyph
import com.jizhang.ui.components.tap
import com.jizhang.ui.theme.IconLibrary
import com.jizhang.ui.theme.LedgerIcons
import com.jizhang.ui.theme.LedgerTheme
import com.jizhang.ui.theme.Motion
import com.jizhang.ui.theme.rememberHaptics
import kotlinx.coroutines.delay

private const val NAME_MAX = 6
private const val ICON_COLUMNS = 6
private const val SWATCH_COLUMNS = 6

/**
 * Create a custom bill category or edit any category: name (up to 6 characters), colour and
 * icon, with a live tile preview. The last colour swatch is the user's own colour (palette +
 * RGB). Custom categories can be deleted (two taps; the view model moves their records to
 * "其他"); an edited built-in offers "恢复默认" instead. The icon picker takes whatever height
 * the sheet has left (at most ~300dp) and scrolls.
 */
@Composable
fun ColumnScope.CategoryForm(
    existing: Category?,
    kind: EntryKind,
    /** Labels already used by other categories of this kind; a duplicate name can't be saved. */
    taken: Set<String> = emptySet(),
    /** [color] is the user's own ARGB colour, or null for the theme [hue]. */
    onSave: (name: String, icon: String, hue: Hue, color: Int?) -> Unit,
    /** Custom categories only. */
    onDelete: (() -> Unit)?,
    /** Built-ins that differ from their original: back to the original name, icon and colour. */
    onReset: (() -> Unit)? = null,
    close: () -> Unit,
) {
    val c = LedgerTheme.colors
    val haptics = rememberHaptics()
    val focus = LocalFocusManager.current
    val groups = IconLibrary.groups
    var name by remember { mutableStateOf(existing?.label ?: "") }
    var icon by remember { mutableStateOf(existing?.icon ?: groups.getOrNull(1)?.icons?.firstOrNull() ?: "other") }
    var hue by remember { mutableStateOf(existing?.hue ?: Hue.Fun) }
    /** The custom colour while the custom swatch is selected; null means the theme [hue]. */
    var color by remember { mutableStateOf(existing?.color) }
    /** Last custom colour, so leaving for a theme hue and coming back keeps it. */
    var lastCustom by remember { mutableStateOf(existing?.color) }
    var picking by remember { mutableStateOf(false) }
    var arming by remember { mutableStateOf(false) }
    LaunchedEffect(arming) {
        if (arming) {
            delay(3000)
            arming = false
        }
    }
    val trimmed = name.trim()
    val duplicate = trimmed in taken
    val valid = trimmed.isNotEmpty() && trimmed.length <= NAME_MAX && !duplicate
    val save = { if (valid) { onSave(trimmed, icon, hue, color); close() } }
    val fill = color?.let { Color(it) } ?: c.hue(hue)

    FormTitle(
        if (existing == null) "新建分类" else "编辑分类",
        when {
            duplicate -> "已经有叫「$trimmed」的分类了，换个名字吧"
            trimmed.length > NAME_MAX -> "名字最多 $NAME_MAX 个字"
            existing == null -> "名字最多 $NAME_MAX 个字，再挑个颜色和图标"
            existing.custom -> "删除后，它的账单会移到「其他」"
            onReset != null -> "改过的内置分类，随时可以恢复默认"
            else -> "内置分类也能改名字、颜色和图标"
        },
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        TilePreview(trimmed, kind, icon, fill, bumpOn = listOf(icon, hue, color == null))
        FormField(
            name, { name = it.take(NAME_MAX * 2) },
            placeholder = "比如：宠物",
            modifier = Modifier.weight(1f),
            autoFocus = existing == null,
            isError = trimmed.length > NAME_MAX || duplicate,
            // Colour and icon still follow, so Done only drops the keyboard to make room for them.
            onDone = { focus.clearFocus() },
        )
    }
    ColorSection(
        selected = hue,
        custom = color,
        picking = picking,
        onPick = {
            focus.clearFocus()
            if (it != hue || color != null) {
                haptics.tick()
                hue = it
                color = null
                picking = false
            }
        },
        onCustom = {
            focus.clearFocus()
            if (color == null) {
                haptics.tick()
                // Starts from the colour the tile has now, so the switch itself changes nothing.
                color = lastCustom ?: c.hue(hue).toArgb()
                picking = true
            } else {
                picking = !picking
            }
        },
        onCustomChange = {
            color = it
            lastCustom = it
        },
    )
    IconPicker(
        groups, icon, fill,
        Modifier.weight(1f, fill = false).heightIn(max = 300.dp),
    ) {
        focus.clearFocus()
        if (it != icon) {
            haptics.tick()
            icon = it
        }
    }
    when {
        onDelete != null -> Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FormButton(if (arming) "确认删除" else "删除", ButtonKind.Danger, Modifier.weight(1.1f)) {
                if (arming) {
                    onDelete()
                    close()
                } else {
                    haptics.tick()
                    arming = true
                }
            }
            FormButton("取消", ButtonKind.Secondary, Modifier.weight(1f), onClick = close)
            FormButton("保存", ButtonKind.Primary, Modifier.weight(1.3f), enabled = valid, onClick = save)
        }
        onReset != null -> Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FormButton("恢复默认", ButtonKind.Secondary, Modifier.weight(1.2f)) {
                onReset()
                close()
            }
            FormButton("取消", ButtonKind.Secondary, Modifier.weight(1f), onClick = close)
            FormButton("保存", ButtonKind.Primary, Modifier.weight(1.3f), enabled = valid, onClick = save)
        }
        else -> FormActions(primary = "保存", primaryEnabled = valid, onPrimary = save, onSecondary = close)
    }
}

/** The tile as the add sheet's grid will show it (selected style), name underneath. */
@Composable
private fun TilePreview(name: String, kind: EntryKind, icon: String, color: Color, bumpOn: Any) {
    val c = LedgerTheme.colors
    val fill by animateColorAsState(color, Motion.fade(), label = "previewHue")
    val bump = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    // Not on every palette drag: only a new icon, a new swatch or switching to / from custom.
    LaunchedEffect(bumpOn) {
        if (first) {
            first = false
            return@LaunchedEffect
        }
        bump.snapTo(.86f)
        bump.animateTo(1f, Motion.layout())
    }
    Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CategoryTile(
            Category("preview", name, kind, Hue.Other, icon, custom = true),
            54.dp, 19.dp, iconSize = 25.dp,
            background = fill,
            glyph = c.onTile,
            modifier = Modifier.graphicsLayer {
                scaleX = bump.value
                scaleY = bump.value
            },
        )
        Text(
            name.ifEmpty { "分类名" },
            fontSize = 12.sp, fontWeight = FontWeight.Medium,
            color = if (name.isEmpty()) c.placeholder else c.label,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

private val Rainbow = listOf(0f, 60f, 120f, 180f, 240f, 300f, 0f).map { Color.hsv(it, .62f, .92f) }

/**
 * The ten theme hues plus a last "自定义" swatch, two rows of six; the chosen one gets a ring.
 * The custom swatch is a rainbow until chosen, then the user's colour; tapping it again folds
 * the palette away (or back) to give the icons room.
 */
@Composable
private fun ColorSection(
    selected: Hue,
    custom: Int?,
    picking: Boolean,
    onPick: (Hue) -> Unit,
    onCustom: () -> Unit,
    onCustomChange: (Int) -> Unit,
) {
    val c = LedgerTheme.colors
    // Holds the last colour so the picker still has one while it folds away.
    val shown = remember { intArrayOf(custom ?: 0) }
    if (custom != null) shown[0] = custom
    val cells: List<Hue?> = Hue.entries + null
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FormLabel(if (custom != null) "颜色 · 自定义" else "颜色")
        cells.chunked(SWATCH_COLUMNS).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { h ->
                    val on = if (h == null) custom != null else custom == null && h == selected
                    val tone = if (h == null) custom?.let { Color(it) } else c.hue(h)
                    val ring by animateFloatAsState(if (on) 1f else 0f, Motion.fade(), label = "hueRing")
                    val grow by animateFloatAsState(if (on) 1f else 0f, Motion.layout(), label = "hueGrow")
                    Box(
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .tap(label = if (h == null) "自定义颜色" else "颜色 ${h.ordinal + 1}") { if (h == null) onCustom() else onPick(h) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(38.dp)
                                .graphicsLayer { alpha = ring }
                                .then(
                                    if (tone != null) Modifier.border(2.dp, tone, CircleShape)
                                    else Modifier.border(2.dp, Brush.sweepGradient(Rainbow), CircleShape),
                                ),
                        )
                        Box(
                            Modifier
                                .size(28.dp)
                                .graphicsLayer {
                                    val s = 1f - .12f * grow
                                    scaleX = s
                                    scaleY = s
                                }
                                .clip(CircleShape)
                                .then(
                                    if (tone != null) Modifier.background(tone)
                                    else Modifier.background(Brush.sweepGradient(Rainbow)),
                                ),
                        )
                    }
                }
                repeat(SWATCH_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        AnimatedVisibility(
            visible = custom != null && picking,
            enter = expandVertically(Motion.size) + fadeIn(Motion.fade()),
            exit = shrinkVertically(Motion.size) + fadeOut(Motion.fade()),
        ) {
            // Reads [custom] itself, so the content recomposes on every change (shown[0] isn't state).
            ColorPicker(Color(custom ?: shown[0]), { onCustomChange(it.toArgb()) }, Modifier.padding(top = 2.dp))
        }
    }
}

/** Icon sections from [IconLibrary.groups], six 48dp cells per row; the chosen cell takes the colour's tint. */
@Composable
private fun IconPicker(
    groups: List<IconLibrary.Group>,
    selected: String,
    color: Color,
    modifier: Modifier,
    onPick: (String) -> Unit,
) {
    val c = LedgerTheme.colors
    val tint = color.copy(alpha = c.tileTintAlpha)
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        groups.forEachIndexed { g, group ->
            if (g > 0) Spacer(Modifier.height(4.dp))
            FormLabel(group.title)
            group.icons.chunked(ICON_COLUMNS).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { key ->
                        val on = key == selected
                        val fill by animateColorAsState(if (on) tint else tint.copy(alpha = 0f), Motion.fade(), label = "iconFill")
                        val tone by animateColorAsState(if (on) color else c.label, Motion.fade(), label = "iconTone")
                        Box(
                            Modifier.weight(1f).height(48.dp).tap(label = "图标") { onPick(key) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(fill),
                                contentAlignment = Alignment.Center,
                            ) { Glyph(LedgerIcons.get(key), 22.dp, tone) }
                        }
                    }
                    repeat(ICON_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

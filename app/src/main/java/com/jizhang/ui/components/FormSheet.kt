package com.jizhang.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jizhang.ui.theme.LedgerTheme
import com.jizhang.ui.theme.Motion
import com.jizhang.ui.theme.numberStyle
import com.jizhang.ui.theme.rememberHaptics
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** What a form can do from inside its content. */
interface FormScope {
    fun close()
}

private class FormRequest(val id: Long, val content: @Composable ColumnScope.(FormScope) -> Unit)

/**
 * Opens glass bottom sheets above everything else (including the add sheet). Replaces system
 * dialogs, which can't blur the app behind them and don't match the rest of the design.
 */
@Stable
class FormSheetController {
    internal var request by mutableStateOf<Any?>(null)
    private var seq = 0L

    /**
     * How many handlers inside the open form want the back press first (e.g. note chips in edit
     * mode). While above zero the host's own BackHandler stands aside, whatever the order in
     * which the handlers were registered.
     */
    internal var backClaims by mutableIntStateOf(0)

    /** A form is showing; overlays underneath should leave the back press to it. */
    val isOpen: Boolean get() = request != null

    fun open(content: @Composable ColumnScope.(FormScope) -> Unit) {
        request = FormRequest(++seq, content)
    }

    fun close() {
        request = null
    }
}

val LocalFormSheet = staticCompositionLocalOf { FormSheetController() }

@Composable
fun FormSheetHost(controller: FormSheetController) {
    val c = LedgerTheme.colors
    val req = controller.request as FormRequest?
    var last by remember { mutableStateOf(req) }
    if (req != null) last = req
    val scrim by animateFloatAsState(if (req != null) 1f else 0f, Motion.fade(), label = "scrim")
    BackHandler(enabled = req != null && controller.backClaims == 0) { controller.close() }
    val scope = remember(controller) { object : FormScope { override fun close() = controller.close() } }

    if (scrim > 0.001f) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = (.26f + .2f * c.darkness) * scrim))
                .then(if (req != null) Modifier.pointerInput(Unit) { detectTapGestures { controller.close() } } else Modifier),
        )
    }
    // Drag down to put the sheet away: the top of the sheet works on every form, and a scrollable
    // form also hands its over-scroll to the sheet (so pulling down at the top of a long list moves it).
    var dragY by remember { mutableFloatStateOf(0f) }
    val dragScope = rememberCoroutineScope()
    val dismissAt = with(LocalDensity.current) { 88.dp.toPx() }
    val flingAt = with(LocalDensity.current) { 600.dp.toPx() }
    // The spring back after a short drag. A new touch cancels it, so the two never fight over dragY.
    val springBack = remember { arrayOfNulls<Job>(1) }
    val settle: (Float) -> Unit = { velocity ->
        springBack[0]?.cancel()
        springBack[0] = dragScope.launch {
            // Pulled past the threshold or flung down, the sheet closes from where the finger left it
            // (the exit slide carries on from there); short of that, it springs back.
            if (dragY > dismissAt || (dragY > 0f && velocity > flingAt)) controller.close()
            else animate(dragY, 0f, animationSpec = Motion.layout()) { v, _ -> dragY = v }
        }
    }
    val dragState = rememberDraggableState { delta -> dragY = (dragY + delta).coerceAtLeast(0f) }
    // A new form opens at rest; the closed one keeps its offset while its exit slide runs.
    LaunchedEffect(req?.id) {
        if (req != null) {
            springBack[0]?.cancel()
            dragY = 0f
        }
    }
    val sheetDrag = remember(dragScope, dismissAt) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                springBack[0]?.cancel()
                // Dragging back up first returns the sheet to its place, and only that far.
                if (available.y < 0f && dragY > 0f) {
                    val back = maxOf(available.y, -dragY)
                    dragY += back
                    return Offset(0f, back)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // Only what the list could not scroll: at its top, pulling down drags the sheet.
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    dragY += available.y
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (dragY > 0f) settle(available.y)
                return Velocity.Zero
            }
        }
    }

    // A tall form (e.g. 定时记账) grows up to the status bar, never under it.
    Box(Modifier.fillMaxSize().statusBarsPadding().imePadding(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = req != null,
            enter = slideInVertically(Motion.offset) { it } + fadeIn(Motion.fade()),
            exit = slideOutVertically(Motion.offset) { it } + fadeOut(Motion.fade()),
        ) {
            val r = last ?: return@AnimatedVisibility
            val base = if (c.isDark) lerp(c.bg1, Color.Black, .25f) else lerp(c.bg0, Color.White, .5f)
            Box(
                Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, dragY.roundToInt().coerceAtLeast(0)) }
                    .nestedScroll(sheetDrag)
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 10.dp)
                    .glass(32.dp, blur = 30.dp, fill = Brush.verticalGradient(listOf(base.copy(alpha = .9f), base.copy(alpha = .8f))))
                    // Taps inside the sheet must not fall through to the scrim.
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                // A 48dp drag strip behind the form's first rows: where the form has nothing to touch
                // (the grabber, the title) a finger lands on it, and anything tappable still wins.
                // Behind rather than in the flow, so the sheet is laid out exactly as it always was.
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(48.dp)
                        .draggable(
                            dragState, Orientation.Vertical,
                            onDragStarted = { springBack[0]?.cancel() },
                            onDragStopped = { velocity -> settle(velocity) },
                        ),
                )
                Column(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(Modifier.align(Alignment.CenterHorizontally).size(36.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(c.track))
                    key(r.id) { r.content(this, scope) }
                }
            }
        }
    }
}

@Composable
fun FormTitle(title: String, subtitle: String? = null) {
    val c = LedgerTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
        if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = c.sub, lineHeight = 19.sp)
    }
}

@Composable
fun FormLabel(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = LedgerTheme.colors.sub)
}

/**
 * Inset text field: soft well, accent ring when focused. [large] is the amount style —
 * light 34sp tabular figures after a ¥ prefix. When [value] changes from outside (a quick-fill
 * pill, say) the cursor jumps to the end; while typing it stays where the user put it and the
 * IME's composing region (pinyin) is kept. Bump [cursorToEnd] to force the cursor to the end
 * (and focus the field) even when the text itself didn't change, e.g. on every quick-fill tap.
 */
@Composable
fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    prefix: String? = null,
    large: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    autoFocus: Boolean = true,
    isError: Boolean = false,
    onDone: (() -> Unit)? = null,
    cursorToEnd: Int = 0,
    /** Sits at the right end inside the well, e.g. the note's add-photo button (bring a 48dp target). */
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = LedgerTheme.colors
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    // Plain holder, not state: it only records which cursorToEnd request has been applied.
    val applied = remember { intArrayOf(cursorToEnd) }
    val external = field.text != value || applied[0] != cursorToEnd
    val shown = if (external) TextFieldValue(value, TextRange(value.length)) else field
    SideEffect {
        applied[0] = cursorToEnd
        if (shown != field) field = shown
    }
    var lastText by remember(value) { mutableStateOf(value) }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val ring by animateFloatAsState(if (focused) 1f else 0f, Motion.fade(), label = "ring")
    val focus = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { focus.requestFocus() }
    // A forced cursor move also brings the caret (and keyboard) back if focus had moved away.
    LaunchedEffect(cursorToEnd) { if (cursorToEnd != 0) focus.requestFocus() }
    val shape = RoundedCornerShape(18.dp)
    val style = if (large) numberStyle(34.sp, FontWeight.Light, -.02f) else TextStyle(fontSize = 16.sp)
    val border = when {
        isError -> c.hue(com.jizhang.domain.Hue.Gift)
        else -> lerp(c.hair, c.accent.copy(alpha = .45f), ring)
    }
    Row(
        modifier
            .fillMaxWidth()
            .height(if (large) 68.dp else 54.dp)
            .clip(shape)
            .background(c.opKey)
            .border(if (focused || isError) 1.5.dp else 1.dp, border, shape)
            .padding(start = 16.dp, end = if (trailing != null) 3.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (prefix != null) {
            Text(
                prefix,
                style = numberStyle(if (large) 20.sp else 16.sp, FontWeight.Normal, 0f),
                color = c.sub,
                modifier = Modifier.padding(end = 6.dp),
            )
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, style = style, color = c.placeholder)
            BasicTextField(
                value = shown,
                onValueChange = { next ->
                    field = next
                    if (next.text != lastText) {
                        lastText = next.text
                        onValueChange(next.text)
                    }
                },
                singleLine = true,
                textStyle = style.copy(color = c.ink),
                cursorBrush = SolidColor(c.accent),
                interactionSource = interaction,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        trailing?.invoke()
    }
}

/**
 * Two- or three-way switch with the add sheet's sliding white droplet. The options take the full
 * [height] as touch target (a page-level switch passes 48.dp); the droplet sits 4dp inside.
 */
@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    fontSize: TextUnit = 14.sp,
) {
    val c = LedgerTheme.colors
    val haptics = rememberHaptics()
    val x = remember { Animatable(selected.toFloat()) }
    LaunchedEffect(selected) { x.animateTo(selected.toFloat(), Motion.layout()) }
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(c.opKey),
    ) {
        val slot = (maxWidth - 8.dp) / options.size
        Box(
            Modifier
                .padding(4.dp)
                .offset { IntOffset((x.value * slot.toPx()).toInt(), 0) }
                .graphicsLayer {
                    val stretch = (abs(x.velocity) / 36f).coerceAtMost(.25f)
                    scaleX = 1f + stretch
                    scaleY = 1f - stretch * .3f
                }
                .width(slot)
                .fillMaxHeight()
                .liftedPill((height - 8.dp) / 2, Color.White.copy(alpha = .92f)),
        )
        Row(Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().tap(label = label, pressedScale = 1f) {
                        if (i != selected) {
                            haptics.tick()
                            onSelect(i)
                        }
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, fontSize = fontSize, fontWeight = FontWeight.SemiBold, color = if (i == selected) c.segOn else c.segOff)
                }
            }
        }
    }
}

/** Horizontally scrolling choice chips; [dots] colours a small dot before each label. */
@Composable
fun ChoiceChips(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    dots: List<Color?> = emptyList(),
) {
    val c = LedgerTheme.colors
    val haptics = rememberHaptics()
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val bg by animateFloatAsState(if (on) 1f else 0f, Motion.fade(), label = "chip")
            Row(
                Modifier
                    .height(38.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(lerp(c.opKey, c.accent, bg))
                    .tap(label = label) {
                        if (!on) {
                            haptics.tick()
                            onSelect(i)
                        }
                    }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                dots.getOrNull(i)?.let { Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(if (on) c.onAccent else it)) }
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = lerp(c.ink, c.onAccent, bg))
            }
        }
    }
}

enum class ButtonKind { Primary, Secondary, Danger }

@Composable
fun FormButton(
    text: String,
    kind: ButtonKind,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 52.dp,
    onClick: () -> Unit,
) {
    val c = LedgerTheme.colors
    val gift = c.hue(com.jizhang.domain.Hue.Gift)
    val shape = RoundedCornerShape(height / 2)
    val (bg, fg) = when (kind) {
        ButtonKind.Primary -> Brush.verticalGradient(0f to lerp(c.accent, Color.White, c.sheen.alpha), .55f to c.accent) to c.onAccent
        ButtonKind.Secondary -> SolidColor(c.opKey) to c.ink
        ButtonKind.Danger -> SolidColor(c.tint(com.jizhang.domain.Hue.Gift)) to gift
    }
    Box(
        modifier
            .height(height)
            .graphicsLayer { alpha = if (enabled) 1f else .45f }
            .clip(shape)
            .background(bg)
            .then(if (kind == ButtonKind.Primary) Modifier.solidEdge(height / 2) else Modifier)
            .tap(label = text, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = fg)
    }
}

/** Standard bottom row: secondary on the left, primary (wider) on the right. */
@Composable
fun FormActions(
    primary: String,
    onPrimary: () -> Unit,
    secondary: String = "取消",
    onSecondary: () -> Unit,
    primaryEnabled: Boolean = true,
    primaryKind: ButtonKind = ButtonKind.Primary,
) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FormButton(secondary, ButtonKind.Secondary, Modifier.weight(1f), onClick = onSecondary)
        FormButton(primary, primaryKind, Modifier.weight(1.6f), enabled = primaryEnabled, onClick = onPrimary)
    }
}

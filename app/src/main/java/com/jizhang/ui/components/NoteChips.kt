package com.jizhang.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import com.jizhang.domain.Hue
import com.jizhang.domain.NoteChip
import com.jizhang.ui.theme.LedgerIcons
import com.jizhang.ui.theme.LedgerTheme
import com.jizhang.ui.theme.Motion
import com.jizhang.ui.theme.rememberHaptics

private val Pill = RoundedCornerShape(17.dp)
private const val MaxNote = 40

/** Edit mode of [NoteChips], hoisted so the surrounding form can end it ([endsNoteEditing]). */
@Stable
class NoteChipsState {
    /** Long-pressed: every chip shows its × badge. */
    var editing by mutableStateOf(false)
    internal var row: LayoutCoordinates? = null
    internal var container: LayoutCoordinates? = null

    internal fun inRow(position: Offset): Boolean {
        val outer = container ?: return false
        val r = row ?: return false
        if (!outer.isAttached || !r.isAttached) return false
        return Rect(outer.localPositionOf(r, Offset.Zero), r.size.toSize()).contains(position)
    }
}

@Composable
fun rememberNoteChipsState(): NoteChipsState = remember { NoteChipsState() }

/** Goes on the container around [NoteChips]: a press anywhere outside the chip row ends edit mode. */
fun Modifier.endsNoteEditing(state: NoteChipsState): Modifier = this
    .onGloballyPositioned { state.container = it }
    .pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (state.editing && !state.inRow(down.position)) state.editing = false
        }
    }

/**
 * Quick-fill notes in one scrolling row that always ends with "+". Tap a chip to fill the note;
 * long-press any chip to show a × on every chip; "+" turns into a small input for a custom one.
 * The same row serves amounts (budget forms) through [input] and [label].
 */
@Composable
fun NoteChips(
    chips: List<NoteChip>,
    onPick: (String) -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (NoteChip) -> Unit,
    modifier: Modifier = Modifier,
    state: NoteChipsState = rememberNoteChipsState(),
    input: ChipInput = ChipInput.Note,
    /** How a chip's text is shown, e.g. "3000" → "¥3000". */
    label: (String) -> String = { it },
) {
    val haptics = rememberHaptics()
    val scroll = rememberScrollState()
    var adding by remember { mutableStateOf(false) }
    /** A chip just added with "+", scrolled into view once it arrives from the database. */
    var added by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(chips.isEmpty()) { if (chips.isEmpty()) state.editing = false }
    // The row keeps growing for a few frames (the input / new chip animates in), so follow the
    // end for a moment instead of aiming at a maxValue read once.
    suspend fun followEnd() {
        withTimeoutOrNull(500) {
            snapshotFlow { scroll.maxValue }.collectLatest { scroll.animateScrollTo(it, Motion.layout()) }
        }
        // The timeout may have cut the spring short; finish at the settled end.
        scroll.animateScrollTo(scroll.maxValue, Motion.layout())
    }
    LaunchedEffect(adding) {
        if (adding) followEnd()
    }
    // Keyed on chips only: clearing `added` must not cancel this effect before it scrolls.
    LaunchedEffect(chips) {
        if (added != null && chips.any { it.text == added }) {
            added = null
            followEnd()
        }
    }
    // Back ends edit mode first. The form host stands aside meanwhile, so the order in which the
    // two BackHandlers were registered doesn't matter.
    val form = LocalFormSheet.current
    if (state.editing) {
        DisposableEffect(form) {
            form.backClaims++
            onDispose { form.backClaims-- }
        }
    }
    BackHandler(enabled = state.editing) { state.editing = false }

    Row(
        modifier
            .onGloballyPositioned { state.row = it }
            .horizontalScroll(scroll)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        chips.forEach { chip ->
            key(chip.text) {
                ChipItem(
                    text = label(chip.text),
                    editing = state.editing,
                    onTap = {
                        if (state.editing) {
                            state.editing = false
                        } else {
                            haptics.tick()
                            onPick(chip.text)
                        }
                    },
                    onLongPress = {
                        if (!state.editing) {
                            haptics.tick()
                            state.editing = true
                        }
                    },
                    onRemove = {
                        haptics.tick()
                        onRemove(chip)
                    },
                )
            }
        }
        if (adding) {
            AddInput(
                onCommit = { raw ->
                    val text = input.commit(raw)
                    if (text == null) {
                        // Not a valid value: refuse and keep the input open so it can be fixed.
                        haptics.reject()
                    } else if (text.isEmpty()) {
                        adding = false
                    } else if (chips.any { it.text == text }) {
                        // Already showing: refuse and keep the input open so it can be changed.
                        haptics.reject()
                    } else {
                        haptics.tick()
                        adding = false
                        added = text
                        onAdd(text)
                    }
                },
                onCancel = { adding = false },
                input = input,
            )
        } else {
            Box(
                Modifier.size(48.dp).tap(label = input.addLabel) {
                    state.editing = false
                    adding = true
                },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(44.dp, 34.dp).clip(Pill).border(1.dp, LedgerTheme.colors.hair, Pill),
                    contentAlignment = Alignment.Center,
                ) { Glyph(LedgerIcons.get("plus"), 15.dp, LedgerTheme.colors.sub) }
            }
        }
    }
}

/** One pill inside a 48dp-tall hit area; the × badge sits on its top-right corner. */
@Composable
private fun ChipItem(text: String, editing: Boolean, onTap: () -> Unit, onLongPress: () -> Unit, onRemove: () -> Unit) {
    val c = LedgerTheme.colors
    val tapNow by rememberUpdatedState(onTap)
    val longNow by rememberUpdatedState(onLongPress)
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) .93f else 1f, Motion.layout(), label = "press")
    val badge by animateFloatAsState(if (editing) 1f else 0f, Motion.layout(), label = "badge")
    val badgeAlpha by animateFloatAsState(if (editing) 1f else 0f, Motion.fade(), label = "badgeAlpha")
    Box(
        Modifier
            .height(48.dp)
            .semantics {
                role = Role.Button
                onClick(label = text) { tapNow(); true }
                onLongClick(label = "编辑") { longNow(); true }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onLongPress = { longNow() },
                    onTap = { tapNow() },
                )
            },
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(end = 6.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .height(34.dp)
                .clip(Pill)
                .border(1.dp, c.hair, Pill)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                fontSize = 13.sp,
                color = c.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 200.dp),
            )
        }
        if (editing || badge > .01f) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(26.dp)
                    .graphicsLayer {
                        val s = badge.coerceAtLeast(0f)
                        scaleX = s
                        scaleY = s
                        alpha = badgeAlpha.coerceIn(0f, 1f)
                    }
                    .tap(label = "删除「$text」", pressedScale = .85f, enabled = editing, onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(18.dp).clip(CircleShape).background(c.hue(Hue.Gift)),
                    contentAlignment = Alignment.Center,
                ) { Glyph(LedgerIcons.get("close", 2.6f), 10.dp, c.onTile) }
            }
        }
    }
}

/**
 * What the "+" input of [NoteChips] accepts. [filter] runs on every keystroke; [commit] turns the
 * typed text into the chip's text: "" cancels, null refuses (the input stays open).
 */
class ChipInput(
    val keyboardType: KeyboardType = KeyboardType.Unspecified,
    val placeholder: String = "自定义",
    val addLabel: String = "添加自定义备注",
    val filter: (String) -> String = { it.take(MaxNote) },
    val commit: (String) -> String? = { it.trim().take(MaxNote) },
) {
    companion object {
        val Note = ChipInput()
    }
}

/** The "+" slot while typing a custom note: focused input plus a ✓. Empty input cancels. */
@Composable
private fun AddInput(onCommit: (String) -> Unit, onCancel: () -> Unit, input: ChipInput) {
    val c = LedgerTheme.colors
    var text by remember { mutableStateOf("") }
    var hadFocus by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(Modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .height(34.dp)
                .clip(Pill)
                .background(c.opKey)
                .border(1.5.dp, c.accent.copy(alpha = .45f), Pill)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.width(112.dp), contentAlignment = Alignment.CenterStart) {
                if (text.isEmpty()) Text(input.placeholder, fontSize = 13.sp, color = c.placeholder, maxLines = 1)
                BasicTextField(
                    value = text,
                    onValueChange = { text = input.filter(it) },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 13.sp, color = c.ink),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = input.keyboardType, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onCommit(text) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        .onFocusChanged {
                            if (it.isFocused) {
                                hadFocus = true
                            } else if (hadFocus && text.isBlank()) {
                                onCancel()
                            }
                        },
                )
            }
        }
        Box(Modifier.size(48.dp).tap(label = "添加") { onCommit(text) }, contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(c.accent),
                contentAlignment = Alignment.Center,
            ) { Glyph(LedgerIcons.get("check", 2.4f), 16.dp, c.onAccent) }
        }
    }
}

package com.jizhang.ui.me

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jizhang.BuildConfig
import com.jizhang.LedgerState
import com.jizhang.LedgerViewModel
import com.jizhang.data.ThemeId
import com.jizhang.domain.Hue
import com.jizhang.domain.formatCentsShort
import com.jizhang.ui.components.Glyph
import com.jizhang.ui.components.LocalFormSheet
import com.jizhang.ui.forms.BackupForm
import com.jizhang.ui.forms.ConfirmForm
import com.jizhang.ui.forms.InfoForm
import com.jizhang.ui.forms.SharkImportForm
import com.jizhang.ui.components.glass
import com.jizhang.ui.components.rise
import com.jizhang.ui.components.tap
import com.jizhang.ui.update.UpdateRows
import com.jizhang.ui.theme.LedgerIcons
import com.jizhang.ui.theme.LedgerTheme
import com.jizhang.ui.theme.Motion
import com.jizhang.ui.theme.rememberHaptics
import com.jizhang.ui.theme.colorsFor
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun MeScreen(
    state: LedgerState,
    vm: LedgerViewModel,
    contentPadding: PaddingValues,
    onOpenRecurring: () -> Unit = {},
    onPickTheme: (ThemeId, origin: Offset) -> Unit,
) {
    val c = LedgerTheme.colors
    val rules by vm.recurring.collectAsStateWithLifecycle()
    val runningRules = rules.count { !it.paused && it.next(state.today) != null }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val form = LocalFormSheet.current

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportTo(context.contentResolver, uri)
    }
    // The CSV that 鲨鱼记账 exports; file managers label it all sorts of ways, so nothing is filtered out.
    val sharkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            vm.readSharkCsv(context.contentResolver, uri).fold(
                { plan -> form.open { s -> SharkImportForm(plan, onImport = { vm.importShark(plan, it) }, close = s::close) } },
                { e -> form.open { s -> InfoForm("无法导入", e.message ?: "文件读取失败", s::close) } },
            )
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            vm.readBackup(context.contentResolver, uri).fold(
                { backup ->
                    form.open { s ->
                        ConfirmForm(
                            title = "导入备份？",
                            message = "备份里有 ${backup.records.size} 条记录、${backup.budgets.size} 个预算。导入后会替换手机上现有的数据，这一步不能撤销。",
                            confirm = "替换并导入",
                            danger = true,
                            onConfirm = { vm.restore(backup) },
                            close = s::close,
                        )
                    }
                },
                { e -> form.open { s -> InfoForm("无法导入", e.message ?: "文件读取失败", s::close) } },
            )
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().rise(1).glass(28.dp).padding(vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.Bottom) {
                Text("主题", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink, modifier = Modifier.weight(1f))
                Text("点一下即可切换", fontSize = 12.sp, color = c.sub)
            }
            ThemePicker(state.settings.theme, onPickTheme)
        }
        Column(Modifier.fillMaxWidth().rise(2).glass(26.dp).padding(horizontal = 16.dp, vertical = 2.dp)) {
            SettingRow("repeat", Hue.Transit, "定时记账", if (runningRules > 0) "$runningRules 条进行中" else "自动记重复的账", onOpenRecurring)
            HorizontalDivider(thickness = 0.5.dp, color = c.hair)
            SettingRow("backup", Hue.Med, "备份与恢复", "导出 · 导入") {
                form.open { s ->
                    BackupForm(
                        onExport = { exporter.launch("记账备份-${LocalDate.now()}.json") },
                        onImport = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                        onImportShark = {
                            sharkPicker.launch(
                                arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel", "application/octet-stream", "*/*"),
                            )
                        },
                        close = s::close,
                    )
                }
            }
            if (state.demoCount > 0) {
                HorizontalDivider(thickness = 0.5.dp, color = c.hair)
                SettingRow("trash", Hue.Gift, "清除示例数据", "${state.demoCount} 条") {
                    form.open { s ->
                        ConfirmForm(
                            title = "清除示例数据？",
                            message = "只删除首次打开时自动生成的 ${state.demoCount} 条示例记录，你自己记的不受影响。",
                            confirm = "清除",
                            danger = true,
                            onConfirm = { vm.clearDemo() },
                            close = s::close,
                        )
                    }
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = c.hair)
            UpdateRows(vm)
            HorizontalDivider(thickness = 0.5.dp, color = c.hair)
            SettingRow("info", Hue.Other, "关于", "v${BuildConfig.VERSION_NAME}") {
                form.open { s ->
                    InfoForm(
                        "记账 v${BuildConfig.VERSION_NAME}",
                        "自己用的记账本，没有广告。数据只存在这台手机上，应用只会联网检查 GitHub 上有没有新版本。记得偶尔导出一份备份。",
                        s::close,
                    )
                }
            }
        }
        Text(
            "个人专属账本",
            fontSize = 12.sp, color = c.sub, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().rise(3),
        )
    }
}

@Composable
private fun SettingRow(icon: String, hue: Hue, title: String, value: String, onClick: () -> Unit) {
    val c = LedgerTheme.colors
    Row(
        Modifier.fillMaxWidth().height(54.dp).tap(label = title, pressedScale = .98f, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(c.tint(hue)),
            contentAlignment = Alignment.Center,
        ) { Glyph(LedgerIcons.get(icon), 18.dp, c.hue(hue)) }
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.ink, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, color = c.sub)
        Glyph(LedgerIcons.get("chevr"), 16.dp, c.sub)
    }
}

private val ThemeCardWidth = 92.dp
private val ThemeCardGap = 10.dp
private val ThemePickerPad = 16.dp

/** Every theme as a preview card in a row that scrolls sideways: the light ones first, then the dark ones. */
@Composable
private fun ThemePicker(selected: ThemeId, onPick: (ThemeId, Offset) -> Unit) {
    val themes = remember { ThemeId.entries.sortedBy { colorsFor(it).isDark } }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var viewport by remember { mutableIntStateOf(0) }
    var placed by remember { mutableStateOf(false) }
    // The page opens with the current theme in view (centred when the row is long enough).
    LaunchedEffect(viewport) {
        if (viewport == 0 || placed) return@LaunchedEffect
        placed = true
        with(density) {
            val left = ThemePickerPad.toPx() + themes.indexOf(selected) * (ThemeCardWidth + ThemeCardGap).toPx()
            scroll.scrollTo((left + ThemeCardWidth.toPx() / 2 - viewport / 2).roundToInt().coerceAtLeast(0))
        }
    }
    // A card half hidden at an edge slides fully into view when tapped.
    fun ease(index: Int) {
        with(density) {
            val pad = ThemePickerPad.toPx()
            val left = pad + index * (ThemeCardWidth + ThemeCardGap).toPx()
            val right = left + ThemeCardWidth.toPx()
            val target = when {
                left - pad < scroll.value -> left - pad
                right + pad > scroll.value + viewport -> right + pad - viewport
                else -> return
            }
            scope.launch { scroll.animateScrollTo(target.roundToInt().coerceIn(0, scroll.maxValue), Motion.layout()) }
        }
    }
    Row(
        Modifier.fillMaxWidth().onSizeChanged { viewport = it.width }.horizontalScroll(scroll).padding(horizontal = ThemePickerPad),
        horizontalArrangement = Arrangement.spacedBy(ThemeCardGap),
    ) {
        themes.forEachIndexed { i, t ->
            ThemePreview(t, selected = selected == t, Modifier.width(ThemeCardWidth)) { id, center ->
                onPick(id, center)
                ease(i)
            }
        }
    }
}

/** A small rendering of each theme: its background, orbs and a glass card with a sample row. */
@Composable
private fun ThemePreview(theme: ThemeId, selected: Boolean, modifier: Modifier, onPick: (ThemeId, Offset) -> Unit) {
    val c = LedgerTheme.colors
    val t = colorsFor(theme)
    var center by remember { mutableStateOf(Offset.Zero) }
    val check by animateFloatAsState(if (selected) 1f else 0f, Motion.layout(), label = "check")
    Column(
        modifier.tap(label = "${theme.label}主题") { onPick(theme, center) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val shape = RoundedCornerShape(20.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .height(132.dp)
                .onGloballyPositioned { center = it.boundsInRoot().center }
                .border(if (selected) 2.dp else 0.5.dp, if (selected) c.accent else c.hair, shape)
                .padding(if (selected) 3.dp else 0.dp)
                .clip(RoundedCornerShape(if (selected) 17.dp else 20.dp))
                .drawBehind {
                    drawRect(Brush.linearGradient(listOf(t.bg0, t.bg2, t.bg3), start = Offset(size.width * .3f, 0f), end = Offset(size.width * .7f, size.height)))
                    fun orb(color: Color, x: Float, y: Float, r: Float) = drawCircle(
                        Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center = Offset(x, y), radius = r),
                        r, Offset(x, y),
                    )
                    orb(t.orb1, size.width * .15f, size.height * .1f, size.width * .7f)
                    orb(t.orb2, size.width * .95f, size.height * .55f, size.width * .55f)
                    orb(t.orb3, size.width * .3f, size.height * 1.05f, size.width * .7f)
                },
        ) {
            Row(
                Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 9.dp)
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(t.glassTop.copy(alpha = t.glassTop.alpha + .24f), t.glassBottom.copy(alpha = t.glassBottom.alpha + .13f))))
                    .border(0.5.dp, t.rimStrong, RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Box(Modifier.size(22.dp).clip(RoundedCornerShape(8.dp)).background(t.hue(Hue.Food)))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(28.dp, 4.dp).clip(CircleShape).background(t.ink.copy(alpha = .7f)))
                    Box(Modifier.size(18.dp, 4.dp).clip(CircleShape).background(t.sub.copy(alpha = .6f)))
                }
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
                    .size(36.dp, 12.dp)
                    .clip(CircleShape)
                    .background(t.accent),
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset((-8).dp, 8.dp)
                    .size(20.dp)
                    .graphicsLayer {
                        alpha = check.coerceIn(0f, 1f)
                        scaleX = .5f + .5f * check
                        scaleY = .5f + .5f * check
                    }
                    .clip(CircleShape)
                    .background(c.accent),
                contentAlignment = Alignment.Center,
            ) { Glyph(LedgerIcons.get("check", 3f), 12.dp, c.onAccent) }
        }
        Text(
            theme.label, fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) c.ink else c.sub,
        )
    }
}

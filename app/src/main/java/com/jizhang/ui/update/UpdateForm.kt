package com.jizhang.ui.update

import android.content.ActivityNotFoundException
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jizhang.BuildConfig
import com.jizhang.LedgerViewModel
import com.jizhang.data.Release
import com.jizhang.data.UpdateController
import com.jizhang.data.UpdateEvent
import com.jizhang.data.UpdateState
import com.jizhang.domain.ReleaseNotes
import com.jizhang.domain.formatBytes
import com.jizhang.ui.components.ButtonKind
import com.jizhang.ui.components.FormActions
import com.jizhang.ui.components.FormButton
import com.jizhang.ui.components.FormLabel
import com.jizhang.ui.components.FormTitle
import com.jizhang.ui.components.LocalFormSheet
import com.jizhang.ui.components.tap
import com.jizhang.ui.forms.InfoForm
import com.jizhang.ui.theme.LedgerTheme
import com.jizhang.ui.theme.Motion
import com.jizhang.ui.theme.numberStyle
import kotlinx.coroutines.flow.first

/** Toast pills are one line; a longer message goes into a sheet instead. */
private const val TOAST_MAX_CHARS = 16

/**
 * Reacts to what the update controller reports: opens the update sheet, says "已是最新版本",
 * sends the owner to 允许安装未知应用. Emits no UI; lives inside the form-sheet provider.
 * [canOpen] is false while something else (the add sheet) is in the way; a new-version sheet then
 * waits its turn, and never replaces a form that is already open.
 */
@Composable
fun UpdateEvents(vm: LedgerViewModel, canOpen: Boolean) {
    val controller = vm.updater
    val form = LocalFormSheet.current
    val context = LocalContext.current
    val free by rememberUpdatedState(canOpen && !form.isOpen)
    LaunchedEffect(controller) {
        controller.events.collect { e ->
            when (e) {
                is UpdateEvent.NewVersion -> {
                    snapshotFlow { free }.first { it }
                    form.open { s -> UpdateForm(controller, e.release, s::close) }
                }
                UpdateEvent.UpToDate -> vm.showMessage("已是最新版本")
                UpdateEvent.NotConfigured -> vm.showMessage("未配置更新源")
                is UpdateEvent.CheckFailed ->
                    if (e.message.length <= TOAST_MAX_CHARS) vm.showMessage(e.message)
                    else {
                        // The check can finish while the owner is already in another form; don't replace it.
                        snapshotFlow { free }.first { it }
                        form.open { s -> InfoForm("没能检查更新", e.message, s::close) }
                    }
                UpdateEvent.GrantInstall -> try {
                    context.startActivity(controller.installSettingsIntent)
                } catch (_: ActivityNotFoundException) {
                    vm.showMessage("打不开系统设置，请手动允许安装应用")
                }
            }
        }
    }
}

/**
 * The update sheet. It follows the controller, so the same sheet shows the offer, the download,
 * the wait for 允许安装未知应用, the installer's confirmation and a failure; closing it never
 * stops a download (取消 does). [offered] is the release it was opened for.
 */
@Composable
fun UpdateForm(controller: UpdateController, offered: Release, close: () -> Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    val s = state
    // Nothing left to offer (skipped, dismissed): the sheet goes with it.
    val done = s is UpdateState.Idle || s is UpdateState.UpToDate || s is UpdateState.Checking
    LaunchedEffect(done) { if (done) close() }
    Column(Modifier.fillMaxWidth().animateContentSize(Motion.size), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when (s) {
            is UpdateState.Available -> Offer(s.release, controller, close)
            is UpdateState.Downloading -> Progress(s, controller)
            is UpdateState.ReadyToInstall -> Ready(s, controller, close)
            is UpdateState.Installing -> Installing(s, controller, close)
            is UpdateState.Failed -> Failed(s, controller, close)
            else -> Unit
        }
    }
}

@Composable
private fun Offer(release: Release, controller: UpdateController, close: () -> Unit) {
    val notes = remember(release.notes) { ReleaseNotes.plain(release.notes) }
    val size = if (release.apkSize > 0) " · 安装包 ${formatBytes(release.apkSize)}" else ""
    FormTitle("发现新版本 v${release.version}", "当前版本 v${BuildConfig.VERSION_NAME}$size")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FormLabel("更新内容")
        Notes(notes.ifBlank { "这个版本没有填写更新说明。" })
    }
    Column {
        FormActions(primary = "立即更新", onPrimary = controller::startUpdate, secondary = "以后再说", onSecondary = close)
        TextAction("跳过这个版本") {
            controller.skip(release)
            close()
        }
    }
}

/** Release notes as plain text; long ones scroll inside a capped area so the buttons stay in reach. */
@Composable
private fun Notes(text: String) {
    val c = LedgerTheme.colors
    Box(Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(20.dp)).background(c.opKey)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(14.dp)) {
            Text(text, fontSize = 13.sp, lineHeight = 20.sp, color = c.label)
        }
    }
}

@Composable
private fun Progress(s: UpdateState.Downloading, controller: UpdateController) {
    val c = LedgerTheme.colors
    FormTitle("正在下载 v${s.release.version}", "关闭这个窗口不会中断下载")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProgressBar(s.fraction)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val detail = if (s.total > 0) "${formatBytes(s.done)} / ${formatBytes(s.total)}" else "已下载 ${formatBytes(s.done)}"
            Text(detail, style = numberStyle(12.sp, FontWeight.Normal, 0f), color = c.sub, modifier = Modifier.weight(1f))
            if (s.total > 0) Text("${(s.fraction * 100).toInt()}%", style = numberStyle(12.sp, FontWeight.Medium, 0f), color = c.label)
        }
    }
    FormButton("取消", ButtonKind.Secondary, Modifier.fillMaxWidth(), onClick = controller::cancelDownload)
}

/** The bar's fill springs to each new fraction; the overshoot is clamped to the track. */
@Composable
private fun ProgressBar(fraction: Float) {
    val c = LedgerTheme.colors
    val shown by animateFloatAsState(fraction, Motion.layout(), label = "download")
    Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(c.track)) {
        Box(
            Modifier
                .fillMaxWidth(shown.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(c.ring1, c.ring2))),
        )
    }
}

@Composable
private fun Ready(s: UpdateState.ReadyToInstall, controller: UpdateController, close: () -> Unit) {
    if (s.needsPermission) {
        FormTitle("还差一步", "需要先允许「记账」安装应用。点「去允许」打开系统设置，打开开关后回到这里，会自动继续安装。")
        FormActions(primary = "去允许", onPrimary = controller::startUpdate, secondary = "以后再说", onSecondary = close)
    } else {
        FormTitle("还没有安装", "v${s.release.version} 已经下载好了，随时可以安装。")
        FormActions(primary = "安装", onPrimary = controller::startUpdate, secondary = "以后再说", onSecondary = close)
    }
}

@Composable
private fun Installing(s: UpdateState.Installing, controller: UpdateController, close: () -> Unit) {
    if (s.needsAction) {
        FormTitle("等待确认安装", "请在系统弹出的窗口里点「更新」。没看到窗口的话，点下面的按钮。")
        FormActions(primary = "继续安装", onPrimary = controller::resumeInstall, secondary = "以后再说", onSecondary = close)
    } else {
        FormTitle("正在安装 v${s.release.version}", "安装完成后「记账」会关闭，再点开就是新版本，账本数据都在。")
        Column {
            FormButton("好", ButtonKind.Primary, Modifier.fillMaxWidth(), onClick = close)
            TextAction("没有反应？重新安装", controller::resumeInstall)
        }
    }
}

@Composable
private fun Failed(s: UpdateState.Failed, controller: UpdateController, close: () -> Unit) {
    val dismiss = {
        controller.dismissFailure()
        close()
    }
    FormTitle("更新没有成功", s.message)
    if (s.release != null && s.retryable) {
        FormActions(primary = "重试", onPrimary = controller::startUpdate, secondary = "关闭", onSecondary = dismiss)
    } else {
        FormButton("关闭", ButtonKind.Primary, Modifier.fillMaxWidth(), onClick = dismiss)
    }
}

/** A quiet text-only action under the buttons (48dp tall, like every touch target). */
@Composable
private fun TextAction(text: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(48.dp).tap(label = text, pressedScale = .97f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 13.sp, color = LedgerTheme.colors.sub) }
}

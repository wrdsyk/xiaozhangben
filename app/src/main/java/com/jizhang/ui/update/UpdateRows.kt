package com.jizhang.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jizhang.BuildConfig
import com.jizhang.LedgerViewModel
import com.jizhang.data.UpdateState
import com.jizhang.domain.Hue
import com.jizhang.ui.components.Glyph
import com.jizhang.ui.components.PillSwitch
import com.jizhang.ui.components.tap
import com.jizhang.ui.theme.LedgerIcons
import com.jizhang.ui.theme.LedgerTheme
import com.jizhang.ui.theme.rememberHaptics
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val LastCheckFormat = DateTimeFormatter.ofPattern("M月d日 HH:mm")

/**
 * 我的's two update rows, for inside its settings card: 检查更新 (right side: the current version,
 * or 有新版本 vX in the accent colour) and the 自动检查更新 switch. Emits the divider between them.
 */
@Composable
fun UpdateRows(vm: LedgerViewModel) {
    val c = LedgerTheme.colors
    val haptics = rememberHaptics()
    val controller = vm.updater
    val state by controller.state.collectAsStateWithLifecycle()
    val prefs by controller.prefs.collectAsStateWithLifecycle()

    val news = when (val s = state) {
        is UpdateState.Available -> "有新版本 v${s.release.version}"
        is UpdateState.Downloading -> "下载中 ${(s.fraction * 100).toInt()}%"
        is UpdateState.ReadyToInstall -> "有新版本 v${s.release.version}"
        is UpdateState.Installing -> "正在安装"
        is UpdateState.Failed -> s.release?.let { "有新版本 v${it.version}" }
        else -> null
    }
    val value = when {
        news != null -> news
        state is UpdateState.Checking -> "检查中…"
        !controller.configured -> "未配置更新源"
        else -> "v${BuildConfig.VERSION_NAME}"
    }
    // With news on the right there is no room for it.
    val lastCheck = prefs.lastCheckAt.takeIf { it > 0 && news == null }?.let {
        "上次检查 " + LastCheckFormat.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
    }

    UpdateRow("update", Hue.Study, "检查更新", lastCheck, onClick = { controller.check(manual = true) }) {
        Text(
            value, fontSize = 13.sp,
            fontWeight = if (news != null) FontWeight.SemiBold else FontWeight.Normal,
            color = if (news != null) c.accent else c.sub,
        )
        Glyph(LedgerIcons.get("chevr"), 16.dp, c.sub)
    }
    HorizontalDivider(thickness = 0.5.dp, color = c.hair)
    UpdateRow(
        "clock", Hue.Daily, "自动检查更新", "每天最多检查一次",
        onClick = {
            haptics.tick()
            controller.setAutoCheck(!prefs.autoCheck)
        },
    ) { PillSwitch(prefs.autoCheck, "自动检查更新") }
}

/** Same look as 我的's other rows, with a small second line under the title and a free right side. */
@Composable
private fun UpdateRow(
    icon: String,
    hue: Hue,
    title: String,
    detail: String?,
    onClick: () -> Unit,
    end: @Composable RowScope.() -> Unit,
) {
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
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.ink)
            if (detail != null) Text(detail, fontSize = 11.sp, color = c.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        end()
    }
}

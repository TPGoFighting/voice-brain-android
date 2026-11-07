package top.tpgofighting.voicebrain.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.BatteryCharging
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.Database
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Monitor
import com.composables.icons.lucide.Moon
import com.composables.icons.lucide.Power
import com.composables.icons.lucide.Settings2
import com.composables.icons.lucide.Sun
import top.tpgofighting.voicebrain.service.KeepAliveHelper
import top.tpgofighting.voicebrain.ui.components.TPCard
import top.tpgofighting.voicebrain.ui.components.brandAccentColor
import top.tpgofighting.voicebrain.ui.theme.TPAccentBlue
import top.tpgofighting.voicebrain.ui.theme.TPMustardYellow
import top.tpgofighting.voicebrain.ui.theme.TPNavyDark
import top.tpgofighting.voicebrain.ui.theme.TPType
import top.tpgofighting.voicebrain.ui.viewmodel.MainUiState

/**
 * 设置页（批次 J）：主流软件式的分组设置——外观 / 录音 / 保活与权限 / 服务 / 关于。
 * 所有开关都接既有真实状态（主题模式、断句阈值、转写展示、电池豁免、自启动），不放假设置。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: MainUiState,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    onSetSilence: (Int) -> Unit,
    onToggleCleansed: (Boolean) -> Unit,
    onBatteryExemption: () -> Unit,
    onAutoStartSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val versionName = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "-"
    } catch (e: Exception) {
        "-"
    }
    val batteryIgnored = KeepAliveHelper.isBatteryOptimizationIgnored(context)
    val notificationsOn = KeepAliveHelper.areNotificationsEnabled(context)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", style = TPType.pageTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Lucide.ArrowLeft, contentDescription = "返回", modifier = Modifier.size(20.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = 96.dp)
        ) {
            item {
                SettingsGroup(title = "外观") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeChip("跟随系统", Lucide.Monitor, themeMode == "system") { onThemeModeChange("system") }
                        ThemeChip("浅色", Lucide.Sun, themeMode == "light") { onThemeModeChange("light") }
                        ThemeChip("深色", Lucide.Moon, themeMode == "dark") { onThemeModeChange("dark") }
                    }
                }
            }
            item {
                SettingsGroup(title = "录音") {
                    Text("断句阈值", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(500 to "500ms 紧凑", 800 to "800ms 标准", 1200 to "1200ms 从容").forEach { (ms, label) ->
                            SelectChip(label, state.sentenceSilenceMs == ms) { onSetSilence(ms) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("转写展示", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SelectChip("原文", !state.showCleansed) { onToggleCleansed(false) }
                        SelectChip("清洗稿", state.showCleansed) { onToggleCleansed(true) }
                    }
                }
            }
            item {
                SettingsGroup(title = "保活与权限") {
                    SettingsRow(
                        icon = Lucide.BatteryCharging,
                        title = "电池优化豁免",
                        subtitle = if (batteryIgnored) "已豁免，后台录音更稳" else "未豁免，长录音可能被系统中断",
                        actionLabel = if (batteryIgnored) null else "去设置",
                        onAction = onBatteryExemption
                    )
                    SettingsRow(
                        icon = Lucide.Power,
                        title = "自启动",
                        subtitle = "ColorOS 等系统需单独允许，录音通知才常驻",
                        actionLabel = "打开",
                        onAction = onAutoStartSettings
                    )
                    SettingsRow(
                        icon = Lucide.Bell,
                        title = "通知",
                        subtitle = if (notificationsOn) "已开启，录音状态会进通知栏" else "未开启，录音通知不可见",
                        actionLabel = null,
                        onAction = {}
                    )
                    SettingsRow(
                        icon = Lucide.Settings2,
                        title = "应用详情",
                        subtitle = "在系统里管理权限与存储",
                        actionLabel = "打开",
                        onAction = { KeepAliveHelper.openAppDetails(context) }
                    )
                }
            }
            item {
                SettingsGroup(title = "服务") {
                    SettingsRow(
                        icon = Lucide.Database,
                        title = "数据服务",
                        subtitle = "voice.tpgofighting.top（你自己的服务器）",
                        actionLabel = null,
                        onAction = {}
                    )
                    SettingsRow(
                        icon = Lucide.Info,
                        title = "历史会话",
                        subtitle = "共 ${state.sessions.size} 条",
                        actionLabel = "打开",
                        onAction = onOpenHistory
                    )
                }
            }
            item {
                SettingsGroup(title = "关于") {
                    SettingsRow(
                        icon = Lucide.Info,
                        title = "Voice Brain · AI 录音豆",
                        subtitle = "版本 v$versionName",
                        actionLabel = null,
                        onAction = {}
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "开源组件：Lucide 图标（ISC）· Airbnb Lottie、mikepenz Markdown、Haze（Apache-2.0）· 线条动画素材来自 LottieFiles（Simple License）",
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )
        Column {
            content()
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String?,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = brandAccentColor())
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = TPType.rowTitle)
            Text(subtitle, style = TPType.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (actionLabel != null) {
            // 行内动作只是跳系统设置，降为文字按钮，不与主 CTA 抢权重（PM 审查 P2）
            Text(
                actionLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = brandAccentColor(),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onAction() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(if (selected) TPNavyDark else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            style = TPType.label,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ThemeChip(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(if (selected) TPNavyDark else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                label,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

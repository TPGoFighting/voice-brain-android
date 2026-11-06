package top.tpgofighting.voicebrain.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.tpgofighting.voicebrain.model.Utterance
import top.tpgofighting.voicebrain.ui.components.*
import top.tpgofighting.voicebrain.ui.theme.*
import top.tpgofighting.voicebrain.ui.viewmodel.MainUiState

/** 说话人配色板（与 Web 端一致的哈希取色思路） */
private val SPEAKER_COLORS = listOf(
    Color(0xFF2563EB), Color(0xFF059669), Color(0xFF7C3AED), Color(0xFFDB2777),
    Color(0xFFD97706), Color(0xFF0891B2), Color(0xFF65A30D), Color(0xFFDC2626)
)

fun speakerColor(speaker: String): Color {
    if (speaker == "发言人") return Color(0xFF64748B)
    return SPEAKER_COLORS[(speaker.hashCode() and Int.MAX_VALUE) % SPEAKER_COLORS.size]
}

// ModalBottomSheet 在当前 material3 版本仍标记实验 API；animateItem 需要 Foundation 的 OptIn
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LiveWorkbenchTab(
    state: MainUiState,
    onToggleRecord: () -> Unit,
    onExportWav: () -> Unit,
    onGenerateMinutes: () -> Unit,
    onSetSilence: (Int) -> Unit = {},
    onSelectDevice: (Int?) -> Unit = {},
    onToggleCleansed: (Boolean) -> Unit = {},
    onDiarize: () -> Unit = {},
    onOpenSession: (String) -> Unit = {},
    onRefreshSessions: () -> Unit = {},
    onBatteryExemption: () -> Unit = {},
    onAutoStartSettings: () -> Unit = {},
    onConfirmMicPermission: () -> Unit = {},
    onDismissMicRationale: () -> Unit = {},
    onBatteryHintHandled: () -> Unit = {}
) {
    val listState = rememberLazyListState()
    var showDeviceMenu by remember { mutableStateOf(false) }
    var showArchive by remember { mutableStateOf(false) }
    var advancedExpanded by remember { mutableStateOf(false) }

    // 自动滚动至最新的转写句子
    LaunchedEffect(state.utterances.size) {
        if (state.utterances.isNotEmpty()) {
            listState.animateScrollToItem(state.utterances.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 0. 麦克风权限被拒后的恢复卡（系统框拒绝后只剩设置页一条路）
        if (state.micPermissionDenied && !state.isRecording) {
            val context = LocalContext.current
            TPCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("麦克风权限未开启", fontSize = 12.sp, fontWeight = FontWeight.Black)
                        Text(
                            "没有麦克风权限无法录音，请在系统设置里为录音豆开启。",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    }) {
                        Text("去设置开启", fontSize = 12.sp, fontWeight = FontWeight.Black, color = TPAccentBlue)
                    }
                }
            }
        }

        // 1. 录音主控制面板 (Hero Panel)
        TPCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = MaterialTheme.colorScheme.surface
        ) {
            // 状态栏与计时器
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val statusVariant = when {
                        state.wsStatusConnected -> TPBadgeVariant.GREEN
                        state.isRecording -> TPBadgeVariant.YELLOW
                        else -> TPBadgeVariant.DARK
                    }
                    // 未开始录音时连接状态没有意义，只显示准备就绪
                    TPBadge(
                        text = if (state.isRecording || state.wsStatusConnected) state.wsStatusText else "准备就绪",
                        variant = statusVariant
                    )
                }

                val mins = String.format("%02d", state.recordDurationSeconds / 60)
                val secs = String.format("%02d", state.recordDurationSeconds % 60)
                TPBadge(text = "$mins:$secs", variant = TPBadgeVariant.BLUE)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 核心录音按钮（按压弹簧缩放反馈）
            val recordInteraction = remember { MutableInteractionSource() }
            val recordPressed by recordInteraction.collectIsPressedAsState()
            val recordScale by animateFloatAsState(
                targetValue = if (recordPressed) 0.97f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                label = "recordScale"
            )
            Button(
                onClick = onToggleRecord,
                interactionSource = recordInteraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .scale(recordScale)
                    .border(1.5.dp, TPNavyDark, RoundedCornerShape(10.dp)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isRecording) TPAccentRed else TPMustardYellow,
                    contentColor = if (state.isRecording) Color.White else TPNavyDark
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = if (state.isRecording) Icons.Outlined.Stop else Icons.Outlined.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (state.isRecording) "停止并保存" else "开始录音",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 动态声波可视化（真实音量历史驱动）
            TPWaveformView(
                volume = state.currentVolume,
                isRecording = state.isRecording,
                barColor = if (state.isRecording) TPMustardYellowDark else Color.LightGray,
                history = state.volumeHistory
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 录音音源（DJI Mic Mini 接收器等外接设备）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.HeadsetMic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "音源:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // 当前实际生效的音源：录音中取系统路由结果，否则取手动选择，
                // 再否则取自动优选（DJI 领夹麦 > USB > 内置）
                val activeDevice = when {
                    state.isRecording -> state.audioDevices.firstOrNull { it.name == state.activeDeviceLabel }
                    state.selectedDeviceId != null -> state.audioDevices.firstOrNull { it.id == state.selectedDeviceId }
                    else -> state.audioDevices.firstOrNull { it.isDji }
                        ?: state.audioDevices.firstOrNull { it.kind == "usb" }
                }
                val externalActive = activeDevice != null && activeDevice.kind != "builtin"
                Box {
                    val currentLabel = when {
                        state.isRecording -> state.activeDeviceLabel
                        state.selectedDeviceId != null ->
                            state.audioDevices.firstOrNull { it.id == state.selectedDeviceId }?.name ?: "指定设备"
                        else -> state.audioDevices.firstOrNull { it.isDji }?.name
                            ?: state.audioDevices.firstOrNull { it.kind == "usb" }?.name
                            ?: "自动（内置麦克风）"
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (externalActive) TPAccentGreenLight else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { showDeviceMenu = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = currentLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (activeDevice?.isDji == true) TPAccentGreen else MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = "切换音源",
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    DropdownMenu(expanded = showDeviceMenu, onDismissRequest = { showDeviceMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("自动优选（DJI/USB 优先）", fontSize = 12.sp) },
                            onClick = { onSelectDevice(null); showDeviceMenu = false },
                            leadingIcon = { Icon(Icons.Filled.AutoAwesome, null, Modifier.size(16.dp)) }
                        )
                        state.audioDevices.forEach { device ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        device.name + if (device.isDji) " · 领夹麦" else "",
                                        fontSize = 12.sp
                                    )
                                },
                                onClick = { onSelectDevice(device.id); showDeviceMenu = false },
                                leadingIcon = {
                                    Icon(
                                        if (device.kind == "builtin") Icons.Filled.PhoneAndroid else Icons.Filled.Usb,
                                        null, Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }
                // 音源状态徽标：正在用领夹麦/外接麦要一眼可见；只是接了没在用则弱提示
                when {
                    activeDevice?.isDji == true ->
                        TPBadge(text = "领夹麦使用中", variant = TPBadgeVariant.GREEN)
                    externalActive ->
                        TPBadge(text = "外接麦使用中", variant = TPBadgeVariant.GREEN)
                    state.audioDevices.any { it.isDji } ->
                        TPBadge(text = "DJI 已接入", variant = TPBadgeVariant.BLUE)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 录音设置默认收起：录音前首屏只留开始按钮与音源；录音中/已有
            // 录音数据时自动展开，也可手动展开（断句、数据量、导出都在里面）
            val showAdvanced = advancedExpanded || state.isRecording || state.totalFrames > 0 || state.utterances.isNotEmpty()
            if (!showAdvanced) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { advancedExpanded = true }
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("录音设置（断句、导出）", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (showAdvanced) {
            // 断句停顿判定 (500ms 紧凑 / 800ms 标准 / 1200ms 从容)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "断句停顿:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                listOf(
                    500 to "500ms 紧凑",
                    800 to "800ms 标准",
                    1200 to "1200ms 从容"
                ).forEach { (ms, label) ->
                    val isSelected = state.sentenceSilenceMs == ms
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .border(
                                1.dp,
                                if (isSelected) TPNavyDark else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                RoundedCornerShape(6.dp)
                            )
                            .background(if (isSelected) TPMustardYellow else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onSetSilence(ms) }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                            color = if (isSelected) TPNavyDark else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // PCM 统计与无损导出
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (state.totalBytes > 0) {
                        val kb = state.totalBytes / 1024f
                        if (kb < 1024f) "已录 %.1f KB".format(kb) else "已录 %.1f MB".format(kb / 1024f)
                    } else "尚未录音",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .clickable(onClick = onExportWav)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = "导出WAV",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "导出无损WAV",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            }
            // 后台保活提示：只在首次录音后出现一次，处理过不再打扰；
            // 配色用警告橙，与品牌黄（主操作/选中态）分工
            if (!state.batteryExempt && (state.isRecording || state.hasRecordedOnce) && !state.batteryHintHandled) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(TPWarningLight)
                        .border(1.dp, TPWarning, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.BatteryAlert,
                        contentDescription = null,
                        tint = TPWarning,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "未加入电池白名单，锁屏长录可能被系统杀后台",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TPNavyDark,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "电池豁免",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = TPNavyDark,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White)
                            .border(1.dp, TPWarning, RoundedCornerShape(6.dp))
                            .clickable { onBatteryExemption(); onBatteryHintHandled() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    Text(
                        text = "自启动",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = TPNavyDark,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White)
                            .border(1.dp, TPWarning, RoundedCornerShape(6.dp))
                            .clickable { onAutoStartSettings(); onBatteryHintHandled() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    Text(
                        text = "知道了",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TPWarningDark,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onBatteryHintHandled() }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // 归档回看横幅
        if (state.viewingSessionTitle != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(TPAccentBlueLight)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Filled.History, null, tint = TPAccentBlue, modifier = Modifier.size(14.dp))
                Text(
                    text = "正在回看：${state.viewingSessionTitle}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TPAccentBlue,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 2. 转写流面板 (Tier 1 & Tier 2)
        TPCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            backgroundColor = MaterialTheme.colorScheme.surface
        ) {
            // 头部小标题与操作
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "实时转写",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${state.utterances.size} 句",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TPAccentBlue
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    // 原文 / 清洗稿切换
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    ) {
                        listOf(false to "原文", true to "清洗稿").forEach { (cleansed, label) ->
                            val selected = state.showCleansed == cleansed
                            Box(
                                modifier = Modifier
                                    .background(if (selected) TPMustardYellow else Color.Transparent)
                                    .clickable { onToggleCleansed(cleansed) }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
                                    color = if (selected) TPNavyDark else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    // 说话人分离
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(enabled = !state.isDiarizing && !state.isRecording) { onDiarize() }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            if (state.isDiarizing) {
                                CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.5.dp)
                            } else {
                                Icon(Icons.Filled.Groups, null, Modifier.size(12.dp))
                            }
                            Text(
                                text = if (state.isDiarizing) "分离中" else "说话人分离",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    // 历史会话
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onRefreshSessions(); showArchive = true }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(Icons.Filled.FolderOpen, null, Modifier.size(12.dp))
                            Text(text = "归档", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (state.isDiarizing && state.diarizeStatus.isNotBlank()) {
                Text(
                    text = state.diarizeStatus,
                    fontSize = 10.sp,
                    color = TPAccentBlue,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            // Tier 1 感知层打字流 (Raw Stream)
            AnimatedVisibility(visible = state.interimText.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = null,
                            tint = TPAccentGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = state.interimText,
                            fontSize = 12.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Tier 2 固化历史 (Keyed LazyColumn)
            if (state.utterances.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.RecordVoiceOver,
                            contentDescription = null,
                            tint = Color.LightGray,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "静候声音输入...",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "点击上方开始录音；插上 DJI 接收器会自动优选领夹麦",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    // 转写卡通铺到毛玻璃导航栏之下：末句留 80dp 才能滚出栏面
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(state.utterances, key = { it.sentenceId }) { u ->
                        // 新句入场走 animateItem 淡入 + 弹簧位移，不再硬切
                        Box(Modifier.animateItem()) {
                            UtteranceItemRow(utterance = u, showCleansed = state.showCleansed)
                        }
                    }
                }
            }
        }

        // 3. 底部快捷操作条：以此人设生成纪要
        if (state.utterances.isNotEmpty() && !state.isRecording) {
            Button(
                onClick = onGenerateMinutes,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .border(1.5.dp, TPNavyDark, RoundedCornerShape(8.dp)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TPNavyDark,
                    contentColor = TPMustardYellow
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "以此生成【${state.currentPersona.name}】结构化纪要",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }

    // 历史会话归档弹窗
    if (showArchive) {
        AlertDialog(
            onDismissRequest = { showArchive = false },
            title = { Text("历史会话归档", fontSize = 15.sp, fontWeight = FontWeight.Black) },
            text = {
                if (state.isLoadingSessions) {
                    // 骨架屏：三行会话占位 + 呼吸式透明度脉动（手写 shimmer，不引额外动效库）
                    val skeletonTransition = rememberInfiniteTransition(label = "archiveSkeleton")
                    val skeletonAlpha by skeletonTransition.animateFloat(
                        initialValue = 0.35f,
                        targetValue = 0.8f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(750),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "archiveSkeletonAlpha"
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        repeat(3) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = skeletonAlpha * 0.16f)
                                    )
                            )
                        }
                    }
                } else if (state.sessions.isEmpty()) {
                    Text("暂无历史会话", fontSize = 12.sp, color = Color.Gray)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(state.sessions.take(40), key = { it.id }) { session ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        onOpenSession(session.id)
                                        showArchive = false
                                    }
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = session.title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2
                                )
                                Text(
                                    text = "${session.stamp.take(16).replace("T", " ")} · ${session.utteranceCount} 句",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showArchive = false }) { Text("关闭") }
            }
        )
    }

    // 首次录音前的麦克风用途说明：讲清录音去向，确认后才调系统权限框
    if (state.micRationaleVisible) {
        ModalBottomSheet(onDismissRequest = onDismissMicRationale) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("开始录音前，先说明一下", fontSize = 15.sp, fontWeight = FontWeight.Black)
                Text(
                    "录音豆需要麦克风权限才能录音。录音只会发送到你自己的服务器（voice.tpgofighting.top）做实时转写，不经过任何第三方，也不会在后台偷偷录音——只有你点下开始、且页面显示录音中时才采集。",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismissMicRationale,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Text("暂不", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onConfirmMicPermission,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TPMustardYellow, contentColor = TPNavyDark)
                    ) {
                        Text("继续，申请权限", fontSize = 13.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun UtteranceItemRow(utterance: Utterance, showCleansed: Boolean = false) {
    val displayText = if (showCleansed && !utterance.cleansedText.isNullOrBlank()) {
        utterance.cleansedText!!
    } else {
        utterance.rawText.ifBlank { utterance.text }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TPBadge(text = "#${utterance.id}", variant = TPBadgeVariant.YELLOW)
                    Text(
                        text = utterance.time,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(speakerColor(utterance.speaker))
                    )
                    Text(
                        text = utterance.speaker,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (utterance.isCleansed) {
                    TPBadge(
                        text = if (showCleansed) "清洗稿展示中" else "清洗稿已存 · 原文展示",
                        variant = TPBadgeVariant.GREEN
                    )
                } else {
                    TPBadge(text = "ASR 定稿", variant = TPBadgeVariant.BLUE)
                }
            }

            Text(
                text = displayText,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

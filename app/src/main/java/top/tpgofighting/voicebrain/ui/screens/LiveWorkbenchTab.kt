package top.tpgofighting.voicebrain.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    var showArchive by remember { mutableStateOf(false) }
    var showSourceSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    // 自动滚动至最新的转写句子
    LaunchedEffect(state.utterances.size, state.interimText) {
        val lastIndex = state.utterances.size - 1 + if (state.interimText.isNotBlank()) 1 else 0
        if (lastIndex >= 0) {
            listState.animateScrollToItem(lastIndex)
        }
    }

    // 当前实际生效的音源（与旧版同一套判定：录音中取系统路由，否则手动选择，再否则自动优选 DJI > USB）
    val activeDevice = when {
        state.isRecording -> state.audioDevices.firstOrNull { it.name == state.activeDeviceLabel }
        state.selectedDeviceId != null -> state.audioDevices.firstOrNull { it.id == state.selectedDeviceId }
        else -> state.audioDevices.firstOrNull { it.isDji }
            ?: state.audioDevices.firstOrNull { it.kind == "usb" }
    }
    val externalActive = activeDevice != null && activeDevice.kind != "builtin"
    val deviceShort = when {
        activeDevice?.isDji == true -> "领夹麦"
        externalActive -> "外接麦"
        else -> "内置麦"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 118.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 麦克风权限被拒后的恢复卡（保持原样）
        if (state.micPermissionDenied && !state.isRecording) {
            val context = LocalContext.current
            TPCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("麦克风权限未开启", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "没有麦克风权限无法录音，请在系统设置里为录音豆开启。",
                            fontSize = 11.sp,
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
                        Text("去设置开启", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = brandAccentColor())
                    }
                }
            }
        }

        // 归档回看横幅
        if (state.viewingSessionTitle != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(TPAccentYellowLight)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Filled.History, null, tint = TPNavyDark, modifier = Modifier.size(14.dp))
                Text(
                    text = "正在回看：${state.viewingSessionTitle}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TPNavyDark,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 状态行：左状态点+状态文，右计时；录音中计时升为 40sp 主角（规格 §2.1）
        Row(
            modifier = Modifier.fillMaxWidth().animateContentSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val dotColor = when {
                state.isRecording -> TPAccentRed
                state.wsStatusConnected -> TPAccentGreen
                else -> Color(0xFF94A3B8)
            }
            val dotPulse = rememberInfiniteTransition(label = "statusDot")
            val dotAlpha by dotPulse.animateFloat(
                initialValue = 1f, targetValue = 0.35f,
                animationSpec = infiniteRepeatable(animation = tween(700), repeatMode = RepeatMode.Reverse),
                label = "statusDotAlpha"
            )
            Box(
                Modifier.size(8.dp).clip(CircleShape)
                    .background(dotColor.copy(alpha = if (state.isRecording) dotAlpha else 1f))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = when {
                    state.isRecording -> "录音中"
                    state.wsStatusConnected -> state.wsStatusText
                    else -> "准备就绪"
                },
                style = TPType.bodySmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.weight(1f))
            val mins = String.format("%02d", state.recordDurationSeconds / 60)
            val secs = String.format("%02d", state.recordDurationSeconds % 60)
            Text(
                text = "$mins:$secs",
                style = if (state.isRecording) TPType.timer else TPType.timerInline
            )
        }

        // 后台保活提示（内容与动作沿用旧版，容器从卡内改为独立横幅）
        if (!state.batteryExempt && (state.isRecording || state.hasRecordedOnce) && !state.batteryHintHandled) {
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
                Icon(Icons.Filled.BatteryAlert, contentDescription = null, tint = TPWarning, modifier = Modifier.size(16.dp))
                Text(
                    text = "未加入电池白名单，锁屏长录可能被系统杀后台",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TPNavyDark,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "电池豁免",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
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
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
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
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TPWarningDark,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onBatteryHintHandled() }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }

        // 波形带：全宽出血，高 48dp
        TPWaveformView(
            volume = state.currentVolume,
            isRecording = state.isRecording,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            barColor = if (state.isRecording) TPMustardYellowDark else Color(0xFFCBD5E1),
            history = state.volumeHistory
        )

        if (state.isDiarizing && state.diarizeStatus.isNotBlank()) {
            Text(
                text = state.diarizeStatus,
                fontSize = 11.sp,
                color = brandAccentColor(),
                fontWeight = FontWeight.SemiBold
            )
        }

        // 转写工具行：仅有内容时出现（规格 §2.1）——左迷你分段，右分离/归档图标钮
        if (state.utterances.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(TPRadius.sm))
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f), RoundedCornerShape(TPRadius.sm))
                ) {
                    listOf(false to "原文", true to "清洗稿").forEach { (cleansed, label) ->
                        val selected = state.showCleansed == cleansed
                        Box(
                            modifier = Modifier
                                .height(28.dp)
                                .background(if (selected) TPNavyDark else Color.Transparent)
                                .clickable { onToggleCleansed(cleansed) }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = TPType.label,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(TPRadius.sm))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(enabled = !state.isDiarizing && !state.isRecording) { onDiarize() },
                    contentAlignment = Alignment.Center
                ) {
                    if (state.isDiarizing) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp)
                    } else {
                        Icon(Icons.Filled.Groups, contentDescription = "说话人分离", modifier = Modifier.size(17.dp))
                    }
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(TPRadius.sm))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onRefreshSessions(); showArchive = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = "归档", modifier = Modifier.size(17.dp))
                }
            }
        }

        // 转写流：无卡文档流，正文 15sp/24sp；空状态居中
        if (state.utterances.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    WaveGlyph(
                        modifier = Modifier.width(148.dp).height(40.dp),
                        color = brandAccentColor()
                    )
                    Text(
                        text = "静候声音输入...",
                        style = TPType.body,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "点下方按钮开始录音",
                        style = TPType.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(state.utterances, key = { it.sentenceId }) { u ->
                    Box(Modifier.animateItem()) {
                        UtteranceItemRow(utterance = u, showCleansed = state.showCleansed)
                    }
                }
                if (state.interimText.isNotBlank()) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.GraphicEq,
                                contentDescription = null,
                                tint = TPAccentGreen,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = state.interimText,
                                style = TPType.bodyMain.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 生成纪要：内容就绪且未录音时，以文字钮形态出现在坞上方右侧
        if (state.utterances.isNotEmpty() && !state.isRecording) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onGenerateMinutes) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp), tint = brandAccentColor())
                    Spacer(Modifier.width(4.dp))
                    Text("以此生成结构化纪要", style = TPType.label, color = brandAccentColor())
                }
            }
        }

        // 录音坞：悬浮于底栏上方，左音源 / 中主按钮 / 右设置（规格 §2.1）
        RecordingDock(
            isRecording = state.isRecording,
            deviceShort = deviceShort,
            externalActive = externalActive,
            onToggleRecord = onToggleRecord,
            onOpenSource = { showSourceSheet = true },
            onOpenSettings = { showSettingsSheet = true }
        )
    }

    // 音源选择底部弹层（行高 56dp）
    if (showSourceSheet) {
        ModalBottomSheet(onDismissRequest = { showSourceSheet = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("选择音源", style = TPType.sectionTitle)
                Text(
                    "插上 DJI 接收器时，自动优选会优先领夹麦。",
                    style = TPType.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                SourceRow(
                    label = "自动优选（DJI/USB 优先）",
                    selected = state.selectedDeviceId == null,
                    onClick = { onSelectDevice(null); showSourceSheet = false }
                )
                state.audioDevices.forEach { device ->
                    SourceRow(
                        label = device.name + if (device.isDji) " · 领夹麦" else "",
                        selected = state.selectedDeviceId == device.id,
                        onClick = { onSelectDevice(device.id); showSourceSheet = false }
                    )
                }
            }
        }
    }

    // 录音设置底部弹层：断句 / 展示 / 导出（设置页仍是唯一真源，这里是录音现场的快捷面）
    if (showSettingsSheet) {
        ModalBottomSheet(onDismissRequest = { showSettingsSheet = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("录音设置", style = TPType.sectionTitle)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("断句停顿", style = TPType.rowTitle)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(500 to "500ms 紧凑", 800 to "800ms 标准", 1200 to "1200ms 从容").forEach { (ms, label) ->
                            val selected = state.sentenceSilenceMs == ms
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(TPRadius.sm))
                                    .background(if (selected) TPNavyDark else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { onSetSilence(ms) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = TPType.label,
                                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("转写展示", style = TPType.rowTitle)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(false to "原文", true to "清洗稿").forEach { (cleansed, label) ->
                            val selected = state.showCleansed == cleansed
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(TPRadius.sm))
                                    .background(if (selected) TPNavyDark else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { onToggleCleansed(cleansed) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = TPType.label,
                                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(TPRadius.sm))
                        .clickable { onExportWav(); showSettingsSheet = false }
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(19.dp))
                    Column {
                        Text("导出无损 WAV", style = TPType.rowTitle)
                        Text(
                            text = if (state.totalBytes > 0) {
                                val kb = state.totalBytes / 1024f
                                if (kb < 1024f) "已录 %.1f KB".format(kb) else "已录 %.1f MB".format(kb / 1024f)
                            } else "尚未录音",
                            style = TPType.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // 历史会话归档弹窗
    if (showArchive) {
        AlertDialog(
            onDismissRequest = { showArchive = false },
            title = { Text("历史会话归档", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
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
                                    fontSize = 11.sp,
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
                Text("开始录音前，先说明一下", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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
                        Text("继续，申请权限", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
    // 文档流行：说话人色点 + 名字 + 时间一行，正文 15sp/24sp，无卡无边（规格 §2.1）
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(speakerColor(utterance.speaker))
            )
            Text(
                text = utterance.speaker,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = utterance.time,
                style = TPType.caption,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            if (utterance.isCleansed) {
                Text(
                    text = if (showCleansed) "清洗稿" else "清洗稿已存",
                    style = TPType.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = displayText,
            style = TPType.bodyMain,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** 录音坞左/右列与音源弹层共用的 56dp 选择行 */
@Composable
private fun SourceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(TPRadius.sm))
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = TPType.body, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp), tint = brandAccentColor())
        }
    }
}

/**
 * 悬浮录音坞（规格 §2.1）：高 80dp、radius-lg 浮层材质；
 * 左音源列（外接时绿点）、中 64dp 圆形主按钮（待机品牌黄+藏青麦；录音中藏青+白色停止块+黄色脉冲环）、右设置列。
 */
@Composable
private fun RecordingDock(
    isRecording: Boolean,
    deviceShort: String,
    externalActive: Boolean,
    onToggleRecord: () -> Unit,
    onOpenSource: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val darkSurface = MaterialTheme.colorScheme.surface == TPCardDark
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .shadow(8.dp, RoundedCornerShape(TPRadius.lg), clip = false)
            .background(if (darkSurface) TPNavySurface else Color.White, RoundedCornerShape(TPRadius.lg))
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(TPRadius.lg))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(64.dp)
                .clip(RoundedCornerShape(TPRadius.sm))
                .clickable { onOpenSource() }
                .padding(vertical = 6.dp)
        ) {
            Box {
                Icon(
                    imageVector = Icons.Outlined.HeadsetMic,
                    contentDescription = "切换音源",
                    modifier = Modifier.size(22.dp)
                )
                if (externalActive) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(TPAccentGreen)
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(text = deviceShort, style = TPType.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.weight(1f))

        Box(contentAlignment = Alignment.Center) {
            if (isRecording) {
                val pulseTransition = rememberInfiniteTransition(label = "dockPulse")
                val pulseScale by pulseTransition.animateFloat(
                    initialValue = 1f, targetValue = 1.38f,
                    animationSpec = infiniteRepeatable(animation = tween(1200), repeatMode = RepeatMode.Restart),
                    label = "dockPulseScale"
                )
                val pulseAlpha by pulseTransition.animateFloat(
                    initialValue = 0.55f, targetValue = 0f,
                    animationSpec = infiniteRepeatable(animation = tween(1200), repeatMode = RepeatMode.Restart),
                    label = "dockPulseAlpha"
                )
                Box(
                    Modifier
                        .size(64.dp)
                        .scale(pulseScale)
                        .border(2.dp, TPMustardYellow.copy(alpha = pulseAlpha), CircleShape)
                )
            }
            val interaction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .pressScale(interaction, 0.94f)
                    .clip(CircleShape)
                    .background(if (isRecording) TPNavyDark else TPMustardYellow)
                    .border(1.5.dp, TPNavyDark, CircleShape)
                    .clickable(interactionSource = interaction, indication = null) { onToggleRecord() }
                    .semantics { contentDescription = if (isRecording) "停止录音" else "开始录音" },
                contentAlignment = Alignment.Center
            ) {
                if (isRecording) {
                    Box(Modifier.size(20.dp).background(Color.White, RoundedCornerShape(4.dp)))
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                        tint = TPNavyDark
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(64.dp)
                .clip(RoundedCornerShape(TPRadius.sm))
                .clickable { onOpenSettings() }
                .padding(vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.MoreHoriz,
                contentDescription = "录音设置",
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.height(2.dp))
            Text(text = "设置", style = TPType.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

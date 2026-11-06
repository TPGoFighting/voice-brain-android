package top.tpgofighting.voicebrain.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
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
    onAutoStartSettings: () -> Unit = {}
) {
    val listState = rememberLazyListState()
    var showDeviceMenu by remember { mutableStateOf(false) }
    var showArchive by remember { mutableStateOf(false) }

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
                    TPBadge(text = state.wsStatusText, variant = statusVariant)
                    TPBadge(text = "16kHz PCM", variant = TPBadgeVariant.OUTLINE)
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
                        imageVector = if (state.isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (state.isRecording) "停止录音并沉淀 (Stop)" else "开始实时录音 (Start Recording)",
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
                    imageVector = Icons.Filled.HeadsetMic,
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
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { showDeviceMenu = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = currentLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
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
                if (state.audioDevices.any { it.isDji }) {
                    TPBadge(text = "DJI 已接入", variant = TPBadgeVariant.GREEN)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

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
                    text = "${state.totalFrames} 帧 (${(state.totalBytes / 1024f).let { String.format("%.1f", it) }} KB)",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
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

            // 后台保活提示（未豁免电池优化时）
            if (!state.batteryExempt) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(TPAccentYellowLight)
                        .border(1.dp, TPMustardYellowDark, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.BatteryAlert,
                        contentDescription = null,
                        tint = TPMustardYellowDark,
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
                            .background(TPMustardYellow)
                            .clickable { onBatteryExemption() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    Text(
                        text = "自启动",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = TPNavyDark,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(TPMustardYellow)
                            .clickable { onAutoStartSettings() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
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
                        text = "分层流式 ASR",
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
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(state.utterances, key = { it.sentenceId }) { u ->
                        UtteranceItemRow(utterance = u, showCleansed = state.showCleansed)
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
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp))
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

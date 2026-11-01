package top.tpgofighting.voicebrain.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.tpgofighting.voicebrain.model.Utterance
import top.tpgofighting.voicebrain.ui.components.*
import top.tpgofighting.voicebrain.ui.theme.*
import top.tpgofighting.voicebrain.ui.viewmodel.MainUiState

@Composable
fun LiveWorkbenchTab(
    state: MainUiState,
    onToggleRecord: () -> Unit,
    onExportWav: () -> Unit,
    onGenerateMinutes: () -> Unit
) {
    val listState = rememberLazyListState()

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

            // 核心录音按钮 (大尺寸触觉反馈)
            Button(
                onClick = onToggleRecord,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
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

            // 动态声波可视化
            TPWaveformView(
                volume = state.currentVolume,
                isRecording = state.isRecording,
                barColor = if (state.isRecording) TPMustardYellowDark else Color.LightGray
            )

            Spacer(modifier = Modifier.height(6.dp))

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
        }

        // 2. 转写流面板 (Tier 1 & Tier 2)
        TPCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            backgroundColor = MaterialTheme.colorScheme.surface
        ) {
            // 头部小标题
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
                    TPBadge(text = "Tier 1: fun-asr", variant = TPBadgeVariant.GREEN)
                    TPBadge(text = "Tier 2: qwen-flash", variant = TPBadgeVariant.PURPLE)
                }
                Text(
                    text = "${state.utterances.size} 句",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TPAccentBlue
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
                            text = "点击上方开始录音，每句话自动原地快修",
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
                        UtteranceItemRow(utterance = u)
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
}

@Composable
fun UtteranceItemRow(utterance: Utterance) {
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
                    Text(
                        text = utterance.speaker,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (utterance.isCleansed) {
                    TPBadge(text = "qwen-flash 快修 (${utterance.latencyMs}ms)", variant = TPBadgeVariant.GREEN)
                } else {
                    TPBadge(text = "ASR 定稿", variant = TPBadgeVariant.BLUE)
                }
            }

            Text(
                text = utterance.text,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

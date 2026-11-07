package top.tpgofighting.voicebrain.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.Brain
import com.composables.icons.lucide.Network
import com.composables.icons.lucide.Lucide
import top.tpgofighting.voicebrain.model.LmmWikiEntity
import top.tpgofighting.voicebrain.model.ObsidianNote
import top.tpgofighting.voicebrain.ui.components.*
import top.tpgofighting.voicebrain.ui.theme.*
import top.tpgofighting.voicebrain.ui.viewmodel.MainUiState

@OptIn(ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ObsidianWikiTab(
    state: MainUiState,
    onToggleAutoWriteBack: () -> Unit,
    onAskObsidian: (String) -> Unit,
    onStopObsidian: () -> Unit,
    onOpenVaultNote: (String) -> Unit,
    onCloseVaultNote: () -> Unit,
    onRefreshObsidianStatus: () -> Unit
) {
    var selectedSection by remember { mutableStateOf(0) } // 0: Hermes问答对账, 1: 笔记与实体网络
    var showVaultPath by remember { mutableStateOf(false) } // 服务器绝对路径默认不展示，点按才展开

    // 进入本页时若状态尚未加载成功（如启动时离线），自动重试一次
    LaunchedEffect(state.obsidianStatus.loaded) {
        if (!state.obsidianStatus.loaded) onRefreshObsidianStatus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. 页眉：标题行 + 状态一行化（规格 §2.5，真实数据；未知就写未知，不伪装）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Lucide.Network, contentDescription = null, tint = brandAccentColor(), modifier = Modifier.size(18.dp))
                Text("Obsidian Vault", style = TPType.rowTitle)
            }
            TPBadge(
                text = when {
                    !state.obsidianStatus.loaded -> "状态未知"
                    state.obsidianStatus.connected -> "已挂载"
                    else -> "未连接"
                },
                variant = if (state.obsidianStatus.loaded && state.obsidianStatus.connected) TPBadgeVariant.GREEN else TPBadgeVariant.DARK
            )
        }
        // 状态一行：笔记 · 录音归档 · Wiki 节点（节点数 = 实体列表长度，真实计数）
        val pathVisible = showVaultPath && state.obsidianStatus.vaultPath.isNotBlank()
        Text(
            text = when {
                pathVisible -> state.obsidianStatus.vaultPath
                !state.obsidianStatus.loaded -> "状态确认中，点此重试"
                state.obsidianStatus.connected -> "笔记 ${"%,d".format(state.obsidianStatus.totalNotes)} · 录音归档 ${state.obsidianStatus.syncedVoiceNotes} · Wiki 节点 ${state.obsidianEntities.size}"
                else -> "未连接，点此重试"
            },
            style = TPType.caption,
            fontFamily = if (pathVisible) FontFamily.Monospace else FontFamily.Default,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable(
                enabled = state.obsidianStatus.loaded && state.obsidianStatus.vaultPath.isNotBlank()
            ) { showVaultPath = !showVaultPath }
        )
        if (!state.obsidianStatus.loaded || !state.obsidianStatus.connected) {
            Text(
                text = "点状态行或右上角刷新重试",
                style = TPType.caption,
                color = brandAccentColor(),
                modifier = Modifier.clickable { onRefreshObsidianStatus() }
            )
        }

        // Hermes 反向追记：独立开关行（标签 + 说明在左，开关在右）
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Hermes 反向追记", style = TPType.rowTitle)
                Text(
                    "开启后，回答会追加写回笔记末尾",
                    style = TPType.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.autoWriteBackEnabled,
                onCheckedChange = { onToggleAutoWriteBack() }
            )
        }

        // 2. 移动端次级分栏 (Hermes 问答 VS 知识实体与笔记)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(TPRadius.md))
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(TPRadius.md))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Button(
                onClick = { selectedSection = 0 },
                modifier = Modifier.weight(1f).height(30.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedSection == 0) MaterialTheme.colorScheme.surface else Color.Transparent,
                    contentColor = if (selectedSection == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("Hermes Agent 问答", style = TPType.label)
            }

            Button(
                onClick = { selectedSection = 1 },
                modifier = Modifier.weight(1f).height(30.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedSection == 1) MaterialTheme.colorScheme.surface else Color.Transparent,
                    contentColor = if (selectedSection == 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("双链实体与同步笔记", style = TPType.label)
            }
        }

        // 3. 内容展示区
        if (selectedSection == 0) {
            // v1.3.5: Agent 对话区按 xchange 助手设计重做（独立文件 ObsidianAgentChat.kt）
            ObsidianAgentChat(
                state = state,
                onAskObsidian = onAskObsidian,
                onStopObsidian = onStopObsidian,
                onOpenVaultNote = onOpenVaultNote,
                onCloseVaultNote = onCloseVaultNote,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        } else {
            // 双链实体与同步笔记列表
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                // 内容通铺到悬浮毛玻璃导航栏之下：末项留 96dp 才能滚出栏面
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                // 已同步录音材料笔记
                item {
                    Text("已同步录音笔记", style = TPType.rowTitle)
                }

                items(state.obsidianNotes, key = { it.path }) { note ->
                    Column(modifier = Modifier.animateItem().fillMaxWidth().padding(vertical = 10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(note.title, style = TPType.body, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            TPBadge(text = note.persona, variant = TPBadgeVariant.YELLOW)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(note.path, style = TPType.caption, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                }

                // LMMWiki 核心实体
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Wiki 核心实体与概念", style = TPType.rowTitle)
                }

                items(state.obsidianEntities, key = { it.name }) { entity ->
                    Column(modifier = Modifier.animateItem().fillMaxWidth().padding(vertical = 10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(entity.name, style = TPType.body, fontWeight = FontWeight.SemiBold, color = brandAccentColor())
                            TPBadge(text = entity.category, variant = TPBadgeVariant.BLUE)
                        }
                        if (entity.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(entity.description, style = TPType.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                }
            }
        }
    }
}

package top.tpgofighting.voicebrain.ui.screens

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
import top.tpgofighting.voicebrain.model.LmmWikiEntity
import top.tpgofighting.voicebrain.model.ObsidianNote
import top.tpgofighting.voicebrain.ui.components.*
import top.tpgofighting.voicebrain.ui.theme.*
import top.tpgofighting.voicebrain.ui.viewmodel.MainUiState

@Composable
fun ObsidianWikiTab(
    state: MainUiState,
    onToggleAutoWriteBack: () -> Unit,
    onAskObsidian: (String) -> Unit,
    onRefreshObsidianStatus: () -> Unit
) {
    var queryText by remember { mutableStateOf("") }
    var selectedSection by remember { mutableStateOf(0) } // 0: Hermes问答对账, 1: 笔记与实体网络
    var showVaultPath by remember { mutableStateOf(false) } // 服务器绝对路径默认不展示，点按才展开

    // 进入本页时若状态尚未加载成功（如启动时离线），自动重试一次
    LaunchedEffect(state.obsidianStatus.loaded) {
        if (!state.obsidianStatus.loaded) onRefreshObsidianStatus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Obsidian Vault 状态看板
        TPCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.AccountTree, contentDescription = null, tint = TPAccentPurple, modifier = Modifier.size(18.dp))
                    Text("Obsidian Vault & LMMWiki", fontSize = 13.sp, fontWeight = FontWeight.Black)
                }
                TPBadge(
                    text = when {
                        !state.obsidianStatus.loaded -> "状态未知"
                        state.obsidianStatus.connected -> "Vault 已挂载"
                        else -> "未连接"
                    },
                    variant = if (state.obsidianStatus.loaded && state.obsidianStatus.connected) TPBadgeVariant.GREEN else TPBadgeVariant.DARK
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            // 默认只说人话；服务器绝对路径是部署细节，点按这一行才展开查看
            val pathVisible = showVaultPath && state.obsidianStatus.vaultPath.isNotBlank()
            Text(
                text = if (pathVisible) state.obsidianStatus.vaultPath else "我的 Obsidian 仓库 · 与服务器实时同步",
                fontSize = if (pathVisible) 9.sp else 10.sp,
                fontFamily = if (pathVisible) FontFamily.Monospace else FontFamily.Default,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(
                    enabled = state.obsidianStatus.loaded && state.obsidianStatus.vaultPath.isNotBlank()
                ) { showVaultPath = !showVaultPath }
            )

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.obsidianStatus.loaded) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("笔记总数: ${state.obsidianStatus.totalNotes}", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("·", fontSize = 10.sp)
                        Text("录音归档: ${state.obsidianStatus.syncedVoiceNotes}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TPAccentBlue)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("状态未知", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "重试",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TPAccentBlue,
                            modifier = Modifier.clickable { onRefreshObsidianStatus() }
                        )
                    }
                }

                // Hermes 反向追记开关
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Hermes 反向追记", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = state.autoWriteBackEnabled,
                        onCheckedChange = { onToggleAutoWriteBack() },
                        modifier = Modifier.height(24.dp)
                    )
                }
            }
        }

        // 2. 移动端次级分栏 (Hermes 问答 VS 知识实体与笔记)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
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
                Text("Hermes Agent 问答", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                Text("双链实体与同步笔记", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // 3. 内容展示区
        if (selectedSection == 0) {
            // Hermes 问答对话流
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.obsidianChatHistory, key = { it.id }) { msg ->
                        ChatBubbleRow(msg)
                    }

                    if (state.isObsidianThinking) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = TPAccentPurple)
                                    Text("Hermes Agent 正在检索 Obsidian Vault 多跳双链...", fontSize = 11.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                }
                            }
                        }
                    }
                }

                // 提问输入框
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = queryText,
                        onValueChange = { queryText = it },
                        modifier = Modifier.weight(1f).height(46.dp),
                        placeholder = { Text("向 Obsidian 知识库提问...", fontSize = 11.sp) },
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            if (queryText.isNotBlank()) {
                                onAskObsidian(queryText)
                                queryText = ""
                            }
                        },
                        modifier = Modifier.height(46.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TPNavyDark, contentColor = TPMustardYellow)
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = "提问", modifier = Modifier.size(16.dp))
                    }
                }
            }
        } else {
            // 双链实体与同步笔记列表
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 已同步录音材料笔记
                item {
                    Text("已同步录音笔记 (01-Raw/录音材料)", fontSize = 12.sp, fontWeight = FontWeight.Black)
                }

                items(state.obsidianNotes, key = { it.path }) { note ->
                    TPCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(note.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            TPBadge(text = note.persona, variant = TPBadgeVariant.YELLOW)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(note.path, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // LMMWiki 核心实体
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("LMMWiki 核心实体与概念", fontSize = 12.sp, fontWeight = FontWeight.Black)
                }

                items(state.obsidianEntities, key = { it.name }) { entity ->
                    TPCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(entity.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TPAccentBlue)
                            TPBadge(text = entity.category, variant = TPBadgeVariant.BLUE)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(entity.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

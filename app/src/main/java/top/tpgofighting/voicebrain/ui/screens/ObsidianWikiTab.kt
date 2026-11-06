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
                    Icon(Lucide.Network, contentDescription = null, tint = brandAccentColor(), modifier = Modifier.size(18.dp))
                    Text("Obsidian Vault", fontSize = 13.sp, fontWeight = FontWeight.Black)
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
                text = if (pathVisible) state.obsidianStatus.vaultPath else when {
                    !state.obsidianStatus.loaded -> "我的 Obsidian 仓库 · 状态确认中"
                    state.obsidianStatus.connected -> "我的 Obsidian 仓库 · 与服务器实时同步"
                    else -> "我的 Obsidian 仓库 · 未连接"
                },
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
                        Text("录音归档: ${state.obsidianStatus.syncedVoiceNotes}", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "点此重试",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = brandAccentColor(),
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
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
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
                // 分工说明：与记忆页的录音问答区分开，一句话讲清
                Text(
                    "查仓库笔记正文；录音内容去「记忆大脑」页问。",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // 示例问题（与第二大脑页对齐的首轮脚手架，批次 R 补齐）
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AgentSuggestionChip(text = "仓库里最近在更新什么主题", onClick = { onAskObsidian("仓库里最近在更新什么主题") })
                    AgentSuggestionChip(text = "LLM-Wiki 的核心概念是什么", onClick = { onAskObsidian("LLM-Wiki 的核心概念是什么") })
                    AgentSuggestionChip(text = "我的项目笔记里有哪些待办", onClick = { onAskObsidian("我的项目笔记里有哪些待办") })
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.obsidianChatHistory, key = { it.id }) { msg ->
                        ChatBubbleRow(msg, agentName = "Hermes Agent")
                    }

                    if (state.isObsidianThinking) {
                        // 与记忆页统一的思考态：Agent 名称行 + 柔面气泡里的 Lottie 三点
                        item {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.padding(start = 2.dp)
                                ) {
                                    Icon(Lucide.Brain, contentDescription = null, modifier = Modifier.size(14.dp), tint = brandAccentColor())
                                    Text("Hermes Agent", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = brandAccentColor())
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                            RoundedCornerShape(topStart = 6.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    AgentTypingIndicator(label = "正在检索仓库笔记与双链")
                                }
                            }
                        }
                    }
                }

                // 提问输入框（固定底栏：内容通铺后自带底部间距，停在毛玻璃导航栏上方）
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 100.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = queryText,
                        onValueChange = { queryText = it },
                        modifier = Modifier.weight(1f).height(52.dp),
                        placeholder = { Text("查仓库笔记，如：点云论文笔记在哪", fontSize = 11.sp) },
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
                        modifier = Modifier.size(46.dp),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (queryText.isBlank()) MaterialTheme.colorScheme.surfaceVariant else TPNavyDark,
                            contentColor = if (queryText.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else TPMustardYellow
                        )
                    ) {
                        Icon(Lucide.ArrowUp, contentDescription = "提问", modifier = Modifier.size(18.dp))
                    }
                }
            }
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
                    Text("已同步录音笔记 (01-Raw/录音材料)", fontSize = 12.sp, fontWeight = FontWeight.Black)
                }

                items(state.obsidianNotes, key = { it.path }) { note ->
                    TPCard(modifier = Modifier.animateItem().fillMaxWidth()) {
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
                    TPCard(modifier = Modifier.animateItem().fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(entity.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = brandAccentColor())
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

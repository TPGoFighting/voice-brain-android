package top.tpgofighting.voicebrain.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.tpgofighting.voicebrain.model.BrainChatMessage
import top.tpgofighting.voicebrain.model.MemoryItem
import top.tpgofighting.voicebrain.ui.components.*
import top.tpgofighting.voicebrain.ui.theme.*
import top.tpgofighting.voicebrain.ui.viewmodel.MainUiState

@Composable
fun MemoryBrainTab(
    state: MainUiState,
    onSetSubTab: (Int) -> Unit,
    onSetCategory: (String) -> Unit,
    onSetSearchQuery: (String) -> Unit,
    onAskSecondBrain: (String) -> Unit
) {
    var inputQuery by remember { mutableStateOf("") }
    val chatListState = rememberLazyListState()

    LaunchedEffect(state.brainChatHistory.size) {
        if (state.brainChatHistory.isNotEmpty()) {
            chatListState.animateScrollToItem(state.brainChatHistory.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. 移动端双栏切页 (记忆库 VS 问问第二大脑 Agent)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Button(
                onClick = { onSetSubTab(0) },
                modifier = Modifier.weight(1f).height(32.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.memorySubTab == 0) MaterialTheme.colorScheme.surface else Color.Transparent,
                    contentColor = if (state.memorySubTab == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(14.dp))
                    Text("记忆资产库 (${state.memories.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = { onSetSubTab(1) },
                modifier = Modifier.weight(1f).height(32.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.memorySubTab == 1) MaterialTheme.colorScheme.surface else Color.Transparent,
                    contentColor = if (state.memorySubTab == 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.SmartToy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Text("问问你的第二大脑", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 2. 子面板 A: 记忆资产库列表
        if (state.memorySubTab == 0) {
            // 搜索框
            OutlinedTextField(
                value = state.memorySearchQuery,
                onValueChange = onSetSearchQuery,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                placeholder = { Text("搜索长期记忆、方案、决策标签...", fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                shape = RoundedCornerShape(8.dp),
                singleLine = true
            )

            // 分类筛选 Chip 滚动条
            val categories = listOf("全部", "偏好画像", "项目方案", "重大决策", "人物承诺", "客观经验")
            val catMap = mapOf(
                "全部" to "全部",
                "偏好画像" to "preference",
                "项目方案" to "project",
                "重大决策" to "decision",
                "人物承诺" to "person",
                "客观经验" to "fact"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categories.forEach { cat ->
                    val isSelected = state.selectedMemoryCategory == cat
                    Box(
                        modifier = Modifier
                            .border(
                                1.dp,
                                if (isSelected) TPMustardYellowDark else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(16.dp)
                            )
                            .background(
                                if (isSelected) TPMustardYellow else MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(16.dp)
                            )
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSetCategory(cat) }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = cat,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                            color = if (isSelected) TPNavyDark else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // 过滤匹配的记忆列表
            val filteredMemories = state.memories.filter { m ->
                val matchCat = when (state.selectedMemoryCategory) {
                    "全部" -> true
                    "偏好画像" -> m.category == "preference"
                    "项目方案" -> m.category == "project"
                    "重大决策" -> m.category == "decision"
                    "人物承诺" -> m.category == "person"
                    "客观经验" -> m.category == "fact"
                    else -> true
                }
                val matchQuery = state.memorySearchQuery.isBlank() ||
                        m.content.contains(state.memorySearchQuery, ignoreCase = true) ||
                        m.tags.any { it.contains(state.memorySearchQuery, ignoreCase = true) }
                matchCat && matchQuery
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredMemories, key = { it.id }) { item ->
                    MemoryCardItem(item)
                }
            }
        } else {
            // 3. 子面板 B: 问问你的第二大脑 Agent
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 快捷提问胶囊
                val suggestions = listOf(
                    "总结我最近关于全双工架构的偏好",
                    "列出近期承诺的待办事项",
                    "回顾上周所有重大决策"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    suggestions.forEach { prompt ->
                        Box(
                            modifier = Modifier
                                .border(1.dp, TPAccentPurple.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                .background(TPAccentPurpleLight, RoundedCornerShape(14.dp))
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onAskSecondBrain(prompt) }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(prompt, fontSize = 10.sp, color = TPAccentPurple, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // 对话历史 LazyColumn
                LazyColumn(
                    state = chatListState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.brainChatHistory, key = { it.id }) { msg ->
                        ChatBubbleRow(msg)
                    }

                    if (state.isBrainThinking) {
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
                                    Text("第二大脑正在跨会话检索历史记忆与决策...", fontSize = 11.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
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
                        value = inputQuery,
                        onValueChange = { inputQuery = it },
                        modifier = Modifier.weight(1f).height(46.dp),
                        placeholder = { Text("问问第二大脑...", fontSize = 11.sp) },
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            if (inputQuery.isNotBlank()) {
                                onAskSecondBrain(inputQuery)
                                inputQuery = ""
                            }
                        },
                        modifier = Modifier.height(46.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TPNavyDark, contentColor = TPMustardYellow)
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = "发送", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun MemoryCardItem(item: MemoryItem) {
    TPCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (badgeText, badgeVariant) = when (item.category) {
                "preference" -> Pair("偏好画像", TPBadgeVariant.YELLOW)
                "project" -> Pair("项目方案", TPBadgeVariant.BLUE)
                "decision" -> Pair("重大决策", TPBadgeVariant.GREEN)
                "person" -> Pair("人物承诺", TPBadgeVariant.PURPLE)
                else -> Pair("客观经验", TPBadgeVariant.DARK)
            }
            TPBadge(text = badgeText, variant = badgeVariant)

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(item.importance) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = TPMustardYellowDark, modifier = Modifier.size(12.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.content,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (item.tags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                item.tags.forEach { tag ->
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("#$tag", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubbleRow(msg: BrainChatMessage) {
    val isUser = msg.isUser
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .border(
                    1.dp,
                    if (isUser) TPMustardYellowDark else MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(8.dp)
                )
                .background(
                    if (isUser) TPMustardYellow else MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(8.dp)
                )
                .padding(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUser) "你" else "第二大脑 Agent",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isUser) TPNavyDark else TPAccentPurple
                    )
                    Text(
                        text = msg.timestamp,
                        fontSize = 8.sp,
                        color = if (isUser) TPNavyDark.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = msg.content,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = if (isUser) TPNavyDark else MaterialTheme.colorScheme.onSurface
                )

                if (msg.citations.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        msg.citations.forEach { cit ->
                            TPBadge(text = "引用: $cit", variant = TPBadgeVariant.PURPLE)
                        }
                    }
                }
            }
        }
    }
}

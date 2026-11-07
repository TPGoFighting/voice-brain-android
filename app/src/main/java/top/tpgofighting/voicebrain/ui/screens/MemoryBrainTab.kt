package top.tpgofighting.voicebrain.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
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
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.Brain
import com.composables.icons.lucide.Lucide
import com.mikepenz.markdown.m3.Markdown
import top.tpgofighting.voicebrain.ui.components.*
import top.tpgofighting.voicebrain.ui.theme.*
import top.tpgofighting.voicebrain.ui.viewmodel.MainUiState

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun MemoryBrainTab(
    state: MainUiState,
    onSetSubTab: (Int) -> Unit,
    onSetCategory: (String) -> Unit,
    onSetSearchQuery: (String) -> Unit,
    onAskSecondBrain: (String) -> Unit,
    onSetFullTextQuery: (String) -> Unit = {},
    onRunSearch: () -> Unit = {},
    onOpenSession: (String) -> Unit = {},
    onGoRecord: () -> Unit = {}
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. 移动端双栏切页 (记忆库 VS 问问第二大脑 Agent)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
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
                    Text("记忆 (${state.memories.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                    Text("问第二大脑", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = { onSetSubTab(2) },
                modifier = Modifier.weight(1f).height(32.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.memorySubTab == 2) MaterialTheme.colorScheme.surface else Color.Transparent,
                    contentColor = if (state.memorySubTab == 2) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.ManageSearch, contentDescription = null, modifier = Modifier.size(14.dp))
                    Text("搜逐字稿", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 2. 子面板 A: 记忆资产库列表
        if (state.memorySubTab == 0) {
            // 搜索框
            OutlinedTextField(
                value = state.memorySearchQuery,
                onValueChange = onSetSearchQuery,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                placeholder = { Text("只在记忆里筛选，如：决策、承诺...", fontSize = 11.sp) },
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
                    .horizontalScroll(rememberScrollState())
                    .padding(end = 12.dp), // 滚动内容尾部留白，最后一颗分类可完整滑入
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categories.forEach { cat ->
                    val isSelected = state.selectedMemoryCategory == cat
                    Box(
                        modifier = Modifier
                            .background(
                                if (isSelected) TPNavyDark else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(TPRadius.sm)
                            )
                            .clip(RoundedCornerShape(TPRadius.sm))
                            .clickable { onSetCategory(cat) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = cat,
                            style = TPType.label,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
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
                verticalArrangement = Arrangement.spacedBy(0.dp),
                // 内容通铺到悬浮毛玻璃导航栏之下：末项留 96dp 才能滚出栏面
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                if (filteredMemories.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (state.memories.isEmpty()) {
                                // 简笔画线条动画：单色声波线按主题染色（与录音页空状态统一）
                                WaveGlyph(
                                    modifier = Modifier.width(148.dp).height(40.dp),
                                    color = brandAccentColor()
                                )
                                Text(
                                    "还没有沉淀任何记忆",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "录一段音并生成纪要后，其中的决策、承诺和偏好会自动提炼到这里，越用越懂你。",
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val goRecordInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                Button(
                                    onClick = onGoRecord,
                                    interactionSource = goRecordInteraction,
                                    modifier = Modifier.pressScale(goRecordInteraction),
                                    shape = RoundedCornerShape(999.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = TPMustardYellow, contentColor = TPNavyDark)
                                ) {
                                    Text("去录一段", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            } else {
                                Text(
                                    "当前分类和搜索下没有记忆，换个分类或清空搜索试试",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(filteredMemories, key = { it.id }) { item ->
                        // 列表项入场/重排走 animateItem 的弹簧位移 + 淡入，不再硬切
                        Box(Modifier.animateItem()) {
                            MemoryCardItem(item)
                        }
                    }
                }
            }
        } else if (state.memorySubTab == 1) {
            // 3. 子面板 B: 问问你的第二大脑 Agent
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 分工说明：与 Obsidian 页的仓库问答区分开，一句话讲清
                Text(
                    "这里问的是你的录音和记忆；想查整个 Obsidian 仓库的笔记，去 Obsidian 页问。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 快捷提问胶囊
                val suggestions = listOf(
                    "总结我最近关于全双工架构的偏好",
                    "列出近期承诺的待办事项",
                    "回顾上周所有重大决策"
                )

                // 建议胶囊自动换行全部露出：横滑会把第三个切在屏幕外（TP 指出像截断）
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    suggestions.forEach { prompt ->
                        AgentSuggestionChip(text = prompt, onClick = { onAskSecondBrain(prompt) })
                    }
                }

                // 对话历史 LazyColumn
                LazyColumn(
                    state = chatListState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.brainChatHistory.isEmpty() && !state.isBrainThinking) {
                        item {
                            Text(
                                "向第二大脑提问，它会基于你的录音原文和长期记忆回答，并标出真实出处——没记录的就直说没提到。",
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)
                            )
                        }
                    }
                    items(state.brainChatHistory, key = { it.id }) { msg ->
                        ChatBubbleRow(msg, onSourceClick = { sessionId -> onOpenSession(sessionId) })
                    }

                    // 思考态已并入 Agent 气泡本体（占位消息 isStreaming 时显示 Lottie 三点），
                    // 不再有独立的转圈盒子
                }

                // 提问输入框
                Row(
                    // 输入行是固定底栏：内容通铺后要自带底部间距，停在毛玻璃导航栏上方
                    modifier = Modifier.fillMaxWidth().padding(bottom = 100.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = inputQuery,
                        onValueChange = { inputQuery = it },
                        modifier = Modifier.weight(1f).height(52.dp),
                        placeholder = { Text("问录音和记忆里的事，如：限流方案定了吗", fontSize = 11.sp) },
                        shape = RoundedCornerShape(TPRadius.md),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            if (inputQuery.isNotBlank()) {
                                onAskSecondBrain(inputQuery)
                                inputQuery = ""
                            }
                        },
                        modifier = Modifier.size(46.dp),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (inputQuery.isBlank()) MaterialTheme.colorScheme.surfaceVariant else TPNavyDark,
                            contentColor = if (inputQuery.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else TPMustardYellow
                        )
                    ) {
                        Icon(Lucide.ArrowUp, contentDescription = "发送", modifier = Modifier.size(18.dp))
                    }
                }
            }
        } else {
            // 4. 子面板 C: 全文检索（逐字稿原文 + 长期记忆，服务端 /api/search）
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = onSetFullTextQuery,
                        modifier = Modifier.weight(1f).height(46.dp),
                        placeholder = { Text("检索全部录音原文与记忆，如：限流", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Filled.ManageSearch, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )
                    Button(
                        onClick = onRunSearch,
                        modifier = Modifier.height(46.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TPNavyDark, contentColor = TPMustardYellow)
                    ) {
                        Text("检索", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (state.isSearching) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                    }
                } else if (state.searchDone && state.searchHits.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("没有命中，换个关键词试试", fontSize = 12.sp, color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 96.dp)
                    ) {
                        items(state.searchHits, key = { "${it.kind}-${it.sessionId}-${it.text.hashCode()}" }) { hit ->
                            TPCard(
                                modifier = Modifier
                                    .animateItem()
                                    .fillMaxWidth()
                                    .clickable(enabled = hit.kind == "transcript" && hit.sessionId.isNotBlank()) {
                                        onOpenSession(hit.sessionId)
                                    }
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TPBadge(
                                        text = if (hit.kind == "memory") "记忆 · ${hit.category}" else "录音原文",
                                        variant = if (hit.kind == "memory") TPBadgeVariant.PURPLE else TPBadgeVariant.BLUE
                                    )
                                    if (hit.sessionTitle.isNotBlank()) {
                                        Text(
                                            text = hit.sessionTitle,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = hit.text,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                if (hit.kind == "transcript") {
                                    Text(
                                        text = "点按在工作台打开这段录音",
                                        fontSize = 11.sp,
                                        color = brandAccentColor(),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MemoryCardItem(item: MemoryItem) {
    // 文档行（规格 §2.3）：分类徽章 + 时间一行、正文 13/19、可点来源行（无来源数据时不编造），行间 hairline
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TPBadge(text = badgeText, variant = badgeVariant)
                if (item.createdAt.isNotBlank()) {
                    Text(
                        text = item.createdAt.take(16).replace("T", " "),
                        style = TPType.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(item.importance) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = TPMustardYellowDark, modifier = Modifier.size(12.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = item.content,
            style = TPType.bodySmall,
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
                        Text("#$tag", style = TPType.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
}

@Composable
fun ChatBubbleRow(
    msg: BrainChatMessage,
    agentName: String = "第二大脑 Agent",
    onSourceClick: (String) -> Unit = {}
) {
    if (msg.isUser) {
        // 用户消息：右对齐品牌黄气泡，无边框大圆角，入场淡入上浮
        AgentAppear(appearKey = msg.id) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .background(
                            TPMustardYellow,
                            RoundedCornerShape(topStart = 18.dp, topEnd = 6.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
                        )
                        .padding(horizontal = 13.dp, vertical = 9.dp)
                ) {
                    Text(msg.content, fontSize = 13.sp, lineHeight = 19.sp, color = TPNavyDark)
                }
                Text(
                    msg.timestamp,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, end = 4.dp)
                )
            }
        }
        return
    }

    // Agent 消息：Lucide 大脑线条图标 + 名称行；正文为无边框柔面块。
    // 思考中显示 Lottie 三点动画，流式中逐字浮现，完成后由 mikepenz Markdown 渲染。
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(start = 2.dp)
        ) {
            Icon(Lucide.Brain, contentDescription = null, modifier = Modifier.size(14.dp), tint = brandAccentColor())
            Text(agentName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = brandAccentColor())
            Text(msg.timestamp, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(4.dp))
        AgentAppear(appearKey = msg.id) {
            Box(
                modifier = Modifier
                    .widthIn(max = 324.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        RoundedCornerShape(topStart = 6.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Column {
                    when {
                        msg.isStreaming && msg.content.isBlank() ->
                            AgentTypingIndicator(label = "正在检索原文与记忆")
                        msg.isStreaming ->
                            AgentStreamingText(fullText = msg.content, streaming = true, messageKey = msg.id)
                        else ->
                            Markdown(content = msg.content, typography = TPMarkdownTypography())
                    }
                    if (msg.sources.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(9.dp))
                        Text(
                            "来源",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            msg.sources.take(4).forEachIndexed { index, src ->
                                AgentSourceRow(
                                    title = src.sessionTitle,
                                    quote = src.text,
                                    appearKey = "${msg.id}-src-$index",
                                    index = index,
                                    onClick = { if (src.sessionId.isNotBlank()) onSourceClick(src.sessionId) }
                                )
                            }
                        }
                    } else if (msg.citations.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            msg.citations.forEach { cit ->
                                TPBadge(text = "引用: $cit", variant = TPBadgeVariant.BLUE)
                            }
                        }
                    }
                }
            }
        }
    }
}

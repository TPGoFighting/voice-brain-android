package top.tpgofighting.voicebrain.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.tpgofighting.voicebrain.model.ActionItem
import top.tpgofighting.voicebrain.model.Persona
import com.mikepenz.markdown.m3.Markdown
import top.tpgofighting.voicebrain.ui.components.*
import top.tpgofighting.voicebrain.ui.theme.*
import top.tpgofighting.voicebrain.ui.viewmodel.MainUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinutesTab(
    state: MainUiState,
    onSelectPersona: (Persona) -> Unit,
    onGenerateMinutes: () -> Unit,
    onToggleViewMode: (Boolean) -> Unit,
    onPolishDraft: () -> Unit,
    onShowToast: (String) -> Unit,
    onSetTemplate: (String) -> Unit = {},
    onGoRecord: () -> Unit = {}
) {
    var showPersonaBottomSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Koboyo 人设胶囊卡片
        TPCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(TPMustardYellow)
                            .border(1.5.dp, TPNavyDark, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Psychology,
                            contentDescription = null,
                            tint = TPNavyDark,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = state.currentPersona.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            TPBadge(text = state.currentPersona.badge, variant = TPBadgeVariant.YELLOW)
                        }
                        Text(
                            text = state.currentPersona.title,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(
                        onClick = { showPersonaBottomSheet = true },
                        modifier = Modifier.height(36.dp),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        // 主题 primary 是品牌黄，默认描边按钮文字会跟着变黄、在浅底上几乎看不清，显式指定为正文色
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("切换人设", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    val generateInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    Button(
                        onClick = onGenerateMinutes,
                        // 无逐字稿时置灰：点了必然空转（PM 审查 P1），改为由空状态引导去录音
                        enabled = !state.isGeneratingMinutes && (state.minutes != null || state.utterances.isNotEmpty()),
                        interactionSource = generateInteraction,
                        modifier = Modifier.height(36.dp).pressScale(generateInteraction),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TPMustardYellow, contentColor = TPNavyDark),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        if (state.isGeneratingMinutes) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = TPNavyDark)
                        } else {
                            Text(if (state.minutes == null) "生成纪要" else "重新生成", fontSize = 11.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "“${state.currentPersona.quote}”",
                fontSize = 11.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))
            // 纪要场景模板（与服务端 MINUTES_TEMPLATES 对齐：通用/会议/课程/访谈）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "模板:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                top.tpgofighting.voicebrain.model.MINUTES_TEMPLATES.forEach { tpl ->
                    val isSelected = state.minutesTemplate == tpl.id
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
                            .clickable { onSetTemplate(tpl.id) }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tpl.label,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                            color = if (isSelected) TPNavyDark else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            val currentTpl = top.tpgofighting.voicebrain.model.MINUTES_TEMPLATES
                .firstOrNull { it.id == state.minutesTemplate }
            if (currentTpl != null) {
                Text(
                    text = currentTpl.hint,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }

        // 2. 双视图切换器 (结构化纪要 VS 排版润色正式稿)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Button(
                onClick = { onToggleViewMode(false) },
                modifier = Modifier.weight(1f).height(32.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!state.isMinutesPolishView) MaterialTheme.colorScheme.surface else Color.Transparent,
                    contentColor = if (!state.isMinutesPolishView) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("纪要与决策", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { onToggleViewMode(true) },
                modifier = Modifier.weight(1f).height(32.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isMinutesPolishView) MaterialTheme.colorScheme.surface else Color.Transparent,
                    contentColor = if (state.isMinutesPolishView) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("正式成稿", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // 3. 内容展示区 (根据双视图呈现)
        if (state.isMinutesPolishView) {
            // 排版润色视图
            TPCard(
                modifier = Modifier.fillMaxWidth().weight(1f),
                backgroundColor = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("正式汇报草稿 (Markdown 深度解析)", fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = onPolishDraft,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "刷新润色", modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = {
                                if (state.polishedDraft.isNotBlank()) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("正式稿", state.polishedDraft))
                                    onShowToast("正式稿全文已复制到剪贴板！")
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "复制", modifier = Modifier.size(16.dp))
                        }
                    }
                }

                if (state.isPolishing) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(color = TPMustardYellowDark)
                            Text("DeepSeek 正在重构长流水账为商业汇报正式稿...", fontSize = 11.sp)
                        }
                    }
                } else if (state.polishedDraft.isBlank()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Button(onClick = onPolishDraft) {
                            Text("点击生成正式成稿")
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        // 内容通铺到悬浮毛玻璃导航栏之下：底部留 96dp 才能滚出栏面
                        contentPadding = PaddingValues(bottom = 96.dp)
                    ) {
                        item {
                            // 成稿用 mikepenz Markdown 渲染器 + 聊天尺度字阶
                            Markdown(content = state.polishedDraft, typography = TPMarkdownTypography())
                        }
                    }
                }
            }
        } else {
            // 结构化纪要与决策对账视图
            val minutes = state.minutes
            if (minutes == null && state.utterances.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    // 与记忆页统一的三段式空状态：线条图形 + 说明 + 去录一段
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 32.dp)
                    ) {
                        WaveGlyph(
                            modifier = Modifier.width(148.dp).height(40.dp),
                            color = brandAccentColor()
                        )
                        Text("还没有纪要", fontSize = 14.sp, fontWeight = FontWeight.Black)
                        Text(
                            "录音结束后，AI 会把逐字稿提炼成纪要、决策与待办",
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        val goRecordInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        Button(
                            onClick = onGoRecord,
                            interactionSource = goRecordInteraction,
                            modifier = Modifier.pressScale(goRecordInteraction),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = TPMustardYellow, contentColor = TPNavyDark),
                            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp)
                        ) {
                            Text("去录一段", fontSize = 13.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    // 纪要标题与模板标识
                    if (minutes != null) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = minutes.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.weight(1f)
                                )
                                TPBadge(text = "${minutes.templateLabel}模板", variant = TPBadgeVariant.YELLOW)
                                TPBadge(text = minutes.date, variant = TPBadgeVariant.OUTLINE)
                            }
                        }
                    }

                    // 30s TL;DR
                    item {
                        TPCard(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.ElectricBolt, contentDescription = null, tint = TPMustardYellowDark, modifier = Modifier.size(18.dp))
                                Text("30秒高管速览 (TL;DR)", fontSize = 13.sp, fontWeight = FontWeight.Black)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            val items = minutes?.tldr ?: emptyList()
                            if (items.isEmpty()) {
                                Text("尚未生成纪要，点上方「生成纪要」", fontSize = 11.sp, color = Color.Gray)
                            }
                            items.forEach { t ->
                                Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                                    Text("•", fontWeight = FontWeight.Bold, color = TPMustardYellowDark, modifier = Modifier.padding(end = 6.dp))
                                    Text(t, fontSize = 12.sp, lineHeight = 18.sp)
                                }
                            }
                        }
                    }

                    // 核心敲定决策
                    item {
                        TPCard(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.Verified, contentDescription = null, tint = brandAccentColor(), modifier = Modifier.size(18.dp))
                                Text("明确敲定的关键决议 (Decisions)", fontSize = 13.sp, fontWeight = FontWeight.Black)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            val decs = minutes?.decisions ?: emptyList()
                            if (decs.isEmpty() && minutes != null) {
                                Text("本次纪要未提炼出明确决议", fontSize = 11.sp, color = Color.Gray)
                            }
                            decs.forEachIndexed { idx, d ->
                                Row(modifier = Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                                    TPBadge(text = "${idx + 1}", variant = TPBadgeVariant.BLUE)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(d, fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp)
                                }
                            }
                        }
                    }

                    // 落地行动清单
                    item {
                        TPCard(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.Checklist, contentDescription = null, tint = TPAccentGreen, modifier = Modifier.size(18.dp))
                                Text("落地行动项 (Action Items)", fontSize = 13.sp, fontWeight = FontWeight.Black)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            val todos = minutes?.actionItems ?: emptyList()
                            if (todos.isEmpty() && minutes != null) {
                                Text("本次纪要未提炼出行动项", fontSize = 11.sp, color = Color.Gray)
                            }
                            todos.forEach { todo ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                        .padding(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(todo.task, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                        Text("负责: ${todo.assignee} · 优先级: ${todo.priority}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    TPBadge(text = todo.status, variant = TPBadgeVariant.PURPLE)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 4. Koboyo 9 认知角色选择弹窗 (BottomSheet)
    if (showPersonaBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPersonaBottomSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "选择 Koboyo 认知角色人设",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "每个角色拥有独立的认知世界观与提炼侧重点，做出来的 AI 总结完全不同：",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(Persona.ALL) { p ->
                        val isSelected = p.id == state.currentPersona.id
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) TPMustardYellowDark else MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .background(
                                    color = if (isSelected) TPAccentYellowLight else MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onSelectPersona(p)
                                    showPersonaBottomSheet = false
                                }
                                .padding(10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(p.name, fontSize = 13.sp, fontWeight = FontWeight.Black)
                                        TPBadge(text = p.badge, variant = if (isSelected) TPBadgeVariant.DARK else TPBadgeVariant.YELLOW)
                                    }
                                    if (isSelected) {
                                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = TPMustardYellowDark, modifier = Modifier.size(18.dp))
                                    }
                                }
                                Text(p.title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = brandAccentColor())
                                Text(p.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

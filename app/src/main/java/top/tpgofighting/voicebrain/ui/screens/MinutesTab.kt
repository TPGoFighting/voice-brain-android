package top.tpgofighting.voicebrain.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Square
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. 人设行：一行收完（规格 §2.2）——头像 32 + 名称 + 等级 caption + 切换文字钮；
        // 签名引言撤出卡面，只在人设选择弹层里展示
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(TPRadius.sm))
                    .background(TPMustardYellow)
                    .border(1.5.dp, TPNavyDark, RoundedCornerShape(TPRadius.sm)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Psychology,
                    contentDescription = null,
                    tint = TPNavyDark,
                    modifier = Modifier.size(19.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.currentPersona.name,
                    style = TPType.rowTitle,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = state.currentPersona.badge,
                    style = TPType.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { showPersonaBottomSheet = true }) {
                Text("切换人设", style = TPType.label, color = brandAccentColor())
            }
        }

        // 模板横滑行 + 生成按钮同行贴右（规格 §2.2）；已生成纪要时主按钮仍为「重新生成」（决策 E）
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                top.tpgofighting.voicebrain.model.MINUTES_TEMPLATES.forEach { tpl ->
                    val isSelected = state.minutesTemplate == tpl.id
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(TPRadius.sm))
                            .background(if (isSelected) TPNavyDark else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onSetTemplate(tpl.id) }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tpl.label,
                            style = TPType.label,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            val generateInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            Button(
                onClick = onGenerateMinutes,
                enabled = !state.isGeneratingMinutes && (state.minutes != null || state.utterances.isNotEmpty()),
                interactionSource = generateInteraction,
                modifier = Modifier.height(36.dp).pressScale(generateInteraction),
                shape = RoundedCornerShape(TPRadius.sm),
                colors = ButtonDefaults.buttonColors(containerColor = TPMustardYellow, contentColor = TPNavyDark),
                contentPadding = PaddingValues(horizontal = 14.dp)
            ) {
                if (state.isGeneratingMinutes) {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = TPNavyDark)
                } else {
                    Text(if (state.minutes == null) "生成纪要" else "重新生成", style = TPType.label, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        val currentTpl = top.tpgofighting.voicebrain.model.MINUTES_TEMPLATES
            .firstOrNull { it.id == state.minutesTemplate }
        if (currentTpl != null) {
            Text(
                text = currentTpl.hint,
                style = TPType.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 2. 双视图切换器 (结构化纪要 VS 排版润色正式稿)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(TPRadius.md))
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(TPRadius.md))
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
                Text("纪要与决策", style = TPType.label)
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
                Text("正式成稿", style = TPType.label)
            }
        }

        // 3. 内容展示区 (根据双视图呈现)
        if (state.isMinutesPolishView) {
            // 排版润色视图
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("正式成稿", style = TPType.sectionTitle)
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
                        Text("还没有纪要", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
                            Text("去录一段", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
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
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                TPBadge(text = "${minutes.templateLabel}模板", variant = TPBadgeVariant.YELLOW)
                                TPBadge(text = minutes.date, variant = TPBadgeVariant.OUTLINE)
                            }
                        }
                    }

                    // 30 秒速览（文档面分节，不再用卡片堆叠：规格 §2.2）
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.ElectricBolt, contentDescription = null, tint = TPMustardYellowDark, modifier = Modifier.size(17.dp))
                                Text("30 秒速览", style = TPType.sectionTitle)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            val items = minutes?.tldr ?: emptyList()
                            if (items.isEmpty()) {
                                Text("尚未生成纪要，点上方「生成纪要」", fontSize = 11.sp, color = Color.Gray)
                            }
                            items.forEach { t ->
                                Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                                    Text("•", fontWeight = FontWeight.Bold, color = TPMustardYellowDark, modifier = Modifier.padding(end = 6.dp))
                                    Text(t, style = TPType.body)
                                }
                            }
                        }
                    }

                    // 关键决议
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.Verified, contentDescription = null, tint = brandAccentColor(), modifier = Modifier.size(17.dp))
                                Text("关键决议", style = TPType.sectionTitle)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            val decs = minutes?.decisions ?: emptyList()
                            if (decs.isEmpty() && minutes != null) {
                                Text("本次纪要未提炼出明确决议", fontSize = 11.sp, color = Color.Gray)
                            }
                            decs.forEachIndexed { idx, d ->
                                Row(modifier = Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                                    TPBadge(text = "${idx + 1}", variant = TPBadgeVariant.BLUE)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(d, style = TPType.body, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }

                    // 行动项
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.Checklist, contentDescription = null, tint = TPAccentGreen, modifier = Modifier.size(17.dp))
                                Text("行动项", style = TPType.sectionTitle)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            val todos = minutes?.actionItems ?: emptyList()
                            if (todos.isEmpty() && minutes != null) {
                                Text("本次纪要未提炼出行动项", fontSize = 11.sp, color = Color.Gray)
                            }
                            todos.forEach { todo ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 5.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (todo.status.contains("完成") || todo.status.contains("done", ignoreCase = true)) Lucide.Check else Lucide.Square,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp).padding(top = 1.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(todo.task, style = TPType.body, fontWeight = FontWeight.Medium)
                                        Text("负责: ${todo.assignee} · 优先级: ${todo.priority}", style = TPType.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    fontWeight = FontWeight.SemiBold
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
                                        Text(p.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        TPBadge(text = p.badge, variant = if (isSelected) TPBadgeVariant.DARK else TPBadgeVariant.YELLOW)
                                    }
                                    if (isSelected) {
                                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = TPMustardYellowDark, modifier = Modifier.size(18.dp))
                                    }
                                }
                                Text(p.title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = brandAccentColor())
                                Text(p.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (p.quote.isNotBlank()) {
                                    Text(
                                        "\u201C${p.quote}\u201D",
                                        fontSize = 11.sp,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

package top.tpgofighting.voicebrain.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import com.composables.icons.lucide.Link2
import com.composables.icons.lucide.Lucide
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import top.tpgofighting.voicebrain.ui.theme.TPAccentBlue

/**
 * Agent 专属组件族（批次 H）。
 *
 * 组件划分对齐 AI 聊天的通行形态（思考指示 / 流式文本 / 建议胶囊 / 来源行）。
 * 批次 I 起，动效与图标改用现成开源件：思考动画为 LottieFiles 简笔画素材
 * 经 Airbnb Lottie 播放，图标为 Lucide 线性图标，回答正文由 mikepenz
 * Markdown 渲染；本文件只负责把它们按 TP 品牌（藏青/品牌黄/标志蓝、
 * 柔边框、大圆角）组装成 Agent 界面组件。
 */

/** 思考态：LottieFiles 简笔画三点动画 + 状态文案（替代旧的小转圈 + 斜体字）。 */
@Composable
fun AgentTypingIndicator(
    label: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LineLottie(
            asset = "typing_dots.json",
            modifier = Modifier.size(width = 34.dp, height = 28.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 流式文本：服务端 delta 到达快慢不均，这里做一层平滑浮现——
 * 显示长度以自适应步长追赶全文（积压多时加速），中文按字浮现，
 * 流结束时立即补全。语义对齐 Stream 的 StreamingText（续写不重播）。
 */
@Composable
fun AgentStreamingText(
    fullText: String,
    streaming: Boolean,
    messageKey: Any,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    var displayedLen by remember(messageKey) {
        mutableIntStateOf(if (streaming) 0 else fullText.length)
    }
    LaunchedEffect(fullText, streaming, messageKey) {
        if (!streaming) {
            displayedLen = fullText.length
            return@LaunchedEffect
        }
        if (displayedLen > fullText.length) displayedLen = 0
        while (displayedLen < fullText.length) {
            val remaining = fullText.length - displayedLen
            val step = when {
                remaining > 80 -> 5
                remaining > 30 -> 3
                remaining > 10 -> 2
                else -> 1
            }
            displayedLen = (displayedLen + step).coerceAtMost(fullText.length)
            delay(16)
        }
    }
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        Text(
            text = fullText.take(displayedLen),
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = color
        )
        if (streaming) {
            val transition = rememberInfiniteTransition(label = "agentCaret")
            val alpha by transition.animateFloat(
                initialValue = 0.1f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(450), RepeatMode.Reverse),
                label = "agentCaretAlpha"
            )
            Text(
                text = "▍",
                fontSize = 13.sp,
                color = TPMustardYellowDark.copy(alpha = alpha)
            )
        }
    }
}

/** 入场动画：淡入 + 轻微上浮，弹簧收尾。可选延迟做级联。 */
@Composable
fun AgentAppear(
    appearKey: Any,
    delayMs: Int = 0,
    content: @Composable () -> Unit
) {
    var shown by remember(appearKey) { mutableStateOf(false) }
    LaunchedEffect(appearKey) {
        if (delayMs > 0) delay(delayMs.toLong())
        shown = true
    }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "agentAppear"
    )
    Box(
        modifier = Modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 14f
        }
    ) {
        content()
    }
}

/** 建议提问胶囊：TP 柔边框 + 按压缩放，替代旧紫色胶囊。 */
@Composable
fun AgentSuggestionChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .pressScale(interaction)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                RoundedCornerShape(50)
            )
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** 答案来源行：线性链接图标 + 会话标题 + 原文引句，级联入场。 */
@Composable
fun AgentSourceRow(
    title: String,
    quote: String,
    appearKey: Any,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AgentAppear(appearKey = appearKey, delayMs = index * 70) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .clickable { onClick() }
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(
                Lucide.Link2,
                contentDescription = null,
                modifier = Modifier.size(13.dp).padding(top = 1.dp),
                tint = brandAccentColor()
            )
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = title.ifBlank { "历史录音" },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = brandAccentColor()
                )
                Text(
                    text = "「${quote.take(80)}」",
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

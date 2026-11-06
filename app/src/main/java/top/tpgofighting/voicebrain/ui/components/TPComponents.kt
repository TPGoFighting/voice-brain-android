package top.tpgofighting.voicebrain.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.tpgofighting.voicebrain.ui.theme.*
import kotlin.random.Random

enum class TPBadgeVariant {
    BLUE, GREEN, PURPLE, YELLOW, DARK, OUTLINE
}

@Composable
fun TPCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    // 非交互容器只给一层淡描边定界，重描边留给可交互元素（VI 的新丑风描边语义）
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
    borderWidth: Dp = 1.dp,
    elevation: Dp = 2.dp,
    cornerRadius: Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = RoundedCornerShape(cornerRadius),
                clip = false
            )
            .border(
                width = borderWidth,
                color = borderColor,
                shape = RoundedCornerShape(cornerRadius)
            )
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(cornerRadius)
            )
            .padding(12.dp)
    ) {
        Column(content = content)
    }
}

@Composable
fun TPButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String,
    icon: ImageVector? = null,
    backgroundColor: Color = TPMustardYellow,
    contentColor: Color = TPNavyDark,
    borderColor: Color = TPNavyDark,
    enabled: Boolean = true
) {
    Surface(
        modifier = modifier
            .shadow(2.dp, RoundedCornerShape(8.dp))
            .border(1.5.dp, if (enabled) borderColor else Color.Gray, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick),
        color = if (enabled) backgroundColor else Color.LightGray
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                color = contentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
fun TPBadge(
    text: String,
    modifier: Modifier = Modifier,
    variant: TPBadgeVariant = TPBadgeVariant.BLUE
) {
    val (bgColor, textColor, borderColor) = when (variant) {
        TPBadgeVariant.BLUE -> Triple(TPAccentBlueLight, TPAccentBlue, TPAccentBlue)
        TPBadgeVariant.GREEN -> Triple(TPAccentGreenLight, TPAccentGreen, TPAccentGreen)
        TPBadgeVariant.PURPLE -> Triple(TPAccentPurpleLight, TPAccentPurple, TPAccentPurple)
        TPBadgeVariant.YELLOW -> Triple(TPAccentYellowLight, Color(0xFFB45309), TPMustardYellowDark)
        TPBadgeVariant.DARK -> Triple(TPNavyDark, Color.White, TPNavyDark)
        TPBadgeVariant.OUTLINE -> Triple(Color.Transparent, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.outline)
    }

    Box(
        modifier = modifier
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .background(bgColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * 实时声波可视化组件 (Audio Waveform Canvas)
 * v1.1.0：改为真实音量历史驱动（每根柱子对应最近一帧 RMS），
 * 柱高用弹簧动画跟随，不再用正弦伪造律动。
 */
@Composable
fun TPWaveformView(
    volume: Float,
    isRecording: Boolean,
    modifier: Modifier = Modifier,
    barColor: Color = TPMustardYellowDark,
    history: List<Float> = emptyList()
) {
    val barCount = 36
    val displayHistory = remember(history, volume) {
        if (history.isNotEmpty()) history.takeLast(barCount)
        else List(barCount) { volume }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val target = if (isRecording) {
                (displayHistory.getOrNull(i) ?: 0.05f).coerceIn(0.05f, 0.95f)
            } else {
                0.08f
            }
            val animated by animateFloatAsState(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "bar$i"
            )
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .fillMaxHeight(animated)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isRecording) barColor else Color.LightGray)
            )
        }
    }
}

/**
 * 结构化 Markdown 快速渲染组件 (去除复杂依赖，原生高效排版)
 */
@Composable
fun TPMarkdownView(
    markdown: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val lines = markdown.split("\n")
        lines.forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("### ") -> {
                    Text(
                        text = trimmed.removePrefix("### "),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                }
                trimmed.startsWith("## ") -> {
                    Text(
                        text = trimmed.removePrefix("## "),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                    )
                }
                trimmed.startsWith("# ") -> {
                    Text(
                        text = trimmed.removePrefix("# "),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                    )
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TPMustardYellowDark,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(
                            text = trimmed.substring(2).replace("**", ""),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                trimmed.startsWith("> ") -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = trimmed.removePrefix("> "),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                trimmed.isNotBlank() -> {
                    Text(
                        text = trimmed.replace("**", ""),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * 按压弹簧缩放：按下时缩到 [pressedScale]，松开弹回。
 * 用法：给 Button 传同一个 [interactionSource]，再把本修饰符挂在按钮上。
 * 与录音大按钮的按压反馈（0.97 + MediumBouncy 弹簧）同一套参数，全应用统一触感。
 */
@Composable
fun Modifier.pressScale(
    interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource,
    pressedScale: Float = 0.97f
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pressScale"
    )
    return this.then(Modifier.scale(scale))
}

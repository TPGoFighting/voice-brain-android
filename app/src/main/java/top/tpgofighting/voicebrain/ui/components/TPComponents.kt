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
    borderColor: Color = MaterialTheme.colorScheme.outline,
    borderWidth: Dp = 1.5.dp,
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
 */
@Composable
fun TPWaveformView(
    volume: Float,
    isRecording: Boolean,
    modifier: Modifier = Modifier,
    barColor: Color = TPMustardYellowDark
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveAnim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(modifier = modifier.fillMaxWidth().height(36.dp)) {
        val width = size.width
        val height = size.height
        val barCount = 36
        val barWidth = width / (barCount * 1.5f)
        val spacing = barWidth * 0.5f

        for (i in 0 until barCount) {
            val x = i * (barWidth + spacing)
            val baseSin = kotlin.math.sin(phase + i * 0.35f)
            val currentVol = if (isRecording) (volume * 0.7f + 0.3f * baseSin.toFloat().coerceAtLeast(0.1f)) else 0.08f
            val barHeight = (height * currentVol.coerceIn(0.08f, 0.95f)).coerceAtLeast(4f)
            val y = (height - barHeight) / 2f

            drawRoundRect(
                color = if (isRecording) barColor else Color.LightGray,
                topLeft = Offset(x, y),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f)
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

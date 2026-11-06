package top.tpgofighting.voicebrain.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.luminance
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.MarkdownTypography
import top.tpgofighting.voicebrain.R
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
        // 批次 L 色彩纪律：原默认蓝/紫与黄+藏青品牌冲突，BLUE 收为藏青系、PURPLE 收为芥末系
        TPBadgeVariant.BLUE -> Triple(Color(0xFFE8EDF5), TPNavyDark, TPNavyDark)
        TPBadgeVariant.GREEN -> Triple(TPAccentGreenLight, TPAccentGreen, TPAccentGreen)
        TPBadgeVariant.PURPLE -> Triple(TPAccentYellowLight, Color(0xFFB45309), TPMustardYellowDark)
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
                // 简笔画线条感：柱子收细到 2dp，间距拉开（批次 I）
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight(animated)
                    .clip(RoundedCornerShape(1.dp))
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

/**
 * TP 线稿 Logo 磁贴：TP 给定的正式线条 Logo（等距 TP 字标，黑线白底），
 * 装进白色圆角磁贴里使用——浅色/深色主题下都成立，替代此前手写的黄底「TP」字块。
 */
@Composable
fun TPLogoTile(size: Dp = 34.dp, modifier: Modifier = Modifier) {
    val radius = size * 0.26f
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(radius))
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f), RoundedCornerShape(radius)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.tp_logo),
            contentDescription = "TP Logo",
            modifier = Modifier.size(size * 0.86f),
            contentScale = ContentScale.Fit
        )
    }
}

/**
 * 品牌强调色（主题感知）：浅色用藏青、深色用品牌黄。
 * 取代散落各处的 Material 默认蓝（TPAccentBlue），保证深浅模式都成立。
 */
/** 空状态声波字形（批次 R）：静态线条柱阵列，简笔画风、任意帧都是完整图形；
 * 取代 sound_wave Lottie——其 trim 动画在静帧只露一小段，视觉分量近乎标点。 */
@Composable
fun WaveGlyph(
    modifier: Modifier = Modifier,
    color: Color = brandAccentColor()
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val bars = 30
        val barW = 3.dp.toPx()
        val gap = (size.width - bars * barW) / (bars - 1)
        for (i in 0 until bars) {
            val t = i.toFloat() / (bars - 1)
            val env = kotlin.math.sin(t * kotlin.math.PI).toFloat()
            val h = size.height * (0.18f + 0.72f * env * (0.55f + 0.45f * kotlin.math.sin(t * 21f).toFloat().let { if (it < 0) -it else it }))
            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(i * (barW + gap), (size.height - h) / 2f),
                size = androidx.compose.ui.geometry.Size(barW, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 2f, barW / 2f)
            )
        }
    }
}

@Composable
fun brandAccentColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) TPMustardYellow else TPNavyDark

/**
 * 聊天尺度的 Markdown 字阶：mikepenz 默认拿 displayLarge/bodyLarge 渲染，
 * 在气泡里过大；这里统一到正文 14sp、标题 15–17sp 的对话尺度。
 */
@Composable
fun TPMarkdownTypography(): MarkdownTypography {
    val body = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Start)
    return markdownTypography(
        h1 = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold),
        h2 = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
        h3 = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold),
        h4 = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
        h5 = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
        h6 = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
        text = body,
        paragraph = body,
        ordered = body,
        bullet = body,
        list = body,
        quote = TextStyle(fontSize = 13.sp, lineHeight = 20.sp),
        code = TextStyle(fontSize = 12.5.sp, lineHeight = 18.sp, fontFamily = FontFamily.Monospace),
        link = TextStyle(
            fontSize = 14.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Bold,
            color = TPAccentBlue,
            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
        )
    )
}

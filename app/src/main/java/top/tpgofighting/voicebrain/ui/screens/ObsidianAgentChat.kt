package top.tpgofighting.voicebrain.ui.screens

import android.os.SystemClock
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.Brain
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.NotebookText
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Square
import com.mikepenz.markdown.m3.Markdown
import kotlinx.coroutines.delay
import top.tpgofighting.voicebrain.model.BrainChatMessage
import top.tpgofighting.voicebrain.model.ObsidianSourceRef
import top.tpgofighting.voicebrain.ui.components.AgentAppear
import top.tpgofighting.voicebrain.ui.components.TPMarkdownTypography
import top.tpgofighting.voicebrain.ui.theme.TPCardDark
import top.tpgofighting.voicebrain.ui.theme.TPMustardYellow
import top.tpgofighting.voicebrain.ui.theme.TPNavyDark
import top.tpgofighting.voicebrain.ui.viewmodel.MainUiState
import top.tpgofighting.voicebrain.ui.viewmodel.VaultNoteUiState
import java.util.Locale
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

// xchange 助手设计语言移植（结构与动效参照其 React 实现，文案与数据均为录音豆自有）
private val ObAccent = Color(0xFFFE5D02)
private val ObEmerald = Color(0xFF10B981)
private val ObAmber = Color(0xFFF59E0B)
private val ObRed = Color(0xFFEF4444)
private val ObCanvasDark = Color(0xFF0B0F19)

/** 耗时格式：<60s → "12.3s"；≥60s → "1m 5.0s" */
fun fmtElapsed(sec: Float): String {
    val s = sec.coerceAtLeast(0f)
    return if (s < 60f) {
        String.format(Locale.US, "%.1fs", s)
    } else {
        val m = (s / 60).toInt()
        String.format(Locale.US, "%dm %.1fs", m, s - m * 60)
    }
}

/** 思考中实时计时（100ms 一跳） */
@Composable
private fun rememberTickingElapsed(sinceMs: Long): Float {
    var now by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(sinceMs) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(100)
        }
    }
    return if (sinceMs > 0L) (now - sinceMs) / 1000f else 0f
}

// ---------------------------------------------------------------- Orb 头像
/**
 * 圆球小机器人头像（参照 xchange orb-avatar 的比例与配色用 Canvas 复刻；
 * SVG 噪声层无 Compose 等价物，略去）。呼吸缩放 3.2s、眨眼 3.6s（右眼 +60ms）。
 */
@Composable
fun OrbAvatar(size: Dp, modifier: Modifier = Modifier, animate: Boolean = true) {
    val transition = rememberInfiniteTransition(label = "orbAvatar")
    val breathe by transition.animateFloat(
        initialValue = 1f, targetValue = 1.055f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "orbBreathe"
    )
    val blinkT by transition.animateFloat(
        initialValue = 0f, targetValue = 3600f,
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart),
        label = "orbBlink"
    )
    fun eyeScale(delayMs: Float): Float {
        if (!animate) return 1f
        val local = ((blinkT - delayMs) % 3600f + 3600f) % 3600f
        val p = local / 3600f
        return when {
            p < 0.93f -> 1f
            p < 0.965f -> 1f - 0.93f * sin(((p - 0.93f) / 0.035f) * PI).toFloat().coerceIn(0f, 1f)
            else -> 1f
        }
    }
    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                if (animate) {
                    scaleX = breathe
                    scaleY = breathe
                }
            }
    ) {
        val s = this.size.minDimension
        val c = Offset(s / 2f, s / 2f)
        val d = s * 0.88f // 球体直径（留出光晕边距）
        val r = d / 2f
        // 外发光
        drawCircle(ObAccent.copy(alpha = 0.18f), radius = s * 0.5f, center = c)
        // 球体：径向渐变（圆心 50%/45%）
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF7C2A06), Color(0xFFFE5D02), Color(0xFFFFB08A), Color(0xFFFFF1E6)),
                center = Offset(c.x, c.y - d * 0.05f),
                radius = r * 1.05f
            ),
            radius = r, center = c
        )
        // 高光（左上椭圆近似）
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.1f), Color.Transparent),
                center = Offset(c.x - r * 0.4f, c.y - r * 0.52f),
                radius = r * 0.85f
            ),
            radius = r, center = c
        )
        // 暗角（右下）
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.Black.copy(alpha = 0.18f), Color.Transparent),
                center = Offset(c.x + r * 0.24f, c.y + r * 0.36f),
                radius = r * 1.05f
            ),
            radius = r, center = c
        )
        // 眼睛：白色渐变胶囊，两眼一组，整体上移 size*0.0417
        val ew = d * 0.125f
        val eh = d * 0.208333f
        val gap = d * 0.208333f
        val eyeY = c.y - d * 0.041667f
        val eyeBrush = Brush.verticalGradient(listOf(Color.White, Color(0xFFFFE8D6)))
        listOf(-1f to 0f, 1f to 60f).forEach { (side, delayMs) ->
            val h = eh * eyeScale(delayMs)
            val cx = c.x + side * (ew / 2f + gap / 2f)
            drawRoundRect(
                brush = eyeBrush,
                topLeft = Offset(cx - ew / 2f, eyeY - h / 2f),
                size = Size(ew, h),
                cornerRadius = CornerRadius(ew / 2f, ew / 2f)
            )
        }
    }
}

// ---------------------------------------------------------------- 像素阵思考头
/** 3×3 像素阵：对角延迟 [0,100,200 / 100,200,300 / 200,300,400]ms，650ms 脉冲 */
@Composable
fun ObPixelGrid(active: Boolean, color: Color, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    val transition = rememberInfiniteTransition(label = "obPixel")
    val t by transition.animateFloat(
        initialValue = 0f, targetValue = 650f,
        animationSpec = infiniteRepeatable(tween(650, easing = LinearEasing), RepeatMode.Restart),
        label = "obPixelT"
    )
    Canvas(modifier = modifier.size(size)) {
        val delays = intArrayOf(0, 100, 200, 100, 200, 300, 200, 300, 400)
        val total = this.size.minDimension
        val sq = total * 4f / 15f
        val gap = total * 1.5f / 15f
        for (i in 0..8) {
            val row = i / 3
            val col = i % 3
            val local = ((t - delays[i]) % 650f + 650f) % 650f
            val a = if (active) {
                0.15f + 0.85f * sin(PI * (local / 650f)).toFloat().coerceIn(0f, 1f)
            } else 0.35f
            drawRoundRect(
                color = color.copy(alpha = a),
                topLeft = Offset(col * (sq + gap), row * (sq + gap)),
                size = Size(sq, sq),
                cornerRadius = CornerRadius(sq * 0.25f, sq * 0.25f)
            )
        }
    }
}

/** 微光扫过文字（运行态标签） */
@Composable
private fun ShimmerText(text: String, fontSize: androidx.compose.ui.unit.TextUnit, color: Color) {
    val transition = rememberInfiniteTransition(label = "obShimmer")
    val x by transition.animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "obShimmerX"
    )
    val brush = Brush.linearGradient(
        colors = listOf(color.copy(alpha = 0.45f), color, color.copy(alpha = 0.45f)),
        start = Offset(x * 260f, 0f),
        end = Offset(x * 260f + 130f, 0f)
    )
    Text(
        text = text,
        style = TextStyle(brush = brush, fontSize = fontSize, fontWeight = FontWeight.Medium)
    )
}

// ---------------------------------------------------------------- 调度规划 DAG
enum class ObNodeStatus { PENDING, RUNNING, COMPLETED, FAILED, BLOCKED, CONFIRM }

data class ObDagState(
    val subStatus: ObNodeStatus,
    val tasks: List<ObNodeStatus>,
    val done: Int,
    val total: Int = 3
)

@Composable
fun ObDagCard(dag: ObDagState, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(true) }
    val chevronAngle by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(200), label = "dagChevron"
    )
    val borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TPCardDark)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f))
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(14.dp).rotate(chevronAngle - 90f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface)
            )
            Text("调度规划", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, borderColor, RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (dag.done >= dag.total) "${dag.total} 节点 · 完成" else "${dag.total} 节点 · ${dag.done}/${dag.total}",
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (expanded) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(ObCanvasDark)
            ) {
                ObDagCanvas(dag)
            }
        }
    }
}

@Composable
private fun ObDagCanvas(dag: ObDagState) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current.density
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val spinTransition = rememberInfiniteTransition(label = "dagSpin")
    val spin by spinTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "dagSpinAngle"
    )
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(482f / 256f)
    ) {
        val sx = size.width / 482f
        val sy = size.height / 256f
        fun X(v: Float) = v * sx
        fun Y(v: Float) = v * sy
        fun fontSp(virtual: Float) = (virtual * sx / density).sp

        // 径向点阵背景（16 虚拟单位间距）
        var gx = 8f
        while (gx < 482f) {
            var gy = 8f
            while (gy < 256f) {
                drawCircle(onSurface.copy(alpha = 0.10f), radius = X(1.1f), center = Offset(X(gx), Y(gy)))
                gy += 16f
            }
            gx += 16f
        }

        // 三层纵向布局：主调度 → 子 Agent → 任务链（RANK 间距 56）
        val py = 14f
        val aw = 188f; val ah = 40f; val tw = 130f; val th = 36f; val sep = 22f; val rank = 56f
        val mainX = (482f - aw) / 2f; val mainY = py
        val subX = mainX; val subY = mainY + ah + rank
        val tasksY = subY + ah + rank
        val totalTasksW = 3 * tw + 2 * sep
        val tasksX = (482f - totalTasksW) / 2f

        fun edgePath(x1: Float, y1: Float, x2: Float, y2: Float): Path {
            val p = Path()
            p.moveTo(X(x1), Y(y1))
            val dy = (y2 - y1) / 2f
            p.cubicTo(X(x1), Y(y1 + dy), X(x2), Y(y2 - dy), X(x2), Y(y2))
            return p
        }
        fun arrow(x: Float, y: Float, color: Color, horizontal: Boolean = false) {
            val p = Path()
            if (horizontal) {
                p.moveTo(X(x), Y(y))
                p.lineTo(X(x - 6f), Y(y - 3.5f))
                p.lineTo(X(x - 6f), Y(y + 3.5f))
            } else {
                p.moveTo(X(x), Y(y))
                p.lineTo(X(x - 3.5f), Y(y - 6f))
                p.lineTo(X(x + 3.5f), Y(y - 6f))
            }
            p.close()
            drawPath(p, color)
        }
        fun label(
            text: String, cx: Float, cy: Float, virtualSize: Float,
            color: Color, weight: FontWeight, maxW: Float, alignRight: Boolean = false
        ) {
            val style = TextStyle(fontSize = fontSp(virtualSize), fontWeight = weight, color = color)
            val result = measurer.measure(
                text, style,
                constraints = Constraints(maxWidth = (maxW * sx).toInt().coerceAtLeast(1)),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            val tx = if (alignRight) X(cx) - result.size.width else X(cx) - result.size.width / 2f
            drawText(result, topLeft = Offset(tx, Y(cy) - result.size.height / 2f))
        }

        // 主 → 子（竖直贝塞尔，实线 + 箭头）
        drawPath(
            edgePath(mainX + aw / 2f, mainY + ah, subX + aw / 2f, subY),
            color = onSurface,
            style = Stroke(width = X(1.5f))
        )
        arrow(subX + aw / 2f, subY, onSurface)
        // 子 → 任务（虚线无箭头）
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(X(4f), X(3f)), 0f)
        for (i in 0..2) {
            val tx = tasksX + i * (tw + sep) + tw / 2f
            drawPath(
                edgePath(subX + aw / 2f, subY + ah, tx, tasksY),
                color = onSurface.copy(alpha = 0.28f),
                style = Stroke(width = X(1.2f), pathEffect = dashEffect)
            )
        }
        // 任务 → 任务（实线 + 箭头，依赖链）
        for (i in 0..1) {
            val x1 = tasksX + i * (tw + sep) + tw
            val x2 = tasksX + (i + 1) * (tw + sep)
            val yy = tasksY + th / 2f
            drawLine(
                Color(0xFF64748B), Offset(X(x1), Y(yy)), Offset(X(x2), Y(yy)),
                strokeWidth = X(1.3f)
            )
            arrow(x2, yy, Color(0xFF64748B), horizontal = true)
        }

        // 主调度节点（深色实心胶囊）
        drawRoundRect(
            color = TPNavyDark,
            topLeft = Offset(X(mainX), Y(mainY)),
            size = Size(X(aw), Y(ah)),
            cornerRadius = CornerRadius(X(20f), Y(20f))
        )
        drawRoundRect(
            color = onSurface.copy(alpha = 0.30f),
            topLeft = Offset(X(mainX), Y(mainY)),
            size = Size(X(aw), Y(ah)),
            cornerRadius = CornerRadius(X(20f), Y(20f)),
            style = Stroke(width = X(1f))
        )
        label("录音豆 · 主调度", mainX + aw / 2f, mainY + ah / 2f, 12.5f, Color.White, FontWeight.SemiBold, aw - 20f)

        // 子 Agent 节点
        val subStroke = when (dag.subStatus) {
            ObNodeStatus.RUNNING -> onSurface
            ObNodeStatus.FAILED -> ObRed
            ObNodeStatus.CONFIRM -> ObAmber
            ObNodeStatus.COMPLETED -> onSurface.copy(alpha = 0.30f)
            else -> onSurface.copy(alpha = 0.30f)
        }
        val subEffect = if (dag.subStatus == ObNodeStatus.PENDING || dag.subStatus == ObNodeStatus.BLOCKED) {
            PathEffect.dashPathEffect(floatArrayOf(X(4f), X(3f)), 0f)
        } else null
        drawRoundRect(
            color = TPCardDark,
            topLeft = Offset(X(subX), Y(subY)),
            size = Size(X(aw), Y(ah)),
            cornerRadius = CornerRadius(X(20f), Y(20f))
        )
        drawRoundRect(
            color = subStroke,
            topLeft = Offset(X(subX), Y(subY)),
            size = Size(X(aw), Y(ah)),
            cornerRadius = CornerRadius(X(20f), Y(20f)),
            style = Stroke(width = X(1.2f), pathEffect = subEffect)
        )
        label(
            "Obsidian 知识库 Agent", subX + 14f + (aw - 48f) / 2f, subY + ah / 2f, 12.5f,
            onSurface, FontWeight.Medium, aw - 48f
        )
        label("${dag.done}/${dag.total}", subX + aw - 16f, subY + ah / 2f, 11f, muted, FontWeight.Medium, 30f, alignRight = true)

        // 任务节点
        val taskNames = listOf("混合检索", "深读原文", "生成回答")
        for (i in 0..2) {
            val tx = tasksX + i * (tw + sep)
            val st = dag.tasks.getOrElse(i) { ObNodeStatus.PENDING }
            val (fill, stroke, dashed) = when (st) {
                ObNodeStatus.PENDING -> Triple(onSurface.copy(alpha = 0.03f), onSurface.copy(alpha = 0.18f), false)
                ObNodeStatus.RUNNING -> Triple(ObAmber.copy(alpha = 0.10f), ObAmber, false)
                ObNodeStatus.COMPLETED -> Triple(ObEmerald.copy(alpha = 0.10f), ObEmerald, false)
                ObNodeStatus.FAILED -> Triple(ObRed.copy(alpha = 0.10f), ObRed, false)
                ObNodeStatus.BLOCKED -> Triple(Color.Transparent, onSurface.copy(alpha = 0.25f), true)
                ObNodeStatus.CONFIRM -> Triple(ObAmber.copy(alpha = 0.08f), ObAmber, false)
            }
            drawRoundRect(
                color = fill,
                topLeft = Offset(X(tx), Y(tasksY)),
                size = Size(X(tw), Y(th)),
                cornerRadius = CornerRadius(X(6f), Y(6f))
            )
            drawRoundRect(
                color = stroke,
                topLeft = Offset(X(tx), Y(tasksY)),
                size = Size(X(tw), Y(th)),
                cornerRadius = CornerRadius(X(6f), Y(6f)),
                style = Stroke(
                    width = X(1.2f),
                    pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(X(4f), X(3f)), 0f) else null
                )
            )
            // 状态图标（节点内左侧 12 虚拟单位圆区）
            val iconCx = tx + 15f
            val iconCy = tasksY + th / 2f
            when (st) {
                ObNodeStatus.COMPLETED -> {
                    val p = Path()
                    p.moveTo(X(iconCx - 4f), Y(iconCy))
                    p.lineTo(X(iconCx - 1.2f), Y(iconCy + 3.4f))
                    p.lineTo(X(iconCx + 4.4f), Y(iconCy - 3.8f))
                    drawPath(p, ObEmerald, style = Stroke(width = X(1.8f)))
                }
                ObNodeStatus.FAILED -> {
                    drawLine(ObRed, Offset(X(iconCx - 3.2f), Y(iconCy - 3.2f)), Offset(X(iconCx + 3.2f), Y(iconCy + 3.2f)), strokeWidth = X(1.8f))
                    drawLine(ObRed, Offset(X(iconCx + 3.2f), Y(iconCy - 3.2f)), Offset(X(iconCx - 3.2f), Y(iconCy + 3.2f)), strokeWidth = X(1.8f))
                }
                ObNodeStatus.RUNNING -> {
                    drawArc(
                        color = ObAmber,
                        startAngle = spin,
                        sweepAngle = 270f,
                        useCenter = false,
                        topLeft = Offset(X(iconCx - 5f), Y(iconCy - 5f)),
                        size = Size(X(10f), Y(10f)),
                        style = Stroke(
                            width = X(1.6f),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(X(2.6f), X(1.8f)), 0f)
                        )
                    )
                }
                else -> drawCircle(muted.copy(alpha = 0.6f), radius = X(2.2f), center = Offset(X(iconCx), Y(iconCy)))
            }
            label(
                taskNames[i], tx + 26f + (tw - 32f) / 2f, iconCy, 12f,
                if (st == ObNodeStatus.PENDING || st == ObNodeStatus.BLOCKED) muted else onSurface,
                FontWeight.Medium, tw - 32f
            )
        }
    }
}

// ---------------------------------------------------------------- 思考头
/** 运行态思考头：像素阵 + 微光标签 + 实时耗时 */
@Composable
private fun ObReasoningHeadRunning(sinceMs: Long) {
    val elapsed = rememberTickingElapsed(sinceMs)
    val fg = MaterialTheme.colorScheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        ObPixelGrid(active = true, color = fg)
        ShimmerText("正在检索知识库", 13.sp, fg)
        Text(
            text = fmtElapsed(elapsed),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 完成态思考头：「已思考 N 秒」+ 可折叠的左线推理视图（句句属实） */
@Composable
private fun ObReasoningHeadDone(elapsedSec: Float, sentences: List<String>) {
    var expanded by remember { mutableStateOf(false) }
    val fg = MaterialTheme.colorScheme.onSurface
    val chevronAngle by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(200), label = "reasonChevron"
    )
    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 2.dp)
        ) {
            ObPixelGrid(active = false, color = fg)
            Text(
                text = "已思考 ${elapsedSec.roundToInt()} 秒",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = fg
            )
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(14.dp).rotate(chevronAngle - 90f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (expanded) {
            Row(
                modifier = Modifier
                    .padding(start = 5.dp, top = 4.dp)
                    .height(IntrinsicSize.Min)
            ) {
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                )
                Spacer(Modifier.width(17.dp))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    sentences.forEach { s ->
                        Text(
                            text = s,
                            fontSize = 12.5.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 子 Agent 卡
enum class ObStepStatus { ACTIVE, COMPLETE, FAILED, DIM }

data class ObStep(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val desc: String,
    val status: ObStepStatus,
    val details: List<String>? = null
)

@Composable
private fun ObStatusBadge(text: String, color: Color) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.85f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "badgePop"
    )
    Row(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(text, fontSize = 10.5.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

@Composable
private fun ObThinkingSteps(steps: List<ObStep>) {
    Column(Modifier.fillMaxWidth()) {
        steps.forEachIndexed { idx, step ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(20.dp).fillMaxHeight()
                ) {
                    Box(
                        modifier = Modifier.size(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when (step.status) {
                            ObStepStatus.ACTIVE -> Box(
                                Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onSurface)
                            )
                            ObStepStatus.COMPLETE, ObStepStatus.FAILED, ObStepStatus.DIM ->
                                Icon(
                                    step.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = when (step.status) {
                                        ObStepStatus.FAILED -> ObRed
                                        ObStepStatus.DIM -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                        }
                    }
                    if (idx < steps.lastIndex) {
                        Box(
                            Modifier
                                .width(1.dp)
                                .weight(1f)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 6.dp, bottom = if (idx < steps.lastIndex) 10.dp else 0.dp)
                ) {
                    if (step.status == ObStepStatus.ACTIVE) {
                        ShimmerText(step.label + "…", 12.5.sp, MaterialTheme.colorScheme.onSurface)
                    } else {
                        Text(
                            text = step.label,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (step.status == ObStepStatus.DIM) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (step.desc.isNotBlank()) {
                        Text(
                            text = step.desc,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (step.details != null) {
                        var detailOpen by remember { mutableStateOf(false) }
                        Text(
                            text = if (detailOpen) "收起执行明细" else "查看执行明细",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = ObAccent,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .clickable { detailOpen = !detailOpen }
                        )
                        if (detailOpen) {
                            Column(
                                modifier = Modifier.padding(top = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                step.details.forEach { path ->
                                    Text(
                                        text = path,
                                        fontSize = 10.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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

/**
 * 子 Agent 卡（PanelCard 移植）：标题行 + 状态徽标、ThinkingSteps、
 * 摘要盒、运行页脚（转圈 + 不定进度条 + 耗时）。
 */
@Composable
fun ObSubAgentCard(
    query: String,
    badgeText: String,
    badgeColor: Color,
    steps: List<ObStep>,
    running: Boolean,
    tickingSince: Long = 0L,
    summary: String? = null,
    modifier: Modifier = Modifier
) {
    val borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TPCardDark)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Lucide.Brain,
                contentDescription = null,
                modifier = Modifier.size(16.dp).padding(top = 1.dp),
                tint = ObAccent
            )
            Column(Modifier.weight(1f)) {
                Text("子 Agent · Obsidian 知识库", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                if (query.isNotBlank()) {
                    Text(
                        text = query,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            ObStatusBadge(badgeText, badgeColor)
        }

        Text(
            text = if (running) "正在调用工具执行…" else "已完成 ${steps.count { it.status == ObStepStatus.COMPLETE }} 个操作",
            fontSize = 12.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ObThinkingSteps(steps)

        if (summary != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "摘要",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(text = summary, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }

        if (running) {
            val elapsed = rememberTickingElapsed(tickingSince)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = ObAccent
                )
                LinearProgressIndicator(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50)),
                    color = ObAccent,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                )
                Text(
                    text = fmtElapsed(elapsed),
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 来源 / 写回 / 元信息
@Composable
private fun ObSourceRow(index: Int, ref: ObsidianSourceRef, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(ObAccent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = index.toString(),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = ObAccent
            )
        }
        Icon(
            Lucide.NotebookText,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = ref.title.ifBlank { ref.path },
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = ref.path,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---------------------------------------------------------------- 单轮对话
/** 用户消息：右对齐柔和气泡（最大 85% 宽） */
@Composable
private fun ObUserBubble(msg: BrainChatMessage) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .widthIn(max = maxWidth * 0.85f),
            horizontalAlignment = Alignment.End
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = msg.content,
                    fontSize = 13.5.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight(450)
                )
            }
            Text(
                text = msg.timestamp,
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(end = 4.dp, top = 3.dp)
            )
        }
    }
}

/** 助手回合：思考头 → 调度图 → 子 Agent 卡 → 正文 → 来源 → 写回 → 元信息 */
@Composable
private fun ObAssistantTurn(
    msg: BrainChatMessage,
    query: String,
    onOpenVaultNote: (String) -> Unit
) {
    val n = msg.sourceRefs.size
    val elapsed = msg.elapsedSec ?: 0f
    val stopped = !msg.isError && msg.backendLabel.isEmpty() && msg.content == "已停止生成"
    val sentences = when {
        msg.isError -> listOf(
            "在知识库中混合检索「$query」",
            "请求失败，未能生成回答"
        )
        stopped -> listOf("在知识库中混合检索「$query」，已手动停止")
        else -> listOf(
            "在知识库中混合检索「$query」，命中 $n 篇相关笔记",
            if (n > 0) "引用 $n 篇来源作为回答依据，见下方引用列表" else "未命中可引用的来源笔记",
            "由 ${msg.backendLabel.ifBlank { "Obsidian Agent" }} 生成回答，用时 ${fmtElapsed(elapsed)}"
        )
    }
    val dag = when {
        msg.isError -> ObDagState(
            subStatus = ObNodeStatus.FAILED,
            tasks = listOf(ObNodeStatus.FAILED, ObNodeStatus.BLOCKED, ObNodeStatus.BLOCKED),
            done = 0
        )
        stopped -> ObDagState(
            subStatus = ObNodeStatus.CONFIRM,
            tasks = listOf(ObNodeStatus.PENDING, ObNodeStatus.PENDING, ObNodeStatus.PENDING),
            done = 0
        )
        else -> ObDagState(
            subStatus = ObNodeStatus.COMPLETED,
            tasks = listOf(ObNodeStatus.COMPLETED, ObNodeStatus.COMPLETED, ObNodeStatus.COMPLETED),
            done = 3
        )
    }
    val steps = when {
        msg.isError -> listOf(
            ObStep(Lucide.Search, "混合检索", "请求失败：连接知识库异常", ObStepStatus.FAILED)
        )
        stopped -> listOf(
            ObStep(Lucide.Search, "混合检索", "用户手动停止", ObStepStatus.DIM)
        )
        else -> listOf(
            ObStep(
                Lucide.Search, "混合检索", "命中 $n 篇相关笔记", ObStepStatus.COMPLETE,
                details = if (n > 0) msg.sourceRefs.map { it.path } else null
            ),
            ObStep(
                Lucide.NotebookText, "深读原文",
                if (n > 0) "载入 $n 篇来源内容" else "无来源可载入",
                ObStepStatus.COMPLETE
            ),
            ObStep(
                Lucide.Brain, "生成回答", "用时 ${fmtElapsed(elapsed)}", ObStepStatus.COMPLETE
            )
        )
    }
    val badge = when {
        msg.isError -> "失败" to ObRed
        stopped -> "已停止" to ObAmber
        else -> "已完成" to ObEmerald
    }
    val summary = when {
        msg.isError || stopped -> null
        else -> buildString {
            append("命中 $n 篇来源 · 用时 ${fmtElapsed(elapsed)}")
            if (msg.writtenBackPath.isNotEmpty()) append(" · 已写回笔记")
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ObReasoningHeadDone(elapsedSec = elapsed, sentences = sentences)
        ObDagCard(dag)
        ObSubAgentCard(
            query = query,
            badgeText = badge.first,
            badgeColor = badge.second,
            steps = steps,
            running = false,
            summary = summary
        )
        Markdown(content = msg.content, typography = TPMarkdownTypography(), modifier = Modifier.fillMaxWidth())
        if (n > 0) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "引用来源 · $n",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                msg.sourceRefs.forEachIndexed { i, ref ->
                    AgentAppear(appearKey = "${msg.id}-src-$i", delayMs = i * 40) {
                        ObSourceRow(index = i + 1, ref = ref) { onOpenVaultNote(ref.path) }
                    }
                }
            }
        }
        if (msg.writtenBackPath.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ObEmerald.copy(alpha = 0.08f))
                    .border(1.dp, ObEmerald.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Lucide.Check, contentDescription = null, modifier = Modifier.size(13.dp), tint = ObEmerald)
                Text(
                    text = "已追加至：${msg.writtenBackPath}",
                    fontSize = 11.5.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "查看",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ObEmerald,
                    modifier = Modifier.clickable { onOpenVaultNote(msg.writtenBackPath) }
                )
            }
        }
        val meta = listOf(
            msg.backendLabel,
            msg.elapsedSec?.let { "${it.roundToInt()} 秒" } ?: "",
            msg.timestamp
        ).filter { it.isNotBlank() }.joinToString(" · ")
        if (meta.isNotBlank()) {
            Text(
                text = meta,
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

/** 运行中回合：实时思考头 + 进行中的调度图与子 Agent 卡 */
@Composable
private fun ObLiveTurn(state: MainUiState, query: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ObReasoningHeadRunning(sinceMs = state.obsidianThinkingSince)
        ObDagCard(
            ObDagState(
                subStatus = ObNodeStatus.RUNNING,
                tasks = listOf(ObNodeStatus.RUNNING, ObNodeStatus.PENDING, ObNodeStatus.PENDING),
                done = 0
            )
        )
        ObSubAgentCard(
            query = query,
            badgeText = "执行中",
            badgeColor = ObAccent,
            steps = listOf(
                ObStep(Lucide.Search, "混合检索", "正在多路召回并排序", ObStepStatus.ACTIVE)
            ),
            running = true,
            tickingSince = state.obsidianThinkingSince
        )
    }
}

// ---------------------------------------------------------------- 空态与输入岛
@Composable
private fun ObEmptyState(state: MainUiState, onAsk: (String) -> Unit) {
    val total = state.obsidianStatus.totalNotes
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OrbAvatar(size = 56.dp)
        Text("问问你的 Obsidian 知识库", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(
            text = if (state.obsidianStatus.loaded && total > 0) {
                "已连接 ${"%,d".format(total)} 篇笔记与沉淀，直接提问即可检索原文。"
            } else {
                "连接知识库后即可提问。"
            },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.widthIn(max = 300.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "试试这样问",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
        listOf("仓库里最近在更新什么主题", "LLM-Wiki 的核心概念是什么", "我的项目笔记里有哪些待办").forEach { q ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(TPCardDark)
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                    .clickable { onAsk(q) }
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Lucide.Search,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(text = q, fontSize = 12.5.sp)
            }
        }
    }
}

@Composable
private fun ObComposer(
    state: MainUiState,
    onAsk: (String) -> Unit,
    onStop: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    val thinking = state.isObsidianThinking
    val targetNote = state.obsidianNotes.firstOrNull()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 100.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(TPCardDark)
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.5.sp,
                lineHeight = 20.sp
            ),
            cursorBrush = SolidColor(TPMustardYellow),
            minLines = 1,
            maxLines = 4,
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth()) {
                    if (text.isEmpty()) {
                        Text("问问你的知识库…", fontSize = 13.5.sp, color = muted)
                    }
                    inner()
                }
            }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.autoWriteBackEnabled && targetNote != null) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f), RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(Lucide.NotebookText, contentDescription = null, modifier = Modifier.size(11.dp), tint = muted)
                    Text(
                        text = "写回 " + targetNote.title.ifBlank { targetNote.path },
                        fontSize = 10.5.sp,
                        color = muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 180.dp)
                    )
                }
            } else {
                Text("Obsidian Agent", fontSize = 10.5.sp, color = muted)
            }
            Spacer(Modifier.weight(1f))
            val enabled = thinking || text.isNotBlank()
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (enabled) TPNavyDark else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f), CircleShape)
                    .clickable(enabled = enabled) {
                        if (thinking) {
                            onStop()
                        } else {
                            onAsk(text)
                            text = ""
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (thinking) Lucide.Square else Lucide.ArrowUp,
                    contentDescription = if (thinking) "停止" else "发送",
                    modifier = Modifier.size(16.dp),
                    tint = if (enabled) TPMustardYellow else muted
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 笔记原文弹窗
@Composable
private fun VaultNoteDialog(note: VaultNoteUiState, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(TPCardDark)
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OrbAvatar(size = 22.dp, animate = false)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = note.title.ifBlank { "笔记" },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = note.path,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = onClose) { Text("关闭") }
            }
            when {
                note.loading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = ObAccent)
                    Text("正在读取笔记原文…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                note.error.isNotEmpty() -> Text(
                    text = "读取失败：${note.error}",
                    fontSize = 12.5.sp,
                    color = ObRed
                )
                else -> Box(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Markdown(content = note.body, typography = TPMarkdownTypography(), modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 根组件
/**
 * Obsidian Agent 对话区（ObsidianWikiTab 分区 0）。
 * 种子欢迎语（id=oc-0）是旧气泡界面的说明文案，新界面以空态呈现，不渲染为回合。
 */
@Composable
fun ObsidianAgentChat(
    state: MainUiState,
    onAskObsidian: (String) -> Unit,
    onStopObsidian: () -> Unit,
    onOpenVaultNote: (String) -> Unit,
    onCloseVaultNote: () -> Unit,
    modifier: Modifier = Modifier
) {
    val turns = remember(state.obsidianChatHistory) {
        state.obsidianChatHistory.filter { it.id != "oc-0" }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(turns.size, state.isObsidianThinking) {
        val count = listState.layoutInfo.totalItemsCount
        if (count > 0) listState.animateScrollToItem(count - 1)
    }
    Column(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            if (turns.isEmpty() && !state.isObsidianThinking) {
                item { ObEmptyState(state, onAskObsidian) }
            }
            itemsIndexed(turns, key = { _, m -> m.id }) { idx, msg ->
                if (msg.isUser) {
                    ObUserBubble(msg)
                } else {
                    val q = turns.subList(0, idx).lastOrNull { it.isUser }?.content ?: ""
                    AgentAppear(appearKey = msg.id) {
                        ObAssistantTurn(msg = msg, query = q, onOpenVaultNote = onOpenVaultNote)
                    }
                }
            }
            if (state.isObsidianThinking) {
                item {
                    val q = turns.lastOrNull { it.isUser }?.content ?: ""
                    ObLiveTurn(state = state, query = q)
                }
            }
        }
        ObComposer(state = state, onAsk = onAskObsidian, onStop = onStopObsidian)
    }
    state.vaultNote?.let { note ->
        VaultNoteDialog(note = note, onClose = onCloseVaultNote)
    }
}

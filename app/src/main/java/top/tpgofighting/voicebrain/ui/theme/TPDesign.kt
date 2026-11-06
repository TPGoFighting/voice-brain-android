package top.tpgofighting.voicebrain.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * v1.3.0 设计系统 token 层（《大厂精修》规格 §1）。
 * 全 App 只许引用这里的间距/圆角/字阶，禁止页面里再裸写魔法数。
 * 字重纪律：Black 只许出现在录音主按钮；标题 SemiBold；正文 Regular；强调 Medium。
 */
object TPSpacing {
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp   // 页面左右边距、卡片内边距
    val s5 = 20.dp
    val s6 = 24.dp   // 组间间距（恒大于组内）
    val s8 = 32.dp
    val s10 = 40.dp
    val s12 = 48.dp
}

object TPRadius {
    val xs = 6.dp    // 徽章、小标签
    val sm = 10.dp   // chip、小按钮、抽屉选中行
    val md = 14.dp   // 输入框、普通卡片
    val lg = 20.dp   // 主卡片、Agent 气泡
    val sheet = 28.dp // 底部弹层、抽屉右缘
    val pill = 999.dp
}

object TPType {
    val timer = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.SemiBold)
    val timerInline = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
    val pageTitle = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
    val sectionTitle = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
    val rowTitle = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
    val bodyMain = TextStyle(fontSize = 15.sp, lineHeight = 24.sp, fontWeight = FontWeight.Regular)
    val body = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Regular)
    val bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.Regular)
    val label = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
    val caption = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Regular)
}

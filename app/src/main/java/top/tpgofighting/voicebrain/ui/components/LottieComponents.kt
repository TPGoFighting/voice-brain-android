package top.tpgofighting.voicebrain.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.LottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import com.airbnb.lottie.LottieProperty

/**
 * 简笔画线条动画（批次 I）：素材来自 LottieFiles 免费库（Lottie Simple License），
 * 注意：COLOR_FILTER 的值必须是 android.graphics.ColorFilter（PorterDuffColorFilter），
 * 传 Compose 的 ColorFilter.tint 会在 FillContent.draw 处 ClassCastException 闪退
 * （v1.2.3/v1.2.4 冒烟实测，批次 L 引入、批次 N 修复）。
 * 存放在 assets/lottie/。单色素材（白线条）通过 COLOR_FILTER 动态着色适配深浅主题。
 */
@Composable
fun LineLottie(
    asset: String,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    speed: Float = 1f
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset("lottie/$asset")
    )
    val dynamicProperties = if (tint != null) {
        rememberLottieDynamicProperties(
            rememberLottieDynamicProperty(
                property = LottieProperty.COLOR_FILTER,
                value = PorterDuffColorFilter(tint.toArgb(), PorterDuff.Mode.SRC_IN),
                keyPath = arrayOf("**")
            )
        )
    } else {
        null
    }
    LottieAnimation(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        modifier = modifier,
        dynamicProperties = dynamicProperties,
        speed = speed
    )
}

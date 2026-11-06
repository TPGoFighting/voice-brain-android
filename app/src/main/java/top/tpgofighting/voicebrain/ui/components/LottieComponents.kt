package top.tpgofighting.voicebrain.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.LottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import com.airbnb.lottie.compose.LottieProperty

/**
 * 简笔画线条动画（批次 I）：素材来自 LottieFiles 免费库（Lottie Simple License），
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
                value = ColorFilter.tint(tint),
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

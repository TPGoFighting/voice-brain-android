package top.tpgofighting.voicebrain.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = TPMustardYellow,
    onPrimary = TPNavyDark,
    primaryContainer = TPMustardYellowDark,
    onPrimaryContainer = TPNavyDark,
    secondary = TPNavyDark,
    onSecondary = Color.White,
    background = TPBackgroundLight,
    onBackground = TPTextPrimaryLight,
    surface = TPCardLight,
    onSurface = TPTextPrimaryLight,
    surfaceVariant = TPNeutralFill,
    onSurfaceVariant = TPTextMutedLight,
    outline = TPBorderLight
)

private val DarkColorScheme = darkColorScheme(
    primary = TPMustardYellow,
    onPrimary = TPNavyDark,
    primaryContainer = TPNavySurface,
    onPrimaryContainer = TPMustardYellow,
    secondary = TPMustardYellow,
    onSecondary = TPNavyDark,
    background = TPBackgroundDark,
    onBackground = TPTextPrimaryDark,
    surface = TPCardDark,
    onSurface = TPTextPrimaryDark,
    surfaceVariant = TPNavySurface,
    onSurfaceVariant = TPTextMutedDark,
    outline = TPBorderDark
)

@Composable
fun TPVoiceBrainTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

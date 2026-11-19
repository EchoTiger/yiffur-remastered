package com.ugf.yiffur1.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.ugf.yiffur1.domain.model.ThemeMode

private val YiffurMidnightColorScheme = darkColorScheme(
    primary = YiffurSkyBlue,
    onPrimary = Color(0xFF00213A),
    primaryContainer = Color(0xFF0F3559),
    onPrimaryContainer = YiffurSkyBlueLight,
    secondary = YiffurGold,
    onSecondary = Color(0xFF2C1E00),
    secondaryContainer = Color(0xFF422F00),
    onSecondaryContainer = YiffurAmber,
    tertiary = FavoritePink,
    onTertiary = Color.White,
    background = MidnightBg,
    onBackground = TextPrimary,
    surface = MidnightSurface,
    onSurface = TextPrimary,
    surfaceVariant = MidnightSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = Color(0xFF0A0D14),
    surfaceContainerLow = Color(0xFF121722),
    surfaceContainer = MidnightSurface,
    surfaceContainerHigh = MidnightSurfaceVariant,
    surfaceContainerHighest = MidnightSurfaceHigh,
    outline = MidnightOutline,
    outlineVariant = Color(0xFF253046),
    error = VoteDownRed,
    onError = Color.White
)

private val YiffurOledColorScheme = YiffurMidnightColorScheme.copy(
    background = OledBg,
    surface = OledSurface,
    surfaceVariant = OledSurfaceVariant,
    surfaceContainerLowest = OledBg,
    surfaceContainerLow = Color(0xFF06080D),
    surfaceContainer = OledSurface,
    surfaceContainerHigh = OledSurfaceVariant,
    surfaceContainerHighest = OledSurfaceHigh
)

@Composable
fun YiffurTheme(
    themeMode: ThemeMode = ThemeMode.YIFFUR_MIDNIGHT,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when (themeMode) {
        ThemeMode.YIFFUR_MIDNIGHT -> YiffurMidnightColorScheme
        ThemeMode.OLED_BLACK -> YiffurOledColorScheme
        ThemeMode.DYNAMIC_DARK -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicDarkColorScheme(context)
            } else {
                YiffurMidnightColorScheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = YiffurTypography,
        content = content
    )
}

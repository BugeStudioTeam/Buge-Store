package com.buge.store.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.buge.store.R
import com.buge.store.data.ColorMode
import com.buge.store.data.ContrastMode
import com.buge.store.data.ThemeMode
import com.buge.store.data.UserPreferences

private val GoogleSansFlex = FontFamily(
    Font(R.font.google_sans_flex_regular, FontWeight.Normal),
    Font(R.font.google_sans_flex_medium, FontWeight.Medium),
    Font(R.font.google_sans_flex_bold, FontWeight.Bold),
)

private val BugeBlue = Color(0xFF1565D8)
private val Teal = Color(0xFF006C67)
private val Violet = Color(0xFF7252BE)
private val Coral = Color(0xFFA8422B)
private val Evergreen = Color(0xFF2D6A48)

@Composable
fun BugeStoreTheme(preferences: UserPreferences, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (preferences.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val scheme = when {
        preferences.colorMode == ColorMode.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> staticColorScheme(seedFor(preferences.colorMode), useDark)
    }.withContrast(preferences.contrastMode)

    val systemBarColor = if (useDark) Color(0xFF101417) else Color(0xFFF8F9FC)
    SideEffect {
        val window = (context as? Activity)?.window ?: return@SideEffect
        window.statusBarColor = systemBarColor.toArgb()
        window.navigationBarColor = systemBarColor.toArgb()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !useDark
            isAppearanceLightNavigationBars = !useDark
        }
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = bugeTypography,
        shapes = BugeShapes,
        content = content,
    )
}

private fun seedFor(mode: ColorMode): Color = when (mode) {
    ColorMode.DYNAMIC, ColorMode.BUGE_BLUE -> BugeBlue
    ColorMode.TEAL -> Teal
    ColorMode.VIOLET -> Violet
    ColorMode.CORAL -> Coral
    ColorMode.EVERGREEN -> Evergreen
}

private fun staticColorScheme(seed: Color, dark: Boolean): ColorScheme {
    return if (dark) {
        darkColorScheme(
            primary = seed.lighten(0.36f), onPrimary = Color(0xFF002E69), primaryContainer = seed.darken(0.25f), onPrimaryContainer = seed.lighten(0.62f),
            secondary = seed.lighten(0.26f), onSecondary = Color(0xFF10201F), secondaryContainer = seed.darken(0.37f), onSecondaryContainer = seed.lighten(0.70f),
            tertiary = Color(0xFFFFB68F), onTertiary = Color(0xFF4E1500), tertiaryContainer = Color(0xFF772600), onTertiaryContainer = Color(0xFFFFDBCA),
            background = Color(0xFF101417), onBackground = Color(0xFFE0E3E7), surface = Color(0xFF101417), onSurface = Color(0xFFE0E3E7),
            surfaceVariant = Color(0xFF40474F), onSurfaceVariant = Color(0xFFC0C7D0), outline = Color(0xFF8A929B),
        )
    } else {
        lightColorScheme(
            primary = seed, onPrimary = Color.White, primaryContainer = seed.lighten(0.73f), onPrimaryContainer = seed.darken(0.58f),
            secondary = seed.darken(0.16f), onSecondary = Color.White, secondaryContainer = seed.lighten(0.78f), onSecondaryContainer = seed.darken(0.54f),
            tertiary = Color(0xFF934A00), onTertiary = Color.White, tertiaryContainer = Color(0xFFFFDCC5), onTertiaryContainer = Color(0xFF301400),
            background = Color(0xFFF8F9FC), onBackground = Color(0xFF191C20), surface = Color(0xFFF8F9FC), onSurface = Color(0xFF191C20),
            surfaceVariant = Color(0xFFDEE2E8), onSurfaceVariant = Color(0xFF42474E), outline = Color(0xFF727780),
        )
    }
}

private fun ColorScheme.withContrast(mode: ContrastMode): ColorScheme = when (mode) {
    ContrastMode.STANDARD -> this
    ContrastMode.MEDIUM -> copy(onSurface = onSurface.copy(alpha = 1f), outline = onSurfaceVariant)
    ContrastMode.HIGH -> copy(onSurface = if (background.luminance() > 0.5f) Color.Black else Color.White, onBackground = if (background.luminance() > 0.5f) Color.Black else Color.White, outline = if (background.luminance() > 0.5f) Color.Black else Color.White)
}

private fun Color.lighten(amount: Float): Color = Color(
    red + (1f - red) * amount,
    green + (1f - green) * amount,
    blue + (1f - blue) * amount,
    alpha,
)

private fun Color.darken(amount: Float): Color = Color(red * (1f - amount), green * (1f - amount), blue * (1f - amount), alpha)

private val bugeTypography = Typography(
    displaySmall = TextStyle(fontFamily = GoogleSansFlex, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 42.sp),
    headlineLarge = TextStyle(fontFamily = GoogleSansFlex, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = GoogleSansFlex, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = GoogleSansFlex, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = GoogleSansFlex, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = GoogleSansFlex, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = GoogleSansFlex, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontFamily = GoogleSansFlex, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = GoogleSansFlex, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

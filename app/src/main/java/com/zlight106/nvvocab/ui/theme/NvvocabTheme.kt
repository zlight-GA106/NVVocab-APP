package com.zlight106.nvvocab.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import com.zlight106.nvvocab.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF0061A4),
    secondary = Color(0xFF535F70),
    tertiary = Color(0xFF6B5778),
    surface = Color(0xFFF8F9FF),
    surfaceVariant = Color(0xFFE0E2EC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9ECAFF),
    secondary = Color(0xFFBBC7DB),
    tertiary = Color(0xFFD6BEE5),
    surface = Color(0xFF111318),
    surfaceVariant = Color(0xFF43474E),
)

@Composable
fun NvvocabTheme(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    themePresetId: String?,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val preset = themePresets.firstOrNull { it.id == themePresetId }
    val colors = when {
        preset != null -> preset.toColorScheme(dark)
        dynamicColor && dark -> dynamicDarkColorScheme(context)
        dynamicColor -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    SideEffect {
        context.findActivity()?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !dark
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = !dark
        }
    }
    val systemDensity = LocalDensity.current
    val windowWidthPx = LocalWindowInfo.current.containerSize.width
    val windowHeightPx = LocalWindowInfo.current.containerSize.height
    val windowWidthDp = if (windowWidthPx > 0) windowWidthPx / systemDensity.density else 360f
    val windowHeightDp = if (windowHeightPx > 0) windowHeightPx / systemDensity.density else 800f
    // Some vendors expose fewer than 360 logical dp after applying their display-size
    // setting. Fixed Material dimensions then consume most of a row and force Chinese
    // text to wrap one character at a time. Give compact phones a stable logical width
    // while keeping tablets and normally configured phones at the system scale.
    val scaleProfile = responsiveUiScale(windowWidthDp, windowHeightDp)
    val responsiveDensity = remember(
        windowWidthPx,
        windowHeightPx,
        systemDensity.density,
        systemDensity.fontScale,
    ) {
        Density(
            density = systemDensity.density * scaleProfile.densityMultiplier,
            // Very large vendor font/display combinations otherwise apply the scale
            // twice. 1.15 still honours an enlarged accessibility font without clipping.
            fontScale = (systemDensity.fontScale * scaleProfile.fontMultiplier).coerceIn(
                0.85f,
                if (scaleProfile.isLargeLandscape) 1.25f else 1.15f,
            ),
        )
    }
    CompositionLocalProvider(LocalDensity provides responsiveDensity) {
        MaterialTheme(colorScheme = animateColorScheme(colors), content = content)
    }
}

internal data class ResponsiveUiScale(
    val densityMultiplier: Float,
    val fontMultiplier: Float,
    val isLargeLandscape: Boolean,
)

internal fun responsiveUiScale(widthDp: Float, heightDp: Float): ResponsiveUiScale {
    val aspectRatio = if (heightDp > 0f) widthDp / heightDp else 0f
    val largeLandscape = widthDp >= 960f &&
        heightDp >= 540f &&
        aspectRatio in 1.45f..2f
    return when {
        largeLandscape -> ResponsiveUiScale(
            densityMultiplier = 1.06f,
            fontMultiplier = 1.10f,
            isLargeLandscape = true,
        )
        widthDp < 360f -> ResponsiveUiScale(
            densityMultiplier = (widthDp / 360f).coerceIn(0.82f, 1f),
            fontMultiplier = 1f,
            isLargeLandscape = false,
        )
        else -> ResponsiveUiScale(1f, 1f, false)
    }
}

@Composable
private fun animateColorScheme(target: androidx.compose.material3.ColorScheme): androidx.compose.material3.ColorScheme {
    val animation = tween<Color>(durationMillis = 480, easing = FastOutSlowInEasing)
    val primary by animateColorAsState(target.primary, animation, label = "theme-primary")
    val onPrimary by animateColorAsState(target.onPrimary, animation, label = "theme-on-primary")
    val primaryContainer by animateColorAsState(target.primaryContainer, animation, label = "theme-primary-container")
    val onPrimaryContainer by animateColorAsState(target.onPrimaryContainer, animation, label = "theme-on-primary-container")
    val secondary by animateColorAsState(target.secondary, animation, label = "theme-secondary")
    val secondaryContainer by animateColorAsState(target.secondaryContainer, animation, label = "theme-secondary-container")
    val tertiary by animateColorAsState(target.tertiary, animation, label = "theme-tertiary")
    val tertiaryContainer by animateColorAsState(target.tertiaryContainer, animation, label = "theme-tertiary-container")
    val background by animateColorAsState(target.background, animation, label = "theme-background")
    val onBackground by animateColorAsState(target.onBackground, animation, label = "theme-on-background")
    val surface by animateColorAsState(target.surface, animation, label = "theme-surface")
    val onSurface by animateColorAsState(target.onSurface, animation, label = "theme-on-surface")
    val surfaceVariant by animateColorAsState(target.surfaceVariant, animation, label = "theme-surface-variant")
    val onSurfaceVariant by animateColorAsState(target.onSurfaceVariant, animation, label = "theme-on-surface-variant")
    val outline by animateColorAsState(target.outline, animation, label = "theme-outline")
    val outlineVariant by animateColorAsState(target.outlineVariant, animation, label = "theme-outline-variant")
    return target.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        secondaryContainer = secondaryContainer,
        tertiary = tertiary,
        tertiaryContainer = tertiaryContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        outline = outline,
        outlineVariant = outlineVariant,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

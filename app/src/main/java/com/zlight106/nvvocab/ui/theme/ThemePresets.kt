package com.zlight106.nvvocab.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

data class ThemePreset(
    val id: String,
    val name: String,
    val description: String,
    val primary: Color,
    val primaryContainer: Color,
    val secondary: Color,
    val tertiary: Color,
    val surface: Color,
    val onSurface: Color,
)

val themePresets = listOf(
    ThemePreset("shuangchao", "霜潮", "冰蓝、霜白、水汽、清冷", hex(0x68A4CA), hex(0xC2ECF7), hex(0x9CA3CA), hex(0xEBBDC2), hex(0xF7FCFD), hex(0x363A67)),
    ThemePreset("tongxuan", "铜玄", "玄蓝、霜白、铜纹、冷焰", hex(0x455065), hex(0xA6B7DC), hex(0xB78A71), hex(0xB4E7F5), hex(0xFDFDFD), hex(0x1B1C29)),
    ThemePreset("heimao", "黑猫", "蓝黑、银白、旧金、金瞳", hex(0x50546C), hex(0xA6ACBF), hex(0x9E8F6B), hex(0x4E753A), hex(0xF5F5F6), hex(0x1B1C29)),
    ThemePreset("honghu", "红狐", "橙红、卡其、雪白、暖金", hex(0xB54A2F), hex(0xE78255), hex(0xB49A75), hex(0xF0C55A), hex(0xF5F1EE), hex(0x35251E)),
    ThemePreset("xingkong", "星空", "星海蓝、深海紫、荧光青、冰白", hex(0x3867BF), hex(0x7EF5F3), hex(0xE2C3B6), hex(0x43429A), hex(0xF5FAFA), hex(0x2F3A56)),
    ThemePreset("bohe", "薄荷", "深靛蓝、薄荷青、浅蓝、雪白", hex(0x2C297F), hex(0x88F5D0), hex(0x81C4E9), hex(0xCCE5BD), hex(0xF4F3F9), hex(0x1C1956)),
)

fun ThemePreset.toColorScheme(dark: Boolean): ColorScheme = if (dark) {
    val darkSurface = mix(Color(0xFF111318), primary, 0.09f)
    val darkVariant = mix(Color(0xFF202328), primary, 0.16f)
    val brightPrimary = mix(primary, Color.White, 0.25f)
    val brightSecondary = mix(secondary, Color.White, 0.24f)
    val brightTertiary = mix(tertiary, Color.White, 0.22f)
    darkColorScheme(
        primary = brightPrimary,
        onPrimary = idealOnColor(brightPrimary),
        primaryContainer = mix(primary, Color.Black, 0.34f),
        onPrimaryContainer = mix(primary, Color.White, 0.76f),
        secondary = brightSecondary,
        onSecondary = idealOnColor(brightSecondary),
        secondaryContainer = mix(secondary, Color.Black, 0.52f),
        onSecondaryContainer = mix(secondary, Color.White, 0.76f),
        tertiary = brightTertiary,
        onTertiary = idealOnColor(brightTertiary),
        tertiaryContainer = mix(tertiary, Color.Black, 0.50f),
        onTertiaryContainer = mix(tertiary, Color.White, 0.78f),
        background = mix(Color(0xFF0F1115), primary, 0.055f),
        onBackground = Color(0xFFE3E2E8),
        surface = darkSurface,
        onSurface = Color(0xFFE5E1E8),
        surfaceVariant = darkVariant,
        onSurfaceVariant = Color(0xFFC9C5CC),
        outline = mix(primary, Color.White, 0.58f),
        outlineVariant = mix(darkVariant, Color.White, 0.18f),
    )
} else {
    lightColorScheme(
        primary = primary,
        onPrimary = idealOnColor(primary),
        primaryContainer = primaryContainer,
        onPrimaryContainer = idealOnColor(primaryContainer),
        secondary = secondary,
        onSecondary = idealOnColor(secondary),
        secondaryContainer = mix(secondary, Color.White, 0.62f),
        onSecondaryContainer = idealOnColor(mix(secondary, Color.White, 0.62f)),
        tertiary = tertiary,
        onTertiary = idealOnColor(tertiary),
        tertiaryContainer = mix(tertiary, Color.White, 0.62f),
        onTertiaryContainer = idealOnColor(mix(tertiary, Color.White, 0.62f)),
        background = mix(surface, primary, 0.025f),
        onBackground = onSurface,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = mix(primaryContainer, surface, 0.52f),
        onSurfaceVariant = mix(onSurface, Color.White, 0.30f),
        outline = mix(primary, onSurface, 0.45f),
        outlineVariant = mix(primaryContainer, onSurface, 0.18f),
    )
}

private fun hex(value: Long): Color = Color(0xFF000000 or value)

private fun idealOnColor(background: Color): Color =
    if (background.luminance() > 0.46f) Color(0xFF17171A) else Color.White

private fun mix(first: Color, second: Color, amount: Float): Color {
    val ratio = amount.coerceIn(0f, 1f)
    return Color(
        red = first.red * (1f - ratio) + second.red * ratio,
        green = first.green * (1f - ratio) + second.green * ratio,
        blue = first.blue * (1f - ratio) + second.blue * ratio,
        alpha = 1f,
    )
}

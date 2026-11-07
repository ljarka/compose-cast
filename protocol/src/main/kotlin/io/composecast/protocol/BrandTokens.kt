package io.composecast.protocol

import kotlinx.serialization.Serializable

@Serializable
data class BrandTokens(
    val light: ColorScheme,
    val dark: ColorScheme,
    val shapes: Shapes = Shapes(),
    val typography: Typography = Typography(),
)

@Serializable
data class ColorScheme(
    val primary: String,
    val onPrimary: String,
    val primaryContainer: String,
    val onPrimaryContainer: String,
    val secondary: String,
    val onSecondary: String,
    val background: String,
    val onBackground: String,
    val surface: String,
    val onSurface: String,
    val error: String,
    val onError: String,
)

@Serializable
data class Shapes(
    val cornerSmall: Float = 4f,
    val cornerMedium: Float = 12f,
    val cornerLarge: Float = 16f,
)

@Serializable
data class Typography(
    val fontFamily: String = "default",
    val scaleFactor: Float = 1f,
)

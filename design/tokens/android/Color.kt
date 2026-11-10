package com.tonalli.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Colores de marca (gráficos y logo)
val BrandTinta = Color(0xFF14213D)
val BrandBrasa = Color(0xFFE8501F)
val BrandBrasaTexto = Color(0xFFC73E14)
val BrandSol = Color(0xFFF5A524)
val BrandSolTexto = Color(0xFF8A5300)
val BrandFrio = Color(0xFF1AA6B7)
val BrandFrioTexto = Color(0xFF0B6E7D)
val BrandPapel = Color(0xFFFFF8EF)
val BrandArena = Color(0xFFF3E8D8)

// Tema claro
val BackgroundLight = Color(0xFFFFF8EF)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceVariantLight = Color(0xFFF3E8D8)
val OnBackgroundLight = Color(0xFF14213D)
val OnSurfaceLight = Color(0xFF14213D)
val OnSurfaceMutedLight = Color(0xFF4A5877)
val PrimaryLight = Color(0xFFC73E14)
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFFFE3D8)
val OnPrimaryContainerLight = Color(0xFF7A2308)
val SecondaryLight = Color(0xFF14213D)
val OnSecondaryLight = Color(0xFFFFF8EF)
val TertiaryLight = Color(0xFF0B6E7D)
val OnTertiaryLight = Color(0xFFFFFFFF)
val ErrorLight = Color(0xFFB3261E)
val OnErrorLight = Color(0xFFFFFFFF)
val OutlineLight = Color(0xFF7A6A52)
val OutlineSubtleLight = Color(0xFFE6D8C3)
val FocusRingLight = Color(0xFF0B6E7D)
val HotBgLight = Color(0xFFFFE3D8)
val HotFgLight = Color(0xFF9A2F0C)
val WarmBgLight = Color(0xFFFFEFC7)
val WarmFgLight = Color(0xFF7A4A00)
val ColdBgLight = Color(0xFFD9F0F3)
val ColdFgLight = Color(0xFF0A5562)

// Tema oscuro
val BackgroundDark = Color(0xFF0B1220)
val SurfaceDark = Color(0xFF14213D)
val SurfaceVariantDark = Color(0xFF1D2D52)
val OnBackgroundDark = Color(0xFFFFF8EF)
val OnSurfaceDark = Color(0xFFFFF8EF)
val OnSurfaceMutedDark = Color(0xFFB8C2D9)
val PrimaryDark = Color(0xFFFF7A4D)
val OnPrimaryDark = Color(0xFF14213D)
val PrimaryContainerDark = Color(0xFF4A1E10)
val OnPrimaryContainerDark = Color(0xFFFFB59B)
val SecondaryDark = Color(0xFFFFF8EF)
val OnSecondaryDark = Color(0xFF14213D)
val TertiaryDark = Color(0xFF4CC9D9)
val OnTertiaryDark = Color(0xFF0B1220)
val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF4A0B07)
val OutlineDark = Color(0xFF7F90B5)
val OutlineSubtleDark = Color(0xFF2C3E66)
val FocusRingDark = Color(0xFF4CC9D9)
val HotBgDark = Color(0xFF4A1E10)
val HotFgDark = Color(0xFFFFB59B)
val WarmBgDark = Color(0xFF3F2D08)
val WarmFgDark = Color(0xFFFFD27A)
val ColdBgDark = Color(0xFF0E3A44)
val ColdFgDark = Color(0xFF8FE0EE)

val TonalliLightColors = lightColorScheme(
    primary = PrimaryLight, onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight, onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight, onSecondary = OnSecondaryLight,
    tertiary = TertiaryLight, onTertiary = OnTertiaryLight,
    background = BackgroundLight, onBackground = OnBackgroundLight,
    surface = SurfaceLight, onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight, onSurfaceVariant = OnSurfaceMutedLight,
    error = ErrorLight, onError = OnErrorLight,
    outline = OutlineLight, outlineVariant = OutlineSubtleLight
)

val TonalliDarkColors = darkColorScheme(
    primary = PrimaryDark, onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark, onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark, onSecondary = OnSecondaryDark,
    tertiary = TertiaryDark, onTertiary = OnTertiaryDark,
    background = BackgroundDark, onBackground = OnBackgroundDark,
    surface = SurfaceDark, onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark, onSurfaceVariant = OnSurfaceMutedDark,
    error = ErrorDark, onError = OnErrorDark,
    outline = OutlineDark, outlineVariant = OutlineSubtleDark
)

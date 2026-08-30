package com.dgpack.chamcong.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = DgAccent,
    secondary = DgAccent,
    background = DgSurfaceDark,
    surface = DgSurfaceDark,
    error = DgError
)

private val LightColors = lightColorScheme(
    primary = DgPrimary,
    secondary = DgAccent,
    error = DgError
)

@Composable
fun ChamCongTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = ChamCongTypography,
        content = content
    )
}

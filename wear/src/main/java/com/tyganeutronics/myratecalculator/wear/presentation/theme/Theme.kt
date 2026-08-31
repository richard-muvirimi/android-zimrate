package com.tyganeutronics.myratecalculator.wear.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

private val Primary = Color(0xFF0F8AFD)
private val PrimaryVariant = Color(0xFF0270D7)
private val Background = Color(0xFF15181D)
private val Surface = Color(0xFF1D2026)

private val WearColorPalette = Colors(
    primary = Primary,
    primaryVariant = PrimaryVariant,
    background = Background,
    surface = Surface,
    onPrimary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
)

@Composable
fun ZimRateWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = WearColorPalette,
        content = content,
    )
}

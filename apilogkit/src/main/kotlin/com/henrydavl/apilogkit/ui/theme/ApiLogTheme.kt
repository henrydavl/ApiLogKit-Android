package com.henrydavl.apilogkit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Semantic colors shared by the inspector UI — status badges and JSON value
 * types — mirroring the iOS palette (string=green, number=blue, bool=purple,
 * null=gray). Light/dark variants keep contrast in both modes.
 */
object ApiLogColors {

    @Composable
    fun statusColor(code: String): Color {
        val value = code.toIntOrNull() ?: -1
        val dark = isSystemInDarkTheme()
        return when (value) {
            in 200..299 -> if (dark) Color(0xFF4CAF50) else Color(0xFF2E7D32)
            in 300..399 -> if (dark) Color(0xFFFFB74D) else Color(0xFFE65100)
            in 400..Int.MAX_VALUE -> if (dark) Color(0xFFE57373) else Color(0xFFC62828)
            else -> if (dark) Color(0xFF9E9E9E) else Color(0xFF616161)
        }
    }

    val string: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF7EC699) else Color(0xFF2E7D32)
    val number: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF6FB1FC) else Color(0xFF1565C0)
    val bool: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFFC792EA) else Color(0xFF6A1B9A)
    val nullValue: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF9E9E9E) else Color(0xFF757575)
    val punctuation: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFFB0B0B0) else Color(0xFF757575)
}

private val LightColors = lightColorScheme()
private val DarkColors = darkColorScheme()

@Composable
fun ApiLogTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}

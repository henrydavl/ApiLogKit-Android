package com.henrydavl.apilogkit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The inspector is intentionally **light-only** — colors never depend on the
 * host's system dark-mode setting. This keeps contrast predictable and lets the
 * status-bar icons stay dark on the light surface (see [ApiLogTheme] usage in
 * `ApiLogActivity`, which forces a light status bar).
 *
 * Semantic colors mirror the iOS palette (string=green, number=blue, bool=purple,
 * null=gray).
 */
object ApiLogColors {

    fun statusColor(code: String): Color = when (code.toIntOrNull() ?: -1) {
        in 200..299 -> Color(0xFF2E7D32)
        in 300..399 -> Color(0xFFE65100)
        in 400..Int.MAX_VALUE -> Color(0xFFC62828)
        else -> Color(0xFF616161)
    }

    val string = Color(0xFF2E7D32)
    val number = Color(0xFF1565C0)
    val bool = Color(0xFF6A1B9A)
    val nullValue = Color(0xFF757575)
    val punctuation = Color(0xFF757575)
}

private val LightColors = lightColorScheme()

@Composable
fun ApiLogTheme(content: @Composable () -> Unit) {
    // Always light, regardless of the system setting.
    MaterialTheme(colorScheme = LightColors, content = content)
}

package fr.rabuxx.iptvtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

// Palette inspirée de tvOS : fond quasi noir, matières grises translucides, focus blanc.
val Background = Color(0xFF000000)
val BackgroundTop = Color(0xFF1C1C22)
val Card = Color(0xFF2C2C2E)
val Chip = Color(0x33FFFFFF)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF8E8E93)
val FocusFill = Color(0xFFFFFFFF)
val FocusText = Color(0xFF000000)
val Glass = Color(0xB31C1C1E)
val ErrorRed = Color(0xFFFF6961)

// Conservés pour compatibilité avec le code existant
val Muted = TextSecondary
val Panel = Card
val Accent = Color(0xFF0A84FF)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = FocusFill,
            onPrimary = FocusText,
            background = Background,
            surface = Card,
            onSurface = TextPrimary,
        ),
    ) {
        CompositionLocalProvider(LocalContentColor provides TextPrimary) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(BackgroundTop, Background))),
            ) {
                content()
            }
        }
    }
}

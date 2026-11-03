package fr.rabuxx.iptvtv.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.tv.material3.ClickableSurfaceColors
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceGlow
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.ClickableSurfaceShape
import androidx.tv.material3.Surface as TvSurface

/**
 * Surface TV qui répond aussi au doigt : celle de tv-material ne réagit qu'aux touches de la télécommande.
 * Tous les écrans passent par elle, le comportement à la télécommande ne change pas.
 */
@Composable
fun Surface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: ClickableSurfaceShape = ClickableSurfaceDefaults.shape(),
    colors: ClickableSurfaceColors = ClickableSurfaceDefaults.colors(),
    scale: ClickableSurfaceScale = ClickableSurfaceDefaults.scale(),
    glow: ClickableSurfaceGlow = ClickableSurfaceDefaults.glow(),
    content: @Composable BoxScope.() -> Unit,
) {
    val tap by rememberUpdatedState(onClick)
    val longTap by rememberUpdatedState(onLongClick)
    TvSurface(
        onClick = onClick,
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(onTap = { tap() }, onLongPress = { longTap?.invoke() })
        },
        onLongClick = onLongClick,
        shape = shape,
        colors = colors,
        scale = scale,
        glow = glow,
        content = content,
    )
}

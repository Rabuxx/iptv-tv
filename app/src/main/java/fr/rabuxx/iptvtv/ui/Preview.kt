package fr.rabuxx.iptvtv.ui

import android.view.TextureView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import fr.rabuxx.iptvtv.data.Channel
import kotlinx.coroutines.delay

/**
 * Aperçu vidéo muet d'une chaîne, lancé quand sa carte garde le focus un instant (comme sur l'Apple TV).
 * Un seul lecteur pour toute la grille ; il est libéré dès qu'on quitte l'écran.
 */
@Stable
class ChannelPreview internal constructor(internal val player: ExoPlayer?) {
    /** Carte qui a le focus. */
    internal var target by mutableStateOf<Channel?>(null)

    /** Chaîne dont l'aperçu est chargé. */
    internal var activeId by mutableStateOf<String?>(null)

    internal var firstFrame by mutableStateOf(false)

    fun onFocus(channel: Channel, focused: Boolean) {
        if (focused) {
            target = channel
        } else if (target?.streamId == channel.streamId) {
            target = null
        }
    }

    /** Vrai quand la vidéo de cette chaîne est prête à être affichée dans sa carte. */
    fun isShowing(channel: Channel) = player != null && activeId == channel.streamId && firstFrame
}

private const val PREVIEW_DELAY_MS = 1_200L

@Composable
fun rememberChannelPreview(enabled: Boolean, urlFor: (Channel, String) -> String): ChannelPreview {
    if (!enabled) return remember { ChannelPreview(null) }

    val player = rememberIptvPlayer()
    val preview = remember(player) { ChannelPreview(player) }

    DisposableEffect(player) {
        player.volume = 0f
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                preview.firstFrame = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(preview.target) {
        val channel = preview.target
        preview.activeId = null
        preview.firstFrame = false
        player.stop()
        if (channel == null) return@LaunchedEffect
        // On attend que le focus reste sur la carte : pas de flux ouvert en faisant défiler la grille
        delay(PREVIEW_DELAY_MS)
        player.setMediaItem(MediaItem.fromUri(urlFor(channel, "ts")))
        player.prepare()
        player.play()
        preview.activeId = channel.streamId
    }

    return preview
}

/** Vidéo de l'aperçu, à placer dans la carte (TextureView : suit l'arrondi et l'agrandissement au focus). */
@Composable
fun PreviewVideo(preview: ChannelPreview) {
    val player = preview.player ?: return
    AndroidView(
        factory = { ctx -> TextureView(ctx).also { player.setVideoTextureView(it) } },
        onRelease = { player.clearVideoTextureView(it) },
        modifier = Modifier.fillMaxSize(),
    )
}

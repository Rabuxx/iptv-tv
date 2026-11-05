package fr.rabuxx.iptvtv.cast

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import java.io.File

/**
 * Envoi vers une Chromecast. Le fichier est lu directement par la Chromecast depuis le serveur :
 * elle utilise la connexion de l'abonnement, et la tablette sert de télécommande.
 * À utiliser depuis le thread principal.
 */
class CastController private constructor(context: Context, private val castContext: CastContext) {

    private val router = MediaRouter.getInstance(context)
    private val selector = MediaRouteSelector.Builder()
        .addControlCategory(
            CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID),
        )
        .build()

    private var pending: CastMedia? = null
    private val fileServer = LocalFileServer()

    var pickerOpen by mutableStateOf(false)
        private set
    var devices by mutableStateOf<List<MediaRouter.RouteInfo>>(emptyList())
        private set
    var connectedTo by mutableStateOf<String?>(null)
        private set
    var title by mutableStateOf<String?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** Volume de la Chromecast, de 0 à 1. */
    var volume by mutableStateOf(0.0)
        private set

    private val routerCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = refreshDevices()
        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = refreshDevices()
        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = refreshDevices()
    }

    private val mediaCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            playing = client?.isPlaying == true
        }
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) = Unit
        override fun onSessionStarted(session: CastSession, sessionId: String) = attach(session)
        override fun onSessionStartFailed(session: CastSession, reason: Int) {
            pending = null
            error = "Connexion à la Chromecast impossible"
        }
        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionEnded(session: CastSession, reason: Int) = detach(session)
        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = attach(session)
        override fun onSessionResumeFailed(session: CastSession, reason: Int) = detach(session)
        override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
    }

    private val session: CastSession? get() = castContext.sessionManager.currentCastSession
    private val client: RemoteMediaClient? get() = session?.remoteMediaClient

    init {
        castContext.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
        session?.let(::attach)
    }

    /** Lit sur la Chromecast connectée, ou propose d'en choisir une. */
    fun cast(media: CastMedia) {
        error = null
        if (session?.isConnected == true) {
            load(media)
        } else {
            pending = media
            pickerOpen = true
            router.addCallback(selector, routerCallback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN)
            refreshDevices()
        }
    }

    /** Fichier téléchargé : la tablette le diffuse elle-même sur le Wi-Fi. */
    fun castFile(media: CastMedia, file: File) {
        val url = fileServer.serve(file, castContentType(media.extension))
        if (url == null) {
            error = "Connecte la tablette au même Wi-Fi que la Chromecast"
            return
        }
        cast(media.copy(url = url))
    }

    fun connect(route: MediaRouter.RouteInfo) {
        closePicker(keepPending = true)
        router.selectRoute(route)
    }

    fun closePicker(keepPending: Boolean = false) {
        pickerOpen = false
        router.removeCallback(routerCallback)
        if (!keepPending) pending = null
    }

    /** Touches de volume ou boutons de l'écran : monte ou baisse le son de la Chromecast. */
    fun changeVolume(delta: Double) {
        val current = session?.takeIf { it.isConnected } ?: return
        val target = (current.volume + delta).coerceIn(0.0, 1.0)
        runCatching {
            current.setVolume(target)
            volume = target
        }
    }

    fun togglePause() {
        client?.togglePlayback()
    }

    fun stop() {
        title = null
        castContext.sessionManager.endCurrentSession(true)
    }

    fun clearError() {
        error = null
    }

    private fun refreshDevices() {
        devices = router.routes.filter { !it.isDefault && it.isEnabled && it.matchesSelector(selector) }
    }

    private fun attach(session: CastSession) {
        connectedTo = session.castDevice?.friendlyName
        runCatching { volume = session.volume }
        session.remoteMediaClient?.unregisterCallback(mediaCallback)
        session.remoteMediaClient?.registerCallback(mediaCallback)
        pending?.let {
            pending = null
            load(it)
        }
    }

    private fun detach(session: CastSession) {
        fileServer.stop()
        session.remoteMediaClient?.unregisterCallback(mediaCallback)
        connectedTo = null
        title = null
        playing = false
    }

    private fun load(media: CastMedia) {
        val remote = client ?: return
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, media.title)
            media.subtitle?.let { putString(MediaMetadata.KEY_SUBTITLE, it) }
            media.image?.let { addImage(WebImage(Uri.parse(it))) }
        }
        val info = MediaInfo.Builder(media.url)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(castContentType(media.extension))
            .setMetadata(metadata)
            .build()
        val request = MediaLoadRequestData.Builder()
            .setMediaInfo(info)
            .setAutoplay(true)
            .setCurrentTime(media.positionMs.coerceAtLeast(0))
            .build()
        title = media.title
        remote.load(request).setResultCallback { result ->
            if (!result.status.isSuccess) {
                title = null
                error = "La Chromecast n'a pas pu lire cette vidéo (format ou serveur)"
            }
        }
    }

    companion object {
        /** Null si les services Google Cast ne sont pas disponibles. */
        fun create(context: Context): CastController? = runCatching {
            CastController(context.applicationContext, CastContext.getSharedInstance(context.applicationContext))
        }.getOrNull()
    }
}

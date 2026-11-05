package fr.rabuxx.iptvtv.data

enum class DownloadState { Queued, Running, Done, Failed }

/** Pourquoi un téléchargement en cours n'avance plus. */
enum class DownloadPause { None, ServerRetry, NoNetwork, WaitingWifi, Other }

/** Codes COLUMN_REASON de DownloadManager pour un téléchargement en pause. */
fun pauseFromReason(reason: Int): DownloadPause = when (reason) {
    1 -> DownloadPause.ServerRetry
    2 -> DownloadPause.NoNetwork
    3 -> DownloadPause.WaitingWifi
    else -> DownloadPause.Other
}

/** Film ou épisode téléchargé pour la lecture hors ligne. L'identifiant est la clé de reprise (« m:12 », « e:34 »). */
data class DownloadItem(
    val id: String,
    val title: String,
    val subtitle: String?,
    val image: String?,
    val url: String,
    val extension: String,
    val systemId: Long = -1,
    val state: DownloadState = DownloadState.Queued,
    val bytes: Long = 0,
    val totalBytes: Long = 0,
    val pause: DownloadPause = DownloadPause.None,
    /** Nombre de fois où le transfert s'est arrêté puis a dû reprendre. */
    val interruptions: Int = 0,
    /** Code HTTP ou erreur système du dernier échec. */
    val failureCode: Int = 0,
    /** Vitesse lissée, en octets par seconde. */
    val speed: Long = 0,
) {
    val progress: Float get() = if (totalBytes > 0) (bytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    val fileName: String get() = "${id.replace(Regex("[^A-Za-z0-9]"), "_")}.${extension.replace(Regex("[^A-Za-z0-9]"), "")}"
}

/**
 * Prochain téléchargement à lancer. Un seul à la fois : un abonnement à une connexion
 * refuserait le deuxième.
 */
fun nextDownload(items: List<DownloadItem>): DownloadItem? =
    if (items.any { it.state == DownloadState.Running }) null
    else items.firstOrNull { it.state == DownloadState.Queued }

/** Libellé du bouton de téléchargement d'un contenu. */
fun downloadLabel(item: DownloadItem?): String {
    if (item == null) return "⬇  Télécharger"
    return when (item.state) {
        DownloadState.Queued -> "En attente"
        DownloadState.Running -> "⬇  ${(item.progress * 100).toInt()} %"
        DownloadState.Done -> "✓  Hors ligne"
        DownloadState.Failed -> "Échec, réessayer"
    }
}

/** 45 s, 12 min, 1 h 05. */
fun formatRemaining(seconds: Long): String = when {
    seconds < 60 -> "$seconds s"
    seconds < 3600 -> "${seconds / 60} min"
    else -> "${seconds / 3600} h ${String.format(java.util.Locale.FRENCH, "%02d", seconds % 3600 / 60)}"
}

/** Vitesse lissée : une mesure isolée ne fait pas sauter l'affichage. */
fun smoothSpeed(previous: Long, measured: Long): Long =
    if (previous <= 0) measured else (previous * 0.7 + measured * 0.3).toLong()

/** Ligne d'état affichée sous le titre. */
fun downloadDetail(item: DownloadItem): String {
    val cuts = if (item.interruptions > 0) " · ${item.interruptions} coupure(s)" else ""
    return when (item.state) {
        DownloadState.Queued -> "En attente"
        DownloadState.Done -> "Disponible hors ligne · ${formatBytes(item.totalBytes)}$cuts"
        DownloadState.Failed -> when (item.failureCode) {
            in 400..599 -> "Échec : le serveur a répondu HTTP ${item.failureCode}$cuts"
            else -> "Échec du téléchargement (code ${item.failureCode})$cuts"
        }
        DownloadState.Running -> {
            val progress = if (item.totalBytes > 0) {
                "${formatBytes(item.bytes)} sur ${formatBytes(item.totalBytes)}"
            } else {
                "Démarrage"
            }
            when (item.pause) {
                DownloadPause.ServerRetry -> "$progress · En pause : connexion coupée, nouvel essai bientôt$cuts"
                DownloadPause.NoNetwork -> "$progress · En pause : pas de réseau$cuts"
                DownloadPause.WaitingWifi -> "$progress · En pause : attend le Wi-Fi$cuts"
                DownloadPause.Other -> "$progress · En pause$cuts"
                DownloadPause.None -> {
                    val left = item.totalBytes - item.bytes
                    val rate = if (item.speed > 0) " · ${formatBytes(item.speed)}/s" else ""
                    val eta = if (item.speed > 0 && left > 0) " · reste ${formatRemaining(left / item.speed)}" else ""
                    "$progress$rate$eta$cuts"
                }
            }
        }
    }
}

/** 734 Mo, 1,4 Go. */
fun formatBytes(bytes: Long): String {
    val mb = bytes / 1_000_000.0
    return if (mb >= 1000) String.format(java.util.Locale.FRENCH, "%.1f Go", mb / 1000)
    else "${mb.toInt()} Mo"
}

package fr.rabuxx.iptvtv.data

enum class DownloadState { Queued, Running, Done, Failed }

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

/** 734 Mo, 1,4 Go. */
fun formatBytes(bytes: Long): String {
    val mb = bytes / 1_000_000.0
    return if (mb >= 1000) String.format(java.util.Locale.FRENCH, "%.1f Go", mb / 1000)
    else "${mb.toInt()} Mo"
}

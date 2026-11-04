package fr.rabuxx.iptvtv.cast

/** Ce qu'on envoie à la Chromecast. */
data class CastMedia(
    val title: String,
    val subtitle: String?,
    val image: String?,
    val url: String,
    val extension: String,
    val positionMs: Long,
)

/** Type du fichier annoncé à la Chromecast. */
fun castContentType(extension: String): String = when (extension.lowercase()) {
    "mkv" -> "video/x-matroska"
    "webm" -> "video/webm"
    "ts" -> "video/mp2t"
    "m3u8" -> "application/x-mpegURL"
    else -> "video/mp4"
}

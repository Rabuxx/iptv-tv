package fr.rabuxx.iptvtv.ui

// Fonctions de formatage pures (sans Compose ni Android) : testées en tests unitaires JVM.

/** « 01:45:00 », « 105 min » ou « 6300 » (secondes) → « 1 h 45 ». */
fun formatDuration(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val minutes: Int = when {
        raw.contains(":") -> {
            val p = raw.split(":").mapNotNull { it.trim().toIntOrNull() }
            when (p.size) {
                3 -> p[0] * 60 + p[1]
                2 -> p[0]
                else -> return raw
            }
        }
        raw.trim().endsWith("min") -> raw.filter { it.isDigit() }.toIntOrNull() ?: return raw
        else -> raw.trim().toIntOrNull()?.let { if (it > 600) it / 60 else it } ?: return raw
    }
    if (minutes <= 0) return null
    return if (minutes >= 60) "${minutes / 60} h ${"%02d".format(minutes % 60)}" else "$minutes min"
}

fun formatRating(raw: String?): String? =
    raw?.toDoubleOrNull()?.takeIf { it > 0 }?.let { "★ " + "%.1f".format(it).replace('.', ',') }

/** 4 523 000 ms → « 1:15:23 », 312 000 ms → « 5:12 ». */
fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

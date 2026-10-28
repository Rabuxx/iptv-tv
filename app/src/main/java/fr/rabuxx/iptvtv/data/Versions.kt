package fr.rabuxx.iptvtv.data

/** Comparaison de numéros de version (« 0.10.1 » > « 0.9.3 »). Sans dépendance Android : testable en JVM. */
object Versions {

    fun isNewer(candidate: String, current: String): Boolean {
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parts(version: String): List<Int> =
        version.trim().removePrefix("v").split('.', '-').map { it.toIntOrNull() ?: 0 }
}

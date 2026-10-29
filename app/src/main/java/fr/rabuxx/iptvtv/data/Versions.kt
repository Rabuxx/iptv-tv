package fr.rabuxx.iptvtv.data

/**
 * Comparaison de numéros de version façon semver, sans dépendance Android (testable en JVM) :
 * « 0.10.1 » > « 0.9.3 », « 0.7.0 » > « 0.7.0-beta.2 » > « 0.7.0-beta.1 » > « 0.6.0 ».
 */
object Versions {

    private data class Parsed(val core: List<Int>, val pre: List<String>)

    private fun parse(version: String): Parsed {
        val v = version.trim().removePrefix("v").substringBefore('+')
        val core = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val pre = if ('-' in v) v.substringAfter('-').split('.').filter { it.isNotEmpty() } else emptyList()
        return Parsed(core, pre)
    }

    fun compare(a: String, b: String): Int {
        val x = parse(a)
        val y = parse(b)
        for (i in 0 until maxOf(x.core.size, y.core.size)) {
            val c = x.core.getOrElse(i) { 0 }.compareTo(y.core.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        // Même numéro : une version finale passe devant ses pré-versions
        if (x.pre.isEmpty() || y.pre.isEmpty()) return y.pre.size.coerceAtMost(1) - x.pre.size.coerceAtMost(1)
        for (i in 0 until maxOf(x.pre.size, y.pre.size)) {
            val p = x.pre.getOrNull(i) ?: return -1
            val q = y.pre.getOrNull(i) ?: return 1
            val pn = p.toIntOrNull()
            val qn = q.toIntOrNull()
            val c = when {
                pn != null && qn != null -> pn.compareTo(qn)
                pn != null -> -1
                qn != null -> 1
                else -> p.compareTo(q)
            }
            if (c != 0) return c
        }
        return 0
    }

    fun isNewer(candidate: String, current: String): Boolean = compare(candidate, current) > 0

    fun isPrerelease(version: String): Boolean = parse(version).pre.isNotEmpty()

    /**
     * Code de version Android, toujours croissant : 0.7.0-beta.2 → 70002, 0.7.0 → 70099.
     * (Même formule que dans app/build.gradle.kts.)
     */
    fun versionCode(version: String): Int {
        val p = parse(version)
        val core = p.core.getOrElse(0) { 0 } * 10_000 + p.core.getOrElse(1) { 0 } * 100 + p.core.getOrElse(2) { 0 }
        val pre = if (p.pre.isEmpty()) 99 else (p.pre.lastOrNull()?.toIntOrNull() ?: 1).coerceIn(1, 98)
        return core * 100 + pre
    }
}

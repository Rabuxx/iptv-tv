package fr.rabuxx.iptvtv.data

import android.content.Context

/** Stockage local : identifiants, dernière catégorie par section, positions de lecture. */
class CredentialsStore(context: Context) {

    private val prefs = context.getSharedPreferences("xtream", Context.MODE_PRIVATE)
    private val positions = context.getSharedPreferences("positions", Context.MODE_PRIVATE)

    fun load(): Credentials? {
        val server = prefs.getString("server", null) ?: return null
        val user = prefs.getString("username", null) ?: return null
        val pass = prefs.getString("password", null) ?: return null
        return Credentials(server, user, pass)
    }

    fun save(creds: Credentials) {
        prefs.edit()
            .putString("server", creds.server)
            .putString("username", creds.username)
            .putString("password", creds.password)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
        positions.edit().clear().apply()
    }

    fun lastCategoryId(section: Section): String? = prefs.getString("last_category_${section.name}", null)

    fun saveLastCategoryId(section: Section, id: String) =
        prefs.edit().putString("last_category_${section.name}", id).apply()

    /**
     * Vrai une seule fois après l'arrivée du tri par défaut (v0.4) : on ignore alors les catégories
     * mémorisées par les versions précédentes pour ouvrir directement le sport FR et les dernières sorties.
     */
    fun consumeDefaultsReset(): Boolean {
        if (prefs.getInt("defaults_version", 0) >= DEFAULTS_VERSION) return false
        prefs.edit().putInt("defaults_version", DEFAULTS_VERSION).apply()
        return true
    }

    /** Mettre les catégories FR / VOSTFR en avant (activé par défaut). */
    var preferFrench: Boolean
        get() = prefs.getBoolean("prefer_french", true)
        set(value) = prefs.edit().putBoolean("prefer_french", value).apply()

    // ---- Catégories masquées / verrouillées (clés « Live:12 », « Movies:34 »…) ----

    var hiddenCategories: Set<String>
        get() = prefs.getStringSet("hidden_categories", emptySet()).orEmpty().toSet()
        set(value) = prefs.edit().putStringSet("hidden_categories", value).apply()

    var lockedCategories: Set<String>
        get() = prefs.getStringSet("locked_categories", emptySet()).orEmpty().toSet()
        set(value) = prefs.edit().putStringSet("locked_categories", value).apply()

    // ---- Code parental (seule l'empreinte SHA-256 est stockée) ----

    val hasPin: Boolean get() = prefs.getString("pin_hash", null) != null

    fun setPin(pin: String?) {
        val edit = prefs.edit()
        if (pin == null) edit.remove("pin_hash") else edit.putString("pin_hash", hash(pin))
        edit.apply()
    }

    fun checkPin(pin: String): Boolean = prefs.getString("pin_hash", null) == hash(pin)

    private fun hash(pin: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest("rabux-tv:$pin".toByteArray())
            .joinToString("") { "%02x".format(it) }

    /** Aperçu vidéo des chaînes au focus dans la grille Direct. */
    var previewEnabled: Boolean
        get() = prefs.getBoolean("preview_enabled", true)
        set(value) = prefs.edit().putBoolean("preview_enabled", value).apply()

    /** Dernière version lancée, pour afficher « Quoi de neuf » une fois après une mise à jour. */
    var lastSeenVersion: String?
        get() = prefs.getString("last_seen_version", null)
        set(value) = prefs.edit().putString("last_seen_version", value).apply()

    /** Canal de mise à jour : false = versions stables, true = versions bêta aussi. */
    var betaChannel: Boolean
        get() = prefs.getBoolean("beta_channel", false)
        set(value) = prefs.edit().putBoolean("beta_channel", value).apply()

    /** Envoi des rapports de plantage (si l'app a été compilée avec un DSN Sentry). */
    var crashReports: Boolean
        get() = prefs.getBoolean("crash_reports", true)
        set(value) = prefs.edit().putBoolean("crash_reports", value).commit().let { }

    /** Clé TMDB saisie depuis le téléphone ; prioritaire sur celle intégrée à la compilation. */
    var tmdbKey: String?
        get() = prefs.getString("tmdb_key", null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString("tmdb_key", value).apply()

    /** Délai avant l'économiseur d'écran, en minutes (0 = désactivé). */
    var screensaverMinutes: Int
        get() = prefs.getInt("screensaver_minutes", 10)
        set(value) = prefs.edit().putInt("screensaver_minutes", value).apply()

    /** Tri et note minimale choisis pour les films ou les séries. */
    fun listFilter(section: Section) = ListFilter(
        sort = prefs.getString("sort_${section.name}", null)
            ?.let { name -> SortMode.entries.firstOrNull { it.name == name } }
            ?: SortMode.Recent,
        minRating = prefs.getInt("min_rating_${section.name}", 0),
    )

    fun saveListFilter(section: Section, filter: ListFilter) {
        prefs.edit()
            .putString("sort_${section.name}", filter.sort.name)
            .putInt("min_rating_${section.name}", filter.minRating)
            .apply()
    }

    private companion object {
        const val DEFAULTS_VERSION = 2
    }

    // ---- Reprise de lecture (films / épisodes) ----

    fun position(key: String): Long = positions.getLong("$key:pos", 0L)

    fun duration(key: String): Long = positions.getLong("$key:dur", 0L)

    /** Avancement entre 0 et 1, ou 0 si jamais lancé. */
    fun progress(key: String): Float {
        val pos = positions.getLong("$key:pos", 0L)
        val dur = positions.getLong("$key:dur", 0L)
        return if (pos > 0 && dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    }

    fun savePosition(key: String, positionMs: Long, durationMs: Long) {
        val finished = durationMs > 0 && positionMs > durationMs * 0.95
        val tooEarly = positionMs < 30_000
        val edit = positions.edit()
        if (finished || tooEarly) {
            edit.remove("$key:pos").remove("$key:dur")
        } else {
            edit.putLong("$key:pos", positionMs).putLong("$key:dur", durationMs)
        }
        edit.apply()
    }
}

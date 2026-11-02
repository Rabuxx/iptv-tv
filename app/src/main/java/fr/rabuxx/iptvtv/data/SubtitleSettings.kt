package fr.rabuxx.iptvtv.data

/** Sous-titres choisis par défaut à chaque lecture (on peut toujours changer de piste pendant la lecture). */
enum class SubtitleMode(val label: String) {
    Auto("Automatiques"),
    French("Français"),
    English("Anglais"),
    Off("Désactivés"),
}

enum class SubtitleSize(val label: String, val scale: Float) {
    Small("Petite", 0.8f),
    Medium("Moyenne", 1f),
    Large("Grande", 1.3f),
    XLarge("Très grande", 1.6f),
}

enum class SubtitleBackground(val label: String, val argb: Long) {
    None("Aucun (contour noir)", 0x00000000),
    Translucent("Noir transparent", 0x99000000),
    Opaque("Noir opaque", 0xFF000000),
}

enum class SubtitleColor(val label: String, val argb: Long) {
    White("Blanc", 0xFFFFFFFF),
    Yellow("Jaune", 0xFFFFE14D),
}

data class SubtitleSettings(
    val mode: SubtitleMode = SubtitleMode.Auto,
    val size: SubtitleSize = SubtitleSize.Medium,
    val background: SubtitleBackground = SubtitleBackground.Translucent,
    val color: SubtitleColor = SubtitleColor.White,
)

/** Valeur suivante, en boucle (boutons des réglages). */
inline fun <reified T : Enum<T>> T.next(): T = enumValues<T>().let { it[(ordinal + 1) % it.size] }

/** Lecture d'une valeur enregistrée, avec repli si elle n'existe plus. */
inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default

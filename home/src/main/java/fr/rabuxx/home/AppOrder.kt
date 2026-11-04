package fr.rabuxx.home

import java.text.Collator
import java.util.Locale

/** Apps affichées en tête de rangée, dans cet ordre. */
val PINNED_PACKAGES = listOf(
    "fr.rabuxx.iptvtv",
    "com.netflix.ninja",
    "org.smarttube.stable",
    "com.disney.disneyplus",
    "com.amazon.amazonvideo.livingroom",
)

/** Apps épinglées d'abord, puis le reste par ordre alphabétique français (accents et casse ignorés). */
fun <T> orderApps(
    apps: List<T>,
    packageOf: (T) -> String,
    labelOf: (T) -> String,
    pinned: List<String> = PINNED_PACKAGES,
): List<T> {
    val collator = Collator.getInstance(Locale.FRENCH).apply { strength = Collator.PRIMARY }
    val (first, others) = apps.partition { packageOf(it) in pinned }
    return first.sortedBy { pinned.indexOf(packageOf(it)) } +
        others.sortedWith { a, b -> collator.compare(labelOf(a), labelOf(b)) }
}

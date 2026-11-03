package fr.rabuxx.home

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppEntry(
    val packageName: String,
    val activityName: String,
    val label: String,
    val image: ImageBitmap,
    /** Vrai pour une bannière 16:9, faux pour une simple icône à centrer dans la tuile. */
    val hasBanner: Boolean,
    val accent: Color,
)

/** Liste les apps Android TV installées et les ouvre. */
class AppCatalog(private val context: Context) {

    /** À appeler hors du thread principal : charger les images de toutes les apps prend du temps. */
    fun load(): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val entries = pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .mapNotNull { info -> runCatching { toEntry(info) }.getOrNull() }
        return orderApps(entries, { it.packageName }, { it.label })
    }

    /** Vrai si l'app s'est ouverte. Une app désinstallée entre-temps ne doit jamais faire planter l'accueil. */
    fun launch(app: AppEntry): Boolean = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setComponent(ComponentName(app.packageName, app.activityName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
        )
    }.isSuccess

    fun openSettings(): Boolean = runCatching {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess

    private fun toEntry(info: ResolveInfo): AppEntry {
        val pm = context.packageManager
        val banner = info.activityInfo.loadBanner(pm)
        val bitmap = if (banner != null) {
            banner.toBitmap(BANNER_WIDTH, BANNER_HEIGHT, Bitmap.Config.ARGB_8888)
        } else {
            info.activityInfo.loadIcon(pm).toBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        }
        return AppEntry(
            packageName = info.activityInfo.packageName,
            activityName = info.activityInfo.name,
            label = info.loadLabel(pm).toString(),
            image = bitmap.asImageBitmap(),
            hasBanner = banner != null,
            accent = averageColor(bitmap),
        )
    }

    /** Réduire l'image à un pixel donne sa couleur moyenne : elle teinte le fond quand l'app a le focus. */
    private fun averageColor(bitmap: Bitmap): Color {
        val pixel = Bitmap.createScaledBitmap(bitmap, 1, 1, true)
        return Color(pixel.getPixel(0, 0)).copy(alpha = 1f)
    }

    private companion object {
        const val BANNER_WIDTH = 640
        const val BANNER_HEIGHT = 360
        const val ICON_SIZE = 192
    }
}

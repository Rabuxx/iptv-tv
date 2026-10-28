package fr.rabuxx.iptvtv.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import fr.rabuxx.iptvtv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val version: String,
    val apkUrl: String,
    val notes: String?,
)

/**
 * Mise à jour depuis les Releases GitHub du dépôt BuildConfig.UPDATE_REPO (« propriétaire/depot »).
 * Le dépôt doit être public pour que l'API soit accessible sans jeton.
 */
class Updater(private val context: Context) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    val currentVersion: String = BuildConfig.VERSION_NAME

    /** @return la mise à jour disponible, ou null si l'app est à jour. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", USER_AGENT)
            .build()
        val body = http.newCall(request).execute().use { resp ->
            when {
                resp.code == 404 -> throw IOException("Aucune release publiée sur ${BuildConfig.UPDATE_REPO}")
                !resp.isSuccessful -> throw IOException("GitHub a répondu HTTP ${resp.code}")
                else -> resp.body?.string() ?: throw IOException("Réponse vide de GitHub")
            }
        }
        val json = JSONObject(body)
        val version = json.optString("tag_name").removePrefix("v").trim()
        val assets = json.optJSONArray("assets")
        val apk = assets?.let { arr ->
            (0 until arr.length())
                .mapNotNull { arr.optJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") }
                ?.optString("browser_download_url")
        } ?: throw IOException("La dernière release ne contient pas d'APK")

        if (isNewer(version, currentVersion)) {
            UpdateInfo(version = version, apkUrl = apk, notes = json.optString("body").takeIf { it.isNotBlank() })
        } else {
            null
        }
    }

    /** Télécharge l'APK dans le cache de l'app. */
    suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "rabux-tv-${info.version}.apk")
        val request = Request.Builder().url(info.apkUrl).header("User-Agent", USER_AGENT).build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Téléchargement impossible (HTTP ${resp.code})")
            val body = resp.body ?: throw IOException("Téléchargement vide")
            val total = body.contentLength()
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var lastReport = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        if (total > 0 && done - lastReport > 256 * 1024) {
                            lastReport = done
                            onProgress(done.toFloat() / total)
                        }
                    }
                }
            }
        }
        onProgress(1f)
        target
    }

    /** Android 8+ : l'app doit être autorisée à installer des applications. */
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Ouvre l'écran système d'autorisation. @return false si la TV ne propose pas cet écran. */
    fun openInstallPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    companion object {
        /** « 0.10.1 » > « 0.9.3 », comparaison numérique segment par segment. */
        fun isNewer(candidate: String, current: String): Boolean {
            val a = candidate.split('.', '-').map { it.toIntOrNull() ?: 0 }
            val b = current.split('.', '-').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}

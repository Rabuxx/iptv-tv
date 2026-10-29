package fr.rabuxx.iptvtv

import android.app.Application
import fr.rabuxx.iptvtv.data.CredentialsStore
import fr.rabuxx.iptvtv.data.Reminders
import fr.rabuxx.iptvtv.data.Scrubber
import fr.rabuxx.iptvtv.data.Versions
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid

class RabuxApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Reminders.ensureChannel(this)
        initCrashReports()
    }

    /**
     * Rapports de plantage Sentry (ou GlitchTip, compatible) : seulement si l'app a été compilée avec un DSN
     * (secret GitHub SENTRY_DSN) et si l'option n'a pas été désactivée dans Réglages.
     * Aucune donnée personnelle n'est envoyée, et les identifiants IPTV sont masqués dans chaque rapport.
     */
    private fun initCrashReports() {
        val dsn = BuildConfig.SENTRY_DSN
        if (dsn.isBlank() || !CredentialsStore(this).crashReports) return
        SentryAndroid.init(this) { options ->
            options.dsn = dsn
            options.release = "rabux-tv@${BuildConfig.VERSION_NAME}"
            options.environment = if (Versions.isPrerelease(BuildConfig.VERSION_NAME)) "beta" else "stable"
            options.isSendDefaultPii = false
            options.isAttachScreenshot = false
            options.isAttachViewHierarchy = false
            options.beforeSend = SentryOptions.BeforeSendCallback { event, _ -> scrub(event) }
            options.beforeBreadcrumb = SentryOptions.BeforeBreadcrumbCallback { breadcrumb, _ ->
                breadcrumb.message = Scrubber.scrub(breadcrumb.message)
                breadcrumb.data.keys.toList().forEach { key ->
                    (breadcrumb.data[key] as? String)?.let { breadcrumb.setData(key, Scrubber.scrub(it) ?: "") }
                }
                breadcrumb
            }
        }
    }

    private fun scrub(event: SentryEvent): SentryEvent {
        event.message?.let { m ->
            m.message = Scrubber.scrub(m.message)
            m.formatted = Scrubber.scrub(m.formatted)
        }
        event.exceptions?.forEach { e -> e.value = Scrubber.scrub(e.value) }
        event.breadcrumbs?.forEach { b -> b.message = Scrubber.scrub(b.message) }
        event.request = null
        return event
    }
}

package fr.rabuxx.iptvtv.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom

/** Ce que le téléphone permet de saisir. */
enum class PhoneLinkMode { Login, Tmdb }

/**
 * Petit serveur web sur le réseau local : la TV affiche un QR code, le téléphone ouvre la page
 * et saisit les identifiants (ou la clé TMDB) avec son clavier.
 * - actif uniquement pendant que l'écran concerné est affiché ;
 * - adresse protégée par un jeton aléatoire présent dans le QR code ;
 * - le formulaire passe en clair sur le réseau local (pas de HTTPS possible sans certificat).
 */
class PhoneLink(
    private val mode: PhoneLinkMode,
    private val onSubmit: (Map<String, String>) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: ServerSocket? = null
    private val token = randomToken()

    /** Adresse à ouvrir sur le téléphone, ou null si la TV n'a pas d'adresse sur le réseau local. */
    var url: String? = null
        private set

    fun start(): String? {
        val ip = localIpv4() ?: return null
        val socket = PORTS.firstNotNullOfOrNull { port ->
            runCatching { ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(port)) } }.getOrNull()
        } ?: return null
        server = socket
        url = "http://$ip:${socket.localPort}/$token"
        scope.launch {
            while (isActive) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                launch { handle(client) }
            }
        }
        return url
    }

    fun stop() {
        runCatching { server?.close() }
        server = null
        scope.cancel()
    }

    private fun handle(client: Socket) {
        client.use { s ->
            s.soTimeout = 10_000
            val out = s.getOutputStream()
            val req = MiniHttp.read(s.getInputStream())
            if (req == null) {
                out.write(MiniHttp.response("400 Bad Request", page("Requête invalide", "<p>Recommence en scannant le QR code.</p>")))
                out.flush()
                return
            }
            val response = when {
                req.path.substringBefore('?') != "/$token" -> MiniHttp.response("404 Not Found", page("Lien expiré", "<p>Scanne à nouveau le QR code affiché sur la TV.</p>"))
                req.method == "POST" -> {
                    val form = MiniHttp.parseForm(req.body).mapValues { it.value.trim() }
                    if (isValid(form)) {
                        onSubmit(form)
                        MiniHttp.response("200 OK", page("C'est envoyé ✓", "<p>Regarde ta TV : Rabux TV utilise ce que tu viens de saisir.</p>"))
                    } else {
                        MiniHttp.response("200 OK", formPage("Merci de remplir tous les champs."))
                    }
                }
                else -> MiniHttp.response("200 OK", formPage(null))
            }
            out.write(response)
            out.flush()
        }
    }

    private fun isValid(form: Map<String, String>) = when (mode) {
        PhoneLinkMode.Login -> listOf("server", "username", "password").all { !form[it].isNullOrBlank() }
        PhoneLinkMode.Tmdb -> !form["tmdb"].isNullOrBlank()
    }

    private fun formPage(error: String?): String {
        val fields = when (mode) {
            PhoneLinkMode.Login -> """
                <label>Adresse du serveur<input name="server" type="url" placeholder="http://exemple.com:8080" autocapitalize="off" autocorrect="off" required></label>
                <label>Identifiant<input name="username" autocapitalize="off" autocorrect="off" required></label>
                <label>Mot de passe<input name="password" type="password" required></label>
            """
            PhoneLinkMode.Tmdb -> """
                <label>Clé API TMDB (v3) ou jeton de lecture (v4)<input name="tmdb" autocapitalize="off" autocorrect="off" required></label>
                <p class="hint">themoviedb.org › Paramètres › API</p>
            """
        }
        val title = if (mode == PhoneLinkMode.Login) "Connexion à Rabux TV" else "Clé TMDB"
        val err = error?.let { "<p class=\"err\">${MiniHttp.escape(it)}</p>" }.orEmpty()
        return page(title, "$err<form method=\"post\">$fields<button>Envoyer à la TV</button></form>")
    }

    private fun page(title: String, body: String) = """
        <!doctype html><html lang="fr"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>${MiniHttp.escape(title)}</title>
        <style>
          body{margin:0;font-family:-apple-system,system-ui,sans-serif;background:#0b0b10;color:#fff;display:flex;justify-content:center}
          main{width:100%;max-width:420px;padding:28px 20px}
          h1{font-size:24px;margin:0 0 20px}
          label{display:block;font-size:14px;color:#a1a1aa;margin:0 0 14px}
          input{display:block;width:100%;box-sizing:border-box;margin-top:6px;padding:14px;border-radius:12px;border:1px solid #333;background:#1c1c22;color:#fff;font-size:16px}
          button{width:100%;padding:15px;border:0;border-radius:12px;background:#fff;color:#000;font-size:16px;font-weight:600;margin-top:8px}
          .err{color:#ff6961}.hint{font-size:12px;color:#71717a}
        </style></head>
        <body><main><h1>${MiniHttp.escape(title)}</h1>$body</main></body></html>
    """.trimIndent()

    companion object {
        private val PORTS = listOf(8765, 8766, 8767, 0)

        private fun randomToken(): String {
            val chars = "abcdefghjkmnpqrstuvwxyz23456789"
            val rnd = SecureRandom()
            return (1..8).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
        }

        /** Adresse IPv4 de la TV sur le réseau local (Wi-Fi ou Ethernet). */
        fun localIpv4(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("eth")) 0 else 1 }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }
}

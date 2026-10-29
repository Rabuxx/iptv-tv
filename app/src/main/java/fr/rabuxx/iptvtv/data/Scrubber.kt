package fr.rabuxx.iptvtv.data

/**
 * Retire les identifiants IPTV d'un texte avant de l'envoyer dans un rapport de plantage.
 * Les adresses Xtream contiennent l'identifiant et le mot de passe :
 * « http://srv:8080/live/USER/PASS/123.ts », « player_api.php?username=USER&password=PASS ».
 */
object Scrubber {

    private val PATH = Regex("/(live|movie|series|timeshift)/[^/\\s]+/[^/\\s]+/", RegexOption.IGNORE_CASE)
    private val QUERY = Regex("(username|password|api_key)=[^&\\s\"']+", RegexOption.IGNORE_CASE)
    private val HOST = Regex("https?://[^/\\s\"']+")

    fun scrub(text: String?): String? = text
        ?.replace(PATH) { "/${it.groupValues[1]}/***/***/" }
        ?.replace(QUERY) { "${it.groupValues[1]}=***" }
        // L'adresse du serveur n'apporte rien au diagnostic et peut identifier le fournisseur
        ?.replace(HOST, "http://serveur")
}

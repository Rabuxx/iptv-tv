package fr.rabuxx.iptvtv.data

import java.io.InputStream
import java.net.URLDecoder

/**
 * Lecture minimale d'une requête HTTP/1.1 (serveur local de saisie depuis le téléphone).
 * Sans dépendance Android : testable en JVM.
 */
object MiniHttp {

    data class Request(val method: String, val path: String, val headers: Map<String, String>, val body: String)

    private const val MAX_HEADER = 8 * 1024
    private const val MAX_BODY = 16 * 1024

    /** @return la requête, ou null si elle est mal formée ou trop grosse. */
    fun read(input: InputStream): Request? {
        val head = StringBuilder()
        // Lecture octet par octet jusqu'à la ligne vide qui termine les en-têtes
        while (!head.endsWith("\r\n\r\n") && !head.endsWith("\n\n")) {
            val b = input.read()
            if (b < 0 || head.length > MAX_HEADER) return null
            head.append(b.toChar())
        }
        val lines = head.lines().filter { it.isNotBlank() }
        val requestLine = lines.firstOrNull()?.split(' ') ?: return null
        if (requestLine.size < 2) return null
        val headers = lines.drop(1).mapNotNull { line ->
            val i = line.indexOf(':')
            if (i <= 0) null else line.substring(0, i).trim().lowercase() to line.substring(i + 1).trim()
        }.toMap()

        val length = headers["content-length"]?.toIntOrNull() ?: 0
        if (length < 0 || length > MAX_BODY) return null
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(body, read, length - read)
            if (n < 0) break
            read += n
        }
        return Request(requestLine[0].uppercase(), requestLine[1], headers, String(body, 0, read, Charsets.UTF_8))
    }

    /** « server=http%3A%2F%2Fa&user=b » → {server=http://a, user=b}. */
    fun parseForm(body: String): Map<String, String> =
        body.split('&').filter { '=' in it }.associate { pair ->
            val (k, v) = pair.split('=', limit = 2)
            URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
        }

    fun response(status: String, html: String): ByteArray {
        val body = html.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $status\r\n" +
            "Content-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Connection: close\r\n\r\n"
        return head.toByteArray(Charsets.UTF_8) + body
    }

    fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}

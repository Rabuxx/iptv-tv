package fr.rabuxx.iptvtv.cast

import java.io.BufferedInputStream
import java.io.File
import java.io.RandomAccessFile
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import kotlin.concurrent.thread

/** Plage d'octets à envoyer : (début, fin incluse), ou null si la demande est invalide. */
fun parseByteRange(header: String?, size: Long): Pair<Long, Long>? {
    if (size <= 0) return null
    if (header == null) return 0L to size - 1
    val match = Regex("""bytes=(\d*)-(\d*)""").find(header.trim()) ?: return null
    val (first, last) = match.destructured
    return when {
        first.isEmpty() && last.isEmpty() -> null
        // « bytes=-500 » : les 500 derniers octets
        first.isEmpty() -> (size - last.toLong()).coerceAtLeast(0) to size - 1
        else -> {
            val start = first.toLong()
            val end = last.toLongOrNull()?.coerceAtMost(size - 1) ?: (size - 1)
            if (start > end) null else start to end
        }
    }
}

/**
 * Sert un fichier téléchargé à la Chromecast, sur le Wi-Fi local : aucun passage par internet
 * ni par l'abonnement IPTV. Le jeton aléatoire de l'adresse évite qu'un autre appareil du réseau
 * puisse lire le fichier. S'arrête avec [stop].
 */
class LocalFileServer {

    private var server: ServerSocket? = null

    /** Adresse du fichier, ou null si la tablette n'a pas d'adresse sur le réseau local. */
    fun serve(file: File, contentType: String, address: String? = localAddress()): String? {
        stop()
        val host = address ?: return null
        val token = UUID.randomUUID().toString()
        val socket = ServerSocket(0)
        server = socket
        thread(isDaemon = true, name = "cast-file-server") {
            runCatching {
                while (!socket.isClosed) {
                    val client = socket.accept()
                    thread(isDaemon = true) { client.use { handle(it, file, contentType, token) } }
                }
            }
        }
        return "http://$host:${socket.localPort}/$token/${file.name}"
    }

    fun stop() {
        runCatching { server?.close() }
        server = null
    }

    private fun handle(client: Socket, file: File, contentType: String, token: String) {
        runCatching {
            val reader = BufferedInputStream(client.getInputStream()).bufferedReader(Charsets.ISO_8859_1)
            val requestLine = reader.readLine().orEmpty().split(' ')
            val headers = generateSequence { reader.readLine()?.takeIf { it.isNotEmpty() } }
                .mapNotNull { line -> line.split(':', limit = 2).takeIf { it.size == 2 } }
                .associate { (name, value) -> name.trim().lowercase() to value.trim() }
            val out = client.getOutputStream()
            val method = requestLine.getOrNull(0)
            if ((method != "GET" && method != "HEAD") || requestLine.getOrNull(1)?.startsWith("/$token/") != true) {
                out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                return@runCatching
            }
            val size = file.length()
            val rangeHeader = headers["range"]
            val range = parseByteRange(rangeHeader, size)
            if (range == null) {
                out.write("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$size\r\nConnection: close\r\n\r\n".toByteArray())
                return@runCatching
            }
            val (start, end) = range
            val length = end - start + 1
            val head = buildString {
                append(if (rangeHeader != null) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
                append("Content-Type: $contentType\r\n")
                append("Content-Length: $length\r\n")
                append("Accept-Ranges: bytes\r\n")
                append("Access-Control-Allow-Origin: *\r\n")
                if (rangeHeader != null) append("Content-Range: bytes $start-$end/$size\r\n")
                append("Connection: close\r\n\r\n")
            }
            out.write(head.toByteArray())
            if (method == "GET") {
                RandomAccessFile(file, "r").use { input ->
                    input.seek(start)
                    val buffer = ByteArray(64 * 1024)
                    var remaining = length
                    while (remaining > 0) {
                        val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        remaining -= read
                    }
                }
            }
            out.flush()
        }
    }

    private fun localAddress(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()
}

package importer

import com.sun.net.httpserver.HttpServer
import java.lang.Exception
import java.net.InetSocketAddress

class Server(private val uri: String) {
    fun create(): HttpServer {
        if (!uri.contains("http://")) {
            throw Exception("redirect_uri must contain 'http://'.")
        }

        val http = uri.substring(0, uri.indexOf("://") + 3)

        val domain = uri.substring(http.length, uri.lastIndexOf(':'))

        val port = kotlin.run {
            val initialString = uri.substring(uri.lastIndexOf(':') + 1)
            try {
                initialString.substring(0, initialString.indexOf('/'))
            } catch (e: Exception) {
                initialString.substring(0, initialString.length)
            }
        }

        val contextAction = try {
            uri.substring(uri.indexOf(port) + port.length).ifBlank { "/" }
        } catch (e: Exception) {
            "/"
        }

        val server = HttpServer.create(InetSocketAddress(domain, port.toInt()), 0)
        server.createContext(contextAction, MyHttpHandler())

        return server.also { currentServer = it }
    }
}
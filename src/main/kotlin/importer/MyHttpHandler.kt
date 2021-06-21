package importer

import com.github.kevinsawicki.http.HttpRequest
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler

object MyHttpHandler : HttpHandler {
    private const val CODE = "code"

    override fun handle(exchange: HttpExchange?) {
        if (exchange == null) {
            return
        }

        App.createImportingMessage()

        val urlRedirected = exchange.requestURI.toString()
        val code = urlRedirected.substring(urlRedirected.lastIndexOf("$CODE=") + 5, urlRedirected.lastIndex + 1)
        val tokenRequest = HttpRequest.get(deezerGetTempTokenURL(code)).body() as String

        val token = tokenRequest.substring(tokenRequest.indexOf('=') + 1, tokenRequest.indexOf("expires") - 1)
        fillToken(token)
    }
}
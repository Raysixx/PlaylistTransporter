package importer

import com.github.kevinsawicki.http.HttpRequest
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler

class MyHttpHandler : HttpHandler {
    override fun handle(exchange: HttpExchange?) {
        if (exchange == null) {
            return
        }

        AppMessage.createImportingMessage()

        val urlRedirected = exchange.requestURI.toString()
        val code = urlRedirected.substring(urlRedirected.lastIndexOf("code=") + 5, urlRedirected.lastIndex + 1)
        val tokenRequest = HttpRequest.get(deezerGetTempTokenURL(code)).body() as String

        tokenRequest.substring(tokenRequest.indexOf('=') + 1, tokenRequest.indexOf("expires") - 1)
            .let { currentToken = it }
    }
}
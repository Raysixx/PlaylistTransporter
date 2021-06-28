package server

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import deezer.DeezerImportScript
import ui.Ui

object HttpHandler : HttpHandler {
    override fun handle(exchange: HttpExchange?) {
        if (exchange == null) {
            return
        }

        Ui.createImportingMessage()

        val urlRedirected = exchange.requestURI.toString()

        val token = DeezerImportScript.getToken(urlRedirected)
        DeezerImportScript.fillToken(token)
    }
}
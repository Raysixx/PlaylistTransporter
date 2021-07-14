package server

import app.AppInterface
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import client.Utils
import ui.UI

object HttpHandler : HttpHandler {
    override fun handle(exchange: HttpExchange?) {
        if (exchange == null) {
            return
        }

        val currentApp = Server.currentApp

        UI.updateOperation(if (currentApp.operation == AppInterface.Operation.IMPORT) Utils.IMPORTING else Utils.EXPORTING)
        UI.createImportingScreen()

        val urlRedirected = exchange.requestURI.toString()

        currentApp.getToken(urlRedirected)
            .let { currentApp.fillToken(it) }
    }
}
package app.apps.deezer

import app.App
import com.github.kevinsawicki.http.HttpRequest
import org.json.JSONObject
import server.Server
import ui.UI
import java.awt.Desktop
import java.net.URI

open class DeezerApp: App() {
    @Volatile override var isRunning: Boolean = false

    override val appId = "487462"
    override val secretKey = "6194eef1df27977560fe0b2ddc8e8b5b"
    override val redirectUri = "http://localhost:5000"

    override var currentCountry: String? = null
    override fun fillCurrentCountry(currentToken: String) {
        val rawUser = HttpRequest.get(URLPlusToken(deezerGetUserURL(), currentToken)).body() as String
        val userJson = JSONObject(rawUser)
        val user = userJson.toMap()

        currentCountry = user[COUNTRY] as String
    }

    override fun getToken(urlRedirected: String): String {
        val code = getCode(urlRedirected)
        val tokenRequest = HttpRequest.get(deezerGetTempTokenURL(code)).body() as String

        return tokenRequest.substring(tokenRequest.indexOf('=') + 1, tokenRequest.indexOf("expires") - 1)
    }

    override fun generateToken() {
        Server.create(redirectUri, this)
        Desktop.getDesktop().browse(URI(deezerAuthenticationURL()))

        UI.updateOperation(IMPORT)
        UI.createLoginScreen()
        super.generateToken()
    }
}

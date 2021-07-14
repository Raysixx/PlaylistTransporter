package app.apps.spotify

import app.App
import com.github.kevinsawicki.http.HttpRequest
import org.json.JSONObject
import server.Server
import ui.UI
import java.awt.Desktop
import java.net.URI
import java.net.URLEncoder

open class SpotifyApp: App() {
    @Volatile override var isRunning: Boolean = false

    override val appId: String = "db69f31c2a5e4b5894e3afd54f8d83eb"
    override val secretKey: String = "3a3138c6fd1548c08ff9b5f7c99f1e03"
    override val redirectUri: String = "http://localhost:5001"

    override var currentCountry: String? = null
    override fun fillCurrentCountry(currentToken: String) {
        val user = URLPlusToken(spotifyGetUserURL(), currentToken).getURLResponse()

        currentCountry = user[COUNTRY] as String
    }

    @Suppress("SimpleRedundantLet")
    override fun getToken(urlRedirected: String): String {
        val code = getCode(urlRedirected)
        val tokenRequest = HttpRequest.post(spotifyGetTempTokenURL()).send(spotifyGetTempTokenPostBodyTemplate(code)).body() as String
        val tokenRequestMap = JSONObject(tokenRequest).toMap()
        val token = tokenRequestMap.entries.first { it.key == ACCESS_TOKEN }.value

        return token.toString()
    }

    override fun generateToken() {
        Server.create(SpotifyExport.redirectUri, this)
        Desktop.getDesktop().browse(URI(spotifyAuthenticationURL()))

        UI.updateOperation(operation!!.message)
        UI.createLoginScreen()
        super.generateToken()
    }

    override val scopes: String = SpotifyScopes.values().map { it.officialName }
        .reduce { acc, s -> "$acc $s" }
        .let { URLEncoder.encode(it, UTF_8) }
}
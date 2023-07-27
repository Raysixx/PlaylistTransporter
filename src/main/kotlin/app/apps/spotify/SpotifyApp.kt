package app.apps.spotify

import app.App
import app.apps.deezer.DeezerUser
import app.apps.deezer.deezerGetUserURL
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kevinsawicki.http.HttpRequest
import model.JsonUser
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

    override var user: JsonUser? = null
    override fun fillUser(currentToken: String) {
        user = HttpRequest.get(URLPlusToken(spotifyGetUserURL(), currentToken)).body().let {
            jacksonObjectMapper().readValue<SpotifyUser>(it)
        }
    }

    override fun getToken(urlRedirected: String): String {
        val code = getCode(urlRedirected)
        val tokenResponse = spotifyGetTempTokenURL().doURLPostWith<SpotifyToken>(spotifyGetTempTokenPostBodyTemplate(code))

        return tokenResponse.token
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
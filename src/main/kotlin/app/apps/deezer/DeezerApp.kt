package app.apps.deezer

import app.App
import client.Apps
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kevinsawicki.http.HttpRequest
import model.Artist
import model.JsonUser
import server.Server
import ui.UI
import java.awt.Desktop
import java.net.URI
import java.net.URLEncoder

@Suppress("UNCHECKED_CAST")
open class DeezerApp: App() {
    @Volatile override var isRunning: Boolean = false

    override val appId = "487462"
    override val secretKey = "6194eef1df27977560fe0b2ddc8e8b5b"
    override val redirectUri = "http://localhost:5000"

    override var user: JsonUser? = null
    override fun fillUser(currentToken: String) {
        user = HttpRequest.get(URLPlusToken(deezerGetUserURL(), currentToken)).body().let {
            jacksonObjectMapper().readValue<DeezerUser>(it)
        }
    }

    override fun getToken(urlRedirected: String): String {
        val code = getCode(urlRedirected)
        val tokenRequest = HttpRequest.get(deezerGetTempTokenURL(code)).body() as String

        return tokenRequest.substring(tokenRequest.indexOf('=') + 1, tokenRequest.indexOf("expires") - 1)
    }

    override fun generateToken() {
        Server.create(redirectUri, this)
        Desktop.getDesktop().browse(URI(deezerAuthenticationURL()))

        UI.updateOperation(operation!!.message)
        UI.createLoginScreen()
        super.generateToken()
    }

    override val scopes: String = DeezerScopes.values().map { it.officialName }
        .reduce { acc, s -> "$acc,$s" }
        .let { URLEncoder.encode(it, UTF_8) }

    protected fun searchTrackByNameAndArtist(serverTrackName: String, artists: List<Artist>): DeezerTrack? {
        val encodedServerTrackName = URLEncoder.encode(serverTrackName, UTF_8)

        return artists.firstNotNullOfOrNull { artist ->
            val encodedArtistName = URLEncoder.encode(artist.name, UTF_8)

            val serverTrackSearchURL = deezerSearchTrackURL(encodedServerTrackName, givenArtistName = encodedArtistName)

            val serverFoundTracks = serverTrackSearchURL.getURLResponse<DeezerFoundTracks>().data
                .ifEmpty { redoQueryIfHasProblematicWords(serverTrackSearchURL, Apps.DEEZER).first as List<DeezerTrack> }
                .sortedByDescending { it.title.equals(serverTrackName, true) }

            serverFoundTracks.firstOrNull {
                it.artist.name.equals(artist.name, true) &&
                it.readable
            }
        }
    }
}

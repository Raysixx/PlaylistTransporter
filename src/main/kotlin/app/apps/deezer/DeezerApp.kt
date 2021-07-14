package app.apps.deezer

import app.App
import client.Apps
import com.github.kevinsawicki.http.HttpRequest
import model.Artist
import org.json.JSONObject
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

        UI.updateOperation(operation!!.message)
        UI.createLoginScreen()
        super.generateToken()
    }

    override val scopes: String = DeezerScopes.values().map { it.officialName }
        .reduce { acc, s -> "$acc,$s" }
        .let { URLEncoder.encode(it, UTF_8) }

    protected fun searchTrackByNameAndArtist(trackName: String, artists: List<Artist>): HashMap<String, *>? {
        val rawTrackName = URLEncoder.encode(trackName, UTF_8)

        return artists.firstNotNullOfOrNull { artist ->
            val rawArtistName = URLEncoder.encode(artist.name, UTF_8)

            val trackSearchURL = deezerSearchTrackURL(rawTrackName, givenArtistName = rawArtistName)
            val rawFoundTracks = trackSearchURL.getURLResponse()

            val foundTracks = (rawFoundTracks[DATA] as List<HashMap<String, *>>)
                .ifEmpty { redoQueryIfHasProblematicWords(trackSearchURL, Apps.DEEZER).first }
                .sortedByDescending { it[TITLE].toString().equals(trackName, true) }

            foundTracks.firstOrNull {
                val artistObject = it[ARTIST] as HashMap<String, *>

                artistObject[NAME].toString().equals(artist.name, true) &&
                it[READABLE] as Boolean
            }
        }
    }
}

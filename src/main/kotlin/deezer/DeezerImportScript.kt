package deezer

import app.App
import client.playlistToImport
import com.github.kevinsawicki.http.HttpRequest
import exporter.FileExporter
import ui.Ui
import importer.ImporterInterface
import model.Playlist
import model.Track
import org.json.JSONObject
import server.Server
import java.awt.Desktop
import java.net.URI

@Suppress("UNCHECKED_CAST", "ComplexRedundantLet", "ControlFlowWithEmptyBody")
object DeezerImportScript: App(DeezerApp), ImporterInterface {
    override fun getPlaylistsFromJson(json: JSONObject): List<Playlist> {
        val jsonMap = json.toMap()

        val rawPlaylists = jsonMap[DATA] as List<HashMap<*, *>>
        return rawPlaylists.mapNotNull { rawPlaylist ->
            val playlistTitleAndId = rawPlaylist.filter { (metaDataName, _) ->
                metaDataName in listOf(TITLE, ID)
            }.values

            val title = playlistTitleAndId.first().toString()
            val id = playlistTitleAndId.last().toString().toLong()

            if (playlistToImport.isNotEmpty() && title.uppercase() !in playlistToImport) {
                return@mapNotNull null
            }

            Ui.updateMessage("$IMPORTING_PLAYLIST $title")

            val playlistRawTracks = rawPlaylist[TRACKLIST]
                .let { HttpRequest.get(urlPlusToken(currentToken, it.toString())).body() as String }
                .let { JSONObject(it) }

            Playlist(title, id)
                .also { playlist -> transformJsonIntoTracks(playlist.title, playlistRawTracks).let { trackList -> playlist.tracks.addAll(trackList) } }
        }
    }

    override fun transformJsonIntoTracks(playlistTitle: String, playlistRawTracks: JSONObject): List<Track> {
        val allTracks = mutableListOf<Track>()

        var current25Tracks = playlistRawTracks.toMap()
        while (current25Tracks != null) {
            val rawTracks = current25Tracks[DATA] as List<HashMap<*, *>>

            val currentTracks = rawTracks.map { rawTrack ->
                val trackArtistAndTrackName = rawTrack.filter { (metadataName, _) ->
                    metadataName in listOf(ARTIST, TITLE)
                }.values.map {
                    if (it is HashMap<*, *>) {
                        it[NAME]
                    } else {
                        it
                    }
                }.let { it.first() to it.last() }

                val artist = trackArtistAndTrackName.first.toString()
                val trackName = trackArtistAndTrackName.second.toString()

                Track(artist, trackName)
                    .also { Ui.updateMessage("$IMPORTING_PLAYLIST $playlistTitle <br>" +
                            "   ${it.artist} - ${it.name}") }
            }
            allTracks.addAll(currentTracks)

            val next25Tracks = current25Tracks[NEXT]
                ?.let { HttpRequest.get(it as String).body() as String }
                ?.let { JSONObject(it).toMap() }

            current25Tracks = next25Tracks
        }

        return allTracks
    }

    override fun fillToken(token: String) {
        currentToken = token
    }

    override fun getToken(urlRedirected: String): String {
        val code = urlRedirected.substring(urlRedirected.lastIndexOf("${CODE}=") + 5, urlRedirected.lastIndex + 1)
        val tokenRequest = HttpRequest.get(deezerGetTempTokenURL(appId, secretKey, code)).body() as String

        return tokenRequest.substring(tokenRequest.indexOf('=') + 1, tokenRequest.indexOf("expires") - 1)
    }

    override fun runImport() {
        Server.create(redirectUri)
        Desktop.getDesktop().browse(URI(deezerAuthenticationURL(appId, redirectUri)))
        Ui.createLoginMessage()

        while (currentToken == null) {}

        val playlists = (HttpRequest.get(deezerUserPlaylistsURL(currentToken)).body() as String)
            .let { JSONObject(it) }
            .let { getPlaylistsFromJson(it) }

        if (playlists.isEmpty()) {
            throw Exception("Nenhuma playlist encontrada.")
        } else {
            FileExporter.exportPlaylistsToFile(playlists)
            Ui.createDoneMessage()
        }
    }
}
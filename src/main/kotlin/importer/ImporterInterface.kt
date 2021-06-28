package importer

import model.Playlist
import model.Track
import org.json.JSONObject

interface ImporterInterface {
    fun getPlaylistsFromJson(json: JSONObject): List<Playlist>
    fun transformJsonIntoTracks(playlistTitle: String, playlistRawTracks: JSONObject): List<Track>
    fun getToken(urlRedirected: String): String
    fun fillToken(token: String)
    fun runImport()
}
package importer

import com.github.kevinsawicki.http.HttpRequest
import com.sun.net.httpserver.HttpServer
import org.json.JSONObject
import java.awt.Desktop
import java.io.File
import java.net.URI
import javax.swing.JFrame

@Volatile var currentToken: String? = null

const val appId = 487462
const val secretKey = "6194eef1df27977560fe0b2ddc8e8b5b"
const val redirect_uri = "http://localhost:5000"

var app: JFrame? = null

var saveWithName: String = "tracks"
var saveAs: String = "txt"
var importFilePath: String = "./"
var playlistToImport = mutableListOf<String>()

lateinit var currentServer: HttpServer

fun main(args: Array<String>) {
    args.forEach {
        when {
            it.startsWith("saveWithName", true) -> saveWithName = it.getArgValueOnly().ifBlank { saveWithName }
            it.startsWith("saveAs", true) -> saveAs = it.getArgValueOnly().ifBlank { saveAs }

            it.startsWith("importFilePath", true) -> importFilePath = it.getArgValueOnly().let { targetFilePath ->
                if (targetFilePath.isNotBlank() && targetFilePath.last() != '/') "$targetFilePath/" else targetFilePath
            }.ifBlank { importFilePath }

            it.startsWith("playlistToImport", true) -> it.getArgValueOnly().uppercase().takeIf { typedPlaylist -> typedPlaylist.isNotBlank()}
                ?.let { typedPlaylist -> playlistToImport.add(typedPlaylist) }
        }
    }

    Server(redirect_uri).create().start()
    Desktop.getDesktop().browse(URI(deezerAuthenticationURL()))
    AppMessage.createLoginMessage()

    while (currentToken == null) {}

    val playlists = (HttpRequest.get(deezerUserPlaylistsURL()).body() as String)
        .let { JSONObject(it) }
        .let { getPlaylistsFromJson(it, currentToken!!) }

    exportPlaylists(playlists)

    AppMessage.createDoneMessage()
}

private fun String.getArgValueOnly(): String {
    return this.substring(this.indexOf('=') + 1)
}

fun deezerAuthenticationURL() = "https://connect.deezer.com/oauth/auth.php?app_id=${appId}&redirect_uri=${redirect_uri}&perms=basic_access,email"
fun deezerGetTempTokenURL(code: String) = "https://connect.deezer.com/oauth/access_token.php?app_id=${appId}&secret=${secretKey}&code=$code"
fun deezerUserPlaylistsURL() = "https://api.deezer.com/user/me/playlists&access_token=$currentToken"

private fun getPlaylistsFromJson(json: JSONObject, token: String): List<Playlist> {
    val jsonMap = json.toMap()

    val rawPlaylists = jsonMap.entries.first { it.key == "data" }.value as List<HashMap<*, *>>
    return rawPlaylists.mapNotNull { rawPlaylist ->
        val playlistTitleAndId = rawPlaylist.filter { (metaDataName, _) ->
            metaDataName in listOf("title", "id")
        }.values

        val title = playlistTitleAndId.first().toString()
        val id = playlistTitleAndId.last().toString().toLong()

        if (playlistToImport.isNotEmpty() && title.uppercase() !in playlistToImport) {
            return@mapNotNull null
        }

        AppMessage.updateMessage("Importando playlist $title")

        val playlistRawTracks = rawPlaylist.entries.first { it.key == "tracklist" }.value
            .let { HttpRequest.get("${it}&access_token=$token").body() as String }
            .let { JSONObject(it) }

        Playlist(title, id)
            .also { playlist -> playlist.transformJsonIntoTracks(playlistRawTracks).let { trackList -> playlist.tracks.addAll(trackList) } }
    }
}

private fun exportPlaylists(playlists: List<Playlist>) {
    val file =  File("${importFilePath}$saveWithName.$saveAs")
    file.createNewFile()

    file.printWriter().use { out ->
        playlists.forEach { playlist ->
            out.println("Playlist ${playlist.title}:")

            playlist.tracks.forEach {
                out.println("    ${it.artist} - ${it.name}")
            }

            out.println()
        }
    }
}

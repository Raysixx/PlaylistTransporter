package importer

import com.github.kevinsawicki.http.HttpRequest
import org.json.JSONObject
import java.awt.Desktop
import java.net.URI

@Volatile
private var currentToken: String? = null

private const val appId = 487462
private const val secretKey = "6194eef1df27977560fe0b2ddc8e8b5b"
private const val redirect_uri = "http://localhost:5000"

lateinit var mainThread: Thread

var saveWithName: String = "tracks"
var saveAs: String = "txt"
var exportFilePath: String = "./"
var playlistTracksPerFile: Int? = null
val playlistToImport = mutableListOf<String>()

@Suppress("EnumEntryName")
enum class SupportedExtensions {
    txt,
    csv
}

@Suppress("ControlFlowWithEmptyBody", "ComplexRedundantLet")
fun main(args: Array<String>) {
    treatArgs(args)

    Server.create(redirect_uri)
    Desktop.getDesktop().browse(URI(deezerAuthenticationURL()))
    App.createLoginMessage()

    while (currentToken == null) {}

    val playlists = (HttpRequest.get(deezerUserPlaylistsURL()).body() as String)
        .let { JSONObject(it) }
        .let { Playlist.getPlaylistsFromJson(it) }

    Exporter.exportPlaylistsToFile(playlists)

    App.createDoneMessage()
}

fun fillToken(token: String) {
    currentToken = token
}

fun deezerAuthenticationURL() = "https://connect.deezer.com/oauth/auth.php?app_id=${appId}&redirect_uri=${redirect_uri}&perms=basic_access,email"
fun deezerGetTempTokenURL(code: String) = "https://connect.deezer.com/oauth/access_token.php?app_id=${appId}&secret=${secretKey}&code=$code"
fun deezerUserPlaylistsURL() = "https://api.deezer.com/user/me/playlists&access_token=$currentToken"
fun urlPlusToken(url: String) = "${url}&access_token=$currentToken"

private fun treatArgs(args: Array<String>) {
    args.forEach {
        when {
            it.startsWith("saveWithName", true) -> saveWithName = it.getArgValueOnly().ifBlank { saveWithName }
            it.startsWith("saveAs", true) -> saveAs = it.getArgValueOnly().lowercase().ifBlank { saveAs }.also { extension -> checkSupportedExtension(extension) }

            it.startsWith("exportFilePath", true) -> exportFilePath = it.getArgValueOnly().let { targetFilePath ->
                if (targetFilePath.isNotBlank() && targetFilePath.last() != '/') "$targetFilePath/" else targetFilePath
            }.ifBlank { exportFilePath }

            it.startsWith("playlistTracksPerFile", true) -> playlistTracksPerFile = it.getArgValueOnly().let { givenPlaylistTracksPerFile ->
                if (givenPlaylistTracksPerFile.isBlank()) {
                    playlistTracksPerFile
                } else {
                    givenPlaylistTracksPerFile.toInt()
                }
            }

            it.startsWith("playlistToImport", true) -> it.getArgValueOnly().uppercase().takeIf { typedPlaylist -> typedPlaylist.isNotBlank()}
                ?.let { typedPlaylist -> playlistToImport.add(typedPlaylist) }
        }
    }
}

private fun checkSupportedExtension(extension: String) {
    val supportedExtensions = SupportedExtensions.values().map { it.name }
    if (extension !in supportedExtensions) {
        throw Exception("Extension $extension is not supported.")
    }
}

private fun String.getArgValueOnly(): String {
    return this.substring(this.indexOf('=') + 1)
}
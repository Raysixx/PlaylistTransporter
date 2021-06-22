package importer

import com.github.kevinsawicki.http.HttpRequest
import org.json.JSONObject
import java.awt.Desktop
import java.io.File
import java.net.URI

@Volatile
private var currentToken: String? = null

private const val appId = 487462
private const val secretKey = "6194eef1df27977560fe0b2ddc8e8b5b"
private const val redirect_uri = "http://localhost:5000"

var saveWithName: String = "Playlists"
var saveAs: String = "txt"
var exportFilePath: String = "./"
var isSeparateFilesByPlaylist = false
var playlistTracksPerFile: Int? = null
val playlistToImport = mutableListOf<String>()

@Suppress("EnumEntryName")
enum class SupportedExtensions {
    txt,
    csv
}

@Suppress("ControlFlowWithEmptyBody", "ComplexRedundantLet")
fun main(args: Array<String>) {
    try {
        treatArgs(args)

        Server.create(redirect_uri)
        Desktop.getDesktop().browse(URI(deezerAuthenticationURL()))
        App.createLoginMessage()

        while (currentToken == null) {
        }

        val playlists = (HttpRequest.get(deezerUserPlaylistsURL()).body() as String)
            .let { JSONObject(it) }
            .let { Playlist.getPlaylistsFromJson(it) }

        if (playlists.isEmpty()) {
            throw Exception("Nenhuma playlist encontrada.")
        } else {
            Exporter.exportPlaylistsToFile(playlists)
            App.createDoneMessage()
        }
    } catch (exception: Exception) {
        throw exception.also { App.createErrorMessage(it.message!!) }
    }
}

fun fillToken(token: String) {
    currentToken = token
}

fun deezerAuthenticationURL() = "https://connect.deezer.com/oauth/auth.php?app_id=${appId}&redirect_uri=${redirect_uri}&perms=basic_access,email"
fun deezerGetTempTokenURL(code: String) = "https://connect.deezer.com/oauth/access_token.php?app_id=${appId}&secret=${secretKey}&code=$code"
fun deezerUserPlaylistsURL() = "https://api.deezer.com/user/me/playlists&access_token=$currentToken"
fun urlPlusToken(url: String) = "${url}&access_token=$currentToken"

fun String.removeWindowsInvalidCharacters(): String {
    return this.replace("[\\\\/:*?\"<>|]".toRegex(), "")
}

private fun treatArgs(args: Array<String>) {
    args.forEach {
        when {
            it.startsWith("saveWithName", true) -> saveWithName = it.getArgValueOnly().ifBlank { saveWithName }.removeWindowsInvalidCharacters()
            it.startsWith("saveAs", true) -> saveAs = it.getArgValueOnly().lowercase().ifBlank { saveAs }.also { extension -> checkSupportedExtension(extension) }

            it.startsWith("exportFilePath", true) -> exportFilePath = it.getArgValueOnly().let { targetFilePath ->
                if (targetFilePath.isNotBlank() && targetFilePath.last() != '/') "$targetFilePath/" else targetFilePath
            }.ifBlank { exportFilePath }.also { path -> checkExportFilePath(path) }

            it.startsWith("isSeparateFilesByPlaylist", true) -> isSeparateFilesByPlaylist = it.getArgValueOnly().let { isSeparateFilesByPlaylistValue ->
                when {
                    isSeparateFilesByPlaylistValue.equals("true", true) -> true
                    isSeparateFilesByPlaylistValue.equals("false", false) -> false
                    isSeparateFilesByPlaylistValue.isBlank() -> isSeparateFilesByPlaylist
                    else -> throw Exception("Valor inválido para 'isSeparateFilesByPlaylist': $isSeparateFilesByPlaylistValue")
                }
            }

            it.startsWith("playlistTracksPerFile", true) -> playlistTracksPerFile = it.getArgValueOnly().let { givenPlaylistTracksPerFile ->
                if (givenPlaylistTracksPerFile.isBlank()) {
                    playlistTracksPerFile
                } else {
                    checkGivenPlaylistTracksPerFile(givenPlaylistTracksPerFile)
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
        throw Exception("Extensão '$extension' não suportada.")
    }
}

private fun checkExportFilePath(path: String) {
    val pathExists = File(path).exists()
    if (!pathExists) {
        throw Exception("Diretório $path não encontrado.")
    }
}

private fun checkGivenPlaylistTracksPerFile(givenPlaylistTracksPerFile: String) {
    val isInt = givenPlaylistTracksPerFile.all { it.isDigit() }
    if (!isInt) {
        throw Exception("Valor inválido para 'playlistTracksPerFile' - $givenPlaylistTracksPerFile")
    }
}

private fun String.getArgValueOnly(): String {
    return this.substring(this.indexOf('=') + 1)
}
package client

import app.apps.deezer.DeezerApp
import app.apps.deezer.DeezerImportScript
import exporter.FileExporter
import exporter.FileExporter.removeWindowsInvalidCharacters
import model.Playlist
import app.apps.spotify.SpotifyExportScript
import model.Utils.Companion.waitForAppFinish
import model.Utils.Companion.waitForCurrentActionDefinition
import server.Server
import ui.UI
import java.io.File

val playlistToImport = mutableListOf<String>()

var saveWithName: String = "Playlists"
var saveAs: String = "txt"
var exportFilePath: String = "./"
var isSeparateFilesByPlaylist = false
var playlistTracksPerFile: Int? = null

@Volatile var currentAction: Action? = null
enum class Action {
    DEEZER_TO_SPOTIFY,
    DEEZER_TO_FILE
}

enum class Apps {
    DEEZER,
    SPOTIFY
}

@Suppress("ControlFlowWithEmptyBody")
fun main(args: Array<String>) {
    try {
        treatArgs(args)

        if (currentAction == null) {
            UI.createActionScreen()
        }
        waitForCurrentActionDefinition()

        DeezerImportScript.runImport()
        waitForAppFinish(DeezerApp())

        if (currentAction == Action.DEEZER_TO_SPOTIFY) {
            val importedPlaylists = Playlist.getPlaylistsFromSpecificApp(Apps.DEEZER)
            SpotifyExportScript.runExport(importedPlaylists)
        }
    } catch (exception: Exception) {
        throw exception.also { UI.createErrorScreen(it.message!!) }
    } finally {
        Server.shutDown()
    }
}

private fun treatArgs(args: Array<String>) {
    args.forEach {
        when {
            it.startsWith("saveWithName", true) -> saveWithName = it.getArgValueOnly().ifBlank { saveWithName }.removeWindowsInvalidCharacters()
            it.startsWith("saveAs", true) -> saveAs = it.getArgValueOnly().lowercase().ifBlank { saveAs }.also { extension -> checkSupportedExtension(extension) }

            it.startsWith("currentAction") -> currentAction = it.getArgValueOnly().let { action ->
                when {
                    action.equals("deezerToSpotify", true) -> Action.DEEZER_TO_SPOTIFY
                    action.equals("deezerToFile", true) -> Action.DEEZER_TO_FILE
                    action.isBlank() -> null
                    else -> throw Exception("Valor inválido para 'currentAction': $action")
                }
            }

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
    val supportedExtensions = FileExporter.SupportedExtensions.values().map { it.name }
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
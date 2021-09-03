package client

import app.apps.deezer.DeezerExport
import app.apps.deezer.DeezerImport
import app.apps.file.FileApp
import app.apps.spotify.SpotifyExport
import app.apps.spotify.SpotifyImport
import client.Utils.Companion.removeWindowsInvalidCharacters
import app.apps.file.FileExport
import app.apps.file.FileImport
import model.Playlist
import exporter.Exporter
import importer.Importer
import client.Utils.Companion.waitForCurrentActionDefinition
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

enum class Apps {
    DEEZER,
    SPOTIFY,
    FILE
}

enum class Action(val importAndExportFunction: Pair<Importer, Exporter>) {
    DEEZER_TO_SPOTIFY(DeezerImport to SpotifyExport),
    DEEZER_TO_FILE(DeezerImport to FileExport),
    SPOTIFY_TO_DEEZER(SpotifyImport to DeezerExport),
    SPOTIFY_TO_FILE(SpotifyImport to FileExport),
    FILE_TO_DEEZER(FileImport to DeezerExport),
    FILE_TO_SPOTIFY(FileImport to SpotifyExport)
}

@Suppress("ControlFlowWithEmptyBody")
fun main(args: Array<String>) {
    try {
        treatArgs(args)

        if (currentAction == null) {
            UI.createActionScreen()
        }
        waitForCurrentActionDefinition()

        val importer = currentAction!!.importAndExportFunction.first
        val exporter = currentAction!!.importAndExportFunction.second

        importer.runImport()

        val importMethodName = importer.javaClass.name.let { it.substring(it.lastIndexOf('.') + 1) }
        val appThatImported = Apps.values().first { importMethodName.startsWith(it.name, ignoreCase = true) }
        val importedPlaylists = Playlist.getPlaylistsFromSpecificApp(appThatImported)

        exporter.runExport(importedPlaylists)
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
    val supportedExtensions = FileApp.Companion.SupportedExtensions.values().map { it.name }
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
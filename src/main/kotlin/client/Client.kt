package client

import app.apps.deezer.DeezerExport
import exporter.FileExporter
import exporter.FileExporter.removeWindowsInvalidCharacters
import model.Playlist
import exporter.Exporter
import importer.Importer
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

@Volatile var currentAction: Pair<Importer, Exporter>? = null

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

        if (currentAction?.second == DeezerExport) throw Exception("Ainda não implementado")

        currentAction!!.first.runImport()

        val importMethodName = currentAction!!.first.javaClass.name.let { it.substring(it.lastIndexOf('.') + 1) }
        val appThatImported = Apps.values().first { importMethodName.startsWith(it.name, ignoreCase = true) }
        val importedPlaylists = Playlist.getPlaylistsFromSpecificApp(appThatImported)

        currentAction!!.second.runExport(importedPlaylists)
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
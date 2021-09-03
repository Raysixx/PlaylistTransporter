package app.apps.file

import client.exportFilePath
import client.isSeparateFilesByPlaylist
import client.playlistTracksPerFile
import client.saveAs
import client.saveWithName
import exporter.Exporter
import model.Artist
import model.Playlist
import model.Track
import ui.UI
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import kotlin.math.ceil
import app.apps.file.FileApp.Companion.SupportedExtensions

@Suppress("MoveLambdaOutsideParentheses")
object FileExport: FileApp(), Exporter {
    private val playlistInFile = mutableMapOf<Playlist, MutableSet<Int>>()
    private val filesAlreadyWithHeader = mutableSetOf<String>()
    private val createdFiles = mutableSetOf<File>()

    override fun runExport(externalPlaylists: List<Playlist>) {
        eraseOldFiles(externalPlaylists)

        export(externalPlaylists)

        createdFiles.onEach { file ->
            PrintWriter(FileWriter(file, true)).use {
                it.print(waterMark)
            }
        }

        UI.createDoneExportToFileScreen()
    }

    private fun export(playlists: List<Playlist>, givenFile: File? = null, getTracksStartingFrom: Int = 0) {
        playlists.forEach { playlist ->
            if (isSeparateFilesByPlaylist) {
                saveWithName = playlist.title.removeWindowsInvalidCharacters()
            }

            val file = (givenFile ?: File("$exportFilePath$saveWithName.$saveAs").apply { if (!this.exists()) this.createNewFile() })
                .also { createdFiles.add(it) }

            PrintWriter(FileWriter(file, true)).use { out ->
                if (saveAs == SupportedExtensions.csv.name && file.name !in filesAlreadyWithHeader) {
                    out.println(SupportedExtensions.csv.header).also { filesAlreadyWithHeader.add(file.name) }
                }

                out.println(getExportPlaylistMessage(playlist, getTracksStartingFrom))

                val tracksQuantity = playlist.tracks.size

                val scopeLimit = if (playlistTracksPerFile != null) {
                    val nextScopeInitial = getTracksStartingFrom + (playlistTracksPerFile!! + 1)
                    if (nextScopeInitial < tracksQuantity) {
                        nextScopeInitial
                    } else {
                        tracksQuantity
                    }
                } else {
                    tracksQuantity
                }

                playlist.tracks.subList(getTracksStartingFrom, scopeLimit).forEachIndexed { index, track ->
                    if (playlistTracksPerFile == null || index != playlistTracksPerFile!!) {
                        out.println(getExportTrackMessage(playlist, track, getTracksStartingFrom + index + 1))
                    } else {
                        val nextFile = getNextFile(playlist)
                        export(listOf(playlist), nextFile, getTracksStartingFrom + playlistTracksPerFile!!)
                    }
                }

                out.println("\n\n")
            }
        }
    }

    private fun getExportPlaylistMessage(playlist: Playlist, getTracksStartingFrom: Int): String {
        val currentTracksScope by lazy { getCurrentTracksCount(getTracksStartingFrom, playlist.tracks.size) }

        return when (saveAs) {
            SupportedExtensions.txt.name -> "Playlist ${playlist.title}: ($currentTracksScope) de ${playlist.tracks.size}"
            SupportedExtensions.csv.name -> ""
            else -> ""
        }
    }

    private fun getExportTrackMessage(playlist: Playlist, track: Track, index: Int): String {
        val isAvailable = track.isAvailable.let { if (it) availableFlag else notAvailableFlag }

        return when (saveAs) {
            SupportedExtensions.txt.name -> "    $index - ${Artist.getArtistsNames(track.artists)} -- ${track.name} <$isAvailable>"
            SupportedExtensions.csv.name -> {
                playlist.title +
                separator +
                track.name +
                separator +
                Artist.getArtistsNames(track.artists, useSpace = false) +
                separator +
                track.albumName +
                separator +
                separator +
                isAvailable +
                separator
            }
            else -> ""
        }
    }

    private fun eraseOldFiles(playlists: List<Playlist>) {
        File(exportFilePath).listFiles()
            ?.filter { file ->
                if (isSeparateFilesByPlaylist) {
                    playlists.map { it.title.removeWindowsInvalidCharacters() }.any { playlistName -> file.name.startsWith(playlistName) }
                } else {
                    file.name.startsWith(saveWithName)
                }
                    &&
                file.extension == saveAs
            }?.forEach { it.delete() }
    }

    private fun getNextFile(playlist: Playlist): File {
        val filesNumberWhereThisPlaylistIs = playlistInFile[playlist] ?: emptySet()

        var currentFileNumber = 2
        var file = File("$exportFilePath$saveWithName$currentFileNumber.$saveAs")

        while (file.exists() && currentFileNumber in filesNumberWhereThisPlaylistIs) {
            currentFileNumber++
            file = File("$exportFilePath$saveWithName$currentFileNumber.$saveAs")
        }

        playlistInFile.getOrPut(playlist, { mutableSetOf() }).add(currentFileNumber)

        if (!file.exists()) {
            file.createNewFile()
        }

        return file
    }

    private fun getCurrentTracksCount(tracksStartingFrom: Int, tracksQuantity: Int): String {
        if (playlistTracksPerFile == null) {
            return "${tracksStartingFrom + 1} - $tracksQuantity"
        }

        val currentScope = ceil((tracksStartingFrom + 1).toDouble() / playlistTracksPerFile!!.toDouble()).toInt()

        return if (playlistTracksPerFile!! * currentScope < tracksQuantity) {
            "${tracksStartingFrom + 1} - ${playlistTracksPerFile!! * currentScope}"
        } else {
            "${tracksStartingFrom + 1} - $tracksQuantity"
        }
    }

    override fun addPlaylists(externalPlaylists: List<Playlist>, userId: String) {}
    override fun addTracks(playlist: Playlist, externalTracks: List<Track>) {}
    override fun getTracks(playlist: Playlist, externalTracks: List<Track>) = emptyList<Track>()
    override fun searchTrack(currentTrack: Track): HashMap<String, *>? = null
    override fun searchTrackByNameArtistAndAlbum(currentTrack: Track, rawTrackName: String, rawTrackAlbumName: String): HashMap<String, *>? = null
    override fun searchTrackByNameAndAlbum(currentTrack: Track, rawTrackName: String, rawTrackAlbumName: String): HashMap<String, *>? = null
    override fun searchTrackByNameAndArtist(currentTrack: Track, trackRawName: String): HashMap<String, *>? = null
    override fun searchTrackByName(currentTrack: Track, trackRawName: String): HashMap<String, *>? = null
}
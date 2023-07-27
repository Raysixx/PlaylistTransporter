package app.apps.file

import app.AppInterface
import client.Apps
import app.Importer
import model.Artist
import model.Playlist
import model.Track
import java.io.File
import app.apps.file.FileApp.Companion.SupportedExtensions
import client.playlistToImport
import model.JsonFoundPlaylists
import model.JsonFoundTracks
import ui.UI
import kotlin.system.exitProcess

object FileImport: FileApp(), Importer {
    override fun runImport() {
        isRunning = true
        operation = AppInterface.Operation.IMPORT

        try {
            val files = UI.createSelectFileScreen().ifEmpty { exitProcess(0) }
            UI.updateOperation(IMPORTING)
            UI.createOperationScreen()

            fillPlaylists(files)

            Playlist.createdPlaylists.filter { it.app.name == Apps.FILE.name }.ifEmpty {
                throw Exception("Nenhuma playlist encontrada.")
            }
        } finally {
            isRunning = false
            operation = null
        }
    }

    private fun fillPlaylists(files: List<File>) {
        files.forEach { file ->
            file.bufferedReader().use { reader ->
                val allLines = reader.readLines().toTypedArray().toList()

                if (allLines.last() != waterMark) throw Exception("Arquivo não criado pelo ${UI.appName}")

                when (file.extension) {
                    SupportedExtensions.txt.name -> importFromTXT(allLines)
                    SupportedExtensions.csv.name -> importFromCSV(allLines)
                }
            }
        }
    }

    private fun importFromTXT(allLines: List<String>) {
        var currentPlaylist: Playlist? = null

        allLines.forEach { line ->
            if (line.isEmpty() || line.contains(UI.appName)) return@forEach

            if (line.contains("Playlist", true)) {
                val playlistName = line.substring(0, line.indexOf(": (")).replace("Playlist ", "")

                if (playlistToImport.isNotEmpty() && playlistName.uppercase() !in playlistToImport) {
                    currentPlaylist = null
                    return@forEach
                }

                val playlistId = (Playlist.createdPlaylists.size + 1).toString()

                currentPlaylist = Playlist.createdPlaylists.firstOrNull { it.title == playlistName && it.app.name == Apps.FILE.name }
                    ?: Playlist(playlistName, playlistId, Apps.FILE)

                return@forEach
            }

            if (currentPlaylist == null) return@forEach

            val artists = line.substring(line.indexOf('-') + 2, line.indexOf("--") - 1).split(",").map { artistName ->
                val newArtistId by lazy { (Artist.createdArtists.size + 1).toString() }

                Artist.createdArtists.firstOrNull { it.name == artistName }
                    ?: Artist(artistName, newArtistId, Apps.FILE)
            }

            val trackName = run {
                val availableFlagIndex = line.indexOf("<D>").let { if (it != -1) it else line.indexOf("<ND>") }

                line.substring(line.indexOf("--") + 3, availableFlagIndex - 1)
            }

            UI.updateMessage("$IMPORTING_PLAYLIST ${currentPlaylist!!.title} <br>" +
                    "${Artist.getArtistsNames(artists)} -- $trackName")

            val albumName by lazy { "" }
            val isAvailable by lazy { line.contains("<D>") }
            val newTrackId by lazy { (Track.createdTracks.size + 1).toString() }

            (Track.createdTracks.firstOrNull { it.name == trackName && it.artists == artists && it.app.name == Apps.FILE.name }
                ?: Track(artists, trackName, albumName, newTrackId, isAvailable, Apps.FILE))
                .let { currentPlaylist!!.tracks.add(it) }
        }
    }

    private fun importFromCSV(allLines: List<String>) {
        val headerMap = SupportedExtensions.csv.header.split(separator).mapIndexed { index, element ->
            element to index
        }.toMap()

        val playlistIndex = headerMap[playlist]!!
        val trackIndex = headerMap[track]!!
        val artistIndex = headerMap[artist]!!
        val albumIndex = headerMap[album]!!
        val isrcIndex = headerMap[isrc]!!
        val availableIndex = headerMap[available]!!

        allLines.forEachIndexed { index, line ->
            if (index == 0 || line.replace(";", "").isEmpty() || line.contains(UI.appName)) return@forEachIndexed

            val lineElements = line.split(separator)

            val playlist = lineElements[playlistIndex].let { playlistName ->
                if (playlistToImport.isNotEmpty() && playlistName.uppercase() !in playlistToImport) {
                    return@forEachIndexed
                }

                val playlistId by lazy { (Playlist.createdPlaylists.size + 1).toString() }

                Playlist.createdPlaylists.firstOrNull { it.title == playlistName && it.app.name == Apps.FILE.name }
                    ?: Playlist(playlistName, playlistId, Apps.FILE)
            }

            val trackName = lineElements[trackIndex]
            val artists = lineElements[artistIndex].split(Artist.artistsSeparator).mapNotNull { artistName ->
                if (artistName.isEmpty()) return@mapNotNull null

                val newArtistId by lazy { (Artist.createdArtists.size + 1).toString() }

                Artist.createdArtists.firstOrNull { it.name == artistName }
                    ?: Artist(artistName, newArtistId, Apps.FILE)
            }

            UI.updateMessage("$IMPORTING_PLAYLIST ${playlist.title} <br>" +
                    "${Artist.getArtistsNames(artists)} -- $trackName")

            val albumName = lineElements[albumIndex]
            val newTrackId by lazy { (Track.createdTracks.size + 1).toString() }
            val isAvailable by lazy { lineElements[availableIndex] == availableFlag }

            (Track.createdTracks.firstOrNull { it.name == trackName && it.artists == artists && it.albumName == albumName && it.app.name == Apps.FILE.name }
            ?: Track(artists, trackName, albumName, newTrackId, isAvailable, Apps.FILE))
                .also { playlist.tracks.add(it) }
        }
    }

    override fun fillPlaylists(serverFoundPlaylists: JsonFoundPlaylists) {}
    override fun getTracks(serverPlaylistTitle: String, serverPlaylistServerTracks: JsonFoundTracks): List<Track> = emptyList()
}
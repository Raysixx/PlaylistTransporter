package exporter

import client.exportFilePath
import client.isSeparateFilesByPlaylist
import client.playlistTracksPerFile
import client.saveAs
import client.saveWithName
import model.Playlist
import model.Track
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import kotlin.math.ceil

@Suppress("EnumEntryName")
object FileExporter {
    enum class SupportedExtensions {
        txt,
        csv
    }

    private val playlistInFile = mutableMapOf<Playlist, MutableSet<Int>>()
    private val filesAlreadyWithHeader = mutableSetOf<String>()

    fun exportPlaylistsToFile(playlists: List<Playlist>) {
        eraseOldFiles(playlists)

        export(playlists)
    }

    private fun export(playlists: List<Playlist>, givenFile: File? = null, getTracksStartingFrom: Int = 0) {
        playlists.forEach { playlist ->
            if (isSeparateFilesByPlaylist) {
                saveWithName = playlist.title.removeWindowsInvalidCharacters()
            }

            val file = givenFile ?: File("$exportFilePath$saveWithName.$saveAs").apply { if (!this.exists()) this.createNewFile() }

            PrintWriter(FileWriter(file, true)).use { out ->
                if (saveAs == SupportedExtensions.csv.name && file.name !in filesAlreadyWithHeader) {
                    out.println("Title;Artist;Album;isrc").also { filesAlreadyWithHeader.add(file.name) }
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
                    if (playlistTracksPerFile != null && index == playlistTracksPerFile!!) {
                        val nextFile = getNextFile(playlist)
                        export(listOf(playlist), nextFile, getTracksStartingFrom + playlistTracksPerFile!!)
                    } else {
                        out.println(getExportTrackMessage(track))
                    }
                }

                if (!isSeparateFilesByPlaylist) out.println()
            }
        }
    }

    private fun getExportPlaylistMessage(playlist: Playlist, getTracksStartingFrom: Int): String {
        val currentTracksScope by lazy { getCurrentTracksCount(getTracksStartingFrom, playlist.tracks.size) }

        return when (saveAs) {
            SupportedExtensions.txt.name -> "Playlist ${playlist.title}: ($currentTracksScope) de ${playlist.tracks.size}"
            SupportedExtensions.csv.name -> "${playlist.title};;;;"
            else -> ""
        }
    }

    private fun getExportTrackMessage(track: Track): String {
        return when (saveAs) {
            SupportedExtensions.txt.name -> "    ${track.artist} - ${track.name}"
            SupportedExtensions.csv.name -> "${track.name};${track.artist};;;"
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
            }
            ?.forEach { it.delete() }
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

    fun String.removeWindowsInvalidCharacters(): String {
        return this.replace("[\\\\/:*?\"<>|]".toRegex(), "")
    }
}
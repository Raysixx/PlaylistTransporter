package importer

import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import kotlin.math.ceil

object Exporter {
    private val playlistInFile = mutableMapOf<Playlist, MutableList<Int>>()

    fun exportPlaylistsToFile(playlists: List<Playlist>) {
        eraseOldFiles()

        val file = File("${exportFilePath}$saveWithName.$saveAs")
        file.createNewFile()

        export(file, playlists)
    }

    private fun export(file: File, playlists: List<Playlist>, getTracksStartingFrom: Int = 0) {
        PrintWriter(FileWriter(file, true)).use { out ->
            playlists.forEach { playlist ->
                if (saveAs == SupportedExtensions.csv.name) out.println("Title;Artist;Album;isrc")

                out.println(getExportPlaylistMessage(playlist, getTracksStartingFrom))

                var isThisScopeFinished = false

                playlist.tracks.drop(getTracksStartingFrom).forEachIndexed { index, track ->
                    if (playlistTracksPerFile != null && index > playlistTracksPerFile!!) {
                        if (isThisScopeFinished) return@forEachIndexed

                        val nextFile = getNextFile(playlist)
                        export(nextFile, listOf(playlist), getTracksStartingFrom.let { if (it == 0) it + 1 else it } + playlistTracksPerFile!!)

                        isThisScopeFinished = true
                    } else {
                        out.println(getExportTrackMessage(track))
                    }
                }

                if (saveAs == SupportedExtensions.txt.name) out.println()
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

    private fun eraseOldFiles() {
        File(exportFilePath).listFiles()
            ?.filter { it.name.startsWith(saveWithName) }
            ?.filter { it.extension == saveAs }
            ?.forEach { it.delete() }
    }

    private fun getNextFile(playlist: Playlist): File {
        val filesNumberWhereThisPlaylistIs = playlistInFile[playlist] ?: emptyList()

        var currentFileNumber = 2
        var file = File("${exportFilePath}$saveWithName$currentFileNumber.$saveAs")

        while (file.exists() && currentFileNumber in filesNumberWhereThisPlaylistIs) {
            currentFileNumber++
            file = File("${exportFilePath}$saveWithName$currentFileNumber.$saveAs")
        }

        playlistInFile.getOrPut(playlist, { mutableListOf(currentFileNumber) }).apply { if (this.isEmpty()) this.add(currentFileNumber) }

        if (!file.exists()) {
            file.createNewFile()
        }

        return file
    }

    private fun getCurrentTracksCount(tracksStartingFrom: Int, tracksQuantity: Int): String {
        if (playlistTracksPerFile == null) {
            return "${tracksStartingFrom.let { if (it == 0) 1 else it }} - $tracksQuantity"
        }

        val currentScope = ceil(tracksStartingFrom.let { if (it == 0) 1.0 else it }.toDouble() / playlistTracksPerFile!!.toDouble()).toInt()

        return if (playlistTracksPerFile!! * currentScope < tracksQuantity) {
            "${tracksStartingFrom.let { if (it == 0) 1 else it }} - ${playlistTracksPerFile!! * currentScope}"
        } else {
            "${tracksStartingFrom.let { if (it == 0) 1 else it }} - $tracksQuantity"
        }
    }
}
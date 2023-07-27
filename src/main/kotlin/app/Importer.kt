package app

import model.JsonFoundPlaylists
import model.JsonFoundTracks
import model.Track

interface Importer {
    fun runImport()
    fun fillPlaylists(serverFoundPlaylists: JsonFoundPlaylists)
    fun getTracks(serverPlaylistTitle: String, serverPlaylistServerTracks: JsonFoundTracks): List<Track>
}
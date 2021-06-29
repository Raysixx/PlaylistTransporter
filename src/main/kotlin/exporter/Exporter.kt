package exporter

import model.Playlist
import model.Track

interface Exporter {
    fun runExport(externalPlaylists: List<Playlist>)
    fun addPlaylists(externalPlaylists: List<Playlist>, userId: String)
    fun addTracks(playlist: Playlist, externalTracks: List<Track>)
    fun getTracks(playlist: Playlist, externalTracks: List<Track>): List<Track>
}
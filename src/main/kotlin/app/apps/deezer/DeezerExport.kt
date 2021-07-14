package app.apps.deezer

import exporter.Exporter
import model.Playlist
import model.Track

object DeezerExport: DeezerApp(), Exporter {
    override fun runExport(externalPlaylists: List<Playlist>) {
        TODO("Not yet implemented")
    }

    override fun addPlaylists(externalPlaylists: List<Playlist>, userId: String) {
        TODO("Not yet implemented")
    }

    override fun addTracks(playlist: Playlist, externalTracks: List<Track>) {
        TODO("Not yet implemented")
    }

    override fun getTracks(playlist: Playlist, externalTracks: List<Track>): List<Track> {
        TODO("Not yet implemented")
    }
}
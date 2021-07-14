package exporter

import model.Playlist
import model.Track

interface Exporter {
    fun runExport(externalPlaylists: List<Playlist>)
    fun addPlaylists(externalPlaylists: List<Playlist>, userId: String)
    fun addTracks(playlist: Playlist, externalTracks: List<Track>)
    fun getTracks(playlist: Playlist, externalTracks: List<Track>): List<Track>
    fun searchTrack(currentTrack: Track): HashMap<String, *>?
    fun searchTrackByNameArtistAndAlbum(currentTrack: Track, rawTrackName: String, rawTrackAlbumName: String): HashMap<String, *>?
    fun searchTrackByNameAndAlbum(currentTrack: Track, rawTrackName: String, rawTrackAlbumName: String): HashMap<String, *>?
    fun searchTrackByNameAndArtist(currentTrack: Track, trackRawName: String): HashMap<String, *>?
    fun searchTrackByName(currentTrack: Track, trackRawName: String): HashMap<String, *>?
}
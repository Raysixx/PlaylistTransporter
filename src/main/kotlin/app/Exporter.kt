package app

import model.JsonTrack
import model.Playlist
import model.Track

interface Exporter {
    fun runExport(externalPlaylists: List<Playlist>)
    fun addPlaylists(externalPlaylists: List<Playlist>, userId: String)
    fun addTracks(createdPlaylist: Playlist, externalTracks: List<Track>)
    fun getTracks(createdPlaylist: Playlist, externalTracks: List<Track>): List<Track>
    fun searchTrack(externalTrack: Track): JsonTrack?
    fun searchTrackByNameArtistAndAlbum(externalTrack: Track, encodedTrackName: String, encodedServerTrackAlbumName: String): JsonTrack?
    fun searchTrackByNameAndAlbum(externalTrack: Track, encodedTrackName: String, encodedServerTrackAlbumName: String): JsonTrack?
    fun searchTrackByNameAndArtist(externalTrack: Track, encodedTrackName: String): JsonTrack?
    fun searchTrackByName(externalTrack: Track, encodedTrackName: String): JsonTrack?
}
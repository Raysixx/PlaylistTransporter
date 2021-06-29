package importer

import model.Playlist
import model.Track

interface Importer {
    fun runImport()
    fun getPlaylists(rawPlaylistsMap: HashMap<String, *>): List<Playlist>
    fun getTracks(playlistTitle: String, playlistRawTracks: HashMap<String, *>): List<Track>
}
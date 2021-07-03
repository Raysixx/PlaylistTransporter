package importer

import model.Track

interface Importer {
    fun runImport()
    fun fillPlaylists(rawPlaylistsMap: HashMap<String, *>)
    fun getTracks(playlistTitle: String, playlistRawTracks: HashMap<String, *>): List<Track>
}
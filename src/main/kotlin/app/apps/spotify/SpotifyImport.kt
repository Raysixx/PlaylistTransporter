package app.apps.spotify

import app.AppInterface
import client.Apps
import client.playlistToImport
import importer.Importer
import model.Artist
import model.Playlist
import model.Track
import ui.UI

@Suppress("ComplexRedundantLet", "UNCHECKED_CAST")
object SpotifyImport: SpotifyApp(), Importer {
    override fun runImport() {
        isRunning = true
        operation = AppInterface.Operation.IMPORT

        try {
            generateToken()
            fillCurrentCountry(currentToken!!)

            spotifyGetUserPlaylistsURL(currentToken).getURLResponse()
                .let { fillPlaylists(it) }

            Playlist.createdPlaylists.filter { it.app.name == Apps.SPOTIFY.name }.ifEmpty {
                throw Exception("Nenhuma playlist encontrada.")
            }
        } finally {
            isRunning = false
            operation = null
        }
    }

    override fun fillPlaylists(rawPlaylistsMap: HashMap<String, *>) {
        var current50PlaylistsObject: HashMap<String, *>? = rawPlaylistsMap
        var current50Playlists: List<HashMap<String, *>>? = current50PlaylistsObject?.get(ITEMS) as List<HashMap<String, *>>
        while (current50Playlists != null) {
            current50Playlists.forEach { rawPlaylist ->
                val title = rawPlaylist[NAME].toString()
                val id = rawPlaylist[ID].toString()

                if (playlistToImport.isNotEmpty() && title.uppercase() !in playlistToImport) {
                    return@forEach
                }

                UI.updateMessage("$IMPORTING_PLAYLIST $title")

                val playlistRawTracksURL = (rawPlaylist[TRACKS] as HashMap<String, *>).let { it[HREF] as String }
                val playlistRawTracks = URLPlusToken(playlistRawTracksURL, currentToken).getURLResponse()

                Playlist(title, id, Apps.SPOTIFY)
                    .apply {
                        getTracks(this.title, playlistRawTracks).let { trackList ->
                            this.tracks.addAll(trackList)
                        }
                    }
            }

            current50PlaylistsObject = (current50PlaylistsObject?.get(NEXT) as String?)?.let { URLPlusToken(it, currentToken, isFirstParameterOfUrl = false) }?.getURLResponse()
            current50Playlists = current50PlaylistsObject?.get(ITEMS) as List<HashMap<String, *>>?
        }
    }

    override fun getTracks(playlistTitle: String, playlistRawTracks: HashMap<String, *>): List<Track> {
        val allTracks = mutableListOf<Track>()

        var current100Tracks: HashMap<String, *>? = playlistRawTracks
        while (current100Tracks != null) {
            val rawTracks = (current100Tracks[ITEMS] as List<HashMap<String, *>>).map { it[TRACK] as HashMap<String, *> }

            getCurrentTracks(rawTracks, playlistTitle).let {
                allTracks.addAll(it)
            }

            current100Tracks = (current100Tracks[NEXT] as String?)?.let { URLPlusToken(it, currentToken, isFirstParameterOfUrl = false) }?.getURLResponse()
        }

        return allTracks
    }

    private fun getCurrentTracks(rawTracks: List<HashMap<String, *>>, playlistTitle: String): List<Track> {
        return rawTracks.map { rawTrack ->
            val artists = getArtist(rawTrack[ARTISTS] as List<HashMap<String, *>>)
            val trackName = rawTrack[NAME].toString()
            val albumName = (rawTrack[ALBUM] as HashMap<String, *>).let { it[NAME].toString() }

            UI.updateMessage("$IMPORTING_PLAYLIST $playlistTitle <br>" +
                    "$SEARCHING_ON_SERVER $FROM_SPOTIFY: <br>" +
                    "${Artist.getArtistsNames(artists)} -- $trackName")

            val trackId = rawTrack[ID].toString() //TODO: Tracks atualmente indisponíveis
            val isAvailable = currentCountry in rawTrack[AVAILABLE_MARKETS] as List<String>

            Track.createdTracks.filter { it.app.name == Apps.SPOTIFY.name }.firstOrNull { it.id == trackId }
                ?: Track(artists, trackName, albumName, trackId, isAvailable, Apps.SPOTIFY)
        }
    }

    private fun getArtist(artists: List<HashMap<String, *>>): List<Artist> {
        return artists.map {
            val artistName = it[NAME].toString()
            val artistId = it[ID].toString()

            Artist(artistName, artistId, Apps.SPOTIFY)
        }
    }
}
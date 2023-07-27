package app.apps.spotify

import app.AppInterface
import client.Apps
import client.playlistToImport
import app.Importer
import model.Artist
import model.JsonFoundPlaylists
import model.JsonFoundTracks
import model.Playlist
import model.Track
import ui.UI

@Suppress("ComplexRedundantLet")
object SpotifyImport: SpotifyApp(), Importer {
    override fun runImport() {
        isRunning = true
        operation = AppInterface.Operation.IMPORT

        try {
            generateToken()
            fillUser(currentToken!!)

            spotifyGetUserPlaylistsURL(currentToken).getURLResponse<SpotifyFoundPlaylists>()
                .let { fillPlaylists(it) }

            Playlist.createdPlaylists.filter { it.app.name == Apps.SPOTIFY.name }.ifEmpty {
                throw Exception("Nenhuma playlist encontrada.")
            }
        } finally {
            isRunning = false
            operation = null
        }
    }

    override fun fillPlaylists(serverFoundPlaylists: JsonFoundPlaylists) {
        var serverCurrent50PlaylistsObject: SpotifyFoundPlaylists? = serverFoundPlaylists as SpotifyFoundPlaylists
        var serverCurrent50Playlists: List<SpotifyPlaylist>? = serverCurrent50PlaylistsObject!!.items

        while (serverCurrent50Playlists != null) {
            serverCurrent50Playlists.forEach { serverPlaylist ->
                val serverPlaylistTitle = serverPlaylist.title

                if (playlistToImport.isNotEmpty() && serverPlaylistTitle.uppercase() !in playlistToImport) {
                    return@forEach
                }

                UI.updateMessage("$IMPORTING_PLAYLIST $serverPlaylistTitle")

                val serverPlaylistTracksURL = serverPlaylist.tracks.href
                val serverPlaylistFoundTracks = URLPlusToken(serverPlaylistTracksURL, currentToken).getURLResponse<SpotifyFoundTracksWithTrackItem>()

                Playlist(serverPlaylistTitle, serverPlaylist.id, Apps.SPOTIFY)
                    .apply {
                        getTracks(this.title, serverPlaylistFoundTracks).let { trackList ->
                            this.tracks.addAll(trackList)
                        }
                    }
            }

            serverCurrent50PlaylistsObject = serverCurrent50PlaylistsObject?.next?.let {
                URLPlusToken(it, currentToken, isFirstParameterOfUrl = false).getURLResponse<SpotifyFoundPlaylists>()
            }

            serverCurrent50Playlists = serverCurrent50PlaylistsObject?.items
        }
    }

    override fun getTracks(serverPlaylistTitle: String, serverPlaylistServerTracks: JsonFoundTracks): List<Track> {
        val allServerTracks = mutableListOf<Track>()

        var serverCurrent100Tracks: SpotifyFoundTracksWithTrackItem? = serverPlaylistServerTracks as SpotifyFoundTracksWithTrackItem
        while (serverCurrent100Tracks != null) {
            val serverFoundTracks = serverCurrent100Tracks.items.map { it.track }

            getCurrentTracks(serverFoundTracks, serverPlaylistTitle).let {
                allServerTracks.addAll(it)
            }

            serverCurrent100Tracks = serverCurrent100Tracks.next?.let {
                URLPlusToken(it, currentToken, isFirstParameterOfUrl = false).getURLResponse<SpotifyFoundTracksWithTrackItem>()
            }
        }

        return allServerTracks
    }

    private fun getCurrentTracks(serverFoundTracks: List<SpotifyTrack>, serverPlaylistTitle: String): List<Track> {
        return serverFoundTracks.map { serverFoundTrack ->
            val artists = serverFoundTrack.artists.map {
                Artist(it.name, it.id, Apps.SPOTIFY)
            }

            val serverFoundTrackTitle = serverFoundTrack.title
            val serverFoundTrackAlbumTitle = serverFoundTrack.album.title

            UI.updateMessage("$IMPORTING_PLAYLIST $serverPlaylistTitle <br>" +
                    "$SEARCHING_ON_SERVER $FROM_SPOTIFY: <br>" +
                    "${Artist.getArtistsNames(artists)} -- $serverFoundTrackTitle")

            val serverFoundTrackId = serverFoundTrack.id //TODO: Tracks atualmente indisponíveis
            val isAvailable = user!!.country in serverFoundTrack.availableCountries

            Track.createdTracks.filter { it.app.name == Apps.SPOTIFY.name }.firstOrNull { it.id == serverFoundTrackId }
                ?: Track(artists, serverFoundTrackTitle, serverFoundTrackAlbumTitle, serverFoundTrackId, isAvailable, Apps.SPOTIFY)
        }
    }
}
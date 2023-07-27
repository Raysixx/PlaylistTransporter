package app.apps.deezer

import app.AppInterface
import client.Apps
import client.playlistToImport
import ui.UI
import app.Importer
import model.Artist
import model.JsonFoundPlaylists
import model.JsonFoundTracks
import model.Playlist
import model.Track

@Suppress("ComplexRedundantLet", "MoveLambdaOutsideParentheses")
object DeezerImport: DeezerApp(), Importer {
    override fun runImport() {
        isRunning = true
        operation = AppInterface.Operation.IMPORT

        try {
            generateToken()
            fillUser(currentToken!!)

            deezerGetUserPlaylistsURL(currentToken).getURLResponse<DeezerFoundPlaylists>()
                .let { fillPlaylists(it) }

            Playlist.createdPlaylists.filter { it.app.name == Apps.DEEZER.name }.ifEmpty {
                throw Exception("Nenhuma playlist encontrada.")
            }
        } finally {
            isRunning = false
            operation = null
        }
    }

    override fun fillPlaylists(serverFoundPlaylists: JsonFoundPlaylists) {
        (serverFoundPlaylists as DeezerFoundPlaylists).data.forEach { serverPlaylist ->
            val serverPlaylistTitle = serverPlaylist.title
            if (playlistToImport.isNotEmpty() && serverPlaylistTitle.uppercase() !in playlistToImport) {
                return@forEach
            }

            UI.updateMessage("$IMPORTING_PLAYLIST $serverPlaylistTitle")

            val serverPlaylistFoundTracks = URLPlusToken(serverPlaylist.tracklistUrl, currentToken).getURLResponse<DeezerFoundTracks>()

            Playlist(serverPlaylistTitle, serverPlaylist.id, Apps.DEEZER)
                .apply {
                    getTracks(serverPlaylistTitle, serverPlaylistFoundTracks).let { trackList ->
                        this.tracks.addAll(trackList)
                    }
                }
        }
    }

    override fun getTracks(serverPlaylistTitle: String, serverPlaylistServerTracks: JsonFoundTracks): List<Track> {
        val allTracks = mutableListOf<Track>()

        var current25ServerTracks: DeezerFoundTracks? = serverPlaylistServerTracks as DeezerFoundTracks
        while (current25ServerTracks != null) {
            getCurrentTracks(current25ServerTracks.data, serverPlaylistTitle).let {
                allTracks.addAll(it)
            }

            current25ServerTracks = current25ServerTracks.next?.getURLResponse()
        }

        return allTracks
    }

    private fun getCurrentTracks(serverTracks: List<DeezerTrack>, serverPlaylistTitle: String): List<Track> {
        return serverTracks.map { serverTrack ->
            val artist = serverTrack.artist.let {
                Artist(it.name, it.id, Apps.DEEZER)
            }

            val serverTrackName = serverTrack.title
            val serverTrackAlbumName = serverTrack.album.title

            UI.updateMessage("$IMPORTING_PLAYLIST $serverPlaylistTitle <br>" +
                    "$SEARCHING_ON_SERVER $FROM_DEEZER: <br>" +
                    "${artist.name} -- $serverTrackName")

            var isAvailable = serverTrack.readable
            val currentTracksNotAvailableIds = (Track.tracksNotAvailable[Apps.DEEZER] ?: emptyList()).map { it.id }

            val currentServerTrackId = serverTrack.id
            val serverTrackId = if (isAvailable || currentServerTrackId in currentTracksNotAvailableIds) {
                currentServerTrackId
            } else {
                getAlternativeTrackId(serverTrackName, artist)?.also { isAvailable = true }
                    ?: currentServerTrackId
            }

            Track.createdTracks.filter { it.app.name == Apps.DEEZER.name }.firstOrNull { it.id == serverTrackId }
                ?: Track(listOf(artist), serverTrackName, serverTrackAlbumName, serverTrackId, isAvailable, Apps.DEEZER)
                    .also { if (!isAvailable) Track.tracksNotAvailable.getOrPut(Apps.DEEZER, { mutableListOf() }).add(it) }
        }
    }

    private fun getAlternativeTrackId(serverTrackName: String, artist: Artist): String? {
        val serverTrack = searchTrackByNameAndArtist(serverTrackName, listOf(artist))
            ?: findTrackOnArtistTrackList(serverTrackName, artist)

        return serverTrack?.id
    }

    private fun findTrackOnArtistTrackList(serverTrackName: String, artist: Artist): DeezerTrack? {
        val serverArtist = deezerGetArtistURL(artist.id).getURLResponse<DeezerArtist>()
        var serverArtistTracklist = serverArtist.tracklistUrl.getURLResponse<DeezerFoundTracks>()

        var serverAlternativeTrack: DeezerTrack? = null

        while (serverAlternativeTrack == null) {
            serverAlternativeTrack = serverArtistTracklist.data.firstOrNull {
                it.title == serverTrackName && it.readable
            }

            if (serverAlternativeTrack == null) {
                serverArtistTracklist = serverArtistTracklist.next?.getURLResponse<DeezerFoundTracks>()
                    ?: break
            }
        }

        return serverAlternativeTrack
    }

    /**
     * Hard search, currently unnecessary
     */
    private fun searchTrackByName(trackName: String, artist: Artist): DeezerTrack? {
        val foundTracks = deezerSearchTrackURL(trackName).getURLResponse<DeezerFoundTracks>()

        var track: DeezerTrack? = null
        var current25FoundTracks = foundTracks.data

        while (track == null) {
            track = current25FoundTracks.firstOrNull {
                UI.addInProgressDots()

                val trackId = it.id
                val currentFullTrack = deezerGetTrackByIdURL(trackId).getURLResponse<DeezerTrack>()

                val isSameArtist = currentFullTrack.contributors?.any { contributor -> contributor.name == artist.name } ?: false

                isSameArtist && it.readable
            }

            if (track == null) {
                val next25TracksURL = foundTracks.next ?: break

                current25FoundTracks = next25TracksURL.getURLResponse<DeezerFoundTracks>().data
            }
        }

        return track
    }
}
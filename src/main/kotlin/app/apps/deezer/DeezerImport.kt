package app.apps.deezer

import app.AppInterface
import client.Apps
import client.playlistToImport
import com.github.kevinsawicki.http.HttpRequest
import ui.UI
import importer.Importer
import model.Artist
import model.Playlist
import model.Track
import org.json.JSONObject

@Suppress("UNCHECKED_CAST", "ControlFlowWithEmptyBody", "ComplexRedundantLet", "SimplifiableCallChain")
object DeezerImport: DeezerApp(), Importer {
    override fun runImport() {
        isRunning = true
        operation = AppInterface.Operation.IMPORT

        try {
            generateToken()
            fillCurrentCountry(currentToken!!)

            deezerGetUserPlaylistsURL(currentToken).getURLResponse()
                .let { fillPlaylists(it) }

            Playlist.createdPlaylists.filter { it.app.name == Apps.DEEZER.name }.ifEmpty {
                throw Exception("Nenhuma playlist encontrada.")
            }
        } finally {
            isRunning = false
            operation = null
        }
    }

    override fun fillPlaylists(rawPlaylistsMap: HashMap<String, *>) {
        val rawPlaylists = rawPlaylistsMap[DATA] as List<HashMap<String, *>>
        rawPlaylists.forEach { rawPlaylist ->
            val title = rawPlaylist[TITLE].toString()
            val id = rawPlaylist[ID].toString()

            if (playlistToImport.isNotEmpty() && title.uppercase() !in playlistToImport) {
                return@forEach
            }

            UI.updateMessage("$IMPORTING_PLAYLIST $title")

            val playlistRawTracksURL = rawPlaylist[TRACKLIST].toString()
            val playlistRawTracks = URLPlusToken(playlistRawTracksURL, currentToken).getURLResponse()

            Playlist(title, id, Apps.DEEZER)
                .apply {
                    getTracks(this.title, playlistRawTracks).let { trackList ->
                        this.tracks.addAll(trackList)
                    }
                }
        }
    }

    override fun getTracks(playlistTitle: String, playlistRawTracks: HashMap<String, *>): List<Track> {
        val allTracks = mutableListOf<Track>()

        var current25Tracks: HashMap<String, *>? = playlistRawTracks
        while (current25Tracks != null) {
            val rawTracks = current25Tracks[DATA] as List<HashMap<String, *>>

            getCurrentTracks(rawTracks, playlistTitle).let {
                allTracks.addAll(it)
            }

            current25Tracks = (current25Tracks[NEXT] as String?)?.getURLResponse()
        }

        return allTracks
    }

    private fun getCurrentTracks(rawTracks: List<HashMap<String, *>>, playlistTitle: String): List<Track> {
        return rawTracks.map { rawTrack ->
            val artist = (rawTrack[ARTIST] as HashMap<String, *>).let {
                val artistName = it[NAME].toString()
                val artistId = it[ID].toString()

                Artist(artistName, artistId, Apps.DEEZER)
            }

            val trackName = rawTrack[TITLE].toString()
            val albumName = (rawTrack[ALBUM] as HashMap<String, *>).let { it[TITLE].toString() }

            UI.updateMessage("$IMPORTING_PLAYLIST $playlistTitle <br>" +
                    "$SEARCHING_ON_SERVER $FROM_DEEZER: <br>" +
                    "${artist.name} -- $trackName")

            var isAvailable = rawTrack[READABLE] as Boolean
            val currentTracksNotAvailableIds = (Track.tracksNotAvailable[Apps.DEEZER] ?: emptyList()).map { it.id }
            val currentTrackId = rawTrack[ID].toString()
            val trackId = if (isAvailable || currentTrackId in currentTracksNotAvailableIds) {
                currentTrackId
            } else {
                getAlternativeTrackId(trackName, artist)?.also { isAvailable = true }
                    ?: currentTrackId
            }

            Track.createdTracks.filter { it.app.name == Apps.DEEZER.name }.firstOrNull { it.id == trackId }
                ?: Track(listOf(artist), trackName, albumName, trackId, isAvailable, Apps.DEEZER)
                    .also { if (!isAvailable) Track.tracksNotAvailable.getOrPut(Apps.DEEZER) { mutableListOf(it) }.add(it) }
        }
    }

    private fun getAlternativeTrackId(trackName: String, artist: Artist): String? {
        val track = searchTrackByNameAndArtist(trackName, listOf(artist))
            ?: findTrackOnArtistTrackList(trackName, artist)

        return track?.get(ID).let { it?.toString() }
    }

    private fun findTrackOnArtistTrackList(trackName: String, artist: Artist): HashMap<String, *>? {
        val rawArtist = deezerGetArtistURL(artist.id).getURLResponse()

        var rawArtistTracklist = (rawArtist[TRACKLIST] as String).getURLResponse()

        var alternativeTrack: HashMap<String, *>? = null
        while (alternativeTrack == null) {
            val artistTracklist = rawArtistTracklist[DATA] as List<HashMap<String, *>>

            alternativeTrack = artistTracklist.firstOrNull {
                it[TITLE] == trackName &&
                it[READABLE] as Boolean
            }

            if (alternativeTrack == null) {
                rawArtistTracklist = (rawArtistTracklist[NEXT] as String?)?.getURLResponse()
                    ?: break
            }
        }

        return alternativeTrack
    }

    /**
     * Hard search, currently unnecessary
     */
    private fun searchTrackByName(trackName: String, artist: Artist): HashMap<String, *>? {
        val foundTracksObject = deezerSearchTrackURL(trackName).getURLResponse()

        var track: HashMap<String, *>? = null
        var current25FoundTracks = foundTracksObject[DATA] as List<HashMap<String, *>>?

        while (track == null) {
            track = current25FoundTracks?.firstOrNull {
                UI.addInProgressDots()

                val trackId = it[ID].toString()
                val currentFullTrack = deezerGetTrackByIdURL(trackId).getURLResponse()

                val contributors = currentFullTrack[CONTRIBUTORS] as List<HashMap<String, *>>

                contributors.any { contributor -> contributor[NAME] == artist.name } &&
                it[READABLE] as Boolean
            }

            if (track == null) {
                val next25TracksURL = (foundTracksObject[NEXT] as String?) ?: break
                val next25TracksObject = (HttpRequest.get(next25TracksURL).body() as String).let { response -> JSONObject(response).toMap() as HashMap<String, *> }

                current25FoundTracks = next25TracksObject[DATA] as List<HashMap<String, *>>
            }
        }

        return track
    }
}
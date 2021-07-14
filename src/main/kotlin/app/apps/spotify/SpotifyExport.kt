package app.apps.spotify

import app.AppInterface
import client.Apps
import com.github.kevinsawicki.http.HttpRequest
import exporter.Exporter
import model.Artist
import model.Playlist
import model.Track
import org.json.JSONObject
import ui.UI
import java.net.URLEncoder

@Suppress("ControlFlowWithEmptyBody", "UNCHECKED_CAST", "ComplexRedundantLet")
object SpotifyExport: SpotifyApp(), Exporter {
    override fun runExport(externalPlaylists: List<Playlist>) {
        isRunning = true
        operation = AppInterface.Operation.EXPORT

        try {
            generateToken()
            fillCurrentCountry(currentToken!!)

            addPlaylists(externalPlaylists.reversed(), getUserId())

            UI.createDoneExportPlaylistScreen(externalPlaylists)
        } finally {
            isRunning = false
            operation = null
        }
    }

    override fun addPlaylists(externalPlaylists: List<Playlist>, userId: String) {
        externalPlaylists.forEach {
            val onlyAvailableExternalTracks = it.tracks.filter { track -> track.isAvailable }

            val createdPlaylistOnSpotify = createPlaylist(it.title, userId)
            addTracks(createdPlaylistOnSpotify, onlyAvailableExternalTracks)
        }
    }

    override fun addTracks(playlist: Playlist, externalTracks: List<Track>) {
        val spotifyTracks = getTracks(playlist, externalTracks)
            .also { playlist.tracks.addAll(it) }

        spotifyTracks.chunked(100).forEach {
            UI.updateMessage("$EXPORTING_PLAYLIST ${playlist.title}")

            val trackIds = it.map { track -> track.id }
            HttpRequest.post(spotifyAddTrackURL(playlist.id, currentToken)).send(spotifyAddTrackPostBodyTemplate(trackIds)).body()
        }
    }

    override fun getTracks(playlist: Playlist, externalTracks: List<Track>): List<Track> {
        return externalTracks.mapNotNull { currentTrack ->
            val currentNotFoundTracks = (Track.tracksNotFound[Apps.SPOTIFY] ?: emptyList()).map { it.id }
            if (currentTrack.id in currentNotFoundTracks) {
                return@mapNotNull null
            }

            UI.updateMessage("$EXPORTING_PLAYLIST ${playlist.title} <br>" +
                    "$SEARCHING_ON_SERVER $FROM_SPOTIFY: <br>" +
                    "${currentTrack.artist.name} - ${currentTrack.name}")

            val existentTrack = Track.externalTrackNameWithSameTrackOnOtherApp[currentTrack.name]
            if (existentTrack != null) {
                return@mapNotNull existentTrack
            }

            val rawTargetTrack = searchTrackByNameAndArtist(currentTrack)
                ?: searchTrackByName(currentTrack)
                ?: run {
                    playlist.tracksNotFound.add(currentTrack)
                    Track.tracksNotFound.getOrPut(Apps.SPOTIFY) { mutableListOf(currentTrack) }.add(currentTrack)
                    return@mapNotNull null
                }

            val rawTargetTrackArtist = (rawTargetTrack[ARTISTS] as List<HashMap<String, *>>).firstOrNull {
                it[NAME].toString().equals(currentTrack.artist.name, true)
            }?.let {
                val artistName = it[NAME].toString()
                val artistId = it[ID].toString()

                Artist(artistName, artistId, Apps.SPOTIFY)
            } ?: currentTrack.artist

            val rawTargetTrackName = rawTargetTrack[NAME].toString()
            val rawTargetTrackId = rawTargetTrack[ID].toString()

            Track(rawTargetTrackArtist, rawTargetTrackName, rawTargetTrackId, isAvailable = true, Apps.SPOTIFY)
                .also { Track.externalTrackNameWithSameTrackOnOtherApp[currentTrack.name] = it }
        }
    }

    private fun getUserId(): String {
        val user = (HttpRequest.get(URLPlusToken(spotifyGetUserURL(), currentToken)).body() as String)
            .let { JSONObject(it).toMap() }

        return user.entries.first { it.key == ID }.value.toString()
    }

    private fun createPlaylist(title: String, userId: String): Playlist {
        val createdPlaylist = spotifyCreatePlaylistURL(userId, currentToken).doURLPostWith(spotifyCreatePlaylistPostBodyTemplate(title))
        val createdPlaylistId = createdPlaylist.entries.first { it.key == ID }.value.toString()

        return Playlist(title, createdPlaylistId, Apps.SPOTIFY)
    }

    private fun searchTrackByNameAndArtist(currentTrack: Track): HashMap<String, *>? {
        val trackRawName = URLEncoder.encode(currentTrack.name, UTF_8)
        val trackRawArtistName = URLEncoder.encode(currentTrack.artist.name, UTF_8)

        val foundTracksObject = spotifySearchTrackURL(trackRawName, currentToken, givenArtist = trackRawArtistName).getURLResponse()

        return getTrack(foundTracksObject, currentTrack).first
    }

    private fun searchTrackByName(currentTrack: Track): HashMap<String, *>? {
        val trackRawName = URLEncoder.encode(currentTrack.name, UTF_8)

        val candidateTracks = mutableListOf<HashMap<String, *>>()

        var rawCurrent50Tracks = spotifySearchTrackURL(trackRawName, currentToken).getURLResponse()
        var rawTargetTrack: HashMap<String, *>? = null
        while (rawTargetTrack == null) {
            val result = getTrack(rawCurrent50Tracks, currentTrack)

            UI.addSearching()

            val foundTrack = result.first
            if (foundTrack != null) {
                if (foundTrack[NAME] == currentTrack.name) {
                    rawTargetTrack = foundTrack
                } else {
                    candidateTracks.add(foundTrack)
                }
            }

            if (rawTargetTrack == null) {
                val next50Tracks = result.second[NEXT] as String?
                if (next50Tracks == null) {
                    rawTargetTrack = candidateTracks.maxByOrNull { it[POPULARITY] as Int }
                    break
                }

                val request = HttpRequest.get(URLPlusToken(next50Tracks, currentToken, isFirstParameterOfUrl = false))
                if (request.code() != 404) {
                    rawCurrent50Tracks = request.getRequestResponse()
                } else {
                    rawTargetTrack = candidateTracks.maxByOrNull { it[POPULARITY] as Int }
                    break
                }
            }
        }

        return rawTargetTrack
    }

    private fun getTrack(foundTracks: HashMap<String, *>, currentTrack: Track): Pair<HashMap<String, *>?, HashMap<String, *>> {
        val foundTracksObject = foundTracks.entries.first().value as HashMap<String, *>
        val rawFoundTracks = (foundTracksObject[ITEMS] as List<HashMap<String, *>>)
            .ifEmpty {
                URLPlusToken(foundTracksObject[HREF].toString(), currentToken, isFirstParameterOfUrl = false)
                    .let { redoQueryIfHasProblematicWords(it, Apps.SPOTIFY) }
            }
            .sortedByDescending { it[NAME].toString().equals(currentTrack.name, true) }

        return findTrackWithSameArtist(rawFoundTracks, currentTrack) to foundTracksObject
    }

    private fun findTrackWithSameArtist(trackList: List<HashMap<String, *>>, currentTrack: Track): HashMap<String, *>? {
        val isGroupOfArtistAsOneArtist = currentTrack.artist.name.contains('&')

        return trackList.firstOrNull { foundTrack ->
            val artists = foundTrack[ARTISTS] as List<HashMap<String, *>>
            val isRemix by lazy {  currentTrack.name.contains("Remix", true) }

            artists.any {
                val artistName = it[NAME].toString()

                artistName.equals(currentTrack.artist.name, true)
                        ||
                if (isRemix) {
                    val foundTrackName = foundTrack[NAME].toString()
                    foundTrackName.contains(artistName, true)
                } else {
                    false
                }
                        ||
                if (isGroupOfArtistAsOneArtist) {
                    currentTrack.artist.name.contains(artistName)
                } else {
                    false
                }
            }
        }
    }
}
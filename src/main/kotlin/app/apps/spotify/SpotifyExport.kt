package app.apps.spotify

import app.AppInterface
import client.Apps
import client.currentAction
import com.github.kevinsawicki.http.HttpRequest
import exporter.Exporter
import model.Artist
import model.Playlist
import model.Track
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

            val playlists = if (currentAction!!.importAndExportFunction.first.javaClass.name.contains("File", true)) {
                externalPlaylists
            } else {
                externalPlaylists.reversed()
            }

            addPlaylists(playlists, getUserId())

            UI.createDoneExportPlaylistScreen(externalPlaylists)
        } finally {
            isRunning = false
            operation = null
        }
    }

    private fun getUserId(): String {
        val user = URLPlusToken(spotifyGetUserURL(), currentToken).getURLResponse()

        return user[ID].toString()
    }

    override fun addPlaylists(externalPlaylists: List<Playlist>, userId: String) {
        externalPlaylists.forEach {
            val onlyAvailableExternalTracks = it.tracks.filter { track -> track.isAvailable }

            val createdPlaylistOnSpotify = createPlaylist(it.title, userId)
            addTracks(createdPlaylistOnSpotify, onlyAvailableExternalTracks)
        }
    }

    private fun createPlaylist(title: String, userId: String): Playlist {
        val createdPlaylist = spotifyCreatePlaylistURL(userId, currentToken).doURLPostWith(spotifyCreatePlaylistPostBodyTemplate(title), getResponse = true)
        val createdPlaylistId = createdPlaylist[ID].toString()

        return Playlist(title, createdPlaylistId, Apps.SPOTIFY)
    }

    override fun addTracks(playlist: Playlist, externalTracks: List<Track>) {
        val spotifyTracks = getTracks(playlist, externalTracks)
            .also { playlist.tracks.addAll(it) }

        spotifyTracks.map { it.id }.distinct().chunked(100).forEach {
            UI.updateMessage("$EXPORTING_PLAYLIST ${playlist.title}")

            spotifyAddTrackURL(playlist.id, currentToken).doURLPostWith(spotifyAddTrackPostBodyTemplate(it), getResponse = false)
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
                    "${Artist.getArtistsNames(currentTrack.artists)} -- ${currentTrack.name}")

            val existentTrack = Track.externalTrackIdWithSameTrackOnOtherApp[currentTrack.id]
            if (existentTrack != null) {
                return@mapNotNull existentTrack
            }

            val foundTrack = searchTrack(currentTrack)
                ?: run {
                    playlist.tracksNotFound.add(currentTrack)
                    Track.tracksNotFound.getOrPut(Apps.SPOTIFY) { mutableListOf() }.add(currentTrack)
                    return@mapNotNull null
                }

            val foundTrackArtists = (foundTrack[ARTISTS] as List<HashMap<String, *>>).map {
                val artistName = it[NAME].toString()
                val artistId = it[ID].toString()

                Artist(artistName, artistId, Apps.SPOTIFY)
            }

            val foundTrackName = foundTrack[NAME].toString()
            val foundTrackAlbumName = (foundTrack[ALBUM] as HashMap<String, *>).let { it[NAME].toString() }
            val foundTrackId = foundTrack[ID].toString()

            Track(foundTrackArtists, foundTrackName, foundTrackAlbumName, foundTrackId, isAvailable = true, Apps.SPOTIFY)
                .also { Track.externalTrackIdWithSameTrackOnOtherApp[currentTrack.id] = it }
        }
    }

    override fun searchTrack(currentTrack: Track): HashMap<String, *>? {
        val rawTrackName = URLEncoder.encode(currentTrack.name, UTF_8)
        val rawTrackAlbumName = URLEncoder.encode(currentTrack.albumName, UTF_8)

        val candidateTracks = mutableSetOf<HashMap<String, *>>()
        fun getOrAddAsCandidate(foundTrack: HashMap<String, *>?): HashMap<String, *>? {
            return foundTrack?.let {
                val foundTrackName = foundTrack[NAME].toString()
                val foundTrackArtists = (foundTrack[ARTISTS] as List<HashMap<String, *>>).map { artist -> artist[NAME].toString() }

                val trackName = currentTrack.name
                val trackNameWithouProblematicWords = getStringWithoutProblematicWords(currentTrack.name)
                val trackArtists = currentTrack.artists.map { artist -> artist.name }

                val isSameName = (foundTrackName.equals(trackName, true) || foundTrackName.equals(trackNameWithouProblematicWords, true))
                val isSameArtist = foundTrackArtists.any { foundArtist -> trackArtists.any { trackArtist -> trackArtist.equals(foundArtist, true) } }

                if (isSameName && isSameArtist) {
                    it
                } else {
                    candidateTracks.add(it)
                    null
                }
            }
        }

        val foundTrackOnNameAlbumAndArtistSearch = searchTrackByNameArtistAndAlbum(currentTrack, rawTrackName, rawTrackAlbumName)
        getOrAddAsCandidate(foundTrackOnNameAlbumAndArtistSearch)?.let { return it }

        val foundTrackOnNameAndAlbumSearch = searchTrackByNameAndAlbum(currentTrack, rawTrackName, rawTrackAlbumName)
        getOrAddAsCandidate(foundTrackOnNameAndAlbumSearch)?.let { return it }

        val foundTrackOnNameAndArtistSearch = searchTrackByNameAndArtist(currentTrack, rawTrackName)
        getOrAddAsCandidate(foundTrackOnNameAndArtistSearch)?.let { return it }

        val foundTrackOnNameSearch = searchTrackByName(currentTrack, rawTrackName)
        getOrAddAsCandidate(foundTrackOnNameSearch)?.let { return it }

        return getRightCandidateTrack(candidateTracks, currentTrack)
    }

    private fun getRightCandidateTrack(candidateTracks: MutableSet<HashMap<String, *>>, currentTrack: Track): HashMap<String, *>? {
        if (candidateTracks.size == 1) return candidateTracks.first()

        val isRemix = currentTrack.name.contains(REMIX, true)

        fun isAnyOfArtistsTheSame(candidateTrack: HashMap<String, *>, isExatchArtistMatch: Boolean): Boolean {
            val artistsNames = (candidateTrack[ARTISTS] as List<HashMap<String, *>>).map { artist -> artist[NAME].toString() }

            return artistsNames.any { foundArtist ->
                currentTrack.artists.any { trackArtist ->
                    if (isExatchArtistMatch) {
                        foundArtist.equals(trackArtist.name, true)
                    } else {
                        foundArtist.contains(trackArtist.name, true) || trackArtist.name.contains(foundArtist, true)
                    }
                }
            }
        }

        return if (isRemix) {
            candidateTracks.firstOrNull { candidateTrack ->
                val trackName = candidateTrack[NAME].toString()
                val artistsNames = currentTrack.artists.map { it.name }

                artistsNames.any { trackName.contains(it, true) }
            }
        } else {
            null
        }
            ?:
        candidateTracks.filter {
            isAnyOfArtistsTheSame(it, isExatchArtistMatch = true)
        }.ifEmpty {
            candidateTracks.filter {
                isAnyOfArtistsTheSame(it, isExatchArtistMatch = false)
            }
        }.let { tracks ->
            tracks.firstOrNull { (it[ALBUM] as HashMap<String, *>)[NAME].toString().equals(currentTrack.albumName, true) }
                ?:
            tracks.maxByOrNull { it[POPULARITY] as Int }
        }
    }

    override fun searchTrackByNameArtistAndAlbum(currentTrack: Track, rawTrackName: String, rawTrackAlbumName: String): HashMap<String, *>? {
        return currentTrack.artists.firstNotNullOfOrNull {
            val rawTrackArtistName = URLEncoder.encode(it.name, UTF_8)

            val foundTracksObject = spotifySearchTrackURL(rawTrackName, currentToken, givenAlbumName = rawTrackAlbumName, givenArtistName = rawTrackArtistName).getURLResponse()

            getTrack(foundTracksObject, currentTrack).first
        }
    }

    override fun searchTrackByNameAndAlbum(currentTrack: Track, rawTrackName: String, rawTrackAlbumName: String): HashMap<String, *>? {
        val foundTracksObject = spotifySearchTrackURL(rawTrackName, currentToken, givenAlbumName = rawTrackAlbumName).getURLResponse()

        return getTrack(foundTracksObject, currentTrack).first
    }

    override fun searchTrackByNameAndArtist(currentTrack: Track, trackRawName: String): HashMap<String, *>? {
        fun doSearch(considerSameAlbum: Boolean): HashMap<String, *>? {
            return currentTrack.artists.firstNotNullOfOrNull {
                val trackRawArtistName = URLEncoder.encode(it.name, UTF_8)

                val foundTracksObject = spotifySearchTrackURL(trackRawName, currentToken, givenArtistName = trackRawArtistName).getURLResponse()

                getTrack(foundTracksObject, currentTrack, considerSameAlbum).first
            }
        }

        return doSearch(considerSameAlbum = true) ?: doSearch(considerSameAlbum = false)
    }

    override fun searchTrackByName(currentTrack: Track, trackRawName: String): HashMap<String, *>? {
        val candidateTracks = mutableListOf<HashMap<String, *>>()

        var rawCurrent50Tracks = spotifySearchTrackURL(trackRawName, currentToken).getURLResponse()
        var rawTargetTrack: HashMap<String, *>? = null
        while (rawTargetTrack == null) {
            val result = getTrack(rawCurrent50Tracks, currentTrack, considerSameAlbum = false)

            UI.addInProgressDots()

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

    private fun getTrack(foundTracks: HashMap<String, *>, currentTrack: Track, considerSameAlbum: Boolean = true): Pair<HashMap<String, *>?, HashMap<String, *>> {
        val foundTracksObject = foundTracks.entries.first().value as HashMap<String, *>
        val rawFoundTracks = (foundTracksObject[ITEMS] as List<HashMap<String, *>>)
            .ifEmpty {
                URLPlusToken(foundTracksObject[HREF].toString(), currentToken, isFirstParameterOfUrl = false)
                    .let { redoQueryIfHasProblematicWords(it, Apps.SPOTIFY).first }
            }
            .sortedByDescending { it[NAME].toString().equals(currentTrack.name, true) }

        return findTrackWithSameArtistAndAlbum(rawFoundTracks, currentTrack, considerSameAlbum) to foundTracksObject
    }

    private fun findTrackWithSameArtistAndAlbum(foundTracks: List<HashMap<String, *>>, currentTrack: Track, considerSameAlbum: Boolean): HashMap<String, *>? {
        val isRemix by lazy {  currentTrack.name.contains(REMIX, true) }
        val currentTrackAlbumName by lazy { currentTrack.albumName.trim() }
        val currentTrackAlbumNameWithoutProblematicWords by lazy { getStringWithoutProblematicWords(currentTrack.albumName, isToEncode = false).trim() }

        return foundTracks.firstOrNull { foundTrack ->
            val foundTrackArtists = foundTrack[ARTISTS] as List<HashMap<String, *>>

            if (considerSameAlbum) {
                isSameAlbum(foundTrack, currentTrackAlbumName, currentTrackAlbumNameWithoutProblematicWords)
            } else {
                true
            }
                    &&
            isSameArtist(foundTrackArtists, foundTrack, currentTrack, isRemix)
        }
    }

    private fun isSameAlbum(foundTrack: HashMap<String, *>, currentTrackAlbumName: String, currentTrackAlbumNameWithoutProblematicWords: String): Boolean {
        val foundTrackAlbumName = (foundTrack[ALBUM] as HashMap<String, *>).let { it[NAME].toString() }.trim()
        val foundTrackAlbumNameWithoutProblematicWords by lazy { getStringWithoutProblematicWords(foundTrackAlbumName, isToEncode = false).trim() }

        return foundTrackAlbumName.equals(currentTrackAlbumName, true) ||
        foundTrackAlbumName.equals(currentTrackAlbumNameWithoutProblematicWords, true) ||
        foundTrackAlbumNameWithoutProblematicWords.equals(currentTrackAlbumName, true) ||
        foundTrackAlbumNameWithoutProblematicWords.equals(currentTrackAlbumNameWithoutProblematicWords, true)
    }

    private fun isSameArtist(foundTrackArtists: List<HashMap<String, *>>, foundTrack: HashMap<String, *>, currentTrack: Track, isRemix: Boolean): Boolean {
        return foundTrackArtists.any { artist ->
            val artistName = artist[NAME].toString()

            currentTrack.artists.any { it.name.contains(artistName, true) || artistName.contains(it.name, true) } ||
            isRemix && foundTrack[NAME].toString().contains(artistName, true)
        }
    }
}
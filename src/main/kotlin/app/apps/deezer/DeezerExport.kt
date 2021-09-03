package app.apps.deezer

import app.AppInterface
import client.Apps
import client.currentAction
import exporter.Exporter
import model.Artist
import model.Playlist
import model.Track
import ui.UI
import java.net.URLEncoder

@Suppress("UNCHECKED_CAST")
object DeezerExport: DeezerApp(), Exporter {
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
        val user = URLPlusToken(deezerGetUserURL(), currentToken).getURLResponse()

        return user[ID].toString()
    }

    override fun addPlaylists(externalPlaylists: List<Playlist>, userId: String) {
        externalPlaylists.forEach {
            val onlyAvailableExternalTracks = it.tracks.filter { track -> track.isAvailable }

            val createdPlaylist = createPlaylist(it, userId)
            addTracks(createdPlaylist, onlyAvailableExternalTracks)
        }
    }

    private fun createPlaylist(playlist: Playlist, userId: String): Playlist {
        val playlistRawName = URLEncoder.encode(playlist.title, UTF_8)
        val newPlaylist = deezerCreatePlaylistURL(userId, playlistRawName, currentToken).doURLPost(getResponse = true)
        val newPlaylistId = newPlaylist[ID].toString()

        return Playlist(playlist.title, newPlaylistId, Apps.DEEZER)
    }

    override fun addTracks(playlist: Playlist, externalTracks: List<Track>) {
        val deezerTracks = getTracks(playlist, externalTracks)
            .also { playlist.tracks.addAll(it) }

        UI.updateMessage("$EXPORTING_PLAYLIST ${playlist.title}")

        deezerTracks.map { it.id }.distinct().chunked(50).forEach {
            UI.addInProgressDots()

            val current50TracksIds = it.reduce { acc, s -> "$acc,$s" }.let { currentTracksIds -> URLEncoder.encode(currentTracksIds, UTF_8) }

            deezerAddTrackURL(playlist.id, current50TracksIds, currentToken).doURLPost()
        }
    }

    override fun getTracks(playlist: Playlist, externalTracks: List<Track>): List<Track> {
        return externalTracks.mapNotNull { currentTrack ->
            val currentNotFoundTracks = (Track.tracksNotFound[Apps.DEEZER] ?: emptyList()).map { it.id }
            if (currentTrack.id in currentNotFoundTracks) {
                return@mapNotNull null
            }

            UI.updateMessage("$EXPORTING_PLAYLIST ${playlist.title} <br>" +
                    "$SEARCHING_ON_SERVER $FROM_DEEZER: <br>" +
                    "${Artist.getArtistsNames(currentTrack.artists)} -- ${currentTrack.name}")

            val existentTrack = Track.externalTrackIdWithSameTrackOnOtherApp[currentTrack.id]
            if (existentTrack != null) {
                return@mapNotNull existentTrack
            }

            val foundTrack = searchTrack(currentTrack)
                ?: run {
                    playlist.tracksNotFound.add(currentTrack)
                    Track.tracksNotFound.getOrPut(Apps.DEEZER) { mutableListOf() }.add(currentTrack)
                    return@mapNotNull null
                }

            val foundTrackArtist = (foundTrack[ARTIST] as HashMap<String, *>).let {
                val artistName = it[NAME].toString()
                val artistId = it[ID].toString()

                Artist(artistName, artistId, Apps.DEEZER)
            }

            val foundTrackName = foundTrack[TITLE].toString()
            val foundTrackId = foundTrack[ID].toString()
            val foundTrackAlbumName = (foundTrack[ALBUM] as HashMap<String, *>).let { it[TITLE].toString() }

            Track(listOf(foundTrackArtist), foundTrackName, foundTrackAlbumName, foundTrackId, isAvailable = true, Apps.DEEZER)
                .also { Track.externalTrackIdWithSameTrackOnOtherApp[currentTrack.name] = it }
        }
    }

    override fun searchTrack(currentTrack: Track): HashMap<String, *>? {
        val rawTrackName = URLEncoder.encode(currentTrack.name, UTF_8)
        val rawTrackAlbumName = URLEncoder.encode(currentTrack.albumName, UTF_8)

        val candidateTracks = mutableSetOf<HashMap<String, *>>()
        fun getOrAddAsCandidate(foundTrack: HashMap<String, *>?): HashMap<String, *>? {
            return foundTrack?.let {
                val foundTrackName = foundTrack[TITLE].toString()
                val foundTrackArtist = (foundTrack[ARTIST] as HashMap<String, *>).let { artist -> artist[NAME].toString() }

                val trackName = currentTrack.name
                val trackNameWithouProblematicWords = getStringWithoutProblematicWords(currentTrack.name)
                val trackArtists = currentTrack.artists.map { artist -> artist.name }

                val isSameName = (foundTrackName.equals(trackName, true) || foundTrackName.equals(trackNameWithouProblematicWords, true))
                val isSameArtist = trackArtists.any { trackArtist -> trackArtist.equals(foundTrackArtist, true) }

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
            val foundArtistName = (candidateTrack[ARTIST] as HashMap<String, *>).let { it[NAME].toString() }

            return currentTrack.artists.any { trackArtist ->
                if (isExatchArtistMatch) {
                    foundArtistName.equals(trackArtist.name, true)
                } else {
                    foundArtistName.contains(trackArtist.name, true) || trackArtist.name.contains(foundArtistName, true)
                }
            }
        }

        return candidateTracks.firstOrNull {
            (it[ALBUM] as HashMap<String, *>)[TITLE].toString().equals(currentTrack.albumName, true)
        }
            ?:
        if (isRemix) {
            candidateTracks.firstOrNull { candidateTrack ->
                val trackName = candidateTrack[TITLE].toString()
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
        }.minByOrNull { it[RANK] as Int }
    }

    override fun searchTrackByNameArtistAndAlbum(currentTrack: Track, rawTrackName: String, rawTrackAlbumName: String): HashMap<String, *>? {
        return currentTrack.artists.firstNotNullOfOrNull {
            val rawTrackArtistName = URLEncoder.encode(it.name, UTF_8)

            val searchURL = deezerSearchTrackURL(rawTrackName, rawTrackAlbumName, rawTrackArtistName)
            val foundTracksObject = searchURL.getURLResponse()

            getTrack(foundTracksObject, searchURL, currentTrack).first
        }
    }

    override fun searchTrackByNameAndAlbum(currentTrack: Track, rawTrackName: String, rawTrackAlbumName: String): HashMap<String, *>? {
        val searchURL = deezerSearchTrackURL(rawTrackName, rawTrackAlbumName)
        val foundTracksObject = searchURL.getURLResponse()

        return getTrack(foundTracksObject, searchURL, currentTrack).first
    }

    override fun searchTrackByNameAndArtist(currentTrack: Track, trackRawName: String): HashMap<String, *>? {
        fun doSearch(considerSameAlbum: Boolean): HashMap<String, *>? {
            return currentTrack.artists.firstNotNullOfOrNull {
                val trackRawArtistName = URLEncoder.encode(it.name, UTF_8)

                val searchURL = deezerSearchTrackURL(trackRawName, givenArtistName = trackRawArtistName)
                val foundTracksObject = searchURL.getURLResponse()

                getTrack(foundTracksObject, searchURL, currentTrack, considerSameAlbum).first
            }
        }

        return doSearch(considerSameAlbum = true) ?: doSearch(considerSameAlbum = false)
    }

    override fun searchTrackByName(currentTrack: Track, trackRawName: String): HashMap<String, *>? {
        val candidateTracks = mutableSetOf<HashMap<String, *>>()

        var searchURL: String? = deezerSearchTrackURL(trackRawName)
        var rawCurrent25Tracks = searchURL?.getURLResponse()

        var rawTargetTrack: HashMap<String, *>? = null
        while (rawTargetTrack == null) {
            val result = getTrack(rawCurrent25Tracks!!, searchURL!!, currentTrack, considerSameAlbum = false)

            UI.addInProgressDots()

            val foundTrack = result.first
            if (foundTrack != null) {
                if (foundTrack[TITLE] == currentTrack.name) {
                    rawTargetTrack = foundTrack
                } else {
                    candidateTracks.add(foundTrack)
                }
            }

            if (rawTargetTrack == null) {
                searchURL = result.second[NEXT] as String?
                if (searchURL == null) {
                    rawTargetTrack = getRightCandidateTrack(candidateTracks, currentTrack)
                    break
                }

                rawCurrent25Tracks = searchURL.getURLResponse()
//                val request = HttpRequest.get(URLPlusToken(next50Tracks, currentToken, isFirstParameterOfUrl = false))
//                if (request.code() != 404) {
//                    rawCurrent50Tracks = request.getRequestResponse()
//                } else {
//                    rawTargetTrack = candidateTracks.maxByOrNull { it[POPULARITY] as Int }
//                    break
//                }
            }
        }

        return rawTargetTrack
    }

    fun oldFunc(currentTrack: Track, trackRawName: String): HashMap<String, *>? {
        val trackName = currentTrack.name
        val artists = currentTrack.artists

        var track: HashMap<String, *>? = null
        val candidateTracks = mutableListOf<HashMap<String, *>>()

        val trackRawSearchURL by lazy { deezerSearchURL(trackRawName) }
        val trackSearchURL = deezerSearchTrackURL(trackRawName)
        var foundTracksObject: HashMap<String, *>? = trackSearchURL.getURLResponse()

        var current25FoundTracks = (foundTracksObject?.get(DATA) as List<HashMap<String, *>>?)
            ?.ifEmpty { trackRawSearchURL.getURLResponse().also { foundTracksObject = it }[DATA] as List<HashMap<String, *>> }
            ?.ifEmpty { redoQueryIfHasProblematicWords(trackSearchURL, Apps.DEEZER).also { foundTracksObject = it.second?.getURLResponse() }.first }
            ?.ifEmpty { redoQueryIfHasProblematicWords(trackRawSearchURL, Apps.DEEZER).also { foundTracksObject = it.second?.getURLResponse() }.first }
            ?.sortedByDescending { it[NAME].toString().equals(trackName, true) }

        while (track == null) {
            UI.addInProgressDots()

            track = current25FoundTracks?.firstOrNull { foundTrack ->
                val isAvailable = (foundTrack[READABLE] as Boolean)
                if (!isAvailable) {
                    return@firstOrNull false
                }

                val foundTrackName = foundTrack[TITLE].toString()
                if (!foundTrackName.contains(trackName, true) && !trackName.contains(foundTrackName, true)) {
                    return@firstOrNull false
                }

                val foundTrackArtist = foundTrack[ARTIST] as HashMap<String, *>
                val foundTrackArtistName = foundTrackArtist[NAME].toString()

                if (artists.any { it.name.contains(foundTrackArtistName, true) || foundTrackArtistName.contains(it.name, true) }) {
                    candidateTracks.add(foundTrack)
                }

                artists.any { it.name.equals(foundTrackArtistName, true) } ||
                        (trackName.contains("Remix", true) && artists.any { foundTrackName.contains(it.name, true) })
            }

            if (track == null) {
                val next25TracksURL = (foundTracksObject?.get(NEXT) as String?) ?: break
                foundTracksObject = next25TracksURL.getURLResponse()

                current25FoundTracks = (foundTracksObject!![DATA] as List<HashMap<String, *>>)
                    .sortedByDescending { it[NAME].toString().equals(trackName, true) }
            }
        }

        return track ?: candidateTracks.minByOrNull { it[RANK] as Int }
    }

    private fun getTrack(foundTracksObject: HashMap<String, *>, searchURL: String, currentTrack: Track, considerSameAlbum: Boolean = true): Pair<HashMap<String, *>?, HashMap<String, *>> {
        val rawFoundTracks = (foundTracksObject[DATA] as List<HashMap<String, *>>)
            .ifEmpty { redoQueryIfHasProblematicWords(searchURL, Apps.DEEZER).first }
            .ifEmpty { searchURL.withoutQuotes(onlyTrackName = true).getURLResponse()[DATA] as List<HashMap<String, *>> }
            .ifEmpty { redoQueryIfHasProblematicWords(searchURL.withoutQuotes(onlyTrackName = true), Apps.DEEZER).first }
            .sortedByDescending { it[TITLE].toString().equals(currentTrack.name, true) }

        return findTrackWithSameArtistAndAlbum(rawFoundTracks, currentTrack, considerSameAlbum) to foundTracksObject
    }

    private fun findTrackWithSameArtistAndAlbum(foundTracks: List<HashMap<String, *>>, currentTrack: Track, considerSameAlbum: Boolean): HashMap<String, *>? {
        val isRemix by lazy {  currentTrack.name.contains(REMIX, true) }
        val currentTrackAlbumName by lazy { currentTrack.albumName.trim() }
        val currentTrackAlbumNameWithoutProblematicWords by lazy { getStringWithoutProblematicWords(currentTrack.albumName, isToEncode = false).trim() }

        return foundTracks.firstOrNull { foundTrack ->
            val foundTrackArtist = foundTrack[ARTIST] as HashMap<String, *>

            if (considerSameAlbum) {
                isSameAlbum(foundTrack, currentTrackAlbumName, currentTrackAlbumNameWithoutProblematicWords)
            } else {
                true
            }
                    &&
            isSameArtist(foundTrackArtist, foundTrack, currentTrack, isRemix)
        }
    }

    private fun isSameAlbum(foundTrack: HashMap<String, *>, currentTrackAlbumName: String, currentTrackAlbumNameWithoutProblematicWords: String): Boolean {
        val foundTrackAlbumName = (foundTrack[ALBUM] as HashMap<String, *>).let { it[TITLE].toString() }.trim()
        val foundTrackAlbumNameWithoutProblematicWords by lazy { getStringWithoutProblematicWords(foundTrackAlbumName, isToEncode = false).trim() }

        return foundTrackAlbumName.equals(currentTrackAlbumName, true) ||
        foundTrackAlbumName.equals(currentTrackAlbumNameWithoutProblematicWords, true) ||
        foundTrackAlbumNameWithoutProblematicWords.equals(currentTrackAlbumName, true) ||
        foundTrackAlbumNameWithoutProblematicWords.equals(currentTrackAlbumNameWithoutProblematicWords, true)
    }

    private fun isSameArtist(foundTrackArtist: HashMap<String, *>, foundTrack: HashMap<String, *>, currentTrack: Track, isRemix: Boolean): Boolean {
        val artistName = foundTrackArtist[NAME].toString()

        return currentTrack.artists.any { it.name.contains(artistName, true) || artistName.contains(it.name, true) } ||
        isRemix && foundTrack[NAME].toString().contains(artistName, true)
    }
}
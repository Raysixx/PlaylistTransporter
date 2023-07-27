package app.apps.deezer

import app.AppInterface
import client.Apps
import client.currentAction
import app.Exporter
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
            fillUser(currentToken!!)

            val playlists = if (currentAction!!.importAndExportFunction.first.javaClass.name.contains("File", true)) {
                externalPlaylists
            } else {
                externalPlaylists.reversed()
            }

            addPlaylists(playlists, user!!.id)

            UI.createDoneExportPlaylistScreen(externalPlaylists)
        } finally {
            isRunning = false
            operation = null
        }
    }

    override fun addPlaylists(externalPlaylists: List<Playlist>, userId: String) {
        externalPlaylists.forEach {
            val onlyAvailableExternalTracks = it.tracks.filter { track -> track.isAvailable }

            val createdPlaylist = createPlaylist(it, userId)
            addTracks(createdPlaylist, onlyAvailableExternalTracks)
        }
    }

    private fun createPlaylist(playlist: Playlist, userId: String): Playlist {
        val playlistEncodedName = URLEncoder.encode(playlist.title, UTF_8)
        val newPlaylist = deezerCreatePlaylistURL(userId, playlistEncodedName, currentToken).doURLPost<DeezerPlaylist>()

        return Playlist(playlist.title, newPlaylist.id, Apps.DEEZER)
    }

    override fun addTracks(createdPlaylist: Playlist, externalTracks: List<Track>) {
        val serverTracks = getTracks(createdPlaylist, externalTracks)
            .also { createdPlaylist.tracks.addAll(it) }

        UI.updateMessage("$EXPORTING_PLAYLIST ${createdPlaylist.title}")

        serverTracks.map { it.id }.distinct().chunked(50).forEach {
            UI.addInProgressDots()

            val serverCurrent50TracksIds = it.reduce { acc, s -> "$acc,$s" }.let { currentTracksIds -> URLEncoder.encode(currentTracksIds, UTF_8) }

            deezerAddTrackURL(createdPlaylist.id, serverCurrent50TracksIds, currentToken).doURLPost()
        }
    }

    override fun getTracks(createdPlaylist: Playlist, externalTracks: List<Track>): List<Track> {
        return externalTracks.mapNotNull { externalTrack ->
            val serverNotFoundTracks = (Track.tracksNotFound[Apps.DEEZER] ?: emptyList()).map { it.id }
            if (externalTrack.id in serverNotFoundTracks) {
                return@mapNotNull null
            }

            UI.updateMessage("$EXPORTING_PLAYLIST ${createdPlaylist.title} <br>" +
                    "$SEARCHING_ON_SERVER $FROM_DEEZER: <br>" +
                    "${Artist.getArtistsNames(externalTrack.artists)} -- ${externalTrack.name}")

            val serverExistentTrack = Track.externalTrackIdWithSameTrackOnOtherApp[externalTrack.id]
            if (serverExistentTrack != null) {
                return@mapNotNull serverExistentTrack
            }

            val serverFoundTrack = searchTrack(externalTrack)
                ?: run {
                    createdPlaylist.tracksNotFound.add(externalTrack)
                    Track.tracksNotFound.getOrPut(Apps.DEEZER) { mutableListOf() }.add(externalTrack)
                    return@mapNotNull null
                }

            val serverFoundTrackArtist = serverFoundTrack.artist.let {
                Artist(it.name, it.id, Apps.DEEZER)
            }

            Track(listOf(serverFoundTrackArtist), serverFoundTrack.title, serverFoundTrack.album.title, serverFoundTrack.id, isAvailable = true, Apps.DEEZER)
                .also { Track.externalTrackIdWithSameTrackOnOtherApp[externalTrack.name] = it }
        }
    }

    override fun searchTrack(externalTrack: Track): DeezerTrack? {
        val externalTrackName = externalTrack.name
        val externalTrackNameWithouProblematicWords = getStringWithoutProblematicWords(externalTrack.name)
        val externalTrackArtistsNames = externalTrack.artists.map { artist -> artist.name }

        val encodedServerTrackName = URLEncoder.encode(externalTrack.name, UTF_8)
        val encodedServerTrackAlbumName = URLEncoder.encode(externalTrack.albumName, UTF_8)

        val candidateServerTracks = mutableSetOf<DeezerTrack>()
        fun getOrAddAsCandidate(serverFoundTrack: DeezerTrack?): DeezerTrack? {
            return serverFoundTrack?.let {
                val serverFoundTrackName = serverFoundTrack.title
                val serverFoundTrackArtistName = serverFoundTrack.artist.name

                val isSameName = (serverFoundTrackName.equals(externalTrackName, true) || serverFoundTrackName.equals(externalTrackNameWithouProblematicWords, true))
                val isSameArtist = externalTrackArtistsNames.any { trackArtist -> trackArtist.equals(serverFoundTrackArtistName, true) }

                if (isSameName && isSameArtist) {
                    it
                } else {
                    candidateServerTracks.add(it)
                    null
                }
            }
        }

        val serverFoundTrackOnNameAlbumAndArtistSearch = searchTrackByNameArtistAndAlbum(externalTrack, encodedServerTrackName, encodedServerTrackAlbumName)
        getOrAddAsCandidate(serverFoundTrackOnNameAlbumAndArtistSearch)?.let { return it }

        val serverFoundTrackOnNameAndAlbumSearch = searchTrackByNameAndAlbum(externalTrack, encodedServerTrackName, encodedServerTrackAlbumName)
        getOrAddAsCandidate(serverFoundTrackOnNameAndAlbumSearch)?.let { return it }

        val serverFoundTrackOnNameAndArtistSearch = searchTrackByNameAndArtist(externalTrack, encodedServerTrackName)
        getOrAddAsCandidate(serverFoundTrackOnNameAndArtistSearch)?.let { return it }

        val serverFoundTrackOnNameSearch = searchTrackByName(externalTrack, encodedServerTrackName)
        getOrAddAsCandidate(serverFoundTrackOnNameSearch)?.let { return it }

        return getRightCandidateTrack(candidateServerTracks, externalTrack)
    }

    private fun getRightCandidateTrack(candidateServerTracks: MutableSet<DeezerTrack>, externalTrack: Track): DeezerTrack? {
        if (candidateServerTracks.size == 1) return candidateServerTracks.first()

        val isRemix = externalTrack.name.contains(REMIX, true)

        fun isAnyOfArtistsTheSame(candidateServerTrack: DeezerTrack, isExactArtistMatch: Boolean): Boolean {
            val candidateServerTrackArtistName = candidateServerTrack.artist.name

            return externalTrack.artists.any { trackArtist ->
                if (isExactArtistMatch) {
                    candidateServerTrackArtistName.equals(trackArtist.name, true)
                } else {
                    candidateServerTrackArtistName.contains(trackArtist.name, true) ||
                    trackArtist.name.contains(candidateServerTrackArtistName, true)
                }
            }
        }

        return candidateServerTracks.firstOrNull {
            it.album.title.equals(externalTrack.albumName, true)
        }
            ?:
        if (isRemix) {
            candidateServerTracks.firstOrNull { candidateServerTrack ->
                val candidateServerTrackName = candidateServerTrack.title
                val candidateServerTrackArtistsNames = externalTrack.artists.map { it.name }

                candidateServerTrackArtistsNames.any { candidateServerTrackName.contains(it, true) }
            }
        } else {
            null
        }
            ?:
        candidateServerTracks.filter {
            isAnyOfArtistsTheSame(it, isExactArtistMatch = true)
        }.ifEmpty {
            candidateServerTracks.filter {
                isAnyOfArtistsTheSame(it, isExactArtistMatch = false)
            }
        }.minByOrNull { it.rank }
    }

    override fun searchTrackByNameArtistAndAlbum(externalTrack: Track, encodedTrackName: String, encodedServerTrackAlbumName: String): DeezerTrack? {
        return externalTrack.artists.firstNotNullOfOrNull {
            val encodedExternalTrackArtistName = URLEncoder.encode(it.name, UTF_8)

            val searchURL = deezerSearchTrackURL(encodedTrackName, encodedServerTrackAlbumName, encodedExternalTrackArtistName)
            val serverFoundTracksObject = searchURL.getURLResponse<DeezerFoundTracks>()

            getTrack(serverFoundTracksObject, searchURL, externalTrack).first
        }
    }

    override fun searchTrackByNameAndAlbum(externalTrack: Track, encodedTrackName: String, encodedServerTrackAlbumName: String): DeezerTrack? {
        val searchURL = deezerSearchTrackURL(encodedTrackName, encodedServerTrackAlbumName)
        val serverFoundTracksObject = searchURL.getURLResponse<DeezerFoundTracks>()

        return getTrack(serverFoundTracksObject, searchURL, externalTrack).first
    }

    override fun searchTrackByNameAndArtist(externalTrack: Track, encodedTrackName: String): DeezerTrack? {
        fun doSearch(considerSameAlbum: Boolean): DeezerTrack? {
            return externalTrack.artists.firstNotNullOfOrNull {
                val encodedExternalTrackArtistName = URLEncoder.encode(it.name, UTF_8)

                val searchURL = deezerSearchTrackURL(encodedTrackName, givenArtistName = encodedExternalTrackArtistName)
                val serverFoundTracksObject = searchURL.getURLResponse<DeezerFoundTracks>()

                getTrack(serverFoundTracksObject, searchURL, externalTrack, considerSameAlbum).first
            }
        }

        return doSearch(considerSameAlbum = true) ?: doSearch(considerSameAlbum = false)
    }

    override fun searchTrackByName(externalTrack: Track, encodedTrackName: String): DeezerTrack? {
        val serverCandidateTracks = mutableSetOf<DeezerTrack>()

        var searchURL: String? = deezerSearchTrackURL(encodedTrackName)
        var serverCurrent25Tracks = searchURL?.getURLResponse<DeezerFoundTracks>()

        var serverTargetTrack: DeezerTrack? = null
        while (serverTargetTrack == null) {
            val result = getTrack(serverCurrent25Tracks!!, searchURL!!, externalTrack, considerSameAlbum = false)

            UI.addInProgressDots()

            val serverFoundTrack = result.first
            if (serverFoundTrack != null) {
                if (serverFoundTrack.title == externalTrack.name) {
                    serverTargetTrack = serverFoundTrack
                } else {
                    serverCandidateTracks.add(serverFoundTrack)
                }
            }

            if (serverTargetTrack == null) {
                searchURL = result.second.next
                if (searchURL == null) {
                    serverTargetTrack = getRightCandidateTrack(serverCandidateTracks, externalTrack)
                    break
                } else {
                    serverCurrent25Tracks = searchURL.getURLResponse<DeezerFoundTracks>()
                }
            }
        }

        return serverTargetTrack
    }

    private fun getTrack(serverFoundTracksObject: DeezerFoundTracks, searchURL: String, externalTrack: Track, considerSameAlbum: Boolean = true): Pair<DeezerTrack?, DeezerFoundTracks> {
        val serverFoundTracks = serverFoundTracksObject.data
            .ifEmpty { redoQueryIfHasProblematicWords(searchURL, Apps.DEEZER).first }
            .ifEmpty { searchURL.withoutQuotes(onlyTrackName = true).getURLResponse<DeezerFoundTracks>().data }
            .ifEmpty { redoQueryIfHasProblematicWords(searchURL.withoutQuotes(onlyTrackName = true), Apps.DEEZER).first }
            .sortedByDescending { it.title.equals(externalTrack.name, true) }
            as List<DeezerTrack>

        return findTrackWithSameArtistAndAlbum(serverFoundTracks, externalTrack, considerSameAlbum) to serverFoundTracksObject
    }

    private fun findTrackWithSameArtistAndAlbum(serverFoundTracks: List<DeezerTrack>, externalTrack: Track, considerSameAlbum: Boolean): DeezerTrack? {
        val isRemix by lazy {  externalTrack.name.contains(REMIX, true) }
        val externalTrackAlbumName by lazy { externalTrack.albumName.trim() }
        val externalTrackAlbumNameWithoutProblematicWords by lazy { getStringWithoutProblematicWords(externalTrack.albumName, isToEncode = false).trim() }

        return serverFoundTracks.firstOrNull { serverFoundTrack ->
            val serverFoundTrackArtist = serverFoundTrack.artist

            if (considerSameAlbum) {
                isSameAlbum(serverFoundTrack, externalTrackAlbumName, externalTrackAlbumNameWithoutProblematicWords)
            } else {
                true
            }
                    &&
            isSameArtist(serverFoundTrackArtist, serverFoundTrack, externalTrack, isRemix)
        }
    }

    private fun isSameAlbum(serverFoundTrack: DeezerTrack, externalTrackAlbumName: String, externalTrackAlbumNameWithoutProblematicWords: String): Boolean {
        val serverFoundTrackAlbumName = serverFoundTrack.album.title.trim()
        val serverFoundTrackAlbumNameWithoutProblematicWords by lazy { getStringWithoutProblematicWords(serverFoundTrackAlbumName, isToEncode = false).trim() }

        return serverFoundTrackAlbumName.equals(externalTrackAlbumName, true) ||
        serverFoundTrackAlbumName.equals(externalTrackAlbumNameWithoutProblematicWords, true) ||
        serverFoundTrackAlbumNameWithoutProblematicWords.equals(externalTrackAlbumName, true) ||
        serverFoundTrackAlbumNameWithoutProblematicWords.equals(externalTrackAlbumNameWithoutProblematicWords, true)
    }

    private fun isSameArtist(serverFoundTrackArtist: DeezerArtist, serverFoundTrack: DeezerTrack, externalTrack: Track, isRemix: Boolean): Boolean {
        val serverFoundTrackArtistName = serverFoundTrackArtist.name

        return externalTrack.artists.any { it.name.contains(serverFoundTrackArtistName, true) || serverFoundTrackArtistName.contains(it.name, true) } ||
        isRemix && serverFoundTrack.title.contains(serverFoundTrackArtistName, true)
    }
}
package app.apps.spotify

import app.AppInterface
import client.Apps
import client.currentAction
import com.github.kevinsawicki.http.HttpRequest
import app.Exporter
import model.Artist
import model.Playlist
import model.Track
import ui.UI
import java.net.URLEncoder

@Suppress("UNCHECKED_CAST")
object SpotifyExport: SpotifyApp(), Exporter {
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

            val createdPlaylist = createPlaylist(it.title, userId)
            addTracks(createdPlaylist, onlyAvailableExternalTracks)
        }
    }

    private fun createPlaylist(title: String, userId: String): Playlist {
        val createdPlaylist = spotifyCreatePlaylistURL(userId, currentToken).doURLPostWith<SpotifyPlaylist>(spotifyCreatePlaylistPostBodyTemplate(title))

        return Playlist(title, createdPlaylist.id, Apps.SPOTIFY)
    }

    override fun addTracks(createdPlaylist: Playlist, externalTracks: List<Track>) {
        val serverTracks = getTracks(createdPlaylist, externalTracks)
            .also { createdPlaylist.tracks.addAll(it) }

        serverTracks.map { it.id }.distinct().chunked(100).forEach {
            UI.updateMessage("$EXPORTING_PLAYLIST ${createdPlaylist.title}")

            spotifyAddTrackURL(createdPlaylist.id, currentToken).doURLPostWith(spotifyAddTrackPostBodyTemplate(it))
        }
    }

    override fun getTracks(createdPlaylist: Playlist, externalTracks: List<Track>): List<Track> {
        return externalTracks.mapNotNull { externalTrack ->
            val serverNotFoundTracks = (Track.tracksNotFound[Apps.SPOTIFY] ?: emptyList()).map { it.id }
            if (externalTrack.id in serverNotFoundTracks) {
                return@mapNotNull null
            }

            UI.updateMessage("$EXPORTING_PLAYLIST ${createdPlaylist.title} <br>" +
                    "$SEARCHING_ON_SERVER $FROM_SPOTIFY: <br>" +
                    "${Artist.getArtistsNames(externalTrack.artists)} -- ${externalTrack.name}")

            val serverExistentTrack = Track.externalTrackIdWithSameTrackOnOtherApp[externalTrack.id]
            if (serverExistentTrack != null) {
                return@mapNotNull serverExistentTrack
            }

            val serverFoundTrack = searchTrack(externalTrack)
                ?: run {
                    createdPlaylist.tracksNotFound.add(externalTrack)
                    Track.tracksNotFound.getOrPut(Apps.SPOTIFY) { mutableListOf() }.add(externalTrack)
                    return@mapNotNull null
                }

            val serverFoundTrackArtists = serverFoundTrack.artists.map {
                Artist(it.name, it.id, Apps.SPOTIFY)
            }

            val serverFoundTrackName = serverFoundTrack.title
            val serverFoundTrackAlbumName = serverFoundTrack.album.title
            val serverFoundTrackId = serverFoundTrack.id

            Track(serverFoundTrackArtists, serverFoundTrackName, serverFoundTrackAlbumName, serverFoundTrackId, isAvailable = true, Apps.SPOTIFY)
                .also { Track.externalTrackIdWithSameTrackOnOtherApp[externalTrack.id] = it }
        }
    }

    override fun searchTrack(externalTrack: Track): SpotifyTrack? {
        val externalTrackName = externalTrack.name
        val externalTrackNameWithouProblematicWords = getStringWithoutProblematicWords(externalTrack.name)
        val externalTrackArtistsNames = externalTrack.artists.map { artist -> artist.name }

        val encodedExternalTrackName = URLEncoder.encode(externalTrack.name, UTF_8)
        val encodedExternalTrackAlbumName = URLEncoder.encode(externalTrack.albumName, UTF_8)

        val serverCandidateTracks = mutableSetOf<SpotifyTrack>()
        fun getOrAddAsCandidate(serverFoundTrack: SpotifyTrack?): SpotifyTrack? {
            return serverFoundTrack?.let {
                val serverFoundTrackName = it.title
                val serverFoundTrackArtistsNames = it.artists.map { serverArtist -> serverArtist.name }

                val isSameName = (serverFoundTrackName.equals(externalTrackName, true) || serverFoundTrackName.equals(externalTrackNameWithouProblematicWords, true))
                val isSameArtist = serverFoundTrackArtistsNames.any { foundArtist -> externalTrackArtistsNames.any { trackArtist -> trackArtist.equals(foundArtist, true) } }

                if (isSameName && isSameArtist) {
                    it
                } else {
                    serverCandidateTracks.add(it)
                    null
                }
            }
        }

        val serverFoundTrackOnNameAlbumAndArtistSearch = searchTrackByNameArtistAndAlbum(externalTrack, encodedExternalTrackName, encodedExternalTrackAlbumName)
        getOrAddAsCandidate(serverFoundTrackOnNameAlbumAndArtistSearch)?.let { return it }

        val serverFoundTrackOnNameAndAlbumSearch = searchTrackByNameAndAlbum(externalTrack, encodedExternalTrackName, encodedExternalTrackAlbumName)
        getOrAddAsCandidate(serverFoundTrackOnNameAndAlbumSearch)?.let { return it }

        val serverFoundTrackOnNameAndArtistSearch = searchTrackByNameAndArtist(externalTrack, encodedExternalTrackName)
        getOrAddAsCandidate(serverFoundTrackOnNameAndArtistSearch)?.let { return it }

        val serverFoundTrackOnNameSearch = searchTrackByName(externalTrack, encodedExternalTrackName)
        getOrAddAsCandidate(serverFoundTrackOnNameSearch)?.let { return it }

        return getRightCandidateTrack(serverCandidateTracks, externalTrack)
    }

    private fun getRightCandidateTrack(serverCandidateTracks: MutableSet<SpotifyTrack>, externalTrack: Track): SpotifyTrack? {
        if (serverCandidateTracks.size == 1) return serverCandidateTracks.first()

        val isRemix = externalTrack.name.contains(REMIX, true)

        fun isAnyOfArtistsTheSame(serverCandidateTrack: SpotifyTrack, isExatchArtistMatch: Boolean): Boolean {
            val serverTrackArtistsNames = serverCandidateTrack.artists.map { it.name }

            return serverTrackArtistsNames.any { serverTrackArtistName ->
                externalTrack.artists.any { externalTrackArtist ->
                    if (isExatchArtistMatch) {
                        serverTrackArtistName.equals(externalTrackArtist.name, true)
                    } else {
                        serverTrackArtistName.contains(externalTrackArtist.name, true) ||
                        externalTrackArtist.name.contains(serverTrackArtistName, true)
                    }
                }
            }
        }

        return if (isRemix) {
            serverCandidateTracks.firstOrNull { serverCandidateTrack ->
                val serverTrackName = serverCandidateTrack.title
                val externalTrackArtistsNames = externalTrack.artists.map { it.name }

                externalTrackArtistsNames.any { serverTrackName.contains(it, true) }
            }
        } else {
            null
        }
            ?:
        serverCandidateTracks.filter {
            isAnyOfArtistsTheSame(it, isExatchArtistMatch = true)
        }.ifEmpty {
            serverCandidateTracks.filter {
                isAnyOfArtistsTheSame(it, isExatchArtistMatch = false)
            }
        }.let { serverCandidateTracksFiltered ->
            serverCandidateTracksFiltered.firstOrNull { it.album.title.equals(externalTrack.albumName, true) }
                ?:
            serverCandidateTracksFiltered.maxByOrNull { it.popularity }
        }
    }

    override fun searchTrackByNameArtistAndAlbum(externalTrack: Track, encodedTrackName: String, encodedServerTrackAlbumName: String): SpotifyTrack? {
        return externalTrack.artists.firstNotNullOfOrNull {
            val externalTrackArtistName = URLEncoder.encode(it.name, UTF_8)

            val serverFoundTracksResult = spotifySearchTrackURL(
                encodedTrackName, currentToken, givenAlbumName = encodedServerTrackAlbumName, givenArtistName = externalTrackArtistName
            ).getURLResponse<SpotifyFoundTracksResult>()

            getTrack(serverFoundTracksResult, externalTrack).first
        }
    }

    override fun searchTrackByNameAndAlbum(externalTrack: Track, encodedTrackName: String, encodedServerTrackAlbumName: String): SpotifyTrack? {
        val serverFoundTracksResult = spotifySearchTrackURL(
            encodedTrackName, currentToken, givenAlbumName = encodedServerTrackAlbumName
        ).getURLResponse<SpotifyFoundTracksResult>()

        return getTrack(serverFoundTracksResult, externalTrack).first
    }

    override fun searchTrackByNameAndArtist(externalTrack: Track, encodedTrackName: String): SpotifyTrack? {
        fun doSearch(considerSameAlbum: Boolean): SpotifyTrack? {
            return externalTrack.artists.firstNotNullOfOrNull {
                val externalTrackArtistName = URLEncoder.encode(it.name, UTF_8)

                val serverFoundTracksResult = spotifySearchTrackURL(
                    encodedTrackName, currentToken, givenArtistName = externalTrackArtistName
                ).getURLResponse<SpotifyFoundTracksResult>()

                getTrack(serverFoundTracksResult, externalTrack, considerSameAlbum).first
            }
        }

        return doSearch(considerSameAlbum = true) ?: doSearch(considerSameAlbum = false)
    }

    override fun searchTrackByName(externalTrack: Track, encodedTrackName: String): SpotifyTrack? {
        val serverCandidateTracks = mutableListOf<SpotifyTrack>()

        var serverCurrent50Tracks = spotifySearchTrackURL(encodedTrackName, currentToken).getURLResponse<SpotifyFoundTracksResult>()
        var serverTargetTrack: SpotifyTrack? = null
        while (serverTargetTrack == null) {
            val (serverFoundTrack, next50Tracks) = getTrack(serverCurrent50Tracks, externalTrack, considerSameAlbum = false).let {
                it.first to it.second.next
            }

            UI.addInProgressDots()

            if (serverFoundTrack != null) {
                if (serverFoundTrack.title == externalTrack.name) {
                    serverTargetTrack = serverFoundTrack
                } else {
                    serverCandidateTracks.add(serverFoundTrack)
                }
            }

            if (serverTargetTrack == null) {
                if (next50Tracks == null) {
                    serverTargetTrack = serverCandidateTracks.maxByOrNull { it.popularity }
                    break
                }

                val request = HttpRequest.get(URLPlusToken(next50Tracks, currentToken, isFirstParameterOfUrl = false))
                if (request.code() != 404) {
                    serverCurrent50Tracks = request.getRequestResponse<SpotifyFoundTracksResult>()
                } else {
                    serverTargetTrack = serverCandidateTracks.maxByOrNull { it.popularity }
                    break
                }
            }
        }

        return serverTargetTrack
    }

    private fun getTrack(serverFoundTracksResult: SpotifyFoundTracksResult, externalTrack: Track, considerSameAlbum: Boolean = true): Pair<SpotifyTrack?, SpotifyFoundTracksWithoutTrackItem> {
        val serverFoundTracksObject = serverFoundTracksResult.tracks
        val serverFoundTracks = serverFoundTracksObject.items
            .ifEmpty {
                URLPlusToken(serverFoundTracksObject.href, currentToken, isFirstParameterOfUrl = false).let {
                    redoQueryIfHasProblematicWords(it, Apps.SPOTIFY).first
                }
            }.sortedByDescending { it.title.equals(externalTrack.name, true) }
            as List<SpotifyTrack>

        return findTrackWithSameArtistAndAlbum(serverFoundTracks, externalTrack, considerSameAlbum) to serverFoundTracksObject
    }

    private fun findTrackWithSameArtistAndAlbum(serverFoundTracks: List<SpotifyTrack>, externalTrack: Track, considerSameAlbum: Boolean): SpotifyTrack? {
        val isRemix by lazy {  externalTrack.name.contains(REMIX, true) }
        val externalTrackAlbumName by lazy { externalTrack.albumName.trim() }
        val externalTrackAlbumNameWithoutProblematicWords by lazy { getStringWithoutProblematicWords(externalTrack.albumName, isToEncode = false).trim() }

        return serverFoundTracks.firstOrNull { serverFoundTrack ->
            if (considerSameAlbum) {
                isSameAlbum(serverFoundTrack, externalTrackAlbumName, externalTrackAlbumNameWithoutProblematicWords)
            } else {
                true
            }
                    &&
            isSameArtist(serverFoundTrack.artists, serverFoundTrack, externalTrack, isRemix)
        }
    }

    private fun isSameAlbum(serverFoundTrack: SpotifyTrack, currentTrackAlbumName: String, currentTrackAlbumNameWithoutProblematicWords: String): Boolean {
        val serverFoundTrackAlbumName = serverFoundTrack.album.title.trim()
        val serverFoundTrackAlbumNameWithoutProblematicWords by lazy { getStringWithoutProblematicWords(serverFoundTrackAlbumName, isToEncode = false).trim() }

        return serverFoundTrackAlbumName.equals(currentTrackAlbumName, true) ||
        serverFoundTrackAlbumName.equals(currentTrackAlbumNameWithoutProblematicWords, true) ||
        serverFoundTrackAlbumNameWithoutProblematicWords.equals(currentTrackAlbumName, true) ||
        serverFoundTrackAlbumNameWithoutProblematicWords.equals(currentTrackAlbumNameWithoutProblematicWords, true)
    }

    private fun isSameArtist(serverFoundTrackArtists: List<SpotifyArtist>, serverFoundTrack: SpotifyTrack, externalTrack: Track, isRemix: Boolean): Boolean {
        return serverFoundTrackArtists.any { serverFoundTrackArtist ->
            val artistName = serverFoundTrackArtist.name

            externalTrack.artists.any { it.name.contains(artistName, true) || artistName.contains(it.name, true) } ||
            isRemix && serverFoundTrack.title.contains(artistName, true)
        }
    }
}
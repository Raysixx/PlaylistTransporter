package importer

import com.github.kevinsawicki.http.HttpRequest
import org.json.JSONObject

@Suppress("UNCHECKED_CAST")
data class Playlist(val title: String, val id: Long) {
    val tracks = mutableListOf<Track>()

    companion object {
        private const val DATA = "data"
        private const val ARTIST = "artist"
        private const val NAME = "name"
        private const val TITLE = "title"
        private const val ID = "id"
        private const val TRACKLIST = "tracklist"
        private const val NEXT = "next"
        private const val IMPORTING_PLAYLIST = "Importando playlist"

        fun getPlaylistsFromJson(json: JSONObject): List<Playlist> {
            val jsonMap = json.toMap()

            val rawPlaylists = jsonMap[DATA] as List<HashMap<*, *>>
            return rawPlaylists.mapNotNull { rawPlaylist ->
                val playlistTitleAndId = rawPlaylist.filter { (metaDataName, _) ->
                    metaDataName in listOf(TITLE, ID)
                }.values

                val title = playlistTitleAndId.first().toString()
                val id = playlistTitleAndId.last().toString().toLong()

                if (playlistToImport.isNotEmpty() && title.uppercase() !in playlistToImport) {
                    return@mapNotNull null
                }

                App.updateMessage("$IMPORTING_PLAYLIST $title")

                val playlistRawTracks = rawPlaylist[TRACKLIST]
                    .let { HttpRequest.get(urlPlusToken(it.toString())).body() as String }
                    .let { JSONObject(it) }

                Playlist(title, id)
                    .also { playlist -> playlist.transformJsonIntoTracks(playlistRawTracks).let { trackList -> playlist.tracks.addAll(trackList) } }
            }
        }
    }

    private fun transformJsonIntoTracks(playlistRawTracks: JSONObject): List<Track> {
        val allTracks = mutableListOf<Track>()

        var current25Tracks = playlistRawTracks.toMap()
        while (current25Tracks != null) {
            val rawTracks = current25Tracks[DATA] as List<HashMap<*, *>>

            val currentTracks = rawTracks.map { rawTrack ->
                val trackArtistAndTrackName = rawTrack.filter { (metadataName, _) ->
                    metadataName in listOf(ARTIST, TITLE)
                }.values.map {
                    if (it is HashMap<*, *>) {
                        it[NAME]
                    } else {
                        it
                    }
                }.let { it.first() to it.last() }

                val artist = trackArtistAndTrackName.first.toString()
                val trackName = trackArtistAndTrackName.second.toString()

                Track(artist, trackName)
                    .also { App.updateMessage("$IMPORTING_PLAYLIST $title <br>" +
                        "   ${it.artist} - ${it.name}") }
            }
            allTracks.addAll(currentTracks)

            val next25Tracks = current25Tracks[NEXT]
                ?.let { HttpRequest.get(it as String).body() as String }
                ?.let { JSONObject(it).toMap() }

            current25Tracks = next25Tracks
        }

        return allTracks
    }
}

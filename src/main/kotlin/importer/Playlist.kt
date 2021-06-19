package importer

import com.github.kevinsawicki.http.HttpRequest
import org.json.JSONObject

data class Playlist(val title: String, val id: Long) {
    val tracks = mutableListOf<Track>()

    fun transformJsonIntoTracks(playlistRawTracks: JSONObject): List<Track> {
        val allTracks = mutableListOf<Track>()

        var current25Tracks = playlistRawTracks.toMap()
        while (current25Tracks != null) {
            val rawTracks = current25Tracks.entries.first { it.key == "data" }.value as List<HashMap<*, *>>

            val currentTracks = rawTracks.map { rawTrack ->
                val trackArtistAndTrackName = rawTrack.filter { (metadataName, _) ->
                    metadataName in listOf("artist", "title")
                }.values.map {
                    if (it is HashMap<*, *>) {
                        it.entries.first { (artistMetadata, _) -> artistMetadata == "name" }.value
                    } else {
                        it
                    }
                }.let { it.first() to it.last() }

                val artist = trackArtistAndTrackName.first.toString()
                val trackName = trackArtistAndTrackName.second.toString()

                Track(artist, trackName)
                    .also { AppMessage.updateMessage("Importando playlist $title <br>" +
                        "   ${it.artist} - ${it.name}") }
            }
            allTracks.addAll(currentTracks)

            val next25Tracks = current25Tracks.entries.firstOrNull { it.key == "next" }?.value
                ?.let { HttpRequest.get(it as String).body() as String }
                ?.let { JSONObject(it).toMap() }

            current25Tracks = next25Tracks
        }

        return allTracks
    }
}

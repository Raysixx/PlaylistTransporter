package model

import app.App
import client.Apps
import client.currentAction
import com.github.kevinsawicki.http.HttpRequest
import org.json.JSONObject
import java.net.URLEncoder

@Suppress("PropertyName", "UNCHECKED_CAST", "ControlFlowWithEmptyBody")
open class Utils {

    enum class PossiblyProblematicWordsToBeOnSearches(val word: Any) {
//        ORIGINAL_MIX("(Original Mix)"),
//        EP_VERSION("(EP Version)"),
//        FEAT("\\(feat. .*\\)".toRegex())
        BRACES("\\(.*\\)".toRegex())
    }

    companion object {
        const val CODE = "code"
        const val DATA = "data"
        const val ARTIST = "artist"
        const val ARTISTS = "artists"
        const val NAME = "name"
        const val TITLE = "title"
        const val ID = "id"
        const val TRACKLIST = "tracklist"
        const val TRACK = "track"
        const val NEXT = "next"
        const val ITEMS = "items"
        const val COUNTRY = "country"
        const val READABLE = "readable"
        const val CONTRIBUTORS = "contributors"
        const val POPULARITY = "popularity"
        const val HREF = "href"

        const val SEARCHING_ON_SERVER = "Procurando no servidor"

        const val FROM_SPOTIFY = "do Spotify"
        const val FROM_DEEZER = "do Deezer"

        const val IMPORT = "importar"
        const val EXPORT = "exportar"

        const val IMPORTING = "Importando"
        const val EXPORTING = "Exportando"

        const val IMPORTING_PLAYLIST = "$IMPORTING playlist"
        const val EXPORTING_PLAYLIST = "$EXPORTING playlist"

        const val ACCESS_TOKEN = "access_token"

        const val UTF_8 = "UTF-8"

        fun getCode(rawURL: String) = rawURL.substring(rawURL.lastIndexOf("${CODE}=") + 5, rawURL.lastIndex + 1)

        @Suppress("FunctionName")
        fun URLPlusToken(url: String, currentToken: String?, isFirstParameterOfUrl: Boolean = true): String {
            val symbol = if (isFirstParameterOfUrl) "?" else "&"
            return "${url}${symbol}access_token=${currentToken}"
        }

        fun redoQueryIfHasProblematicWords(url: String, app: Apps): List<HashMap<String, *>> {
            val problematicWordsThatURLContains = PossiblyProblematicWordsToBeOnSearches.values().map {
                when (it.word) {
                    is String -> URLEncoder.encode(it.word, UTF_8)
                    is Regex -> {
                        val regexPattern = it.word.pattern.replace("\\", "")
                        val newRegex = URLEncoder.encode(regexPattern, UTF_8).toRegex()
                        newRegex.find(url)?.value ?: ""
                    }
                    else -> ""
                }
            }.filter {
                url.contains(it)
            }

            return if (problematicWordsThatURLContains.isNotEmpty()) {
                var targetUrl = url
                problematicWordsThatURLContains.forEach {
                    targetUrl = targetUrl.replace(it, "")
                }

                val rawResponse = targetUrl.getURLResponse()

                when (app.name) {
                    Apps.DEEZER.name -> deezerRedoQueryWithoutProblematicWord(rawResponse)
                    Apps.SPOTIFY.name -> spotifyRedoQueryWithoutProblematicWord(rawResponse)
                    else -> emptyList()
                }
            } else {
                emptyList()
            }
        }

        private fun deezerRedoQueryWithoutProblematicWord(response: HashMap<String, *>): List<HashMap<String, *>> {
            return response[DATA] as List<HashMap<String, *>>
        }

        private fun spotifyRedoQueryWithoutProblematicWord(response: HashMap<String, *>): List<HashMap<String, *>> {
            val foundTracksObject = response.entries.first().value as HashMap<String, *>

            return foundTracksObject[ITEMS] as List<HashMap<String, *>>
        }

        fun HttpRequest.getRequestResponse(): HashMap<String, *> {
            val requestBody = this.body() as String
            val responseJson = JSONObject(requestBody)

            return responseJson.toMap() as HashMap<String, *>
        }

        fun String.getURLResponse(): HashMap<String, *> {
            return HttpRequest.get(this).getRequestResponse()
        }

        fun String.doURLPostWith(body: String): HashMap<String, *> {
            val response = HttpRequest.post(this).send(body).body() as String
            val responseJson = JSONObject(response)

            return responseJson.toMap() as HashMap<String, *>
        }

        fun waitForCurrentActionDefinition() {
            while (currentAction == null) {}
        }

        fun waitForAppFinish(app: App) {
            while (app.isRunning) {}
        }

        fun comparePlaylistsTracks(firstPlaylist: Playlist, secondPlaylist: Playlist): List<String> {
            val tracksOfFirstPlaylist = firstPlaylist.tracks.filter { it.isAvailable }
            val tracksOfSecondPlaylist = secondPlaylist.tracks.filter { it.isAvailable }

            val biggerTracklist = listOf(tracksOfFirstPlaylist, tracksOfSecondPlaylist).maxByOrNull { it.size }!!
            val otherTracklist = listOf(tracksOfFirstPlaylist, tracksOfSecondPlaylist).first { it != biggerTracklist }

            return biggerTracklist.filter { track1 ->
                val track1Name = track1.name.replace("(", "").replace(")", "").replace("- ", "").replace("& ", "")

                otherTracklist.none { track2 ->
                    val track2Name = track2.name.replace("(", "").replace(")", "").replace("- ", "").replace("& ", "")

                    track1Name.contains(track2Name, true) || track2Name.contains(track1Name, true)
                }
            }.map { "${it.artist.name} --- ${it.name}" }
        }
    }
}
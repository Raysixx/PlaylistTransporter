package client

import com.github.kevinsawicki.http.HttpRequest
import model.Artist
import model.Playlist
import org.json.JSONObject
import java.net.URLEncoder

@Suppress("PropertyName", "UNCHECKED_CAST", "ControlFlowWithEmptyBody", "RegExpRedundantEscape")
open class Utils {

    enum class PossiblyProblematicWordsToBeOnSearches(val word: Any) {
//        ORIGINAL_MIX("(Original Mix)"),
//        EP_VERSION("(EP Version)"),
//        FEAT("\\(feat. .*\\)".toRegex())
        PARENTHESIS("\\(.*\\)".toRegex()),
        BRACKETS("\\[.*\\]".toRegex())
    }

    enum class PossiblyProlematicSymbols(val symbol: String) {
        OPEN_PARENTHESIS("("),
        CLOSE_PARENTHESIS(")"),
        HIFEN(" -")
    }

    companion object {
        const val CODE = "code"
        const val DATA = "data"
        const val ARTIST = "artist"
        const val ARTISTS = "artists"
        const val NAME = "name"
        const val TITLE = "title"
        const val ALBUM = "album"
        const val ID = "id"
        const val TRACKLIST = "tracklist"
        const val TRACK = "track"
        const val TRACKS = "tracks"
        const val NEXT = "next"
        const val ITEMS = "items"
        const val COUNTRY = "country"
        const val READABLE = "readable"
        const val CONTRIBUTORS = "contributors"
        const val POPULARITY = "popularity"
        const val RANK = "rank"
        const val HREF = "href"
        const val AVAILABLE_MARKETS = "available_markets"
        const val REMIX = "remix"

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

        fun getCode(rawURL: String) = rawURL.substring(rawURL.lastIndexOf("$CODE=") + 5, rawURL.lastIndex + 1)

        @Suppress("FunctionName")
        fun URLPlusToken(url: String, currentToken: String?, isFirstParameterOfUrl: Boolean = true): String {
            val symbol = if (isFirstParameterOfUrl) "?" else "&"
            return "${url}${symbol}access_token=${currentToken}"
        }

        fun redoQueryIfHasProblematicWords(url: String, app: Apps): Pair<List<HashMap<String, *>>, String?> {
            val targetUrl = getStringWithoutProblematicWords(url)

            return if (targetUrl != url) {
                val rawResponse = targetUrl.getURLResponse()

                when (app.name) {
                    Apps.DEEZER.name -> deezerRedoQueryWithoutProblematicWord(rawResponse)
                    Apps.SPOTIFY.name -> spotifyRedoQueryWithoutProblematicWord(rawResponse)
                    else -> emptyList<HashMap<String, *>>() to null
                }
            } else {
                emptyList<HashMap<String, *>>() to null
            }
        }

        private fun deezerRedoQueryWithoutProblematicWord(response: HashMap<String, *>): Pair<List<HashMap<String, *>>, String?> {
            return response[DATA] as List<HashMap<String, *>> to response[NEXT] as String?
        }

        private fun spotifyRedoQueryWithoutProblematicWord(response: HashMap<String, *>): Pair<List<HashMap<String, *>>, String?> {
            val foundTracksObject = response.entries.first().value as HashMap<String, *>

            return foundTracksObject[ITEMS] as List<HashMap<String, *>> to null
        }

        fun getStringWithoutProblematicWords(string: String, isToEncode: Boolean = true): String {
            val problematicWordsThatStringContains = getProblematicWordsThatStringContains(string, isToEncode)

            return if (problematicWordsThatStringContains.isNotEmpty()) {
                problematicWordsThatStringContains.fold(string) { acc, problematicWord ->
                    acc.replace(problematicWord, "")
                }
            } else {
                string
            }
        }

        private fun getProblematicWordsThatStringContains(string: String, isToEncode: Boolean): List<String> {
            return PossiblyProblematicWordsToBeOnSearches.values().map {
                when (it.word) {
                    is String -> URLEncoder.encode(it.word, UTF_8)
                    is Regex -> {
                        val regex = if (isToEncode) {
                            val regexPattern = it.word.pattern.replace("\\", "")
                            URLEncoder.encode(regexPattern, UTF_8).toRegex()
                        } else {
                            it.word
                        }
                        regex.find(string)?.value ?: ""
                    }
                    else -> ""
                }
            }.filter {
                string.contains(it)
            }
        }

        fun String.withoutProblematicSymbols(): String {
            return PossiblyProlematicSymbols.values().map { it.symbol }.fold(this) { acc, symbol ->
                acc.replace(symbol, "")
            }
        }

        /**
         * It considers that the string has all trackName, artistName and albumName in the respective order
         */
        fun String.withoutQuotes(onlyTrackName: Boolean = false, onlyArtistName: Boolean = false, onlyAlbumName: Boolean = false): String {
            fun String.getTrackIndex() = this.indexOf("track:")
            fun String.getArtistIndex() = this.indexOf("artist:")
            fun String.getAlbumIndex() = this.indexOf("album:")

            val symbol = if (this.contains("\"")) "\"" else "%22"

            fun replaceTrack(string: String): String {
                return try {
                    string.substring(string.getTrackIndex(), string.getArtistIndex()).replace(symbol, "").let { string.replaceRange(string.getTrackIndex(), string.getArtistIndex(), it) }
                } catch (e: Exception) {
                    return try {
                        string.substring(string.getTrackIndex(), string.getAlbumIndex()).replace(symbol, "").let { string.replaceRange(string.getTrackIndex(), string.getAlbumIndex(), it) }
                    } catch (e: Exception) {
                        string.substring(string.getTrackIndex()).replace(symbol, "").let { string.replaceRange(string.getTrackIndex(), string.length, it) }
                    }
                }
            }
            fun replaceArtist(string: String) = string.substring(string.getArtistIndex(), string.getAlbumIndex()).replace(symbol, "").let { string.replaceRange(string.getArtistIndex(), string.getAlbumIndex(), it) }
            fun replaceAlbum(string: String) = string.substring(string.getAlbumIndex(), string.length).replace(symbol, "").let { string.replaceRange(string.getAlbumIndex(), string.length, it) }
            fun replaceTrackAndArtist(string: String) = string.substring(string.getTrackIndex(), string.getAlbumIndex()).replace(symbol, "").let { string.replaceRange(string.getTrackIndex(), string.getAlbumIndex(), it) }
            fun replaceArtistAndAlbum(string: String) = string.substring(string.getArtistIndex(), string.length).replace(symbol, "").let { string.replaceRange(string.getArtistIndex(), string.length, it) }

            return when {
                onlyTrackName && onlyArtistName && onlyAlbumName -> this.replace(symbol, "")
                onlyTrackName && !onlyArtistName && !onlyAlbumName-> replaceTrack(this)
                onlyTrackName && onlyArtistName && !onlyAlbumName -> replaceTrackAndArtist(this)
                onlyTrackName && !onlyArtistName && onlyAlbumName -> {
                    var newString = this
                    newString = replaceTrack(newString)
                    newString = replaceAlbum(newString)
                    return newString
                }
                !onlyTrackName && onlyArtistName && !onlyAlbumName -> replaceArtist(this)
                !onlyTrackName && onlyArtistName && onlyAlbumName -> replaceArtistAndAlbum(this)
                !onlyTrackName && !onlyArtistName && onlyAlbumName -> replaceAlbum(this)
                else -> this
            }
        }

        fun HttpRequest.getRequestResponse(): HashMap<String, *> {
            val requestBody = this.body() as String

            val responseJson = try {
                JSONObject(requestBody)
            } catch (exception: Exception) {
                throw Exception(requestBody)
            }

            return responseJson.toMap() as HashMap<String, *>
        }

        fun String.getURLResponse(): HashMap<String, *> {
            return HttpRequest.get(this).getRequestResponse()
        }

        fun String.doURLPostWith(body: String, getResponse: Boolean = false): HashMap<String, *> {
            val response = HttpRequest.post(this).send(body).body() as String

            if (!getResponse) {
                return hashMapOf<String, Any>()
            }

            val responseJson = try {
                JSONObject(response)
            } catch (exception: Exception) {
                throw Exception(response)
            }

            return responseJson.toMap() as HashMap<String, *>
        }

        fun String.doURLPost(getResponse: Boolean = false): HashMap<String, *> {
            val response = HttpRequest.post(this).send("").body() as String

            if (!getResponse) {
                return hashMapOf<String, Any>()
            }

            val responseJson = try {
                JSONObject(response)
            } catch (exception: Exception) {
                throw Exception(response)
            }

            return responseJson.toMap() as HashMap<String, *>
        }

        fun String.removeWindowsInvalidCharacters(): String {
            return this.replace("[\\\\/:*?\"<>|]".toRegex(), "")
        }

        fun waitForCurrentActionDefinition() {
            while (currentAction == null) {}
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
            }.map { "${Artist.getArtistsNames(it.artists)} --- ${it.name}" }
        }
    }
}
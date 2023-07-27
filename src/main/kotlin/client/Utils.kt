package client

import app.apps.deezer.DeezerFoundTracks
import app.apps.deezer.DeezerTrack
import app.apps.spotify.SpotifyFoundTracksResult
import app.apps.spotify.SpotifyFoundTracksWithTrackItem
import app.apps.spotify.SpotifyFoundTracksWithoutTrackItem
import app.apps.spotify.SpotifyTrack
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kevinsawicki.http.HttpRequest
import model.Artist
import model.JsonTrack
import model.Playlist
import java.net.URLEncoder

@Suppress("ControlFlowWithEmptyBody", "RegExpRedundantEscape")
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
        const val TRACK = "track"
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

        const val UTF_8 = "UTF-8"

        fun getCode(rawURL: String) = rawURL.substring(rawURL.lastIndexOf("$CODE=") + 5, rawURL.lastIndex + 1)

        @Suppress("FunctionName")
        fun URLPlusToken(url: String, currentToken: String?, isFirstParameterOfUrl: Boolean = true): String {
            val symbol = if (isFirstParameterOfUrl) "?" else "&"
            return "${url}${symbol}access_token=${currentToken}"
        }

        fun redoQueryIfHasProblematicWords(url: String, app: Apps): Pair<List<JsonTrack>, String?> {
            val targetUrl = getStringWithoutProblematicWords(url)

            return if (targetUrl != url) {
                when (app.name) {
                    Apps.DEEZER.name -> targetUrl.getURLResponse<DeezerFoundTracks>().let { it.data to it.next }
                    Apps.SPOTIFY.name -> targetUrl.getURLResponse<SpotifyFoundTracksResult>().tracks.items to null
                    else -> emptyList<JsonTrack>() to null
                }
            } else {
                emptyList<JsonTrack>() to null
            }
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

        inline fun <reified T> String.getURLResponse() = HttpRequest.get(this).getRequestResponse<T>()
        inline fun <reified T> HttpRequest.getRequestResponse() = jacksonObjectMapper().readValue<T>(body())

        fun String.doURLPostWith(body: String) { HttpRequest.post(this).send(body).body() }
        inline fun <reified T> String.doURLPostWith(body: String) = HttpRequest.post(this).send(body).body().let {
                jacksonObjectMapper().readValue<T>(it)
            }

        fun String.doURLPost() { HttpRequest.post(this).send("").body() }
        inline fun <reified T> String.doURLPost() = HttpRequest.post(this).send("").body().let {
            jacksonObjectMapper().readValue<T>(it)
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
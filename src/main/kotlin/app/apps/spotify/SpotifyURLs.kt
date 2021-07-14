package app.apps.spotify

import client.Utils
import client.Utils.Companion.URLPlusToken

enum class SpotifyScopes(val officialName: String) {
    READ_PRIVATE_PLAYLISTS("playlist-read-private"),
    READ_COLLABORATIVE_PLAYLISTS("playlist-read-collaborative"),
    MODIFY_PUBLIC_PLAYLISTS("playlist-modify-public"),
    MODIFY_PRIVATE_PLAYLISTS("playlist-modify-private"),
    USER_READ_PRIVATE("user-read-private")
}

fun spotifyAuthenticationURL() = "https://accounts.spotify.com/authorize?client_id=${SpotifyApp().appId}&response_type=code&redirect_uri=${SpotifyApp().redirectUri}&scope=${SpotifyApp().scopes}&show_dialog=true"

fun spotifyGetUserPlaylistsURL(currentToken: String?) = "${URLPlusToken("${spotifyGetUserURL()}/playlists", currentToken)}&limit=50"

fun spotifyGetTempTokenURL() = "https://accounts.spotify.com/api/token"
fun spotifyGetTempTokenPostBodyTemplate(code: String) = "grant_type=authorization_code&code=${code}&redirect_uri=${SpotifyApp().redirectUri}&client_id=${SpotifyApp().appId}&client_secret=${SpotifyApp().secretKey}"

fun spotifyCreatePlaylistURL(userId: String, currentToken: String?) = URLPlusToken("https://api.spotify.com/v1/users/$userId/playlists", currentToken)
fun spotifyCreatePlaylistPostBodyTemplate(playlistTitle: String) = "{ \"name\": \"$playlistTitle\",\"public\": \"false\" }"

fun spotifyAddTrackURL(playlistId: Any, currentToken: String?) = "${URLPlusToken("https://api.spotify.com/v1/playlists/$playlistId/tracks", currentToken)}&content-type=application/json"
fun spotifyAddTrackPostBodyTemplate(tracksIds: List<String>) = "{\"uris\": [\"spotify:track:${tracksIds.reduce { acc, s -> "$acc\",\"spotify:track:$s" }}\"]}"

fun spotifyGetUserURL(givenUserId: String? = null): String {
    val userId = givenUserId ?: "me"
    return "https://api.spotify.com/v1/$userId"
}

fun spotifySearchURL(keyword: String, whatToSearch: String, currentToken: String?, vararg fieldFilter: String = arrayOf("")): String {
    val filters = fieldFilter.reduce { acc, s -> "$acc$s" }
    return "${URLPlusToken("https://api.spotify.com/v1/search", currentToken)}&q=${keyword}${filters}&type=${whatToSearch}&limit=50"
}

fun spotifySearchTrackURL(name: String, currentToken: String?, givenArtistName: String? = null, givenAlbumName: String? = null): String {
    val album = if (givenAlbumName == null) "" else "%20album:$givenAlbumName"
    val artist = if (givenArtistName == null) "" else "%20artist:$givenArtistName"
    return spotifySearchURL(name, Utils.TRACK, currentToken, artist, album)
}
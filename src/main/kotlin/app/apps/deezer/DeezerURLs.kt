package app.apps.deezer

import client.Utils.Companion.URLPlusToken

enum class DeezerScopes(val officialName: String) {
    BASIC_ACCESS("basic_access"),
    MANAGE_LIBRARY("manage_library")
}

fun deezerAuthenticationURL() = "https://connect.deezer.com/oauth/auth.php?app_id=${DeezerApp().appId}&redirect_uri=${DeezerApp().redirectUri}&perms=${DeezerApp().scopes}"
fun deezerGetTempTokenURL(code: String) = "https://connect.deezer.com/oauth/access_token.php?app_id=${DeezerApp().appId}&secret=${DeezerApp().secretKey}&code=$code"
fun deezerGetUserPlaylistsURL(currentToken: String?) = URLPlusToken("${deezerGetUserURL()}/playlists", currentToken)
fun deezerGetTrackByIdURL(trackId: String) = "https://api.deezer.com/track/$trackId"
fun deezerGetArtistURL(artistId: String) = "https://api.deezer.com/artist/$artistId"

fun deezerGetUserURL(givenUserId: String? = null): String {
    val userId = givenUserId ?: "me"
    return "https://api.deezer.com/user/$userId"
}

fun deezerCreatePlaylistURL(userId: String, playlistTitle: String, currentToken: String?) =
    "${URLPlusToken("${deezerGetUserURL(userId)}/playlists", currentToken)}&title=$playlistTitle"

fun deezerAddTrackURL(playlistId: String, tracksIds: String, currentToken: String?) =
    "${URLPlusToken("https://api.deezer.com/playlist/$playlistId/tracks", currentToken)}&songs=$tracksIds"

fun deezerSearchURL(keyword: String) = "https://api.deezer.com/search?q=$keyword"

fun deezerSearchTrackURL(givenTrackName: String, givenAlbumName: String? = null, givenArtistName: String? = null): String {
    val trackName = "\"$givenTrackName\""
    val artistName = if (givenArtistName == null) "" else "%20artist:\"$givenArtistName\""
    val albumName = if (givenAlbumName == null) "" else "%20album:\"$givenAlbumName\""
    return deezerSearchURL("track:$trackName$artistName$albumName")
}

fun deezerSearchArtistURL(artistName: String) = "https://api.deezer.com/search/artist/?q=artist:\"$artistName\""
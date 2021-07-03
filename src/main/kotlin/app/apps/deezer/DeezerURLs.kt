package app.apps.deezer

import model.Utils.Companion.URLPlusToken

fun deezerAuthenticationURL() = "https://connect.deezer.com/oauth/auth.php?app_id=${DeezerApp().appId}&redirect_uri=${DeezerApp().redirectUri}&perms=basic_access,email"
fun deezerGetTempTokenURL(code: String) = "https://connect.deezer.com/oauth/access_token.php?app_id=${DeezerApp().appId}&secret=${DeezerApp().secretKey}&code=$code"
fun deezerGetUserPlaylistsURL(currentToken: String?) = URLPlusToken("${deezerGetUserURL()}/playlists", currentToken)
fun deezerGetTrackByIdURL(trackId: String) = "https://api.deezer.com/track/$trackId"
fun deezerGetArtistURL(artistId: String) = "https://api.deezer.com/artist/$artistId"

fun deezerGetUserURL(givenUserId: String? = null): String {
    val userId = givenUserId ?: "me"
    return "https://api.deezer.com/user/$userId"
}

fun deezerSearchURL(keyword: String) = "https://api.deezer.com/search?q=$keyword"

fun deezerSearchTrackByNameURL(trackName: String, givenArtistName: String? = null): String {
    val artistName = if (givenArtistName == null) "" else "%20artist:\"$givenArtistName\""
    return deezerSearchURL("track:\"$trackName\"$artistName")
}
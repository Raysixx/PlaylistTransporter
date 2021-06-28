package deezer

fun deezerAuthenticationURL(appId: String, redirect_uri: String) = "https://connect.deezer.com/oauth/auth.php?app_id=${appId}&redirect_uri=${redirect_uri}&perms=basic_access,email"
fun deezerGetTempTokenURL(appId: String, secretKey: String, code: String) = "https://connect.deezer.com/oauth/access_token.php?app_id=${appId}&secret=${secretKey}&code=$code"
fun deezerUserPlaylistsURL(currentToken: String?) = "https://api.deezer.com/user/me/playlists&access_token=$currentToken"
fun urlPlusToken(currentToken: String?, url: String) = "${url}&access_token=$currentToken"
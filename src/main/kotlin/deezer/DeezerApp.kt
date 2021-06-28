package deezer

import app.AppInterface

object DeezerApp: AppInterface {
    override val appId = "487462"
    override val secretKey = "6194eef1df27977560fe0b2ddc8e8b5b"
    override val redirectUri = "http://localhost:5000"
}

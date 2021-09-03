package app.apps.file

import app.App

@Suppress("EnumEntryName")
open class FileApp: App() {

    @Volatile override var isRunning: Boolean = false

    companion object {
        enum class SupportedExtensions(val header: String = "") {
            txt,
            csv("$playlist$separator$track$separator$artist$separator$album$separator$isrc$separator$available")
        }

        const val waterMark = "Created by PlaylistTransporter"

        const val availableFlag = "D"
        const val notAvailableFlag = "ND"

        const val playlist = "Playlist"
        const val track = "Track"
        const val artist = "Artista"
        const val album = "Album"
        const val isrc = "isrc"
        const val available = "Disponivel"
        const val separator = ";"
    }

    override val appId: String = ""
    override val secretKey: String = ""
    override val redirectUri: String = ""
    override var currentCountry: String? = null

    override fun fillCurrentCountry(currentToken: String) {}
    override fun getToken(urlRedirected: String): String = ""

    override val scopes: String = ""
}
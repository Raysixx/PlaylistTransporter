package app

interface AppInterface {

    enum class Operation {
        IMPORT,
        EXPORT
    }

    var isRunning: Boolean

    val appId: String
    val secretKey: String
    val redirectUri: String

    var operation: Operation?

    var currentCountry: String?
    fun fillCurrentCountry(currentToken: String)

    fun generateToken()
    fun getToken(urlRedirected: String): String
    fun fillToken(token: String)
}
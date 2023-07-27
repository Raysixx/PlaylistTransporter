package app

import client.Utils
import model.JsonUser

interface AppInterface {

    enum class Operation(val message: String) {
        IMPORT(Utils.IMPORT),
        EXPORT(Utils.EXPORT)
    }

    var isRunning: Boolean

    val appId: String
    val secretKey: String
    val redirectUri: String

    var operation: Operation?

    var user: JsonUser?
    fun fillUser(currentToken: String)

    fun generateToken()
    fun getToken(urlRedirected: String): String
    fun fillToken(token: String)

    val scopes: String
}
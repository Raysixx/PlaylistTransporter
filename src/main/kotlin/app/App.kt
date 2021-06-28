package app

import model.Utils

open class App(app: AppInterface): Utils(), AppInterface {
    @Volatile protected var currentToken: String? = null

    override val appId: String = app.appId
    override val secretKey: String = app.secretKey
    override val redirectUri: String = app.redirectUri
}
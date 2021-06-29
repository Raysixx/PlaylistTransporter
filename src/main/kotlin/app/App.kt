package app

import model.Utils

@Suppress("ControlFlowWithEmptyBody")
abstract class App: Utils(), AppInterface {
    @Volatile protected var currentToken: String? = null

    override var operation: AppInterface.Operation? = null

    override fun generateToken() {
        while (currentToken == null) {}
    }

    override fun fillToken(token: String) {
        currentToken = token
    }
}
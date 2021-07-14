package model

import client.Apps

data class Playlist(val title: String, val id: String, val app: Apps) {
    val tracks = mutableListOf<Track>()
    val tracksNotFound = mutableListOf<Track>()

    init {
        createdPlaylists.add(this)
    }

    companion object {
        val createdPlaylists = mutableListOf<Playlist>()

        fun getPlaylistsFromSpecificApp(app: Apps): List<Playlist> {
            return createdPlaylists.filter { it.app.name == app.name }
        }
    }
}

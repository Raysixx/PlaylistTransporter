package model

data class Playlist(val title: String, val id: Long) {
    val tracks = mutableListOf<Track>()

    init {
        createdPlaylists.add(this)
    }

    companion object {
        val createdPlaylists = mutableSetOf<Playlist>()
    }
}

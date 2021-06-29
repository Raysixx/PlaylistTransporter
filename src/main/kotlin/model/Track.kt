package model

import client.Apps

data class Track(val artist: Artist, val name: String, val id: String, val isAvailable: Boolean, val app: Apps) {
    init {
        createdTracks.add(this)
    }

    companion object {
        val createdTracks = mutableListOf<Track>()

        val tracksNotFound = mutableMapOf<Apps, MutableList<Track>>()
        val tracksNotAvailable = mutableMapOf<Apps, MutableList<Track>>()

        val externalTrackNameWithSameTrackOnOtherApp = mutableMapOf<String, Track>()
    }
}

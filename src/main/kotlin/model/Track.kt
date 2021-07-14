package model

import client.Apps

data class Track(val artists: List<Artist>, val name: String, val albumName: String, val id: String, val isAvailable: Boolean, val app: Apps) {
    init {
        createdTracks.add(this)
    }

    companion object {
        val createdTracks = mutableListOf<Track>()

        val tracksNotFound = mutableMapOf<Apps, MutableList<Track>>()
        val tracksNotAvailable = mutableMapOf<Apps, MutableList<Track>>()

        val externalTrackIdWithSameTrackOnOtherApp = mutableMapOf<String, Track>()
    }
}

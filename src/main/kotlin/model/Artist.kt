package model

import client.Apps

data class Artist(val name: String, val id: String, val app: Apps) {

    init {
        createdArtists.add(this)
    }

    companion object {
        const val artistsSeparator = ","

        val createdArtists = mutableListOf<Artist>()

        fun getArtistsNames(artists: List<Artist>, useSpace: Boolean = true): String {
            val separator = if (useSpace) " " else ""
            val artistsNames = artists.map { it.name }
            return artistsNames.reduce { acc, s -> "$acc$artistsSeparator$separator$s" }
        }
    }
}

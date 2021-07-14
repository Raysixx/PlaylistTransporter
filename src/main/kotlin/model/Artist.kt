package model

import client.Apps

data class Artist(val name: String, val id: String, val app: Apps) {
    companion object {
        fun getArtistsNames(artists: List<Artist>, useSpace: Boolean = true): String {
            val separator = if (useSpace) " " else ""
            val artistsNames = artists.map { it.name }
            return artistsNames.reduce { acc, s -> "$acc,$separator$s" }
        }
    }
}

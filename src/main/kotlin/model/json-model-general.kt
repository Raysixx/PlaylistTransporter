package model

import com.fasterxml.jackson.annotation.JsonAnySetter

@Suppress("MemberVisibilityCanBePrivate")
abstract class DefaultParent {
    val notMappedAttributes = mutableMapOf<String, Any>()

    @JsonAnySetter
    private fun setNotMappedAttribute(key: String, value: Any) { notMappedAttributes[key] = value }
}

abstract class JsonUser: DefaultParent() {
    abstract val id: String
    abstract val name: String
    abstract val country: String
}

abstract class JsonFoundPlaylists: DefaultParent()
abstract class JsonPlaylist: DefaultParent()
abstract class JsonFoundTracks: DefaultParent()

abstract class JsonTrack: DefaultParent() {
    abstract val id: String
    abstract val title: String
}
package app.apps.deezer

import com.fasterxml.jackson.annotation.JsonProperty
import model.DefaultParent
import model.JsonFoundPlaylists
import model.JsonFoundTracks
import model.JsonPlaylist
import model.JsonTrack
import model.JsonUser

data class DeezerUser(
    override val id: String,
    override val name: String,
    override val country: String
): JsonUser()

data class DeezerFoundPlaylists(
    val total: Int,
    val data: List<DeezerPlaylist>
): JsonFoundPlaylists()

data class DeezerPlaylist(
    val id: String,
    val title: String,

    @JsonProperty("tracklist")
    val tracklistUrl: String
): JsonPlaylist()

data class DeezerFoundTracks(
    val total: Int,
    val data: List<DeezerTrack>,
    val next: String?
): JsonFoundTracks()

data class DeezerTrack(
    override val id: String,
    override val title: String,
    val artist: DeezerArtist,
    val album: DeezerAlbum,
    val readable: Boolean,
    val link: String,
    val rank: Int,

    val contributors: List<DeezerArtist>?
): JsonTrack()

data class DeezerArtist(
    val id: String,
    val name: String,
    val link: String?,

    @JsonProperty("tracklist")
    val tracklistUrl: String
): DefaultParent()

data class DeezerAlbum(
    val id: String,
    val title: String,

    @JsonProperty("tracklist")
    val tracklistUrl: String
): DefaultParent()
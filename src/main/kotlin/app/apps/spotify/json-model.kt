package app.apps.spotify

import com.fasterxml.jackson.annotation.JsonProperty
import model.DefaultParent
import model.JsonFoundPlaylists
import model.JsonFoundTracks
import model.JsonPlaylist
import model.JsonTrack
import model.JsonUser

data class SpotifyToken(
    @JsonProperty("access_token")
    val token: String
): DefaultParent()

data class SpotifyUser(
    override val id: String,

    @JsonProperty("display_name")
    override val name: String,

    override val country: String
): JsonUser()

data class SpotifyFoundPlaylists(
    val total: Int,
    val items: List<SpotifyPlaylist>,
    val next: String?
): JsonFoundPlaylists()

data class SpotifyPlaylist(
    val id: String,

    @JsonProperty("name")
    val title: String,

    val tracks: SpotifyTracksRef
): JsonPlaylist()

data class SpotifyTracksRef(
    val total: Int,
    val href: String
): DefaultParent()

data class SpotifyFoundTracksResult(
    val tracks: SpotifyFoundTracksWithoutTrackItem
): DefaultParent()

data class SpotifyFoundTracksWithoutTrackItem(
    val total: Int,
    val items: List<SpotifyTrack>,
    val next: String?,
    val href: String
): JsonFoundTracks()

data class SpotifyFoundTracksWithTrackItem(
    val total: Int,
    val items: List<SpotifyTrackItem>,
    val next: String?,
    val href: String
): JsonFoundTracks()

data class SpotifyTrackItem(
    val track: SpotifyTrack
): DefaultParent()

data class SpotifyTrack(
    override val id: String,

    @JsonProperty("name")
    override val title: String,

    val artists: List<SpotifyArtist>,
    val album: SpotifyAlbum,

    @JsonProperty("available_markets")
    val availableCountries: List<String>,

    val popularity: Int
): JsonTrack()

data class SpotifyArtist(
    val id: String,
    val name: String,
    val href: String
): DefaultParent()

data class SpotifyAlbum(
    val id: String,

    @JsonProperty("name")
    val title: String,

    val artists: List<SpotifyArtist>,
    val href: String
): DefaultParent()
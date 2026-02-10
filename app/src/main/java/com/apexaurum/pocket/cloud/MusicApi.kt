package com.apexaurum.pocket.cloud

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.*

/** Music API client — JWT-authenticated for browsing library and managing tracks. */
interface MusicApi {

    @GET("api/v1/music/library")
    suspend fun getLibrary(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
        @Query("favorites_only") favoritesOnly: Boolean = false,
        @Query("status") status: String? = null,
        @Query("search") search: String? = null,
    ): MusicLibraryResponse

    @GET("api/v1/music/tasks/{id}")
    suspend fun getTask(@Path("id") id: String): MusicTrack

    @POST("api/v1/music/tasks/{id}/play")
    suspend fun markPlayed(@Path("id") id: String)

    @PATCH("api/v1/music/tasks/{id}/favorite")
    suspend fun toggleFavorite(
        @Path("id") id: String,
        @Query("favorite") favorite: Boolean,
    ): MusicFavoriteResponse
}

// ─── Music Models ─────────────────────────────────────────────

@Serializable
data class MusicTrack(
    val id: String = "",
    val prompt: String = "",
    val style: String? = null,
    val title: String? = null,
    val model: String = "",
    val instrumental: Boolean = false,
    val status: String = "pending",
    val progress: String? = null,
    @SerialName("file_path") val filePath: String? = null,
    @SerialName("audio_url") val audioUrl: String? = null,
    val duration: Float? = null,
    val error: String? = null,
    val favorite: Boolean = false,
    @SerialName("play_count") val playCount: Int = 0,
    @SerialName("agent_id") val agentId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
)

@Serializable
data class MusicLibraryResponse(
    val tasks: List<MusicTrack> = emptyList(),
    val total: Int = 0,
    @SerialName("total_duration") val totalDuration: Float = 0f,
)

@Serializable
data class MusicFavoriteResponse(
    val id: String = "",
    val favorite: Boolean = false,
    val title: String? = null,
)

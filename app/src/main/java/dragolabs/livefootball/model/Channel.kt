package dragolabs.livefootball.model

import com.google.gson.annotations.SerializedName

data class Channel(
    @SerializedName("channel_id") val channelId: String,
    @SerializedName("channel_name") val channelName: String,
    @SerializedName("channel_url") var channelUrl: String,
    @SerializedName("channel_type") val channelType: String, // "exo" or "embed"
    @SerializedName("agent") val agent: String? = null,
    @SerializedName("origin") val origin: String? = null,
    @SerializedName("eh1") val eh1: String? = null, // Referer
    @SerializedName("eh2") val eh2: String? = null, // Extra headers
    @SerializedName("cUrl") val cUrl: String? = null
)

data class PostResponse(
    @SerializedName("posts") val posts: List<Channel>? = null,
    @SerializedName("main_v2") val mainV2: String? = null
)

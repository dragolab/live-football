package dragolabs.livefootball.model

import com.google.gson.annotations.SerializedName

data class Channel(
    @SerializedName(value = "channel_id", alternate = ["id", "post_id"]) 
    val channelId: String,
    
    @SerializedName(value = "channel_name", alternate = ["channel_title", "title"]) 
    val channelName: String,
    
    @SerializedName(value = "channel_url", alternate = ["url", "stream_url"]) 
    var channelUrl: String,
    
    @SerializedName(value = "channel_type", alternate = ["type", "stream_type"]) 
    val channelType: String, // "exo" or "embed"

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

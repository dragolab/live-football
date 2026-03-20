package dragolabs.livefootball.model

import com.google.gson.annotations.SerializedName

data class Channel(
    @SerializedName(value = "channel_id", alternate = ["id", "post_id", "cid"]) 
    val channelId: String,
    
    @SerializedName(value = "channel_name", alternate = ["channel_title", "title", "post_title"]) 
    val channelName: String,
    
    @SerializedName(value = "channel_url", alternate = ["url", "stream_url", "post_url"]) 
    var channelUrl: String,
    
    @SerializedName(value = "channel_type", alternate = ["type", "stream_type"]) 
    val channelType: String,

    @SerializedName("agent") val agent: String? = null,
    @SerializedName("origin") val origin: String? = null,
    @SerializedName("eh1") val eh1: String? = null,
    @SerializedName("eh2") val eh2: String? = null,
    @SerializedName("cUrl") val cUrl: String? = null
)

data class PostResponse(
    @SerializedName(value = "posts", alternate = ["channels", "post", "live"]) 
    val posts: List<Channel>? = null
)

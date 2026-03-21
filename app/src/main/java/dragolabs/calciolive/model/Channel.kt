package dragolabs.calciolive.model

import com.google.gson.annotations.SerializedName

data class Channel(
    @SerializedName(value = "channel_id", alternate = ["f31671a", "id", "post_id"])
    val channelId: String,

    @SerializedName(value = "channel_name", alternate = ["f31672b", "channel_title", "title"])
    val channelName: String,

    @SerializedName(value = "channel_image", alternate = ["f31673c", "image"])
    val channelImage: String? = null,

    @SerializedName(value = "channel_url", alternate = ["f31675e", "url", "stream_url"])
    var channelUrl: String,

    @SerializedName(value = "channel_type", alternate = ["f31674d", "type", "stream_type"])
    val channelType: String, // "exo" or "embed"

    @SerializedName(value = "agent", alternate = ["f31679i"])
    val agent: String? = null,

    @SerializedName(value = "cUrl", alternate = ["f31682l"])
    val cUrl: String? = null,

    @SerializedName(value = "livetime", alternate = ["f31676f"])
    val liveTime: String? = null,

    @SerializedName("eh1") val eh1: String? = null, // Referer
    @SerializedName("eh2") val eh2: String? = null, // Extra headers
    @SerializedName("origin") val origin: String? = null
)

data class PostResponse(
    @SerializedName(value = "posts", alternate = ["channels", "data", "LIVETV"])
    val posts: List<Channel>? = null,

    @SerializedName("main_v2") val mainV2: String? = null,
    @SerializedName("post_v2") val postV2: String? = null
)
package dragolabs.livefootball.model

import com.google.gson.annotations.SerializedName

data class Category(
    @SerializedName("cid") val cid: String,
    @SerializedName("category_name") val categoryName: String,
    @SerializedName("category_image") val categoryImage: String
)

data class ConfigResponse(
    @SerializedName("categories") val categories: List<Category>? = null,
    // Sometimes the whole response or parts are base64
    @SerializedName("main_v2") val mainV2: String? = null
)

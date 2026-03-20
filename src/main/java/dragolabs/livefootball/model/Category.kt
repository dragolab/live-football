package dragolabs.livefootball.model

import com.google.gson.annotations.SerializedName

data class Category(
    @SerializedName(value = "cid", alternate = ["category_id", "id", "f31671a"]) 
    val cid: String? = null,
    
    @SerializedName(value = "category_name", alternate = ["category_title", "title", "f31672b"]) 
    val categoryName: String? = null,
    
    @SerializedName(value = "category_image", alternate = ["image", "category_img", "f31673c"]) 
    val categoryImage: String? = null
)

data class ConfigResponse(
    @SerializedName(value = "categories", alternate = ["data", "LIVETV"]) 
    val categories: List<Category>? = null,
    @SerializedName("main_v2") val mainV2: String? = null
)

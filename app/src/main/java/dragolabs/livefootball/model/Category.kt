package dragolabs.livefootball.model

import com.google.gson.annotations.SerializedName

data class Category(
    @SerializedName(value = "cid", alternate = ["category_id", "id"]) 
    val cid: String? = null,
    
    @SerializedName(value = "category_name", alternate = ["category_title", "title", "name"]) 
    val categoryName: String? = null,
    
    @SerializedName(value = "category_image", alternate = ["image", "category_img", "img"]) 
    val categoryImage: String? = null
)

data class ConfigResponse(
    @SerializedName(value = "categories", alternate = ["category", "LIVETV"]) 
    val categories: List<Category>? = null
)

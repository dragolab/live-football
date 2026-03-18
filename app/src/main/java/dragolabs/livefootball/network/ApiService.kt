package dragolabs.livefootball.network

import dragolabs.livefootball.model.ConfigResponse
import dragolabs.livefootball.model.PostResponse
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

interface ApiService {
    @GET("get_ads_config.js")
    suspend fun getConfig(): String

    @GET("get_ads_posts.js")
    suspend fun getPosts(@Query("id") categoryId: String): String

    @GET
    suspend fun refreshUrl(@Url url: String): String
}

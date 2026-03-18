package dragolabs.livefootball.repository

import com.google.gson.Gson
import dragolabs.livefootball.model.Category
import dragolabs.livefootball.model.Channel
import dragolabs.livefootball.model.ConfigResponse
import dragolabs.livefootball.model.PostResponse
import dragolabs.livefootball.network.RetrofitClient
import dragolabs.livefootball.utils.Base64Utils

class SportRepository {
    private val apiService = RetrofitClient.apiService
    private val gson = Gson()

    suspend fun getCategories(): List<Category> {
        return try {
            val responseString = apiService.getConfig()
            val configResponse = gson.fromJson(responseString, ConfigResponse::class.java)
            
            val decodedJson = Base64Utils.decode(configResponse.mainV2)
            if (decodedJson.isNotEmpty()) {
                val decodedResponse = gson.fromJson(decodedJson, ConfigResponse::class.java)
                decodedResponse.categories ?: emptyList()
            } else {
                configResponse.categories ?: emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getChannels(categoryId: String): List<Channel> {
        return try {
            val responseString = apiService.getPosts(categoryId)
            val postResponse = gson.fromJson(responseString, PostResponse::class.java)
            
            val decodedJson = Base64Utils.decode(postResponse.mainV2)
            if (decodedJson.isNotEmpty()) {
                val decodedResponse = gson.fromJson(decodedJson, PostResponse::class.java)
                decodedResponse.posts ?: emptyList()
            } else {
                postResponse.posts ?: emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getUpdatedUrl(cUrl: String): String? {
        return try {
            apiService.refreshUrl(cUrl)
        } catch (e: Exception) {
            null
        }
    }
}

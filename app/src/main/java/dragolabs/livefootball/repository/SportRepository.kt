package dragolabs.livefootball.repository

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import dragolabs.livefootball.model.Category
import dragolabs.livefootball.model.Channel
import dragolabs.livefootball.model.ConfigResponse
import dragolabs.livefootball.model.PostResponse
import dragolabs.livefootball.network.RetrofitClient
import dragolabs.livefootball.utils.CryptoUtils

class SportRepository {
    private val apiService = RetrofitClient.apiService
    private val gson = Gson()

    private fun extractJson(input: String): String {
        val start = input.indexOf('{')
        val end = input.lastIndexOf('}')
        return if (start != -1 && end != -1 && end > start) {
            input.substring(start, end + 1)
        } else {
            input
        }
    }

    suspend fun getCategories(): List<Category> {
        return try {
            val rawResponse = apiService.getConfig()
            
            // 1. Tenta la decrittazione se la risposta intera è cifrata
            val decrypted = if (!rawResponse.trim().startsWith("{")) {
                CryptoUtils.decrypt(rawResponse) ?: rawResponse
            } else {
                rawResponse
            }

            val responseString = extractJson(decrypted)
            val jsonObject = try {
                gson.fromJson(responseString, JsonObject::class.java)
            } catch (e: Exception) {
                null
            } ?: return emptyList()
            
            // 2. Prova a leggere direttamente le categorie
            val configResponse = gson.fromJson(jsonObject, ConfigResponse::class.java)
            if (!configResponse?.categories.isNullOrEmpty()) {
                return configResponse.categories!!
            }

            // 3. Se non ci sono, prova a decifrare il campo 'main_v2' o 'config_v2'
            val encryptedData = jsonObject.get("main_v2")?.asString 
                ?: jsonObject.get("config_v2")?.asString
            
            if (!encryptedData.isNullOrEmpty()) {
                val decodedJson = CryptoUtils.decrypt(encryptedData)
                if (decodedJson != null) {
                    val decodedString = extractJson(decodedJson)
                    val decodedObject = gson.fromJson(decodedString, JsonObject::class.java)
                    
                    // Gestione nodo "LIVETV" se presente nel JSON decifrato
                    val finalNode = if (decodedObject.has("LIVETV")) {
                        decodedObject.getAsJsonObject("LIVETV")
                    } else {
                        decodedObject
                    }
                    
                    val finalResponse = gson.fromJson(finalNode, ConfigResponse::class.java)
                    return finalResponse?.categories ?: emptyList()
                }
            }
            emptyList()
        } catch (e: Exception) {
            Log.e("SportRepository", "Error loading categories", e)
            emptyList()
        }
    }

    suspend fun getChannels(categoryId: String): List<Channel> {
        return try {
            val rawResponse = apiService.getPosts(categoryId)
            
            val decrypted = if (!rawResponse.trim().startsWith("{")) {
                CryptoUtils.decrypt(rawResponse) ?: rawResponse
            } else {
                rawResponse
            }

            val responseString = extractJson(decrypted)
            val jsonObject = try {
                gson.fromJson(responseString, JsonObject::class.java)
            } catch (e: Exception) {
                null
            } ?: return emptyList()

            // Prova lettura diretta
            val postResponse = gson.fromJson(jsonObject, PostResponse::class.java)
            if (!postResponse?.posts.isNullOrEmpty()) {
                return postResponse.posts!!
            }

            // Prova decrittazione campo main_v2
            val encryptedData = jsonObject.get("main_v2")?.asString
            if (!encryptedData.isNullOrEmpty()) {
                val decodedJson = CryptoUtils.decrypt(encryptedData)
                if (decodedJson != null) {
                    val decodedString = extractJson(decodedJson)
                    val decodedObject = gson.fromJson(decodedString, JsonObject::class.java)
                    
                    val finalNode = if (decodedObject.has("LIVETV")) {
                        decodedObject.getAsJsonObject("LIVETV")
                    } else {
                        decodedObject
                    }

                    val finalResponse = gson.fromJson(finalNode, PostResponse::class.java)
                    return finalResponse?.posts ?: emptyList()
                }
            }
            emptyList()
        } catch (e: Exception) {
            Log.e("SportRepository", "Error loading channels", e)
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

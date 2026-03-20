package dragolabs.livefootball.repository

import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import dragolabs.livefootball.model.Category
import dragolabs.livefootball.model.Channel
import dragolabs.livefootball.model.ConfigResponse
import dragolabs.livefootball.model.PostResponse
import dragolabs.livefootball.network.RetrofitClient
import dragolabs.livefootball.utils.CryptoUtils

class SportRepository {
    private val apiService = RetrofitClient.apiService
    private val gson = GsonBuilder().setLenient().create()

    private fun extractJson(input: String): String {
        val firstBrace = input.indexOf('{')
        val firstBracket = input.indexOf('[')
        
        val start = if (firstBrace != -1 && firstBracket != -1) minOf(firstBrace, firstBracket)
                    else if (firstBrace != -1) firstBrace
                    else if (firstBracket != -1) firstBracket
                    else -1

        val lastBrace = input.lastIndexOf('}')
        val lastBracket = input.lastIndexOf(']')
        val end = maxOf(lastBrace, lastBracket)

        return if (start != -1 && end != -1 && end > start) {
            input.substring(start, end + 1)
        } else {
            input.trim()
        }
    }

    suspend fun getCategories(): List<Category> {
        return try {
            val rawResponse = apiService.getConfig()
            val jsonString = extractJson(rawResponse)
            val jsonObject = gson.fromJson(jsonString, JsonObject::class.java)
            
            // Priorità a main_v2 come nell'originale
            val encryptedData = jsonObject.get("main_v2")?.asString 
                ?: jsonObject.get("config_v2")?.asString
            
            if (!encryptedData.isNullOrEmpty()) {
                val decryptedJson = CryptoUtils.decrypt(encryptedData)
                if (decryptedJson != null) {
                    val finalJson = extractJson(decryptedJson)
                    Log.d("DEBUG_JSON", "Decriptato Categorie: $finalJson")
                    
                    val element = gson.fromJson(finalJson, JsonElement::class.java)
                    
                    if (element.isJsonArray) {
                        val type = object : TypeToken<List<Category>>() {}.type
                        return gson.fromJson(element, type)
                    } else if (element.isJsonObject) {
                        val obj = element.asJsonObject
                        val dataNode = when {
                            obj.has("LIVETV") -> obj.get("LIVETV")
                            obj.has("categories") -> obj.get("categories")
                            else -> obj
                        }
                        
                        if (dataNode.isJsonArray) {
                            val type = object : TypeToken<List<Category>>() {}.type
                            return gson.fromJson(dataNode, type)
                        } else {
                            val response = gson.fromJson(obj, ConfigResponse::class.java)
                            return response?.categories ?: emptyList()
                        }
                    }
                }
            }
            emptyList()
        } catch (e: Exception) {
            Log.e("SportRepository", "Errore caricamento categorie: ${e.message}")
            emptyList()
        }
    }

    suspend fun getChannels(categoryId: String): List<Channel> {
        return try {
            Log.d("SportRepository", "Richiesta canali per cat_id: $categoryId")
            val rawResponse = apiService.getPosts(categoryId)
            val jsonString = extractJson(rawResponse)
            val jsonObject = gson.fromJson(jsonString, JsonObject::class.java)

            val encryptedData = jsonObject.get("main_v2")?.asString
            if (!encryptedData.isNullOrEmpty()) {
                val decryptedJson = CryptoUtils.decrypt(encryptedData)
                if (decryptedJson != null) {
                    val finalJson = extractJson(decryptedJson)
                    Log.d("DEBUG_JSON", "Decriptato Canali: $finalJson")

                    val element = gson.fromJson(finalJson, JsonElement::class.java)
                    
                    if (element.isJsonArray) {
                        val type = object : TypeToken<List<Channel>>() {}.type
                        return gson.fromJson(element, type)
                    } else if (element.isJsonObject) {
                        val obj = element.asJsonObject
                        val dataNode = when {
                            obj.has("LIVETV") -> obj.get("LIVETV")
                            obj.has("posts") -> obj.get("posts")
                            else -> obj
                        }
                        
                        if (dataNode.isJsonArray) {
                            val type = object : TypeToken<List<Channel>>() {}.type
                            return gson.fromJson(dataNode, type)
                        } else {
                            val response = gson.fromJson(obj, PostResponse::class.java)
                            return response?.posts ?: emptyList()
                        }
                    }
                }
            }
            emptyList()
        } catch (e: Exception) {
            Log.e("SportRepository", "Errore caricamento canali: ${e.message}")
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

package dragolabs.calciolive.repository

import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import dragolabs.calciolive.model.Category
import dragolabs.calciolive.model.Channel
import dragolabs.calciolive.model.ConfigResponse
import dragolabs.calciolive.model.PostResponse
import dragolabs.calciolive.network.RetrofitClient
import dragolabs.calciolive.utils.CryptoUtils

class SportRepository {
    private val apiService = RetrofitClient.apiService
    private val gson = GsonBuilder().setLenient().create()

    private fun extractJson(input: String): String {
        val trimmed = input.trim()
        val firstBrace = trimmed.indexOf('{')
        val firstBracket = trimmed.indexOf('[')

        val result = when {
            firstBracket != -1 && (firstBrace == -1 || firstBracket < firstBrace) -> {
                val lastBracket = trimmed.lastIndexOf(']')
                if (lastBracket != -1) trimmed.substring(firstBracket, lastBracket + 1) else trimmed
            }
            firstBrace != -1 -> {
                val lastBrace = trimmed.lastIndexOf('}')
                if (lastBrace != -1) trimmed.substring(firstBrace, lastBrace + 1) else trimmed
            }
            else -> trimmed
        }
        return fixJsonArray(result)
    }

    // Risolve l'errore "Unexpected value at line 1 column 224" aggiungendo [] se mancano
    private fun fixJsonArray(input: String): String {
        val s = input.trim()
        if (s.startsWith("{") && s.contains("},{") && !s.startsWith("[")) {
            return "[$s]"
        }
        return s
    }

    suspend fun getCategories(): List<Category> {
        return try {
            val rawResponse = apiService.getConfig()
            val decrypted = if (!rawResponse.trim().startsWith("{") && !rawResponse.trim().startsWith("[")) {
                CryptoUtils.decrypt(rawResponse) ?: rawResponse
            } else rawResponse

            val responseString = extractJson(decrypted)
            Log.d("DEBUG_JSON", "Categorie: $responseString")

            if (responseString.startsWith("[")) {
                return gson.fromJson(responseString, object : TypeToken<List<Category>>() {}.type)
            }

            val jsonObject = gson.fromJson(responseString, JsonObject::class.java)
            val encryptedData = jsonObject.get("main_v2")?.asString ?: jsonObject.get("config_v2")?.asString

            if (!encryptedData.isNullOrEmpty()) {
                val innerDecrypted = CryptoUtils.decrypt(encryptedData) ?: ""
                val innerJson = extractJson(innerDecrypted)

                if (innerJson.startsWith("[")) {
                    return gson.fromJson(innerJson, object : TypeToken<List<Category>>() {}.type)
                }
                val innerObj = gson.fromJson(innerJson, JsonObject::class.java)
                val node = if (innerObj.has("LIVETV")) innerObj.get("LIVETV") else innerObj
                return gson.fromJson(node, ConfigResponse::class.java)?.categories ?: emptyList()
            }
            emptyList()
        } catch (e: Exception) {
            Log.e("SportRepository", "Error categories: ${e.message}")
            emptyList()
        }
    }

    suspend fun getChannels(categoryId: String): List<Channel> {
        return try {
            Log.d("SportRepository", "Richiesta canali id: $categoryId")
            val rawResponse = apiService.getPosts(categoryId)
            val responseString = extractJson(rawResponse)

            val jsonObject = gson.fromJson(responseString, JsonObject::class.java)
            val encryptedData = jsonObject.get("post_v2")?.asString ?: jsonObject.get("main_v2")?.asString

            if (!encryptedData.isNullOrEmpty()) {
                val decrypted = CryptoUtils.decrypt(encryptedData) ?: ""
                val decodedString = extractJson(decrypted)
                Log.d("DEBUG_JSON", "Canali: $decodedString")

                if (decodedString.startsWith("[")) {
                    return gson.fromJson(decodedString, object : TypeToken<List<Channel>>() {}.type)
                }

                val decodedObject = gson.fromJson(decodedString, JsonObject::class.java)
                val finalNode = if (decodedObject.has("LIVETV")) decodedObject.get("LIVETV") else decodedObject
                return gson.fromJson(finalNode, PostResponse::class.java)?.posts ?: emptyList()
            }
            emptyList()
        } catch (e: Exception) {
            Log.e("SportRepository", "Error channels: ${e.message}")
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
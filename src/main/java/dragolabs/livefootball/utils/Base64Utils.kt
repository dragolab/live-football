package dragolabs.livefootball.utils

import android.util.Base64
import android.util.Log

object Base64Utils {
    fun decode(base64String: String?): String? {
        if (base64String.isNullOrEmpty()) return null
        
        // Se la stringa è già un JSON (inizia con {), non decodificare
        if (base64String.trim().startsWith("{")) return base64String

        return try {
            val decodedBytes = try {
                Base64.decode(base64String, Base64.DEFAULT)
            } catch (e: Exception) {
                try {
                    Base64.decode(base64String, Base64.NO_WRAP)
                } catch (e2: Exception) {
                    Base64.decode(base64String, Base64.URL_SAFE)
                }
            }
            String(decodedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e("Base64Utils", "Error decoding string: $base64String", e)
            null
        }
    }
}

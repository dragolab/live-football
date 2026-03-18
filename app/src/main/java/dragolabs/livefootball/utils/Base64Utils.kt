package dragolabs.livefootball.utils

import android.util.Base64

object Base64Utils {
    fun decode(base64String: String?): String? {
        if (base64String.isNullOrEmpty()) return null
        return try {
            // Try different flags if default fails
            val decodedBytes = try {
                Base64.decode(base64String, Base64.DEFAULT)
            } catch (e: Exception) {
                try {
                    Base64.decode(base64String, Base64.NO_WRAP)
                } catch (e2: Exception) {
                    Base64.decode(base64String, Base64.URL_SAFE)
                }
            }
            String(decodedBytes)
        } catch (e: Exception) {
            null
        }
    }
}

package dragolabs.livefootball.utils

import android.util.Log
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec

object CryptoUtils {
    private const val ALGORITHM = "AES/CBC/NoPadding"
    private const val KEY = "1g2j4d5rb56s39wc"
    private const val IV = "g4fst5gpd5f5r7j4"

    /**
     * Decifra una stringa esadecimale utilizzando AES/CBC/NoPadding
     */
    fun decrypt(hexString: String?): String? {
        if (hexString.isNullOrEmpty()) return null
        val trimmed = hexString.trim()
        
        // Se la stringa sembra già essere un JSON (inizia con { o [), la restituiamo così com'è
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) return trimmed

        return try {
            val cipher = Cipher.getInstance(ALGORITHM)
            val keySpec = SecretKeySpec(KEY.toByteArray(Charsets.UTF_8), "AES")
            val ivSpec = IvParameterSpec(IV.toByteArray(Charsets.UTF_8))
            
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            
            val encryptedBytes = hexToByteArray(trimmed)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            
            // Rimuoviamo eventuali caratteri di padding (null bytes o spazi) comuni con NoPadding
            String(decryptedBytes, Charsets.UTF_8).trim { it <= ' ' || it == '\u0000' }
        } catch (e: Exception) {
            Log.e("CryptoUtils", "Errore durante la decrittazione: ${e.message}")
            null
        }
    }

    private fun hexToByteArray(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = (
                (Character.digit(hex[i], 16) shl 4) + 
                Character.digit(hex[i + 1], 16)
            ).toByte()
        }
        return data
    }
}

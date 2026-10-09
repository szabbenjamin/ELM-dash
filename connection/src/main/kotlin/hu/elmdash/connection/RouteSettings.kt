package hu.elmdash.connection

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.net.URI
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class RouteConfig(val record: Boolean = false, val upload: Boolean = false,
    val server: String = "", val folder: String = "ELM-Dash", val username: String = "", val password: String = "") {
    fun destination(): String {
        val base = URI(server.trim())
        require(base.rawPath.orEmpty().split('/').none { it == "." || it == ".." || it.contains("%2e", true) || it.contains("%2f", true) || it.contains("%5c", true) }) { "A WebDAV URL útvonala nem tartalmazhat relatív szegmenst." }
        require(base.scheme == "https" && !base.host.isNullOrBlank() && base.rawUserInfo == null && base.rawQuery == null && base.rawFragment == null) {
            "HTTPS WebDAV URL szükséges, beágyazott jelszó és query nélkül."
        }
        val parts = folder.trim('/').split('/').filter { it.isNotBlank() }
        require(parts.isNotEmpty() && parts.none { it == "." || it == ".." || it.contains('\\') || it.any { c -> c.code < 32 } }) { "Adj meg érvényes célmappát." }
        return base.toASCIIString().trimEnd('/') + "/" + parts.joinToString("/") { URI(null, null, it, null).rawPath } + "/"
    }
    fun targetId(): String = MessageDigest.getInstance("SHA-256").digest((destination() + "\n" + username).toByteArray())
        .joinToString("") { "%02x".format(it) }
}

/** Credentials never enter WorkManager input, logs, exports or Android backups. */
class RouteSettings(context: Context) {
    private val prefs = context.getSharedPreferences("elm-route-settings", Context.MODE_PRIVATE)
    var status: String
        get() = prefs.getString("status", "Nincs feltöltés.")!!
        set(value) { prefs.edit().putString("status", value).apply() }
    fun load(): RouteConfig {
        val secret = prefs.getString("secret", null)?.let { decrypt(it) }?.let(::JSONObject)
        return RouteConfig(prefs.getBoolean("record", false), prefs.getBoolean("upload", false),
            prefs.getString("server", "")!!, prefs.getString("folder", "ELM-Dash")!!,
            secret?.optString("username").orEmpty(), secret?.optString("password").orEmpty())
    }
    fun save(config: RouteConfig) {
        if (config.upload) { config.destination(); require(config.username.isNotBlank() && !config.username.contains(':') && config.password.isNotBlank()) { "Felhasználónév és jelszó szükséges." } }
        val secret = encrypt(JSONObject().put("username", config.username).put("password", config.password).toString())
        check(prefs.edit().putBoolean("record", config.record).putBoolean("upload", config.upload)
            .putString("server", config.server.trim()).putString("folder", config.folder).putString("secret", secret).commit())
    }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("elm-webdav-v1", null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("elm-webdav-v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun encrypt(text: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(text.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
    private fun decrypt(text: String): String {
        val data = Base64.decode(text, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, 12))) }
        return cipher.doFinal(data.copyOfRange(12, data.size)).toString(Charsets.UTF_8)
    }
}

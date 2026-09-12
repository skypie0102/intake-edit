package cloud.shadowmonarchbooks.intakeedit

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("repo_settings", Context.MODE_PRIVATE)
    fun load(): RepoSettings {
        val loaded = RepoSettings(
            owner = prefs.getString("owner", "skypie0102") ?: "skypie0102",
            repo = prefs.getString("repo", "purelove") ?: "purelove",
            branch = prefs.getString("branch", "main") ?: "main",
            intakeRoot = prefs.getString("intake_root", "editor_input") ?: "editor_input",
        )
        return if (loaded.owner == "shadowmonarchbooks-cloud" && loaded.repo == "purelovexviolation") {
            loaded.copy(owner = "skypie0102", repo = "purelove").also(::save)
        } else loaded
    }
    fun save(settings: RepoSettings) {
        prefs.edit().putString("owner", settings.owner.trim()).putString("repo", settings.repo.trim())
            .putString("branch", settings.branch.trim()).putString("intake_root", settings.intakeRoot.trim().trim('/')).apply()
    }
}

class SecureTokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("secure_token", Context.MODE_PRIVATE)
    private val alias = "intake_edit_github_token_key"
    fun save(token: String) {
        if (token.isBlank()) { prefs.edit().clear().apply(); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        prefs.edit().putString("ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP)).apply()
    }
    fun load(): String = runCatching {
        val ciphertext = prefs.getString("ciphertext", "").orEmpty(); val iv = prefs.getString("iv", "").orEmpty()
        if (ciphertext.isBlank() || iv.isBlank()) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        String(cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)), Charsets.UTF_8)
    }.getOrDefault("")
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build()
        generator.init(spec); return generator.generateKey()
    }
}

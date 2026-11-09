package app.restora.live

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

fun obj(vararg pairs: Pair<String, Any?>) = JSONObject().apply { pairs.forEach { (k, v) -> put(k, v ?: JSONObject.NULL) } }
fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
fun JSONObject.text(key: String): String = if (isNull(key)) "" else optString(key, "")
fun JSONObject.array(key: String): List<JSONObject> = optJSONArray(key)?.objects().orEmpty()
fun JSONObject.strings(key: String): List<String> = optJSONArray(key)?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
fun JSONObject.copyJson() = JSONObject(toString())

/** Cookies and cached business records are encrypted with a non-exportable Android Keystore key. */
interface StringStore {
    fun read(name: String): String?
    fun write(name: String, value: String)
    fun clear()
}
class PrivateStore(context: Context) : StringStore {
    private val prefs = context.getSharedPreferences("restora_private", Context.MODE_PRIVATE)
    private val key: SecretKey by lazy {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("restora_storage", null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("restora_storage", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized override fun read(name: String): String? = prefs.getString(name, null)?.let { encoded ->
        runCatching {
            val parts = encoded.split(":", limit = 2)
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
                String(doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
            }
        }.getOrElse { prefs.edit().remove(name).apply(); null }
    }
    @Synchronized override fun write(name: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(name, Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit()
    }
    @Synchronized override fun clear() { prefs.edit().clear().commit() }
}

class SessionCookies(private val store: StringStore, private val origin: String = BuildConfig.API_URL) : CookieJar {
    private val cookies = mutableListOf<Cookie>()
    init {
        val url = origin.toHttpUrl()
        store.read("cookies")?.let { runCatching { JSONArray(it) }.getOrNull() }?.let { a ->
            for (i in 0 until a.length()) Cookie.parse(url, a.getString(i))?.let(cookies::add)
        }
    }
    @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { incoming ->
            this.cookies.removeAll { it.name == incoming.name && it.domain == incoming.domain && it.path == incoming.path }
            if (incoming.expiresAt > System.currentTimeMillis()) this.cookies.add(incoming)
        }
        store.write("cookies", JSONArray(this.cookies.map { it.toString() }).toString())
    }
    @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies.filter { it.expiresAt > System.currentTimeMillis() && it.matches(url) }
    @Synchronized fun clear() { cookies.clear() }
}

class ApiError(val status: Int, val answer: JSONObject) : Exception(answer.text("message").ifBlank { "Request failed ($status)" })
class RestoraApi(store: StringStore, private val origin: String = BuildConfig.API_URL) {
    val cookies = SessionCookies(store, origin)
    val client = OkHttpClient.Builder().cookieJar(cookies).connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    fun request(path: String, method: String = "GET", body: JSONObject? = null): Request = Request.Builder()
        .url(origin.trimEnd('/') + "/api/v1" + path)
        .header("Accept", "application/json")
        .header("Origin", "restora://")
        .method(method, if (method in listOf("GET", "HEAD")) null else (body ?: JSONObject()).toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
    suspend fun call(path: String, method: String = "GET", body: JSONObject? = null): Any = withContext(Dispatchers.IO) {
        client.newCall(request(path, method, body)).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val parsed = runCatching { JSONTokener(raw).nextValue() }.getOrNull()
            if (!response.isSuccessful) throw ApiError(response.code, parsed as? JSONObject ?: obj("message" to "Server returned ${response.code}"))
            if (parsed !is JSONObject && parsed !is JSONArray) throw IOException("The server did not return JSON data")
            parsed
        }
    }
}

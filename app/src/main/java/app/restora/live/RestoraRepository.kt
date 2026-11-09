package app.restora.live

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** One main-safe boundary for network, encrypted persistence, and the foreground event stream. */
interface RestoraDataSource {
    suspend fun initialize()
    suspend fun read(key: String): Any?
    suspend fun write(key: String, value: Any)
    suspend fun clearSession(clearData: Boolean = true)
    suspend fun request(path: String, method: String = "GET", body: JSONObject? = null): Any
    fun openEvents(listener: EventSourceListener): EventSource
}

class RestoraRepository(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : RestoraDataSource {
    private val applicationContext = context.applicationContext
    private val storageMutex = Mutex()
    private lateinit var storage: PrivateStore
    private lateinit var api: RestoraApi

    override suspend fun initialize() = withContext(ioDispatcher) {
        storage = PrivateStore(applicationContext)
        api = RestoraApi(storage, ioDispatcher = ioDispatcher)
    }

    override suspend fun read(key: String): Any? = storageMutex.withLock {
        withContext(ioDispatcher) {
            storage.read(key)?.let { raw ->
                try {
                    when (val value = org.json.JSONTokener(raw).nextValue()) {
                        is JSONObject, is org.json.JSONArray -> value
                        JSONObject.NULL -> null
                        else -> throw LocalStorageException("Saved data has an invalid format")
                    }
                } catch (error: org.json.JSONException) { throw LocalStorageException("Saved data could not be read", error) }
            }
        }
    }

    override suspend fun write(key: String, value: Any) = storageMutex.withLock {
        withContext(ioDispatcher) { storage.write(key, value.toString()) }
    }

    override suspend fun clearSession(clearData: Boolean) = storageMutex.withLock {
        withContext(ioDispatcher) {
            api.cookies.clear()
            if (clearData) storage.clear()
            else { storage.write("cookies", "[]"); storage.write("me", "null") }
        }
    }

    override suspend fun request(path: String, method: String, body: JSONObject?): Any =
        api.call(path, method, body)

    override fun openEvents(listener: EventSourceListener): EventSource {
        val streamClient = api.client.newBuilder().readTimeout(0, TimeUnit.SECONDS).build()
        return EventSources.createFactory(streamClient).newEventSource(api.request("/orders/stream"), listener)
    }
}

/** A successful response with the wrong format is not a connection failure and must never queue a write. */
class InvalidApiResponse : IOException("The server did not return valid JSON data")

package app.restora.live

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

 data class AppState(
    val starting: Boolean = true,
    val me: JSONObject? = null,
    val page: String = "Home",
    val data: Map<String, Any> = emptyMap(),
    val loading: Set<String> = emptySet(),
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val offline: Boolean = false,
    val outbox: List<JSONObject> = emptyList(),
    val editingOrder: JSONObject? = null,
    val generation: Int = 0
) {
    val workplace: JSONObject? get() = me?.optJSONObject("workplace")
    fun can(permission: String) = workplace?.strings("permissions")?.contains(permission) == true
    val scope: String get() = listOf(me?.optJSONObject("user")?.text("id"), workplace?.optJSONObject("restaurant")?.text("_id"), workplace?.optJSONObject("branch")?.text("_id")).joinToString(":")
}

class RestoraModel(app: Application) : AndroidViewModel(app) {
    private val store = PrivateStore(app)
    val api = RestoraApi(store)
    private val mutable = MutableStateFlow(AppState())
    val state = mutable.asStateFlow()
    private var live: okhttp3.sse.EventSource? = null
    private var liveRetry: Job? = null
    private var foreground = false
    private var liveOpened = false
    private var syncing = false
    private val jobs = mutableMapOf<String, Job>()
    init { start() }
    private fun start() = viewModelScope.launch {
        try {
            val me = api.call("/me") as JSONObject
            store.write("me", me.toString())
            mutable.update { it.copy(starting = false, me = me) }; restoreOutbox()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (e is ApiError && e.status == 401) { api.cookies.clear(); store.clear() }
            val cached = if (e is IOException) store.read("me")?.let(::JSONObject) else null
            mutable.update { it.copy(starting = false, me = cached, offline = cached != null, error = if (e is ApiError && e.status == 401) null else e.message) }; restoreOutbox()
        }
    }
    fun showError(message: String) { mutable.update { it.copy(error = message) } }
    fun clearMessage() { mutable.update { it.copy(error = null, notice = null) } }
    fun navigate(page: String) { mutable.update { it.copy(page = page, error = null, editingOrder = null) } }
    fun editOrder(order: JSONObject) { mutable.update { it.copy(page = "New order", editingOrder = order, error = null) } }
    private fun acceptMe(me: JSONObject?) {
        stopLive()
        jobs.values.forEach { it.cancel() }; jobs.clear()
        if (me != null) store.write("me", me.toString())
        mutable.update { AppState(starting = false, me = me, generation = it.generation + 1) }; restoreOutbox()
    }
    fun action(path: String, method: String = "POST", body: JSONObject = JSONObject(), failure: (Exception) -> Unit = {}, done: (JSONObject) -> Unit = {}) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val answer = api.call(path, method, body) as JSONObject
                if (path in listOf("/me/workplace", "/me/branch")) acceptMe(answer)
                else if (answer.has("me")) acceptMe(answer.optJSONObject("me"))
                mutable.update { it.copy(offline = false) }
                done(answer)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                handle(e)
                failure(e)
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
    private fun handle(e: Exception) {
        if (e is ApiError && e.status == 401) {
            api.cookies.clear(); store.clear(); acceptMe(null)
        } else if (e is ApiError && e.answer.text("code") == "context_lost") acceptMe(e.answer.optJSONObject("me"))
        mutable.update { it.copy(error = e.message ?: "Unable to connect. Please try again.", offline = e is IOException) }
    }
    fun deleteAccount() = action("/me", "DELETE") { api.cookies.clear(); store.clear(); acceptMe(null) }
    fun signOut() = action("/auth/sign-out") {
        api.cookies.clear(); store.clear(); acceptMe(null)
    }
    fun load(path: String, force: Boolean = false) {
        if (jobs[path]?.isActive == true || (!force && mutable.value.data.containsKey(path))) return
        val scope = mutable.value.scope
        if (!force) store.read("$scope:$path")?.let { raw ->
            runCatching { org.json.JSONTokener(raw).nextValue() }.getOrNull()?.let { saved -> mutable.update { it.copy(data = it.data + (path to saved)) } }
        }
        mutable.update { it.copy(loading = it.loading + path, error = null) }
        jobs[path] = viewModelScope.launch {
            try {
                val data = api.call(path)
                if (mutable.value.scope == scope) {
                    store.write("$scope:$path", data.toString())
                    mutable.update { it.copy(data = it.data + (path to data), offline = false) }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                handle(e)
            } finally { mutable.update { it.copy(loading = it.loading - path) } }
        }
    }
    fun put(path: String, value: Any) {
        store.write("${mutable.value.scope}:$path", value.toString())
        mutable.update { it.copy(data = it.data + (path to value)) }
    }
    fun upsert(path: String, record: JSONObject, delete: Boolean = false) {
        val old = (mutable.value.data[path] as? JSONArray)?.objects().orEmpty()
        val next = old.filterNot { it.text("_id") == record.text("_id") }.toMutableList()
        if (!delete) next.add(0, record)
        put(path, JSONArray(next))
    }
    fun updateMenu(key: String, record: JSONObject, delete: Boolean = false) {
        val menu = (mutable.value.data["/menu"] as? JSONObject)?.copyJson() ?: return
        val rows = menu.array(key).filterNot { it.text("_id") == record.text("_id") }.toMutableList()
        if (!delete) rows.add(record)
        menu.put(key, JSONArray(rows)); put("/menu", menu)
    }
    fun updateProfile(name: String) {
        val me = mutable.value.me?.copyJson() ?: return
        me.getJSONObject("user").put("name", name)
        store.write("me", me.toString()); mutable.update { it.copy(me = me) }
    }
    fun updateBranch(branch: JSONObject) {
        val me = mutable.value.me?.copyJson() ?: return
        val workplace = me.optJSONObject("workplace") ?: return
        val hadAccess = workplace.array("branches").any { it.text("_id") == branch.text("_id") }
        val list = workplace.array("branches").filterNot { it.text("_id") == branch.text("_id") }.toMutableList()
        if (branch.text("archivedAt").isBlank() && (workplace.optBoolean("allBranches") || hadAccess)) list.add(branch)
        workplace.put("branches", JSONArray(list))
        if (workplace.optJSONObject("branch")?.text("_id") == branch.text("_id")) workplace.put("branch", if (branch.text("archivedAt").isBlank()) branch else JSONObject.NULL)
        store.write("me", me.toString()); mutable.update { it.copy(me = me) }
    }
    fun updateSettings(settings: JSONObject) {
        val me = mutable.value.me?.copyJson() ?: return
        val workplace = me.optJSONObject("workplace") ?: return
        workplace.put("settings", settings)
        if (settings.has("restaurantName")) {
            workplace.getJSONObject("restaurant").put("name", settings.text("restaurantName"))
            me.array("restaurants").firstOrNull { it.text("_id") == workplace.getJSONObject("restaurant").text("_id") }?.put("name", settings.text("restaurantName"))
        }
        store.write("me", me.toString()); mutable.update { it.copy(me = me) }
    }
    private fun restoreOutbox() {
        val rows = store.read("outbox:${mutable.value.scope}")?.let { runCatching { JSONArray(it).objects() }.getOrNull() }.orEmpty()
        mutable.update { it.copy(outbox = rows) }
    }
    private fun saveOutbox(rows: List<JSONObject>) {
        store.write("outbox:${mutable.value.scope}", JSONArray(rows).toString())
        mutable.update { it.copy(outbox = rows) }
    }
    fun discardPending(clientId: String) { saveOutbox(mutable.value.outbox.filterNot { it.text("clientId") == clientId }) }
    fun createOrder(body: JSONObject, done: (JSONObject) -> Unit) {
        action("/orders", body = body, failure = { error ->
            if (error is IOException && body.text("paymentStatus") !in listOf("paid", "khata")) {
                if (mutable.value.outbox.none { it.text("clientId") == body.text("clientId") }) saveOutbox(mutable.value.outbox + body)
                mutable.update { it.copy(error = null, notice = "Order saved on this device. It will send when connected.", page = "Orders") }
            }
        }, done = done)
    }
    fun syncPending(retryFailed: Boolean = false) {
        if (syncing || mutable.value.outbox.isEmpty()) return
        syncing = true
        val scope = mutable.value.scope
        viewModelScope.launch {
            try {
                for (pending in mutable.value.outbox.toList()) {
                    if (pending.has("syncError") && !retryFailed) continue
                    if (mutable.value.scope != scope) break
                    try {
                        val saved = api.call("/orders", "POST", pending) as JSONObject
                        if (mutable.value.scope != scope) break
                        val path = "/orders?date=${saved.text("dayKey")}"
                        if (mutable.value.data.containsKey(path)) upsert(path, saved)
                        saveOutbox(mutable.value.outbox.filterNot { it.text("clientId") == pending.text("clientId") })
                        mutable.update { it.copy(notice = "Order #${saved.text("dailyToken")} sent", offline = false) }
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        if (mutable.value.scope != scope) break
                        handle(error)
                        if (error is ApiError && mutable.value.scope == scope) saveOutbox(mutable.value.outbox.map { if (it.text("clientId") == pending.text("clientId")) it.copyJson().put("syncError", error.message) else it })
                        // Keep rejected orders visible for review; never silently drop or repeatedly submit them.
                        break
                    }
                }
            } finally { syncing = false }
        }
    }
    fun startLive() {
        foreground = true
        if (live != null || !mutable.value.can("orders:view")) return
        val scope = mutable.value.scope
        val client = api.client.newBuilder().readTimeout(0, java.util.concurrent.TimeUnit.SECONDS).build()
        live = okhttp3.sse.EventSources.createFactory(client).newEventSource(api.request("/orders/stream"), object : okhttp3.sse.EventSourceListener() {
            override fun onOpen(eventSource: okhttp3.sse.EventSource, response: okhttp3.Response) {
                viewModelScope.launch {
                    if (scope != mutable.value.scope || live !== eventSource) return@launch
                    mutable.update { it.copy(offline = false) }
                    val day = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Karachi"))
                    if (liveOpened) mutable.value.data.keys.filter { it == "/orders?date=$day" || it == "/tables/floor" }.forEach { load(it, true) }
                    liveOpened = true
                    syncPending()
                }
            }
            override fun onEvent(eventSource: okhttp3.sse.EventSource, id: String?, type: String?, data: String) {
                viewModelScope.launch {
                    if (scope != mutable.value.scope || live !== eventSource) return@launch
                    val record = runCatching { JSONObject(data) }.getOrNull() ?: return@launch
                    if (type?.startsWith("order.") == true) {
                        mutable.value.data.keys.filter { it.startsWith("/orders?date=") && it.substringAfter("date=").take(10) == record.text("dayKey") }.forEach { upsert(it, record) }
                        (mutable.value.data["/tables/floor"] as? JSONArray)?.let { floor ->
                            val rows = floor.objects().map { table ->
                                val next = table.copyJson()
                                val orders = table.array("orders").filterNot { it.text("_id") == record.text("_id") }.toMutableList()
                                if (record.text("tableId") == table.text("_id") && record.text("status") != "cancelled" && !(record.text("status") == "completed" && record.text("paymentStatus") == "paid")) orders.add(record)
                                next.put("orders", JSONArray(orders))
                            }
                            put("/tables/floor", JSONArray(rows))
                        }
                    } else if (type?.startsWith("call.") == true) {
                        (mutable.value.data["/tables/floor"] as? JSONArray)?.let { floor ->
                            put("/tables/floor", JSONArray(floor.objects().map { table ->
                                val requests = table.array("requests").filterNot { it.text("_id") == record.text("_id") }.toMutableList()
                                if (!record.optBoolean("resolved") && record.text("tableId") == table.text("_id")) requests.add(record)
                                table.copyJson().put("requests", JSONArray(requests))
                            }))
                        }
                    }
                }
            }
            override fun onClosed(eventSource: okhttp3.sse.EventSource) { retryLive(scope, eventSource) }
            override fun onFailure(eventSource: okhttp3.sse.EventSource, t: Throwable?, response: okhttp3.Response?) {
                viewModelScope.launch { if (live !== eventSource) return@launch; if (scope == mutable.value.scope && response?.code == 401) { stopLive(); api.cookies.clear(); store.clear(); acceptMe(null) } else retryLive(scope, eventSource) }
            }
        })
    }
    private fun retryLive(scope: String, source: okhttp3.sse.EventSource) {
        viewModelScope.launch {
            if (!foreground || scope != mutable.value.scope || live !== source) return@launch
            live = null
            liveRetry?.cancel()
            liveRetry = viewModelScope.launch { delay(5000); if (foreground) startLive() }
        }
    }
    fun stopLive() {
        foreground = false
        liveRetry?.cancel(); liveRetry = null
        live?.cancel(); live = null
    }
    override fun onCleared() { stopLive(); super.onCleared() }
    /** Pending orders resume in the foreground; SSE reconciles the current board and floor. */
    fun reconnect() { syncPending() }
}

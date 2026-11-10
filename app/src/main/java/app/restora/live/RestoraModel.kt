package app.restora.live

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

/** A local draft, never presented as an order that has reached the kitchen. */
data class OrderDraft(
    val items: List<JSONObject> = emptyList(),
    val clientId: String = UUID.randomUUID().toString(),
    val editingOrder: JSONObject? = null,
    val checkout: Map<String, String> = emptyMap(),
) {
    fun toJson() = obj("items" to JSONArray(items), "clientId" to clientId, "editingOrder" to editingOrder, "checkout" to JSONObject(checkout))
}

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
    val draft: OrderDraft = OrderDraft(),
    val generation: Int = 0,
) {
    val workplace: JSONObject? get() = me?.optJSONObject("workplace")
    val editingOrder: JSONObject? get() = draft.editingOrder
    fun can(permission: String) = workplace?.strings("permissions")?.contains(permission) == true
    fun canOpenPage(page: String): Boolean = when (page) {
        "Home" -> can("reports:view")
        "Orders", "History", "WhatsApp" -> can("orders:view")
        "New order" -> can(if (editingOrder == null) "orders:create" else "orders:update")
        "Customers" -> can("customers:view")
        "Tables" -> can("tables:view")
        "Menu" -> can("menu:view")
        "Reservations" -> can("reservations:view")
        "Staff" -> can("staff:view")
        "Branches" -> can("branches:manage")
        "Roles" -> can("roles:manage")
        "Teams" -> can("teams:manage")
        "Settings" -> can("settings:manage")
        "Devices", "Printers" -> can("devices:manage")
        "Activity" -> can("audit:view")
        "More", "Account", "Workspace" -> true
        else -> false
    }
    val scope: String get() = listOf(me?.optJSONObject("user")?.text("id"), workplace?.optJSONObject("restaurant")?.text("_id"), workplace?.optJSONObject("branch")?.text("_id")).joinToString(":")
}

class RestoraModel(
    private val repository: RestoraDataSource,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    companion object {
        val Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[APPLICATION_KEY])
                RestoraModel(RestoraRepository(application), createSavedStateHandle())
            }
        }
    }

    private val mutable = MutableStateFlow(AppState(page = savedState["page"] ?: "Home"))
    val state = mutable.asStateFlow()
    private val jobs = mutableMapOf<String, Job>()
    private var live: EventSource? = null
    private var liveRetry: Job? = null
    private var foreground = false
    private var liveOpened = false
    private var retryDelay = 1_000L
    private var syncing = false

    init { start() }

    private fun start() = viewModelScope.launch {
        try {
            repository.initialize()
            val me = repository.request("/me") as? JSONObject ?: throw InvalidApiResponse()
            repository.write("me", me)
            mutable.update { it.copy(me = me) }
            restoreLocalWork()
            mutable.update { it.copy(starting = false) }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            try {
                if (error is ApiError && error.status == 401) repository.clearSession(clearData = false)
                val cached = if (isConnectionFailure(error)) (repository.read("me") as? JSONObject) else null
                mutable.update { it.copy(me = cached, offline = cached != null, error = if (error is ApiError && error.status == 401) null else error.message) }
                if (cached != null) restoreLocalWork()
            } catch (localError: Exception) {
                if (localError is CancellationException) throw localError
                mutable.update { it.copy(me = null, error = localError.message ?: "Saved data could not be restored") }
            } finally { mutable.update { it.copy(starting = false) } }
        }
    }

    fun notify(message: String) { mutable.update { it.copy(notice = message) } }
    fun showError(message: String) { mutable.update { it.copy(error = message) } }
    fun clearMessage() { mutable.update { it.copy(error = null, notice = null) } }
    fun navigate(page: String) {
        savedState["page"] = page
        mutable.update { it.copy(page = page, error = null) }
    }
    fun editOrder(order: JSONObject) {
        setDraft(OrderDraft(items = order.array("items").map { it.copyJson().put("lineKey", UUID.randomUUID().toString()) }, editingOrder = order.copyJson(), checkout = order.keys().asSequence().filter { order.opt(it) !is JSONObject && order.opt(it) !is JSONArray }.associateWith { order.text(it) }))
        navigate("New order")
    }
    fun setCart(items: List<JSONObject>) { setDraft(mutable.value.draft.copy(items = items.map { if (it.text("lineKey").isBlank()) it.copyJson().put("lineKey", UUID.randomUUID().toString()) else it })) }
    fun setCheckout(values: Map<String, String>) { setDraft(mutable.value.draft.copy(checkout = values)) }
    fun clearDraft() { setDraft(OrderDraft()) }
    private fun setDraft(draft: OrderDraft) {
        mutable.update { it.copy(draft = draft) }
        persist("draft:${mutable.value.scope}", draft.toJson())
    }
    private suspend fun restoreLocalWork() {
        val scope = mutable.value.scope
        val generation = mutable.value.generation
        val pending = (repository.read("outbox:$scope") as? JSONArray)?.objects().orEmpty()
        val stored = (repository.read("draft:$scope") as? JSONObject)
        val draft = stored?.let { OrderDraft(it.array("items").map { line -> if (line.text("lineKey").isBlank()) line.copyJson().put("lineKey", UUID.randomUUID().toString()) else line }, it.text("clientId").ifBlank { UUID.randomUUID().toString() }, it.optJSONObject("editingOrder"), it.optJSONObject("checkout")?.let { values -> values.keys().asSequence().associateWith { key -> values.text(key) } }.orEmpty()) } ?: OrderDraft()
        if (sameContext(scope, generation)) mutable.update { it.copy(outbox = pending, draft = draft) }
    }
    private suspend fun acceptMe(me: JSONObject?, clearData: Boolean? = null) {
        stopLive()
        val caller = currentCoroutineContext().job
        jobs.values.filter { it !== caller }.forEach(Job::cancel)
        jobs.clear()
        savedState["page"] = "Home"
        mutable.update { AppState(starting = true, me = me, busy = it.busy, generation = it.generation + 1) }
        try {
            if (clearData != null) repository.clearSession(clearData)
            if (me != null) {
                repository.write("me", me)
                restoreLocalWork()
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            showError(error.message ?: "Could not restore this workspace on the phone")
        } finally { mutable.update { it.copy(starting = false) } }
    }
    fun action(path: String, method: String = "POST", body: JSONObject = JSONObject(), failure: suspend (Exception) -> Unit = {}, done: (JSONObject) -> Unit = {}) {
        if (mutable.value.busy || mutable.value.starting) return
        mutable.update { it.copy(busy = true, error = null) }
        val scope = mutable.value.scope
        val generation = mutable.value.generation
        viewModelScope.launch {
            try {
                val answer = repository.request(path, method, body) as? JSONObject ?: throw InvalidApiResponse()
                if (!sameContext(scope, generation)) return@launch
                if (path in listOf("/me/workplace", "/me/branch")) acceptMe(answer)
                else if (answer.has("me")) acceptMe(answer.optJSONObject("me"))
                mutable.update { it.copy(offline = false) }
                done(answer)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (sameContext(scope, generation)) { handle(error); failure(error) }
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
    private suspend fun handle(error: Exception) {
        if (error is ApiError && error.status == 401) {
            // Expiration must not destroy an unpaid order waiting on this device.
            acceptMe(null, clearData = false)
        } else if (error is ApiError && error.answer.text("code") == "context_lost") acceptMe(error.answer.optJSONObject("me"))
        mutable.update { it.copy(error = error.message ?: "Unable to connect. Please try again.", offline = isConnectionFailure(error)) }
    }
    fun deleteAccount() = action("/me", "DELETE") { clearAccount() }
    fun signOut() = action("/auth/sign-out") { clearAccount() }
    private fun clearAccount() {
        viewModelScope.launch { acceptMe(null, clearData = true) }
    }
    private fun sameContext(scope: String, generation: Int) = mutable.value.scope == scope && mutable.value.generation == generation

    fun load(path: String, force: Boolean = false) {
        if (mutable.value.starting || jobs[path]?.isActive == true || (!force && mutable.value.data.containsKey(path))) return
        val scope = mutable.value.scope
        val generation = mutable.value.generation
        mutable.update { it.copy(loading = it.loading + path, error = null) }
        jobs[path] = viewModelScope.launch {
            try {
                if (!force) {
                    val cached = repository.read("$scope:$path")
                    if ((cached is JSONObject || cached is JSONArray) && sameContext(scope, generation)) mutable.update { it.copy(data = it.data + (path to cached)) }
                }
                val data = repository.request(path)
                if (sameContext(scope, generation)) {
                    mutable.update { it.copy(data = it.data + (path to data), offline = false) }
                    repository.write("$scope:$path", data)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (sameContext(scope, generation)) handle(error)
            } finally {
                if (jobs[path] === currentCoroutineContext().job) {
                    jobs.remove(path)
                    if (sameContext(scope, generation)) mutable.update { it.copy(loading = it.loading - path) }
                }
            }
        }
    }
    private fun persist(key: String, value: Any) {
        viewModelScope.launch {
            try { repository.write(key, value) }
            catch (error: Exception) { if (error is CancellationException) throw error; showError(error.message ?: "Could not save data on this device") }
        }
    }
    fun put(path: String, value: Any) {
        mutable.update { it.copy(data = it.data + (path to value)) }
        persist("${mutable.value.scope}:$path", value)
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
        put("/menu", menu.put(key, JSONArray(rows)))
    }
    fun updateProfile(name: String) {
        val me = mutable.value.me?.copyJson() ?: return
        me.getJSONObject("user").put("name", name)
        mutable.update { it.copy(me = me) }; persist("me", me)
    }
    fun updateBranch(branch: JSONObject) {
        val me = mutable.value.me?.copyJson() ?: return
        val workplace = me.optJSONObject("workplace") ?: return
        val hadAccess = workplace.array("branches").any { it.text("_id") == branch.text("_id") }
        val list = workplace.array("branches").filterNot { it.text("_id") == branch.text("_id") }.toMutableList()
        if (branch.text("archivedAt").isBlank() && (workplace.optBoolean("allBranches") || hadAccess)) list.add(branch)
        workplace.put("branches", JSONArray(list))
        if (workplace.optJSONObject("branch")?.text("_id") == branch.text("_id")) workplace.put("branch", if (branch.text("archivedAt").isBlank()) branch else JSONObject.NULL)
        mutable.update { it.copy(me = me) }; persist("me", me)
    }
    fun updateSettings(settings: JSONObject) {
        val me = mutable.value.me?.copyJson() ?: return
        val workplace = me.optJSONObject("workplace") ?: return
        workplace.put("settings", settings)
        if (settings.has("restaurantName")) {
            workplace.getJSONObject("restaurant").put("name", settings.text("restaurantName"))
            me.array("restaurants").firstOrNull { it.text("_id") == workplace.getJSONObject("restaurant").text("_id") }?.put("name", settings.text("restaurantName"))
        }
        mutable.update { it.copy(me = me) }; persist("me", me)
    }
    private suspend fun saveOutbox(rows: List<JSONObject>): Boolean {
        val scope = mutable.value.scope
        val generation = mutable.value.generation
        repository.write("outbox:$scope", JSONArray(rows))
        val current = sameContext(scope, generation)
        if (current) mutable.update { it.copy(outbox = rows) }
        return current
    }
    fun discardPending(clientId: String) {
        viewModelScope.launch { try { saveOutbox(mutable.value.outbox.filterNot { it.text("clientId") == clientId }) } catch (error: Exception) { if (error is CancellationException) throw error; handle(error) } }
    }
    fun createOrder(body: JSONObject, done: (JSONObject) -> Unit) {
        action("/orders", body = body, failure = { error ->
            if (isConnectionFailure(error) && body.text("paymentStatus") !in listOf("paid", "khata")) {
                try {
                    val rows = if (mutable.value.outbox.none { it.text("clientId") == body.text("clientId") }) mutable.value.outbox + body.copyJson() else mutable.value.outbox
                    if (saveOutbox(rows)) {
                        clearDraft(); navigate("Orders")
                        mutable.update { it.copy(error = null, notice = "Saved on this device. Waiting to send to the kitchen.") }
                    }
                } catch (storageError: Exception) { if (storageError is CancellationException) throw storageError; handle(storageError) }
            }
        }, done = done)
    }
    fun syncPending(retryFailed: Boolean = false) {
        if (syncing || mutable.value.outbox.isEmpty()) return
        syncing = true
        val scope = mutable.value.scope
        val generation = mutable.value.generation
        viewModelScope.launch {
            try {
                for (pending in mutable.value.outbox.toList()) {
                    if (pending.has("syncError") && !retryFailed) continue
                    if (!sameContext(scope, generation)) break
                    try {
                        val saved = repository.request("/orders", "POST", pending) as? JSONObject ?: throw InvalidApiResponse()
                        if (!sameContext(scope, generation)) break
                        val path = "/orders?date=${saved.text("dayKey")}"
                        if (mutable.value.data.containsKey(path)) upsert(path, saved)
                        if (!saveOutbox(mutable.value.outbox.filterNot { it.text("clientId") == pending.text("clientId") })) break
                        mutable.update { it.copy(notice = "Order #${saved.text("dailyToken")} sent", offline = false) }
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        if (!sameContext(scope, generation)) break
                        handle(error)
                        if (error is ApiError && sameContext(scope, generation)) saveOutbox(mutable.value.outbox.map { if (it.text("clientId") == pending.text("clientId")) it.copyJson().put("syncError", error.message) else it })
                        break
                    }
                }
            } finally { syncing = false }
        }
    }
    fun startLive() {
        foreground = true
        if (mutable.value.starting || live != null || !mutable.value.can("orders:view")) return
        val scope = mutable.value.scope
        val generation = mutable.value.generation
        live = repository.openEvents(object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                viewModelScope.launch {
                    if (!sameContext(scope, generation) || live !== eventSource) return@launch
                    retryDelay = 1_000L
                    mutable.update { it.copy(offline = false) }
                    val day = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Karachi"))
                    if (liveOpened) mutable.value.data.keys.filter { it == "/orders?date=$day" || it == "/tables/floor" }.forEach { load(it, true) }
                    liveOpened = true
                    syncPending()
                }
            }
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                // OkHttp invokes this on its IO thread, before crossing into UI state.
                val record = parseObject(data)
                viewModelScope.launch {
                    if (!sameContext(scope, generation) || live !== eventSource) return@launch
                    if (record == null) { showError("A live update could not be read. Refresh this page."); return@launch }
                    if (type?.startsWith("order.") == true) {
                        mutable.value.data.keys.filter { it.startsWith("/orders?date=") && it.substringAfter("date=").take(10) == record.text("dayKey") }.forEach { upsert(it, record) }
                        (mutable.value.data["/tables/floor"] as? JSONArray)?.let { floor ->
                            put("/tables/floor", JSONArray(floor.objects().map { table ->
                                val orders = table.array("orders").filterNot { it.text("_id") == record.text("_id") }.toMutableList()
                                if (record.text("tableId") == table.text("_id") && record.text("status") != "cancelled" && !(record.text("status") == "completed" && record.text("paymentStatus") == "paid")) orders.add(record)
                                if (table.text("_id") == record.text("tableId") || table.array("orders").any { it.text("_id") == record.text("_id") }) table.copyJson().put("orders", JSONArray(orders)) else table
                            }))
                        }
                    } else if (type?.startsWith("call.") == true) {
                        (mutable.value.data["/tables/floor"] as? JSONArray)?.let { floor ->
                            put("/tables/floor", JSONArray(floor.objects().map { table ->
                                val requests = table.array("requests").filterNot { it.text("_id") == record.text("_id") }.toMutableList()
                                if (!record.optBoolean("resolved") && record.text("tableId") == table.text("_id")) requests.add(record)
                                if (table.text("_id") == record.text("tableId") || table.array("requests").any { it.text("_id") == record.text("_id") }) table.copyJson().put("requests", JSONArray(requests)) else table
                            }))
                        }
                    }
                }
            }
            override fun onClosed(eventSource: EventSource) { retryLive(scope, generation, eventSource) }
            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                viewModelScope.launch {
                    if (live !== eventSource || !sameContext(scope, generation)) return@launch
                    if (response?.code == 401) { stopLive(); handle(ApiError(401, obj("message" to "Sign in to reconnect"))) }
                    else retryLive(scope, generation, eventSource)
                }
            }
        })
    }
    private fun retryLive(scope: String, generation: Int, source: EventSource) {
        viewModelScope.launch {
            if (!foreground || !sameContext(scope, generation) || live !== source) return@launch
            live = null
            liveRetry?.cancel()
            val wait = retryDelay
            retryDelay = (retryDelay * 2).coerceAtMost(30_000L)
            liveRetry = viewModelScope.launch { delay(wait); if (foreground) startLive() }
        }
    }
    fun stopLive() {
        foreground = false
        liveRetry?.cancel(); liveRetry = null
        live?.cancel(); live = null
    }
    override fun onCleared() { stopLive() }
    fun reconnect() { if (!mutable.value.starting) syncPending() }
}

private fun parseObject(raw: String): JSONObject? = try { JSONObject(raw) } catch (_: org.json.JSONException) { null }
private fun isConnectionFailure(error: Exception) = error is IOException && error !is InvalidApiResponse

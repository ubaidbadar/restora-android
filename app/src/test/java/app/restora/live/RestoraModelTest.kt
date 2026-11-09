package app.restora.live

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

private fun account(branch: String = "a") = obj(
    "user" to obj("id" to "owner", "name" to "Owner"),
    "workplace" to obj("restaurant" to obj("_id" to "restaurant"), "branch" to obj("_id" to branch), "permissions" to JSONArray(listOf("orders:view", "orders:create"))),
)

private class FakeDataSource : RestoraDataSource {
    val stored = mutableMapOf<String, String>()
    val requests = mutableListOf<String>()
    val cleared = mutableListOf<Boolean>()
    var beforeWrite: suspend (String) -> Unit = {}
    var eventsOpened = 0
    var eventsCancelled = 0
    var handle: suspend (String, String, JSONObject?) -> Any = { path, _, _ -> if (path == "/me") account() else JSONArray() }
    override suspend fun initialize() {}
    override suspend fun read(key: String): Any? = stored[key]?.let { org.json.JSONTokener(it).nextValue().takeUnless { value -> value == JSONObject.NULL } }
    override suspend fun write(key: String, value: Any) { beforeWrite(key); stored[key] = value.toString() }
    override suspend fun clearSession(clearData: Boolean) { cleared += clearData; if (clearData) stored.clear() else { stored["me"] = "null"; stored["cookies"] = "[]" } }
    override suspend fun request(path: String, method: String, body: JSONObject?): Any { requests += path; return handle(path, method, body) }
    override fun openEvents(listener: EventSourceListener): EventSource {
        eventsOpened++
        return object : EventSource {
            override fun request() = Request.Builder().url("https://example.invalid/orders/stream").build()
            override fun cancel() { eventsCancelled++ }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RestoraModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { models.clear(); Dispatchers.resetMain() }
    private fun model(source: FakeDataSource, key: String = "model", savedState: SavedStateHandle = SavedStateHandle()): RestoraModel = ViewModelProvider(models, viewModelFactory { initializer { RestoraModel(source, savedState) } })[key, RestoraModel::class.java]

    @Test fun oneStartupReadAndSharedScreenRead() = runTest(dispatcher) {
        val source = FakeDataSource()
        val answer = CompletableDeferred<Any>()
        source.handle = { path, _, _ -> if (path == "/me") account() else answer.await() }
        val model = model(source); advanceUntilIdle()
        model.load("/customers"); model.load("/customers"); runCurrent()
        assertEquals(1, source.requests.count { it == "/me" })
        assertEquals(1, source.requests.count { it == "/customers" })
        assertTrue("/customers" in model.state.value.loading)
        answer.complete(JSONArray(listOf(obj("_id" to "guest-1")))); advanceUntilIdle()
        model.load("/customers"); advanceUntilIdle()
        assertEquals(1, source.requests.count { it == "/customers" })
        assertTrue(model.state.value.loading.isEmpty())
    }

    @Test fun cancelledOldBranchReadCannotSignOutNewBranchOrClearItsLoading() = runTest(dispatcher) {
        val source = FakeDataSource()
        val old = CompletableDeferred<Unit>(); val next = CompletableDeferred<Any>()
        var reads = 0
        source.handle = { path, _, _ -> when (path) {
            "/me" -> account()
            "/me/branch" -> account("b")
            else -> if (++reads == 1) withContext(NonCancellable) { old.await(); throw ApiError(401, obj("message" to "Old session expired")) } else next.await()
        } }
        val model = model(source); advanceUntilIdle()
        model.load("/customers"); runCurrent()
        model.action("/me/branch", body = obj("branchId" to "b")) { model.load("/customers") }; runCurrent()
        old.complete(Unit); runCurrent()
        assertEquals("b", model.state.value.workplace?.optJSONObject("branch")?.text("_id"))
        assertTrue("/customers" in model.state.value.loading)
        assertTrue(source.cleared.isEmpty())
        next.complete(JSONArray(listOf(obj("_id" to "new-branch-guest")))); advanceUntilIdle()
        assertEquals("new-branch-guest", (model.state.value.data["/customers"] as JSONArray).getJSONObject(0).text("_id"))
    }

    @Test fun unpaidOrderSurvivesSessionExpiryAndReturnsAfterSignIn() = runTest(dispatcher) {
        val source = FakeDataSource(); val model = model(source); advanceUntilIdle()
        source.handle = { path, _, _ -> when (path) {
            "/orders" -> throw IOException("Offline")
            "/protected" -> throw ApiError(401, obj("message" to "Sign in"))
            "/auth/sign-in/email" -> obj("me" to account())
            else -> account()
        } }
        model.createOrder(obj("clientId" to "order-unique-123", "items" to JSONArray(), "paymentStatus" to "unpaid")) {}; advanceUntilIdle()
        assertEquals(1, model.state.value.outbox.size)
        model.load("/protected"); advanceUntilIdle()
        assertNull(model.state.value.me)
        assertEquals(listOf(false), source.cleared)
        model.action("/auth/sign-in/email"); advanceUntilIdle()
        assertEquals("order-unique-123", model.state.value.outbox.single().text("clientId"))
    }

    @Test fun malformedSuccessIsNeverQueuedAsAnOfflineOrder() = runTest(dispatcher) {
        val source = FakeDataSource(); val model = model(source); advanceUntilIdle()
        source.handle = { _, _, _ -> throw InvalidApiResponse() }
        model.createOrder(obj("clientId" to "stable-id", "paymentStatus" to "unpaid")) {}; advanceUntilIdle()
        assertTrue(model.state.value.outbox.isEmpty())
        assertFalse(model.state.value.offline)
        assertTrue(model.state.value.error.orEmpty().contains("JSON"))
    }

    @Test fun paidOrdersAreNeverQueued() = runTest(dispatcher) {
        val source = FakeDataSource(); val model = model(source); advanceUntilIdle()
        source.handle = { _, _, _ -> throw IOException("Offline") }
        model.createOrder(obj("clientId" to "stable-paid", "paymentStatus" to "paid")) {}; advanceUntilIdle()
        assertTrue(model.state.value.outbox.isEmpty())
    }

    @Test fun cartAndCheckoutRestoreFromEncryptedRepositoryData() = runTest(dispatcher) {
        val source = FakeDataSource(); val first = model(source, "first"); advanceUntilIdle()
        val clientId = first.state.value.draft.clientId
        first.setCart(listOf(obj("itemId" to "dish-1", "qty" to 2, "price" to 250)))
        first.setCheckout(mapOf("type" to "delivery", "address" to "Faisalabad")); first.navigate("New order"); advanceUntilIdle()
        val second = model(source, "second", SavedStateHandle(mapOf("page" to "New order"))); advanceUntilIdle()
        assertEquals(clientId, second.state.value.draft.clientId)
        assertEquals(2, second.state.value.draft.items.single().optInt("qty"))
        assertTrue(second.state.value.draft.items.single().text("lineKey").isNotBlank())
        assertEquals("Faisalabad", second.state.value.draft.checkout["address"])
        assertEquals("New order", second.state.value.page)
    }

    @Test fun rejectedOutboxOrderWaitsForExplicitRetry() = runTest(dispatcher) {
        val source = FakeDataSource(); val model = model(source); advanceUntilIdle()
        source.handle = { _, _, _ -> throw IOException("Offline") }
        model.createOrder(obj("clientId" to "stable-order", "paymentStatus" to "unpaid")) {}; advanceUntilIdle()
        source.handle = { _, _, _ -> throw ApiError(400, obj("message" to "That dish is sold out")) }
        model.syncPending(); advanceUntilIdle()
        val requests = source.requests.size
        assertEquals("That dish is sold out", model.state.value.outbox.single().text("syncError"))
        model.syncPending(); advanceUntilIdle(); assertEquals(requests, source.requests.size)
        model.syncPending(true); advanceUntilIdle(); assertEquals(requests + 1, source.requests.size)
    }

    @Test fun foregroundStreamHasOneOwnerAndClosesOnStop() = runTest(dispatcher) {
        val source = FakeDataSource(); val model = model(source); advanceUntilIdle()
        model.startLive(); model.startLive(); assertEquals(1, source.eventsOpened)
        model.stopLive(); assertEquals(1, source.eventsCancelled)
        model.startLive(); assertEquals(2, source.eventsOpened)
        model.stopLive()
    }
    @Test fun contextLostReadFinishesRestoringItsReplacementContext() = runTest(dispatcher) {
        val source = FakeDataSource(); val model = model(source); advanceUntilIdle()
        source.handle = { _, _, _ -> throw ApiError(403, obj("code" to "context_lost", "message" to "Your branch changed", "me" to account("replacement"))) }
        model.load("/customers"); advanceUntilIdle()
        assertEquals("replacement", model.state.value.workplace?.optJSONObject("branch")?.text("_id"))
        assertFalse(model.state.value.starting)
        assertTrue(model.state.value.loading.isEmpty())
    }

    @Test fun offlineDoubleTapStaysBlockedUntilTheOrderIsDurable() = runTest(dispatcher) {
        val source = FakeDataSource(); val model = model(source); advanceUntilIdle()
        val saved = CompletableDeferred<Unit>()
        source.beforeWrite = { key -> if (key.startsWith("outbox:")) saved.await() }
        source.handle = { _, _, _ -> throw IOException("Offline") }
        val body = obj("clientId" to "one-order", "paymentStatus" to "unpaid")
        model.createOrder(body) {}; runCurrent()
        assertTrue(model.state.value.busy)
        model.createOrder(body) {}; runCurrent()
        assertEquals(1, source.requests.count { it == "/orders" })
        saved.complete(Unit); advanceUntilIdle()
        assertFalse(model.state.value.busy)
        assertEquals(1, model.state.value.outbox.size)
    }

    @Test fun restoredRoutesRespectTheLatestPermissions() {
        val me = account()
        me.getJSONObject("workplace").put("permissions", JSONArray(listOf("reports:view")))
        val state = AppState(starting = false, me = me, page = "Orders")
        assertFalse(state.canOpenPage("Orders"))
        assertFalse(state.canOpenPage("New order"))
        assertFalse(state.canOpenPage("Settings"))
        assertTrue(state.canOpenPage("Home"))
        assertTrue(state.canOpenPage("Account"))
    }

}

package app.restora.live

import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.ZonedDateTime

private class MemoryStore : StringStore {
    private val values = mutableMapOf<String, String>()
    override fun read(name: String) = values[name]
    override fun write(name: String, value: String) { values[name] = value }
    override fun clear() { values.clear() }
}

class RestoraContractTest {
    @Test fun signInUsesNativeOriginAndExistingApiPath() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("{\"me\":{\"user\":{\"id\":\"user-1\"}}}"))
        server.start()
        try {
            val api = RestoraApi(MemoryStore(), server.url("/").toString())
            val result = api.call("/auth/sign-in/email", "POST", obj("email" to "owner@example.com", "password" to "password123")) as JSONObject
            assertEquals("user-1", result.getJSONObject("me").getJSONObject("user").getString("id"))
            val request = server.takeRequest()
            assertEquals("/api/v1/auth/sign-in/email", request.path)
            assertEquals("restora://", request.getHeader("Origin"))
            assertEquals("owner@example.com", JSONObject(request.body.readUtf8()).getString("email"))
        } finally { server.shutdown() }
    }
    @Test fun authCookieSurvivesClientRecreationAndIsSentToMe() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            val store = MemoryStore()
            val origin = server.url("/").toString()
            server.enqueue(MockResponse().addHeader("Set-Cookie", "better-auth.session_token=signed; Path=/; Max-Age=3600; HttpOnly").setBody("{}"))
            RestoraApi(store, origin).call("/auth/sign-in/email", "POST", JSONObject())
            server.takeRequest()
            server.enqueue(MockResponse().setBody("{\"user\":{}}"))
            RestoraApi(store, origin).call("/me")
            assertTrue(server.takeRequest().getHeader("Cookie").orEmpty().contains("better-auth.session_token=signed"))
        } finally { server.shutdown() }
    }
    @Test fun cookieDeletionAndScopeAreRespected() {
        val store = MemoryStore(); val jar = SessionCookies(store)
        val root = "https://restora.live/".toHttpUrl()
        jar.saveFromResponse(root, listOf(Cookie.parse(root, "session=abc; Path=/api; Secure; HttpOnly; Max-Age=3600")!!))
        assertEquals(1, jar.loadForRequest("https://restora.live/api/v1/me".toHttpUrl()).size)
        assertTrue(jar.loadForRequest("https://other.example/api/v1/me".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("http://restora.live/api/v1/me".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("https://restora.live/health".toHttpUrl()).isEmpty())
        jar.saveFromResponse(root, listOf(Cookie.parse(root, "session=; Path=/api; Max-Age=0")!!))
        assertTrue(SessionCookies(store).loadForRequest("https://restora.live/api/v1/me".toHttpUrl()).isEmpty())
    }
    @Test fun serverErrorsKeepTheActionableMessage() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(403).setBody("{\"code\":\"reauth_required\",\"message\":\"Sign in again before changing access\"}"))
            try { RestoraApi(MemoryStore(), server.url("/").toString()).call("/me/pin", "POST"); fail("Expected API failure") }
            catch (e: ApiError) { assertEquals(403, e.status); assertEquals("reauth_required", e.answer.text("code")); assertEquals("Sign in again before changing access", e.message) }
        } finally { server.shutdown() }
    }
    @Test fun hostingHtmlCannotBeMistakenForApiData() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setBody("<!doctype html><html>Restora</html>"))
            try { RestoraApi(MemoryStore(), server.url("/").toString()).call("/menu"); fail("Expected invalid response") }
            catch (e: IOException) { assertTrue(e.message.orEmpty().contains("JSON")) }
        } finally { server.shutdown() }
    }
    @Test fun dealAvailabilityHandlesDaysBoundariesAndOvernightHours() {
        val monday = ZonedDateTime.parse("2026-11-09T23:00:00+05:00[Asia/Karachi]")
        val deal = obj("days" to JSONArray(listOf(1)), "from" to "22:00", "to" to "02:00")
        assertTrue(onSale(deal, monday))
        assertFalse(onSale(deal, monday.withHour(12)))
        assertFalse(onSale(deal, monday.plusDays(1)))
        deal.put("days", JSONArray())
        assertTrue(onSale(deal, monday.plusDays(1).withHour(1)))
        assertFalse(onSale(deal, monday.plusDays(1).withHour(2)))
        deal.put("from", "").put("to", "")
        assertTrue(onSale(deal, monday.withHour(12)))
    }
}

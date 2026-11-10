package app.restora.live

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.restora.live.ui.theme.RestoraTheme
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import org.json.JSONArray
import org.json.JSONObject

/** Preview fixtures live only in the debug source set; the running app never substitutes them for API data. */
private class PreviewSource : RestoraDataSource {
    override suspend fun initialize() {}
    override suspend fun read(key: String): Any? = null
    override suspend fun write(key: String, value: Any) {}
    override suspend fun clearSession(clearData: Boolean) {}
    override suspend fun request(path: String, method: String, body: JSONObject?): Any = throw ApiError(401, obj("message" to "Preview"))
    override fun openEvents(listener: EventSourceListener): EventSource = error("Previews never open a stream")
}

@Preview(name = "Sign in · phone", widthDp = 360, heightDp = 800, showBackground = true)
@Composable private fun SignInPreview() {
    RestoraTheme { AuthScreen(AppState(starting = false), remember { RestoraModel(PreviewSource()) }) }
}

@Preview(name = "Order card · large text", widthDp = 360, fontScale = 1.5f, showBackground = true)
@Composable private fun OrderPreview() {
    RestoraTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(Modifier.padding(20.dp)) { OrderCard(obj("_id" to "preview-order", "dailyToken" to 24, "type" to "dine_in", "tableName" to "Table 4", "status" to "preparing", "paymentStatus" to "unpaid", "total" to 2450, "customer" to obj("name" to "Preview guest"), "items" to JSONArray(listOf(obj("name" to "Dish", "qty" to 2))))) {} }
        }
    }
}

@Preview(name = "Sales overview · phone", widthDp = 360, heightDp = 440, showBackground = true)
@Composable private fun SalesPreview() {
    RestoraTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                app.restora.live.ui.PageHeading("Overview", "Your restaurant, at a glance.") {}
                SalesSummary(obj("sales" to 28450, "orderCount" to 18, "newCustomers" to 4))
            }
        }
    }
}

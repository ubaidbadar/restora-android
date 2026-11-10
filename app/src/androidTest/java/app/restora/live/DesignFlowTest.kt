package app.restora.live

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import java.io.File
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.restora.live.ui.Field
import app.restora.live.ui.FormDialog
import app.restora.live.ui.theme.RestoraTheme
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs without a real account or any business API calls. */
private class SignedOutSource : RestoraDataSource {
    override suspend fun initialize() {}
    override suspend fun read(key: String): Any? = null
    override suspend fun write(key: String, value: Any) {}
    override suspend fun clearSession(clearData: Boolean) {}
    override suspend fun request(path: String, method: String, body: JSONObject?): Any = throw ApiError(401, obj("message" to "Sign in"))
    override fun openEvents(listener: EventSourceListener): EventSource = error("A signed-out screen must not open a stream")
}

@RunWith(AndroidJUnit4::class)
class DesignFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun signInIsInlineAndAccountCreationIsAnExplicitChoice() {
        compose.setContent { RestoraTheme { AuthScreen(AppState(starting = false), remember { RestoraModel(SignedOutSource()) }) } }
        compose.onNodeWithText("Welcome back.").assertIsDisplayed()
        compose.onNodeWithText("Email address *").assertIsDisplayed()
        compose.onNodeWithText("Password *").assertIsDisplayed()
        compose.onNodeWithText("Save changes").assertDoesNotExist()
        compose.onNodeWithText("Create account").performClick()
        compose.onNodeWithText("Full name *").assertIsDisplayed()
    }

    @Test fun ticketRemainsReadableAndClickableAtLargeFontScale() {
        var opened = false
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                RestoraTheme {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.width(360.dp).padding(20.dp)) {
                            OrderCard(obj("dailyToken" to 24, "type" to "dine_in", "tableName" to "Table 4", "status" to "preparing", "paymentStatus" to "unpaid", "total" to 2450, "items" to org.json.JSONArray(listOf(obj("name" to "Chicken karahi", "qty" to 2))))) { opened = true }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Preparing").assertIsDisplayed()
        compose.onNodeWithText("Rs 2,450").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(opened) }
    }

    @Test fun overviewComponentHasOneSalesValueAndExportsNativePreview() {
        compose.setContent {
            RestoraTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.width(360.dp).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        app.restora.live.ui.PageHeading("Overview", "Your restaurant, at a glance.") {}
                        SalesSummary(obj("sales" to 28450, "orderCount" to 18, "newCustomers" to 4))
                        OrderCard(obj("dailyToken" to 24, "type" to "dine_in", "tableName" to "Table 4", "status" to "preparing", "paymentStatus" to "unpaid", "total" to 2450, "items" to org.json.JSONArray(listOf(obj("name" to "Chicken karahi", "qty" to 2))))) {}
                    }
                }
            }
        }
        compose.onNodeWithText("Rs 28,450").assertIsDisplayed()
        compose.onNodeWithText("New guests").assertIsDisplayed()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.cacheDir, "restora-native-components.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun formRestoresNonSecretInputsButDoesNotSavePasswords() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { RestoraTheme { FormDialog("Test form", listOf(Field("name", "Guest name", true), Field("password", "Password", true, "password")), AppState(starting = false), {}) {} } }
        compose.onNodeWithText("Guest name *").performTextInput("Ubaid")
        compose.onNodeWithText("Password *").performTextInput("test-secret")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Guest name *").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("Ubaid")))
        compose.onNodeWithText("Password *").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
    }
}

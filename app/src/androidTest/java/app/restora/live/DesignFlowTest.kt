package app.restora.live

import androidx.compose.runtime.remember
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

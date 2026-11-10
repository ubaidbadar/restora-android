package app.restora.live

import androidx.compose.material3.*
import app.restora.live.ui.SecondaryButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

/** Uses Android Credential Manager. The web OAuth client ID is public build configuration, never a client secret. */
@Composable fun GoogleSignIn(state: AppState, model: RestoraModel) {
    if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf(false) }
    SecondaryButton(onClick = {
        pending = true
        scope.launch {
            try {
                val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
                val result = CredentialManager.create(context).getCredential(context, GetCredentialRequest.Builder().addCredentialOption(option).build())
                val token = GoogleIdTokenCredential.createFrom(result.credential.data).idToken
                model.action("/auth/sign-in/social", body = obj("provider" to "google", "idToken" to obj("token" to token)))
            } catch (_: NoCredentialException) {
                model.showError("No Google account is available. Add one to your phone, or sign in with email.")
            } catch (_: GetCredentialCancellationException) {
                // Dismissing the account chooser keeps the sign-in form available.
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                model.showError(e.message ?: "Google sign-in could not finish")
            } finally { pending = false }
        }
    }, enabled = !pending && !state.busy) { Text(if (pending) "Opening Google…" else "Continue with Google") }
}

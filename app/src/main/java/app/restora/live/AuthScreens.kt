package app.restora.live

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.restora.live.ui.*
import org.json.JSONArray

@Composable fun AuthScreen(state: AppState, model: RestoraModel) {
    var mode by remember { mutableStateOf("Sign in") }
    var email by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(48.dp)); Text("Restora", style = MaterialTheme.typography.displaySmall)
        Text("Your restaurant, in your pocket.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Orders, guests and your team — together.", style = MaterialTheme.typography.bodyLarge)
        GoogleSignIn(state, model)
        listOf("Sign in", "Create account", "Verify email", "Reset password").forEach { title ->
            Button(onClick = { model.clearMessage(); mode = title }, modifier = Modifier.fillMaxWidth(), enabled = !state.busy) { Text(title) }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (mode != "") {
            val fields = when (mode) {
                "Create account" -> listOf(Field("name", "Full name", true), Field("email", "Email", true, "email", initial = email), Field("password", "Password (at least 8 characters)", true, "password"))
                "Verify email" -> listOf(Field("email", "Email", true, "email", initial = email), Field("otp", "Email code", true))
                "Reset password" -> listOf(Field("email", "Email", true, "email", initial = email))
                "Choose password" -> listOf(Field("email", "Email", true, "email", initial = email), Field("otp", "Email code", true), Field("password", "New password (at least 8 characters)", true, "password"))
                else -> listOf(Field("email", "Email", true, "email", initial = email), Field("password", "Password", true, "password"))
            }
            FormDialog(mode, fields, state, { mode = "" }) { body ->
                email = body.text("email")
                val path = when (mode) { "Create account" -> "/auth/sign-up/email"; "Verify email" -> "/auth/email-otp/verify-email"; "Reset password" -> "/auth/request-password-reset"; "Choose password" -> "/auth/email-otp/reset-password"; else -> "/auth/sign-in/email" }
                val submittedMode = mode
                model.action(path, body = body, failure = { e ->
                    if (e is ApiError && e.answer.text("code") == "EMAIL_NOT_VERIFIED") { model.clearMessage(); mode = "Verify email" }
                }) {
                    mode = when (submittedMode) { "Create account" -> "Verify email"; "Reset password" -> "Choose password"; "Choose password" -> "Sign in"; else -> "" }
                }
            }
        }
    }
}

@Composable fun WorkspaceScreen(state: AppState, model: RestoraModel) {
    var create by remember { mutableStateOf(false) }
    var deleteAccount by remember { mutableStateOf(false) }
    LaunchedEffect(state.me?.optJSONObject("user")?.text("id")) { model.load("/my-invitations") }
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Choose your workspace", style = MaterialTheme.typography.headlineSmall)
        Text(state.me?.optJSONObject("user")?.text("email").orEmpty())
        state.me?.array("restaurants")?.forEach { r ->
            OutlinedButton(onClick = { model.action("/me/workplace", body = obj("restaurantId" to r.text("_id"))) }, enabled = !state.busy && r.text("status").isBlank(), modifier = Modifier.fillMaxWidth()) { Text(r.text("name") + if (r.text("status").isNotBlank()) " · ${r.text("status")}" else "") }
        }
        state.workplace?.array("branches")?.forEach { branch ->
            TextButton(onClick = { model.action("/me/branch", body = obj("branchId" to branch.text("_id"))) }, enabled = !state.busy) { Text("Work at ${branch.text("name")}") }
        }
        val invitations = (state.data["/my-invitations"] as? JSONArray)?.objects().orEmpty()
        invitations.forEach { invitation -> Panel {
            Text(invitation.optJSONObject("restaurant")?.text("name") ?: invitation.text("restaurantName"))
            Button(onClick = { model.action("/my-invitations/${invitation.text("_id")}/accept") }, enabled = !state.busy) { Text("Accept invitation") }
        } }
        Button(onClick = { create = true }) { Text("Create restaurant") }
        TextButton(onClick = { model.signOut() }, enabled = !state.busy) { Text("Sign out") }
        TextButton(onClick = { deleteAccount = true }) { Text("Delete account", color = MaterialTheme.colorScheme.error) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    if (deleteAccount) Confirm("Delete account permanently?", "Your profile and every session will be deleted. Transfer or delete owned restaurants first.", state, { deleteAccount = false }) { model.deleteAccount() }
    if (create) FormDialog("Create restaurant", listOf(Field("name", "Restaurant name", true), Field("branchName", "Branch name", true), Field("area", "Area"), Field("city", "City"), Field("phone", "Phone", type = "phone")), state, { create = false }) {
        model.action("/onboarding", body = it) { create = false }
    }
}

package app.restora.live

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.restora.live.ui.*
import org.json.JSONArray

@Composable fun AuthScreen(state: AppState, model: RestoraModel) {
    var mode by rememberSaveable { mutableStateOf("Sign in") }
    var email by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var validation by rememberSaveable { mutableStateOf<String?>(null) }
    val title = when (mode) { "Sign in" -> "Welcome back."; "Create account" -> "A fresh start for\nyour restaurant."; "Verify email" -> "Check your inbox."; "Choose password" -> "Choose a new password."; else -> "Forgot your password?" }
    val subtitle = when (mode) { "Sign in" -> "Sign in to keep your service running smoothly."; "Create account" -> "Create your account. Bring your team together."; "Verify email" -> "Enter the code we sent to your email."; "Choose password" -> "Enter your email code and a new password."; else -> "We'll send you a code to get back in." }
    BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        Column(
            Modifier.widthIn(max = 460.dp).fillMaxWidth().align(Alignment.TopCenter).heightIn(min = maxHeight).verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BrandMark(); Text("Restora", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, style = MaterialTheme.typography.headlineLarge)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
            }
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (mode == "Create account") FormInput(Field("name", "Full name", true), name, { name = it }, !state.busy)
                FormInput(Field("email", "Email address", true, "email"), email, { email = it }, !state.busy)
                if (mode in listOf("Verify email", "Choose password")) FormInput(Field("otp", "6-digit email code", true), code, { code = it }, !state.busy)
                if (mode in listOf("Sign in", "Create account", "Choose password")) FormInput(Field("password", if (mode == "Choose password") "New password" else "Password", true, "password"), password, { password = it }, !state.busy)
                if (mode == "Sign in") TextButton(onClick = { mode = "Reset password"; model.clearMessage(); validation = null }, modifier = Modifier.align(Alignment.End)) { Text("Forgot password?") }
                (validation ?: state.error)?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                PrimaryButton(onClick = {
                    validation = when {
                        email.isBlank() || !email.contains('@') -> "Enter your email address"
                        mode == "Create account" && name.isBlank() -> "Enter your name"
                        mode in listOf("Sign in", "Create account", "Choose password") && password.isBlank() -> "Enter your password"
                        mode in listOf("Verify email", "Choose password") && code.isBlank() -> "Enter the code from your email"
                        else -> null
                    }
                    if (validation == null) {
                        val submittedMode = mode
                        val path = when (mode) { "Create account" -> "/auth/sign-up/email"; "Verify email" -> "/auth/email-otp/verify-email"; "Reset password" -> "/auth/request-password-reset"; "Choose password" -> "/auth/email-otp/reset-password"; else -> "/auth/sign-in/email" }
                        model.action(path, body = obj("email" to email.trim(), "name" to name.trim(), "password" to password, "otp" to code), failure = { error ->
                            if (error is ApiError && error.answer.text("code") == "EMAIL_NOT_VERIFIED") { model.clearMessage(); mode = "Verify email" }
                        }) {
                            password = ""; code = ""
                            mode = when (submittedMode) { "Create account" -> "Verify email"; "Reset password" -> "Choose password"; "Choose password" -> "Sign in"; else -> "Sign in" }
                        }
                    }
                }, enabled = !state.busy, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(if (state.busy) "Please wait…" else when (mode) { "Reset password" -> "Send reset code"; "Verify email" -> "Verify email"; "Choose password" -> "Update password"; else -> mode })
                }
                if (mode in listOf("Sign in", "Create account")) GoogleSignIn(state, model)
            }
            Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                Text(if (mode == "Sign in") "New to Restora?" else "Already have an account?", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { mode = if (mode == "Sign in") "Create account" else "Sign in"; password = ""; code = ""; validation = null; model.clearMessage() }) { Text(if (mode == "Sign in") "Create account" else "Sign in") }
            }
            Text("A calmer day. A better service.", modifier = Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable fun WorkspaceScreen(state: AppState, model: RestoraModel) {
    var create by rememberSaveable { mutableStateOf(false) }
    var deleteAccount by rememberSaveable { mutableStateOf(false) }
    var signOut by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.me?.optJSONObject("user")?.text("id")) { model.load("/my-invitations") }
    val restaurants = state.me?.array("restaurants").orEmpty()
    val branches = state.workplace?.array("branches").orEmpty()
    val invitationsRaw = state.data["/my-invitations"]
    val invitations = remember(invitationsRaw) { (invitationsRaw as? JSONArray)?.objects().orEmpty() }
    LazyColumn(
        modifier = Modifier.fillMaxSize().then(if (state.workplace == null) Modifier.systemBarsPadding() else Modifier),
        contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { BrandMark(); Text("Restora", style = MaterialTheme.typography.titleLarge) } }
        item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Where are we working?", style = MaterialTheme.typography.headlineMedium); Text("Choose a workspace to start your service.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        if (restaurants.isNotEmpty()) item { SectionLabel("Your restaurants") }
        items(restaurants, key = { "restaurant:${it.text("_id")}" }) { restaurant ->
            val available = restaurant.text("status") !in listOf("suspended", "deleting")
            Surface(onClick = { model.action("/me/workplace", body = obj("restaurantId" to restaurant.text("_id"))) }, enabled = !state.busy && available, color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.size(44.dp)) { Box(contentAlignment = Alignment.Center) { Text(restaurant.text("name").take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary) } }
                    Column(Modifier.weight(1f)) { Text(restaurant.text("name"), style = MaterialTheme.typography.titleMedium); if (!available) Text(label(restaurant.text("status")), style = MaterialTheme.typography.bodySmall) }
                    Icon(painterResource(R.drawable.ic_chevron), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (branches.isNotEmpty()) item { SectionLabel("Or switch branch") }
        items(branches, key = { "branch:${it.text("_id")}" }) { branch ->
            Surface(onClick = { model.action("/me/branch", body = obj("branchId" to branch.text("_id"))) }, enabled = !state.busy && branch.text("_id") != state.workplace?.optJSONObject("branch")?.text("_id"), color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Text(branch.text("name"), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall); Icon(painterResource(R.drawable.ic_switch), null, tint = MaterialTheme.colorScheme.primary) }
            }
        }
        if (invitations.isNotEmpty()) item { SectionLabel("Invitations for you") }
        items(invitations, key = { "invitation:${it.text("_id")}" }) { invitation -> Panel {
            Text(invitation.optJSONObject("restaurant")?.text("name") ?: invitation.text("restaurantName"), style = MaterialTheme.typography.titleMedium)
            PrimaryButton(onClick = { model.action("/my-invitations/${invitation.text("_id")}/accept") }, enabled = !state.busy) { Text("Accept invitation") }
        } }
        item { PrimaryButton(onClick = { create = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Text("Create a restaurant") } }
        item { Text(state.me?.optJSONObject("user")?.text("email").orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Row { TextButton(onClick = { signOut = true }) { Text("Sign out") }; TextButton(onClick = { deleteAccount = true }) { Text("Delete account", color = MaterialTheme.colorScheme.error) } } }
        state.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
    }
    if (signOut) Confirm("Sign out?", "Saved records and unsent orders on this phone will be cleared. Send waiting orders first.", state, { signOut = false }) { model.signOut() }
    if (deleteAccount) Confirm("Delete account permanently?", "Your profile and every session will be deleted. Transfer or delete owned restaurants first.", state, { deleteAccount = false }) { model.deleteAccount() }
    if (create) FormDialog("Create restaurant", listOf(Field("name", "Restaurant name", true), Field("branchName", "Branch name", true), Field("area", "Area"), Field("city", "City"), Field("phone", "Phone", type = "phone")), state, { create = false }) { model.action("/onboarding", body = it) { create = false } }
}

package app.restora.live

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.restora.live.ui.*
import org.json.JSONArray
import org.json.JSONObject

@Composable fun MoreScreen(state: AppState, model: RestoraModel) {
    Text("Everything in its place", style = MaterialTheme.typography.headlineSmall)
    Text("Tools for a smoother day.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    data class Link(val title: String, val detail: String, val icon: Int, val permission: String)
    val sections = listOf(
        "Service" to listOf(Link("Menu", "Dishes, prices and availability", R.drawable.ic_menu, "menu:view"), Link("Reservations", "Plan the next great evening", R.drawable.ic_calendar, "reservations:view"), Link("WhatsApp", "Conversations with your guests", R.drawable.ic_chat, "orders:view"), Link("History", "Find past orders and receipts", R.drawable.ic_receipt, "orders:view")),
        "Your restaurant" to listOf(Link("Staff", "People, invitations and access", R.drawable.ic_people, "staff:view"), Link("Branches", "Your locations", R.drawable.ic_home, "branches:manage"), Link("Roles", "Permissions for your team", R.drawable.ic_people, "roles:manage"), Link("Teams", "Keep people working together", R.drawable.ic_people, "teams:manage"), Link("Settings", "Restaurant, tax and receipts", R.drawable.ic_settings, "settings:manage")),
        "Connected tools" to listOf(Link("Devices", "Registered terminals", R.drawable.ic_table, "devices:manage"), Link("Printers", "Counter and kitchen printing", R.drawable.ic_receipt, "devices:manage"), Link("Activity", "What changed, and who changed it", R.drawable.ic_receipt, "audit:view")),
    )
    sections.forEach { (name, links) ->
        val allowed = links.filter { state.can(it.permission) }
        if (allowed.isNotEmpty()) {
            SectionLabel(name)
            Panel { allowed.forEachIndexed { index, link ->
                NavigationRow(link.title, link.detail, link.icon) { model.navigate(link.title) }
                if (index != allowed.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            } }
        }
    }
    Panel { NavigationRow("Your account", state.me?.optJSONObject("user")?.text("email").orEmpty(), R.drawable.ic_people) { model.navigate("Account") } }
}

@Composable fun SettingsScreen(state: AppState, model: RestoraModel) {
    var editing by remember { mutableStateOf("") }
    val settings = state.workplace?.optJSONObject("settings") ?: JSONObject()
    Text("Restaurant settings", style = MaterialTheme.typography.headlineSmall)
    Panel {
        Text(state.workplace?.optJSONObject("restaurant")?.text("name").orEmpty(), style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = { editing = "Restaurant" }) { Text("Edit name") }
    }
    Panel { Text("Tax by payment method", style = MaterialTheme.typography.titleMedium)
        paymentOptions.forEach { (method, title) -> Text("$title · ${settings.optJSONObject("taxByPaymentMethod")?.optDouble(method, 0.0) ?: 0.0}%") }
        TextButton(onClick = { editing = "Tax rates" }) { Text("Edit tax rates") }
    }
    Panel { Text("Receipts", style = MaterialTheme.typography.titleMedium); Text(settings.optJSONObject("receipt")?.text("footer").orEmpty()); TextButton(onClick = { editing = "Receipts" }) { Text("Edit receipt details") } }
    if (state.workplace?.optBoolean("isPrimaryOwner") == true) OwnerActions(state, model)
    if (editing.isNotBlank()) {
        val fields = when (editing) {
            "Restaurant" -> listOf(Field("restaurantName", "Restaurant name", true, initial = state.workplace?.optJSONObject("restaurant")?.text("name").orEmpty()))
            "Tax rates" -> paymentOptions.map { (key, name) -> Field(key, "$name (%)", true, "number", initial = settings.optJSONObject("taxByPaymentMethod")?.text(key) ?: "0") }
            else -> listOf("ntn", "strn", "footer").map { Field(it, if (it == "footer") "Receipt footer" else it.uppercase(), initial = settings.optJSONObject("receipt")?.text(it).orEmpty()) }
        }
        FormDialog(editing, fields, state, { editing = "" }) { values ->
            val body = when (editing) { "Tax rates" -> obj("taxByPaymentMethod" to values); "Receipts" -> obj("receipt" to values); else -> values }
            model.action("/settings", "PATCH", body) { model.updateSettings(it); editing = "" }
        }
    }
}

@Composable fun AccountScreen(state: AppState, model: RestoraModel) {
    var action by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { model.load("/me/sessions") }
    val user = state.me?.optJSONObject("user") ?: JSONObject()
    Text("Your account", style = MaterialTheme.typography.headlineSmall)
    Panel { Text(user.text("name"), style = MaterialTheme.typography.titleLarge); Text(user.text("email")); TextButton(onClick = { action = "Profile" }) { Text("Edit profile") }; TextButton(onClick = { action = "Password" }) { Text("Change password") }; TextButton(onClick = { action = "PIN" }) { Text("Set staff PIN") } }
    Panel { Text("Sessions", style = MaterialTheme.typography.titleMedium)
        (state.data["/me/sessions"] as? JSONArray)?.objects()?.forEach { session -> Text(session.text("device").ifBlank { "Signed-in device" }); Text(session.text("updatedAt"), style = MaterialTheme.typography.bodySmall) }
        TextButton(onClick = { model.action("/me/sessions/revoke-others") { val sessions = (state.data["/me/sessions"] as? JSONArray)?.objects().orEmpty(); model.put("/me/sessions", JSONArray(sessions.filter { it.optBoolean("current") })) } }, enabled = !state.busy) { Text("Sign out other devices") }
    }
    PrimaryButton(onClick = { action = "Sign out" }) { Text("Sign out") }
    TextButton(onClick = { action = "Delete account" }) { Text("Delete account", color = MaterialTheme.colorScheme.error) }
    if (action in listOf("Profile", "Password", "PIN")) {
        val fields = when (action) {
            "Profile" -> listOf(Field("name", "Full name", true, initial = user.text("name")))
            "PIN" -> listOf(Field("pin", "Staff PIN (4–6 digits)", true, "password"))
            else -> listOf(Field("currentPassword", "Current password", true, "password"), Field("newPassword", "New password (at least 8 characters)", true, "password"))
        }
        FormDialog(action, fields, state, { action = "" }) { body ->
            val path = when (action) { "Profile" -> "/me/profile"; "PIN" -> "/me/pin"; else -> "/auth/change-password" }
            model.action(path, if (action == "Profile") "PATCH" else "POST", body) {
                if (action == "Profile") model.updateProfile(body.text("name"))
                action = ""
            }
        }
    }
    if (action == "Delete account") Confirm("Delete account permanently?", "Your profile and every session will be deleted. Transfer or delete owned restaurants first.", state, { action = "" }) { model.deleteAccount() }
    if (action == "Sign out") Confirm("Sign out?", "Saved records and any unsent orders on this device will be cleared. Send waiting orders before signing out.", state, { action = "" }) { model.signOut() }
}

@Composable fun ManagementScreen(page: String, state: AppState, model: RestoraModel) {
    val path = when (page) { "Branches" -> "/branches"; "Staff" -> "/members"; "Roles" -> "/roles"; "Teams" -> "/teams"; "Devices" -> "/devices"; "Printers" -> "/print-agents"; else -> "/audit" }
    var editing by remember(page) { mutableStateOf<JSONObject?>(null) }
    var deleting by remember(page) { mutableStateOf<JSONObject?>(null) }
    var invite by remember { mutableStateOf(false) }
    LaunchedEffect(page) {
        model.load(path)
        if (page == "Staff") { model.load("/roles"); model.load("/invitations") }
        if (page == "Teams" && state.can("staff:view")) model.load("/members")
    }
    var showInvitations by rememberSaveable(page) { mutableStateOf(false) }
    val shownPath = if (page == "Staff" && showInvitations) "/invitations" else path
    val raw = state.data[shownPath]
    val records = remember(raw, showInvitations) {
        val rows = if (raw is JSONArray) raw.objects() else (raw as? JSONObject)?.array("entries")
        if (showInvitations) rows?.filter { it.text("status") == "pending" } else rows
    }
    RecordPage(page, when (page) { "Staff" -> "The people behind a great service."; "Branches" -> "Every location, working together."; "Roles" -> "The right access for the right people."; "Printers" -> "From the counter to the kitchen."; else -> "Keep your restaurant organised." }, records, shownPath in state.loading, { model.load(shownPath, true) }, "No ${page.lowercase()} here yet", header = {
        if (page == "Staff") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!showInvitations, { showInvitations = false }, label = { Text("People") })
            FilterChip(showInvitations, { showInvitations = true }, label = { Text("Invitations") })
        }
        if (page in listOf("Branches", "Roles", "Teams", "Printers")) TextButton(onClick = { editing = JSONObject() }) { Text("+ Add ${page.dropLast(1).lowercase()}") }
        if (page == "Staff" && state.can("staff:invite")) TextButton(onClick = { invite = true }) { Text("+ Invite staff") }
    }) { row ->
        if (page == "Staff" && showInvitations) Panel {
            Text(row.text("name").ifBlank { row.text("email").ifBlank { row.text("phone") } }, style = MaterialTheme.typography.titleMedium)
            Text(row.text("email").ifBlank { row.text("phone") }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row {
                TextButton(onClick = { model.action("/invitations/${row.text("_id")}/resend") { model.notify("Invitation resent") } }) { Text("Resend") }
                TextButton(onClick = { model.action("/invitations/${row.text("_id")}/revoke") { model.upsert("/invitations", row, true) } }) { Text("Revoke", color = MaterialTheme.colorScheme.error) }
            }
        } else Panel {

            val name = row.text("name").ifBlank { row.optJSONObject("user")?.text("name") ?: label(row.text("action")) }
            Text(name, style = MaterialTheme.typography.titleMedium)
            when (page) {
                "Staff" -> { Text(row.optJSONObject("user")?.text("email").orEmpty()); Text(label(row.text("status"))); Text(row.strings("roleIds").mapNotNull { id -> (state.data["/roles"] as? JSONArray)?.objects()?.find { it.text("_id") == id }?.text("name") }.joinToString(", ")) }
                "Branches" -> Text(listOf(row.text("area"), row.text("city"), row.text("phone")).filter { it.isNotBlank() }.joinToString(" · "))
                "Roles" -> Text("${row.optInt("memberCount")} members · ${row.strings("permissions").size} permissions")
                "Teams" -> Text("${row.strings("membershipIds").size} members · ${row.text("description")}")
                "Printers" -> { row.optJSONObject("pairing")?.let { Text("Pairing code: ${it.text("code")}"); Text("Expires ${it.text("expiresAt")}") }; row.array("printers").forEach { Text("${it.text("label")} · ${it.text("systemName")}") } }
                "Activity" -> { Text(row.optJSONObject("actor")?.text("name").orEmpty()); Text(row.optJSONObject("target")?.text("label").orEmpty()); Text(row.text("createdAt")) }
                "Devices" -> Text("Last seen ${row.text("lastSeenAt").ifBlank { "Never" }}")
            }
            Row {
                if ((page in listOf("Branches", "Roles", "Teams") && !(page == "Roles" && row.text("key") == "owner")) || (page == "Staff" && state.can("staff:manage") && !row.optBoolean("isOwner"))) TextButton(onClick = { editing = row }) { Text("Edit") }
                if (page == "Staff" && state.can("staff:manage") && !row.optBoolean("isOwner")) TextButton(onClick = {
                    model.action("$path/${row.text("_id")}/${if (row.text("status") == "suspended") "activate" else "suspend"}") { model.upsert(path, it) }
                }, enabled = !state.busy) { Text(if (row.text("status") == "suspended") "Activate" else "Suspend") }
                if (page == "Printers" && row.text("lastSeenAt").isBlank()) TextButton(onClick = { model.action("$path/${row.text("_id")}/code") { model.upsert(path, row.copyJson().put("pairing", it.optJSONObject("pairing") ?: it)) } }) { Text("New pairing code") }
                if (page in listOf("Branches", "Roles", "Teams", "Devices", "Printers") && !(page == "Roles" && row.optBoolean("isPreset"))) TextButton(onClick = { deleting = row }) { Text(if (page == "Branches") "Archive" else "Remove", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    editing?.let { row ->
        if (page == "Roles") RoleEditor(row, state, model) { editing = null }
        else if (page == "Teams") TeamEditor(row, state, model) { editing = null }
        else if (page == "Staff") AccessEditor(row, state, model) { editing = null }
        else {
            val fields = buildList {
                add(Field("name", "Name", true, initial = row.text("name")))
                if (page == "Branches") listOf("address", "area", "city", "phone").forEach { add(Field(it, label(it), initial = row.text(it))) }
                if (page == "Teams") {
                    add(Field("description", "Description", initial = row.text("description")))
                    add(Field("branchId", "Branch", type = "choice", options = listOf("" to "All branches") + state.workplace?.array("branches").orEmpty().map { it.text("_id") to it.text("name") }, initial = row.text("branchId")))
                }
            }
            FormDialog("${if (row.has("_id")) "Edit" else "Add"} ${page.dropLast(1).lowercase()}", fields, state, { editing = null }) { body ->
                model.action(path + if (row.has("_id")) "/${row.text("_id")}" else "", if (row.has("_id")) "PATCH" else "POST", body) { model.upsert(path, it); if (page == "Branches") model.updateBranch(it); editing = null }
            }
        }
    }
    if (invite) InvitationEditor(state, model) { invite = false }
    deleting?.let { row -> Confirm("${if (page == "Branches") "Archive" else "Remove"} ${row.text("name")}?", "Confirm this change for your restaurant.", state, { deleting = null }) {
        model.action("$path/${row.text("_id")}", "DELETE") { answer -> if (page == "Branches") { model.upsert(path, answer); model.updateBranch(answer) } else model.upsert(path, row, true); deleting = null }
    } }
}

@Composable fun OwnerActions(state: AppState, model: RestoraModel) {
    var action by remember { mutableStateOf("") }
    Panel {
        Text("Ownership", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = { model.load("/members"); action = "Transfer ownership" }) { Text("Transfer ownership") }
        TextButton(onClick = { action = "Delete restaurant" }) { Text("Delete restaurant", color = MaterialTheme.colorScheme.error) }
    }
    if (action.isNotBlank()) {
        val fields = buildList {
            if (action == "Transfer ownership") add(Field("membershipId", "New primary owner", true, type = "choice", options = (state.data["/members"] as? JSONArray)?.objects().orEmpty().filter { !it.optBoolean("isPrimaryOwner") && it.text("status") == "active" }.map { it.text("_id") to (it.optJSONObject("user")?.text("name") ?: "Staff member") }))
            add(Field("confirmName", "Type the restaurant name to confirm", true))
        }
        FormDialog(action, fields, state, { action = "" }) { body ->
            model.action(if (action == "Transfer ownership") "/restaurant/transfer" else "/restaurant/delete", body = body) { action = "" }
        }
    }
}

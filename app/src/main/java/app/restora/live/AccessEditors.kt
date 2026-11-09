package app.restora.live

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.restora.live.ui.*
import org.json.JSONArray
import org.json.JSONObject

val permissionCatalog = listOf("orders:view", "orders:create", "orders:update", "orders:payment", "orders:discount", "orders:cancel", "customers:view", "customers:manage", "customers:khata", "customers:import", "customers:delete", "reservations:view", "reservations:manage", "tables:view", "tables:manage", "menu:view", "menu:update", "menu:stock", "reports:view", "reports:view_all_branches", "staff:view", "staff:invite", "staff:manage", "teams:manage", "roles:manage", "settings:manage", "branches:manage", "devices:manage", "audit:view")

@Composable fun RoleEditor(row: JSONObject, state: AppState, model: RestoraModel, dismiss: () -> Unit) {
    var name by remember { mutableStateOf(row.text("name")) }
    var description by remember { mutableStateOf(row.text("description")) }
    var permissions by remember { mutableStateOf(row.strings("permissions").toSet()) }
    Dialog(onDismissRequest = { if (!state.busy) dismiss() }) {
        Surface(shape = MaterialTheme.shapes.large) { Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Role permissions", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(name, { name = it }, label = { Text("Role name") })
            OutlinedTextField(description, { description = it }, label = { Text("Description") })
            permissionCatalog.forEach { permission -> Row {
                Checkbox(checked = permission in permissions, onCheckedChange = { permissions = if (it) permissions + permission else permissions - permission })
                Text(label(permission.replace(':', ' ')), Modifier.padding(top = 12.dp))
            } }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            PrimaryButton(onClick = { model.action("/roles" + if (row.has("_id")) "/${row.text("_id")}" else "", if (row.has("_id")) "PATCH" else "POST", obj("name" to name, "description" to description, "permissions" to JSONArray(permissions.toList()))) { model.upsert("/roles", it); dismiss() } }, enabled = !state.busy && name.isNotBlank()) { Text(if (state.busy) "Saving…" else "Save role") }
            TextButton(onClick = dismiss, enabled = !state.busy) { Text("Cancel") }
        } }
    }
}

@Composable fun AccessEditor(row: JSONObject, state: AppState, model: RestoraModel, dismiss: () -> Unit) {
    var roles by remember { mutableStateOf(row.strings("roleIds").toSet()) }
    var all by remember { mutableStateOf(row.text("branchScope") == "all") }
    var branches by remember { mutableStateOf(row.strings("branchIds").toSet()) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Staff access") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            AccessChecks(roles, { roles = it }, all, { all = it }, branches, { branches = it }, state)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { PrimaryButton(onClick = { model.action("/members/${row.text("_id")}", "PATCH", obj("roleIds" to JSONArray(roles.toList()), "branchScope" to if (all) "all" else "list", "branchIds" to JSONArray(branches.toList()))) { model.upsert("/members", it); dismiss() } }, enabled = !state.busy && roles.isNotEmpty() && (all || branches.isNotEmpty())) { Text("Save") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
@Composable fun AccessChecks(roles: Set<String>, setRoles: (Set<String>) -> Unit, all: Boolean, setAll: (Boolean) -> Unit, branches: Set<String>, setBranches: (Set<String>) -> Unit, state: AppState) {
    Text("Roles", style = MaterialTheme.typography.titleMedium)
    (state.data["/roles"] as? JSONArray)?.objects()?.filter { it.text("key") != "owner" }?.forEach { role ->
        val id = role.text("_id")
        Row { Checkbox(id in roles, { setRoles(if (it) roles + id else roles - id) }); Text(role.text("name"), Modifier.padding(top = 12.dp)) }
    }
    Row { Checkbox(all, setAll); Text("All branches", Modifier.padding(top = 12.dp)) }
    if (!all) state.workplace?.array("branches")?.forEach { branch ->
        val id = branch.text("_id")
        Row { Checkbox(id in branches, { setBranches(if (it) branches + id else branches - id) }); Text(branch.text("name"), Modifier.padding(top = 12.dp)) }
    }
}
@Composable fun InvitationEditor(state: AppState, model: RestoraModel, dismiss: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var roles by remember { mutableStateOf<Set<String>>(emptySet()) }
    var all by remember { mutableStateOf(true) }
    var branches by remember { mutableStateOf<Set<String>>(emptySet()) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Invite staff by email") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }); OutlinedTextField(email, { email = it }, label = { Text("Email") })
            AccessChecks(roles, { roles = it }, all, { all = it }, branches, { branches = it }, state)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { PrimaryButton(onClick = {
        model.action("/invitations", body = obj("channel" to "email", "email" to email, "name" to name, "roleIds" to JSONArray(roles.toList()), "branchScope" to if (all) "all" else "list", "branchIds" to JSONArray(branches.toList()))) { model.upsert("/invitations", it); dismiss() }
    }, enabled = !state.busy && email.contains('@') && roles.isNotEmpty() && (all || branches.isNotEmpty())) { Text(if (state.busy) "Sending…" else "Send invitation") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable fun TeamEditor(row: JSONObject, state: AppState, model: RestoraModel, dismiss: () -> Unit) {
    var name by remember { mutableStateOf(row.text("name")) }
    var description by remember { mutableStateOf(row.text("description")) }
    var members by remember { mutableStateOf(row.strings("membershipIds").toSet()) }
    var branch by remember { mutableStateOf(row.text("branchId")) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Team") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Team name") })
            OutlinedTextField(description, { description = it }, label = { Text("Description") })
            Choices(branch, listOf("" to "All branches") + state.workplace?.array("branches").orEmpty().map { it.text("_id") to it.text("name") }, { branch = it })
            (state.data["/members"] as? JSONArray)?.objects()?.forEach { member ->
                val id = member.text("_id")
                Row { Checkbox(id in members, { members = if (it) members + id else members - id }); Text(member.optJSONObject("user")?.text("name") ?: "Staff member", Modifier.padding(top = 12.dp)) }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { PrimaryButton(onClick = { model.action("/teams" + if (row.has("_id")) "/${row.text("_id")}" else "", if (row.has("_id")) "PATCH" else "POST", obj("name" to name, "description" to description, "branchId" to branch, "membershipIds" to JSONArray(members.toList()))) { model.upsert("/teams", it); dismiss() } }, enabled = !state.busy && name.isNotBlank()) { Text("Save team") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

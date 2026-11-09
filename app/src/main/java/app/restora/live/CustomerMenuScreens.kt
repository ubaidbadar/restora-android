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
import org.json.JSONObject

@Composable fun CustomersScreen(state: AppState, model: RestoraModel) {
    var query by remember { mutableStateOf("") }
    var applied by remember { mutableStateOf("") }
    var edit by remember { mutableStateOf<JSONObject?>(null) }
    var profile by remember { mutableStateOf<String?>(null) }
    val path = "/customers" + if (applied.isNotBlank()) "?q=${java.net.URLEncoder.encode(applied, "UTF-8")}" else ""
    LaunchedEffect(path) { model.load(path) }
    PageTitle("Customers") { model.load(path, true) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(query, { query = it }, modifier = Modifier.weight(1f), label = { Text("Name or mobile") }, singleLine = true)
        TextButton(onClick = { applied = query }) { Text("Search") }
    }
    if (state.can("customers:manage")) Button(onClick = { edit = JSONObject() }) { Text("Add customer") }
    ReadState(state, path, model) { data ->
        val customers = (data as JSONArray).objects()
        if (customers.isEmpty()) Empty("No customers found")
        customers.forEach { c -> Card(onClick = { profile = c.text("_id"); model.load("/customers/${c.text("_id")}") }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(c.text("name"), style = MaterialTheme.typography.titleMedium); Text(c.text("phone"))
                Text("${c.optInt("orderCount")} orders · ${money(c.optDouble("totalSpent", 0.0))} spent")
                if (c.optDouble("khataBalance", 0.0) != 0.0) Text("Khata ${money(c.optDouble("khataBalance"))}", color = MaterialTheme.colorScheme.error)
            }
        } }
    }
    profile?.let { id -> CustomerProfile(id, state, model, { profile = null }, { edit = it }) { saved -> model.upsert(path, saved) } }
    edit?.let { c ->
        val fields = listOf(Field("name", "Name", true, initial = c.text("name")), Field("phone", "Mobile", true, "phone", initial = c.text("phone")), Field("altPhone", "Other phone", initial = c.text("altPhone")), Field("area", "Area", initial = c.text("area")), Field("addresses", "Addresses (one per line)", type = "list", initial = c.strings("addresses").joinToString("\n")), Field("tags", "Tags (comma separated)", type = "list", initial = c.strings("tags").joinToString(", ")), Field("notes", "Notes", type = "multiline", initial = c.text("notes")))
        FormDialog(if (c.has("_id")) "Edit customer" else "Add customer", fields, state, { edit = null }) { body ->
            model.action("/customers" + if (c.has("_id")) "/${c.text("_id")}" else "", if (c.has("_id")) "PATCH" else "POST", body) { saved ->
                model.upsert(path, saved); model.put("/customers/${saved.text("_id")}", saved); edit = null
            }
        }
    }
}
@Composable fun CustomerProfile(id: String, state: AppState, model: RestoraModel, dismiss: () -> Unit, edit: (JSONObject) -> Unit, updated: (JSONObject) -> Unit) {
    var tab by remember { mutableStateOf("Profile") }
    var payment by remember { mutableStateOf(false) }
    val path = "/customers/$id"
    val customer = state.data[path] as? JSONObject
    val detailPath = if (tab == "Khata") "$path/khata" else "/orders?customerId=$id&allDates=true"
    LaunchedEffect(tab) { if (tab != "Profile") model.load(detailPath) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(customer?.text("name") ?: "Customer") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Choices(tab, listOf("Profile", "Orders", "Khata").map { it to it }, { tab = it })
            if (tab == "Profile") ReadState(state, path, model) { value ->
                val c = value as JSONObject
                Text(c.text("phone")); Text(c.text("area")); Text(c.strings("addresses").joinToString("\n")); Text(c.text("notes")); Text("Khata ${money(c.optDouble("khataBalance", 0.0))}")
                if (state.can("customers:manage")) TextButton(onClick = { edit(c) }) { Text("Edit profile") }
            } else ReadState(state, detailPath, model) { value ->
                val rows = (value as JSONArray).objects()
                if (rows.isEmpty()) Text("No ${tab.lowercase()} yet")
                rows.forEach { row -> if (tab == "Khata") Text("${label(row.text("type"))} · ${money(row.optDouble("amount", 0.0))} · ${row.text("note")}") else Text("#${row.text("dailyToken")} · ${label(row.text("status"))} · ${money(row.optDouble("total", 0.0))}") }
            }
            if (state.can("customers:khata")) Button(onClick = { payment = true }) { Text("Record khata payment") }
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text("Close") } })
    if (payment) FormDialog("Khata entry", listOf(Field("type", "Entry type", true, type = "choice", options = listOf("payment" to "Payment received", "adjustment" to "Balance adjustment"), initial = "payment"), Field("amount", "Amount (Rs)", true, "number"), Field("note", "Note")), state, { payment = false }) { body ->
        model.action("$path/khata", body = body) { entry ->
            model.upsert("$path/khata", entry)
            entry.optJSONObject("customer")?.let { c -> model.put(path, c); updated(c) }
            payment = false
        }
    }
}

@Composable fun MenuScreen(state: AppState, model: RestoraModel) {
    var tab by remember { mutableStateOf("items") }
    var edit by remember { mutableStateOf<JSONObject?>(null) }
    var remove by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(Unit) { model.load("/menu") }
    PageTitle("Menu") { model.load("/menu", true) }
    Choices(tab, listOf("items" to "Dishes & deals", "categories" to "Categories", "addonGroups" to "Add-on groups"), { tab = it })
    if (state.can("menu:update")) Button(onClick = { edit = JSONObject() }) { Text("Add ${when (tab) { "items" -> "dish"; "categories" -> "category"; else -> "add-on group" }}") }
    ReadState(state, "/menu", model) { data ->
        val menu = data as JSONObject
        val rows = menu.array(tab)
        if (rows.isEmpty()) Empty("Nothing added yet")
        rows.forEach { row -> Panel {
            Text(row.text("name"), style = MaterialTheme.typography.titleMedium)
            if (tab == "items") {
                row.array("sizes").forEach { Text("${it.text("name").ifBlank { "Regular" }} · ${money(it.optDouble("price", 0.0))}") }
                if (row.optBoolean("hidden")) Text("Hidden from ordering")
                if (state.can("menu:stock")) Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("In stock", Modifier.padding(top = 12.dp)); Switch(checked = !row.optBoolean("soldOut"), onCheckedChange = { inStock -> model.action("/menu/items/${row.text("_id")}/stock", "PATCH", obj("soldOut" to !inStock)) { model.updateMenu(tab, row.copyJson().put("soldOut", !inStock)) } }, enabled = !state.busy)
                }
            }
            if (tab == "addonGroups") Text(row.array("options").joinToString { "${it.text("name")} + ${money(it.optDouble("price", 0.0))}" })
            if (state.can("menu:update")) Row { TextButton(onClick = { edit = row }) { Text("Edit") }; TextButton(onClick = { remove = row }) { Text("Delete", color = MaterialTheme.colorScheme.error) } }
        } }
    }
    val menu = state.data["/menu"] as? JSONObject ?: JSONObject()
    val endpoint = if (tab == "addonGroups") "addon-groups" else tab
    edit?.let { row ->
        val fields = buildList {
            add(Field("name", "Name", true, initial = row.text("name")))
            when (tab) {
                "items" -> {
                    add(Field("categoryId", "Category", true, type = "choice", options = menu.array("categories").map { it.text("_id") to it.text("name") }, initial = row.text("categoryId")))
                    add(Field("description", "Description", type = "multiline", initial = row.text("description")))
                    add(Field("prices", "Sizes: name = price (one per line)", true, "multiline", initial = row.array("sizes").joinToString("\n") { "${it.text("name")} = ${it.text("price")}" }))
                }
                "categories" -> add(Field("sortOrder", "Sort order", type = "number", initial = row.text("sortOrder").ifBlank { "0" }))
                else -> {
                    add(Field("min", "Minimum choices", true, "number", initial = row.text("min").ifBlank { "0" })); add(Field("max", "Maximum choices", true, "number", initial = row.text("max").ifBlank { "1" }))
                    add(Field("prices", "Options: name = price (one per line)", true, "multiline", initial = row.array("options").joinToString("\n") { "${it.text("name")} = ${it.text("price")}" }))
                }
            }
        }
        var invalid by remember { mutableStateOf<String?>(null) }
        FormDialog("${if (row.has("_id")) "Edit" else "Add"} ${if (tab == "items") "dish" else if (tab == "categories") "category" else "add-on group"}", fields, state.copy(error = invalid ?: state.error), { edit = null }) { body ->
            invalid = null
            if (tab != "categories") {
                val old = row.array(if (tab == "items") "sizes" else "options")
                val prices = body.text("prices").lines().filter { it.isNotBlank() }.map { line ->
                    val name = line.substringBeforeLast('=').trim(); val price = line.substringAfterLast('=').trim().toDoubleOrNull()
                    if ('=' !in line || price == null || !price.isFinite() || price < 0) invalid = "Use one name = price per line, with a price of 0 or more"
                    obj("name" to name, "price" to (price ?: 0.0)).apply { old.firstOrNull { it.text("name") == name }?.let { put("_id", it.text("_id")) } }
                }
                body.remove("prices"); body.put(if (tab == "items") "sizes" else "options", JSONArray(prices))
            }
            if (invalid == null) model.action("/menu/$endpoint" + if (row.has("_id")) "/${row.text("_id")}" else "", if (row.has("_id")) "PATCH" else "POST", body) { model.updateMenu(tab, it); edit = null }
        }
    }
    remove?.let { row -> Confirm("Delete ${row.text("name")}?", "This removes the record from the restaurant menu.", state, { remove = null }) { model.action("/menu/$endpoint/${row.text("_id")}", "DELETE") { model.updateMenu(tab, row, true); remove = null } } }
}

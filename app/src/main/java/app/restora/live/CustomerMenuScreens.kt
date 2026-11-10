package app.restora.live

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.restora.live.ui.*
import org.json.JSONArray
import org.json.JSONObject

@Composable fun CustomersScreen(state: AppState, model: RestoraModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var applied by rememberSaveable { mutableStateOf("") }
    var edit by remember { mutableStateOf<JSONObject?>(null) }
    var profile by rememberSaveable { mutableStateOf<String?>(null) }
    val path = "/customers" + if (applied.isNotBlank()) "?q=${java.net.URLEncoder.encode(applied, "UTF-8")}" else ""
    LaunchedEffect(path) { model.load(path) }
    val raw = state.data[path]
    val customers = remember(raw) { (raw as? JSONArray)?.objects() }
    RecordPage("Guests", "Profiles, visits and khata.", customers, path in state.loading, { model.load(path, true) }, "No guests found", header = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(query, { query = it }, modifier = Modifier.weight(1f), label = { Text("Search name or mobile") }, singleLine = true, shape = MaterialTheme.shapes.small)
            TextButton(onClick = { applied = query }) { Text("Search") }
        }
        if (state.can("customers:manage")) TextButton(onClick = { edit = JSONObject() }) { Text("+ Add guest") }
    }) { customer ->
        Surface(onClick = { profile = customer.text("_id"); model.load("/customers/${customer.text("_id")}") }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.size(46.dp)) { Box(contentAlignment = Alignment.Center) { Text(customer.text("name").take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary) } }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(customer.text("name"), style = MaterialTheme.typography.titleMedium)
                    Text(customer.text("phone"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${customer.optInt("orderCount")} visits · ${money(customer.optDouble("totalSpent", 0.0))} spent", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (customer.optDouble("khataBalance", 0.0) != 0.0) Text("Khata ${money(customer.optDouble("khataBalance"))}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                }
                Icon(painterResource(R.drawable.ic_chevron), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun CustomerProfile(id: String, state: AppState, model: RestoraModel, dismiss: () -> Unit, edit: (JSONObject) -> Unit, updated: (JSONObject) -> Unit) {
    var tab by rememberSaveable(id) { mutableStateOf("Profile") }
    var payment by rememberSaveable { mutableStateOf(false) }
    val path = "/customers/$id"
    val customer = state.data[path] as? JSONObject
    val detailPath = if (tab == "Khata") "$path/khata" else "/orders?customerId=$id&allDates=true"
    LaunchedEffect(tab) { if (tab != "Profile") model.load(detailPath) }
    if (!payment) ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(customer?.text("name") ?: "Guest profile", style = MaterialTheme.typography.headlineSmall)
            Text(customer?.text("phone").orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Profile", "Orders", "Khata").forEach { name -> FilterChip(selected = tab == name, onClick = { tab = name }, label = { Text(name) }) } }
            if (tab == "Profile") Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ReadState(state, path, model) { value ->
                    val guest = value as JSONObject
                    AmountRow("Khata balance", guest.optDouble("khataBalance", 0.0), emphasized = true)
                    if (guest.text("area").isNotBlank()) Text(guest.text("area"))
                    guest.strings("addresses").forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (guest.text("notes").isNotBlank()) { SectionLabel("Notes"); Text(guest.text("notes"), style = MaterialTheme.typography.bodyMedium) }
                    if (state.can("customers:manage")) TextButton(onClick = { dismiss(); edit(guest) }) { Text("Edit guest profile") }
                }
            } else {
                val raw = state.data[detailPath]
                val rows = remember(raw) { (raw as? JSONArray)?.objects() }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (rows == null) item { ReadState(state, detailPath, model) {} }
                    else if (rows.isEmpty()) item { Empty("No ${tab.lowercase()} yet") }
                    else items(rows, key = { it.text("_id") }) { row ->
                        if (tab == "Khata") Column {
                            AmountRow(label(row.text("type")), row.optDouble("amount", 0.0)); Text(row.text("note"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("#${row.text("dailyToken")} · ${label(row.text("status"))}", style = MaterialTheme.typography.bodyMedium); Text(money(row.optDouble("total", 0.0)), style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
            if (state.can("customers:khata")) PrimaryButton(onClick = { payment = true }, modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp).heightIn(min = 54.dp)) { Text("Record khata payment") }
            else Spacer(Modifier.height(20.dp))
        }
    }
    if (payment) FormDialog("Khata entry", listOf(Field("type", "Entry type", true, type = "choice", options = listOf("payment" to "Payment received", "adjustment" to "Balance adjustment"), initial = "payment"), Field("amount", "Amount (Rs)", true, "number"), Field("note", "Note")), state, { payment = false }) { body ->
        model.action("$path/khata", body = body) { entry ->
            model.upsert("$path/khata", entry)
            entry.optJSONObject("customer")?.let { c -> model.put(path, c); updated(c) }
            payment = false
        }
    }
}

@Composable fun MenuScreen(state: AppState, model: RestoraModel) {
    var tab by rememberSaveable { mutableStateOf("items") }
    var edit by remember { mutableStateOf<JSONObject?>(null) }
    var remove by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(Unit) { model.load("/menu") }
    val raw = state.data["/menu"]
    val records = remember(raw, tab) { (raw as? JSONObject)?.array(tab) }
    var actions by remember { mutableStateOf<JSONObject?>(null) }
    RecordPage("Your menu", "Dishes, prices and availability.", records, "/menu" in state.loading, { model.load("/menu", true) }, "Nothing on this menu yet", header = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("items" to "Dishes", "categories" to "Categories", "addonGroups" to "Add-ons").forEach { (key, title) -> FilterChip(selected = tab == key, onClick = { tab = key }, label = { Text(title) }) }
        }
        if (state.can("menu:update")) TextButton(onClick = { edit = JSONObject() }) { Text("+ Add ${when (tab) { "items" -> "dish"; "categories" -> "category"; else -> "add-on group" }}") }
    }) { record ->
        Panel {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small, modifier = Modifier.size(48.dp)) { Box(contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_brand), null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary) } }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(record.text("name"), style = MaterialTheme.typography.titleMedium)
                    if (tab == "items") Text(record.array("sizes").joinToString(" · ") { (it.text("name").takeIf(String::isNotBlank)?.let { name -> "$name " } ?: "") + money(it.optDouble("price", 0.0)) }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (tab == "addonGroups") Text(record.array("options").joinToString { it.text("name") }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.can("menu:update")) IconButton(onClick = { actions = record }) { Icon(painterResource(R.drawable.ic_more), "Manage ${record.text("name")}") }
            }
            if (tab == "items" && state.can("menu:stock")) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (record.optBoolean("hidden")) "Hidden from ordering" else if (record.optBoolean("soldOut")) "Sold out for now" else "Available to order", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Switch(checked = !record.optBoolean("soldOut"), onCheckedChange = { available -> model.action("/menu/items/${record.text("_id")}/stock", "PATCH", obj("soldOut" to !available)) { model.updateMenu(tab, it) } }, enabled = !state.busy)
            }
        }
    }
    actions?.let { record -> AlertDialog(onDismissRequest = { actions = null }, title = { Text(record.text("name")) }, confirmButton = { TextButton(onClick = { edit = record; actions = null }) { Text("Edit") } }, dismissButton = { TextButton(onClick = { remove = record; actions = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) } }) }
    val menu = state.data["/menu"] as? JSONObject ?: JSONObject()
    val endpoint = if (tab == "addonGroups") "addon-groups" else tab
    edit?.let { row ->
        val fields = buildList {
            add(Field("name", "Name", true, initial = row.text("name")))
            when (tab) {
                "items" -> {
                    add(Field("categoryId", "Category", true, type = "choice", options = menu.array("categories").map { it.text("_id") to it.text("name") }, initial = row.text("categoryId")))
                    add(Field("description", "Description", type = "multiline", initial = row.text("description")))
                    add(Field("prices", "Prices", true, "prices", initial = row.array("sizes").joinToString("\n") { "${it.text("name")} = ${it.text("price")}" }))
                }
                "categories" -> add(Field("sortOrder", "Sort order", type = "number", initial = row.text("sortOrder").ifBlank { "0" }))
                else -> {
                    add(Field("min", "Minimum choices", true, "number", initial = row.text("min").ifBlank { "0" })); add(Field("max", "Maximum choices", true, "number", initial = row.text("max").ifBlank { "1" }))
                    add(Field("prices", "Options", true, "prices", initial = row.array("options").joinToString("\n") { "${it.text("name")} = ${it.text("price")}" }))
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

package app.restora.live

import androidx.activity.compose.BackHandler
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
import java.util.UUID

@Composable fun NewOrderScreen(state: AppState, model: RestoraModel) {
    val existing = state.editingOrder
    var cart by remember { mutableStateOf(existing?.array("items").orEmpty()) }
    var chosen by remember { mutableStateOf<JSONObject?>(null) }
    var checkout by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var clientId by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var leave by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { model.load("/menu"); if (state.can("customers:view")) model.load("/customers"); if (state.can("tables:view")) model.load("/tables") }
    BackHandler { if (cart.isEmpty()) model.navigate("Orders") else leave = true }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (existing == null) "New order" else "Edit order #${existing.text("dailyToken")}", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = { if (cart.isEmpty()) model.navigate("Orders") else leave = true }) { Text("Close") }
        }
        OutlinedTextField(search, { search = it }, label = { Text("Find a dish") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        ReadState(state, "/menu", model) { data ->
            val menu = data as JSONObject
            val items = menu.array("items").filter { !it.optBoolean("hidden") && !it.optBoolean("soldOut") && it.text("name").contains(search, true) && onSale(it) }
            if (items.isEmpty()) Empty("No available dishes", "Check your menu or choose a different search.")
            items.forEach { dish -> Panel {
                Text(dish.text("name"), style = MaterialTheme.typography.titleMedium)
                Text(dish.text("description"))
                Text(dish.array("sizes").minOfOrNull { it.optDouble("price", 0.0) }?.let(::money) ?: "No price")
                Button(onClick = { chosen = dish }) { Text("Add to order") }
            } }
        }
        if (cart.isNotEmpty()) Panel {
            Text("Your order", style = MaterialTheme.typography.titleLarge)
            cart.forEachIndexed { index, line ->
                Text("${line.optInt("qty")} × ${line.text("name")} ${line.text("sizeName")} · ${money(line.optDouble("price") * line.optInt("qty"))}")
                Row {
                    TextButton(onClick = { cart = cart.mapIndexedNotNull { i, row -> if (i != index) row else if (row.optInt("qty") > 1) row.copyJson().put("qty", row.optInt("qty") - 1) else null } }) { Text("−", style = MaterialTheme.typography.titleLarge) }
                    TextButton(onClick = { cart = cart.mapIndexed { i, row -> if (i == index) row.copyJson().put("qty", row.optInt("qty") + 1) else row } }) { Text("+", style = MaterialTheme.typography.titleLarge) }
                }
            }
            Text("Subtotal ${money(cart.sumOf { it.optDouble("price") * it.optInt("qty") })}")
            Text("Tax and delivery are calculated by the server when the order is placed.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = { checkout = true }, modifier = Modifier.fillMaxWidth()) { Text("Review & place order") }
        }
        Spacer(Modifier.height(24.dp))
    }
    chosen?.let { dish -> DishDialog(dish, state.data["/menu"] as JSONObject, { chosen = null }) { line -> cart = cart + line; chosen = null } }
    if (checkout) {
        val customers = (state.data["/customers"] as? JSONArray)?.objects().orEmpty()
        val tables = (state.data["/tables"] as? JSONArray)?.objects().orEmpty()
        val fields = buildList {
            add(Field("type", "Order type", true, type = "choice", options = listOf("takeaway", "dine_in", "delivery").map { it to label(it) }, initial = "takeaway"))
            if (customers.isNotEmpty()) add(Field("customerId", "Saved customer", type = "choice", options = listOf("" to "Walk-in / new customer") + customers.map { it.text("_id") to "${it.text("name")} · ${it.text("phone")}" }))
            add(Field("newName", "New customer name")); add(Field("newPhone", "New customer mobile", type = "phone"))
            if (tables.isNotEmpty()) add(Field("tableId", "Table (dine-in)", type = "choice", options = listOf("" to "No table") + tables.map { it.text("_id") to it.text("name") }))
            add(Field("tableName", "Table name (if not picked)")); add(Field("address", "Delivery address", type = "multiline")); add(Field("area", "Delivery area")); add(Field("rider", "Rider"))
            add(Field("deliveryFee", "Delivery fee", type = "number", initial = "0"))
            if (state.can("orders:discount")) add(Field("discount", "Discount", type = "number", initial = "0"))
            if (state.can("orders:payment")) {
                add(Field("paymentStatus", "Payment", true, type = "choice", options = listOf("unpaid" to "Pay later", "paid" to "Paid", "khata" to "Khata"), initial = "unpaid"))
                add(Field("paymentMethod", "Payment method (if paid / khata)", type = "choice", options = listOf("" to "Not paid yet") + paymentOptions))
            }
        }
        val initialFields = if (existing == null) fields else fields.map { f -> f.copy(initial = existing.text(f.key).ifBlank { f.initial }) }
        FormDialog(if (existing == null) "Place order" else "Save order changes", initialFields, state, { checkout = false }) { body ->
            body.put("clientId", clientId).put("items", JSONArray(cart.map { line -> obj("itemId" to line.text("itemId"), "sizeId" to line.text("sizeId"), "addonIds" to line.optJSONArray("addonIds"), "qty" to line.optInt("qty"), "note" to line.text("note")) }))
            val finish: (JSONObject) -> Unit = { saved ->
                val path = "/orders?date=${saved.text("dayKey").ifBlank { today() }}"
                if (state.data.containsKey(path)) model.upsert(path, saved)
                cart = emptyList(); checkout = false; clientId = UUID.randomUUID().toString(); model.navigate("Orders")
            }
            if (existing == null) {
                body.put("placedAt", java.time.Instant.now().toString())
                if (body.text("paymentStatus") in listOf("paid", "khata")) model.action("/orders", body = body, done = finish) else model.createOrder(body, finish)
            } else model.action("/orders/${existing.text("_id")}", "PUT", body, done = finish)
        }
    }
    if (leave) Confirm("Discard this order?", "The dishes in this unsaved order will be removed.", state, { leave = false }) { model.navigate("Orders") }
}

fun onSale(dish: JSONObject, now: java.time.ZonedDateTime = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Karachi"))): Boolean {
    val days = dish.optJSONArray("days")
    val day = now.dayOfWeek.value % 7
    if (days != null && days.length() > 0 && (0 until days.length()).none { days.optInt(it) == day }) return false
    val from = dish.text("from"); val to = dish.text("to")
    if (from.isEmpty() || to.isEmpty()) return true
    val time = now.toLocalTime().toString().take(5)
    return if (from < to) time >= from && time < to else time >= from || time < to
}

@Composable fun DishDialog(dish: JSONObject, menu: JSONObject, dismiss: () -> Unit, add: (JSONObject) -> Unit) {
    val sizes = dish.array("sizes")
    var size by remember { mutableStateOf(sizes.firstOrNull()) }
    var addons by remember { mutableStateOf<Set<String>>(emptySet()) }
    var note by remember { mutableStateOf("") }
    val groups = menu.array("addonGroups").filter { it.text("_id") in dish.strings("addonGroupIds") }
    val selectedOptions = groups.flatMap { it.array("options") }.filter { it.text("_id") in addons }
    val price = (size?.optDouble("price", 0.0) ?: 0.0) + selectedOptions.sumOf { it.optDouble("price", 0.0) }
    val valid = size != null && groups.all { g -> val n = g.array("options").count { it.text("_id") in addons }; n >= g.optInt("min") && n <= g.optInt("max", 1) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(dish.text("name")) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sizes.forEach { s -> Row { RadioButton(selected = size?.text("_id") == s.text("_id"), onClick = { size = s }); Text("${s.text("name").ifBlank { "Regular" }} · ${money(s.optDouble("price", 0.0))}", Modifier.padding(top = 12.dp)) } }
            groups.forEach { group ->
                Text("${group.text("name")} · Choose ${group.optInt("min")}–${group.optInt("max", 1)}")
                group.array("options").forEach { option ->
                    val id = option.text("_id")
                    Row { Checkbox(checked = id in addons, onCheckedChange = { checked ->
                        if (!checked) addons = addons - id else {
                            val groupIds = group.array("options").map { it.text("_id") }.toSet()
                            if (group.optInt("max", 1) == 1) addons = addons - groupIds + id
                            else if (addons.count { it in groupIds } < group.optInt("max", 1)) addons = addons + id
                        }
                    }); Text("${option.text("name")} + ${money(option.optDouble("price", 0.0))}", Modifier.padding(top = 12.dp)) }
                }
            }
            OutlinedTextField(note, { note = it }, label = { Text("Kitchen note") })
        }
    }, confirmButton = { Button(enabled = valid, onClick = { add(obj("itemId" to dish.text("_id"), "name" to dish.text("name"), "sizeId" to size?.text("_id"), "sizeName" to size?.text("name"), "addonIds" to JSONArray(addons.toList()), "note" to note, "price" to price, "qty" to 1)) }) { Text("Add · ${money(price)}") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

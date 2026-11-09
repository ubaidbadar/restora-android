package app.restora.live

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.restora.live.ui.*
import org.json.JSONArray
import org.json.JSONObject

val paymentOptions = listOf("cash", "card", "jazzcash", "easypaisa", "bank").map { it to label(it) }

@Composable fun PageTitle(title: String, refresh: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        TextButton(onClick = refresh) { Text("Refresh") }
    }
}
@Composable fun DashboardScreen(state: AppState, model: RestoraModel) {
    var date by remember { mutableStateOf(today()) }
    val path = "/dashboard?date=$date"
    LaunchedEffect(path) { model.load(path) }
    PageTitle("Overview") { model.load(path, true) }
    DateFilter(date) { date = it }
    ReadState(state, path, model) { value ->
        val answer = value as JSONObject
        val stats = answer.optJSONObject("stats") ?: JSONObject()
        listOf("Sales" to money(stats.optDouble("sales", 0.0)), "Orders" to stats.optInt("orderCount").toString(), "Cash received" to money(stats.optDouble("cash", 0.0)), "Digital received" to money(stats.optDouble("digital", 0.0)), "Not collected" to money(stats.optDouble("notCollected", 0.0)), "Khata outstanding" to money(stats.optDouble("khataTotal", 0.0)), "New customers" to stats.optInt("newCustomers").toString()).chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { pair.forEach { (name, amount) -> Column(Modifier.weight(1f)) { Panel { Text(name, style = MaterialTheme.typography.labelLarge); Text(amount, style = MaterialTheme.typography.titleLarge) } } } }
        }
        Panel { Text("Order types", style = MaterialTheme.typography.titleMedium); answer.optJSONObject("stats")?.array("byType")?.forEach { Text("${label(it.text("type"))} · ${it.optInt("count")} · ${money(it.optDouble("sales", 0.0))}") } }
        if (answer.array("slipping").isNotEmpty()) Panel { Text("Guests to reconnect with", style = MaterialTheme.typography.titleMedium); answer.array("slipping").forEach { Text("${it.text("name")} · ${it.text("phone")}") } }
    }
}
@Composable fun DateFilter(value: String, change: (String) -> Unit) {
    var input by remember(value) { mutableStateOf(value) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(input, { input = it }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.weight(1f))
        TextButton(onClick = { if (runCatching { java.time.LocalDate.parse(input) }.isSuccess) change(input) }) { Text("Apply") }
    }
}

@Composable fun OrdersScreen(state: AppState, model: RestoraModel, history: Boolean = false) {
    var date by remember { mutableStateOf(today()) }
    var filter by remember { mutableStateOf("all") }
    var selected by remember { mutableStateOf<JSONObject?>(null) }
    val path = "/orders?date=$date"
    LaunchedEffect(path) { model.load(path) }
    PageTitle(if (history) "Order history" else "Orders") { model.load(path, true) }
    DateFilter(date) { date = it }
    state.outbox.forEach { pending -> Panel {
        Text("Waiting to send", style = MaterialTheme.typography.titleMedium)
        Text("${label(pending.text("type"))} · ${pending.array("items").sumOf { it.optInt("qty") }} dishes · ${pending.text("newName").ifBlank { "Walk-in guest" }}")
        Text(pending.text("syncError").ifBlank { "Saved on this device. It has not reached the kitchen." })
        Row {
            TextButton(onClick = { model.syncPending(true) }) { Text("Retry") }
            TextButton(onClick = { model.discardPending(pending.text("clientId")) }) { Text("Discard") }
        }
    } }
    Choices(filter, listOf("all" to "All orders") + listOf("pending", "new", "preparing", "ready", "out_for_delivery", "completed", "cancelled").map { it to label(it) }, { filter = it })
    ReadState(state, path, model) { value ->
        val orders = (value as JSONArray).objects().filter { filter == "all" || it.text("status") == filter }
        if (orders.isEmpty()) Empty("No orders", "Orders for this date and status will appear here.")
        orders.forEach { order -> Card(onClick = { selected = order }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("#${order.text("dailyToken")} · ${label(order.text("type"))}", style = MaterialTheme.typography.titleMedium); Text(money(order.optDouble("total", 0.0))) }
                Text("${label(order.text("status"))} · ${label(order.text("paymentStatus"))}", color = MaterialTheme.colorScheme.primary)
                Text(listOf(order.optJSONObject("customer")?.text("name"), order.text("tableName")).filterNot { it.isNullOrBlank() }.joinToString(" · ").ifBlank { "Walk-in guest" })
                Text(order.array("items").joinToString { "${it.optInt("qty")} × ${it.text("name")}" }, maxLines = 2)
            }
        } }
    }
    selected?.let { order -> OrderDialog(order, state, model, { selected = null }) { saved -> model.upsert(path, saved); selected = saved } }
}

@Composable fun OrderDialog(order: JSONObject, state: AppState, model: RestoraModel, dismiss: () -> Unit, saved: (JSONObject) -> Unit) {
    var action by remember(order.text("_id")) { mutableStateOf("") }
    val id = order.text("_id")
    val status = order.text("status")
    AlertDialog(onDismissRequest = dismiss, title = { Text("Order #${order.text("dailyToken")}") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${label(order.text("type"))} · ${label(status)} · ${label(order.text("paymentStatus"))}")
            order.array("items").forEach { line ->
                Text("${line.optInt("qty")} × ${line.text("name")} ${line.text("sizeName")} · ${money(line.optDouble("price", 0.0) * line.optInt("qty"))}")
                if (line.text("note").isNotBlank()) Text(line.text("note"))
                line.array("addons").forEach { Text("+ ${it.text("name")}") }
            }
            listOf("subtotal", "discount", "tax", "deliveryFee", "total").forEach { Text("${label(it)}: ${money(order.optDouble(it, 0.0))}") }
            order.optJSONObject("customer")?.let { Text("${it.text("name")} · ${it.text("phone")}") }
            if (order.text("address").isNotBlank()) Text(order.text("address"))
            if (state.can("orders:update")) {
                val next = when (status) { "pending" -> "new"; "new" -> "preparing"; "preparing" -> "ready"; "ready" -> if (order.text("type") == "delivery") "out_for_delivery" else "completed"; "out_for_delivery" -> "completed"; else -> "" }
                if (next.isNotEmpty()) Button(onClick = { model.action("/orders/$id/status", "PATCH", obj("status" to next)) { saved(it) } }, enabled = !state.busy) { Text(if (status == "pending") "Accept order" else "Mark ${label(next).lowercase()}") }
            }
            if (state.can("orders:update") && status in listOf("new", "preparing", "ready") && order.text("paymentStatus") == "unpaid") TextButton(onClick = { dismiss(); model.editOrder(order) }) { Text("Edit order") }
            val context = androidx.compose.ui.platform.LocalContext.current
            TextButton(onClick = {
                val receipt = "Restora · Order #${order.text("dailyToken")}\n" + order.array("items").joinToString("\n") { "${it.optInt("qty")} × ${it.text("name")} · ${money(it.optDouble("price", 0.0) * it.optInt("qty"))}" } + "\nTotal ${money(order.optDouble("total", 0.0))} · ${label(order.text("paymentStatus"))}"
                context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, receipt) }, "Share receipt"))
            }) { Text("Share receipt") }
            TextButton(onClick = { model.action("/print-jobs", body = obj("orderId" to id, "kind" to "receipt")) {} }, enabled = !state.busy) { Text("Print receipt on restaurant PC") }
            if (state.can("orders:payment") && order.text("paymentStatus") == "unpaid" && status !in listOf("pending", "cancelled")) OutlinedButton(onClick = { action = "payment" }) { Text("Record payment") }
            if (state.can("orders:cancel") && status !in listOf("cancelled", "completed")) TextButton(onClick = { action = "cancel" }) { Text("Cancel order", color = MaterialTheme.colorScheme.error) }
            if (state.can("orders:view")) TextButton(onClick = { action = "timeline"; model.load("/orders/$id/timeline") }) { Text("View activity") }
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text("Close") } })
    if (action == "payment") FormDialog("Record payment", listOf(Field("paymentMethod", "Payment method", true, type = "choice", options = paymentOptions, initial = "cash"), Field("paymentStatus", "Payment status", true, type = "choice", options = listOf("paid" to "Paid", "khata" to "Khata"), initial = "paid")), state, { action = "" }) { body ->
        model.action("/orders/$id/payment", body = body) { saved(it); action = "" }
    }
    if (action == "cancel") FormDialog("Cancel order", listOf(Field("note", "Reason for cancellation", true)), state, { action = "" }) { body ->
        body.put("reason", "other"); model.action("/orders/$id/cancel", body = body) { saved(it); action = "" }
    }
    if (action == "timeline") AlertDialog(onDismissRequest = { action = "" }, title = { Text("Order activity") }, text = { Column { ReadState(state, "/orders/$id/timeline", model) { data -> (data as JSONArray).objects().forEach { Text("${it.text("createdAt")} · ${label(it.text("action"))}") } } } }, confirmButton = { TextButton(onClick = { action = "" }) { Text("Close") } })
}

@Composable fun TablesScreen(state: AppState, model: RestoraModel) {
    val path = "/tables/floor"
    var edit by remember { mutableStateOf<JSONObject?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { model.load(path) }
    PageTitle("Tables") { model.load(path, true) }
    if (state.can("tables:manage")) Button(onClick = { edit = JSONObject() }) { Text("Add table") }
    ReadState(state, path, model) { data ->
        val tables = (data as JSONArray).objects()
        if (tables.isEmpty()) Empty("No tables", "Add your first table to start taking dine-in orders.")
        tables.forEach { table -> Panel {
            Text(table.text("name"), style = MaterialTheme.typography.titleLarge)
            Text("${table.text("section")} · ${table.text("seats").ifBlank { "—" }} seats · " + if (table.optBoolean("archived")) "Archived" else if (table.array("orders").isEmpty()) "Available" else "Occupied")
            if (table.text("qrCode").isNotBlank()) TextButton(onClick = {
                context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, "${table.text("name")} · ${BuildConfig.API_URL}/t/${table.text("qrCode")}") }, "Share table menu"))
            }) { Text("Share guest menu link") }
            table.array("orders").forEach { Text("Order #${it.text("dailyToken")} · ${money(it.optDouble("total", 0.0))}") }
            table.array("requests").forEach { request -> Button(onClick = { model.action("/guest-requests/${request.text("_id")}/resolve") {
                model.upsert(path, table.copyJson().put("requests", JSONArray(table.array("requests").filterNot { it.text("_id") == request.text("_id") })))
            } }, enabled = !state.busy) { Text("Resolve ${request.text("type")} call") } }
            if (state.can("tables:manage")) Row { TextButton(onClick = { edit = table }) { Text("Edit") }; TextButton(onClick = {
                model.action("/tables/${table.text("_id")}/${if (table.optBoolean("archived")) "restore" else "archive"}") { model.upsert(path, table.copyJson().put("archived", it.optBoolean("archived"))) }
            }, enabled = !state.busy) { Text(if (table.optBoolean("archived")) "Restore" else "Archive") } }
        } }
    }
    edit?.let { table -> FormDialog(if (table.has("_id")) "Edit table" else "Add table", listOf(Field("name", "Table name", true, initial = table.text("name")), Field("section", "Section", initial = table.text("section")), Field("seats", "Seats", type = "number", initial = table.text("seats"))), state, { edit = null }) { body ->
        model.action("/tables" + if (table.has("_id")) "/${table.text("_id")}" else "", if (table.has("_id")) "PATCH" else "POST", body) { model.upsert(path, table.copyJson().apply { it.keys().forEach { key -> put(key, it.get(key)) } }); edit = null }
    } }
}

@Composable fun ReservationsScreen(state: AppState, model: RestoraModel) {
    var date by remember { mutableStateOf(today()) }
    var edit by remember { mutableStateOf<JSONObject?>(null) }
    val path = "/reservations?date=$date"
    LaunchedEffect(path) { model.load(path) }
    PageTitle("Reservations") { model.load(path, true) }
    DateFilter(date) { date = it }
    if (state.can("reservations:manage")) Button(onClick = { edit = JSONObject() }) { Text("New reservation") }
    ReadState(state, path, model) { data ->
        val bookings = (data as JSONArray).objects()
        if (bookings.isEmpty()) Empty("No reservations for this date")
        bookings.forEach { r -> Panel {
            Text(r.optJSONObject("customer")?.text("name") ?: "Guest", style = MaterialTheme.typography.titleMedium)
            Text("Party of ${r.optInt("partySize")} · ${r.text("dateTime")} · ${label(r.text("status"))}")
            if (r.text("note").isNotBlank()) Text(r.text("note"))
            if (state.can("reservations:manage")) {
                TextButton(onClick = { edit = r }) { Text("Edit booking") }
                Choices(r.text("status"), listOf("pending", "confirmed", "seated", "cancelled", "no_show").map { it to label(it) }, { status -> model.action("/reservations/${r.text("_id")}", "PATCH", obj("status" to status)) { model.upsert(path, it) } }, !state.busy)
            }
        } }
    }
    edit?.let { r ->
        val fields = buildList {
            if (!r.has("_id")) { add(Field("name", "Guest name", true)); add(Field("phone", "Mobile number", true, "phone")) }
            add(Field("partySize", "Party size", true, "number", initial = r.text("partySize").ifBlank { "2" }))
            add(Field("dateTime", "Booking date and time", true, "datetime", initial = r.text("dateTime").ifBlank { "${date}T19:00:00+05:00" }))
            add(Field("note", "Notes", type = "multiline", initial = r.text("note")))
        }
        FormDialog(if (r.has("_id")) "Edit reservation" else "New reservation", fields, state, { edit = null }) { body ->
            model.action("/reservations" + if (r.has("_id")) "/${r.text("_id")}" else "", if (r.has("_id")) "PATCH" else "POST", body) { model.upsert(path, it); edit = null }
        }
    }
}

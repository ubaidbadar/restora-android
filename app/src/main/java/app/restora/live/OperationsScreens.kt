package app.restora.live

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.restora.live.ui.*
import org.json.JSONArray
import org.json.JSONObject

val paymentOptions = listOf("cash", "card", "jazzcash", "easypaisa", "bank").map { it to label(it) }

@Composable fun PageTitle(title: String, refresh: () -> Unit) = PageHeading(title, "", refresh)

@Composable fun DashboardScreen(state: AppState, model: RestoraModel) {
    var date by rememberSaveable { mutableStateOf(today()) }
    val path = "/dashboard?date=$date"
    LaunchedEffect(path) { model.load(path) }
    val answer = state.data[path] as? JSONObject
    val stats = answer?.optJSONObject("stats")
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 104.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { PageHeading("Overview", "Your restaurant, at a glance.") { model.load(path, true) } }
        item { DateFilter(date) { date = it } }
        if (stats == null) item { ReadState(state, path, model) {} }
        else {
            item { SalesSummary(stats) }
            item {
                SectionLabel("Collections")
                Spacer(Modifier.height(10.dp))
                Panel {
                    AmountRow("Cash received", stats.optDouble("cash", 0.0))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    AmountRow("Digital received", stats.optDouble("digital", 0.0))
                }
            }
            item {
                Panel {
                    SectionLabel("Outstanding")
                    AmountRow("Unpaid orders", stats.optDouble("notCollected", 0.0))
                    AmountRow("Guest khata", stats.optDouble("khataTotal", 0.0))
                    if (state.can("orders:view") || state.can("customers:view")) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (state.can("orders:view")) TextButton(onClick = { model.navigate("Orders") }) { Text("View orders"); Icon(painterResource(R.drawable.ic_chevron), null, Modifier.size(16.dp)) }
                            if (state.can("customers:view")) TextButton(onClick = { model.navigate("Customers") }) { Text("View guests"); Icon(painterResource(R.drawable.ic_chevron), null, Modifier.size(16.dp)) }
                        }
                    }
                }
            }
            item { SectionLabel("Sales by service", "${stats.optInt("orderCount")} orders") }
            items(stats.array("byType"), key = { "type:${it.text("type")}" }) { type ->
                Panel {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                            Icon(painterResource(if (type.text("type") == "dine_in") R.drawable.ic_table else R.drawable.ic_receipt), null, Modifier.padding(12.dp).size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Column(Modifier.weight(1f)) { Text(label(type.text("type")), style = MaterialTheme.typography.titleSmall); Text("${type.optInt("count")} orders", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Text(money(type.optDouble("sales", 0.0)), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            if (answer.array("slipping").isNotEmpty()) {
                item { SectionLabel("Guests to reconnect with") }
                items(answer.array("slipping"), key = { "guest:${it.text("_id")}" }) { guest -> Panel { Text(guest.text("name"), style = MaterialTheme.typography.titleSmall); Text(guest.text("phone"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            }
        }
    }
}

/** A single sales hierarchy; no invented trends, live counts, or comparative claims. */
@Composable fun SalesSummary(stats: JSONObject) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(7.dp).background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape))
                Text("Total sales", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(money(stats.optDouble("sales", 0.0)), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text("${stats.optInt("orderCount")}", style = MaterialTheme.typography.titleLarge); Text("Orders", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text("${stats.optInt("newCustomers")}", style = MaterialTheme.typography.titleLarge); Text("New guests", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DateFilter(value: String, change: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val date = java.time.LocalDate.parse(value)
    val picker = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli())
    Surface(onClick = { open = true }, color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.small) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_calendar), null, Modifier.padding(end = 12.dp).size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Text(if (value == today()) "Today" else date.format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM")), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Text(date.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy")), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(painterResource(R.drawable.ic_chevron), null, Modifier.padding(start = 10.dp).size(18.dp))
        }
    }
    if (open) DatePickerDialog(onDismissRequest = { open = false }, confirmButton = { TextButton(onClick = {
        picker.selectedDateMillis?.let { change(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()) }; open = false
    }) { Text("Apply date") } }, dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } }) { DatePicker(picker) }
}

@Composable fun OrdersScreen(state: AppState, model: RestoraModel, history: Boolean = false) {
    var date by rememberSaveable { mutableStateOf(today()) }
    var filter by rememberSaveable { mutableStateOf("all") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val path = "/orders?date=$date"
    LaunchedEffect(path) { model.load(path) }
    val raw = state.data[path]
    val allOrders = remember(raw) { (raw as? JSONArray)?.objects() }
    val records = remember(allOrders, filter) { allOrders?.filter { filter == "all" || it.text("status") == filter } }
    RecordPage(if (history) "Order history" else "Orders", "Track tickets from kitchen to collection.", records, path in state.loading, { model.load(path, true) }, "No orders here yet", header = {
        DateFilter(date) { date = it }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("all", "pending", "new", "preparing", "ready", "out_for_delivery", "completed", "cancelled")) { status ->
                FilterChip(selected = filter == status, onClick = { filter = status }, label = { Text(if (status == "all") "All orders" else label(status)) }, shape = MaterialTheme.shapes.small)
            }
        }
        state.outbox.forEach { pending -> Panel {
            SectionLabel("Waiting to send")
            Text(pending.text("syncError").ifBlank { "This order is saved on your phone. The kitchen hasn't received it yet." }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row { TextButton(onClick = { model.syncPending(true) }) { Text("Retry") }; TextButton(onClick = { model.discardPending(pending.text("clientId")) }) { Text("Discard") } }
        } }
    }) { order -> OrderCard(order) { selectedId = order.text("_id") } }
    allOrders?.firstOrNull { it.text("_id") == selectedId }?.let { order -> OrderDialog(order, state, model, { selectedId = null }) { saved -> model.upsert(path, saved) } }
}

@Composable fun OrderCard(order: JSONObject, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("#${order.text("dailyToken")}", style = MaterialTheme.typography.titleLarge)
                Text(listOf(label(order.text("type")), order.text("tableName")).filter(String::isNotBlank).joinToString(" · "), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(painterResource(R.drawable.ic_chevron), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(order.optJSONObject("customer")?.text("name")?.ifBlank { null } ?: "Walk-in guest", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(order.array("items").joinToString { "${it.optInt("qty")} × ${it.text("name")}" }, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            // FlowRow lets long payment labels and large fonts wrap instead of squeezing the amount.
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(order.text("status"))
                    Text(label(order.text("paymentStatus")), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(money(order.optDouble("total", 0.0)), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun OrderDialog(order: JSONObject, state: AppState, model: RestoraModel, dismiss: () -> Unit, saved: (JSONObject) -> Unit) {
    var action by rememberSaveable(order.text("_id")) { mutableStateOf("") }
    val id = order.text("_id")
    val status = order.text("status")
    val context = androidx.compose.ui.platform.LocalContext.current
    if (action.isEmpty()) ModalBottomSheet(onDismissRequest = { if (!state.busy) dismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().heightIn(max = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp * 0.88f).padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Order #${order.text("dailyToken")}", style = MaterialTheme.typography.headlineSmall); Text(label(order.text("type")) + order.text("tableName").takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                StatusPill(status)
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                order.optJSONObject("customer")?.let { Text("${it.text("name")} · ${it.text("phone")}", style = MaterialTheme.typography.bodyMedium) }
                order.array("items").forEach { line ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.extraSmall) { Text("${line.optInt("qty")}×", Modifier.padding(9.dp), style = MaterialTheme.typography.labelLarge) }
                        Column(Modifier.weight(1f)) { Text(line.text("name"), style = MaterialTheme.typography.titleSmall); Text(listOf(line.text("sizeName"), line.array("addons").joinToString { it.text("name") }, line.text("note")).filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Text(money(line.optDouble("price", 0.0) * line.optInt("qty")), style = MaterialTheme.typography.labelLarge)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AmountRow("Subtotal", order.optDouble("subtotal", 0.0))
                if (order.optDouble("discount", 0.0) != 0.0) AmountRow("Discount", -order.optDouble("discount", 0.0))
                AmountRow("Tax", order.optDouble("tax", 0.0))
                if (order.optDouble("deliveryFee", 0.0) != 0.0) AmountRow("Delivery", order.optDouble("deliveryFee", 0.0))
                AmountRow("Total", order.optDouble("total", 0.0), emphasized = true)
                Text(label(order.text("paymentStatus")), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (order.text("address").isNotBlank()) Text(order.text("address"))
                if (state.can("orders:payment") && order.text("paymentStatus") == "unpaid" && status !in listOf("pending", "cancelled")) SecondaryButton(onClick = { action = "payment" }, modifier = Modifier.fillMaxWidth()) { Text("Record payment") }
                if (state.can("orders:update") && status in listOf("new", "preparing", "ready") && order.text("paymentStatus") == "unpaid") TextButton(onClick = { dismiss(); model.editOrder(order) }) { Text("Edit this order") }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = {
                        val receipt = "Restora · Order #${order.text("dailyToken")}\n" + order.array("items").joinToString("\n") { "${it.optInt("qty")} × ${it.text("name")} · ${money(it.optDouble("price", 0.0) * it.optInt("qty"))}" } + "\nTotal ${money(order.optDouble("total", 0.0))} · ${label(order.text("paymentStatus"))}"
                        context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, receipt) }, "Share receipt"))
                    }) { Text("Share receipt") }
                    TextButton(onClick = { model.action("/print-jobs", body = obj("orderId" to id, "kind" to "receipt")) { model.notify("Receipt sent to your restaurant printer") } }, enabled = !state.busy) { Text("Print") }
                }
                Row {
                    TextButton(onClick = { action = "timeline"; model.load("/orders/$id/timeline") }) { Text("Order activity") }
                    if (state.can("orders:cancel") && status !in listOf("cancelled", "completed")) TextButton(onClick = { action = "cancel" }) { Text("Cancel order", color = MaterialTheme.colorScheme.error) }
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            val next = when (status) { "pending" -> "new"; "new" -> "preparing"; "preparing" -> "ready"; "ready" -> if (order.text("type") == "delivery") "out_for_delivery" else "completed"; "out_for_delivery" -> "completed"; else -> "" }
            if (state.can("orders:update") && next.isNotEmpty()) PrimaryButton(onClick = { model.action("/orders/$id/status", "PATCH", obj("status" to next)) { saved(it) } }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp).heightIn(min = 54.dp)) { Text(if (state.busy) "Updating…" else if (status == "pending") "Accept order" else "Mark as ${label(next).lowercase()}") }
            else Spacer(Modifier.height(20.dp))
        }
    }
    if (action == "payment") FormDialog("Record payment", listOf(Field("paymentMethod", "Payment method", true, type = "choice", options = paymentOptions, initial = "cash"), Field("paymentStatus", "Payment status", true, type = "choice", options = listOf("paid" to "Paid", "khata" to "Khata"), initial = "paid")), state, { action = "" }) { body ->
        model.action("/orders/$id/payment", body = body) { saved(it); action = "" }
    }
    if (action == "cancel") FormDialog("Cancel order", listOf(Field("note", "Reason for cancellation", true)), state, { action = "" }) { body ->
        body.put("reason", "other"); model.action("/orders/$id/cancel", body = body) { saved(it); action = "" }
    }
    if (action == "timeline") AlertDialog(onDismissRequest = { action = "" }, title = { Text("Order activity") }, text = { Column { ReadState(state, "/orders/$id/timeline", model) { data -> (data as JSONArray).objects().forEach { Text("${it.text("createdAt")} · ${label(it.text("action"))}") } } } }, confirmButton = { TextButton(onClick = { action = "" }) { Text("Close") } })
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable fun TablesScreen(state: AppState, model: RestoraModel) {
    val path = "/tables/floor"
    var edit by remember { mutableStateOf<JSONObject?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { model.load(path) }
    val raw = state.data[path]
    val tables = remember(raw) { (raw as? JSONArray)?.objects() }
    LazyVerticalGrid(GridCells.Adaptive(140.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 104.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) { PageHeading("Tables", "Availability and guest requests.") { model.load(path, true) } }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (tables == null) "Checking availability…" else "${tables.count { !it.optBoolean("archived") && it.array("orders").isEmpty() }} tables available", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.can("tables:manage")) TextButton(onClick = { edit = JSONObject() }) { Text("+ Add table") }
            }
        }
        if (tables == null) item(span = { GridItemSpan(maxLineSpan) }) { ReadState(state, path, model) {} }
        else if (tables.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Empty("Your first table starts here", "Add tables to organise dine-in orders and guest requests.") }
        else items(tables, key = { it.text("_id") }, contentType = { "table" }) { table ->
            val status = when { table.optBoolean("archived") -> "archived"; table.array("orders").isEmpty() -> "available"; else -> "occupied" }
            Surface(onClick = { selectedId = table.text("_id") }, shape = MaterialTheme.shapes.medium, color = if (status == "occupied") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(painterResource(R.drawable.ic_table), null, Modifier.size(32.dp), tint = when (status) { "available" -> MaterialTheme.colorScheme.tertiary; "occupied" -> MaterialTheme.colorScheme.secondary; else -> MaterialTheme.colorScheme.onSurfaceVariant })
                    Text(table.text("name"), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOf(table.text("section"), table.text("seats").takeIf(String::isNotBlank)?.let { "$it seats" }).filterNotNull().filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    StatusPill(status)
                    if (table.array("requests").isNotEmpty()) Text("${table.array("requests").size} guest requests", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    else Text(if (table.array("orders").isEmpty()) "Ready for guests" else "${table.array("orders").size} open orders", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    tables?.firstOrNull { it.text("_id") == selectedId }?.let { table ->
        ModalBottomSheet(onDismissRequest = { selectedId = null }, containerColor = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(22.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(table.text("name"), style = MaterialTheme.typography.headlineSmall)
                table.array("orders").forEach { Text("Order #${it.text("dailyToken")} · ${money(it.optDouble("total", 0.0))}") }
                table.array("requests").forEach { request -> PrimaryButton(onClick = { model.action("/guest-requests/${request.text("_id")}/resolve") {
                    model.upsert(path, table.copyJson().put("requests", JSONArray(table.array("requests").filterNot { it.text("_id") == request.text("_id") })))
                } }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Resolve ${request.text("type")} request") } }
                if (table.text("qrCode").isNotBlank()) SecondaryButton(onClick = {
                    context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, "${table.text("name")} · ${BuildConfig.API_URL}/t/${table.text("qrCode")}") }, "Share table menu"))
                }, modifier = Modifier.fillMaxWidth()) { Text("Share guest menu") }
                if (state.can("tables:manage")) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrimaryButton(onClick = { edit = table; selectedId = null }, modifier = Modifier.weight(1f)) { Text("Edit table") }
                    SecondaryButton(onClick = { model.action("/tables/${table.text("_id")}/${if (table.optBoolean("archived")) "restore" else "archive"}") { model.upsert(path, table.copyJson().put("archived", it.optBoolean("archived"))); selectedId = null } }, enabled = !state.busy) { Text(if (table.optBoolean("archived")) "Restore" else "Archive") }
                }
            }
        }
    }
    edit?.let { table -> FormDialog(if (table.has("_id")) "Edit table" else "Add table", listOf(Field("name", "Table name", true, initial = table.text("name")), Field("section", "Section", initial = table.text("section")), Field("seats", "Seats", type = "number", initial = table.text("seats"))), state, { edit = null }) { body ->
        model.action("/tables" + if (table.has("_id")) "/${table.text("_id")}" else "", if (table.has("_id")) "PATCH" else "POST", body) { saved -> model.upsert(path, table.copyJson().apply { saved.keys().forEach { key -> put(key, saved.get(key)) } }); edit = null }
    } }
}

@Composable fun ReservationsScreen(state: AppState, model: RestoraModel) {
    var date by rememberSaveable { mutableStateOf(today()) }
    var edit by remember { mutableStateOf<JSONObject?>(null) }
    val path = "/reservations?date=$date"
    LaunchedEffect(path) { model.load(path) }
    LaunchedEffect(Unit) { if (state.can("tables:view")) model.load("/tables") }
    val raw = state.data[path]
    val bookings = remember(raw) { (raw as? JSONArray)?.objects() }
    RecordPage("Reservations", "Make room for a great evening.", bookings, path in state.loading, { model.load(path, true) }, "No bookings for this day", header = {
        DateFilter(date) { date = it }
        if (state.can("reservations:manage")) PrimaryButton(onClick = { edit = JSONObject() }) { Text("+ New reservation") }
    }) { booking ->
        Surface(onClick = { if (state.can("reservations:manage")) edit = booking }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(booking.optJSONObject("customer")?.text("name") ?: "Guest", style = MaterialTheme.typography.titleMedium); Text("Party of ${booking.optInt("partySize")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    StatusPill(booking.text("status"))
                }
                Text(bookingTime(booking.text("dateTime")), style = MaterialTheme.typography.titleSmall)
                if (booking.text("note").isNotBlank()) Text(booking.text("note"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    edit?.let { booking ->
        val tables = (state.data["/tables"] as? JSONArray)?.objects().orEmpty()
        val fields = buildList {
            if (!booking.has("_id")) { add(Field("name", "Guest name", true)); add(Field("phone", "Mobile number", true, "phone")) }
            add(Field("partySize", "Party size", true, "number", initial = booking.text("partySize").ifBlank { "2" }))
            add(Field("dateTime", "Booking date and time", true, "datetime", initial = booking.text("dateTime").ifBlank { "${date}T19:00:00+05:00" }))
            if (tables.isNotEmpty()) add(Field("tableId", "Table", type = "choice", options = listOf("" to "Not assigned") + tables.map { it.text("_id") to it.text("name") }, initial = booking.text("tableId")))
            add(Field("status", "Booking status", true, "choice", listOf("pending", "confirmed", "seated", "cancelled", "no_show").map { it to label(it) }, booking.text("status").ifBlank { "pending" }))
            add(Field("note", "Notes", type = "multiline", initial = booking.text("note")))
        }
        FormDialog(if (booking.has("_id")) "Edit reservation" else "New reservation", fields, state, { edit = null }) { body ->
            if (!body.has("tableId")) body.put("tableId", booking.text("tableId").ifBlank { null })
            model.action("/reservations" + if (booking.has("_id")) "/${booking.text("_id")}" else "", if (booking.has("_id")) "PATCH" else "POST", body) { saved -> model.upsert(path, saved); edit = null }
        }
    }
}

private fun bookingTime(value: String): String = try {
    java.time.OffsetDateTime.parse(value).atZoneSameInstant(java.time.ZoneId.of("Asia/Karachi")).format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM · h:mm a"))
} catch (_: java.time.format.DateTimeParseException) { value }

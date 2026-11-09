package app.restora.live

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.restora.live.ui.*
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun NewOrderScreen(state: AppState, model: RestoraModel) {
    val existing = state.editingOrder
    val cart = state.draft.items
    var chosenId by rememberSaveable { mutableStateOf<String?>(null) }
    var checkout by rememberSaveable { mutableStateOf(false) }
    var cartOpen by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var leave by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { model.load("/menu") }
    LaunchedEffect(checkout) { if (checkout) { if (state.can("customers:view")) model.load("/customers"); if (state.can("tables:view")) model.load("/tables") } }
    BackHandler { if (cart.isEmpty()) model.navigate("Orders") else leave = true }
    val menu = state.data["/menu"] as? JSONObject
    val allDishes = remember(menu) { menu?.array("items").orEmpty() }
    val dishes = remember(allDishes, category, search) { allDishes.filter { !it.optBoolean("hidden") && !it.optBoolean("soldOut") && (category.isBlank() || it.text("categoryId") == category) && it.text("name").contains(search, true) && onSale(it) } }
    val count = cart.sumOf { it.optInt("qty") }
    val subtotal = cart.sumOf { it.optDouble("price", 0.0) * it.optInt("qty") }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(if (existing == null) "New order" else "Edit #${existing.text("dailyToken")}", style = MaterialTheme.typography.headlineSmall); Text("Something delicious starts here.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    TextButton(onClick = { if (cart.isEmpty()) model.navigate("Orders") else leave = true }) { Text("Close") }
                }
            }
            item { OutlinedTextField(search, { search = it }, label = { Text("Find a dish") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = MaterialTheme.shapes.small) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected = category.isBlank(), onClick = { category = "" }, label = { Text("All dishes") }) }
                    items(menu?.array("categories").orEmpty(), key = { it.text("_id") }) { group -> FilterChip(selected = category == group.text("_id"), onClick = { category = group.text("_id") }, label = { Text(group.text("name")) }) }
                }
            }
            if (menu == null) item { ReadState(state, "/menu", model) {} }
            else if (dishes.isEmpty()) item { Empty("No dishes found", "Try another category or search.") }
            else items(dishes, key = { it.text("_id") }, contentType = { "dish" }) { dish ->
                Panel {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.size(50.dp)) { Icon(painterResource(R.drawable.ic_brand), null, Modifier.padding(7.dp), tint = MaterialTheme.colorScheme.primary) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(dish.text("name"), style = MaterialTheme.typography.titleMedium)
                            if (dish.text("description").isNotBlank()) Text(dish.text("description"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            dish.array("sizes").minOfOrNull { it.optDouble("price", 0.0) }?.let { Text(money(it), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                        }
                        TonalButton(onClick = { chosenId = dish.text("_id") }) { Text("Add") }
                    }
                }
            }
        }
        if (cart.isNotEmpty()) Surface(shadowElevation = 10.dp, color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) { Text("$count items", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(money(subtotal), style = MaterialTheme.typography.titleLarge) }
                PrimaryButton(onClick = { cartOpen = true }, modifier = Modifier.heightIn(min = 52.dp)) { Text("Review order") }
            }
        }
    }
    allDishes.firstOrNull { it.text("_id") == chosenId }?.let { dish -> if (menu != null) DishDialog(dish, menu, { chosenId = null }) { model.setCart(cart + it); chosenId = null } }
    if (cartOpen) ModalBottomSheet(onDismissRequest = { cartOpen = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().heightIn(max = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp * 0.86f).padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Your order", style = MaterialTheme.typography.headlineSmall)
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                items(cart, key = { it.text("lineKey") }) { line ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(line.text("name"), style = MaterialTheme.typography.titleMedium)
                        if (line.text("sizeName").isNotBlank()) Text(line.text("sizeName"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(money(line.optDouble("price", 0.0) * line.optInt("qty")), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                            SecondaryButton(onClick = { model.setCart(cart.mapNotNull { row -> if (row.text("lineKey") != line.text("lineKey")) row else if (row.optInt("qty") > 1) row.copyJson().put("qty", row.optInt("qty") - 1) else null }) }) { Text("−") }
                            Text("${line.optInt("qty")}", Modifier.padding(horizontal = 14.dp), style = MaterialTheme.typography.titleSmall)
                            SecondaryButton(onClick = { model.setCart(cart.map { row -> if (row.text("lineKey") == line.text("lineKey")) row.copyJson().put("qty", row.optInt("qty") + 1) else row }) }) { Text("+") }
                        }
                    }
                }
            }
            AmountRow("Subtotal", subtotal, emphasized = true)
            Text("Tax, discounts and delivery are confirmed at checkout.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PrimaryButton(onClick = { cartOpen = false; checkout = true }, enabled = cart.isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp).heightIn(min = 54.dp)) { Text("Continue to checkout") }
        }
    }
    if (checkout) {
        val customers = (state.data["/customers"] as? JSONArray)?.objects().orEmpty()
        val tables = (state.data["/tables"] as? JSONArray)?.objects().orEmpty()
        val fields = buildList {
            add(Field("type", "Order type", true, "choice", listOf("takeaway", "dine_in", "delivery").map { it to label(it) }, "takeaway"))
            if (customers.isNotEmpty()) add(Field("customerId", "Guest", type = "choice", options = listOf("" to "Walk-in / new guest") + customers.map { it.text("_id") to "${it.text("name")} · ${it.text("phone")}" }))
            add(Field("newName", "New guest name", whenField = "customerId".takeIf { customers.isNotEmpty() }, whenValues = setOf(""))); add(Field("newPhone", "New guest mobile", type = "phone", whenField = "customerId".takeIf { customers.isNotEmpty() }, whenValues = setOf("")))
            if (tables.isNotEmpty()) add(Field("tableId", "Table", type = "choice", options = listOf("" to "Choose a table") + tables.map { it.text("_id") to it.text("name") }, whenField = "type", whenValues = setOf("dine_in")))
            add(Field("tableName", "Or enter a table name", whenField = "type", whenValues = setOf("dine_in")))
            add(Field("address", "Delivery address", true, "multiline", whenField = "type", whenValues = setOf("delivery")))
            add(Field("area", "Delivery area", whenField = "type", whenValues = setOf("delivery")))
            add(Field("rider", "Rider", whenField = "type", whenValues = setOf("delivery")))
            add(Field("deliveryFee", "Delivery fee", type = "number", initial = "0", whenField = "type", whenValues = setOf("delivery")))
            if (state.can("orders:discount")) add(Field("discount", "Discount", type = "number", initial = "0"))
            if (state.can("orders:payment")) {
                add(Field("paymentStatus", "Payment", true, "choice", listOf("unpaid" to "Pay later", "paid" to "Paid", "khata" to "Khata"), "unpaid"))
                add(Field("paymentMethod", "Payment method", true, "choice", paymentOptions, whenField = "paymentStatus", whenValues = setOf("paid", "khata")))
            }
        }
        FormDialog(if (existing == null) "Place order" else "Save order changes", fields, state, { checkout = false }, initialValues = state.draft.checkout, onValuesChange = model::setCheckout) { body ->
            body.put("clientId", state.draft.clientId).put("items", JSONArray(cart.map { line -> obj("itemId" to line.text("itemId"), "sizeId" to line.text("sizeId"), "addonIds" to line.optJSONArray("addonIds"), "qty" to line.optInt("qty"), "note" to line.text("note")) }))
            val finish: (JSONObject) -> Unit = { saved ->
                val path = "/orders?date=${saved.text("dayKey").ifBlank { today() }}"
                if (state.data.containsKey(path)) model.upsert(path, saved)
                model.clearDraft(); checkout = false; model.navigate("Orders")
            }
            if (existing == null) {
                body.put("placedAt", java.time.Instant.now().toString())
                if (body.text("paymentStatus") in listOf("paid", "khata")) model.action("/orders", body = body, done = finish) else model.createOrder(body, finish)
            } else model.action("/orders/${existing.text("_id")}", "PUT", body, done = finish)
        }
    }
    if (leave) Confirm("Leave this order?", "Your draft is saved on this phone. You can continue it from New order.", state, { leave = false }) { model.navigate("Orders") }
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
    var sizeId by rememberSaveable(dish.text("_id")) { mutableStateOf(sizes.firstOrNull()?.text("_id")) }
    val size = sizes.firstOrNull { it.text("_id") == sizeId }
    var addonsList by rememberSaveable(dish.text("_id")) { mutableStateOf(emptyList<String>()) }
    val addons = addonsList.toSet()
    var note by rememberSaveable(dish.text("_id")) { mutableStateOf("") }
    val groups = menu.array("addonGroups").filter { it.text("_id") in dish.strings("addonGroupIds") }
    val selectedOptions = groups.flatMap { it.array("options") }.filter { it.text("_id") in addons }
    val price = (size?.optDouble("price", 0.0) ?: 0.0) + selectedOptions.sumOf { it.optDouble("price", 0.0) }
    val valid = size != null && groups.all { g -> val n = g.array("options").count { it.text("_id") in addons }; n >= g.optInt("min") && n <= g.optInt("max", 1) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(dish.text("name")) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sizes.forEach { s -> Row { RadioButton(selected = size?.text("_id") == s.text("_id"), onClick = { sizeId = s.text("_id") }); Text("${s.text("name").ifBlank { "Regular" }} · ${money(s.optDouble("price", 0.0))}", Modifier.padding(top = 12.dp)) } }
            groups.forEach { group ->
                Text("${group.text("name")} · Choose ${group.optInt("min")}–${group.optInt("max", 1)}")
                group.array("options").forEach { option ->
                    val id = option.text("_id")
                    Row { Checkbox(checked = id in addons, onCheckedChange = { checked ->
                        if (!checked) addonsList = (addons - id).toList() else {
                            val groupIds = group.array("options").map { it.text("_id") }.toSet()
                            if (group.optInt("max", 1) == 1) addonsList = (addons - groupIds + id).toList()
                            else if (addons.count { it in groupIds } < group.optInt("max", 1)) addonsList = (addons + id).toList()
                        }
                    }); Text("${option.text("name")} + ${money(option.optDouble("price", 0.0))}", Modifier.padding(top = 12.dp)) }
                }
            }
            OutlinedTextField(note, { note = it }, label = { Text("Kitchen note") })
        }
    }, confirmButton = { PrimaryButton(enabled = valid, onClick = { add(obj("itemId" to dish.text("_id"), "name" to dish.text("name"), "sizeId" to size?.text("_id"), "sizeName" to size?.text("name"), "addonIds" to JSONArray(addons.toList()), "note" to note, "price" to price, "qty" to 1)) }) { Text("Add · ${money(price)}") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

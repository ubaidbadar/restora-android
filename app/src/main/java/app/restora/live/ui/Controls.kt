package app.restora.live.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.restora.live.*
import app.restora.live.R
import org.json.JSONObject
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

fun money(value: Double): String = "Rs " + NumberFormat.getNumberInstance(Locale.US).apply { maximumFractionDigits = 2 }.format(value)
fun label(value: String): String = value.replace('_', ' ').replaceFirstChar { it.uppercase() }
fun today(): String = LocalDate.now(ZoneId.of("Asia/Karachi")).toString()

@Composable fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}
@Composable fun Empty(title: String, detail: String = "New records will appear here.") {
    Panel {
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
    }
}
@Composable fun Skeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(4) { Box(Modifier.fillMaxWidth().height(96.dp).background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)) }
    }
}
@Composable fun ReadState(state: AppState, path: String, model: RestoraModel, content: @Composable (Any) -> Unit) {
    val data = state.data[path]
    if (data != null) content(data)
    else if (path in state.loading) Skeleton()
    else Panel { Text("Could not load this page"); PrimaryButton(onClick = { model.load(path, true) }) { Text("Try again") } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun Choices(value: String, options: List<Pair<String, String>>, onChange: (String) -> Unit, enabled: Boolean = true) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(options, query) { options.filter { it.second.contains(query, true) } }
    Surface(onClick = { expanded = true }, enabled = enabled, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), shape = MaterialTheme.shapes.small) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(options.firstOrNull { it.first == value }?.second ?: "Choose…", Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Icon(painterResource(R.drawable.ic_chevron), null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (expanded) ModalBottomSheet(onDismissRequest = { expanded = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Choose an option", style = MaterialTheme.typography.titleLarge)
            if (options.size > 8) OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Search") }, singleLine = true, shape = MaterialTheme.shapes.small)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(filtered, key = { it.first }) { (key, name) ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected = value == key, role = Role.RadioButton, onClick = { onChange(key); expanded = false; query = "" }), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        RadioButton(selected = value == key, onClick = null)
                    }
                }
            }
        }
    }
}

data class Field(val key: String, val title: String, val required: Boolean = false, val type: String = "text", val options: List<Pair<String, String>> = emptyList(), val initial: String = "", val whenField: String? = null, val whenValues: Set<String> = emptySet())

/** Passwords deliberately stay out of the Activity's saved-state Bundle. */
fun formStateSaver(fields: List<Field>) = mapSaver<Map<String, String>>(
    save = { form -> form.filterKeys { key -> fields.none { it.key == key && it.type == "password" } } },
    restore = { saved -> fields.associate { it.key to if (it.type == "password") "" else (saved[it.key] as? String ?: it.initial) } },
)

@Composable fun FormInput(field: Field, value: String, change: (String) -> Unit, enabled: Boolean = true) {
    var visible by remember { mutableStateOf(false) }
    if (field.type == "prices") PriceRows(value, change)
    else if (field.type == "datetime") DateTimeField(field.title, value, change)
    else if (field.options.isNotEmpty() || field.type == "choice") {
        Text(field.title, style = MaterialTheme.typography.labelLarge)
        Choices(value, field.options, change, enabled && field.options.isNotEmpty())
    } else OutlinedTextField(
        value = value, onValueChange = change,
        label = { Text(field.title + if (field.required) " *" else "") }, modifier = Modifier.fillMaxWidth(), enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outline, unfocusedContainerColor = MaterialTheme.colorScheme.surface),
        singleLine = field.type !in listOf("multiline", "list"),
        keyboardOptions = KeyboardOptions(keyboardType = when (field.type) { "number" -> KeyboardType.Decimal; "phone" -> KeyboardType.Phone; "email" -> KeyboardType.Email; "password" -> KeyboardType.Password; else -> KeyboardType.Text }),
        visualTransformation = if (field.type == "password" && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (field.type == "password") ({ IconButton(onClick = { visible = !visible }) { Icon(painterResource(if (visible) R.drawable.ic_eye_off else R.drawable.ic_eye), if (visible) "Hide password" else "Show password") } }) else null,
    )
}

fun visibleFormFields(fields: List<Field>, values: Map<String, String>): List<Field> = fields.filter { field ->
    val controller = field.whenField
    if (controller == null) true
    else (values[controller] ?: fields.firstOrNull { it.key == controller }?.initial) in field.whenValues
}

fun validateForm(fields: List<Field>, values: Map<String, String>): Pair<JSONObject?, String?> {
    val missing = fields.firstOrNull { it.required && values[it.key].isNullOrBlank() }
    if (missing != null) return null to "Enter ${missing.title.lowercase()}"
    val answer = JSONObject()
    for (field in fields) {
        val value = values[field.key].orEmpty().trim()
        when (field.type) {
            "number" -> if (value.isNotEmpty()) {
                val number = value.toDoubleOrNull()
                if (number == null || !number.isFinite()) return null to "Enter a valid ${field.title.lowercase()}"
                answer.put(field.key, number)
            }
            "list" -> answer.put(field.key, org.json.JSONArray(value.split(',', '\n').map(String::trim).filter(String::isNotEmpty)))
            else -> answer.put(field.key, value)
        }
    }
    return answer to null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun FormDialog(title: String, fields: List<Field>, state: AppState, dismiss: () -> Unit, initialValues: Map<String, String> = emptyMap(), onValuesChange: ((Map<String, String>) -> Unit)? = null, save: (JSONObject) -> Unit) {
    var form by rememberSaveable(title, stateSaver = formStateSaver(fields)) { mutableStateOf(fields.associate { it.key to it.initial } + initialValues) }
    var validation by rememberSaveable { mutableStateOf<String?>(null) }
    val visibleFields = visibleFormFields(fields, form)
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.9f
    ModalBottomSheet(onDismissRequest = { if (!state.busy) dismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).imePadding().padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                (validation ?: state.error)?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                visibleFields.forEach { field -> key(field.key) { FormInput(field, form[field.key].orEmpty(), { form = form + (field.key to it); onValuesChange?.invoke(form) }, !state.busy) } }
                Spacer(Modifier.height(2.dp))
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton(onClick = dismiss, enabled = !state.busy, modifier = Modifier.heightIn(min = 52.dp)) { Text("Cancel") }
                PrimaryButton(onClick = {
                    val (answer, error) = validateForm(visibleFields, form)
                    validation = error
                    if (answer != null) save(answer)
                }, enabled = !state.busy, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                    Text(if (state.busy) "Saving…" else when (title) { "Place order" -> "Place order"; "Record payment" -> "Record payment"; "Cancel order" -> "Cancel order"; "Delete restaurant" -> "Schedule deletion"; "Transfer ownership" -> "Transfer ownership"; else -> if (title.startsWith("Add ") || title.startsWith("Create ")) title else "Save changes" })
                }
            }
        }
    }
}

@Composable fun Confirm(title: String, detail: String, state: AppState, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = { if (!state.busy) dismiss() }, title = { Text(title) }, text = { Column { Text(detail); state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } },
        confirmButton = { PrimaryButton(onClick = confirm, enabled = !state.busy) { Text(if (state.busy) "Saving…" else "Confirm") } },
        dismissButton = { TextButton(onClick = dismiss, enabled = !state.busy) { Text("Cancel") } })
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DateTimeField(title: String, value: String, change: (String) -> Unit) {
    val zone = java.time.ZoneId.of("Asia/Karachi")
    val parsed = runCatching { java.time.OffsetDateTime.parse(value).atZoneSameInstant(zone) }.getOrElse { java.time.ZonedDateTime.now(zone) }
    var stage by remember { mutableStateOf("") }
    val dateState = rememberDatePickerState(initialSelectedDateMillis = parsed.toLocalDate().atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli())
    val timeState = rememberTimePickerState(initialHour = parsed.hour, initialMinute = parsed.minute)
    Text(title, style = MaterialTheme.typography.labelLarge)
    SecondaryButton(onClick = { stage = "date" }, modifier = Modifier.fillMaxWidth()) { Text(parsed.format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM · h:mm a"))) }
    if (stage == "date") DatePickerDialog(onDismissRequest = { stage = "" }, confirmButton = { TextButton(onClick = { stage = "time" }) { Text("Choose time") } }, dismissButton = { TextButton(onClick = { stage = "" }) { Text("Cancel") } }) { DatePicker(state = dateState) }
    if (stage == "time") AlertDialog(onDismissRequest = { stage = "" }, title = { Text("Booking time (Pakistan)") }, text = { TimePicker(timeState) }, confirmButton = { TextButton(onClick = {
        val day = java.time.Instant.ofEpochMilli(dateState.selectedDateMillis ?: System.currentTimeMillis()).atZone(java.time.ZoneOffset.UTC).toLocalDate()
        change(day.atTime(timeState.hour, timeState.minute).atZone(zone).toOffsetDateTime().toString()); stage = ""
    }) { Text("Done") } }, dismissButton = { TextButton(onClick = { stage = "" }) { Text("Cancel") } })
}

@Composable fun PriceRows(value: String, change: (String) -> Unit) {
    val rows = remember(value) { value.lines().filter(String::isNotBlank).ifEmpty { listOf(" = ") }.map { it.substringBeforeLast('=').trim() to it.substringAfterLast('=').trim() } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("Prices & sizes")
        rows.forEachIndexed { index, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(row.first, { name -> change(rows.mapIndexed { i, entry -> if (i == index) "$name = ${entry.second}" else "${entry.first} = ${entry.second}" }.joinToString("\n")) }, label = { Text("Size / option") }, singleLine = true, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small)
                OutlinedTextField(row.second, { price -> change(rows.mapIndexed { i, entry -> if (i == index) "${entry.first} = $price" else "${entry.first} = ${entry.second}" }.joinToString("\n")) }, label = { Text("Rs") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(0.8f), shape = MaterialTheme.shapes.small)
                if (rows.size > 1) TextButton(onClick = { change(rows.filterIndexed { i, _ -> i != index }.joinToString("\n") { "${it.first} = ${it.second}" }) }) { Text("×") }
            }
        }
        TextButton(onClick = { change(rows.joinToString("\n") { "${it.first} = ${it.second}" } + "\n = ") }) { Text("+ Add another price") }
    }
}

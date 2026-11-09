package app.restora.live.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}
@Composable fun Empty(title: String, detail: String = "New records will appear here.") = Panel {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable fun Skeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(5) { Box(Modifier.fillMaxWidth().height(88.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))) }
    }
}
@Composable fun ReadState(state: AppState, path: String, model: RestoraModel, content: @Composable (Any) -> Unit) {
    val data = state.data[path]
    if (data != null) content(data)
    else if (path in state.loading) Skeleton()
    else Panel { Text("Could not load this page"); Button(onClick = { model.load(path, true) }) { Text("Try again") } }
}
@Composable fun Choices(value: String, options: List<Pair<String, String>>, onChange: (String) -> Unit, enabled: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(options.firstOrNull { it.first == value }?.second ?: "Choose…")
        }
        DropdownMenu(expanded, { expanded = false }) { options.forEach { (key, name) ->
            DropdownMenuItem(text = { Text(name) }, onClick = { onChange(key); expanded = false })
        } }
    }
}

data class Field(val key: String, val title: String, val required: Boolean = false, val type: String = "text", val options: List<Pair<String, String>> = emptyList(), val initial: String = "")

@Composable fun FormDialog(title: String, fields: List<Field>, state: AppState, dismiss: () -> Unit, save: (JSONObject) -> Unit) {
    var form by remember(title, fields) { mutableStateOf(fields.associate { it.key to it.initial }) }
    var validation by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = { if (!state.busy) dismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().imePadding(), color = MaterialTheme.colorScheme.background) {
            Column {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = dismiss, enabled = !state.busy) { Icon(painterResource(R.drawable.ic_back), "Close") }
                    Text(title, Modifier.weight(1f).padding(top = 12.dp), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = {
                        validation = fields.firstOrNull { it.required && form[it.key].isNullOrBlank() }?.let { "Enter ${it.title.lowercase()}" }
                        if (validation == null) {
                            val result = JSONObject()
                            fields.forEach { f ->
                                val v = form[f.key].orEmpty().trim()
                                when (f.type) {
                                    "number" -> if (v.isNotEmpty()) {
                                        val n = v.toDoubleOrNull()
                                        if (n == null || !n.isFinite()) validation = "Enter a valid ${f.title.lowercase()}" else result.put(f.key, n)
                                    }
                                    "list" -> result.put(f.key, org.json.JSONArray(v.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }))
                                    else -> if (v.isNotEmpty() || !f.required) result.put(f.key, v)
                                }
                            }
                            if (validation == null) save(result)
                        }
                    }, enabled = !state.busy) { Text(if (state.busy) "Saving…" else when (title) { "Sign in" -> "Sign in"; "Create account" -> "Create"; "Verify email" -> "Verify"; "Reset password" -> "Send code"; "Choose password" -> "Update"; "Place order" -> "Place order"; "Record payment" -> "Record"; "Cancel order" -> "Cancel order"; else -> "Save" }) }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    (validation ?: state.error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    fields.forEach { f ->
                        if (f.type == "datetime") {
                            DateTimeField(f.title, form[f.key].orEmpty(), { form = form + (f.key to it) })
                        } else if (f.options.isNotEmpty() || f.type == "choice") {
                            Text(f.title, style = MaterialTheme.typography.labelLarge)
                            Choices(form[f.key].orEmpty(), f.options, { form = form + (f.key to it) }, !state.busy && f.options.isNotEmpty())
                        } else OutlinedTextField(
                            value = form[f.key].orEmpty(), onValueChange = { form = form + (f.key to it) },
                            label = { Text(f.title + if (f.required) " *" else "") }, modifier = Modifier.fillMaxWidth(), enabled = !state.busy,
                            singleLine = f.type !in listOf("multiline", "list"),
                            keyboardOptions = KeyboardOptions(keyboardType = when (f.type) { "number" -> KeyboardType.Decimal; "phone" -> KeyboardType.Phone; "email" -> KeyboardType.Email; "password" -> KeyboardType.Password; else -> KeyboardType.Text }),
                            visualTransformation = if (f.type == "password" && !visible) PasswordVisualTransformation() else VisualTransformation.None,
                            trailingIcon = if (f.type == "password") ({ IconButton(onClick = { visible = !visible }) { Icon(painterResource(if (visible) R.drawable.ic_eye_off else R.drawable.ic_eye), if (visible) "Hide password" else "Show password") } }) else null
                        )
                    }
                }
            }
        }
    }
}

@Composable fun Confirm(title: String, detail: String, state: AppState, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = { if (!state.busy) dismiss() }, title = { Text(title) }, text = { Column { Text(detail); state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } },
        confirmButton = { Button(onClick = confirm, enabled = !state.busy) { Text(if (state.busy) "Saving…" else "Confirm") } },
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
    OutlinedButton(onClick = { stage = "date" }, modifier = Modifier.fillMaxWidth()) { Text(parsed.format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM · h:mm a"))) }
    if (stage == "date") DatePickerDialog(onDismissRequest = { stage = "" }, confirmButton = { TextButton(onClick = { stage = "time" }) { Text("Choose time") } }, dismissButton = { TextButton(onClick = { stage = "" }) { Text("Cancel") } }) { DatePicker(state = dateState) }
    if (stage == "time") AlertDialog(onDismissRequest = { stage = "" }, title = { Text("Booking time (Pakistan)") }, text = { TimePicker(timeState) }, confirmButton = { TextButton(onClick = {
        val day = java.time.Instant.ofEpochMilli(dateState.selectedDateMillis ?: System.currentTimeMillis()).atZone(java.time.ZoneOffset.UTC).toLocalDate()
        change(day.atTime(timeState.hour, timeState.minute).atZone(zone).toOffsetDateTime().toString()); stage = ""
    }) { Text("Done") } }, dismissButton = { TextButton(onClick = { stage = "" }) { Text("Cancel") } })
}

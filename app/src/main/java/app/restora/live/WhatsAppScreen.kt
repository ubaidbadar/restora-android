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

@Composable fun WhatsAppScreen(state: AppState, model: RestoraModel) {
    val path = "/whatsapp/conversations"
    var selected by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(Unit) { model.load(path); if (state.can("devices:manage")) model.load("/whatsapp/connections") }
    PageTitle("WhatsApp inbox") { model.load(path, true) }
    ReadState(state, path, model) { data ->
        val conversations = (data as JSONArray).objects()
        if (conversations.isEmpty()) Empty("No conversations", "Messages from your connected WhatsApp number will appear here.")
        conversations.forEach { thread -> Card(onClick = { selected = thread }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) { Text(thread.text("customerName").ifBlank { thread.text("phone") }, style = MaterialTheme.typography.titleMedium); Text(thread.optJSONObject("last")?.text("text").orEmpty()); Text("${thread.optInt("count")} messages") }
        } }
    }
    if (state.can("devices:manage")) {
        Text("Connected numbers", style = MaterialTheme.typography.titleMedium)
        (state.data["/whatsapp/connections"] as? JSONArray)?.objects()?.forEach { connection -> Panel {
            Text(connection.text("displayPhoneNumber").ifBlank { connection.text("phoneNumberId") })
            Row { Checkbox(connection.optBoolean("invoiceOnComplete"), { enabled -> model.action("/whatsapp/connections/${connection.text("_id")}", "PATCH", obj("invoiceOnComplete" to enabled)) { model.upsert("/whatsapp/connections", it) } }); Text("Send invoice when completed", Modifier.padding(top = 12.dp)) }
        } }
    }
    selected?.let { thread ->
        val conversation = "/whatsapp/conversations/${thread.text("phone")}?phoneNumberId=${thread.text("phoneNumberId")}"
        var reply by remember { mutableStateOf("") }
        LaunchedEffect(conversation) { model.load(conversation) }
        AlertDialog(onDismissRequest = { selected = null }, title = { Text(thread.text("customerName").ifBlank { thread.text("phone") }) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReadState(state, conversation, model) { answer -> (answer as JSONObject).array("messages").forEach { message ->
                    Text("${if (message.text("direction") == "outbound") "You" else "Guest"}: ${message.text("text").ifBlank { label(message.text("type")) }}")
                } }
                if (state.can("orders:update")) OutlinedTextField(reply, { reply = it }, label = { Text("Reply") })
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = { if (state.can("orders:update")) Button(onClick = {
            model.action("/whatsapp/conversations/${thread.text("phone")}/reply", body = obj("phoneNumberId" to thread.text("phoneNumberId"), "text" to reply)) { answer ->
                answer.optJSONObject("message")?.let { message ->
                    val current = state.data[conversation] as? JSONObject ?: JSONObject()
                    model.put(conversation, current.copyJson().put("messages", JSONArray(current.array("messages") + message)))
                }
                reply = ""
            }
        }, enabled = !state.busy && reply.isNotBlank()) { Text(if (state.busy) "Sending…" else "Send reply") } }, dismissButton = { TextButton(onClick = { selected = null }) { Text("Close") } })
    }
}

package app.restora.live

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
@Composable fun WhatsAppScreen(state: AppState, model: RestoraModel) {
    val path = "/whatsapp/conversations"
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { model.load(path); if (state.can("devices:manage")) model.load("/whatsapp/connections") }
    val raw = state.data[path]
    val conversations = remember(raw) { (raw as? JSONArray)?.objects() }
    RecordPage("Conversations", "A warm welcome, before they arrive.", conversations, path in state.loading, { model.load(path, true) }, "Your inbox is quiet", recordKey = { it.text("phoneNumberId") + ":" + it.text("phone") }, header = {
        if (state.can("devices:manage")) (state.data["/whatsapp/connections"] as? JSONArray)?.objects()?.forEach { connection ->
            Panel {
                Text(connection.text("displayPhoneNumber").ifBlank { connection.text("phoneNumberId") }, style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Send invoice when completed", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Switch(connection.optBoolean("invoiceOnComplete"), { enabled -> model.action("/whatsapp/connections/${connection.text("_id")}", "PATCH", obj("invoiceOnComplete" to enabled)) { model.upsert("/whatsapp/connections", it) } })
                }
            }
        }
    }) { thread ->
        Surface(onClick = { selectedKey = thread.text("phoneNumberId") + ":" + thread.text("phone") }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.small) { Icon(painterResource(R.drawable.ic_chat), null, Modifier.padding(12.dp).size(24.dp), tint = MaterialTheme.colorScheme.tertiary) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(thread.text("customerName").ifBlank { thread.text("phone") }, style = MaterialTheme.typography.titleMedium)
                    Text(thread.optJSONObject("last")?.text("text").orEmpty(), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(painterResource(R.drawable.ic_chevron), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    conversations?.firstOrNull { it.text("phoneNumberId") + ":" + it.text("phone") == selectedKey }?.let { thread ->
        val conversation = "/whatsapp/conversations/${thread.text("phone")}?phoneNumberId=${thread.text("phoneNumberId")}"
        var reply by rememberSaveable(selectedKey) { mutableStateOf("") }
        LaunchedEffect(conversation) { model.load(conversation) }
        val answer = state.data[conversation] as? JSONObject
        val messages = remember(answer) { answer?.array("messages") }
        ModalBottomSheet(onDismissRequest = { selectedKey = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxWidth().heightIn(max = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp * 0.88f).imePadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                PageHeading(thread.text("customerName").ifBlank { thread.text("phone") }, thread.text("phone")) { model.load(conversation, true) }
                LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                    if (messages == null) item { ReadState(state, conversation, model) {} }
                    else items(messages, key = { it.text("_id") }) { message ->
                        val outgoing = message.text("direction") == "outbound"
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start) {
                            Surface(color = if (outgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium, modifier = Modifier.widthIn(max = 300.dp)) {
                                Text(message.text("text").ifBlank { label(message.text("type")) }, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (state.can("orders:update")) Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(reply, { reply = it }, label = { Text("Write a reply") }, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small, maxLines = 4)
                    PrimaryButton(onClick = {
                        model.action("/whatsapp/conversations/${thread.text("phone")}/reply", body = obj("phoneNumberId" to thread.text("phoneNumberId"), "text" to reply)) { response ->
                            response.optJSONObject("message")?.let { message -> model.put(conversation, (answer?.copyJson() ?: JSONObject()).put("messages", JSONArray(answer?.array("messages").orEmpty() + message))) }
                            reply = ""; model.notify("Reply sent")
                        }
                    }, enabled = !state.busy && reply.isNotBlank()) { Text(if (state.busy) "Sending…" else "Send") }
                } else Spacer(Modifier.height(16.dp))
            }
        }
    }
}

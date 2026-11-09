package app.restora.live.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.restora.live.R
import app.restora.live.text
import org.json.JSONObject

@Composable fun BrandMark(size: Int = 44) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(size.dp)) {
        Icon(painterResource(R.drawable.ic_brand), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(3.dp))
    }
}

@Composable fun SectionLabel(title: String, detail: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        detail?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable fun PageHeading(title: String, subtitle: String, refresh: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = refresh) { Text("Refresh") }
    }
}

/** Large datasets have one scroll owner and compose only visible rows, with server record IDs as keys. */
@Composable fun RecordPage(
    title: String,
    subtitle: String,
    records: List<JSONObject>?,
    loading: Boolean,
    refresh: () -> Unit,
    emptyTitle: String,
    recordKey: (JSONObject) -> String = { it.text("_id") },
    header: @Composable ColumnScope.() -> Unit = {},
    row: @Composable (JSONObject) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "heading", contentType = "heading") { PageHeading(title, subtitle, refresh) }
        item(key = "controls", contentType = "controls") { Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = header) }
        if (records == null) {
            item(key = "read-state", contentType = "read-state") {
                if (loading) Skeleton() else Empty("Unable to load this page", "Check your connection and tap Refresh.")
            }
        } else if (records.isEmpty()) {
            item(key = "empty", contentType = "read-state") { Empty(emptyTitle, "Try another filter, or add your first record.") }
        } else items(records, key = { "record:${recordKey(it)}" }, contentType = { "record" }) { row(it) }
    }
}

@Composable fun StatusPill(value: String) {
    val colors = MaterialTheme.colorScheme
    val positive = value in listOf("paid", "completed", "available", "confirmed", "active", "seated")
    val negative = value in listOf("cancelled", "suspended", "no_show", "sold_out")
    val container = when { positive -> colors.tertiaryContainer; negative -> colors.errorContainer; else -> colors.primaryContainer }
    val ink = when { positive -> colors.onTertiaryContainer; negative -> colors.onErrorContainer; else -> colors.onPrimaryContainer }
    Surface(color = container, shape = MaterialTheme.shapes.extraSmall) {
        Text(label(value), Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = ink, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable fun NavigationRow(title: String, detail: String, icon: Int, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
            Icon(painterResource(icon), null, Modifier.padding(11.dp).size(22.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(painterResource(R.drawable.ic_chevron), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable fun AmountRow(title: String, amount: Double, emphasized: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium, color = if (emphasized) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(money(amount), style = if (emphasized) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium, fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium)
    }
}

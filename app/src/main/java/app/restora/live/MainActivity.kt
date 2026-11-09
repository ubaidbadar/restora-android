package app.restora.live

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.restora.live.ui.*
import app.restora.live.ui.theme.RestoraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { RestoraTheme { RestoraApp() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun RestoraApp(model: RestoraModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, state.scope, state.generation) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_START -> { model.startLive(); model.reconnect() }
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> model.stopLive()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) model.startLive()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); model.stopLive() }
    }
    if (state.starting) {
        Surface(Modifier.fillMaxSize()) { Column(Modifier.statusBarsPadding().padding(24.dp)) { Text("Restora", style = MaterialTheme.typography.headlineLarge); Spacer(Modifier.height(24.dp)); Skeleton() } }
        return
    }
    if (state.me == null) { AuthScreen(state, model); return }
    if (state.workplace == null) { WorkspaceScreen(state, model); return }
    val pages = buildList {
        if (state.can("reports:view")) add("Home" to R.drawable.ic_home)
        if (state.can("orders:view")) add("Orders" to R.drawable.ic_receipt)
        if (state.can("tables:view")) add("Tables" to R.drawable.ic_table)
        if (state.can("customers:view")) add("Customers" to R.drawable.ic_people)
        add("More" to R.drawable.ic_more)
    }
    val screen = if (state.page == "Home" && !state.can("reports:view")) pages.first().first else state.page
    BackHandler(enabled = screen !in pages.map { it.first }) { model.navigate("More") }
    Scaffold(
        topBar = { TopAppBar(title = { Column {
            Text(state.workplace?.optJSONObject("restaurant")?.text("name").orEmpty(), style = MaterialTheme.typography.titleLarge)
            Text(state.workplace?.optJSONObject("branch")?.text("name").orEmpty(), style = MaterialTheme.typography.labelMedium)
        } }, actions = { IconButton(onClick = { model.navigate("Workspace") }) { Icon(painterResource(R.drawable.ic_switch), "Switch restaurant or branch") } }) },
        bottomBar = { NavigationBar(containerColor = MaterialTheme.colorScheme.surface) { pages.forEach { (name, icon) ->
            NavigationBarItem(selected = screen == name, onClick = { model.navigate(name) }, icon = { Icon(painterResource(icon), name) }, label = { Text(name) })
        } } },
        floatingActionButton = { if (screen in listOf("Orders", "Tables", "Home") && state.can("orders:create")) FloatingActionButton(onClick = { model.navigate("New order") }) { Icon(painterResource(R.drawable.ic_add), "New order") } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            state.notice?.let { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text(it, Modifier.weight(1f)); TextButton(onClick = model::clearMessage) { Text("Dismiss") } } }
            if (state.offline) Text("Offline · Showing saved data. Connect before saving.", Modifier.fillMaxWidth().padding(12.dp), color = MaterialTheme.colorScheme.error)
            if (state.error != null) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(state.error.orEmpty(), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = model::clearMessage) { Text("Dismiss") }
            }
            key(state.generation) {
                when (screen) {
                    "New order" -> NewOrderScreen(state, model)
                    "Workspace" -> WorkspaceScreen(state, model)
                    else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).padding(bottom = 72.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        when (screen) {
                            "Home" -> DashboardScreen(state, model)
                            "Orders" -> OrdersScreen(state, model)
                            "Tables" -> TablesScreen(state, model)
                            "Customers" -> CustomersScreen(state, model)
                            "Menu" -> MenuScreen(state, model)
                            "WhatsApp" -> WhatsAppScreen(state, model)
                            "Reservations" -> ReservationsScreen(state, model)
                            "Settings" -> SettingsScreen(state, model)
                            "Branches", "Staff", "Roles", "Teams", "Activity", "Devices", "Printers" -> ManagementScreen(screen, state, model)
                            "Account" -> AccountScreen(state, model)
                            "History" -> OrdersScreen(state, model, history = true)
                            else -> MoreScreen(state, model)
                        }
                    }
                }
            }
        }
    }
}

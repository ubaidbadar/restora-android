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
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import app.restora.live.ui.*
import app.restora.live.ui.theme.RestoraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        setContent { RestoraTheme { RestoraApp() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun RestoraApp(model: RestoraModel = viewModel(factory = RestoraModel.Factory)) {
    val state by model.state.collectAsStateWithLifecycle()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, state.scope, state.generation, state.starting) {
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
    val screen = if (state.canOpenPage(state.page)) state.page else pages.first().first
    BackHandler(enabled = screen !in pages.map { it.first }) { model.navigate("More") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        BrandMark(36)
                        Column {
                            Text(state.workplace?.optJSONObject("restaurant")?.text("name").orEmpty(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(state.workplace?.optJSONObject("branch")?.text("name").orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    IconButton(onClick = { model.navigate("Workspace") }) { Icon(painterResource(R.drawable.ic_switch), "Switch restaurant or branch") }
                    IconButton(onClick = { model.navigate("Account") }) { Icon(painterResource(R.drawable.ic_people), "Your account") }
                },
            )
        },
        bottomBar = { if (screen != "New order") NavigationBar(containerColor = MaterialTheme.colorScheme.surface) { pages.forEach { (name, icon) ->
            NavigationBarItem(selected = screen == name, onClick = { model.navigate(name) }, icon = { Icon(painterResource(icon), name) }, label = { Text(if (name == "Customers") "Guests" else name) })
        } } },
        floatingActionButton = { if (screen in listOf("Orders", "Tables", "Home") && state.can("orders:create")) ExtendedFloatingActionButton(onClick = { model.navigate("New order") }, icon = { Icon(painterResource(R.drawable.ic_add), null) }, text = { Text("New order") }, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            state.notice?.let { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text(it, Modifier.weight(1f)); TextButton(onClick = model::clearMessage) { Text("Dismiss") } } }
            if (state.offline) Text("Offline · Your saved data is available.", Modifier.fillMaxWidth().padding(12.dp), color = MaterialTheme.colorScheme.error)
            if (state.error != null) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(state.error.orEmpty(), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = model::clearMessage) { Text("Dismiss") }
            }
            key(state.generation) {
                when (screen) {
                    "New order" -> NewOrderScreen(state, model)
                    "Workspace" -> WorkspaceScreen(state, model)
                    "Home" -> DashboardScreen(state, model)
                    "Orders" -> OrdersScreen(state, model)
                    "History" -> OrdersScreen(state, model, history = true)
                    "Tables" -> TablesScreen(state, model)
                    "Customers" -> CustomersScreen(state, model)
                    "Menu" -> MenuScreen(state, model)
                    "Reservations" -> ReservationsScreen(state, model)
                    "WhatsApp" -> WhatsAppScreen(state, model)
                    "Branches", "Staff", "Roles", "Teams", "Activity", "Devices", "Printers" -> ManagementScreen(screen, state, model)
                    else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).padding(bottom = 72.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        when (screen) {
                            "Settings" -> SettingsScreen(state, model)
                            "Account" -> AccountScreen(state, model)
                            else -> MoreScreen(state, model)
                        }
                    }
                }
            }
        }
    }
}

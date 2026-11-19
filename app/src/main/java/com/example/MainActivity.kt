package com.example

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.core.PreferencesStore
import com.example.core.ReminderNotificationManager
import com.example.core.configureAppCheck
import com.example.data.AppearanceMode
import com.example.data.GeminiAssistantRepository
import com.example.data.LogisticsRepository
import com.example.data.RtoWebLookupService
import com.example.ui.LogisticsViewModel
import com.example.ui.auth.AuthScreen
import com.example.ui.screens.DocumentStudioScreen
import com.example.ui.screens.GarageScreen
import com.example.ui.screens.OverviewScreen
import com.example.ui.screens.SheetsScreen
import com.example.ui.screens.ToolsScreen
import com.example.ui.theme.GoLogisticsTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

const val ROUTE_OVERVIEW = "overview"
const val ROUTE_SHEETS = "sheets"
const val ROUTE_GARAGE = "garage"
const val ROUTE_STUDIO = "studio"
const val ROUTE_TOOLS = "tools"

private data class NavDestinationItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val navItems = listOf(
    NavDestinationItem(ROUTE_OVERVIEW, "Overview", Icons.Rounded.Dashboard, Icons.Outlined.Dashboard),
    NavDestinationItem(ROUTE_SHEETS, "Sheets", Icons.Rounded.GridOn, Icons.Outlined.GridOn),
    NavDestinationItem(ROUTE_GARAGE, "Garage", Icons.Rounded.DirectionsCar, Icons.Outlined.DirectionsCar),
    NavDestinationItem(ROUTE_STUDIO, "Studio", Icons.Rounded.PictureAsPdf, Icons.Outlined.PictureAsPdf),
    NavDestinationItem(ROUTE_TOOLS, "Tools", Icons.Rounded.Build, Icons.Outlined.Build)
)

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            val prefsStore = remember { PreferencesStore(applicationContext) }
            val appearanceMode by prefsStore.appearanceMode.collectAsStateWithLifecycle(
                initialValue = AppearanceMode.SYSTEM
            )
            GoLogisticsTheme(appearanceMode = appearanceMode) {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedWorkspace(currentUserId = user.uid)
        }
    }
}

@Composable
private fun AuthenticatedWorkspace(currentUserId: String) {
    val context = LocalContext.current
    val viewModel: LogisticsViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as Application
                val dbId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(dbId)
                LogisticsViewModel(
                    repository = LogisticsRepository(db),
                    geminiRepo = GeminiAssistantRepository(),
                    rtoLookup = RtoWebLookupService(),
                    preferencesStore = PreferencesStore(app),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_OVERVIEW
    val snackbarHostState = remember { SnackbarHostState() }
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
    val alerts by viewModel.upcomingAlerts.collectAsStateWithLifecycle()
    val profile by viewModel.businessProfile.collectAsStateWithLifecycle()

    LaunchedEffect(toastMessage) {
        toastMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    // Automatically evaluate and dispatch 3-day early alerts once per session if permitted
    var alertsChecked by remember { mutableStateOf(false) }
    LaunchedEffect(alerts, profile.notificationsEnabled) {
        if (!alertsChecked && alerts.isNotEmpty() && profile.notificationsEnabled) {
            ReminderNotificationManager.dispatchAlertsIfPermitted(context, alerts)
            alertsChecked = true
        }
    }

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                navItems.forEach { item ->
                    val selected = currentRoute == item.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navigateToTab(item.route) },
                        icon = {
                            Icon(
                                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label
                            )
                        },
                        label = { Text(item.label) },
                        modifier = Modifier.testTag("nav_${item.route}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = ROUTE_OVERVIEW
            ) {
                composable(ROUTE_OVERVIEW) {
                    OverviewScreen(
                        viewModel = viewModel,
                        onNavigateToTab = ::navigateToTab
                    )
                }
                composable(ROUTE_SHEETS) {
                    BackHandler { navigateToTab(ROUTE_OVERVIEW) }
                    SheetsScreen(viewModel = viewModel)
                }
                composable(ROUTE_GARAGE) {
                    BackHandler { navigateToTab(ROUTE_OVERVIEW) }
                    GarageScreen(viewModel = viewModel)
                }
                composable(ROUTE_STUDIO) {
                    BackHandler { navigateToTab(ROUTE_OVERVIEW) }
                    DocumentStudioScreen(viewModel = viewModel)
                }
                composable(ROUTE_TOOLS) {
                    BackHandler { navigateToTab(ROUTE_OVERVIEW) }
                    ToolsScreen(viewModel = viewModel)
                }
            }
        }
    }
}

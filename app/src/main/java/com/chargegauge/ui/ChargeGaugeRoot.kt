package com.chargegauge.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.chargegauge.ChargeGaugeApp
import com.chargegauge.data.AppSettings
import com.chargegauge.service.MonitorService
import com.chargegauge.ui.health.HealthScreen
import com.chargegauge.ui.now.NowScreen
import com.chargegauge.ui.sessions.SessionDetailScreen
import com.chargegauge.ui.sessions.SessionsScreen
import com.chargegauge.ui.settings.SettingsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("now", "Now", Icons.Filled.Bolt),
    Tab("sessions", "Sessions", Icons.Filled.History),
    Tab("health", "Health", Icons.Filled.Favorite),
    Tab("settings", "Settings", Icons.Filled.Settings),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChargeGaugeRoot() {
    val context = LocalContext.current
    val app = context.applicationContext as ChargeGaugeApp
    val navController = rememberNavController()
    val snapshot by app.container.repository.snapshot.collectAsStateWithLifecycle()
    val settings by app.container.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: "now"
    val lifecycleOwner = LocalLifecycleOwner.current

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(snapshot.isPlugged, settings.monitorWhileCharging, snapshot.timestampMillis) {
        val recording = settings.monitorWhileCharging && snapshot.isPlugged && snapshot.timestampMillis > 0L
        if (recording && !MonitorService.isRunning) MonitorService.start(context)
        if (!settings.monitorWhileCharging && MonitorService.isRunning) MonitorService.stop(context)
    }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                if (!MonitorService.isRunning) app.container.repository.refreshOnce()
                delay(1_000)
            }
        }
    }

    val title = when {
        route.startsWith("sessions/") -> "Session"
        else -> tabs.firstOrNull { it.route == route }?.label ?: "ChargeGauge"
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text(title) }) },
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    val selected = if (tab.route == "sessions") route.startsWith("sessions") else route == tab.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "now",
            modifier = Modifier.padding(padding),
        ) {
            composable("now") {
                NowScreen(
                    snapshot = snapshot,
                    onOpenSettings = { navController.navigate("settings") },
                )
            }
            composable("sessions") {
                SessionsScreen(onOpenSession = { id -> navController.navigate("sessions/$id") })
            }
            composable("sessions/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull()
                if (id == null) {
                    Text("Missing session")
                } else {
                    SessionDetailScreen(sessionId = id, onDeleted = { navController.popBackStack() })
                }
            }
            composable("health") {
                HealthScreen(
                    snapshot = snapshot,
                    onOpenSettings = { navController.navigate("settings") },
                )
            }
            composable("settings") { SettingsScreen() }
        }
    }
}

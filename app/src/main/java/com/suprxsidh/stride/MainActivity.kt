package com.suprxsidh.stride

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.suprxsidh.stride.health.HealthConnectManager
import com.suprxsidh.stride.health.HealthConnectSyncWorker
import com.suprxsidh.stride.reminders.ReminderIntents
import com.suprxsidh.stride.reminders.ReminderScheduler
import com.suprxsidh.stride.ui.nav.StrideNavHost
import com.suprxsidh.stride.ui.nav.Routes
import com.suprxsidh.stride.ui.theme.StrideTheme
import com.suprxsidh.stride.ui.theme.StrideOnSurfaceMuted
import com.suprxsidh.stride.ui.theme.component.NavFlagIcon
import kotlinx.coroutines.launch

private data class BottomDestination(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val BOTTOM_DESTINATIONS = listOf(
    BottomDestination(Routes.DASHBOARD, "Today", Icons.Default.Home),
    BottomDestination(Routes.FOOD_LOG, "Food", Icons.AutoMirrored.Filled.List),
    BottomDestination(Routes.WEIGHT, "Weight", Icons.Default.Info)
)

class MainActivity : ComponentActivity() {
    // Feature E (completeness pass, spec §6): the action a notification tap should deep-link
    // to, e.g. "OPEN_WEIGH_IN" (see ReminderIntents). A Compose-observable class property
    // (rather than a local `remember`) so onNewIntent (below) can update it and have the
    // running composition react, since MainActivity itself is a single long-lived instance
    // (launchMode="singleTask") rather than being recreated per notification tap.
    private var pendingDeepLinkAction by mutableStateOf<String?>(null)

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as StrideApp
        pendingDeepLinkAction = intent?.getStringExtra(ReminderIntents.EXTRA_OPEN_ACTION)
        lifecycleScope.launch {
            try {
                app.container.geminiFoodRepository.retryPendingDrafts()
            } catch (e: Exception) {
                // Draft retry is opportunistic; a DB or network failure must not take startup down.
                Log.e("MainActivity", "Failed to retry pending Gemini drafts", e)
            }
        }
        lifecycleScope.launch {
            try {
                // Feature E (completeness pass, spec §6): defensive reschedule on every cold
                // start, same rationale as Health Connect sync's own schedule-on-launch below --
                // BootCompletedReceiver covers a real reboot, but this also recovers from any
                // other reason alarms might have been cleared (e.g. the OS's own "unused app"
                // cleanup) without waiting for the next reboot. Cheap and idempotent: scheduling
                // the same reminder id twice just replaces the pending alarm.
                val reminders = app.container.reminderRepository.getAll()
                if (reminders.isNotEmpty()) {
                    ReminderScheduler(applicationContext).rescheduleAll(reminders)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to reschedule reminders on launch", e)
            }
        }
        setContent {
            StrideTheme {
                var startDestination by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(Unit) {
                    startDestination = try {
                        val profile = app.container.userProfileRepository.getProfile()
                        if (profile == null) Routes.ONBOARDING else Routes.DASHBOARD
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Failed to check existing profile", e)
                        Routes.ONBOARDING
                    }

                    try {
                        if (HealthConnectManager.availability(this@MainActivity) == HealthConnectClient.SDK_AVAILABLE &&
                            HealthConnectManager.hasAllPermissions(this@MainActivity)
                        ) {
                            HealthConnectSyncWorker.schedulePeriodic(applicationContext)
                            HealthConnectSyncWorker.triggerOneOff(applicationContext)
                        }
                    } catch (e: Exception) {
                        // A Health Connect provider hiccup shouldn't block the user from the app;
                        // sync just doesn't get scheduled this launch.
                        Log.e("MainActivity", "Failed to schedule Health Connect sync", e)
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    val start = startDestination
                    if (start == null) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else {
                        val navController = rememberNavController()
                        // Feature E (completeness pass, spec §6): handles a notification's
                        // "tap the body" deep link (weigh-in screen, or Food Log for meal/snack/
                        // camera -- see ReminderIntents.ACTION_OPEN_FOOD_LOG's doc for why Camera
                        // lands on Food Log rather than auto-launching the camera). Keyed on
                        // pendingDeepLinkAction so a later onNewIntent (app already running) also
                        // re-triggers this navigation.
                        LaunchedEffect(pendingDeepLinkAction) {
                            val action = pendingDeepLinkAction ?: return@LaunchedEffect
                            val route = when (action) {
                                ReminderIntents.ACTION_OPEN_WEIGH_IN -> Routes.WEIGHT
                                ReminderIntents.ACTION_OPEN_FOOD_LOG -> Routes.FOOD_LOG
                                else -> null
                            }
                            if (route != null) {
                                navController.navigate(route) { launchSingleTop = true }
                            }
                            pendingDeepLinkAction = null
                        }
                        val backStackEntry by navController.currentBackStackEntryAsState()
                        val currentRoute = backStackEntry?.destination?.route
                        val showBottomBar = currentRoute != null && currentRoute != Routes.ONBOARDING

                        Scaffold(
                            topBar = {
                                if (showBottomBar) {
                                    TopAppBar(
                                        title = { Text("Stride") },
                                        actions = {
                                            IconButton(onClick = {
                                                navController.navigate(Routes.SETTINGS) {
                                                    launchSingleTop = true
                                                }
                                            }) {
                                                Icon(Icons.Default.Settings, contentDescription = "Settings")
                                            }
                                        }
                                    )
                                }
                            },
                            bottomBar = {
                                if (showBottomBar) {
                                    NavigationBar {
                                        BOTTOM_DESTINATIONS.forEach { destination ->
                                            val selected = backStackEntry?.destination?.hierarchy
                                                ?.any { it.route == destination.route } == true
                                            NavigationBarItem(
                                                selected = selected,
                                                onClick = {
                                                    navController.navigate(destination.route) {
                                                        popUpTo(Routes.DASHBOARD) { saveState = true }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                },
                                                icon = {
                                                    // Spec §9: active tab gets the checkered-flag glyph instead of a
                                                    // tinted stock icon; inactive tabs keep the stock Material icon
                                                    // outline, recolored muted rather than the default tint.
                                                    if (selected) {
                                                        NavFlagIcon(contentDescription = destination.label)
                                                    } else {
                                                        Icon(destination.icon, contentDescription = destination.label, tint = StrideOnSurfaceMuted)
                                                    }
                                                },
                                                label = { Text(destination.label) }
                                            )
                                        }
                                    }
                                }
                            }
                        ) { innerPadding ->
                            StrideNavHost(
                                navController = navController,
                                startDestination = start,
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                }
            }
        }
    }

    // Feature E (completeness pass, spec §6): launchMode="singleTask" means a notification tap
    // while the app is already running delivers here instead of a fresh onCreate -- without
    // this override, that tap's deep-link extra would be silently dropped.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLinkAction = intent.getStringExtra(ReminderIntents.EXTRA_OPEN_ACTION)
    }
}

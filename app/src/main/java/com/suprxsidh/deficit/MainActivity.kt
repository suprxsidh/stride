package com.suprxsidh.deficit

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.suprxsidh.deficit.ui.nav.DeficitNavHost
import com.suprxsidh.deficit.ui.nav.Routes
import com.suprxsidh.deficit.ui.theme.DeficitTheme

private data class BottomDestination(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val BOTTOM_DESTINATIONS = listOf(
    BottomDestination(Routes.DASHBOARD, "Today", Icons.Default.Home),
    BottomDestination(Routes.FOOD_LOG, "Food", Icons.AutoMirrored.Filled.List),
    BottomDestination(Routes.WEIGHT, "Weight", Icons.Default.Info)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as DeficitApp
        setContent {
            DeficitTheme {
                var startDestination by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(Unit) {
                    startDestination = try {
                        val profile = app.container.userProfileRepository.getProfile()
                        if (profile == null) Routes.ONBOARDING else Routes.DASHBOARD
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Failed to check existing profile", e)
                        Routes.ONBOARDING
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    val start = startDestination
                    if (start == null) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else {
                        val navController = rememberNavController()
                        val backStackEntry by navController.currentBackStackEntryAsState()
                        val currentRoute = backStackEntry?.destination?.route
                        val showBottomBar = currentRoute != null && currentRoute != Routes.ONBOARDING

                        Scaffold(
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
                                                icon = { Icon(destination.icon, contentDescription = destination.label) },
                                                label = { Text(destination.label) }
                                            )
                                        }
                                    }
                                }
                            }
                        ) { innerPadding ->
                            DeficitNavHost(
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
}

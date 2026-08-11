package com.suprxsidh.deficit.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.suprxsidh.deficit.ui.dashboard.DashboardScreen
import com.suprxsidh.deficit.ui.food.FoodLogScreen
import com.suprxsidh.deficit.ui.health.RunDetailScreen
import com.suprxsidh.deficit.ui.onboarding.OnboardingScreen
import com.suprxsidh.deficit.ui.settings.SettingsScreen
import com.suprxsidh.deficit.ui.weight.WeightScreen

@Composable
fun DeficitNavHost(navController: NavHostController, startDestination: String, modifier: Modifier = Modifier) {
    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onComplete = {
                navController.navigate(Routes.DASHBOARD) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                }
            })
        }
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onQuickAdd = { navController.navigate(Routes.FOOD_LOG) },
                onViewRunHistory = { navController.navigate(Routes.RUN_DETAIL) }
            )
        }
        composable(Routes.FOOD_LOG) { FoodLogScreen() }
        composable(Routes.WEIGHT) { WeightScreen() }
        composable(Routes.RUN_DETAIL) { RunDetailScreen() }
        composable(Routes.SETTINGS) { SettingsScreen() }
    }
}

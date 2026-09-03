package com.suprxsidh.stride.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.suprxsidh.stride.ui.dashboard.DashboardScreen
import com.suprxsidh.stride.ui.food.FoodLogScreen
import com.suprxsidh.stride.ui.onboarding.OnboardingScreen
import com.suprxsidh.stride.ui.settings.SettingsScreen
import com.suprxsidh.stride.ui.weight.WeightScreen

@Composable
fun StrideNavHost(navController: NavHostController, startDestination: String, modifier: Modifier = Modifier) {
    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onComplete = {
                navController.navigate(Routes.DASHBOARD) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                }
            })
        }
        composable(Routes.DASHBOARD) {
            DashboardScreen(onQuickAdd = { navController.navigate(Routes.FOOD_LOG) })
        }
        composable(Routes.FOOD_LOG) { FoodLogScreen() }
        composable(Routes.WEIGHT) { WeightScreen() }
        composable(Routes.SETTINGS) { SettingsScreen() }
    }
}

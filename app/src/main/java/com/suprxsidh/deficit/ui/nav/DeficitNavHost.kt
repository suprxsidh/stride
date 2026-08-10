package com.suprxsidh.deficit.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.suprxsidh.deficit.ui.dashboard.DashboardScreen
import com.suprxsidh.deficit.ui.food.FoodLogScreen
import com.suprxsidh.deficit.ui.onboarding.OnboardingScreen
import com.suprxsidh.deficit.ui.weight.WeightScreen

@Composable
fun DeficitNavHost(navController: NavHostController, startDestination: String) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.ONBOARDING) { OnboardingScreen() }
        composable(Routes.DASHBOARD) { DashboardScreen() }
        composable(Routes.FOOD_LOG) { FoodLogScreen() }
        composable(Routes.WEIGHT) { WeightScreen() }
    }
}

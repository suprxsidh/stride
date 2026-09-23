package com.suprxsidh.stride.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.suprxsidh.stride.ui.dashboard.DashboardScreen
import com.suprxsidh.stride.ui.food.FoodLogScreen
import com.suprxsidh.stride.ui.onboarding.OnboardingScreen
import com.suprxsidh.stride.ui.settings.SettingsScreen
import com.suprxsidh.stride.ui.theme.MotionTokens
import com.suprxsidh.stride.ui.weight.WeightScreen

// Nav-transition fix (spec §7): bare Navigation-Compose default crossfade replaced with explicit
// forward/back semantics -- push slides the new screen in from the end (right) with a fade, pop
// slides the previous screen back in from the start (left) with a fade, matching standard Android
// forward/back motion. Defined once here and passed as NavHost-level defaults so every route gets
// the same feel without repeating the animation spec per `composable()` call.
private fun AnimatedContentTransitionScope<NavBackStackEntry>.pushEnter(): EnterTransition =
    slideIntoContainer(
        AnimatedContentTransitionScope.SlideDirection.Start,
        animationSpec = tween(MotionTokens.NAV_TRANSITION_DURATION_MS)
    ) + fadeIn(animationSpec = tween(MotionTokens.NAV_TRANSITION_DURATION_MS))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.pushExit(): ExitTransition =
    slideOutOfContainer(
        AnimatedContentTransitionScope.SlideDirection.Start,
        animationSpec = tween(MotionTokens.NAV_TRANSITION_DURATION_MS)
    ) + fadeOut(animationSpec = tween(MotionTokens.NAV_TRANSITION_DURATION_MS))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnter(): EnterTransition =
    slideIntoContainer(
        AnimatedContentTransitionScope.SlideDirection.End,
        animationSpec = tween(MotionTokens.NAV_TRANSITION_DURATION_MS)
    ) + fadeIn(animationSpec = tween(MotionTokens.NAV_TRANSITION_DURATION_MS))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit(): ExitTransition =
    slideOutOfContainer(
        AnimatedContentTransitionScope.SlideDirection.End,
        animationSpec = tween(MotionTokens.NAV_TRANSITION_DURATION_MS)
    ) + fadeOut(animationSpec = tween(MotionTokens.NAV_TRANSITION_DURATION_MS))

@Composable
fun StrideNavHost(navController: NavHostController, startDestination: String, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = { pushEnter() },
        exitTransition = { pushExit() },
        popEnterTransition = { popEnter() },
        popExitTransition = { popExit() },
    ) {
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

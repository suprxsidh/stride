package com.suprxsidh.deficit

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.suprxsidh.deficit.ui.nav.DeficitNavHost
import com.suprxsidh.deficit.ui.nav.Routes
import com.suprxsidh.deficit.ui.theme.DeficitTheme

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
                        DeficitNavHost(navController = navController, startDestination = start)
                    }
                }
            }
        }
    }
}

package com.suprxsidh.deficit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
                    val profile = app.container.userProfileRepository.getProfile()
                    startDestination = if (profile == null) Routes.ONBOARDING else Routes.DASHBOARD
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    startDestination?.let { start ->
                        val navController = rememberNavController()
                        DeficitNavHost(navController = navController, startDestination = start)
                    }
                }
            }
        }
    }
}

package com.suprxsidh.deficit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import com.suprxsidh.deficit.ui.nav.DeficitNavHost
import com.suprxsidh.deficit.ui.nav.Routes
import com.suprxsidh.deficit.ui.theme.DeficitTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DeficitTheme {
                val navController = rememberNavController()
                DeficitNavHost(navController = navController, startDestination = Routes.ONBOARDING)
            }
        }
    }
}

package com.tresorsdeparis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.tresorsdeparis.data.FavoritesStore
import com.tresorsdeparis.data.WordPressApi
import com.tresorsdeparis.ui.LocalFavorites
import com.tresorsdeparis.ui.TresorsApp
import com.tresorsdeparis.ui.theme.TresorsDeParisTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        WordPressApi.init(this)
        val favorites = FavoritesStore(this)
        // `--ei startTab N` opens another tab (used for emulator screenshots)
        val startTab = intent.getIntExtra("startTab", 0)

        setContent {
            TresorsDeParisTheme {
                CompositionLocalProvider(LocalFavorites provides favorites) {
                    TresorsApp(startTab)
                }
            }
        }
    }
}

package com.booxbook

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.rememberNavController
import com.booxbook.core.engine.epub.RestorableReadiumFragmentFactory
import com.booxbook.core.ui.theme.BooxBookTheme
import com.booxbook.navigation.BooxBookNavHost
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The FragmentFactory must be installed *before* super.onCreate(), because
        // FragmentManager restores its fragments during activity creation and
        // EpubNavigatorFragment has no no-arg constructor. Field injection is not available
        // yet at this point (Hilt injects on context-available), hence the entry point.
        val readerFragmentFactory = EntryPointAccessors
            .fromApplication(applicationContext, ReaderFragmentFactoryEntryPoint::class.java)
            .restorableReadiumFragmentFactory()
        supportFragmentManager.fragmentFactory = readerFragmentFactory

        // If the process was restarted, the open publication is gone and a saved navigator
        // fragment can no longer be recreated: drop the stale state instead of crashing.
        val restoredState = if (readerFragmentFactory.canRestoreNavigator) savedInstanceState else null

        installSplashScreen()
        super.onCreate(restoredState)
        WebView.setWebContentsDebuggingEnabled(true)
        enableEdgeToEdge()

        setContent {
            BooxBookTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val navController = rememberNavController()
                    BooxBookNavHost(navController = navController)
                }
            }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReaderFragmentFactoryEntryPoint {
    fun restorableReadiumFragmentFactory(): RestorableReadiumFragmentFactory
}

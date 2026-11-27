package com.example

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.notification.NewsCatalystNotifier
import com.example.data.notification.TradeExecutionNotifier
import com.example.data.notification.VixAlertNotifier
import com.example.domain.model.MainTab
import com.example.domain.model.ThemeMode
import com.example.ui.MarketScanApp
import com.example.ui.MarketScanViewModel
import com.example.ui.theme.MarketScanTheme

class MainActivity : ComponentActivity() {
    private var requestedTab by mutableStateOf<MainTab?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleNavigationIntent(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            val marketViewModel: MarketScanViewModel = viewModel()
            val state by marketViewModel.uiState.collectAsStateWithLifecycle()
            val isDark = state.config.themeMode == ThemeMode.DARK

            LaunchedEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (isDark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                )
            }

            MarketScanTheme(darkTheme = isDark) {
                MarketScanApp(
                    requestedTab = requestedTab,
                    onConsumedRequestedTab = { requestedTab = null },
                    viewModel = marketViewModel
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavigationIntent(intent)
    }

    private fun handleNavigationIntent(intent: Intent?) {
        when (intent?.getStringExtra(TradeExecutionNotifier.EXTRA_TARGET_TAB)) {
            TradeExecutionNotifier.TARGET_TAB_TRADE -> requestedTab = MainTab.TRADE
            NewsCatalystNotifier.TARGET_TAB_NEWS -> requestedTab = MainTab.NEWS
            VixAlertNotifier.TARGET_TAB_SIGNALS -> requestedTab = MainTab.SIGNALS
        }
    }
}

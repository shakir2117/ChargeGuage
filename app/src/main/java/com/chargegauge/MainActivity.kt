package com.chargegauge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chargegauge.data.AppSettings
import com.chargegauge.ui.ChargeGaugeRoot
import com.chargegauge.ui.theme.ChargeGaugeTheme

class MainActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()
        (application as ChargeGaugeApp).uiVisible = true
    }

    override fun onStop() {
        (application as ChargeGaugeApp).uiVisible = false
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by (application as ChargeGaugeApp).container.settings.settings
                .collectAsStateWithLifecycle(AppSettings())
            ChargeGaugeTheme(themeId = settings.themeId, amoled = settings.amoled) {
                ChargeGaugeRoot()
            }
        }
    }
}

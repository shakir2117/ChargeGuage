package com.chargegauge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.chargegauge.ui.ChargeGaugeRoot
import com.chargegauge.ui.theme.ChargeGaugeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChargeGaugeTheme {
                ChargeGaugeRoot()
            }
        }
    }
}

package com.tuozhu.consumablestatistics

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.tuozhu.consumablestatistics.ui.ConsumableApp
import com.tuozhu.consumablestatistics.ui.ConsumableViewModel
import com.tuozhu.consumablestatistics.ui.ConsumableViewModelFactory
import com.tuozhu.consumablestatistics.ui.theme.TuoZhuConsumableStatisticsTheme

class MainActivity : ComponentActivity() {
    private val viewModel: ConsumableViewModel by viewModels {
        ConsumableViewModelFactory(
            repository = (application as ConsumableApplication).repository,
            syncSettingsStore = (application as ConsumableApplication).syncSettingsStore,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TuoZhuConsumableStatisticsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ConsumableApp(viewModel = viewModel)
                }
            }
        }
    }
}

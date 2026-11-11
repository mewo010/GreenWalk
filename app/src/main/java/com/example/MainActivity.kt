package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.ui.GreenWalkHomeScreen
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.GreenWalkTheme
import com.example.viewmodel.GreenWalkViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: GreenWalkViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GreenWalkTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    GreenWalkHomeScreen(viewModel = viewModel)
                }
            }
        }
    }
}

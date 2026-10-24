package com.locudroids.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.locudroids.app.ui.LocudroidsApp
import com.locudroids.app.ui.LocudroidsTheme
import com.locudroids.app.ui.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LocudroidsTheme {
                LocudroidsApp(viewModel)
            }
        }
    }
}

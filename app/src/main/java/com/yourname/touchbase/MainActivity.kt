package com.yourname.touchbase

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.yourname.touchbase.ui.TouchBaseNavHost
import com.yourname.touchbase.ui.theme.TouchBaseTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TouchBaseTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TouchBaseNavHost()
                }
            }
        }
    }
}

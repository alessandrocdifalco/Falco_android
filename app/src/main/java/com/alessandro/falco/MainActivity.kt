package com.alessandro.falco

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.alessandro.falco.ui.FalcoApp
import com.alessandro.falco.ui.theme.FalcoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FalcoTheme { FalcoApp() } }
    }
}

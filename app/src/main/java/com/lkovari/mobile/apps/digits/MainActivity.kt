package com.lkovari.mobile.apps.digits

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.lkovari.mobile.apps.digits.ui.game.DigitsGameScreen
import com.lkovari.mobile.apps.digits.ui.theme.NumbersBlueWash
import com.lkovari.mobile.apps.digits.ui.theme.NumbersTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NumbersTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = NumbersBlueWash
                ) { innerPadding ->
                    DigitsGameScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

package com.example.loyaltyapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import com.example.loyaltyapp.ui.navigation.LoyaltyNavHost
import com.example.loyaltyapp.ui.theme.LoyaltyAppTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LoyaltyAppTheme {
                LoyaltyNavHost()
            }
        }
    }
}

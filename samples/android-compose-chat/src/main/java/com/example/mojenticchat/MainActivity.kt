package com.example.mojenticchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels {
        viewModelFactory {
            initializer {
                // BuildConfig.OPENAI_API_KEY is wired in build.gradle.kts — see README.
                // Falling back to the env var keeps local development simple.
                val apiKey = BuildConfig.OPENAI_API_KEY.ifBlank {
                    System.getenv("OPENAI_API_KEY")
                        ?: error("Set OPENAI_API_KEY in BuildConfig or the env")
                }
                ChatViewModel(apiKey = apiKey)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface { ChatScreen(viewModel = viewModel) }
            }
        }
    }
}

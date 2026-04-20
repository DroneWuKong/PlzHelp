package com.orqa.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.orqa.chat.ui.*
import com.orqa.chat.ui.theme.OrqaTheme

class MainActivity : ComponentActivity() {

    private val vm: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OrqaTheme {
                OrqaApp(vm)
            }
        }
    }
}

@Composable
fun OrqaApp(vm: ChatViewModel) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = "chat") {
        composable("chat") {
            ChatScreen(
                vm                 = vm,
                onNavigateSettings = { nav.navigate("settings") },
                onNavigateSync     = { nav.navigate("sync") }
            )
        }
        composable("settings") {
            SettingsScreen(vm = vm, onBack = { nav.popBackStack() })
        }
        composable("sync") {
            SyncScreen(vm = vm, onBack = { nav.popBackStack() })
        }
    }
}

package com.dgpack.chamcong.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dgpack.chamcong.ui.camera.CameraScreen
import com.dgpack.chamcong.ui.enroll.EnrollScreen
import com.dgpack.chamcong.ui.queue.QueueScreen
import com.dgpack.chamcong.ui.settings.SettingsScreen

private object Routes {
    const val CAMERA = "camera"
    const val QUEUE = "queue"
    const val ENROLL = "enroll"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.CAMERA) {
        composable(Routes.CAMERA) {
            CameraScreen(onOpenAdmin = { navController.navigate(Routes.QUEUE) })
        }
        composable(Routes.QUEUE) {
            QueueScreen(
                onBack = { navController.popBackStack() },
                onOpenEnroll = { navController.navigate(Routes.ENROLL) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.ENROLL) {
            EnrollScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}

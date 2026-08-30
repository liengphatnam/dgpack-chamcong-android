package com.dgpack.chamcong.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dgpack.chamcong.ChamCongApplication
import com.dgpack.chamcong.ui.camera.CameraScreen
import com.dgpack.chamcong.ui.enroll.EnrollScreen
import com.dgpack.chamcong.ui.pin.PinEntryScreen
import com.dgpack.chamcong.ui.queue.QueueScreen
import com.dgpack.chamcong.ui.settings.SettingsScreen

private object Routes {
    const val CAMERA = "camera"
    const val ADMIN_GATE = "admin_gate"
    const val QUEUE = "queue"
    const val ENROLL = "enroll"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.CAMERA) {
        composable(Routes.CAMERA) {
            CameraScreen(onOpenAdmin = { navController.navigate(Routes.ADMIN_GATE) })
        }

        // Phase 2: khoá màn hình quản trị bằng PIN (mục [2]/[12]) — nếu chưa đặt PIN ở
        // Cài đặt, vào thẳng Hàng đợi như Phase 1, không đổi hành vi mặc định.
        composable(Routes.ADMIN_GATE) {
            val context = LocalContext.current
            val app = context.applicationContext as ChamCongApplication
            val pin = app.settingsRepository.current().adminPin

            fun enterAdmin() {
                navController.navigate(Routes.QUEUE) {
                    popUpTo(Routes.ADMIN_GATE) { inclusive = true }
                }
            }

            if (pin.isBlank()) {
                LaunchedEffect(Unit) { enterAdmin() }
            } else {
                PinEntryScreen(
                    correctPin = pin,
                    onSuccess = { enterAdmin() },
                    onCancel = { navController.popBackStack() }
                )
            }
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

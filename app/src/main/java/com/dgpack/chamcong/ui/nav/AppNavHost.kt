package com.dgpack.chamcong.ui.nav

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.dgpack.chamcong.R
import com.dgpack.chamcong.ui.pin.AdminLevel
import com.dgpack.chamcong.ui.pin.AdminPinPolicy
import com.dgpack.chamcong.ui.pin.AdminSession
import com.dgpack.chamcong.util.TimeUtils
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dgpack.chamcong.ChamCongApplication
import com.dgpack.chamcong.ui.camera.CameraScreen
import com.dgpack.chamcong.ui.employees.EmployeesScreen
import com.dgpack.chamcong.ui.enroll.EnrollScreen
import com.dgpack.chamcong.ui.luckydraw.LuckyDrawScreen
import com.dgpack.chamcong.ui.pin.PinEntryScreen
import com.dgpack.chamcong.ui.queue.QueueScreen
import com.dgpack.chamcong.ui.settings.SettingsScreen

private object Routes {
    const val CAMERA = "camera"
    const val ADMIN_GATE = "admin_gate"
    const val QUEUE = "queue"
    const val ENROLL = "enroll"
    const val ENROLL_ARG_CODE = "code"
    const val ENROLL_ARG_NAME = "name"
    const val ENROLL_PATTERN = "$ENROLL?$ENROLL_ARG_CODE={$ENROLL_ARG_CODE}&$ENROLL_ARG_NAME={$ENROLL_ARG_NAME}"
    const val SETTINGS = "settings"
    const val EMPLOYEES = "employees"
    const val LUCKY_DRAW = "lucky_draw"
    const val ELEVATE = "admin_elevate"

    /** Mở Enroll với mã + tên điền sẵn (từ màn Nhân viên ERP). Tên tiếng Việt cần encode. */
    fun enrollWith(code: String, name: String) =
        "$ENROLL?$ENROLL_ARG_CODE=${Uri.encode(code)}&$ENROLL_ARG_NAME=${Uri.encode(name)}"
}

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.CAMERA) {
        composable(Routes.CAMERA) {
            CameraScreen(onOpenAdmin = { navController.navigate(Routes.ADMIN_GATE) })
        }

        // Phase 2: khoá màn hình quản trị bằng PIN (mục [2]/[12]) — nếu chưa đặt PIN ở
        // Cài đặt, vào thẳng Hàng đợi như Phase 1, không đổi hành vi mặc định.
        // Hai cấp mật mã (ui/pin/AdminSession.kt): chưa đặt mã cấp 1 thì vào thẳng ở cấp 1
        // (đồng bộ + đăng ký khuôn mặt); đã đặt thì nhập mã cấp 1 hoặc mã cấp 2 theo ngày
        // (= ngày × tháng × 2 × 3). Ở cấp 1 vẫn nâng lên cấp 2 được bằng nút ở màn Hàng đợi.
        composable(Routes.ADMIN_GATE) {
            val context = LocalContext.current
            val app = context.applicationContext as ChamCongApplication
            val pin = app.settingsRepository.current().adminPin
            remember { AdminSession.clear(); true }

            fun enterAdmin(level: AdminLevel) {
                AdminSession.set(level)
                // "Khi đăng nhập" khu quản trị: kéo danh sách NV từ ERP + đồng bộ embedding
                // (chạy nền, bỏ qua nếu chưa cấu hình API key / không có mạng — xem
                // EmployeeSyncCoordinator; kết quả hiện ở màn Nhân viên ERP).
                app.employeeSyncCoordinator.requestSync()
                navController.navigate(Routes.QUEUE) {
                    popUpTo(Routes.ADMIN_GATE) { inclusive = true }
                }
            }

            if (pin.isBlank()) {
                LaunchedEffect(Unit) { enterAdmin(AdminLevel.LEVEL_1) }
            } else {
                PinEntryScreen(
                    title = stringResource(R.string.nhap_ma_pin),
                    validate = { input -> AdminPinPolicy.resolve(input, pin, TimeUtils.vnToday()) },
                    onSuccess = { level -> enterAdmin(level) },
                    onCancel = { navController.popBackStack() }
                )
            }
        }

        // Nâng từ cấp 1 lên cấp 2 ngay trong phiên (chỉ nhận mã theo ngày).
        composable(Routes.ELEVATE) {
            PinEntryScreen(
                title = stringResource(R.string.nhap_ma_pin_cap2),
                validate = { input ->
                    if (input == AdminPinPolicy.level2Code(TimeUtils.vnToday())) AdminLevel.LEVEL_2 else null
                },
                onSuccess = { AdminSession.set(AdminLevel.LEVEL_2); navController.popBackStack() },
                onCancel = { navController.popBackStack() }
            )
        }

        composable(Routes.QUEUE) {
            val level by AdminSession.level.collectAsState()
            QueueScreen(
                level = level,
                onElevate = { navController.navigate(Routes.ELEVATE) },
                onBack = { navController.popBackStack() },
                onOpenEnroll = { navController.navigate(Routes.ENROLL) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenEmployees = { navController.navigate(Routes.EMPLOYEES) },
                onOpenLuckyDraw = { navController.navigate(Routes.LUCKY_DRAW) }
            )
        }
        composable(Routes.LUCKY_DRAW) {
            LuckyDrawScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.EMPLOYEES) {
            EmployeesScreen(
                onBack = { navController.popBackStack() },
                onOpenEnroll = { code, name -> navController.navigate(Routes.enrollWith(code, name)) }
            )
        }
        composable(
            route = Routes.ENROLL_PATTERN,
            arguments = listOf(
                navArgument(Routes.ENROLL_ARG_CODE) { type = NavType.StringType; defaultValue = "" },
                navArgument(Routes.ENROLL_ARG_NAME) { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            EnrollScreen(
                onBack = { navController.popBackStack() },
                // Mở từ danh sách Nhân viên ERP: lưu xong tự quay về danh sách để chọn người kế tiếp.
                onSaved = { navController.popBackStack() },
                prefillCode = backStackEntry.arguments?.getString(Routes.ENROLL_ARG_CODE).orEmpty(),
                prefillName = backStackEntry.arguments?.getString(Routes.ENROLL_ARG_NAME).orEmpty()
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}

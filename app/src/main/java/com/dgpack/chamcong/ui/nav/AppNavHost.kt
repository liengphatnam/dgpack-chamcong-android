package com.dgpack.chamcong.ui.nav

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dgpack.chamcong.ChamCongApplication
import com.dgpack.chamcong.R
import com.dgpack.chamcong.data.prefs.AttendanceMode
import com.dgpack.chamcong.ui.camera.CameraScreen
import com.dgpack.chamcong.ui.card.AssignCardScreen
import com.dgpack.chamcong.ui.card.CardScreen
import com.dgpack.chamcong.ui.employees.EmployeePurpose
import com.dgpack.chamcong.ui.employees.EmployeesScreen
import com.dgpack.chamcong.ui.enroll.EnrollScreen
import com.dgpack.chamcong.ui.forgot.ForgotCardLogScreen
import com.dgpack.chamcong.ui.luckydraw.LuckyDrawScreen
import com.dgpack.chamcong.ui.pin.AdminLevel
import com.dgpack.chamcong.ui.pin.AdminPinPolicy
import com.dgpack.chamcong.ui.pin.AdminSession
import com.dgpack.chamcong.ui.pin.PinEntryScreen
import com.dgpack.chamcong.ui.queue.QueueScreen
import com.dgpack.chamcong.ui.settings.SettingsScreen
import com.dgpack.chamcong.util.TimeUtils

private object Routes {
    /** Màn chính: thẻ từ hoặc khuôn mặt tuỳ Cài đặt (AttendanceMode). */
    const val HOME = "home"
    const val ADMIN_GATE = "admin_gate"
    const val ELEVATE = "admin_elevate"
    const val QUEUE = "queue"
    const val SETTINGS = "settings"
    const val LUCKY_DRAW = "lucky_draw"
    const val FORGOT_LOG = "forgot_log"

    const val ARG_CODE = "code"
    const val ARG_NAME = "name"
    const val ARG_PURPOSE = "purpose"

    const val ENROLL = "enroll"
    const val ENROLL_PATTERN = "$ENROLL?$ARG_CODE={$ARG_CODE}&$ARG_NAME={$ARG_NAME}"
    fun enrollWith(code: String, name: String) =
        "$ENROLL?$ARG_CODE=${Uri.encode(code)}&$ARG_NAME=${Uri.encode(name)}"

    const val EMPLOYEES = "employees"
    const val EMPLOYEES_PATTERN = "$EMPLOYEES/{$ARG_PURPOSE}"
    fun employees(purpose: EmployeePurpose) = "$EMPLOYEES/${purpose.name}"

    const val ASSIGN_CARD = "assign_card"
    const val ASSIGN_CARD_PATTERN = "$ASSIGN_CARD?$ARG_CODE={$ARG_CODE}&$ARG_NAME={$ARG_NAME}"
    fun assignCard(code: String, name: String) =
        "$ASSIGN_CARD?$ARG_CODE=${Uri.encode(code)}&$ARG_NAME=${Uri.encode(name)}"
}

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            val app = LocalContext.current.applicationContext as ChamCongApplication
            val settings by app.settingsRepository.settings.collectAsState()
            if (settings.attendanceMode == AttendanceMode.FACE) {
                CameraScreen(onOpenAdmin = { navController.navigate(Routes.ADMIN_GATE) })
            } else {
                CardScreen(onOpenAdmin = { navController.navigate(Routes.ADMIN_GATE) })
            }
        }

        // Hai cấp mật mã (ui/pin/AdminSession.kt): chưa đặt mã cấp 1 thì vào thẳng ở cấp 1
        // (đồng bộ + đăng ký); đã đặt thì nhập mã cấp 1 hoặc mã cấp 2 theo ngày
        // (= ngày × tháng × 2 × 3). Ở cấp 1 vẫn nâng lên cấp 2 được bằng nút ở màn Hàng đợi.
        composable(Routes.ADMIN_GATE) {
            val context = LocalContext.current
            val app = context.applicationContext as ChamCongApplication
            val pin = app.settingsRepository.current().adminPin
            remember { AdminSession.clear(); true }

            fun enterAdmin(level: AdminLevel) {
                AdminSession.set(level)
                // "Khi đăng nhập" khu quản trị: kéo danh sách NV từ ERP + đồng bộ embedding/thẻ
                // (chạy nền, bỏ qua nếu chưa cấu hình API key / không có mạng).
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
                onOpenEmployees = { navController.navigate(Routes.employees(EmployeePurpose.ENROLL_FACE)) },
                onOpenAssignCard = { navController.navigate(Routes.employees(EmployeePurpose.ASSIGN_CARD)) },
                onOpenLuckyDraw = { navController.navigate(Routes.LUCKY_DRAW) },
                onOpenForgotLog = { navController.navigate(Routes.FORGOT_LOG) }
            )
        }
        composable(Routes.LUCKY_DRAW) {
            LuckyDrawScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.FORGOT_LOG) {
            ForgotCardLogScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.EMPLOYEES_PATTERN,
            arguments = listOf(navArgument(Routes.ARG_PURPOSE) { type = NavType.StringType })
        ) { backStackEntry ->
            val purpose = runCatching {
                EmployeePurpose.valueOf(backStackEntry.arguments?.getString(Routes.ARG_PURPOSE).orEmpty())
            }.getOrDefault(EmployeePurpose.ENROLL_FACE)
            EmployeesScreen(
                purpose = purpose,
                onBack = { navController.popBackStack() },
                onSelect = { code, name ->
                    if (purpose == EmployeePurpose.ASSIGN_CARD) navController.navigate(Routes.assignCard(code, name))
                    else navController.navigate(Routes.enrollWith(code, name))
                }
            )
        }

        composable(
            route = Routes.ASSIGN_CARD_PATTERN,
            arguments = listOf(
                navArgument(Routes.ARG_CODE) { type = NavType.StringType; defaultValue = "" },
                navArgument(Routes.ARG_NAME) { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            AssignCardScreen(
                employeeCode = backStackEntry.arguments?.getString(Routes.ARG_CODE).orEmpty(),
                fullName = backStackEntry.arguments?.getString(Routes.ARG_NAME).orEmpty(),
                onBack = { navController.popBackStack() },
                // Gán xong tự quay về danh sách để chọn người kế tiếp.
                onDone = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.ENROLL_PATTERN,
            arguments = listOf(
                navArgument(Routes.ARG_CODE) { type = NavType.StringType; defaultValue = "" },
                navArgument(Routes.ARG_NAME) { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            EnrollScreen(
                onBack = { navController.popBackStack() },
                // Mở từ danh sách Nhân viên ERP: lưu xong tự quay về danh sách để chọn người kế tiếp.
                onSaved = { navController.popBackStack() },
                prefillCode = backStackEntry.arguments?.getString(Routes.ARG_CODE).orEmpty(),
                prefillName = backStackEntry.arguments?.getString(Routes.ARG_NAME).orEmpty()
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}

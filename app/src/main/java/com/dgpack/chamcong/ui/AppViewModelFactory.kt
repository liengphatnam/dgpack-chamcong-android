package com.dgpack.chamcong.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dgpack.chamcong.ChamCongApplication

/**
 * Không dùng Hilt (quy mô app nhỏ) — helper này thay cho @HiltViewModel, lấy các
 * repository singleton từ ChamCongApplication để khởi tạo ViewModel thủ công.
 */
@Composable
inline fun <reified T : ViewModel> appViewModel(crossinline create: (ChamCongApplication) -> T): T {
    val app = LocalContext.current.applicationContext as ChamCongApplication
    return viewModel(factory = viewModelFactory {
        initializer { create(app) }
    })
}

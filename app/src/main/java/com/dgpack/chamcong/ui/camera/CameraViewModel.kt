package com.dgpack.chamcong.ui.camera

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgpack.chamcong.ChamCongApplication
import com.dgpack.chamcong.face.FaceMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CameraUiState(
    val overlayName: String? = null,
    val pendingCount: Int = 0
)

class CameraViewModel(private val app: ChamCongApplication) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private var hideOverlayJob: Job? = null

    init {
        viewModelScope.launch {
            app.attendanceRepository.observeCounts().collect { counts ->
                _uiState.value = _uiState.value.copy(pendingCount = counts.pending)
            }
        }
    }

    /** Gọi từ FaceAnalyzer (luồng background) mỗi khi có 1 khuôn mặt đã crop sẵn 112x112. */
    fun onFaceDetected(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val embedding = app.faceEmbedder.embed(bitmap)
                val settings = app.settingsRepository.current()
                val match = FaceMatcher.findBestMatch(
                    query = embedding,
                    enrolled = app.employeeRepository.matchCache.value,
                    threshold = settings.similarityThreshold
                )
                if (match != null) {
                    // Ghi sự kiện có debounce (mục [3]) — dù bị debounce chặn hay không,
                    // vẫn hiện overlay tên để công nhân biết máy đã nhận ra mình.
                    app.attendanceRepository.recordEventIfNotDebounced(
                        employeeCode = match.employeeCode,
                        deviceCode = settings.deviceCode,
                        debounceMinutes = settings.debounceMinutes
                    )
                    val name = app.employeeRepository.nameCache.value[match.employeeCode] ?: match.employeeCode
                    showOverlay(name)
                }
                // Không đạt ngưỡng similarity nào -> không làm gì, không báo lỗi (mục [4.1]).
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun showOverlay(name: String) {
        _uiState.value = _uiState.value.copy(overlayName = name)
        hideOverlayJob?.cancel()
        hideOverlayJob = viewModelScope.launch {
            delay(2000)
            _uiState.value = _uiState.value.copy(overlayName = null)
        }
    }
}

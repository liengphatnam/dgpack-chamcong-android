package com.dgpack.chamcong.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LastSyncKind { NONE, OK, UNAUTHORIZED, SERVER_MISCONFIGURED, NETWORK_ERROR, UNEXPECTED }

data class LastSyncInfo(
    val kind: LastSyncKind = LastSyncKind.NONE,
    val atEpochMillis: Long = 0L
)

/** Trạng thái đồng bộ gần nhất, để UI (Queue/Settings) hiển thị cảnh báo mà không cần polling. */
object SyncStatusHolder {
    private val _lastSync = MutableStateFlow(LastSyncInfo())
    val lastSync: StateFlow<LastSyncInfo> = _lastSync.asStateFlow()

    fun update(kind: LastSyncKind) {
        _lastSync.value = LastSyncInfo(kind, System.currentTimeMillis())
    }
}

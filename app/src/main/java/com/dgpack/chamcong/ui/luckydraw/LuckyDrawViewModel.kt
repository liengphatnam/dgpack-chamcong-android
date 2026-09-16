package com.dgpack.chamcong.ui.luckydraw

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgpack.chamcong.ChamCongApplication
import com.dgpack.chamcong.data.db.LuckyDrawWinEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 1 ngày trong sổ trúng thưởng: [date] yyyy-MM-dd (VN), dòng mới nhất trước. */
data class LuckyDrawDay(val date: String, val wins: List<LuckyDrawWinEntity>) {
    val totalCans: Int get() = wins.sumOf { it.cans }
    val unclaimed: Int get() = wins.count { !it.claimed }
}

data class LuckyDrawUiState(
    val days: List<LuckyDrawDay> = emptyList(),
    val totalWins: Int = 0,
    val totalCans: Int = 0,
    val unclaimed: Int = 0
)

class LuckyDrawViewModel(private val app: ChamCongApplication) : ViewModel() {

    val uiState: StateFlow<LuckyDrawUiState> = app.luckyDrawRepository.observeAll()
        .map { all ->
            val days = all.groupBy { it.drawDate }
                .map { (date, wins) -> LuckyDrawDay(date, wins) }
                .sortedByDescending { it.date }
            LuckyDrawUiState(
                days = days,
                totalWins = all.size,
                totalCans = all.sumOf { it.cans },
                unclaimed = all.count { !it.claimed }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LuckyDrawUiState())

    /** Nhân sự đã phát (hoặc bấm nhầm, bỏ đánh dấu). */
    fun setClaimed(win: LuckyDrawWinEntity, claimed: Boolean) {
        viewModelScope.launch { app.luckyDrawRepository.setClaimed(win.localId, claimed) }
    }
}

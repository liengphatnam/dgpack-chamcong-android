package com.dgpack.chamcong.ui.luckydraw

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgpack.chamcong.ChamCongApplication
import com.dgpack.chamcong.data.db.LuckyDrawWinEntity
import com.dgpack.chamcong.util.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

/** Bộ lọc thời gian của sổ trúng thưởng (ngày VN). Tuần tính từ thứ Hai. */
enum class LuckyFilter { TODAY, YESTERDAY, THIS_WEEK, THIS_MONTH, ALL }

/** 1 ngày trong sổ trúng thưởng: [date] yyyy-MM-dd (VN), dòng mới nhất trước. */
data class LuckyDrawDay(val date: String, val wins: List<LuckyDrawWinEntity>) {
    val totalCans: Int get() = wins.sumOf { it.cans }
    val unclaimed: Int get() = wins.count { !it.claimed }
}

data class LuckyDrawUiState(
    val filter: LuckyFilter = LuckyFilter.TODAY,
    val days: List<LuckyDrawDay> = emptyList(),
    val totalWins: Int = 0,
    val totalCans: Int = 0,
    val unclaimed: Int = 0
)

class LuckyDrawViewModel(private val app: ChamCongApplication) : ViewModel() {

    private val filter = MutableStateFlow(LuckyFilter.TODAY)

    val uiState: StateFlow<LuckyDrawUiState> = combine(app.luckyDrawRepository.observeAll(), filter) { all, f ->
        val today = TimeUtils.vnToday()
        val selected = all.filter { inRange(it.drawDate, f, today) }
        val days = selected.groupBy { it.drawDate }
            .map { (date, wins) -> LuckyDrawDay(date, wins) }
            .sortedByDescending { it.date }
        LuckyDrawUiState(
            filter = f,
            days = days,
            totalWins = selected.size,
            totalCans = selected.sumOf { it.cans },
            unclaimed = selected.count { !it.claimed }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LuckyDrawUiState())

    fun setFilter(value: LuckyFilter) = filter.update { value }

    /** Nhân sự đã phát (hoặc bấm nhầm, bỏ đánh dấu). */
    fun setClaimed(win: LuckyDrawWinEntity, claimed: Boolean) {
        viewModelScope.launch { app.luckyDrawRepository.setClaimed(win.localId, claimed) }
    }

    companion object {
        fun inRange(drawDate: String, filter: LuckyFilter, today: LocalDate): Boolean {
            val date = runCatching { LocalDate.parse(drawDate) }.getOrNull() ?: return filter == LuckyFilter.ALL
            return when (filter) {
                LuckyFilter.TODAY -> date == today
                LuckyFilter.YESTERDAY -> date == today.minusDays(1)
                LuckyFilter.THIS_WEEK -> {
                    val monday = today.with(DayOfWeek.MONDAY)
                    !date.isBefore(monday) && !date.isAfter(today)
                }
                LuckyFilter.THIS_MONTH -> date.year == today.year && date.monthValue == today.monthValue
                LuckyFilter.ALL -> true
            }
        }
    }
}

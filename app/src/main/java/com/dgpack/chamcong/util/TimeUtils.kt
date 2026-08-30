package com.dgpack.chamcong.util

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Mục [5.2]: server tính "hôm nay"/tăng ca dựa trên UTC (SYSUTCDATETIME()), KHÔNG phải giờ VN.
 * Mọi thời điểm gửi lên server BẮT BUỘC phải là UTC — tuyệt đối không dùng
 * LocalDateTime.now() (giờ thiết bị, thường là UTC+7) gửi thẳng lên, sẽ lệch 7 giờ.
 */
object TimeUtils {

    // Định dạng an toàn theo mục [5.3]: "2026-08-30T01:00:00" — không có hậu tố Z,
    // nhưng giá trị số đã tự đảm bảo là UTC trước khi format.
    private val API_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    fun nowUtcIso(): String = instantToApiString(Instant.now())

    fun instantToApiString(instant: Instant): String =
        instant.atOffset(ZoneOffset.UTC).format(API_FORMATTER)

    /** Dùng khi đọc lại eventTimeUtc đã lưu trong Room để parse thành Instant. */
    fun apiStringToInstant(value: String): Instant =
        Instant.parse(if (value.endsWith("Z")) value else "${value}Z")

    private val VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh")
    private val VN_DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")

    /**
     * CHỈ dùng để hiển thị cho người xem (mục [7]: UI tiếng Việt) — giá trị lưu trong Room
     * và gửi lên server vẫn luôn là UTC, không đổi. Quy đổi hiển thị riêng để tránh nhầm
     * lẫn "giờ không phải giờ Việt Nam" khi admin xem hàng đợi đồng bộ.
     */
    fun apiStringToVnDisplay(value: String): String =
        apiStringToInstant(value).atZone(VN_ZONE).format(VN_DISPLAY_FORMATTER)
}

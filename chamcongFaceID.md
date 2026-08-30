# PROMPT CHO CLAUDE CODE — APP ANDROID "CHẤM CÔNG NHẬN DIỆN KHUÔN MẶT" (DGPack)

> Cách dùng: mở Claude Code tại thư mục gốc của **repo Android riêng** (KHÔNG phải repo DGP.ERP —
> xem mục [0.3]), rồi dán toàn bộ nội dung bên dưới.

---

## [0] BỐI CẢNH

### [0.1] Công ty & nhu cầu

Công ty TNHH Dona Green Pack (sản xuất bao bì carton sóng) đang dùng hệ thống ERP nội bộ **DGP.ERP**
(.NET 10, Azure Web App `dgperp`, Azure SQL `DGPERP`). ERP đã có sẵn module **"Chấm công nhà máy"**
(schema `hr`) — hiển thị 3 số trên dashboard: đang lên Ca / nghỉ phép / tăng ca, tính từ dữ liệu chấm
công thật. Nhưng ERP **không có phần cứng/camera** — nó chỉ có 1 API chờ sẵn để **nhận** dữ liệu chấm
công từ bên ngoài gửi lên.

Nhiệm vụ của bạn: xây **app Android native** cài trên điện thoại/tablet đặt tại cổng nhà máy, dùng
camera để **nhận diện khuôn mặt công nhân** mỗi khi họ đi ngang qua, ghi nhận cục bộ (SQLite), rồi
định kỳ đồng bộ lên đúng API/schema ERP đã dựng sẵn.

### [0.2] Vì sao offline-first

Nhà máy có thể mất mạng/wifi chập chờn ở khu vực cổng — app phải **hoạt động nhận diện + ghi nhận hoàn
toàn offline** (không cần internet để chấm công), chỉ cần internet khi **đồng bộ** (có thể trễ vài phút
không sao).

### [0.3] Quan hệ với repo ERP (DGP.ERP)

Đây là **app hoàn toàn tách biệt**, build trong **repo GitHub riêng** (không đụng code ERP). Điểm nối
DUY NHẤT giữa 2 hệ thống là 1 API HTTP đã có sẵn — xem chi tiết đầy đủ ở mục [5]. Bạn **không có quyền
và không cần** sửa bất kỳ file nào trong repo DGP.ERP.

### [0.4] Stack bắt buộc

- **Kotlin**, Android native (KHÔNG Flutter/React Native/Xamarin — máy tablet nhà máy cấu hình thấp,
  cần hiệu năng camera + inference tốt nhất, native là lựa chọn an toàn nhất).
- **CameraX** (Jetpack) cho luồng camera.
- **ML Kit Face Detection** (Google, on-device, miễn phí, không cần internet) để phát hiện có khuôn mặt
  trong khung hình + crop.
- **TensorFlow Lite** + 1 model embedding khuôn mặt on-device (đề xuất **MobileFaceNet**, ~5MB, chạy tốt
  trên thiết bị yếu) để **nhận diện danh tính** (ML Kit chỉ *phát hiện* có mặt người, không tự nhận diện
  đó là ai — 2 việc khác nhau, cần cả 2).
- **Room** (SQLite) cho lưu trữ cục bộ.
- **WorkManager** (Jetpack) cho tác vụ đồng bộ định kỳ chạy nền.
- **Jetpack Security (EncryptedSharedPreferences)** cho lưu API key (KHÔNG hardcode).
- Ngôn ngữ UI: **tiếng Việt có dấu**.

---

## [1] BƯỚC BẮT BUỘC TRƯỚC KHI VIẾT CODE

**Không sinh code ngay.** Trước tiên hãy khảo sát và báo cáo lại cho người dùng:

1. Xác nhận đã hiểu đúng API contract ở mục [5] — nếu môi trường của bạn gọi thử endpoint
   `POST /api/v1/attendance/sync-events` mà không có kết nối tới `dgperp.azurewebsites.net` được, báo rõ
   cho người dùng, đừng tự bịa response giả để code tiếp theo cho "có vẻ chạy".
2. Xác nhận phiên bản Android tối thiểu cần hỗ trợ (đề xuất minSdk 26 / Android 8.0 — đủ mới cho CameraX +
   ML Kit, đủ cũ để chạy được trên tablet nhà máy giá rẻ đời cũ). Hỏi lại người dùng nếu tablet thực tế
   cũ hơn.
3. Trình bày kế hoạch triển khai (danh sách file sẽ tạo) và **chờ người dùng duyệt** rồi mới code.

---

## [2] PHẠM VI — CHỈ LÀM PHASE 1

| # | Tính năng | Mô tả |
|---|---|---|
| F1 | **Enroll (đăng ký khuôn mặt)** | Màn hình admin: nhập Mã NV (phải khớp `dm.Employee.EmployeeCode` đã có sẵn trong ERP — xem [2.1]), chụp 3-5 ảnh mặt, sinh embedding, lưu local. |
| F2 | **Chấm công tự động** | Camera chạy liên tục (hoặc theo nút bấm — xem [6.3]), phát hiện mặt → nhận diện → nếu khớp, ghi 1 dòng sự kiện local + hiện tên/ảnh xác nhận 1-2 giây. |
| F3 | **Hàng đợi đồng bộ** | Lưu mọi sự kiện local, đồng bộ định kỳ lên ERP qua API, đánh dấu trạng thái theo phản hồi. |
| F4 | **Màn hình quản trị nhỏ** | Xem danh sách đã enroll, xem hàng đợi đồng bộ (bao nhiêu Pending/Synced/Lỗi), nút "Đồng bộ ngay", cấu hình API key + tên thiết bị (DeviceCode) + URL server. |

**KHÔNG làm trong phase này** (chỉ chừa chỗ mở rộng): nhận diện nhiều mặt cùng lúc trong 1 khung hình,
chống giả mạo ảnh/video (liveness detection), gửi ảnh chụp lên server, đồng bộ 2 chiều (app không cần
BIẾT về nghỉ phép/tăng ca — đó là việc của ERP), đăng nhập admin phân quyền (V1 mặc định ai cầm máy cũng
vào được màn hình Enroll — chấp nhận được vì thiết bị đặt cố định tại nhà máy, có thể khoá bằng PIN đơn
giản nếu người dùng yêu cầu sau).

### [2.1] Ràng buộc quan trọng: Mã NV phải có sẵn trong ERP trước

App **không tự tạo nhân viên**. Nếu Enroll với 1 Mã NV chưa tồn tại trong `dm.Employee` (ERP), việc
enroll vẫn lưu được local (app không có cách nào kiểm tra online lúc enroll nếu offline), nhưng khi
đồng bộ sự kiện của mã đó lên, server sẽ trả về `status: "UnknownEmployee"` cho từng sự kiện — xem [5.4].
UI hàng đợi đồng bộ phải hiển thị rõ các mã bị `UnknownEmployee` để admin biết cần vào ERP tạo nhân viên
trước (trang "Danh mục nhân viên", `/danh-muc-nhan-vien`).

---

## [3] DATA MODEL — SQLite LOCAL (Room)

### Bảng `enrolled_employee`
```
employee_code   TEXT PRIMARY KEY   -- PHẢI khớp dm.Employee.EmployeeCode bên ERP (xem [2.1])
full_name       TEXT               -- chỉ để hiển thị lúc chấm công, KHÔNG gửi lên server
embedding       BLOB NOT NULL      -- vector embedding (FloatArray serialize), model MobileFaceNet
enrolled_at     TEXT NOT NULL      -- ISO-8601 UTC
photo_sample    BLOB NULL          -- 1 ảnh đại diện để admin xem lại khi cần, KHÔNG bắt buộc
```

### Bảng `attendance_event_local`
```
local_id        INTEGER PRIMARY KEY AUTOINCREMENT
employee_code   TEXT NOT NULL
event_time_utc  TEXT NOT NULL      -- ISO-8601 UTC, xem [5.2] về múi giờ — SAI múi giờ sẽ tính nhầm ngày làm việc
device_code     TEXT NOT NULL      -- tên thiết bị, cấu hình ở màn hình Settings
sync_status     TEXT NOT NULL      -- 'Pending' | 'Synced' | 'Duplicate' | 'UnknownEmployee' | 'Failed'
created_at      TEXT NOT NULL
synced_at       TEXT NULL
sync_attempts   INTEGER NOT NULL DEFAULT 0
```

**Append-only tinh thần**: KHÔNG bao giờ UPDATE/DELETE `event_time_utc`/`employee_code` của 1 dòng đã
tạo — chỉ được cập nhật `sync_status`/`synced_at`/`sync_attempts`. Đây là ledger cục bộ, mirror đúng
triết lý `hr.AttendanceEvent` append-only bên ERP (xem [5.1]).

### Debounce chống ghi trùng khi đứng lâu trước camera

Nếu cùng 1 người được nhận diện nhiều lần liên tiếp (đứng chờ, camera quét lặp), **KHÔNG** tạo 1 dòng
`attendance_event_local` mới cho mỗi lần nhận diện — chỉ ghi dòng mới nếu lần nhận diện gần nhất của
đúng `employee_code` đó cách lần trước **≥ 5 phút** (ngưỡng đề xuất, có thể chỉnh ở Settings). Việc này
giảm rác dữ liệu cục bộ VÀ giảm tải batch đồng bộ — server cũng tự chống trùng theo
`(EmployeeID, EventTime)` chính xác tới giây (xem [5.1]) nhưng đó là lớp phòng thủ cuối, không nên dựa
vào nó để xử lý debounce UX.

---

## [4] NHẬN DIỆN KHUÔN MẶT

### [4.1] Luồng kỹ thuật

```
CameraX (khung hình liên tục)
   → ML Kit Face Detection (tìm khung mặt, KHÔNG biết là ai)
   → Crop + align khuôn mặt
   → TFLite MobileFaceNet → vector embedding (128 hoặc 512 chiều)
   → So khớp cosine similarity với toàn bộ enrolled_employee.embedding đã nạp vào RAM lúc khởi động app
   → similarity cao nhất VÀ vượt ngưỡng (đề xuất 0.6, cần tự test hiệu chỉnh với dữ liệu thật) → nhận diện
     thành công → ghi sự kiện (theo debounce ở mục [3])
   → similarity không đạt ngưỡng nào → không ghi gì, không báo lỗi ồn ào (bình thường, chỉ là người đi
     ngang không phải để chấm công, hoặc góc mặt chưa tốt)
```

### [4.2] Enroll (đăng ký) — càng nhiều mẫu càng chính xác

Chụp **3-5 ảnh** ở góc độ hơi khác nhau (thẳng, hơi nghiêng trái/phải) cho 1 nhân viên, sinh embedding
cho từng ảnh rồi lưu **embedding trung bình** (average vector) — chính xác hơn nhiều so với 1 ảnh duy
nhất. Cho phép Enroll lại (ghi đè) nếu nhận diện sai nhiều — không cần xoá thủ công trước.

### [4.3] Hiệu năng thiết bị yếu

Không chạy nhận diện trên MỌI khung hình camera (30fps sẽ làm nóng máy vô ích) — throttle xuống ~2-3
lần/giây là đủ cho use-case người đi bộ ngang qua cổng.

---

## [5] ĐỒNG BỘ LÊN ERP — API CONTRACT (BẮT BUỘC ĐÚNG 100%, ĐÃ CÓ SẴN BÊN SERVER)

**Đây là phần quan trọng nhất của toàn bộ tài liệu — sai bất kỳ chi tiết nào ở đây (tên field, kiểu dữ
liệu, tên header, múi giờ) đều khiến đồng bộ thất bại hoặc ghi sai dữ liệu vào ERP.**

### [5.1] Đích đến ở phía ERP (chỉ để hiểu, KHÔNG động vào)

Server ghi vào bảng `hr.AttendanceEvent` (schema `hr`, KHÔNG phải `EventLog` chung của module Order/kho —
đó là 1 bảng khác `inv.EventLog` không liên quan) thông qua stored procedure
`hr.usp_AttendanceEvent_Sync`. App Android **không** kết nối SQL trực tiếp — mọi thứ đi qua API HTTP duy
nhất dưới đây.

### [5.2] Endpoint

```
POST https://dgperp.azurewebsites.net/api/v1/attendance/sync-events
```

Header bắt buộc:
```
Content-Type: application/json
X-Attendance-Api-Key: <giá trị API key — cấu hình ở màn hình Settings của app, KHÔNG hardcode trong code>
```

**Múi giờ — RẤT QUAN TRỌNG**: server tính "hôm nay"/"tăng ca sau 16:30" dựa trên **UTC**
(`SYSUTCDATETIME()`), KHÔNG phải giờ Việt Nam. `eventTime` gửi lên **PHẢI là thời điểm UTC** (device ở
Việt Nam là UTC+7 → phải trừ 7 giờ trước khi gửi, hoặc dùng `Instant`/`OffsetDateTime` của Java Time API
và convert đúng, đừng tự lấy `LocalDateTime.now()` gửi thẳng — sẽ lệch 7 giờ, khiến 1 lần chấm công lúc
17h chiều VN (10h UTC, chưa quá giờ tan ca) bị hiểu nhầm thành 00h UTC hôm sau hoặc tính sai tăng ca).

### [5.3] Request body

Mảng JSON (không bọc trong object), mỗi phần tử:

```json
[
  {
    "employeeCode": "NV001",
    "eventTime": "2026-08-30T01:00:00",
    "deviceCode": "Cong-Chinh"
  }
]
```

- `employeeCode` (string, bắt buộc) — PHẢI khớp `EnrolledEmployee.employee_code` cục bộ (chính là
  `dm.Employee.EmployeeCode` bên ERP).
- `eventTime` (string ISO-8601, bắt buộc) — giờ UTC, xem [5.2]. Định dạng an toàn:
  `"2026-08-30T01:00:00"` (không có hậu tố `Z` cũng được — server parse bằng kiểu `DateTime` của .NET,
  chấp nhận cả 2, nhưng để tránh mọi nhầm lẫn, **hãy tự đảm bảo giá trị số đã là UTC trước khi format**,
  không dựa vào hậu tố `Z` để server "tự hiểu").
- `deviceCode` (string, tùy chọn, có thể null) — tên thiết bị cấu hình ở Settings.

Gửi theo **batch** (nhiều phần tử 1 lần gọi) — không gọi API riêng từng sự kiện, tốn round-trip. Đề xuất
batch tối đa 200 phần tử/lần.

### [5.4] Response

HTTP 200, body là mảng JSON cùng thứ tự với request:

```json
[
  { "employeeCode": "NV001", "eventTime": "2026-08-30T01:00:00", "status": "Inserted" },
  { "employeeCode": "NV002", "eventTime": "2026-08-30T01:05:00", "status": "Duplicate" },
  { "employeeCode": "NVLA", "eventTime": "2026-08-30T01:10:00", "status": "UnknownEmployee" }
]
```

`status` là 1 trong 3 giá trị:
- `"Inserted"` → thành công, app đánh dấu `sync_status='Synced'`, `synced_at=now`.
- `"Duplicate"` → server đã có sẵn đúng `(employeeCode, eventTime)` này từ trước (thường do app gửi lại
  sau khi mất kết nối giữa chừng lần trước) → app **cũng đánh dấu `Synced`** (không phải lỗi, coi như
  thành công).
- `"UnknownEmployee"` → Mã NV chưa tồn tại bên ERP → app đánh dấu `sync_status='UnknownEmployee'`, **không
  retry tự động vô hạn** (sẽ mãi thất bại tới khi admin tạo nhân viên bên ERP) — hiện rõ trong màn hình
  hàng đợi để admin biết cần xử lý, cho phép nút "Thử lại" thủ công riêng cho các dòng này.

### [5.5] Mã lỗi HTTP khác cần xử lý

- `401 Unauthorized` — API key sai/thiếu → app phải dừng đồng bộ, báo rõ "API key không đúng, vào Cài
  đặt kiểm tra lại" — đừng lặp lại request liên tục (tốn pin/data vô ích khi chắc chắn sẽ luôn 401 tới
  khi người dùng tự sửa key).
- `503 Service Unavailable` — server chưa cấu hình API key (tình huống hiếm, phía admin ERP quên set) →
  báo lỗi rõ, coi như lỗi tạm thời, retry sau theo lịch bình thường (không phải lỗi phía app).
- Lỗi mạng (timeout, không có kết nối) → giữ nguyên `Pending`, thử lại ở lần chạy WorkManager tiếp theo,
  tăng `sync_attempts` để hiển thị debug nếu cần nhưng KHÔNG dùng số lần thử để chặn retry (khác hẳn
  `UnknownEmployee` — lỗi mạng luôn đáng thử lại vô hạn lần, lỗi UnknownEmployee thì không).

### [5.6] Đồng bộ định kỳ

`WorkManager` periodic, đề xuất **mỗi 10 phút** khi có mạng (`Constraints.NetworkType.CONNECTED`), cộng
thêm nút "Đồng bộ ngay" thủ công trong màn hình quản trị. Không cần đồng bộ real-time ngay lúc chấm công
— đây không phải hệ thống chấm công thời gian thực cho bảo vệ kiểm tra tại chỗ, chỉ để tính công/báo cáo.

---

## [6] BẢO MẬT

1. **API key KHÔNG được hardcode/commit vào source code hay file Gradle** — repo này (dù Private) không
   nên chứa secret thật trong lịch sử git. Nhập 1 lần ở màn hình Settings lúc cài đặt máy tại nhà máy,
   lưu bằng `EncryptedSharedPreferences` (Jetpack Security). Nếu cần giá trị mặc định cho môi trường dev
   test riêng của lập trình viên, dùng `local.properties` (đã có sẵn trong `.gitignore` mặc định của mọi
   project Android) — KHÔNG commit file này.
2. **Embedding khuôn mặt lưu cục bộ, không bao giờ rời khỏi thiết bị** — chỉ `employeeCode` +
   `eventTime` + `deviceCode` được gửi lên server, KHÔNG gửi ảnh/embedding lên bất kỳ đâu (kể cả lên
   ERP) — giữ đúng tinh thần "camera chỉ dùng để suy ra thời điểm có mặt, không phải kho lưu ảnh nhân
   viên".
3. Không log dữ liệu nhạy cảm (embedding, ảnh mặt) ra Logcat ở bản release.
4. Quyền `CAMERA` xin lúc chạy (runtime permission), giải thích rõ mục đích trong UI trước khi xin.
5. HTTPS bắt buộc cho mọi request tới server (endpoint đã là `https://`).

---

## [7] GIAO DIỆN

- Thiết bị mục tiêu chủ yếu là **tablet gắn cố định tại cổng**, màn hình xoay ngang (landscape) — ưu
  tiên layout ngang, chữ to dễ nhìn từ xa vài mét.
- Màn hình chính (chế độ vận hành bình thường): camera preview full-screen + overlay tên/trạng thái khi
  nhận diện thành công (2 giây rồi tự ẩn) — không cần nút bấm gì cho công nhân, hoàn toàn tự động.
- Vào màn hình Enroll/Cài đặt/Hàng đợi đồng bộ qua 1 nút nhỏ góc màn hình (không nổi bật, tránh công nhân
  bấm nhầm) — có thể khóa bằng PIN 4 số đơn giản nếu người dùng yêu cầu sau (chưa làm ở Phase 1, xem
  [2]).
- Toàn bộ text tiếng Việt có dấu.
- Hiện rõ trạng thái mạng (có/không có internet) và số lượng sự kiện đang chờ đồng bộ ở màn hình quản
  trị — admin cần biết máy có "tồn đọng" hay không mà không cần mở app ERP để kiểm tra chéo.

---

## [8] CI/CD — BUILD APK TỰ ĐỘNG (GitHub Actions)

### [8.1] Mục tiêu

Mỗi lần push lên `main`, GitHub Actions tự build APK, để người dùng **tải trực tiếp về điện thoại
Android cài đặt** (sideload) — KHÔNG publish lên Google Play (ngoài phạm vi, đây là app nội bộ công ty).

### [8.2] Ký APK (signing)

Dùng **debug build** (`./gradlew assembleDebug`) cho Phase 1 — Android cho phép cài APK ký bằng debug
key nếu bật "Cài đặt từ nguồn không xác định" trên điện thoại, đơn giản hơn nhiều so với quản lý
release keystore + GitHub Secrets. Ghi rõ TODO: nếu sau này cần phân phối rộng hơn/qua MDM doanh nghiệp,
mới cần chuyển sang release keystore ký thật (hỏi lại người dùng lúc đó, đừng tự làm trước khi cần).

### [8.3] Workflow đề xuất (`.github/workflows/build-apk.yml`)

- Trigger: `push` tới `main`.
- Steps: checkout → setup JDK 17 → setup Android SDK (`android-actions/setup-android`) → cache Gradle →
  `./gradlew assembleDebug` → upload APK làm **GitHub Release** (tag theo `run_number`, đính kèm file
  `.apk`) — KHÔNG chỉ dừng ở "artifact" tạm thời của Actions (artifact hết hạn sau vài chục ngày mặc
  định và khó lấy link ổn định để cài lại sau này); Release cho link tải cố định, dễ chia sẻ.
- Người dùng vào tab "Releases" của repo trên điện thoại (trình duyệt), tải file `.apk`, cài đặt.

---

## [9] KIỂM THỬ

- Unit test cho hàm so khớp embedding (cosine similarity) — test với vector giả lập, không cần model
  thật.
- Unit test cho logic debounce (không tạo sự kiện trùng trong ngưỡng thời gian).
- Unit test cho việc convert local time → UTC trước khi gửi API (đây là chỗ dễ sai nhất, xem [5.2] — bắt
  buộc có test riêng cho việc này).
- Test tích hợp (có thể mock HTTP) cho luồng đồng bộ: request thành công toàn bộ, request có
  `UnknownEmployee`, mất mạng giữa chừng, sai API key.
- Test thủ công trên thiết bị thật: enroll 2-3 người thật, đứng trước camera, xác nhận nhận diện đúng và
  KHÔNG nhận nhầm giữa 2 người quen mặt nhau (anh em, đồng nghiệp giống nhau) — ngưỡng similarity ở [4.1]
  có thể cần chỉnh tay sau bước này.

---

## [10] QUY TẮC LÀM VIỆC

1. **Hỏi trước khi**: đổi kiến trúc nhận diện khuôn mặt (model khác, thêm cloud API), đổi API contract ở
   mục [5] (đây là hợp đồng với server đã CHẠY THẬT trên production, tự đổi phía app mà không báo phía
   ERP sẽ làm hỏng đồng bộ), thêm quyền Android nhạy cảm ngoài CAMERA/INTERNET.
2. Làm từng bước nhỏ, commit theo từng nhóm logic rõ ràng.
3. Không tự bịa thêm field/endpoint không có trong mục [5] — nếu thấy cần, dừng lại hỏi người dùng
   (người dùng sẽ cần quay lại phiên Claude Code của repo DGP.ERP để thêm field/endpoint đó trước).
4. Comment tiếng Việt cho phần logic nghiệp vụ đặc thù (debounce, ngưỡng similarity, quy đổi múi giờ).
5. Test kỹ trên thiết bị thật trước khi coi 1 tính năng là xong — nhận diện khuôn mặt sai lệch nhiều
   giữa môi trường giả lập (emulator không có camera thật) và thiết bị thật.

---

## [11] ĐỊNH NGHĨA HOÀN THÀNH (PHASE 1)

- [ ] Enroll được nhân viên (chụp ảnh, sinh embedding, lưu local)
- [ ] Camera chạy liên tục, nhận diện đúng người đã enroll, ghi sự kiện local có debounce
- [ ] Hàng đợi đồng bộ hoạt động đúng theo API contract mục [5] (test thật với server
      `dgperp.azurewebsites.net`, không phải mock) — bao gồm đúng cả 3 trạng thái Inserted/Duplicate/
      UnknownEmployee và các mã lỗi HTTP ở [5.5]
- [ ] Giờ gửi lên server là UTC chính xác (verify bằng cách chấm công lúc gần nửa đêm giờ VN, kiểm tra
      `hr.vw_AttendanceDaily` bên ERP xem có bị lệch ngày không)
- [ ] API key không hardcode trong source, nhập được ở màn hình Settings
- [ ] GitHub Actions build APK thành công, tạo Release có file `.apk` tải được
- [ ] Test trên ít nhất 1 thiết bị Android thật (không chỉ emulator)
- [ ] Ghi chú rõ những gì còn là TODO Phase 2

---

## [12] ĐỊNH HƯỚNG PHASE SAU (chỉ để thiết kế cho dễ mở rộng, KHÔNG code bây giờ)

- Phase 2: chống giả mạo (liveness detection — chớp mắt/quay đầu theo yêu cầu), khóa màn hình quản trị
  bằng PIN/vân tay, nhận diện nhiều mặt cùng lúc trong 1 khung hình (nhiều người đi cùng lúc qua cổng).
- Phase 3: đồng bộ 2 chiều nhẹ (app hiện được trạng thái "đã đồng bộ tới lúc nào" lấy từ server thay vì
  chỉ tự suy luận từ local), thông báo đẩy khi có nhân viên `UnknownEmployee` tồn đọng lâu.
- Phase 4: quản lý nhiều thiết bị tập trung (xem tất cả tablet đang hoạt động, tồn đọng đồng bộ, phiên
  bản app đang chạy) — có thể cần 1 màn hình nhỏ phía ERP, KHÔNG phải trong app Android này.

**Bắt đầu bằng bước khảo sát ở mục [1].**

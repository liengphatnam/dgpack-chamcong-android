# API đồng bộ NHÂN VIÊN + EMBEDDING khuôn mặt (app Android ⇄ DGP.ERP)

> Bổ sung cho mục [5] của `chamcongFaceID.md`. App Android đã code sẵn phía client theo
> contract này (`network/FaceSyncDtos.kt`, `network/AttendanceApi.kt`, `sync/EmployeeSyncEngine.kt`).
> **Phía ERP (repo DGP.ERP) CHƯA có 3 endpoint dưới đây** — cần triển khai trong phiên Claude Code
> của repo DGP.ERP rồi deploy lên `https://dgperp.azurewebsites.net`. Cho tới lúc đó app sẽ báo
> "Server ERP chưa có API đồng bộ nhân viên (404)" ở màn hình *Nhân viên ERP*; chấm công và
> đồng bộ sự kiện (`sync-events`) vẫn chạy bình thường, không bị ảnh hưởng.

## Xác thực & quy ước chung

- Cùng cơ chế với `POST /api/v1/attendance/sync-events`: header `X-Attendance-Api-Key`,
  so khớp với `Attendance:SyncApiKey` (xem `AttendanceController.SyncEvents` +
  `AttendanceSyncOptions`), `[AllowAnonymous]`, không qua Entra ID.
- Mã lỗi giống hệt: `401` key sai → app dừng và báo "API key không đúng"; `503` server chưa cấu
  hình key → thử lại sau. `404` → app hiểu là "ERP chưa triển khai endpoint".
- Mọi thời điểm là **UTC**, định dạng `"yyyy-MM-ddTHH:mm:ss"` (như `eventTime` của sync-events).
- Route gợi ý: thêm vào `AttendanceController` (route gốc `api/v1/attendance`), tái dùng hàm
  `FixedTimeEquals` cho key.

## 1. `GET /api/v1/attendance/employees`

Trả về **toàn bộ** `dm.Employee` (cả `IsActive = 0`, app tự lọc) kèm cờ đã có embedding hay chưa.

```json
[
  { "employeeCode": "NV001", "fullName": "Nguyễn Văn A", "isActive": true,
    "hasFaceEmbedding": true,  "faceUpdatedAt": "2026-09-16T01:00:00",
    "birthDate": "1990-08-20", "lateEarlyCount30d": 0, "commendationCount": 2 },
  { "employeeCode": "NV002", "fullName": "Trần Thị B",   "isActive": true,
    "hasFaceEmbedding": false, "faceUpdatedAt": null,
    "birthDate": null, "lateEarlyCount30d": 3, "commendationCount": 0 }
]
```

`hasFaceEmbedding` = tồn tại dòng `hr.EmployeeFaceEmbedding` cho `EmployeeID` đó (bất kỳ thiết bị nào).

3 trường cuối phục vụ **chương trình trúng thưởng lon nước ngọt** (mục 4), đều **tuỳ chọn** — server
chưa trả thì app vẫn chạy nhưng mọi người trọng số bằng nhau và không ai được ưu tiên sinh nhật:

| Trường | Kiểu | Ý nghĩa |
|---|---|---|
| `birthDate` | `"yyyy-MM-dd"` hoặc null | Ngày sinh (`dm.Employee.BirthDate`). App chấp nhận cả `"1990-08-20T00:00:00"` kiểu .NET, chỉ lấy 10 ký tự đầu. |
| `lateEarlyCount30d` | int, mặc định 0 | Số lần **đi trễ hoặc về sớm** trong 30 ngày gần nhất, ERP tính từ `hr.AttendanceEvent` so với ca làm việc. |
| `commendationCount` | int, mặc định 0 | Số lần được **khen thưởng / phối hợp nội quy** ghi nhận trong Log của ERP (đề xuất: đếm trong khoảng ngày của đợt, xem mục 4). |

## 2. `POST /api/v1/attendance/face-embeddings`

App đẩy embedding của NV enroll trên thiết bị (mỗi NV **1 vector trung bình** đã L2‑normalize,
192 số float). Body là mảng, tối đa 50 phần tử/lần:

```json
[
  { "employeeCode": "NV001",
    "model": "mobilefacenet-192-align2",
    "dimension": 192,
    "embedding": [0.0123, -0.0456, ...],      // đúng 192 số
    "enrolledAt": "2026-09-16T01:00:00",      // UTC, lúc chụp trên thiết bị
    "deviceCode": "Cong-Chinh" }
]
```

Server **upsert theo `(EmployeeID, Model)`** (1 NV chỉ giữ 1 embedding mới nhất cho mỗi model),
lưu `enrolledAt` vào `UpdatedAt` (KHÔNG thay bằng giờ server — app dùng đúng mốc này để so sánh
2 chiều ở bước 3). Response cùng thứ tự với request:

```json
[
  { "employeeCode": "NV001", "status": "Saved" },
  { "employeeCode": "NVLA",  "status": "UnknownEmployee" }
]
```

- `Saved` → app đánh dấu đã đồng bộ (`enrolled_employee.faceSyncedAt = enrolledAt`).
- `UnknownEmployee` → mã chưa có trong `dm.Employee`, app giữ lại và đẩy lại lần sau (giống
  cách xử lý `UnknownEmployee` của sync-events).
- Nếu `dimension` ≠ số phần tử `embedding` → `400`.

## 3. `GET /api/v1/attendance/face-embeddings`

Trả về toàn bộ embedding server đang giữ, để tablet khác (hoặc máy cài lại) tải về và nhận diện
được NV enroll ở nơi khác:

```json
[
  { "employeeCode": "NV001", "model": "mobilefacenet-192-align2", "dimension": 192,
    "embedding": [0.0123, -0.0456, ...],
    "updatedAt": "2026-09-16T01:00:00", "deviceCode": "Cong-Chinh" }
]
```

App bỏ qua phần tử có `model`/`dimension` khác model đang chạy. Quy tắc xung đột phía app:
bản enroll cục bộ **chưa đẩy được** luôn thắng; còn lại bản có `updatedAt`/`enrolledAt` mới hơn thắng.
Số lượng NV nhà máy cỡ vài trăm × ~2 KB/vector → không cần phân trang ở V1.

## 4. `POST /api/v1/attendance/lucky-draws` — sổ trúng thưởng lon nước ngọt

### Nghiệp vụ (chạy hoàn toàn trên tablet, ERP chỉ nhận kết quả)

Chương trình **tháng 8–9/2026** (admin đổi được ngày bắt đầu/kết thúc và quota ở *Cài đặt*):
mỗi ngày **8 người** quét mặt ngẫu nhiên trúng **1 lon nước ngọt**; trúng thì tablet bắn pháo hoa,
đọc giọng nói chúc mừng và hướng dẫn *liên hệ phòng Nhân sự nhận thưởng*.

Thuật toán (`luckydraw/LuckyDrawEngine.kt`, có unit test):

1. Chỉ quay ở **lần chấm công đầu tiên trong ngày** (ngày VN) của mỗi người — 1 người tối đa 1 lượt/ngày.
2. **Sinh nhật** (trùng ngày-tháng với hôm nay) → **chắc chắn trúng 3 lon**, không tính vào quota 8.
3. Còn lại quay ngẫu nhiên **có trọng số**:
   - không đi trễ/về sớm 30 ngày qua ×2 · 1–2 lần ×1 · từ 3 lần ×0.5,
   - mỗi lần được khen thưởng/phối hợp nội quy (Log) **+50 %**, tối đa 4 lần (×3),
   - mỗi lần đã trúng trong đợt ×0.5 (sàn 0.1) để lon nước lan đều ra nhiều người.
4. Chọn đúng ~8 người/ngày dù không biết trước ai sẽ đi làm: *lấy mẫu tuần tự có trọng số*,
   mỗi lượt `p = min(1, quota_còn_lại × w / Σw(người dự kiến còn chấm công hôm nay))`. Pool "dự kiến"
   = ai đã chấm công trên máy này 14 ngày gần đây trừ người đã chấm hôm nay. Không bao giờ vượt quota;
   nếu cả pool đi làm thì kỳ vọng đúng bằng quota. Quota tính **riêng từng thiết bị**.

Kết quả ghi bảng `lucky_draw_win` trên tablet (ngày, người, số lon, lý do, trọng số, xác suất lúc
quay) — admin xem ở màn *Sổ trúng thưởng*, bấm "Đã phát" sau khi trao. Đồng thời đẩy lên ERP ở
**bước 4** của cùng lượt đồng bộ (sau 3 bước trên) để nhân sự đối chiếu trên web.

### Request

Body là mảng, tối đa 200 phần tử:

```json
[
  { "employeeCode": "NV001", "drawDate": "2026-08-20", "wonAt": "2026-08-20T00:31:12",
    "cans": 3, "reason": "Birthday", "chance": 1.0,  "deviceCode": "Cong-Chinh" },
  { "employeeCode": "NV007", "drawDate": "2026-08-20", "wonAt": "2026-08-20T00:35:40",
    "cans": 1, "reason": "Random",   "chance": 0.083, "deviceCode": "Cong-Chinh" }
]
```

- `drawDate` là **ngày Việt Nam**, `wonAt` là **UTC** (như `eventTime`). Khoá chống trùng đề xuất:
  `(EmployeeID, WonAt, DeviceCode)`.
- `reason`: `"Random"` (1 lon) | `"Birthday"` (3 lon). `chance` 0..1 tại lúc quay, để HR đối chiếu.

### Response (cùng thứ tự với request)

```json
[
  { "employeeCode": "NV001", "wonAt": "2026-08-20T00:31:12", "status": "Saved" },
  { "employeeCode": "NV007", "wonAt": "2026-08-20T00:35:40", "status": "Duplicate" },
  { "employeeCode": "NVLA",  "wonAt": "2026-08-20T00:40:00", "status": "UnknownEmployee" }
]
```

`Saved`/`Duplicate` → app đánh dấu đã đồng bộ; `UnknownEmployee` → đánh dấu để admin thấy trong sổ.
`404` (chưa triển khai), mất mạng, lỗi khác → app giữ Pending, thử lại lần sau, **không** ảnh hưởng
3 bước đồng bộ nhân viên/embedding.

```sql
CREATE TABLE hr.LuckyDrawWin
(
    LuckyDrawWinID INT IDENTITY PRIMARY KEY,
    EmployeeID   INT           NOT NULL REFERENCES dm.Employee(EmployeeID),
    DrawDate     DATE          NOT NULL,          -- ngày VN
    WonAt        DATETIME2(0)  NOT NULL,          -- UTC
    Cans         INT           NOT NULL,
    Reason       VARCHAR(20)   NOT NULL,          -- 'Random' | 'Birthday'
    Chance       DECIMAL(6,4)  NOT NULL,
    DeviceCode   NVARCHAR(50)  NULL,
    ClaimedAt    DATETIME2(0)  NULL,              -- nhân sự đánh dấu đã phát trên web (tuỳ chọn)
    CreatedAt    DATETIME2(0)  NOT NULL CONSTRAINT DF_LuckyDrawWin_CreatedAt DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT UQ_LuckyDrawWin UNIQUE (EmployeeID, WonAt, DeviceCode)
);
```

## Bảng gợi ý phía SQL (`hr` schema)

```sql
CREATE TABLE hr.EmployeeFaceEmbedding
(
    EmployeeID   INT           NOT NULL,
    Model        VARCHAR(50)   NOT NULL,          -- 'mobilefacenet-192'
    Dimension    INT           NOT NULL,          -- 192
    Embedding    VARBINARY(MAX) NOT NULL,         -- 192 x float32 little-endian (768 byte), hoặc NVARCHAR(MAX) JSON nếu muốn đọc bằng mắt
    DeviceCode   NVARCHAR(50)  NULL,
    UpdatedAt    DATETIME2(0)  NOT NULL,          -- = enrolledAt app gửi lên (UTC)
    CreatedAt    DATETIME2(0)  NOT NULL CONSTRAINT DF_EmployeeFaceEmbedding_CreatedAt DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_EmployeeFaceEmbedding PRIMARY KEY (EmployeeID, Model),
    CONSTRAINT FK_EmployeeFaceEmbedding_Employee FOREIGN KEY (EmployeeID) REFERENCES dm.Employee(EmployeeID)
);
```

Dữ liệu embedding là dữ liệu sinh trắc học — không cần hiển thị trên web ERP, chỉ cần lockdown
quyền như `hr.AttendanceEvent` (chỉ API đọc/ghi).

## Khi nào app gọi

| Lúc nào | Việc gì |
|---|---|
| Admin vào khu quản trị (sau PIN) | chạy đủ 3 bước (kéo NV → đẩy embedding → tải embedding) + bước 4 đẩy sổ trúng thưởng |
| Admin bấm "Đồng bộ nhân viên & khuôn mặt" ở màn *Nhân viên ERP* | như trên |
| Vừa lưu enroll 1 người | như trên (để embedding lên server ngay nếu có mạng) |
| WorkManager 15 phút (cùng lúc với sync-events) | như trên |

Màn *Nhân viên ERP* mặc định lọc **NV đang hoạt động chưa có khuôn mặt ở đâu cả**; chạm vào 1 dòng
sẽ mở Enroll với mã + tên điền sẵn.

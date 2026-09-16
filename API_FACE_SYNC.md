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
    "hasFaceEmbedding": true,  "faceUpdatedAt": "2026-09-16T01:00:00" },
  { "employeeCode": "NV002", "fullName": "Trần Thị B",   "isActive": true,
    "hasFaceEmbedding": false, "faceUpdatedAt": null }
]
```

`hasFaceEmbedding` = tồn tại dòng `hr.EmployeeFaceEmbedding` cho `EmployeeID` đó (bất kỳ thiết bị nào).

## 2. `POST /api/v1/attendance/face-embeddings`

App đẩy embedding của NV enroll trên thiết bị (mỗi NV **1 vector trung bình** đã L2‑normalize,
192 số float). Body là mảng, tối đa 50 phần tử/lần:

```json
[
  { "employeeCode": "NV001",
    "model": "mobilefacenet-192",
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
  { "employeeCode": "NV001", "model": "mobilefacenet-192", "dimension": 192,
    "embedding": [0.0123, -0.0456, ...],
    "updatedAt": "2026-09-16T01:00:00", "deviceCode": "Cong-Chinh" }
]
```

App bỏ qua phần tử có `model`/`dimension` khác model đang chạy. Quy tắc xung đột phía app:
bản enroll cục bộ **chưa đẩy được** luôn thắng; còn lại bản có `updatedAt`/`enrolledAt` mới hơn thắng.
Số lượng NV nhà máy cỡ vài trăm × ~2 KB/vector → không cần phân trang ở V1.

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
| Admin vào khu quản trị (sau PIN) | chạy đủ 3 bước (kéo NV → đẩy embedding → tải embedding) |
| Admin bấm "Đồng bộ nhân viên & khuôn mặt" ở màn *Nhân viên ERP* | như trên |
| Vừa lưu enroll 1 người | như trên (để embedding lên server ngay nếu có mạng) |
| WorkManager 15 phút (cùng lúc với sync-events) | như trên |

Màn *Nhân viên ERP* mặc định lọc **NV đang hoạt động chưa có khuôn mặt ở đâu cả**; chạm vào 1 dòng
sẽ mở Enroll với mã + tên điền sẵn.

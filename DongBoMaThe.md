# YÊU CẦU PHÍA DGP.ERP: ĐỒNG BỘ MÃ THẺ TỪ + QUÊN THẺ + CHI TIẾT CÔNG + TRÚNG THƯỞNG

> Copy file này vào repo **DGP.ERP** và giao cho Claude Code triển khai. App Android "Chấm Công DGPack"
> (repo `dgpack-chamcong-android`) **đã code xong phía client** theo đúng contract dưới đây và đang gọi
> các endpoint này mỗi 15 phút; hiện server trả 404 nên app giữ dữ liệu chờ trên tablet.
> Ưu tiên làm theo thứ tự mục 1 → 2 → 3 → 4 → 5. Mục 1 và 2 là **bắt buộc trước** (không có thì tablet
> không biết thẻ nào của ai khi cài máy mới).

## 0. Bối cảnh & quy ước chung (giống `sync-events` đã có)

- Stack: .NET 10, Azure Web App `dgperp`, Azure SQL `DGPERP`. Controller hiện có:
  `AttendanceController` (route gốc `api/v1/attendance`), action `SyncEvents` cho
  `POST api/v1/attendance/sync-events`. **Không đổi** contract `sync-events` (vẫn đúng 3 field
  `employeeCode`, `eventTime`, `deviceCode`).
- Xác thực: header `X-Attendance-Api-Key`, so với cấu hình `Attendance:SyncApiKey` bằng
  `FixedTimeEquals`, action `[AllowAnonymous]` (không qua Entra ID). Thiếu/sai key → **401**; server
  chưa cấu hình key → **503**. Tái dùng đúng helper đang có trong `AttendanceController`.
- Mọi thời điểm là **UTC**, định dạng `"yyyy-MM-ddTHH:mm:ss"` (không hậu tố `Z`). Riêng các trường
  "ngày" (`drawDate`, `month`) là **lịch Việt Nam** (UTC+7).
- Body request là **mảng JSON** (không bọc object). Response là mảng **cùng thứ tự và cùng số phần
  tử** với request. Serializer: `camelCase`, bỏ qua field lạ.
- Mã nhân viên khớp `dm.Employee.EmployeeCode` (đã `Trim()`); mã không tồn tại → phần tử đó trả
  `"UnknownEmployee"`, **không** làm hỏng cả batch.
- Mã thẻ (`cardId`) app đã chuẩn hoá: **chữ hoa, chỉ `0-9A-Z`** (UID NFC dạng hex như `04A1B2C3D4`,
  hoặc số in trên thẻ 125 kHz như `0001234567`). Server lưu nguyên chuỗi đó, so khớp exact.

---

## 1. `GET /api/v1/attendance/employees` — thêm 4 trường

Endpoint này có thể đã tồn tại (theo `API_FACE_SYNC.md` mục 1). Cần trả **toàn bộ** `dm.Employee`
(cả `IsActive = 0`, app tự lọc) với các trường:

```json
[
  {
    "employeeCode": "DN0001",
    "fullName": "Nguyễn Văn A",
    "isActive": true,
    "cardId": "0001234567",
    "birthDate": "1990-08-20",
    "lateEarlyCount30d": 1,
    "commendationCount": 2
  }
]
```

| Trường | Kiểu | Nguồn / cách tính |
|---|---|---|
| `cardId` | string hoặc null | Thẻ hiện hành của NV (mục 2). Null nếu chưa gán. |
| `birthDate` | `"yyyy-MM-dd"` hoặc null | `dm.Employee.BirthDate` (nếu chưa có cột thì thêm, nullable). App chấp nhận cả `"1990-08-20T00:00:00"`. |
| `lateEarlyCount30d` | int, mặc định 0 | Số lần đi trễ **hoặc** về sớm trong 30 ngày gần nhất, tính từ `hr.AttendanceEvent` so với ca làm việc. Chưa có logic ca thì trả 0. |
| `commendationCount` | int, mặc định 0 | Số lần khen thưởng / phối hợp nội quy ghi trong Log của ERP trong **tháng hiện tại** (VN). Chưa có nguồn thì trả 0. |

Các trường cũ `hasFaceEmbedding`, `faceUpdatedAt` **không cần nữa** (app bỏ nhận diện khuôn mặt);
có trả cũng không sao.

---

## 2. `POST /api/v1/attendance/cards` — thẻ gán trên tablet (BẮT BUỘC)

Admin gán thẻ ngay trên tablet (chọn NV → quét thẻ → lưu offline); app đẩy lên ở lần đồng bộ tới.
Tối đa 200 phần tử/request.

**Request**
```json
[
  { "cardId": "0001234567", "employeeCode": "DN0001",
    "assignedAt": "2026-09-23T01:00:00", "deviceCode": "Cong-Chinh" }
]
```

**Response** (cùng thứ tự)
```json
[ { "cardId": "0001234567", "status": "Saved" } ]
```
`status` ∈ `"Saved"` | `"UnknownEmployee"`.

**Luật upsert theo `cardId`:**
- 1 thẻ chỉ thuộc 1 NV: thẻ đã thuộc người khác → **chuyển sang NV mới** (ghi lịch sử), không lỗi.
- 1 NV chỉ giữ 1 thẻ hiện hành: gán thẻ mới → thẻ cũ của NV đó hết hiệu lực.
- Idempotent: gửi lại cùng cặp (cardId, employeeCode) → vẫn `"Saved"`.

**SQL gợi ý**
```sql
ALTER TABLE dm.Employee ADD CardId NVARCHAR(32) NULL;
CREATE UNIQUE INDEX UX_Employee_CardId ON dm.Employee(CardId) WHERE CardId IS NOT NULL;

CREATE TABLE hr.EmployeeCardHistory
(
    Id          INT IDENTITY PRIMARY KEY,
    EmployeeID  INT           NOT NULL REFERENCES dm.Employee(EmployeeID),
    CardId      NVARCHAR(32)  NOT NULL,
    AssignedAt  DATETIME2(0)  NOT NULL,   -- UTC, app gửi
    RevokedAt   DATETIME2(0)  NULL,       -- khi thẻ chuyển người / NV đổi thẻ
    DeviceCode  NVARCHAR(50)  NULL,
    CreatedAt   DATETIME2(0)  NOT NULL CONSTRAINT DF_EmployeeCardHistory_CreatedAt DEFAULT (SYSUTCDATETIME())
);
```
Trong 1 transaction: (a) NV khác đang giữ `CardId` này → set `CardId = NULL` cho người đó + `RevokedAt`;
(b) NV này đang giữ thẻ khác → `RevokedAt` dòng cũ; (c) set `dm.Employee.CardId`, insert lịch sử.

Nên có thêm màn hình web nhỏ ở ERP để nhân sự xem/sửa thẻ (không bắt buộc cho app).

---

## 3. `POST /api/v1/attendance/forgot-card` — nhật ký quên thẻ kèm ảnh bằng chứng

NV quên thẻ: nhập mã → xác nhận tên → chớp mắt 2 lần → tablet chụp ảnh mặt ~200 px (JPEG ~10 KB) →
ghi 1 sự kiện chấm công **bình thường qua `sync-events`** + 1 dòng nhật ký gửi lên đây. Tối đa 50
phần tử/request (~0.5 MB).

**Request**
```json
[
  { "employeeCode": "DN0001", "eventTime": "2026-09-23T00:31:12",
    "deviceCode": "Cong-Chinh", "photoBase64": "/9j/4AAQSkZJRg..." }
]
```

**Response**
```json
[ { "employeeCode": "DN0001", "eventTime": "2026-09-23T00:31:12", "status": "Saved" } ]
```
`status` ∈ `"Saved"` | `"Duplicate"` | `"UnknownEmployee"`. Chống trùng theo `(EmployeeID, EventTime)`
chính xác tới giây → `"Duplicate"` (app coi như thành công).

**SQL gợi ý**
```sql
CREATE TABLE hr.ForgotCardLog
(
    Id          INT IDENTITY PRIMARY KEY,
    EmployeeID  INT            NOT NULL REFERENCES dm.Employee(EmployeeID),
    EventTime   DATETIME2(0)   NOT NULL,          -- UTC
    DeviceCode  NVARCHAR(50)   NULL,
    PhotoJpeg   VARBINARY(MAX) NULL,              -- ~10 KB
    CreatedAt   DATETIME2(0)   NOT NULL CONSTRAINT DF_ForgotCardLog_CreatedAt DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT UQ_ForgotCardLog UNIQUE (EmployeeID, EventTime)
);
```
Ảnh là dữ liệu cá nhân: chỉ nhân sự xem, giữ tối đa 6 tháng rồi xoá (job dọn). Tablet tự xoá sau 2 tháng.

**Luật phạt (để ERP tính `penaltyAmount` ở mục 4, khớp với tablet):** đếm số lần trong **tháng VN**
(`EventTime` quy về UTC+7). Mặc định: lần thứ **2** trừ 50.000 đ, sau đó cứ **2** lần trừ thêm 50.000 đ;
tháng sau về 0. Ba tham số này admin chỉnh được trên tablet → nên đưa vào bảng cấu hình ERP
(`FirstAt = 2, Every = 2, Amount = 50000`) để hai bên cùng một nguồn. Công thức:
`penalty(n) = n < FirstAt ? 0 : Amount * (1 + (n - FirstAt) / Every)` (chia lấy phần nguyên).

---

## 4. `GET /api/v1/attendance/month-summary?month=yyyy-MM` — chi tiết công tháng

Sau khi quét thẻ, NV bấm "Chi tiết công tháng này" và xem ngay (kể cả mất mạng) — tablet cache toàn bộ
bảng này mỗi lần đồng bộ. `month` theo lịch VN. Tính **tới hết ngày hôm qua** (VN), hôm nay chưa tính.
Trả về mọi NV đang hoạt động (NV không có dữ liệu → toàn 0).

```json
[
  { "employeeCode": "DN0001",
    "workDays": 18.5,
    "otRegularHours": 12.0,
    "otSundayHours": 8.0,
    "otHolidayHours": 0.0,
    "leaveDays": 1.0,
    "disciplinaryCount": 0,
    "commendationCount": 1,
    "forgotCardCount": 1,
    "penaltyAmount": 0 }
]
```

| Trường | Nguồn gợi ý |
|---|---|
| `workDays` | tổng ngày công từ `hr.vw_AttendanceDaily` (hoặc view tương đương) trong tháng |
| `otRegularHours` / `otSundayHours` / `otHolidayHours` | giờ tăng ca ngày thường / chủ nhật / ngày lễ (danh mục ngày lễ của ERP) |
| `leaveDays` | ngày nghỉ phép đã duyệt trong tháng |
| `disciplinaryCount` | số biên bản phạt trong tháng |
| `commendationCount` | số lần khen thưởng / phối hợp nội quy trong tháng |
| `forgotCardCount` | `COUNT(*)` từ `hr.ForgotCardLog` trong tháng VN |
| `penaltyAmount` | công thức mục 3 áp lên `forgotCardCount` (đồng, số nguyên) |

Chỗ nào ERP chưa có dữ liệu (vd chưa quản lý ngày lễ) thì trả **0** và ghi TODO, không được bỏ trường.
Tablet hiện `max(forgotCardCount của ERP, số máy tự đếm)` và tự tính lại tiền theo luật đang cài.

---

## 5. `POST /api/v1/attendance/lucky-draws` — sổ trúng thưởng lon nước ngọt

Chương trình tháng 8–9/2026: mỗi ngày ~8 người quét thẻ trúng 1 lon (quay có trọng số trên tablet),
sinh nhật chắc chắn 3 lon. Tablet ghi sổ và đẩy lên để nhân sự phát thưởng. Tối đa 200 phần tử/request.

**Request**
```json
[
  { "employeeCode": "DN0001", "drawDate": "2026-09-23", "wonAt": "2026-09-23T00:31:12",
    "cans": 1, "reason": "Random", "chance": 0.083, "deviceCode": "Cong-Chinh" }
]
```
`reason` ∈ `"Random"` | `"Birthday"`; `chance` 0..1 (xác suất lúc quay, để HR đối chiếu); `drawDate` ngày VN.

**Response**
```json
[ { "employeeCode": "DN0001", "wonAt": "2026-09-23T00:31:12", "status": "Saved" } ]
```
`status` ∈ `"Saved"` | `"Duplicate"` | `"UnknownEmployee"`, chống trùng theo `(EmployeeID, WonAt, DeviceCode)`.

**SQL gợi ý**
```sql
CREATE TABLE hr.LuckyDrawWin
(
    Id          INT IDENTITY PRIMARY KEY,
    EmployeeID  INT           NOT NULL REFERENCES dm.Employee(EmployeeID),
    DrawDate    DATE          NOT NULL,          -- ngày VN
    WonAt       DATETIME2(0)  NOT NULL,          -- UTC
    Cans        INT           NOT NULL,
    Reason      VARCHAR(20)   NOT NULL,
    Chance      DECIMAL(6,4)  NOT NULL,
    DeviceCode  NVARCHAR(50)  NULL,
    ClaimedAt   DATETIME2(0)  NULL,              -- nhân sự đánh dấu đã phát (tuỳ chọn)
    CreatedAt   DATETIME2(0)  NOT NULL CONSTRAINT DF_LuckyDrawWin_CreatedAt DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT UQ_LuckyDrawWin UNIQUE (EmployeeID, WonAt, DeviceCode)
);
```

---

## 6. Cách app gọi & cách xử lý lỗi (để test cho đúng)

Mỗi lượt đồng bộ (15 phút/lần khi có mạng, hoặc khi admin vào khu quản trị, hoặc vừa gán thẻ) app gọi
**theo thứ tự**: `GET employees` → `POST lucky-draws` → `POST cards` → `POST forgot-card` →
`GET month-summary`. Chỉ `GET employees` là bước chính (lỗi → dừng lượt); các bước sau **404 / lỗi mạng
/ 5xx → app bỏ qua, giữ Pending, gửi lại lần sau**. Vì vậy có thể deploy từng endpoint một.

Trước đó app còn gọi `POST sync-events` (đã có) để đẩy sự kiện chấm công — không đổi.

## 7. Kiểm thử nhanh bằng curl

```bash
KEY='<Attendance:SyncApiKey>'
BASE='https://dgperp.azurewebsites.net/api/v1/attendance'

curl -s -H "X-Attendance-Api-Key: $KEY" "$BASE/employees" | head -c 600

curl -s -H "X-Attendance-Api-Key: $KEY" -H "Content-Type: application/json" \
  -d '[{"cardId":"0001234567","employeeCode":"DN0001","assignedAt":"2026-09-23T01:00:00","deviceCode":"TEST"}]' \
  "$BASE/cards"
# -> [{"cardId":"0001234567","status":"Saved"}]

curl -s -H "X-Attendance-Api-Key: $KEY" -H "Content-Type: application/json" \
  -d '[{"employeeCode":"DN0001","eventTime":"2026-09-23T00:31:12","deviceCode":"TEST","photoBase64":null}]' \
  "$BASE/forgot-card"
# -> [{"employeeCode":"DN0001","eventTime":"2026-09-23T00:31:12","status":"Saved"}]  (gửi lần 2 -> "Duplicate")

curl -s -H "X-Attendance-Api-Key: $KEY" "$BASE/month-summary?month=2026-09" | head -c 600

curl -s -o /dev/null -w '%{http_code}\n' -H "X-Attendance-Api-Key: sai" "$BASE/cards" -d '[]' -H "Content-Type: application/json"
# -> 401
```

## 8. Định nghĩa hoàn thành

- [ ] `GET employees` trả `cardId`, `birthDate`, `lateEarlyCount30d`, `commendationCount`.
- [ ] `POST cards` upsert đúng luật 1 thẻ – 1 người, có lịch sử, trả `Saved`/`UnknownEmployee`.
- [ ] `POST forgot-card` lưu ảnh, chống trùng, trả 3 trạng thái.
- [ ] `GET month-summary` trả đủ 10 trường cho mọi NV đang hoạt động, tính tới hết hôm qua (VN).
- [ ] `POST lucky-draws` lưu sổ, chống trùng.
- [ ] Tất cả dùng chung xác thực `X-Attendance-Api-Key`, 401/503 như `sync-events`.
- [ ] Đã test bằng curl ở mục 7 trên `dgperp.azurewebsites.net`, sau đó trên tablet bấm "Đồng bộ ngay"
      và thấy số thẻ hiện trong danh sách "Gán thẻ từ" chuyển sang "Đã đồng bộ".

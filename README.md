# Chấm Công DGPack — Android

App Android native (Kotlin) chấm công bằng nhận diện khuôn mặt, chạy offline-first tại
cổng nhà máy Dona Green Pack, đồng bộ định kỳ lên **DGP.ERP**
(`https://dgperp.azurewebsites.net`, repo ERP: [liengphatnam/DGP.ERP](https://github.com/liengphatnam/DGP.ERP)).
Xem đặc tả đầy đủ tại [`chamcongFaceID.md`](./chamcongFaceID.md) ở repo này.

## Yêu cầu môi trường build

- Android Studio (Koala trở lên) hoặc JDK 17 + Android SDK (compileSdk 34) cài riêng.
- **App này CHƯA được build/compile thử trong máy dùng để viết code** (môi trường đó
  không có JDK/Gradle/Android SDK) — mở bằng Android Studio để lần build đầu tiên tự
  đồng bộ Gradle, tải dependency, và báo lỗi version nếu có (Android Studio thường tự
  đề xuất bump version qua Upgrade Assistant nếu cần).

```bash
cp local.properties.example local.properties   # rồi sửa sdk.dir cho đúng máy bạn
./gradlew assembleDebug
./gradlew test          # chạy unit test
```

## Model nhận diện khuôn mặt

`app/src/main/assets/mobilefacenet.tflite` — MobileFaceNet, input `[1,112,112,3]`
(chuẩn hoá `(pixel-128)/128`), output embedding `192` chiều. Nguồn tải và license xem
[`app/src/main/assets/MODEL_LICENSE.txt`](app/src/main/assets/MODEL_LICENSE.txt). App
nội bộ công ty, không phân phối công khai nên rủi ro license thấp — nếu sau này cần
phân phối rộng hơn, nên tự convert model từ nguồn license rõ ràng hơn.

## Điểm lệch nhỏ so với đặc tả gốc (đã cân nhắc, không tự tiện đổi API contract)

- **Chu kỳ đồng bộ**: đặc tả đề xuất 10 phút, nhưng `PeriodicWorkRequest` của
  WorkManager bị Android ép chu kỳ tối thiểu **15 phút** (giới hạn nền tảng, không sửa
  được). Đã bù bằng nút "Đồng bộ ngay" thủ công. Không ảnh hưởng contract API ở mục
  [5] của tài liệu gốc — chỉ là tần suất gọi.

## Kiến trúc tóm tắt

- `camera/FaceAnalyzer.kt` — ML Kit phát hiện khuôn mặt, throttle ~3fps.
- `face/FaceEmbedder.kt` — TFLite MobileFaceNet sinh embedding.
- `face/FaceMatcher.kt` — cosine similarity, ngưỡng cấu hình được ở Settings.
- `data/db/` — Room (SQLite), 2 bảng `enrolled_employee` / `attendance_event_local`.
- `sync/SyncEngine.kt` — logic đồng bộ thuần (test được bằng MockWebServer), tách khỏi
  `sync/SyncWorker.kt` (WorkManager).
- `network/` — Retrofit theo đúng contract mục [5] của `chamcongFaceID.md`.
- Không dùng Hilt/DI framework — `ChamCongApplication` đóng vai trò service locator
  đơn giản (quy mô 4 màn hình, không cần thêm phụ thuộc).

## TODO Phase 2 (chưa làm, xem mục [12] tài liệu gốc)

- Liveness detection (chống giả mạo ảnh/video).
- Khoá màn hình quản trị bằng PIN.
- Nhận diện nhiều khuôn mặt cùng lúc trong 1 khung hình.
- Ký APK bằng release keystore thật (hiện dùng debug build — xem mục [8.2]).
- Test trên thiết bị Android thật (bắt buộc trước khi coi Phase 1 là "xong" — xem mục
  [11] Định nghĩa hoàn thành).

## CI/CD

Push lên `main` → GitHub Actions build `assembleDebug` → tạo GitHub Release đính kèm
`.apk` (xem `.github/workflows/build-apk.yml`). Tải APK từ tab **Releases** của repo,
cài trực tiếp trên tablet (cần bật "Cài đặt từ nguồn không xác định").

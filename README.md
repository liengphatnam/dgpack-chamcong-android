# DGPack — Chấm công nhận diện khuôn mặt (Android)

App Android native chấm công công nhân bằng nhận diện khuôn mặt qua camera, lưu cục bộ (SQLite),
đồng bộ định kỳ lên hệ thống ERP nội bộ [DGP.ERP](https://github.com/liengphatnam/DGP.ERP) (module
"Chấm công nhà máy", schema `hr`).

**Repo này chưa có code** — mới chỉ có tài liệu đặc tả. Để bắt đầu xây dựng:

1. Mở Claude Code tại thư mục gốc repo này.
2. Dán toàn bộ nội dung [`chamcongFaceID.md`](./chamcongFaceID.md) làm prompt đầu tiên.
3. Làm theo đúng thứ tự các bước trong tài liệu (khảo sát → duyệt kế hoạch → code).

API contract kết nối với ERP (endpoint, header, format dữ liệu) đã được ghi chính xác ở mục [5] của
`chamcongFaceID.md` — đây là hợp đồng với server production thật, không tự đổi khi chưa xác nhận lại.

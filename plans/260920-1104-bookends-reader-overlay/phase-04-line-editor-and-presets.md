# Phase 04 — Line editor & preset library

**Module:** `:feature:reader` · **Phụ thuộc:** Phase 02 · **Trạng thái:** ⏳

## Đã có (tầng logic + UI tối thiểu)

- Thêm/xoá/sửa dòng theo từng vị trí trong `BookendsSettingsSheet` (nhập chuỗi định dạng trực tiếp).
- Chọn style dòng (Regular/Bold/Italic/Bold-Italic), uppercase, cỡ chữ, lọc trang lẻ/chẵn.
- Lưu preset mới, đổi tên, nhân bản, xoá, đặt làm preset đang dùng.
- `BookendsAutoRule` + logic chọn preset theo đuôi file (đuôi khớp → preset, hoặc `null` = ẩn hẳn).
- Khôi phục preset gốc.

## Còn lại

1. **Live preview trong lúc gõ** — hiện preview chỉ cập nhật sau khi lưu dòng; cần preview theo từng
   ký tự với snapshot giả lập.
2. **Bảng chọn token** — danh sách token có nhóm (Metadata / Trang / Thời gian / Đọc / Thiết bị),
   chèn vào vị trí con trỏ; kèm mô tả và ví dụ.
3. **Bảng chọn icon** — thay cho Nerd Fonts picker.
4. **Sắp xếp dòng**: di chuyển lên/xuống, chuyển sang vị trí khác.
5. **UI cho auto-rule** — thêm/sửa/xoá quy tắc theo đuôi file.
6. **Preset gallery** — chỉ khi có nguồn preset từ xa; hiện chưa có nên hoãn.
7. **Cử chỉ** đổi preset / ẩn hiện nhanh (ví dụ nhấn giữ vùng dưới giữa).

## Lý do tách

Phần này là công thái học chỉnh sửa, không phải ngữ nghĩa. Phase 1–2 đã cho người dùng sửa bằng cách
gõ chuỗi định dạng, đủ để dùng thật trước khi đầu tư vào editor trực quan.

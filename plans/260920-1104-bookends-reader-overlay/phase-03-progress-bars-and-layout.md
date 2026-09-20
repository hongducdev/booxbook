# Phase 03 — Thanh tiến độ full-width & layout nâng cao

**Module:** `:core:model` + `:feature:reader` · **Phụ thuộc:** Phase 02 · **Trạng thái:** ⏳ một phần

## Đã có

- `BookendsBarLayer` (anchor `TOP/BOTTOM/LEFT/RIGHT`, fill 4 hướng, style, `thicknessDp`, `insetDp`,
  `ticks OFF/TOP_LEVEL/TOP_TWO_LEVELS`) và renderer vẽ thanh neo ở cạnh màn đọc.
- `%bar` nội dòng với 5 style, co giãn theo bề rộng còn lại của dòng.
- Margin toàn cục 4 phía + `truncationGapDp` trong preset.
- `%spacer` co giãn; `%token{N}` giới hạn bề rộng.

## Còn lại

1. **Mốc chương trên thanh full-width** — vẽ tick theo `TocItem.children`, độ dày giảm dần theo cấp.
2. **Smart ellipsis** — khi vùng trái/giữa/phải cùng hàng chồng nhau thì cắt bớt kèm `…`, ưu tiên
   vùng giữa (và tuỳ chọn đảo ưu tiên như bản gốc).
3. **Margin theo từng vùng** — tầng thứ hai của hệ 3 tầng (hiện có toàn cục + per-line).
4. **Nudge pixel theo dòng** — tầng thứ ba.
5. **`WAVE` / `RADIAL` / `RADIAL_HOLLOW`** cho `%bar` — hiện đã khai báo enum nhưng renderer mới phủ
   5 style đầu; cần hoặc cài đặt hoặc bỏ khỏi danh sách chọn để không hứa hão.
6. **Định dạng thời lượng theo lựa chọn người dùng** (`classic` / `letters` / `modern`) thay vì cố
   định kiểu `letters`.

## Ghi chú

Không có mục nào ở đây chặn Phase 1–2 dùng được. Đây là phần "đẹp thêm", nên tách riêng để không
làm phình lần giao đầu.

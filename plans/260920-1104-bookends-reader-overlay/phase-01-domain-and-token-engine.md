# Phase 01 — Domain model & token engine

**Module:** `:core:model` · **Phụ thuộc:** không · **Trạng thái:** ✅

## Mục tiêu

Toàn bộ ngữ nghĩa của bookends (token, điều kiện, định dạng) nằm trong lớp thuần Kotlin, không phụ
thuộc Android hay Compose, để unit test chạy nhanh và để `:feature:reader` chỉ còn việc vẽ.

## Việc đã làm

1. `BookendsPreset.kt` — `BookendsPosition` (6 vùng), `BookendsTextStyle`, `BookendsPageFilter`,
   `BookendsBarType`, `BookendsBarStyle`, `BookendsBarSpec`, `BookendsLine`, `BookendsGroup`,
   `BookendsBarLayer` (+ anchor/fill/ticks), `BookendsPreset`, `BookendsSettings`,
   `BookendsAutoRule`, `BookendsDefaults` (preset `minimal` + `standard`).
2. `BookendsSnapshot.kt` — ảnh chụp bất biến các giá trị token có thể cần: metadata, vị trí đọc,
   thống kê phiên, thiết bị, đồng hồ, mục lục theo cấp.
3. `BookendsChunk.kt` — đầu ra đã resolve: `Text` (kèm bold/italic/uppercase/maxWidthDp),
   `Icon` (Material icon enum), `ProgressBar`, `Spacer`.
4. `BookendsDuration.kt` — `duration()`, `clock()`, `date()`… + `strftime(pattern)` rút gọn.
5. `BookendsConditional.kt` — parser đệ quy `[if:expr]A[else]B[/if]`, biểu thức với `= != < > <= >=`,
   `and`/`or`/`not`, ngoặc, tham chiếu `@key`, so sánh số khi cả hai vế là số.
6. `BookendsTokenizer.kt` — quét chuỗi: `%token`, `%token{N}`, `%<token>`, `%bar`, `%spacer`,
   `[b]/[i]/[u]` lồng đúng thứ tự, pluralisation `x(s)`/`x(es)`, token lạ giữ nguyên nghĩa đen.
7. `BookendsFormatter.kt` — `format(format, snapshot, pageIndex)` → `BookendsRender(chunks, isBlank)`.

## Quyết định thiết kế

- **Resolver trả về Chunk, không trả String.** `%bar` và `%spacer` cần layout của Compose
  (`weight(1f)`), nên không thể nhét vào text. `Icon` tách khỏi text để dùng Material Icons thay
  Nerd Fonts.
- **Auto-hide tính bằng `isBlank`**, không cần cờ riêng trên dòng.
- **Điều kiện ẩn thì xoá hẳn**, giống bản gốc: khoảng trắng nằm *ngoài* khối vẫn còn, nên mới khuyến
  nghị viết `[if:x]… [/if]` với dấu cách bên trong.
- **Token số vẫn giữ `lastNumeric`** để pluralisation hoạt động ở từ đứng ngay sau.

## Kiểm thử

`core/model/src/test/java/com/booxbook/core/model/bookends/`
- `BookendsConditionalTest` — ưu tiên toán tử, so sánh số/chuỗi, `@key`, ngoặc, lồng nhau, `[else]`.
- `BookendsTokenizerTest` — token biết/không biết, width limit, `%<…>`, tag lồng, tag sai thứ tự,
  pluralisation.
- `BookendsFormatterTest` — token thật từ snapshot, `%bar`, `%spacer`, auto-hide, lọc trang lẻ/chẵn.

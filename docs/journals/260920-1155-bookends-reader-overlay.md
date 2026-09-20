# Journal Entry: Bookends — lớp thông tin phủ trên trang đọc
**Date:** 2026-09-20
**Module:** `:core:model`, `:feature:reader`
**Status:** Phase 1–5 xong, đã nghiệm thu trên máy thật (Galaxy S24 FE, Android 15) — compile ✓, 179 unit test ✓, không crash, migration giữ nguyên dữ liệu người dùng.
**Plan:** `plans/260920-1104-bookends-reader-overlay/`

## Yêu cầu

> "tôi muốn thêm chức năng tương tự https://github.com/AndyHazz/bookends.koplugin vào trong app của tôi"

## Nguồn tham chiếu là gì (đọc trước khi đoán)

`bookends.koplugin` **không phải** trang bìa đầu/cuối sách. Nó là hệ overlay text **cấu hình được, phủ
trực tiếp lên trang đọc** của KOReader:

| Thành phần | Quy mô |
| --- | --- |
| 6 vị trí neo (TL/TC/TR/BL/BC/BR), nhiều dòng mỗi vị trí | — |
| Token engine | ~120 token |
| Conditional `[if:…][else][/if]` với so sánh, `and/or/not`, ngoặc, lồng nhau | — |
| Inline format `[b]/[i]/[u]`, pluralisation `x(s)`, width limit `%t{N}`, `%spacer` | — |
| Thanh tiến độ: `%bar` nội dòng + 8 thanh full-width, 7 style, chapter ticks | — |
| Preset + gallery cộng đồng + auto-preset theo đuôi file | — |
| Line editor có live preview, per-line font/size/page-filter/nudge | — |

`main.lua` 153KB, `bookends_overlay_widget.lua` 127KB, `bookends_tokens.lua` 169KB. Port 1:1 là bất khả
thi; đây là **cài đặt lại ngữ nghĩa bằng Kotlin/Compose**, không dịch mã Lua.

## Quyết định kiến trúc

### 1. Tầng resolve nằm ở `:core:model`, không ở `:feature:reader`

Toàn bộ cú pháp (token, conditional, tokenizer) phụ thuộc **không** Android lẫn Compose, nên test chạy
bằng JUnit thường — không Robolectric, không Room, không chờ đồng hồ. Đồng hồ và múi giờ đi vào qua
`nowMillis`/`zoneId`, nên kết quả tái lập được tuyệt đối.

### 2. Resolver trả `Chunk`, không trả `String`

Đây là quyết định quan trọng nhất. `%bar` cần `Modifier.weight(1f)`, `%spacer` cần đẩy hai đầu, icon thì
nên dùng Material Icons thay vì glyph Nerd Fonts. Ba thứ đó không nhồi vừa một chuỗi.

```kotlin
sealed interface BookendsChunk {
    data class Text(text, bold, italic, uppercase, maxWidthDp) : BookendsChunk
    data class Icon(icon, description) : BookendsChunk
    data class ProgressBar(type, style, maxWidthDp) : BookendsChunk
    data object Spacer : BookendsChunk
}
```

`AnnotatedString` **không** dùng được cho việc này vì nó nằm trong `androidx.compose.ui.text` — kéo
Compose vào `:core:model`.

### 3. `BookendsViewModel` tách khỏi `ReaderViewModel`

`ReaderViewModel` đã 870 dòng và gánh điều phối engine + phiên đọc + TTS. Overlay chỉ *đọc* trạng thái.
Màn đọc đẩy vào đúng hai thứ nó sở hữu (`bookendsContext`, `bookendsSessionProgress`); mọi nguồn khác
(Room, phần cứng, đồng hồ) được gom trong `BookendsViewModel`.

### 4. Không lặp lại lỗi cài đặt reader

`tapZoneMode`/`pageTurnEffect`/`hapticsEnabled` được `SettingsViewModel` ghi vào
`booxbook_reader_prefs`, nhưng `ReaderViewModel` **không bao giờ đọc lại** — đổi ở tab Cài đặt không có
tác dụng gì trong màn đọc (bug có sẵn, ngoài phạm vi lần này). Bookends đi qua đúng một
`BookendsPreferencesManager` (`@Singleton`, `StateFlow`) cho cả hai phía.

## Lỗi phát hiện trong lúc tự soát

Subagent `code-reviewer` **không chạy được** (hết credit OpenRouter), nên phần review do tôi tự làm. Nó
tìm ra sáu vấn đề thật, đáng ghi lại vì đều thuộc loại test không bắt được nếu chỉ test đường hạnh phúc:

1. **`%book_time_left` in nguyên văn.** `putBookEstimates` được viết ra nhưng chưa bao giờ được gọi từ
   `resolveAll`. Tokenizer coi tên lạ là văn bản thường (để người dùng thấy lỗi gõ), nên hậu quả là chữ
   `%book_time_left` hiện lên trang đọc.

2. **Cả một lớp lỗi cùng dạng.** Từ (1) rút ra bất biến: *mọi token đã tài liệu hoá phải luôn có mặt
   trong bảng token, kể cả khi rỗng* — vì auto-hide dựa vào "resolve ra rỗng", còn "không đăng ký" lại ra
   văn bản. Đã thêm `registerChapterTokens()` và một test duyệt qua ~95 tên token trên `BookendsSnapshot()`
   rỗng, khẳng định không tên nào bị in nguyên văn.

3. **`chap_num` đếm sai với mục lục hai tầng.** Đếm theo "cấp ≤ N" thì sách 2 phần 24 chương hiện
   "chương 13/26". Nhưng `%chap_title_N` vẫn phải lấy theo "cấp ≤ N" (mục sâu nhất phủ vị trí đọc) vì đó
   là ngữ nghĩa bản gốc. Hai quy tắc khác nhau là **chủ ý**, đã ghi rõ trong `docs/reader.md` §3.8.

4. **`%chap_time_left` không hậu tố dùng cấp 1, `%chap_pct` dùng cấp sâu nhất.** Hai con số trên cùng một
   dòng nói về hai phạm vi. Đã thống nhất: không hậu tố = cấp sâu nhất.

5. **"Thêm dòng" trông như nút hỏng.** `withLines` lọc bỏ dòng rỗng (đó là cách xoá dòng), nên chèn
   `BookendsLine(format = "")` xong dòng biến mất ngay. Sửa: mở thẳng trình soạn thảo, `index = -1` nghĩa
   là thêm mới.

6. **Bịa token.** Tôi sinh ra `%chap_time_left_1_h` — bản gốc chỉ có `%chap_time_left_h` (không hậu tố) và
   `%chap_time_left_N_eta`. Đã bỏ biến thể `_N` của `_h`/`_m`. Bài học: khi port, kiểm lại từng tên token
   với tài liệu gốc thay vì suy rộng quy tắc cho tiện.

## Số trang: phát hiện ngoài dự kiến

`ReaderUiState` trước đây chỉ có `currentPage`/`totalPages`, và hai giá trị này **không cùng thang đo**
với EPUB: `currentPage` là `Locator.locations.position` (đếm theo trang), còn `totalPages` là
`readingOrder.size` (số mục thứ tự đọc). Nên `%page_num / %page_count` — token chủ lực của tính năng — sẽ
hiện những dòng như `42 / 12`.

Readium 3.1.1 có `EpubNavigatorFragment.PaginationListener.onPageChanged(pageIndex, totalPages, locator)`,
và hạ tầng đã có sẵn: `ReadiumReaderEngine.createFragmentFactory(…, paginationListener)` được khai báo,
truyền xuống `EpubNavigatorFactory`, nhưng **chưa nơi nào truyền vào**. Chỉ cần nối dây.

Thêm hai trường mới `displayPageNumber`/`displayPageCount` thay vì sửa ngữ nghĩa `currentPage`/`totalPages`:
cặp cũ còn được `FloatingReaderToolbar` dùng cho ánh xạ tìm kiếm, đổi nó là một thay đổi khác có nhu cầu
test riêng. Hệ quả: thanh công cụ **vẫn** hiện cặp logic cũ — đã ghi nhận là việc còn lại, không sửa nửa
vời.

## Đợt 2 — hoàn thiện

Sau khi nghiệm thu đợt đầu, các phần còn lại được làm nốt:

### Sửa gốc rễ bộ theo dõi phiên đọc

`flushReadingSession` được gọi mỗi 60 giây bởi nhịp định kỳ **và** tự cộng thêm thời gian trôi qua kể từ tương
tác cuối. Hệ quả: để màn đọc mở mà không làm gì vẫn sinh ra 60 giây "thời gian đọc" mỗi phút — đó là nguồn
thật của con số 4,5 giờ cho 11 trang.

Sửa: tách `accumulateActiveTime()` (chỉ `recordUserInteraction` và `pauseReadingSession` gọi) khỏi
`flushReadingSession` (chỉ ghi phần đã cộng dồn). Thêm `clock: () -> Long` làm khe cắm thời gian vì test điều
khiển được thời gian ảo của coroutine scheduler nhưng **không** điều khiển được `System.currentTimeMillis()`.
Bốn test mới khoá hành vi: mở mà không tương tác → 0 giây; hai tương tác cách 30 giây → 30 giây; nghỉ ba tiếng
→ tối đa ngưỡng; tạm dừng → không tính thời gian ở nền.

### Phase 5 — metadata, migration, đánh giá

Xem `phase-05-metadata-enrichment.md`. Điểm quan trọng nhất: **bỏ `fallbackToDestructiveMigration()`** và viết
`MIGRATION_2_3` tay. Đã kiểm trên DB v2 thật của người dùng — `DB version upgrading from 2 to 3`, sách, tiến độ,
phiên đọc và chú thích còn nguyên.

### Phase 3 — thanh WAVE, định vị ba tầng

- Thêm `WAVE` (6 kiểu thanh) dùng ngôn ngữ hình ảnh M3 Expressive của ứng dụng.
- `BookendsGroup.extraMargin*Dp` (lề riêng theo vùng) + `BookendsLine.nudgeXDp/nudgeYDp` (tinh chỉnh theo dòng)
  — tầng 2 và 3 của hệ định vị.
- `truncationGapDp` từ chỗ không được dùng trở thành khoảng cách thật giữa hai vùng cùng hàng.
- **Không làm `RADIAL`**, kèm lý do ngay trong enum: vòng tròn không biểu diễn được trên một thanh tuyến tính.
  Thà 6 kiểu cài đặt tử tế hơn 7 kiểu với một kiểu giả.

### Phase 4 — trình soạn thảo

- `BookendsTokenCatalogue`: danh mục ~70 token kèm mô tả và ví dụ, là **tài liệu duy nhất** về token. Có test
  duyệt toàn bộ danh mục để chốt rằng nó không quảng cáo token không tồn tại.
- Bảng chọn "Chèn token" chèn tại **vị trí con trỏ** (đổi ô nhập sang `TextFieldValue`).
- Sắp xếp dòng lên/xuống, lề riêng theo vùng, nudge theo dòng.
- Mục Bookends ở tab Cài đặt.

### Đã kiểm trên máy đợt 2

| Kiểm tra | Kết quả |
|---|---|
| Migration 2 → 3 | `DB version upgrading from 2 to 3`; sách + tiến độ + phiên + chú thích còn nguyên |
| Backfill metadata | `%lang` = `en`, `%size` = `488.7 KB` |
| Đánh giá | chạm 4 sao → `4/5`, `%rating` = `★★★★☆` trong màn đọc |
| Dòng token mới | `en\|reading\|488.7 KB\|★★★★☆` |
| Thanh WAVE + thanh full-width có mốc chương | render đúng |
| Tab Cài đặt → Bookends | mở được sheet |
| Trình soạn thảo | nút lề riêng / lên-xuống / chèn token đều có; bảng chọn token hiện đủ nhóm |
| `logcat -b crash` | trống suốt phiên |

### Còn lại

1. **Preset gallery** — chưa có nguồn preset từ xa để trỏ tới.
2. **Cử chỉ đổi preset / ẩn hiện nhanh** — phải sửa đường vào của tap-zone trong `EpubReaderContainer`.
3. **`RADIAL`** — cần một component tròn riêng.
4. **`%chap_pages`/`%chap_read` là ước lượng** — sai số ±1 trang.
5. **Token `%opened`, `%quote`, `%file_num`, `%file_count`** trả rỗng.
6. **Chưa kiểm CBZ/AZW3 trên máy** — máy chỉ có EPUB.
7. **Thanh công cụ màn đọc** vẫn hiện cặp số trang logic cũ.

## Nghiệm thu đợt 1

Cài `app-debug.apk` lên Galaxy S24 FE (Android 15), mở sách *Tù Nhân*, bật Bookends, đổi preset, kiểm tra
preview và lớp che. `logcat -b crash` trống suốt phiên.

Kết quả preset "Đầy đủ": `01.` (trên trái, nghiêng) · `3%` (trên phải) · `Tù Nhân` (dưới trái) ·
`11 / 297` + thanh `%bar` (dưới giữa) · *(dòng dưới phải tự ẩn — xem lỗi 3)*.

### Bốn lỗi chỉ máy thật mới bắt được

166 unit test xanh **không** nói được gì về bốn lỗi này — tất cả đều nằm ở chỗ nối giữa các tầng, chỉ hiện ra
khi có sách thật, mục lục thật, dữ liệu thống kê thật và màn hình thật.

| # | Triệu chứng | Nguyên nhân gốc | Cách sửa |
| --- | --- | --- | --- |
| 1 | Preview báo "Mở một cuốn sách…" dù đang đọc sách | `snapshot` chỉ dựng khi overlay **đã bật** | Luôn dựng snapshot; `preset == null` mới quyết định vẽ |
| 2 | `%page_num / %page_count` → `11 / 8` | `PaginationListener.totalPages` là số trang **trong từng tệp chương**, không phải toàn sách | Đọc `Publication.positions().size` trong nền |
| 3 | `%book_time_left` → `118h 35m còn lại` | Bộ theo dõi phiên đọc tích 4,5 giờ cho 11 trang → 25 phút/trang | Hai chốt từ chối ước lượng, **không** kẹp về ngưỡng |
| 4 | `%chap_title` → `HIỆN TẠI` trong khi thanh công cụ hiện `01.` | Đang ở front matter, **trước** mục lục đầu tiên; `titleAt` rơi về `scoped.first()` và **bịa** ra chương đầu | `%chap_title` lấy `Locator.title` của engine; `titleAt` trả rỗng khi đứng trước mục lục |

### Bài học lớn nhất của lần này

Tôi đã "sửa" lỗi số trang bằng `PaginationListener` **dựa trên suy đoán về ngữ nghĩa API** — và biến `11 / 93`
thành `11 / 8`, tức là **làm cho tệ hơn**. Nếu không cài lên máy thì lỗi này đã đi vào commit kèm một comment
rất tự tin. Kiểm tra trên thiết bị không phải bước xác nhận cuối cùng; nó là một **nguồn thông tin** không gì
thay thế được.

### Đã giao

`:core:model` (`com.booxbook.core.model.bookends`, 9 file):
`BookendsPreset` · `BookendsSnapshot` · `BookendsValue` · `BookendsChunk` · `BookendsDuration` ·
`BookendsConditional` · `BookendsTokenizer` · `BookendsTokens` · `BookendsFormatter` · `BookendsSettingsCodec`

`:feature:reader`:
`BookendsPreferencesManager` · `BookendsChapterIndexFactory` · `BookendsSnapshotAssembler` ·
`BookendsDeviceState` · `BookendsViewModel` · `BookendsOverlay` · `BookendsBars` · `BookendsSettingsSheet`

Nối dây: `ReaderScreen` · `ReaderViewModel` · `ReaderUiState` · `EpubReaderContainer` · `ReaderSettingsSheet`

## Chưa giao — không được coi là xong

| Việc | Ghi chú |
| --- | --- |
| Phase 5 (series/description/rating + `Migration`) | `Book` chưa có trường; thêm cột cần `Migration` thật vì `DatabaseModule` đang dùng `fallbackToDestructiveMigration()`. Token tương ứng hiện trả rỗng. |
| Mục Bookends ở tab Cài đặt | Hiện chỉ mở từ `ReaderSettingsSheet`. |
| `WAVE`/`RADIAL`, smart ellipsis, margin 2/3 tầng, nudge | Phase 3. |
| Bảng chọn token/icon, sắp xếp dòng, preset gallery | Phase 4. |
| `%chap_pages`/`%chap_read` chính xác | Hiện là ước lượng `spanLength × pageCount`; `Publication.positionsByReadingOrder()` cho số thật. |
| Chưa kiểm CBZ/AZW3 và màn e-ink | Máy nghiệm thu là điện thoại AMOLED. |
| Gốc rễ dữ liệu phiên đọc bị phồng | Bookends chỉ từ chối ước lượng từ nó, không sửa bộ theo dõi. |

## Ghi công

Ý tưởng, ngữ nghĩa token và cấu trúc preset dựa trên **bookends.koplugin** của AndyHazz (GPL-3.0).
Bản này là cài đặt lại bằng Kotlin/Compose, không sao chép mã Lua.

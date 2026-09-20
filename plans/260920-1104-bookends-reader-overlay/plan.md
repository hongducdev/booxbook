# Bookends — Overlay đọc sách cấu hình được (port từ bookends.koplugin)

**Nguồn tham chiếu:** [AndyHazz/bookends.koplugin](https://github.com/AndyHazz/bookends.koplugin) v5.25.0 (KOReader, Lua, GPL-3.0)
**Ngày:** 2026-09-20 · **Trạng thái:** Phase 1–5 xong, đã nghiệm thu trên máy thật (Galaxy S24 FE, Android 15). Còn 4 mục nhỏ ghi ở mục "Chưa giao"
**Mode:** interactive (cook workflow)

---

## 1. Vấn đề

BooxBook chưa có lớp thông tin nào phủ trên trang đọc. Muốn xem tiến độ, thời gian còn lại, tên
chương… người đọc phải mở thanh công cụ (che mất trang) hoặc thoát ra màn Thống kê.

`bookends.koplugin` giải quyết đúng việc này trên KOReader: một hệ overlay text **cấu hình bằng
chuỗi định dạng + token**, neo ở 6 vị trí quanh trang đọc, có điều kiện hiển thị, thanh tiến độ, và
preset. Đây là bản port ý tưởng đó sang Compose, không phải bản dịch Lua.

## 2. Phạm vi

### Trong phạm vi
- Chuỗi định dạng + engine token thuần Kotlin (~70 token), chạy được unit test không cần Android.
- Khối điều kiện `[if:…]…[else]…[/if]` với so sánh, `and`/`or`/`not`, ngoặc, lồng nhau.
- Định dạng nội dòng `[b]`/`[i]`/`[u]`, pluralisation `highlight(s)`, giới hạn bề rộng `%title{200}`,
  `%spacer` co giãn, auto-hide dòng rỗng.
- 6 vị trí neo × nhiều dòng, mỗi dòng có style/size/page-filter riêng.
- `%bar` nội dòng + tối đa 8 thanh tiến độ full-width (anchor/fill/style/ticks/thickness/inset).
- Preset: nhiều preset, preset đang dùng, auto-preset theo đuôi file (ẩn overlay cho CBZ).
- Màn cấu hình Bookends riêng (mở từ sheet cài đặt trong reader và từ tab Cài đặt).

### Ngoài phạm vi (ghi nhận, không làm)
- Gallery preset cộng đồng + tải preset qua mạng.
- Nerd Fonts glyph picker (thay bằng Material Icons — bản địa hoá Android).
- Token plugin ngoài (`%plugin_content`), token phần cứng KOReader (`%warmth`, `%mem`, `%disk`).
- Bar style `WAVE` và `RADIAL` của bản gốc: Phase 3 chỉ làm `SOLID/BORDER/ROUND/METRO/HOLLOW`.

## 3. Kiến trúc

```
core:model  com.booxbook.core.model.bookends      ← thuần Kotlin, không phụ thuộc Android/Compose
  BookendsPreset.kt        preset, line, bar layer, enum vị trí/style
  BookendsSnapshot.kt      ảnh chụp dữ liệu đầu vào để resolve token
  BookendsChunk.kt         đầu ra đã resolve: Text | Icon | ProgressBar | Spacer
  BookendsTokenizer.kt     quét chuỗi: token, [b]/[i]/[u], %bar, {N}, pluralisation
  BookendsConditional.kt   parser + evaluator biểu thức [if:…]
  BookendsFormatter.kt     điểm vào duy nhất: format() → List<BookendsChunk>
  BookendsDuration.kt      định dạng thời lượng/ngày giờ + strftime rút gọn

feature:reader
  BookendsPreferencesManager.kt   @Singleton, SharedPreferences + JSON, StateFlow
  BookendsHostState.kt            gom snapshot từ ReaderUiState + Room + thiết bị
  components/BookendsOverlay.kt   6 vùng neo, đặt trên canvas dưới chrome
  components/BookendsLineView.kt  chunk → Compose (Row + AnnotatedString + bar + spacer)
  components/BookendsProgressBar.kt  %bar nội dòng + thanh full-width
  components/BookendsSettingsSheet.kt  màn cấu hình + live preview
```

**Luồng dữ liệu:** `ReaderViewModel` dựng `BookendsSnapshot` → `BookendsFormatter.format()` (thuần,
test được) → `BookendsOverlay` vẽ. Không có I/O trong lớp formatter.

## 4. Các phase

| Phase | Nội dung | Trạng thái |
|-------|----------|-----------|
| [01](phase-01-domain-and-token-engine.md) | Domain model + token engine + conditional parser | ✅ xong |
| [02](phase-02-overlay-rendering.md) | Overlay 6 vị trí, preset store, màn cấu hình, wiring reader | ✅ xong (kể cả mục ở tab Cài đặt) |
| [03](phase-03-progress-bars-and-layout.md) | Thanh full-width + mốc chương, 6 kiểu thanh, định vị 3 tầng | ✅ xong (trừ `RADIAL` — xem lý do trong enum) |
| [04](phase-04-line-editor-and-presets.md) | Trình soạn thảo có bảng chọn token, sắp xếp dòng, preset, auto-rule | ✅ xong (trừ preset gallery — chưa có nguồn) |
| [05](phase-05-metadata-enrichment.md) | Series/tags/description + `Migration(2,3)` thật + bảng đánh giá | ✅ xong |

### Đã giao

- `:core:model` — 11 file trong `com.booxbook.core.model.bookends`: ~70 token, conditional parser, tokenizer,
  danh mục token, codec JSON. **61 unit test**.
- `:core:database` — metadata sách (5 cột), `book_reviews`, `MIGRATION_2_3` viết tay, parse OPF mở rộng,
  `backfillMetadata()`.
- `:feature:reader` — 5 file trong `.../bookends` + 3 component Compose, nối vào `ReaderScreen`,
  `ReaderViewModel`, `ReaderUiState`, `ReaderSettingsSheet`. **74 unit test**.
- `:feature:library` — mục "Nhật ký đọc" (đánh giá + cảm nhận + mốc đọc xong) trong `BookDetailScreen`.
- `:app` — mục Bookends ở tab Cài đặt.
- Sửa gốc rễ bộ theo dõi phiên đọc: nhịp ghi định kỳ không còn tự cộng thời gian nhàn rỗi.

### Đã nghiệm thu trên máy thật

Galaxy S24 FE (Android 15), sách *Tù Nhân* (EPUB 488 KB, mục lục 87 mục phẳng, thứ tự đọc 93 mục):

| Kiểm tra | Kết quả |
|---|---|
| Migration 2 → 3 | `SQLiteOpenHelper: DB version upgrading from 2 to 3`, không crash, sách + tiến độ + phiên đọc + chú thích còn nguyên |
| Bảng/cột mới | `book_reviews`, `series_index`, `description`, `language`, `tags`, `rating`, `finished_at` đều có |
| Backfill metadata | `%lang` hiện `en`, `%size` hiện `488.7 KB` — dữ liệu thật từ OPF |
| Đánh giá | Chạm 4 sao → nhãn `4/5`, `%rating` hiện `★★★★☆` trong màn đọc |
| Token mới trên overlay | `en\|reading\|488.7 KB\|★★★★☆` |
| Thanh WAVE + thanh full-width có mốc chương | render đúng ở đáy màn hình |
| Tab Cài đặt → Bookends | mở được sheet, hiện "Đang bật" |
| Trình soạn thảo | nút "Lề riêng cho vùng…", "Đưa dòng lên/xuống", "Chèn token" đều có; bảng chọn token hiện đủ nhóm |
| `logcat -b crash` | trống suốt phiên |

### Lỗi tìm thấy khi nghiệm thu trên máy (không unit test nào bắt được)

| # | Triệu chứng | Nguyên nhân |
| --- | --- | --- |
| 1 | Preview báo "Mở một cuốn sách…" dù đang đọc sách | `snapshot` chỉ được dựng khi overlay **đã bật** → người dùng phải bật trước rồi mới thấy mình vừa bật cái gì |
| 2 | `%page_num / %page_count` → `11 / 8` | `PaginationListener.totalPages` là số trang **trong từng tệp chương**, không phải toàn sách |
| 3 | `%book_time_left` → `118h 35m còn lại` | Bộ theo dõi phiên đọc tích 4,5 giờ cho 11 trang → 25 phút/trang |
| 4 | `%chap_title` → `HIỆN TẠI` trong khi thanh công cụ hiện `01.` | Vị trí đang ở front matter, **trước** mục lục đầu tiên; `titleAt` rơi về `scoped.first()` và **bịa** ra chương đầu |

Sửa lần lượt: (1) luôn dựng snapshot, chỉ dùng `preset == null` để quyết định vẽ; (2) đọc
`Publication.positions().size` trong nền; (3) hai chốt từ chối ước lượng (< 5 trang mẫu, ngoài 2–300
  giây/trang) — **không** kẹp về ngưỡng; (4) `%chap_title` lấy `Locator.title` của engine, và `titleAt` trả
rỗng khi đứng trước mục lục.

**Bài học:** cả bốn lỗi đều nằm ở chỗ nối giữa các tầng — chúng chỉ hiện ra khi có sách thật, mục lục thật,
dữ liệu thống kê thật và màn hình thật. Unit test xanh không nói được gì về chúng.

### Chưa giao (không được coi là xong)

1. **Preset gallery** — bản gốc tải preset từ một repo GitHub; chưa có nguồn nào để trỏ tới.
2. **Cử chỉ đổi preset / ẩn hiện nhanh** — sẽ phải sửa đường vào của tap-zone trong `EpubReaderContainer`,
   tách riêng vì nó đụng vào logic lật trang đang chạy tốt.
3. **`RADIAL`** — đã khai báo là không làm kèm lý do trong `BookendsBarStyle`: vòng tròn không biểu diễn được
   trên một thanh tuyến tính.
4. **`%chap_pages`/`%chap_read` vẫn là ước lượng** — sai số ±1 trang với mục lục phẳng; dùng
   `positionsByReadingOrder()` sẽ chính xác nhưng thêm một lời gọi API và một đường truyền tham số mới.
5. **Token `%opened`, `%quote`, `%quote_source`, `%file_num`, `%file_count`** trả rỗng — dữ liệu chưa được
   theo dõi; dòng chứa chúng tự ẩn.
6. **Thanh công cụ trong màn đọc** vẫn hiển thị cặp số trang logic cũ (xem `docs/reader.md` §3.8).
7. **Chưa kiểm với CBZ/AZW3 trên máy** — máy chỉ có EPUB; nhánh CBZ được phủ bằng unit test
   (`BookendsChapterIndexFactoryTest` cho `page://`, `ReaderViewModelTest` cho luồng mở CBZ).

### Lỗi phát hiện trong lúc tự soát (subagent review không chạy được)

- `putBookEstimates` chưa được gọi → `%book_time_left` in nguyên văn.
- Token quên đăng ký bị in nguyên tên thay vì để dòng tự ẩn → thêm `registerChapterTokens()` + test khoá danh
  sách token đã tài liệu hoá.
- `chap_num`/`chap_count` đếm theo "cấp ≤ N" → sửa thành "đúng cấp N".
- `%chap_time_left` không hậu tố dùng cấp 1 trong khi `%chap_pct` dùng cấp sâu nhất → thống nhất về cấp sâu nhất.
- "Thêm dòng" chèn dòng rỗng rồi bị `withLines` lọc bỏ (nút trông như hỏng) → mở thẳng trình soạn thảo.
- `%chap_time_left_1_h` là token tự nghĩ ra, không có trong bản gốc → bỏ biến thể `_N` của `_h`/`_m`.

## 5. Rủi ro

| Rủi ro | Mức | Xử lý |
|--------|-----|-------|
| `DatabaseModule` dùng `fallbackToDestructiveMigration()` → bump version là mất DB người dùng | **Cao** | Phase 5 viết `Migration` thật, KHÔNG bump version trần |
| Token đọc dữ liệu Room mỗi lần vẽ → giật khi lật trang | Trung bình | Snapshot chỉ dựng lại khi state đổi; số liệu thống kê gom 1 lần mỗi phiên |
| Overlay che chữ ở màn nhỏ | Trung bình | Auto-hide dòng rỗng + margin cấu hình được + preset tối giản mặc định |
| Tiếng Việt: `uppercase` phải theo locale | Thấp | `uppercase(Locale.getDefault())`, không dùng `toUpperCase()` |
| `ReaderPreferences` của tab Cài đặt không được reader đọc (bug có sẵn) | Thấp | Bookends không lặp lại lỗi này: đọc/ghi qua `BookendsPreferencesManager` |

## 6. Tiêu chí hoàn thành

- [x] `BookendsFormatter` có unit test phủ token, conditional, pluralisation, width limit, auto-hide.
- [x] Bật overlay trong reader → thấy đúng preset, đổi preset cập nhật ngay — **đã xác nhận trên Galaxy S24 FE**.
- [x] Dòng có token rỗng tự ẩn, không để lại khoảng trắng thừa — có test riêng, và xác nhận trên máy qua
  `[if:book_time_left]` tự ẩn khi ước lượng bị từ chối.
- [x] `./gradlew testDebugUnitTest :app:assembleDebug` xanh — **179 test, 0 lỗi**.
- [x] `docs/reader.md` §3.8 và `docs/database.md` cập nhật; journal ghi lại.
- [x] Migration 2 → 3 giữ nguyên dữ liệu người dùng — đã xác nhận trên DB thật.
- [ ] Code review độc lập — subagent `code-reviewer` **không chạy được** (hết credit OpenRouter). Nghiệm thu
  trên máy thật đã bắt được 4 lỗi mà tự soát và unit test bỏ sót.

## 7. Ghi công

Ý tưởng, ngữ nghĩa token và cấu trúc preset dựa trên **bookends.koplugin** của AndyHazz (GPL-3.0).
Bản này là **cài đặt lại bằng Kotlin/Compose**, không sao chép mã Lua.

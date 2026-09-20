# Lề trang & viền khung vùng đọc

**Ngày:** 2026-09-20 · **Trạng thái:** xong, đã nghiệm thu trên Galaxy S24 FE
**Mode:** cook interactive · người dùng đã chốt hướng **B + C** (lề bốn phía + viền khung nhìn thấy được)

---

## Kết quả

| Việc | Trạng thái |
|---|---|
| Viền khung: bật/tắt, độ dày, bo góc, khoảng cách, 3 kiểu nét, 3 màu | ✅ |
| Lề trang: **trên riêng, dưới riêng, trái+phải chung** | ✅ padding Compose, áp cho cả ba định dạng |
| Cài đặt **dính** qua lần mở sách sau | ✅ sửa lỗi có sẵn (không chỉ cho lề) |
| Bookends neo theo màn đọc, không trôi theo lề | ✅ |
| Nền trang cùng màu nền xung quanh | ✅ sửa lỗi có sẵn |

`./gradlew clean testDebugUnitTest :app:assembleDebug` — **202 test, 0 lỗi**.

## Bốn lần thử cho phần lề — lần thứ tư mới đúng

| Lần | Cách làm | Kết quả trên máy |
|---|---|---|
| 1 | Padding Compose, không báo gì cho Readium | Đổi lề giữa phiên: **chữ chồng lên nhau, tràn phải**. Mở sách mới thì đúng → padding đúng, Readium không biết vùng đọc đã hẹp lại |
| 2 | Padding + dựng lại `EpubNavigatorFragment` | Dàn lại được nhưng **canvas trắng** |
| 3 | `pageMargins` của Readium | Đúng, nhưng Readium chỉ có **một** hệ số cho cả bốn phía → không đủ cho yêu cầu trên/dưới riêng |
| 4 | Padding + `onSizeChanged` → `submitPreferences` | Đúng, và đủ ba mức |

**Mắt xích thiếu ở lần 1 chỉ là một dòng:** báo cho Readium biết vùng đọc đã đổi kích thước. Lần 2 là lúc đáng lẽ
phải dừng và đặt câu hỏi về kiến trúc — dựng lại navigator là chống lại framework, trong khi thiếu đúng một dòng
báo cho nó biết.

`javap` xác nhận `EpubPreferences` chỉ có một `pageMargins: Double?`, và không có `invalidatePagination` hay
tương đương trên `EpubNavigatorFragment`/`R2ViewPager`. `submitPreferences` là API công khai duy nhất khiến
Readium dàn lại.

**Yêu cầu của người dùng** (sau lần thử đầu): *"tôi muốn chỉnh độc lập trên và dưới và có thể trái phải chỉnh
chung"* — nên `marginLeftDp`/`marginRightDp` gộp thành `marginHorizontalDp`, và bản cũ vẫn đọc lại được giá trị
`margin_left_dp` để cài đặt đang có không bị đặt lại về 0.

## Hai lỗi có sẵn được sửa kèm

1. **Cài đặt reader không dính.** `SettingsViewModel` ghi `booxbook_reader_prefs` còn `ReaderViewModel` không bao
giờ đọc lại → đổi vùng chạm / hiệu ứng lật trang / rung ở tab Cài đặt không có tác dụng khi đang đọc. Giờ cả hai
phía dùng `ReaderPreferencesManager`, và **tên khoá cũ được giữ nguyên** để cài đặt đang có không mất.
2. **Vệt lệch màu quanh trang.** Readium vẽ nền theo `Theme` của nó, Compose vẽ nền theo bảng màu của app.
`ReaderThemePalette` + `EpubPreferencesFactory` ghi đè `backgroundColor`/`textColor` → một định nghĩa cho hai phía.

`EpubReaderEngine` và `Azw3ReaderEngine` trước đây **sao chép** nhau hàm `buildEpubPreferences` — đúng kiểu để một
bản được sửa còn bản kia thì không. Đã gộp vào `EpubPreferencesFactory`.

## Kiểm trên máy (Galaxy S24 FE)

| Kiểm tra | Kết quả |
|---|---|
| Nền trang vs nền xung quanh | đồng nhất, không còn vệt |
| Đổi lề trên 61 dp + trái/phải 61 dp + dưới 22,5 dp **giữa phiên** | dàn lại đúng ngay, không chồng chữ |
| Bookends khi đổi lề | đứng yên ở mép màn hình |
| Viền khung nét đứt, màu nhấn | render đúng, bo góc theo cấu hình |
| Cài đặt ghi xuống `shared_prefs` | `margin_top_dp`, `margin_bottom_dp`, `margin_horizontal_dp`, `frame_*` đúng |
| `logcat -b crash` | trống |

## Ghi chú kỹ thuật

- Viền **không chiếm chỗ** — chỉ vẽ lên trên vùng đọc, nên lề và viền độc lập nhau.
- Viền vẽ bằng `Canvas` + `Stroke(pathEffect)`: `Modifier.border` không có kiểu nét đứt.
- Chấm = gạch dài `0.01f` + `StrokeCap.Round`.
- `BookendsOverlay` nằm ngoài hộp padding; `ReadingFrameLayer` và `TapZonePreviewOverlay` nằm trong.
- `ReaderPreferences.pageMargins` vẫn tồn tại nhưng **không dùng** — Readium cần một giá trị, còn lề thật là ba
  trường `margin*Dp`.

## Còn lại

- Tách riêng lề trái và lề phải: cần thêm một trường và một thanh trượt nữa — không có rào cản kỹ thuật, chỉ là
  chưa cần.
- Viền khung không tự bám theo lề: viền vẽ ở mép vùng đọc, chữ nằm sau lề; `insetDp` chỉnh tay được.

---

## 1. Vấn đề

Người đọc không chỉnh được khoảng chừa quanh vùng đọc, và không có cách nào để *thấy* một đường viền phân
cách trang sách với nền màn hình.

Hiện trạng đã khảo sát:

| Thứ | Trạng thái |
|---|---|
| `ReaderPreferences.pageMargins` | có trong model, **đã truyền vào Readium** (`EpubReaderEngine.kt:143`) nhưng không có UI |
| UI chỉnh lề | không có |
| Lưu cài đặt reader | **không có** — `ReaderViewModel` không đọc/ghi `booxbook_reader_prefs` |
| CBZ | ảnh tràn viền (`ContentScale.FillWidth`, `PaddingValues(0.dp)`) |
| Viền khung | chưa có gì |

## 2. Ràng buộc kỹ thuật (đã kiểm bằng `javap`, không đoán)

- `EpubPreferences.pageMargins` là **một `Double` duy nhất**, áp đều bốn phía. Không có type `PageMargins`
  riêng, không có API per-side. → Muốn bốn phía riêng thì phải padding ở tầng Compose.
- Padding ở Compose làm **WebView đổi kích thước → Readium dàn trang lại → tổng số trang đổi**. Vì vậy
  `%page_count` phải được tính lại, nếu không overlay hiện tổng số trang cũ.
- `Modifier.border` **không** vẽ được nét đứt. Viền khung phải tự vẽ bằng `drawWithContent` + `Stroke(pathEffect)`.

## 3. Quyết định thiết kế

1. **Lề bốn phía làm ở tầng Compose**, không dùng `pageMargins` của Readium. Nhờ vậy **một cơ chế áp cho cả ba
   định dạng** (EPUB, AZW3, CBZ) thay vì EPUB có một kiểu còn CBZ không có gì. `pageMargins` giữ nguyên giá
   trị mặc định và được ghi chú là không dùng cho việc này.
2. **`ReaderPreferencesManager` là nguồn sự thật duy nhất** cho cài đặt hiển thị của màn đọc. Việc này sửa
   luôn lỗi có sẵn: `SettingsViewModel` ghi `booxbook_reader_prefs` nhưng `ReaderViewModel` không bao giờ đọc
   lại, nên vùng chạm / hiệu ứng lật trang / rung đổi ở tab Cài đặt không có tác dụng gì khi đọc.
   Giữ nguyên tên khoá SharedPreferences cũ để cài đặt đang có của người dùng không mất.
3. **Viền khung vẽ bằng `drawWithContent` + `Stroke(pathEffect)`**, không dùng `Modifier.border`. Ba kiểu nét:
   liền, đứt, chấm.
4. **Màu viền theo bảng chọn nhỏ, không dùng color picker tự do.** Trên màn e-ink, một màu tuỳ ý dễ ra
   không đủ tương phản. Ba lựa chọn: `AUTO` (tương phản với nền đọc hiện tại), `ACCENT` (màu nhấn của app),
   `WARM` (nâu giấy).
5. **Overlay Bookends nằm trong cùng vùng đã chừa lề**, để nó neo theo *trang* chứ không theo màn hình. Nếu
   không thì tăng lề xong, chữ overlay sẽ lệch ra ngoài trang.
6. **Tính lại `positions()` khi lề đổi**, có debounce, và đặt `displayPageCount = 0` trong lúc chờ để token tự
   ẩn thay vì hiện tổng số trang cũ.
7. **UI đặt trong `ReaderSettingsSheet`**, mục "Lề & viền trang". Đây là chỗ người đọc đang nhìn thấy trang
   nên chỉnh xong thấy ngay kết quả phía sau sheet.

## 4. Các phase

| Phase | Nội dung |
|---|---|
| 01 | `ReaderPreferencesManager` + nối vào `ReaderViewModel` và `SettingsViewModel` (sửa lỗi lưu) |
| 02 | Model: bốn lề + `ReadingFrame` trong `ReaderPreferences` |
| 03 | Render: padding quanh vùng đọc + `Modifier.readingFrame` |
| 04 | Tính lại `positions()` khi lề đổi |
| 05 | UI trong `ReaderSettingsSheet` + xem trước ngay trên trang thật |
| 06 | Test, kiểm trên máy, cập nhật `docs/reader.md` |

## 5. Rủi ro

| Rủi ro | Xử lý |
|---|---|
| Padding làm Readium dàn trang lại → `%page_count` cũ | Phase 04: tính lại có debounce, reset về 0 trong lúc chờ |
| Vùng chạm lệch sau khi chừa lề | Vùng chạm tính theo kích thước view của canvas (`EpubReaderContainer` dùng `view.width`), nên tự co theo; **phải kiểm trên máy** |
| Đổi nguồn cài đặt làm mất cài đặt cũ của người dùng | Giữ nguyên tên khoá SharedPreferences cũ; đọc kèm giá trị mặc định |
| Viền đè lên chữ ở lề nhỏ | `insetDp` cho phép đẩy viền ra ngoài; mặc định dương |
| Nét đứt ở màn e-ink bị răng cưa | Dùng `StrokeCap.Round` cho kiểu chấm; kiểm trên máy |

## 6. Tiêu chí hoàn thành

- [ ] Bốn thanh trượt lề hoạt động, áp cho cả EPUB và CBZ.
- [ ] Bật viền khung → thấy đường viền; đổi độ dày / bo góc / kiểu nét / màu đều ăn ngay.
- [ ] Cài đặt **dính** qua lần mở sách sau (đây là phần sửa lỗi lưu).
- [ ] Đổi vùng chạm ở tab Cài đặt → có tác dụng trong màn đọc (lỗi cũ đã sửa).
- [ ] `%page_count` đúng sau khi đổi lề.
- [ ] `./gradlew testDebugUnitTest :app:assembleDebug` xanh.
- [ ] Kiểm trên Galaxy S24 FE, không crash.

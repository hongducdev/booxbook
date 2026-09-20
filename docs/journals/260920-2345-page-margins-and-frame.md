# Journal Entry: Lề trang & viền khung vùng đọc
**Date:** 2026-09-20
**Module:** `:core:engine`, `:feature:reader`, `:app`
**Status:** Xong, đã nghiệm thu trên Galaxy S24 FE — 201 unit test ✓, không crash
**Plan:** `plans/260920-1310-page-margins-and-frame/plan.md`

## Yêu cầu

> "bổ sung giúp tôi setting để chỉnh các viền của trang sách"
> → chốt hướng **B + C**: lề bốn phía + viền khung nhìn thấy được.

Sau khi thử lần đầu, người dùng phản hồi chính xác hai vấn đề:

> "chỉnh padding nhưng mà nền với nền reader đang không cùng màu. và vị trí của bookend là cố định chỉnh.
> chỉ chỉnh padding cho trang sách"

Ba yêu cầu đó định hình luôn cách sửa: padding chỉ cho **trang**, nền phải **cùng màu**, Bookends phải **đứng yên**.

## Ba lần thử cho phần lề, và lần thứ tư mới đúng

Đây là phần đáng ghi lại nhất.

| Lần | Cách làm | Kết quả trên máy |
|---|---|---|
| 1 | Padding Compose, không báo gì cho Readium | Đổi lề **giữa phiên**: chữ chồng lên nhau, tràn phải. Mở sách mới thì đúng hoàn toàn → padding đúng, nhưng pager của Readium giữ bề rộng trang cũ |
| 2 | Padding + dựng lại `EpubNavigatorFragment` để ép dàn lại | Dàn lại được, nhưng để lại **canvas trắng** |
| 3 | `EpubPreferences.pageMargins` | Đúng, nhưng Readium chỉ có **một** hệ số cho cả bốn phía → không đủ |
| 4 | Padding + `onSizeChanged` → `submitPreferences` | Đúng, và đủ ba mức |

**Mắt xích thiếu ở lần 1 chỉ là một dòng.** Padding làm WebView đổi kích thước, nhưng Readium không tự biết; gọi
`submitPreferences` sau mỗi lần đổi kích thước là đủ. Lần 2 là lúc đáng lẽ phải dừng và đặt câu hỏi về kiến trúc —
dựng lại navigator là chống lại framework, trong khi thiếu đúng một dòng báo cho nó biết vùng đọc đã đổi.

`javap` xác nhận `EpubPreferences` chỉ có một `pageMargins: Double?`, và không có `invalidatePagination` hay
tương đương trên `EpubNavigatorFragment`/`R2ViewPager`. `submitPreferences` là API công khai duy nhất.

### Yêu cầu sau đó của người dùng

> "tôi muốn chỉnh độc lập trên và dưới và có thể trái phải chỉnh chung"

Nên `marginLeftDp`/`marginRightDp` gộp thành `marginHorizontalDp`, và bản cũ vẫn **đọc lại** `margin_left_dp` để cài
đặt đang có của người dùng không bị đặt lại về 0 — có test riêng khoá điều đó.

## Hai lỗi có sẵn được sửa kèm

### 1. Cài đặt reader không dính

`SettingsViewModel` ghi thẳng `booxbook_reader_prefs`, còn `ReaderViewModel` **không bao giờ đọc lại**. Nghĩa là
đổi vùng chạm / hiệu ứng lật trang / rung ở tab Cài đặt không có tác dụng gì khi đang đọc. Lỗi này có từ trước,
tôi đã ghi nhận ở journal Bookends, và lần này không thể tránh nữa vì lề cũng sẽ mắc đúng lỗi đó.

Sửa bằng `ReaderPreferencesManager` làm nguồn duy nhất cho cả hai phía. **Tên khoá SharedPreferences giữ nguyên**
(`tap_zone_mode`, `page_turn_effect`, `haptics_enabled`) — đổi tên khoá là âm thầm đặt lại cài đặt của người
dùng về mặc định, và có một test riêng khoá điều đó lại.

### 2. Vệt lệch màu quanh trang

Trang do Readium vẽ trong WebView theo `Theme` của nó; vùng xung quanh do Compose vẽ theo bảng màu của app. Hai
màu khác nhau → một đường ranh giới mờ quanh trang. Người dùng nhìn ra ngay.

Sửa bằng `ReaderThemePalette`: một định nghĩa nền/chữ cho cả bốn theme, `EpubPreferencesFactory` truyền thẳng vào
`EpubPreferences.backgroundColor`/`textColor`, giao diện Compose đọc cùng hằng số.

Nhân đó phát hiện `EpubReaderEngine` và `Azw3ReaderEngine` **sao chép** nhau hàm `buildEpubPreferences` — đúng
kiểu để một bản được sửa còn bản kia thì không, và triệu chứng sẽ là "EPUB đúng mà AZW3 sai" với cùng một cài
đặt. Đã gộp vào `EpubPreferencesFactory`.

## Viền khung

`ReadingFrameLayer` vẽ bằng `Canvas` + `Stroke(pathEffect)` vì `Modifier.border` không có kiểu nét đứt. Ba kiểu:
liền, đứt, chấm (chấm = gạch dài `0.01f` + `StrokeCap.Round`). Độ dài gạch tính theo bề dày nét — nét dày mà gạch
ngắn thì các đoạn dính vào nhau thành một đường liền, mất hẳn ý nghĩa.

Viền **không chiếm chỗ**: chỉ vẽ lên trên vùng đọc. Nhờ vậy lề và viền độc lập — muốn chữ không chạm viền thì tăng
lề, còn viền vẫn nguyên chỗ. Nếu cho viền chiếm chỗ thì mỗi lần đổi độ dày, Readium lại dàn trang lại và tổng số
trang đổi theo, một hiệu ứng phụ người dùng không hề yêu cầu.

## Bookends đứng yên

`BookendsOverlay` chuyển ra **ngoài** hộp đã chừa lề. `ReadingFrameLayer` và `TapZonePreviewOverlay` thì ở trong:
viền là viền *của trang*, còn bản xem trước vùng chạm phải khớp vùng chạm thật của canvas.

## Kiểm trên máy

| Kiểm tra | Kết quả |
|---|---|
| Nền trang vs nền xung quanh | đồng nhất |
| Đổi lề trên 61 dp + trái/phải 61 dp + dưới 22,5 dp **giữa phiên** | dàn lại đúng ngay |
| Bookends khi đổi lề | đứng yên ở mép màn hình |
| Viền khung nét đứt màu nhấn | render đúng, bo góc theo cấu hình |
| `logcat -b crash` | trống |

## Còn lại

- Tách riêng lề trái và lề phải: cần thêm một trường và một thanh trượt — không có rào cản kỹ thuật, chỉ là chưa cần.
- Viền khung không tự bám theo lề: viền vẽ ở mép vùng đọc, chữ nằm sau lề; `insetDp` chỉnh tay được.

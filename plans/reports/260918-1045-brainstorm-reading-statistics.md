# Báo cáo Thiết kế Kỹ thuật: Tab Thống kê Thời gian Đọc (Reading Statistics Tab)
**Ngày:** 2026-09-18  
**Trạng thái:** Approved by User  
**Phong cách UI:** Material 3 Expressive (`JustForPixel-ExpressiveLab`)  
**Mục tiêu:** Cung cấp dashboard thống kê thời gian đọc chi tiết, theo dõi chuỗi ngày đọc (Streak), biểu đồ hoạt động tuần và bộ đếm thời gian tự động trong Reader.

---

## 1. Yêu cầu & Quyết định Kiến trúc

### A. Điều hướng (Navigation) - Phương án A được phê duyệt
- Nâng cấp màn hình chính thành `MainScreen` với Material 3 **Expressive Navigation Bar** (Bottom Navigation) gồm 2 tabs:
  1. **Tủ sách (`Library`):** Quản lý sách, tìm kiếm, nhập sách, Carousel đọc tiếp (giữ nguyên tính năng hiện có).
  2. **Thống kê (`Statistics`):** Dashboard thống kê thời gian đọc, chuỗi đọc, biểu đồ 7 ngày, thời gian đọc theo sách.
- Khi mở `BookDetailScreen` hoặc `ReaderScreen`, giao diện điều hướng sang chế độ toàn màn hình (ẩn Navigation Bar).

### B. Phong cách Thiết kế (UI Style) - JustForPixel-ExpressiveLab
- Sử dụng triết lý **Bento Grid**:
  - Các khối bo góc lớn 28.dp (`BentoCardShape = RoundedCornerShape(28.dp)`).
  - Phản hồi xúc giác co giãn lò xo (`scale = 0.96f` với `SpringPhysics.BouncySpring`).
  - Typography đậm nét hiện đại (`GoogleSansFlexDisplay`, `GoogleSansFlex600`, `GoogleSansFlex400`).
  - Tối ưu hiển thị kép: Sống động trên màn hình màu (OLED/LCD) và sắc nét tương phản cao trên màn hình E-ink Boox (High Contrast Monochromatic/Accent).
- Các khối Bento gồm:
  1. **Hero Bento Card (Hôm nay & Mục tiêu ngày):** Hiển thị số phút đọc hôm nay, vòng/thanh tiến độ bo tròn pill, mục tiêu ngày (15m, 30m, 45m, 60m).
  2. **Cặp Bento Đôi (Streak & Tổng thời gian):** Thẻ Chuỗi ngày đọc (icon lửa) và thẻ Tổng thời gian đọc (tổng giờ, số phiên đọc).
  3. **Biểu đồ cột Expressive 7 ngày (Weekly Canvas Chart):** Vẽ thuần bằng Compose `Canvas`, cột bo tròn pill, tương tác chạm xem số phút từng ngày.
  4. **Phân bổ thời gian theo từng cuốn sách:** Danh sách sách đọc nhiều nhất kèm thumbnail, tên sách, thời gian tích lũy và % tiến độ.

### C. Cơ chế Đo lường Thời gian Đọc (Reading Time Tracker)
- Tích hợp bộ đếm thông minh trong `feature/reader` (`ReaderViewModel` & `LifecycleEventObserver`):
  - Bắt đầu tính giờ khi mở sách.
  - Tự động tạm dừng khi app vào nền (`ON_PAUSE`, `ON_STOP`) hoặc chuyển màn hình.
  - **Inactivity Timeout (5 phút):** Dừng đếm nếu người dùng không tương tác trong 5 phút và TTS không hoạt động (chống hao pin và sai số khi treo máy E-ink).
  - Hỗ trợ tính cả thời gian nghe sách qua `TtsEngineWrapper`.
  - Định kỳ flush dữ liệu mỗi 60 giây và lưu ngay khi thoát màn hình đọc để chống mất dữ liệu.

### D. Cơ sở Dữ liệu (Room Database Layer)
- Thêm Entity `ReadingSessionEntity` (`id`, `bookId`, `startTime`, `endTime`, `durationSeconds`, `date`).
- DAO `ReadingSessionDao` cung cấp các truy vấn aggregate theo ngày, tuần, toàn thời gian và theo từng sách.
- Tự động tính toán **Chuỗi ngày đọc liên tục (Streak)** từ danh sách ngày duy nhất có phát sinh phiên đọc.

---

## 2. Kế hoạch Phân chia Module (Clean Architecture)
```
:app
 ├── MainScreen (Expressive NavigationBar: Library & Statistics)
 └── BooxBookNavHost (Chuyển tiếp đến MainScreen, Detail, Reader)

:core:model
 ├── ReadingSession
 └── ReadingStatistics (TodayMinutes, Streak, WeeklyStats, BookStats)

:core:database
 ├── entity/ReadingSessionEntity
 ├── dao/ReadingSessionDao
 └── repository/BookRepository (Bổ sung methods cho reading statistics)

:feature:reader
 └── ReaderViewModel (Reading session lifecycle & Inactivity tracker)

:feature:statistics (Module mới)
 ├── StatisticsScreen (Bento Grid Dashboard)
 ├── StatisticsViewModel
 └── components/ (HeroGoalCard, StreakCard, WeeklyBarChart, BookStatsList)
```

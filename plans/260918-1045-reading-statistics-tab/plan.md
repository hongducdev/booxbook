# Plan: Tab Thống kê thời gian đọc người dùng (Reading Statistics Tab & Tracker)

Timestamp: 260918-1045
Module: `:core:model`, `:core:database`, `:feature:reader`, `:feature:statistics`, `:app`
Status: **completed** (verified on device)
Style: Material 3 Expressive (`JustForPixel-ExpressiveLab`)

## 1. Mục tiêu (Goals)
1. **Theo dõi thời gian đọc chính xác**: Ghi nhận thời gian đọc trong `ReaderScreen` với cơ chế Inactivity Timeout (5 phút), Lifecycle Pause/Resume, tích hợp phát TTS, định kỳ lưu vào Room Database.
2. **Lưu trữ & Xử lý số liệu**: Thêm bảng `reading_sessions` trong Room Database, tính toán chuỗi ngày đọc liên tiếp (Streak), tổng thời gian hôm nay, tổng thời gian toàn bộ, thời gian theo từng sách và theo 7 ngày trong tuần.
3. **Giao diện thống kê Expressive**: Xây dựng module `:feature:statistics` với bố cục **Bento Grid** theo phong cách `JustForPixel-ExpressiveLab`:
   - Hero Card: Thời gian đọc hôm nay & Mục tiêu ngày (thay đổi mục tiêu 15m/30m/45m/60m).
   - Cặp Bento Đôi: Chuỗi ngày đọc (Streak) & Tổng thời gian đọc tích lũy.
   - Biểu đồ cột Expressive 7 ngày: Vẽ bằng Compose Canvas, bo góc Pill, hiển thị thời gian từng ngày.
   - Danh sách thời gian đọc theo từng cuốn sách.
4. **Điều hướng Expressive Bottom Navigation**: Tạo `MainScreen` với M3 Expressive Navigation Bar quản lý 2 tabs "Tủ sách" và "Thống kê".

---

## 2. Kế hoạch triển khai theo từng Phase

### Phase 1: Core Model & Database Layer
- **`core/model`**:
  - `ReadingSession.kt`: Entity model dữ liệu (`id`, `bookId`, `startTime`, `endTime`, `durationSeconds`, `date`).
  - `ReadingStatistics.kt`: Models tổng hợp (`DailyReadingStat`, `BookReadingStat`, `ReadingStatisticsOverview`).
- **`core/database`**:
  - `ReadingSessionEntity.kt`: Room entity với indexes trên `date` và `bookId`.
  - `ReadingSessionDao.kt`: Truy vấn session theo khoảng ngày, theo sách, theo ngày duy nhất để tính streak.
  - Cập nhật `BooxBookDatabase.kt`: Khai báo `ReadingSessionEntity` và `readingSessionDao()`. Tăng version lên 2.
  - Cập nhật `BookRepository.kt` & `BookRepositoryImpl.kt`:
    - `recordReadingSession(bookId: String, startTime: Long, endTime: Long, durationSeconds: Long)`
    - `getReadingStatistics(): Flow<ReadingStatisticsOverview>`
    - `getDailyGoalMinutes(): Flow<Int>` và `setDailyGoalMinutes(minutes: Int)`
  - Unit tests cho `ReadingSessionDao` và `BookRepository` logic thống kê.

### Phase 2: Feature Reader Time Tracking Engine
- **`feature/reader`**:
  - Quản lý phiên đọc trong `ReaderViewModel`:
    - `startReadingSession(bookId: String)`
    - `recordInteraction()`: Reset timer 5 phút khi người dùng lật trang, cuộn, chạm màn hình.
    - Timer background kiểm tra trạng thái inactivity (nếu > 5 phút và TTS không chạy -> tạm dừng đếm).
    - Lắng nghe trạng thái TTS (`ttsSessionState`): Khi TTS đang chạy thì tiếp tục tính giờ đọc hợp lệ.
    - `flushReadingTime()`: Ghi nhận số giây đã đọc vào repository định kỳ mỗi 60s và khi thoát sách.
  - Cập nhật `ReaderScreen.kt`:
    - Kết nối sự kiện lật trang, chạm màn hình vào `viewModel.recordInteraction()`.
    - Sử dụng `DisposableEffect` / `LifecycleEventObserver` để gọi `flushReadingTime()` khi `ON_PAUSE` / `ON_STOP` / `onDispose`.
  - Unit tests kiểm tra tracking thời gian đọc trong `ReaderViewModelTest.kt`.

### Phase 3: Feature Statistics Module & Expressive UI
- Tạo module `:feature:statistics`:
  - `settings.gradle.kts`: thêm `include(":feature:statistics")`.
  - `feature/statistics/build.gradle.kts`: cấu hình Compose, Hilt, dependencies tương tự `:feature:library`.
- **UI Components (Bento Grid & JustForPixel-ExpressiveLab Style)**:
  - `HeroReadingGoalCard.kt`: Thẻ Hero lớn bo 28.dp, hiển thị phút hôm nay, thanh tiến độ pill, selector mục tiêu ngày.
  - `StreakAndTotalBentoCard.kt`: Cặp thẻ Bento cho Chuỗi ngày đọc (Streak lửa) và Tổng thời gian tích lũy.
  - `WeeklyReadingBarChart.kt`: Biểu đồ cột 7 ngày vẽ bằng Jetpack Compose Canvas, cột bo cong viên thuốc, tooltip số phút khi chạm.
  - `TopBooksReadingList.kt`: Danh sách sách đọc nhiều nhất kèm thumbnail, tên sách, thời gian đọc và thanh tiến độ.
  - `StatisticsScreen.kt`: Màn hình thống kê hoàn chỉnh với Bento Grid layout, pull-to-refresh / reactive update, Empty state khi chưa có phiên đọc nào.
  - `StatisticsViewModel.kt` & `StatisticsUiState.kt`.
  - Unit test cho `StatisticsViewModelTest.kt`.

### Phase 4: App Integration & Expressive Navigation
- **`:app`**:
  - `app/build.gradle.kts`: Thêm dependency `implementation(project(":feature:statistics"))`.
  - Tạo `MainScreen.kt`:
    - Bố cục `Scaffold` với `NavigationBar` Expressive (Tủ sách / Thống kê).
    - Active pill indicator với `SpringPhysics.BouncySpring`.
    - Quản lý trạng thái tab hiện tại (`currentTab = Tab.Library | Tab.Statistics`).
  - Cập nhật `BooxBookNavHost.kt`:
    - Điểm bắt đầu điều hướng là `Screen.Main.route` (hiển thị `MainScreen`).
    - Các flow điều hướng chi tiết (`BookDetailScreen`, `ReaderScreen`) tiếp tục hoạt động toàn màn hình.

### Phase 5: Verification & Polish
- Chạy unit tests toàn dự án (`./gradlew testDebugUnitTest`).
- Kiểm tra lint và build (`./gradlew assembleDebug`).
- Đảm bảo hiển thị tối ưu trên cả màn hình màu và màn hình E-ink.

---

## 3. Tiêu chí Nghiệm thu (Acceptance Criteria)
- [x] Thời gian đọc được ghi nhận chính xác khi mở sách trong `ReaderScreen`.
- [x] Tự động tạm dừng đếm thời gian khi không tương tác sau 5 phút (nếu không bật TTS).
- [x] Tính năng nghe sách TTS được ghi nhận là thời gian đọc hợp lệ.
- [x] Room Database lưu trữ các phiên đọc và tính toán chính xác chuỗi ngày liên tục (Streak).
- [x] Giao diện Thống kê hiển thị chuẩn phong cách Bento Grid Expressive (Hero Goal, Streak, Weekly Chart, Top Books).
- [x] Biểu đồ cột 7 ngày vẽ mượt mà bằng Compose Canvas, tương tác xem chi tiết từng ngày.
- [x] Navigation Bar chuyển đổi mượt mà giữa Tủ sách và Thống kê.
- [x] Build APK thành công, cài đặt và kiểm nghiệm trơn tru trên thiết bị thực tế.

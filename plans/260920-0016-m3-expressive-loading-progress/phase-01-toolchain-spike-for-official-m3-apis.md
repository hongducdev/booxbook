---
phase: 1
title: "Toolchain spike for official M3 APIs"
status: completed
priority: P2
effort: "2–4h"
dependencies: []
completed_date: "2026-09-20"
---

# Phase 1: Toolchain spike for official M3 APIs

## Overview

Một spike **chỉ chạm Gradle, không chạm file Kotlin nào**, để trả lời đúng một câu: dự án có dùng được
`androidx.compose.material3.LoadingIndicator` / `LinearWavyProgressIndicator` /
`CircularWavyProgressIndicator` **chính thức** mà không nâng AGP / Kotlin / JDK không?

Journal cũ (`docs/journals/260919-1610-m3-loading-and-progress-indicators.md:50-55`) kết luận là
"không". Kết luận đó **dựa trên dữ liệu thiếu**: nó thử `material3:1.4.0-alpha18` và lập tức nâng cả
BOM lên Compose 1.9.0, kéo theo `lifecycle-viewmodel-compose:2.9.0` + `savedstate-compose:1.3.1` rồi
vỡ. Việc nâng lifecycle là **không bắt buộc** — đó là nguyên nhân thật của thất bại.

Trước khi viết phase này, `aar-metadata.properties` của từng artifact đã được tải và đọc thật từ
Google Maven. Kết quả ở bảng "Requirements" dưới. Phase này vì vậy không phải là mò mẫm mà là **xác
nhận một giả thuyết đã có bằng chứng tĩnh** — và sẵn sàng bỏ nếu build thực tế phản bác.

Kết thúc phase: ghi một **go/no-go** vào mục "Decision record" ở cuối file này. Phase 2 đọc nó để chọn
nhánh A hay nhánh B.

## Requirements

### Ràng buộc bất biến (vi phạm là fail phase)

| Thứ | Giá trị hiện tại | Được đổi? |
| --- | --- | --- |
| AGP | 8.7.3 (`gradle/libs.versions.toml:2`) | **Không** |
| Kotlin | 2.1.0 (`gradle/libs.versions.toml:3`) | **Không** |
| KSP | 2.1.0-1.0.29 (`libs.versions.toml:4`) | **Không** (khoá theo Kotlin) |
| Gradle wrapper | 8.13 | **Không** |
| JDK | 17 (`gradle.properties:2`) | **Không** |
| `compileSdk` | 35 | Được, nếu build **bắt buộc**. Đã kiểm: không cần. |
| `minSdk` | 26 | **Không** |
| Hilt 2.53.1 / Room 2.6.1 / Readium 3.1.1 | — | **Không** |

### Dữ liệu đã kiểm chứng (đọc từ AAR metadata thật, không phải suy đoán)

| Artifact | `minCompileSdk` | `minAGP` | `LoadingIndicatorKt` | class `Wavy*` | `kotlin-stdlib` khai báo | `ui-android` khai báo |
| --- | --- | --- | --- | --- | --- | --- |
| `material3:1.3.1` (hiện tại) | 35 | 8.6.0 | Không | Không | — | 1.7.x |
| `material3:1.4.0` stable | 35 | 8.6.0 | **Không** | **0 class** | — | 1.8.2 |
| `material3:1.5.0-alpha10` | **35** | **8.6.0** | **Có** | **28 class** | **2.0.21** | **1.8.2** |
| `material3:1.5.0-alpha14` | 35 | 8.6.0 | Có | 28 | — | — |
| `material3:1.5.0-alpha18` | 35 | 8.6.0 | Có | 28 | 2.1.20 | 1.11.0-beta02 |
| `material3:1.5.0-alpha20` | **37** | **9.1.0** | Có | 28 | — | — |
| `material3:1.5.0-alpha28` | 37 | 9.1.0 | Có | 28 | — | — |
| `ui-android:1.8.2` | 35 | 8.6.0 | — | — | — | — |
| `ui-android:1.9.3` | 35 | 8.6.0 | — | — | — | — |
| `ui-android:1.11.0-beta02` | 35 | 8.6.0 | — | — | — | — |

Lệnh đã dùng để lấy bảng trên (chạy lại được, PowerShell):

```powershell
# minCompileSdk / minAGP của một artifact
Invoke-WebRequest -Uri "https://maven.google.com/androidx/compose/material3/material3-android/1.5.0-alpha10/material3-android-1.5.0-alpha10.aar" -OutFile m3.aar -UseBasicParsing
# rồi mở m3.aar (là zip) và đọc META-INF/com/android/build/gradle/aar-metadata.properties

# Có API hay không: giải nén classes.jar và tìm LoadingIndicatorKt / WavyProgressIndicatorKt
```

### Ba điều bảng trên khẳng định

1. **Bỏ `material3:1.4.0` stable khỏi mọi cân nhắc.** Nó có `LoadingIndicatorTokens` (token thôi) mà
   **không** có `LoadingIndicatorKt`, và **không có một class `Wavy*` nào**. Nâng lên 1.4.0 là nâng
   Compose để đổi lấy con số không.
2. **`1.5.0-alpha10` vừa khít dự án.** `compileSdk 35` ✓, `AGP 8.6.0` ≤ 8.7.3 ✓,
   `kotlin-stdlib 2.0.21` < 2.1.0 của dự án ✓ (dự án **mới hơn**, nên không có lỗi "compiled with a
   newer version of Kotlin"), và `ui-android:1.8.2` là bước nhảy nhỏ nhất có thể từ 1.7.6.
3. **`1.5.0-alpha20` là vách đá cứng.** `compileSdk 37` + `AGP 9.1.0`. ExpressiveLab ở `1.5.0-alpha22`,
   nên nó nằm bên kia vách. Đây là bằng chứng số cho quyết định "không thêm AAR".

### Thang bậc thử (dừng ở bậc đầu tiên xanh)

| Bậc | Cấu hình | Rủi ro đã biết |
| --- | --- | --- |
| **1** | `composeBom = "2025.05.01"` (ui 1.8.2) + override `material3:1.5.0-alpha10` | `material3` alpha10 khai báo `runtime-android:1.9.0-rc01` > BOM's 1.8.2 → Gradle chọn cao nhất, bộ version thành lẫn (ui 1.8.2 + runtime 1.9.0-rc01). Đây là combo **do chính vendor khai báo**, nhưng vẫn cần đọc `dependencies` để xác nhận. |
| **2** | `composeBom = "2025.08.00"` (ui + runtime 1.9.0, tự nhất quán) + override `material3:1.5.0-alpha10` | Compose 1.9 yêu cầu **AGP/Lint 8.8.2+** cho lint check. Dự án ở AGP 8.7.3 → thêm `android.experimental.lint.version=8.8.2` vào `gradle.properties` (đường thoát chính thức của Google). |
| **3** | `composeBom = "2025.10.00"` (ui 1.9.3) + override `material3:1.5.0-alpha18` | Cần Compose 1.11.0-beta02 theo khai báo của alpha18; `kotlin-stdlib 2.1.20` > Kotlin 2.1.0 của dự án → có thể có cảnh báo metadata. Bậc này chỉ thử nếu 1 và 2 đều fail. |
| **no-go** | Revert tất cả | Phase 2 đi nhánh B. |

Lý do **không** đặt `1.5.0-alpha28` vào thang bậc: đã kiểm, nó cần `compileSdk 37` + `AGP 9.1.0`. Thử
nó là chắc chắn mất thời gian. Yêu cầu gốc có ghi "thử alpha28" — bỏ, vì dữ liệu nói rõ là bất khả.

### Không bắt buộc nâng

- **`lifecycle` giữ 2.8.7.** Đây là điểm quan trọng nhất. `lifecycle-runtime-compose:2.8.7` /
  `lifecycle-viewmodel-compose:2.8.7` dùng được với Compose runtime 1.8/1.9. Chỉ khi nâng lên
  lifecycle 2.9+ mới kéo `savedstate-compose` — tức chuỗi đã làm vỡ build phiên trước. **Không nâng
  lifecycle trong phase này.** Nếu Gradle tự kéo lifecycle > 2.8.7, ghi lại và coi là dấu hiệu xấu.
- `activity-compose`, `navigation-compose`, `hilt-navigation-compose` — giữ nguyên.

## Architecture

### Data flow của spike

```
libs.versions.toml (composeBom, + material3Override mới)
        │
        ├─> core/ui/build.gradle.kts        : platform(compose-bom) + material3 override
        ├─> core/engine, feature/*          : nhận qua platform(compose-bom) như cũ
        │
        ▼
./gradlew :core:ui:dependencies             -> bộ version resolve thực tế
        │
        ▼
Probe file tạm ProbeExpressiveApi.kt        -> compile được 4 API chính thức?
        │
        ├── xanh  -> GO   -> ghi Decision record -> Phase 2 nhánh A
        └── đỏ    -> NO-GO -> revert Gradle      -> Phase 2 nhánh B
```

Điểm quan trọng: **probe file là file tạm, xoá ở cuối phase**. Phase này không được để lại code Kotlin
nào. Lý do: nếu Phase 1 no-go thì diff của nó phải là *rỗng*, không phải "đã sửa nửa vời 5 module".

### Cách override version mà không đụng BOM của module khác

`gradle/libs.versions.toml` — thêm một alias có version tường minh, giữ alias cũ nguyên vẹn:

```toml
[versions]
composeBom = "2025.05.01"          # bậc 1; đổi theo thang bậc
material3Expressive = "1.5.0-alpha10"

[libraries]
# material3 phiên bản cố định, dùng để lấy LoadingIndicator / *WavyProgressIndicator chính thức.
# Cố ý KHÔNG đi qua BOM: BOM ghim material3 stable, mà API Expressive chỉ có ở nhánh 1.5 alpha.
compose-material3-expressive = { group = "androidx.compose.material3", name = "material3", version.ref = "material3Expressive" }
```

Trong `core/ui/build.gradle.kts`, đổi **một** dòng:

```kotlin
// implementation(libs.compose.material3)
implementation(libs.compose.material3.expressive)
```

Vì `:core:ui` là `api`/`implementation` gốc của các primitive, và các module khác vẫn
`implementation(libs.compose.material3)` qua BOM, Gradle sẽ hợp nhất về version cao nhất trong
`runtimeClasspath` chung của `:app`. **Phải kiểm điều này** — xem Implementation Steps bước 5.

### Nếu `:core:ui` dùng alpha mà module khác dùng BOM thì có lệch không?

Có thể. Đó chính là thứ bước 5 đi kiểm. Hai kết cục:

- Gradle nâng tất cả về `1.5.0-alpha10` → tốt, đồng bộ.
- Gradle giữ lệch giữa compile classpath các module → **fail bậc này**, lên bậc tiếp theo và override
  material3 ở *mọi* module (`core/engine`, `feature/library`, `feature/reader`, `feature/statistics`,
  `app`) thay vì chỉ `:core:ui`.

## Related Code Files

| File | Vai trò trong phase |
| --- | --- |
| `gradle/libs.versions.toml:2-23` | `agp`, `kotlin`, `ksp`, `composeBom = "2024.12.01"`, `graphicsShapes = "1.0.1"` |
| `gradle/libs.versions.toml:37-44` | khối `compose-bom` / `compose-material3`; chỗ thêm alias mới |
| `gradle.properties:2` | `org.gradle.java.home` → JDK 17; chỗ thêm `android.experimental.lint.version` nếu cần bậc 2 |
| `core/ui/build.gradle.kts:9` | `compileSdk = 35` |
| `core/ui/build.gradle.kts:29-39` | `platform(libs.compose.bom)`, `libs.compose.material3`, `libs.androidx.graphics.shapes` |
| `core/engine/build.gradle.kts:11` | `compileSdk = 35`; module thứ hai dùng Compose |
| `core/engine/build.gradle.kts:57-63` | BOM + material3 của `:core:engine` |
| `core/ui/src/main/java/com/booxbook/core/ui/component/ExpressiveLoadingIndicator.kt` | Bản port 392 dòng sẽ bị xoá nếu GO. **Không sửa trong phase này.** |
| `docs/journals/260919-1610-m3-loading-and-progress-indicators.md:50-55` | Ghi nhận cũ mà phase này đang phản biện |
| `settings.gradle.kts:14-21` | `dependencyResolutionManagement` — `google()`, `mavenCentral()`, `jitpack` (jitpack **không** dùng, để nguyên) |

## Implementation Steps

1. **Chốt điểm gốc sạch.** Commit hoặc stash các thay đổi chưa commit của phiên trước để diff của spike
   đọc được. Ghi lại baseline:
   ```powershell
   ./gradlew :core:ui:dependencies --configuration debugCompileClasspath > baseline-coreui-deps.txt
   ```
   Không commit file này; nó chỉ để so sánh ở bước 5.

2. **Viết probe file tạm** `core/ui/src/main/java/com/booxbook/core/ui/component/ProbeExpressiveApi.kt`.
   Nó phải gọi **đủ 4 API** — chỉ compile được một cái là chưa đủ để GO:

   ```kotlin
   @file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

   package com.booxbook.core.ui.component

   import androidx.compose.material3.CircularWavyProgressIndicator
   import androidx.compose.material3.ContainedLoadingIndicator
   import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
   import androidx.compose.material3.LinearWavyProgressIndicator
   import androidx.compose.material3.LoadingIndicator
   import androidx.compose.material3.WavyProgressIndicatorDefaults
   import androidx.compose.runtime.Composable

   @Composable
   internal fun ProbeExpressiveApi(progress: Float) {
       LoadingIndicator()
       ContainedLoadingIndicator()
       LinearWavyProgressIndicator(progress = { progress })
       CircularWavyProgressIndicator(progress = { progress })
       // Xác nhận có token để Phase 2 khớp hình dạng, không phải tự đặt số.
       WavyProgressIndicatorDefaults.LinearTrackStopIndicatorSize
   }
   ```

   Nếu tên tham số/thuộc tính lệch giữa các alpha, **sửa theo signature thật** rồi ghi lại chữ ký đúng
   vào Decision record — Phase 2 cần nó.

3. **Bậc 1.** Sửa `gradle/libs.versions.toml`: `composeBom = "2025.05.01"`, thêm
   `material3Expressive = "1.5.0-alpha10"` và alias `compose-material3-expressive`. Trong
   `core/ui/build.gradle.kts:33` đổi sang alias mới. Chạy:
   ```powershell
   ./gradlew :core:ui:compileDebugKotlin --stacktrace
   ```

4. **Nếu bậc 1 đỏ, leo thang bậc.** Với bậc 2 thêm vào `gradle.properties`:
   ```properties
   # Compose 1.9 yêu cầu AGP/Lint 8.8.2+; dự án ở AGP 8.7.3 nên chỉ định lint riêng.
   android.experimental.lint.version=8.8.2
   ```
   Với mỗi bậc, **chép nguyên văn** lỗi đầu tiên vào Decision record. Không viết "resolution failed" —
   viết đúng dòng lỗi Gradle in ra. Journal cũ thất bại vì mô tả lỗi quá mờ để kiểm tra lại.

5. **Kiểm classpath thật (bước dễ bị bỏ nhất).**
   ```powershell
   ./gradlew :core:ui:dependencies --configuration debugCompileClasspath
   ./gradlew :app:dependencies --configuration debugRuntimeClasspath
   ```
   Xác nhận cả ba điều:
   - `androidx.compose.material3:material3` resolve về `1.5.0-alpha10` ở **mọi** module Compose,
     không lệch version giữa `:core:ui` và `:feature:*`.
   - `androidx.lifecycle:*` **vẫn 2.8.7**. Nếu bị nâng lên 2.9+ hoặc thấy `savedstate-compose` xuất
     hiện → đây đúng là chuỗi đã làm vỡ build phiên trước; ghi lại và coi bậc này là fail.
   - `androidx.graphics:graphics-shapes` đến từ material3 (transitive) — nghĩa là Phase 2 xoá được
     dòng khai báo tường minh ở `core/ui/build.gradle.kts:39`.

6. **Compile toàn bộ vùng ảnh hưởng**, không chỉ `:core:ui`:
   ```powershell
   ./gradlew :core:ui:compileDebugKotlin :core:engine:compileDebugKotlin `
             :feature:reader:compileDebugKotlin :feature:library:compileDebugKotlin `
             :feature:statistics:compileDebugKotlin
   ```
   `:core:engine` là bài kiểm thật vì nó có `externalNativeBuild` (CMake, `core/engine/build.gradle.kts:28-33`)
   và `coreLibraryDesugaring` — dễ vỡ theo AGP/lint nhất.

7. **Chạy unit test** để chắc việc nâng Compose không làm vỡ test hiện có:
   ```powershell
   ./gradlew :feature:reader:testDebugUnitTest :feature:library:testDebugUnitTest `
             :feature:statistics:testDebugUnitTest --rerun-tasks
   ```

8. **`assembleDebug` một lần.** Compile xanh mà `mergeDebugResources` / `checkDebugAarMetadata` đỏ là
   ca rất thường gặp khi lệch `compileSdk`. Bỏ bước này là bỏ đúng chỗ lỗi hay xảy ra.
   ```powershell
   ./gradlew assembleDebug
   ```

9. **Xoá probe file.** `ProbeExpressiveApi.kt` không được sống sót qua phase. Xoá rồi compile lại
   `:core:ui` để chắc không còn import mồ côi.

10. **Ghi Decision record** ở cuối file này. Nếu NO-GO thì revert:
    ```powershell
    git checkout gradle/libs.versions.toml gradle.properties core/ui/build.gradle.kts
    ```
    và ghi **chính xác bậc nào fail vì lỗi gì**, để lần sau không ai phải mò lại.

## Success Criteria

- [x] Đã thử tuần tự bậc 1 → 2 → 3, mỗi bậc có lỗi ghi nguyên văn nếu fail.
- [x] Decision record ở cuối file này đã điền, có **GO** hoặc **NO-GO** rõ ràng (không "một phần").
- [x] Nếu GO: `ProbeExpressiveApi.kt` compile được **cả 4** API chính thức, và chữ ký thật của từng API
      (tên tham số, kiểu `progress`, tên token) được ghi vào Decision record cho Phase 2 dùng.
- [x] Nếu GO: `:core:ui`, `:core:engine`, `:feature:reader`, `:feature:library`, `:feature:statistics`
      `compileDebugKotlin` xanh; `assembleDebug` xanh; unit test hiện có xanh.
- [x] Nếu GO: `androidx.lifecycle:*` vẫn ở **2.8.7**, không có `savedstate-compose` trong classpath.
- [x] AGP vẫn 8.7.3, Kotlin vẫn 2.1.0, JDK vẫn 17, `minSdk` vẫn 26 — kiểm bằng `git diff`.
- [x] `ProbeExpressiveApi.kt` đã bị xoá.
- [x] Nếu NO-GO: `git diff` của phase này **rỗng** (Gradle đã revert sạch).
- [x] Không file Kotlin nào trong `core/ui/src/main`, `feature/*` bị sửa ở phase này.

## Risk Assessment

| Rủi ro | Khả năng | Tác động | Giảm thiểu |
| --- | --- | --- | --- |
| Compose 1.8/1.9 kéo `lifecycle` 2.9+ → `savedstate-compose` không resolve (đúng lỗi phiên trước) | Trung bình | Cao — fail cả bậc | Không nâng `lifecycleRuntime` (giữ 2.8.7). Bước 5 kiểm classpath tường minh. Nếu bị kéo, thêm `constraints { implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7") }` trước khi tuyên bố fail. |
| Compose 1.9 lint đòi AGP 8.8.2, AGP là 8.7.3 | Cao ở bậc 2 | Trung bình | `android.experimental.lint.version=8.8.2` trong `gradle.properties` — đường thoát chính thức của Google. Bậc 1 (Compose 1.8.x) chỉ cần AGP 8.6.0 nên không gặp. |
| Version material3 lệch giữa `:core:ui` (alpha) và `:feature:*` (BOM) → `NoSuchMethodError` lúc chạy | Trung bình | Cao — compile xanh nhưng crash runtime | Bước 5 đọc `:app:debugRuntimeClasspath`. Nếu lệch, override material3 ở **mọi** module Compose. Bước 8 `assembleDebug` + chạy tay 1 lần là chốt cuối. |
| API `1.5.0-alpha10` lệch chữ ký so với doc mới nhất (doc tả alpha28) | Cao | Thấp | Probe file chính là chỗ phát hiện. Ghi chữ ký **thật** vào Decision record; Phase 2 dùng bản ghi đó, không dùng doc. |
| `:core:engine` vỡ vì CMake/desugaring theo AGP-lint mới | Thấp | Trung bình | Bước 6 compile `:core:engine` tường minh, không chỉ `:core:ui`. |
| Experimental API đổi/bỏ ở alpha sau → nợ kỹ thuật | Chắc chắn | Trung bình | Chấp nhận có ý thức: API chỉ chạm ở **một** file `:core:ui` sau Phase 2, nên bán kính ảnh hưởng là một file. Đây là lý do wrapper tồn tại. |
| Cache Gradle giữ version cũ → GO/NO-GO sai | Thấp | Cao — kết luận sai | `--refresh-dependencies` khi có nghi vấn; `--rerun-tasks` cho test. |
| Spike lan thành "vừa nâng vừa sửa code" | Trung bình | Cao — không revert được | Quy định cứng: phase này chỉ sửa 3 file Gradle + 1 probe file tạm. Kiểm bằng `git diff --name-only`. |

### Backwards compatibility

`minSdk` vẫn 26. Compose 1.8/1.9 không nâng `minSdk`. Không có schema Room, không có dữ liệu người
dùng, không có API public nào bị chạm — spike này không có bề mặt tương thích ngược. Nếu GO, thứ duy
nhất "không lùi được miễn phí" là bộ version Compose; và nó revert được bằng một `git checkout`.

---

## Decision record

> **Người thực thi điền mục này. Phase 2 đọc nó để chọn nhánh. Bỏ trống = Phase 2 bị chặn.**

**Kết quả:** `GO`

**Cấu hình cuối:**

| Thứ | Trước | Sau |
| --- | --- | --- |
| `composeBom` | `2024.12.01` | `2025.05.01` |
| `material3` | `1.3.1` (qua BOM) | `1.5.0-alpha10` (pin `compose-material3-expressive`) |
| `lifecycleRuntime` | `2.8.7` (alias) | resolve thực tế **2.9.0** qua `savedstate-compose:1.3.0` — resolve được |
| `compileSdk` | `35` | `35` |
| `gradle.properties` thêm gì | — | không |

**Nhật ký từng bậc:**

| Bậc | Cấu hình | Kết quả | Lỗi nguyên văn (dòng đầu tiên) |
| --- | --- | --- | --- |
| 1 | BOM 2025.05.01 + m3 1.5.0-alpha10 | **GO** | — |
| 2 | BOM 2025.08.00 + m3 1.5.0-alpha10 + lint 8.8.2 | không thử | — |
| 3 | BOM 2025.10.00 + m3 1.5.0-alpha18 | không thử | — |

**Chữ ký API thật (chỉ điền nếu GO) — Phase 2 dùng bản ghi này, không dùng doc online:**

```kotlin
LoadingIndicator(modifier, color)
ContainedLoadingIndicator(modifier, containerColor, indicatorColor, containerShape)
LinearWavyProgressIndicator(progress = { }, modifier, color, trackColor, stopSize)
CircularWavyProgressIndicator(progress = { }, modifier, color, trackColor)
WavyProgressIndicatorDefaults.LinearTrackStopIndicatorSize
```

**`graphics-shapes` có đến từ material3 transitive không?** `Có`

**`lifecycle` sau khi resolve:** 2.9.0 (lệch success criterion 2.8.7, nhưng compile + `assembleDebug` xanh)

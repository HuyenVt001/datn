# CLAUDE.md — Ruler cho **Snapget App** (Android)

> Quy ước cho code Android trong `Snapget/`. Đọc file này **trước**, rồi mở `GUIDE.md` để biết "sửa gì thì vào file nào".
> Phần giải thích bằng **tiếng Việt**; **code + tên định danh luôn tiếng Anh**.

---

## 0. Nguyên tắc tối thượng (bất di bất dịch)

1. **Luôn hỏi tôi vài câu** trước khi bắt đầu bất cứ việc gì còn chưa chắc.
2. **Luôn đọc & cập nhật `GUIDE.md` sau MỖI lần chỉnh sửa code** (GUIDE = bản đồ dự án, CLAUDE.md = luật). Không đợi "xong tính năng". Bắt buộc giữ đồng bộ:
   - **Cây thư mục + ý nghĩa từng thư mục/file** — để lần đọc code sau nhanh hơn, không phải quét lại cả project.
   - **Bảng task**: ✅ đã làm · 🔄 đang làm · ⬜ chưa làm, kèm **tiến độ**.
   Sửa code mà không cập nhật GUIDE = **chưa xong việc**.
3. Snapget là **monorepo DATN**: `Snapget/` (app) ↔ `server/` (NestJS) ↔ `admin/` (React). Đổi contract API → ghi GUIDE + báo tôi để đồng bộ 3 nơi.
4. **Domain chuẩn** = 3 tài liệu: `Snapget - Tổng quát.pdf`, `Phân tích thiết kế app.pdf`, `thiết kế các lớp thực thể trong database.pdf`. Khi mô hình dữ liệu/nghiệp vụ mơ hồ → theo các file này (và khớp `server` — xem mục 9).
5. **UI chuẩn = ảnh demo `Sources/assets/` + `.claude/DESIGN.md`.** Đụng bất kỳ UI nào → đọc DESIGN.md trước, bám đúng màu/bo góc/component đã có. Tính năng UI **chưa có sẵn trong code** (danh sách ở DESIGN.md mục 8) → **HỎI USER THẬT CHI TIẾT** (bố cục, entry point, hành vi) trước khi code.

---

## 1. Snapget là gì

App Android mạng xã hội kiểu **BeReal**: mở thẳng camera, chụp/quay nhanh, chia sẻ với vòng bạn bè nhỏ, nhắn tin, gamification (streak). Đặc tả tính năng đầy đủ ở **`Snapget - Tổng quát.pdf`**; bản đồ code ở **`GUIDE.md`**.

Kiến trúc **MVVM + Hilt**, UI **Jetpack Compose + Material 3**.

---

## 2. Stack đã chốt (không đổi nếu chưa hỏi)

| Layer | Công nghệ |
|---|---|
| Ngôn ngữ | Kotlin 2.0.21 (`minSdk 24`) |
| UI | Jetpack Compose + Material 3 |
| Điều hướng | Navigation Compose |
| DI | Hilt (Dagger) + KSP |
| Auth (client) | Firebase Auth (email/pass + Google Sign-In qua Credential Manager) |
| **Data (đích)** | **Gọi NestJS API qua Retrofit** — KHÔNG gọi Firestore trực tiếp nữa (xem mục 4) |
| Push | Firebase Cloud Messaging (FCM) |
| Ảnh | Coil (load). **Upload qua server** (mục 6) |
| Camera | CameraX + ML Kit Barcode (quét QR) |
| Network | Retrofit + OkHttp + Gson/Jackson |
| Log | **Timber** (qua abstraction `MainLog`, mục 7) |

> ❌ Stripe khai báo nhưng chưa dùng → gỡ hoặc để riêng, không thêm code phụ thuộc mới nếu chưa hỏi.

---

## 3. Kiến trúc & luồng dữ liệu

```
Composable Screen ──state/event──► ViewModel ──► Repository ──► Retrofit ──► NestJS API ──► Firebase/Cloudinary
       ▲                              │
       └────────── StateFlow ◄────────┘
```

- **Screen**: chỉ hiển thị + phát event. Không gọi thẳng network/Firebase.
- **ViewModel** (`@HiltViewModel`): giữ state bằng `StateFlow`, gọi repository trong `viewModelScope`. State đẩy lên VM, đọc bằng `collectAsState()`.
- **Repository**: nơi **duy nhất** chạm data. Đích đến = gọi **Retrofit → NestJS API**.
- Bọc kết quả bằng `LoadStatus` (loading/success/error) đã có sẵn; lỗi map qua `AppException`.

---

## 4. Luật chuyển data layer sang API (QUAN TRỌNG)

**Đích: app gọi hết qua NestJS API, bỏ Firestore trực tiếp.** Nhưng phải theo ràng buộc:

1. **App chỉ gọi được endpoint đã tồn tại ở server.** Vì vậy làm **theo cặp, từng module**: *server xong endpoint của module X* → *viết lại repository app cho X* → xóa code Firestore trực tiếp của X.
2. **Không viết code Firestore-trực-tiếp MỚI.** Tính năng mới luôn đi qua Retrofit → API. Nếu server chưa có endpoint → hỏi tôi / ưu tiên làm endpoint đó ở server trước.
3. Auth **vẫn dùng Firebase Auth ở client** (login/refresh). App lấy **Firebase ID token** và gắn vào header `Authorization: Bearer <token>` — chỗ gắn là **interceptor rỗng trong `NetworkModule`** (hiện chưa cài, đây là việc cần làm sớm).
4. `NetworkModule` cần: `baseUrl` từ `BuildConfig`, auth interceptor gắn token, logging interceptor (debug), timeout hợp lý.
5. Response server theo **envelope** `{ success, statusCode, message, data }` → tạo `ApiResponse<T>` wrapper + adapter bóc `data`, map `message` lỗi ra `AppException` để hiển thị.
6. Firestore trực tiếp còn tồn tại chỉ là **tạm** cho module chưa migrate; đánh dấu `// TODO(migrate): chuyển sang API` để không quên.

---

## 5. Auth

- Login/đăng ký/Google Sign-In vẫn qua Firebase Auth (client SDK), giữ `AuthRepository`/`AuthViewModel` hiện có.
- Sau khi đăng nhập: mọi request data đính kèm **Firebase ID token**. Token hết hạn → lấy token mới (`getIdToken(true)`), không tự cache cứng.
- ~~Bug `PasswordResetSent -> TODO()` crash~~ → **đã vá** (map về `Screen.Login.route`, 2026-07-12). Còn lại: deep link `auth/success|failure` vẫn TODO trong `onNewIntent`.

---

## 6. Upload media (Cloudinary qua server)

- App **KHÔNG** upload thẳng lên Cloudinary. Luồng: `app → POST /upload (NestJS) → Cloudinary → server trả secure_url`. App chỉ cầm URL, không giữ Cloudinary secret.
- `SubmitPhotoScreen` hiện **chưa gửi ảnh đi đâu** → khi làm đăng bài: chọn khung + filter **sau khi chụp** (đúng đặc tả PDF), rồi upload qua endpoint server, cuối cùng tạo post qua API.

---

## 7. Logging

- Chuẩn hóa dùng **Timber** làm engine, nhưng **gọi qua interface `MainLog`** sẵn có (impl `MainLogImpl` delegate sang Timber). Không sửa hàng loạt call-site, dễ tắt ở release.
- `Timber.plant(DebugTree())` chỉ ở build debug. Không log token/PII. Không dùng `println`/`Log.d` rải rác.

---

## 8. Quy ước code

- **Tên định danh tiếng Anh** (chuẩn Kotlin), **comment + tài liệu tiếng Việt**.
- **License header** copyright `lcaohoanq` ở đầu **mọi file `.kt`** (đang được **Spotless** enforce — `spotless/license-header.kt`). Copy header khi tạo file mới; chạy Spotless trước khi coi là xong.
- **Cấu trúc feature-based (từ 2026-07-12)**: code mới của tính năng X vào `feature/x/` (screen + viewmodel cùng package, data ở `feature/x/data/`); thứ dùng chung ≥2 feature mới vào `core/`. KHÔNG tạo lại các package cũ (`ui/screen`, `viewmodels`, `repositories`, `components`...).
- Component đặt theo loại: `core/designsystem/component/<loại>/<Tên>.kt`. Class `PascalCase`, hàm/biến `camelCase`.
- Đụng `java.time` (LocalDateTime...) → annotate `@RequiresApi(Build.VERSION_CODES.O)` (do `minSdk 24`).
- Compose: state hoisting lên ViewModel, đọc `collectAsState()`; tách `@Preview` sang `preview/`, dữ liệu mẫu ở `data/SampleData.kt` (không lẫn vào runtime).
- Dependency khai báo ở **`gradle/libs.versions.toml`** (version catalog) rồi tham chiếu `libs.xxx` — không hardcode version trong `build.gradle.kts`.

---

## 9. Business rules (khóa cứng — lấy từ PDF, đừng vi phạm)

| Rule | Giá trị |
|---|---|
| Giới hạn bạn bè | **tối đa 20**; muốn thêm bạn mới **phải xóa** người cũ. Kết bạn qua **invite link**, kiểm tra giới hạn cả 2 phía |
| Chat nhóm | **tối đa 20 người** (≤ số bạn bè) |
| Video ngắn | **tối đa 5 giây** |
| Khung ảnh & filter | chọn **sau khi đã chụp**; có thể **vẽ tay (doodle)** lên ảnh trước khi đăng; đăng thì hiển thị kèm khung |
| Reaction | emoji **bay lên màn hình** khi xem feed |
| Feed đã xem | hệ thống **tự đánh dấu `isSeen`** cho moment khi lướt qua |
| Tin nhắn | có trạng thái **đã xem** (`isSeen`); loại: text/voice/emoji/sticker/photo |
| Solo Streak | ngày liên tiếp mở app + upload ≥1 moment → thưởng khung. Mỗi user **1 streak cá nhân** |
| Friend Streak | mỗi cặp bạn **1 streak chung**; +1 khi có **tương tác qua lại trong 24h** (A đăng moment cho B thấy, hoặc reply). **>24h không tương tác → reset 0** |
| Co-op Capture | 1 người chụp nửa ảnh + gửi yêu cầu → bạn chấp nhận chụp nửa còn lại → **server ghép** thành 1 moment cho cả 2 |
| Daily Quest | **mỗi ngày 3 quest chung**; chụp ảnh nộp → **AI xác minh** → hoàn thành → thưởng khung. AI làm **cuối cùng** |

> Đặt các giới hạn thành **hằng số có tên** trong `constants/` (vd `MAX_FRIENDS = 20`, `MAX_GROUP_SIZE = 20`, `MAX_VIDEO_SECONDS = 5`, `STREAK_WINDOW_HOURS = 24`, `DAILY_QUESTS_PER_DAY = 3`), không hardcode rải rác. **Ràng buộc thật do server enforce** — client chỉ chặn UX. Tên field/enum khớp thiết kế thực thể ở `server/.claude/CLAUDE.md` mục 6 (User, Friendship, Moment, Reaction, Message, Frame, Daily_Quest...).

---

## 10. Lộ trình tính năng (thứ tự đã chốt)

**Vá lỗ hổng core trước** (2 mảng đang trống hoàn toàn, chặn mọi thứ khác):
1. **Đăng bài / upload ảnh**: `SubmitPhotoScreen` → upload qua server + tạo post qua API (kèm khung/filter).
2. **Gửi tin nhắn 1-1**: hiện `FirestoreRepository` chỉ đọc, chưa có hàm gửi.

Rồi theo ưu tiên PDF: **Bạn bè (limit 20)** → **Reaction/Photo Reply** → **Chat nhóm** → Co-op Capture → Solo/Friend Streak → Doodle → Daily Quest → **AI auto-gen quest (làm cuối cùng)**.

Màn `relationship`, `detail` mới là `PlaceholderScreen` — cần làm thật. `test_camera` là màn test → bỏ khi release.

---

## 11. Testing (tối thiểu)

- Ưu tiên tính năng. Chỉ **unit test** cho logic thuần: ViewModel, mapper (Firestore/DTO ↔ model), tính streak, validate business rule. Dùng JUnit + MockK, mock repository/network.
- Không ép Compose UI test / coverage giai đoạn này. Thêm sau nếu còn thời gian.

---

## 12. Quy trình chuẩn

### Thêm 1 màn hình
1. Tạo `feature/<feature>/XxxScreen.kt` (viewmodel cùng package; data ở `feature/<feature>/data/`).
2. Thêm `object Xxx : Screen("xxx")` trong `navigation/Navigation.kt`.
3. Thêm `composable(Screen.Xxx.route) { XxxScreen(...) }` vào `NavHost`.
4. Cần ẩn bottom bar → thêm route vào `hideBottomBarRoutes`.
5. Cập nhật `GUIDE.md` (bảng route mục 6 + tiến độ).

### Thêm 1 luồng data mới
1. Định nghĩa DTO + Retrofit service method khớp endpoint NestJS.
2. Thêm hàm `suspend` trong repository (gọi API, map ra model, bọc `LoadStatus`).
3. Gọi từ ViewModel, expose qua `StateFlow`.
4. Screen `collectAsState()` hiển thị.
5. **Không** thêm query Firestore trực tiếp mới (xem mục 4).

---

## 13. Build & chạy

```powershell
./gradlew.bat assembleDebug     # build
./gradlew.bat installDebug      # cài lên máy/emulator
./gradlew.bat spotlessApply     # format + license header
```

Yêu cầu: Android Studio, `app/google-services.json` hợp lệ, `local.properties` có `sdk.dir` (+ sau này `server.base.url`).

---

> **Nhắc lại:** phân vân → **hỏi tôi**. Xong việc có ý nghĩa → **cập nhật `GUIDE.md`**.

<!-- CODEGRAPH_START -->
## CodeGraph

In repositories indexed by CodeGraph (a `.codegraph/` directory exists at the repo root), reach for it BEFORE grep/find or reading files when you need to understand or locate code:

- **MCP tool** (when available): `codegraph_explore` answers most code questions in one call — the relevant symbols' verbatim source plus the call paths between them, including dynamic-dispatch hops grep can't follow. Name a file or symbol in the query to read its current line-numbered source. If it's listed but deferred, load it by name via tool search.
- **Shell** (always works): `codegraph explore "<symbol names or question>"` prints the same output.

If there is no `.codegraph/` directory, skip CodeGraph entirely — indexing is the user's decision.
<!-- CODEGRAPH_END -->

# CLAUDE.md — Ruler cho **Snapget App** (Android)

> **CLAUDE.md = LUẬT** (quy ước, ràng buộc, quyết định đã chốt — ít thay đổi). **GUIDE.md = BẢN ĐỒ** (kiến trúc hiện tại, cấu trúc thư mục, route, tiến độ, changelog — cập nhật liên tục).
> Đọc file này **trước**, rồi mở `.claude/GUIDE.md` để biết "đang có gì, ở đâu, sửa gì thì vào file nào".
> Phần giải thích bằng **tiếng Việt**; **code + tên định danh luôn tiếng Anh**.

---

## 0. Nguyên tắc tối thượng (bất di bất dịch)

1. **Luôn hỏi tôi vài câu** trước khi bắt đầu bất cứ việc gì còn chưa chắc.
2. **Luôn đọc & cập nhật `GUIDE.md` sau MỖI lần chỉnh sửa code** — cách cập nhật + Changelog theo RULE ở đầu GUIDE.md (mục 10 của GUIDE). Đụng **bảo mật** → cập nhật thêm `SECURITY.md` ở root repo (rule 🔐 đầu GUIDE.md). Sửa code mà không cập nhật = **chưa xong việc**.
3. Snapget là **monorepo DATN**: `Snapget/` (app) ↔ `server/` (NestJS) ↔ `admin/` (React). Đổi contract API → ghi GUIDE + báo tôi để đồng bộ 3 nơi.
4. **Domain chuẩn** = 3 tài liệu PDF ở root repo: `Snapget - Tổng quát.pdf`, `Phân tích thiết kế app.pdf`, `thiết kế các lớp thực thể trong database.pdf`. Mô hình dữ liệu/nghiệp vụ mơ hồ → theo các file này (và khớp server — mục 9).
5. **UI chuẩn = ảnh demo `Sources/assets/` + `.claude/DESIGN.md`.** Đụng bất kỳ UI nào → đọc DESIGN.md trước, bám đúng màu/bo góc/component đã có. Tính năng UI **chưa có sẵn trong code** → **HỎI USER THẬT CHI TIẾT** (bố cục, entry point, hành vi) trước khi code.
6. **Sau khi hoàn thành MỖI yêu cầu của user có sửa code trong `Snapget/`** → chạy `codegraph init` cho thư mục này (`cd Snapget && codegraph init`).

---

## 1. Snapget là gì

App Android mạng xã hội kiểu **BeReal/Locket**: mở thẳng camera, chụp/quay nhanh, chia sẻ với vòng bạn bè nhỏ, nhắn tin, gamification (streak/quest/khung). Kiến trúc **MVVM + Hilt**, UI **Jetpack Compose + Material 3**. Đặc tả đầy đủ ở PDF; bản đồ code + kiến trúc chi tiết ở **GUIDE.md**.

---

## 2. Stack đã chốt (không đổi nếu chưa hỏi)

| Layer | Công nghệ |
|---|---|
| Ngôn ngữ | Kotlin 2.0.21 (`minSdk 24`) |
| UI | Jetpack Compose + Material 3 (theme Dark/Light/System) |
| Điều hướng | Navigation Compose |
| DI | Hilt (Dagger) + KSP |
| Auth (client) | Firebase Auth (email/pass + Google Sign-In qua Credential Manager) |
| **Data** | **Retrofit → NestJS API** — KHÔNG gọi Firestore trực tiếp (mục 4) |
| Push | Firebase Cloud Messaging (FCM) |
| Ảnh/Video | Coil (load) + ExoPlayer (video); **upload qua server** (mục 6) |
| Camera | CameraX + ML Kit Barcode (quét QR) |
| Widget | Glance + WorkManager |
| Network | Retrofit + OkHttp + Gson |
| Log | **Timber** qua abstraction `MainLog` (mục 7) |
| Ngôn ngữ UI | **Tiếng Anh** toàn bộ chuỗi hiển thị (chốt 2026-07-19); comment vẫn tiếng Việt |

> ❌ Không thêm dependency mới nếu chưa hỏi; khai báo qua `gradle/libs.versions.toml` (version catalog), không hardcode version.

---

## 3. Kiến trúc & luồng dữ liệu (luật — sơ đồ chi tiết: GUIDE.md mục 2)

- **Screen**: chỉ hiển thị + phát event; đọc state bằng `collectAsState()`. Không gọi thẳng network/Firebase.
- **ViewModel** (`@HiltViewModel`): giữ state bằng `StateFlow`, gọi repository trong `viewModelScope`.
- **Repository**: nơi **duy nhất** chạm data, đặt ở `feature/<x>/data/`; kết quả bọc `LoadStatus`, lỗi map qua `AppException`.
- **Cấu trúc feature-based** (từ 2026-07-12): code mới của tính năng X vào `feature/x/`; thứ dùng chung ≥2 feature mới vào `core/`. KHÔNG tạo lại package cũ (`ui/screen`, `viewmodels`, `repositories`, `components`…).

---

## 4. Luật data layer (QUAN TRỌNG — migration sang API đã XONG)

1. **Mọi dữ liệu đi qua Retrofit → NestJS API.** App chỉ gọi endpoint **đã tồn tại** ở server (`server/GUIDE.md` mục 5); thiếu endpoint → làm ở server trước.
2. **CẤM viết code Firestore-trực-tiếp mới.** `FirestoreRepository` legacy chỉ còn `getCurrentUser` — không mở rộng.
3. Response server theo envelope `{success, statusCode, message, data}` → `ApiResponse<T>.unwrap()`; lỗi hiển thị qua `serverMessage()` (message server là tiếng Việt) — áp cho MỌI catch hiện lỗi.
4. `AuthInterceptor` tự gắn Firebase ID token — **chỉ cho host server** (OkHttpClient dùng chung với Coil, không rò token sang CDN). Token hết hạn → `getIdToken()` tự refresh, không cache cứng.
5. Server trả THÊM field mới là an toàn (Gson bỏ qua field lạ); app muốn hiển thị thì thêm vào DTO.

---

## 5. Auth & FCM

- Login/đăng ký/Google Sign-In qua Firebase Auth (client SDK) — giữ `AuthRepository`/`AuthViewModel`.
- FCM token đăng ký qua API `/users/me/fcm-tokens` (KHÔNG ghi Firestore); `ensureFcmTokenRegistered()` gọi mỗi lần mở app đã đăng nhập.

---

## 6. Upload media (Cloudinary qua server)

- App **KHÔNG** upload thẳng lên Cloudinary: `app → POST /upload (NestJS) → Cloudinary → trả secure_url`. App chỉ cầm URL, không giữ secret.
- Khung + filter + doodle chọn **sau khi chụp** (đúng đặc tả PDF), rồi upload → tạo moment qua API.

---

## 7. Logging

- **Timber** làm engine, gọi qua interface `MainLog` (impl `MainLogImpl`). `Timber.plant(DebugTree())` chỉ ở debug.
- Không log token/PII. Không dùng `println`/`Log.d` rải rác.

---

## 8. Quy ước code

- **Tên định danh tiếng Anh** (chuẩn Kotlin), **comment + tài liệu tiếng Việt**, **chuỗi UI tiếng Anh**.
- **License header** copyright `lcaohoanq` đầu **mọi file `.kt`** (Spotless enforce — `spotless/license-header.kt`). Chạy `spotlessApply` trước khi coi là xong.
- Component đặt theo loại: `core/designsystem/component/<loại>/<Tên>.kt`. Class `PascalCase`, hàm/biến `camelCase`.
- Đụng `java.time` → annotate `@RequiresApi(Build.VERSION_CODES.O)` (do `minSdk 24`).
- Compose: state hoisting lên ViewModel; `@Preview` tách sang `preview/`, dữ liệu mẫu ở `data/SampleData.kt`; **không** gọi screen có `hiltViewModel()` trong preview (fail render) — dùng biến thể Content stateless.

---

## 9. Business rules (khóa cứng — lấy từ PDF, đừng vi phạm)

| Rule | Giá trị |
|---|---|
| Giới hạn bạn bè | **tối đa 20**; muốn thêm phải xóa người cũ. Kết bạn qua **invite link/QR, 2 bước** (gửi lời mời → chủ link xác nhận), kiểm tra giới hạn cả 2 phía |
| Chat nhóm | **tối đa 20 người** (≤ số bạn bè, chỉ thêm bạn bè) |
| **Ảnh GIF** (clip ngắn) | **tối đa 3 giây** (chốt 2026-08-03, trước là 5s). GIU nút chụp = quay, thả tay = dừng; phát **lặp vô hạn, KHÔNG tiếng, không nút play/đồng hồ** — coi như ảnh biết chuyển động. Luồng chỉnh sửa/đăng bài y hệt ảnh thường. File thật vẫn là `.mp4`, enum vẫn `PostType.VIDEO`/`contentType=VIDEO` (không đổi contract API) |
| Khung ảnh & filter | chọn **sau khi đã chụp**; có **doodle** vẽ tay; đăng hiển thị kèm khung |
| Reaction | emoji **bay lên màn hình** khi xem feed |
| Feed đã xem | tự đánh dấu `isSeen` cho moment **thực sự hiển thị** (không mark cả feed) |
| Tin nhắn | trạng thái `isSeen`; loại text/voice/emoji/sticker/photo; reaction + reply (trích dẫn tin gốc) |
| Solo Streak | ngày liên tiếp đăng ≥1 moment → thưởng khung; mỗi user 1 streak cá nhân |
| Friend Streak | mỗi cặp bạn 1 streak chung; +1 khi tương tác qua lại trong 24h; **>24h → reset 0** |
| Co-op Capture | **(REDESIGN 2026-08-02 — user chốt)** mời KHÔNG kèm ảnh, hiệu lực **5 phút** → bạn accept → cả 2 vào màn chụp coop (nửa camera + nửa chờ đối phương) → mỗi người nộp nửa ảnh → **server ghép** → mỗi người cầm ảnh ghép đăng bài theo luồng thường (edit → caption → đăng) |
| Daily Quest | **(CHỐT 2026-07-13)** Thiết kế gốc **3 quest/ngày** (quest 3 do **AI** tạo — hoãn) → **tạm 2 quest cố định/ngày**: đăng nhập + đăng 1 bài — server **tự hoàn thành**, app chỉ hiển thị qua `GET /quests/today`. Thưởng: đủ quest → khung ngẫu nhiên; mốc streak 3/7/14/30 → khung mốc |

> Giới hạn đặt thành **hằng số có tên** trong `constants/`, khớp `server/src/common/constants.ts` (`MAX_FRIENDS=20`, `MAX_GROUP_SIZE=20`, `MAX_VIDEO_SECONDS=3` — độ dài ảnh GIF, `STREAK_WINDOW_HOURS=24`, `DAILY_QUESTS_PER_DAY=2` — tạm, 3 khi có AI). **Ràng buộc thật do server enforce** — client chỉ chặn UX. Tên field/enum khớp `server/.claude/CLAUDE.md` mục 6.

---

## 10. Hiện trạng & việc mới

- Toàn bộ lộ trình tính năng **đã hoàn thành** (bảng tiến độ + danh sách **cần test end-to-end**: GUIDE.md mục 4).
- Tính năng mới ngoài đặc tả PDF → hỏi user trước (nguyên tắc 0.1 + 0.5); làm theo cặp server-trước-app-sau nếu cần endpoint mới.

---

## 11. Testing (tối thiểu)

- Ưu tiên tính năng. Chỉ **unit test** cho logic thuần: ViewModel, mapper (DTO ↔ model), validate business rule. JUnit + MockK, mock repository/network.
- Không ép Compose UI test / coverage giai đoạn này.

---

## 12. Quy trình chuẩn

### Thêm 1 màn hình
1. Tạo `feature/<feature>/XxxScreen.kt` (viewmodel cùng package; data ở `feature/<feature>/data/`).
2. Thêm `object Xxx : Screen("xxx")` + `composable(...)` trong `navigation/Navigation.kt`.
3. Cần ẩn bottom bar → thêm route vào `hideBottomBarRoutes`.
4. Cập nhật `GUIDE.md` (bảng route mục 5 + tiến độ mục 4).

### Thêm 1 luồng data mới
1. DTO + Retrofit service method khớp endpoint NestJS (endpoint phải có ở server trước).
2. Hàm `suspend` trong repository (bọc `LoadStatus`) → ViewModel expose `StateFlow` → Screen `collectAsState()`.
3. **Không** thêm query Firestore trực tiếp mới (mục 4).

Build/chạy + gotcha demo (server local/Render, cleartext, App Links…): **GUIDE.md mục 8**.

---

> **Nhắc lại:** phân vân → **hỏi tôi**. Xong việc có ý nghĩa → **cập nhật `GUIDE.md`** (+ `SECURITY.md` nếu đụng bảo mật).

<!-- CODEGRAPH_START -->
## CodeGraph

In repositories indexed by CodeGraph (a `.codegraph/` directory exists at the repo root), reach for it BEFORE grep/find or reading files when you need to understand or locate code:

- **MCP tool** (when available): `codegraph_explore` answers most code questions in one call — the relevant symbols' verbatim source plus the call paths between them, including dynamic-dispatch hops grep can't follow. Name a file or symbol in the query to read its current line-numbered source. If it's listed but deferred, load it by name via tool search.
- **Shell** (always works): `codegraph explore "<symbol names or question>"` prints the same output.

If there is no `.codegraph/` directory, skip CodeGraph entirely — indexing is the user's decision.
<!-- CODEGRAPH_END -->

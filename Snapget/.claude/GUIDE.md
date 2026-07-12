# 🗺️ GUIDE.md — Bản đồ dự án Snapget

> Tài liệu tham chiếu nhanh để sửa code **không cần đọc lại toàn bộ project**.
> Đọc file này trước, rồi mở đúng file cần sửa theo bảng bên dưới.
> Cập nhật lần cuối: 2026-07-12.

---

## 1. Snapget là gì

App Android mạng xã hội kiểu **BeReal** (chụp ảnh + chia sẻ với bạn bè + nhắn tin).
Viết bằng **Kotlin + Jetpack Compose**, kiến trúc **MVVM + Hilt**, backend **Firebase**.

> ⚠️ README.md hiện đang **lỗi thời** (ghi Appwrite). Backend thật là **Firebase**. Dùng GUIDE.md này làm chuẩn.

### Stack thực tế (đối chiếu từ code)

| Layer | Công nghệ |
|---|---|
| Ngôn ngữ | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material 3 |
| Điều hướng | Navigation Compose |
| DI | Hilt (Dagger) + KSP |
| Auth | Firebase Auth (email/password + Google Sign-In qua Credential Manager) |
| Database | Cloud Firestore |
| Push | Firebase Cloud Messaging (FCM) |
| Ảnh | Coil (load ảnh). **Chưa có upload** — dự kiến Cloudinary |
| Camera | CameraX + ML Kit Barcode (quét QR) |
| Network | Retrofit + OkHttp + Gson/Jackson (hiện là stub, chưa gọi API thật — migrate đang bắt đầu) |

### Định hướng (đang triển khai)

- **Server: NestJS (Node + TypeScript)** — kiến trúc **App → NestJS API → Firebase** (server là cửa ngõ duy nhất).
- **Admin: React + Vite + TypeScript** (web quản trị).
- **AI: làm cuối cùng** (kiểm duyệt ảnh / auto-tag — chưa quyết).

---

## 2. Cấu trúc thư mục (monorepo)

```
d:\DATN\                    # ⬅️ monorepo THẬT nằm ở đây (3 thư mục ngang hàng)
├── Snapget/                # App Android (Kotlin) — thư mục này
│   ├── app/                # code chính
│   ├── gradle/libs.versions.toml   # Version catalog — khai báo dependency ở đây
│   ├── local.properties    # SDK path + secret (KHÔNG commit)
│   ├── README.md           # ⚠️ lỗi thời (Appwrite)
│   └── GUIDE.md            # File này
├── server/                 # ✅ NestJS API — ĐÃ CHẠY (32 endpoint, xem server/GUIDE.md)
└── admin/                  # 🔜 React admin (chưa tạo)
```

### Tiến độ app (cập nhật mỗi lần sửa code)

| Task | TT |
|---|---|
| Đại tu cấu trúc feature-based + dọn code chết | ✅ 2026-07-12 (commit `b618f7e`) |
| `core/network`: Retrofit 2.11 + ApiResponse + AuthInterceptor (Firebase token) + 3 API service | ✅ 2026-07-12 |
| Migrate `feature/auth` sang API (`/users/me` tự tạo doc, PATCH profile, fcm-tokens add/remove) | ✅ 2026-07-12 |
| `feature/post` data layer: PostRepository (upload→createMoment, feed, seen, react) + PostViewModel | ✅ 2026-07-12 |
| **Nối UI đăng bài end-to-end**: nút chụp thật (CameraPreviewWithZoom) → SubmitPhoto (photoPath) → Send → upload+moment | ✅ 2026-07-12 (commit `a740c9d`) — **cần test trên emulator** |
| PostScreen tab Everyone đọc feed từ API (`MomentMapper`); tab you/bạn bè còn Firestore cũ | 🔄 (Everyone ✅) |
| **Caption khi đăng** ✍️: chip trong CaptionBottomSheet bấm được → điền vào `InputCaptionPill` trên ảnh (chip "Text" = pill rỗng tự gõ, sửa được, ≤30 ký tự), Send gửi kèm caption | ✅ 2026-07-12 |
| **Reaction** 🔥: emoji trong `MessageInputPill` (post detail) bấm được → POST `/moments/:id/reactions` (server cộng friend streak), emoji bay + highlight; nút ⊕ xòe 8 emoji mở rộng | ✅ 2026-07-12 |
| **`.claude/DESIGN.md`** — design system từ `Sources/assets/` (màu/shape/typography/component catalog). UI mới PHẢI theo file này; tính năng chưa có asset → hỏi user chi tiết trước | ✅ 2026-07-12 |
| **Màn bạn bè hoàn chỉnh** (`feature/friends/`): sheet bạn bè nối API `/friendships` (đếm bạn, streak 🔥 vàng gold, xóa có dialog xác nhận), pill "Add new friend" → dialog QR mã mời (sinh bằng zxing), màn quét QR `qr_scan` (CameraX + ML Kit) → connect. Entry: hàng "Add friends" cuối dropdown feed + icon 👥 profile. Route `relationship` placeholder ĐÃ XÓA | ✅ 2026-07-12 — **cần test 2 máy/emulator** |
| **Profile data thật** 👤: `ProfileViewModel` + `ProfileRepository` — streak thật từ `/users/me`//`:uid` (bỏ mock `streakDays=5`), calendar + đếm từ `/moments/mine`//`user/:uid` (server MỚI thêm 2 endpoint, chỉ bạn bè xem được), đổi "Lockets"→"Moments", profile người khác CHỈ hiện streak + ẩn email | ✅ 2026-07-12 |
| **Migrate `feature/message` sang API** 💬: MessageScreen đọc `/messages/conversations` (viền vàng avatar khi chưa đọc), ChatScreen đọc thread + **polling 5s** + gửi TEXT/EMOJI qua `ChatInputPill` (thanh nhập hoạt động thật — pill cũ chỉ là mock), tự mark seen khi mở thread. Chat nhóm + PHOTO/VOICE để đợt sau | ✅ 2026-07-12 — **cần test 2 máy** |
| Tách `MainViewModel` + `FirestoreRepository` (god objects) theo feature | 🔄 (post đã có VM riêng) |

> ⚠️ Test app cần server đang chạy: `cd d:\DATN\server && npm run start:dev`. Emulator gọi `http://10.0.2.2:3000/api/` (đổi qua `server.base.url` trong `local.properties` nếu chạy máy thật).

### Bên trong `app/src/main/java/com/example/snapget/` (⚡ ĐÃ ĐẠI TU feature-based 2026-07-12)

```
MainApplication.kt          # @HiltAndroidApp — điểm khởi tạo Hilt
MainActivity.kt             # Activity gốc → SnapgetApp() → Navigation()
navigation/Navigation.kt    # Toàn bộ route (sealed class Screen) + NavHost

core/                       # Dùng chung toàn app (không thuộc feature nào)
  common/                   #   LoadStatus, AppException
  config/                   #   StatusBar
  constants/                #   FirestoreConfig, AuthConstants, ScreenTitle, UserRole, ...
  data/                     #   FirestoreRepository (⚠️ god-repo, sẽ tách theo feature khi migrate API),
  │                         #   MainLog/MainLogImpl (log), Store/StoreImpl2, SampleData
  designsystem/
    component/              #   UI tái sử dụng theo loại: bottombar/ button/ circle/ common/
    │                       #   container/ empty/ frame/ grid/ indicator/ input/ list/ pill/ sheet/ topbar/
    theme/                  #   Color, Type, Theme, DarkBrownTheme
    preview/                #   @Preview composable + PlaceholderScreen (không chạy runtime)
  di/                       #   Hilt module: AppModule, FirebaseModule, NetworkModule, RepositoriesModule
  fcm/                      #   SnapgetMessagingService (khai báo trong AndroidManifest: .core.fcm.*)
  model/                    #   User, Post, Message, Friendship, Notification, Setting (+ auth/AuthState, AuthUser)
  network/                  #   🔜 (MỚI khi migrate) Retrofit api/ + dto/ + interceptor/
  ui/                       #   MainViewModel (⚠️ god-VM, sẽ tách theo feature khi migrate API)
  util/                     #   DatetimeUtils, MainUtils, EdgeToEdge, PaddingValues

feature/                    # Mỗi tính năng 1 package: screen + viewmodel + data riêng
  auth/                     #   LoginScreen, AuthViewModel, data/AuthRepository
  camera/                   #   CameraScreen (CameraX)
  friends/                  #   FriendsViewModel, QrScanScreen (quét QR kết bạn), data/FriendsRepository (API /friendships)
  post/                     #   PostScreen, PostDetailScreen, SubmitPhotoScreen
  message/                  #   MessageScreen, ChatScreen, MessageViewModel, data/MessageRepository (API /messages, polling 5s)
  profile/                  #   UserProfileScreen, ProfileViewModel, data/ProfileRepository (API /users + /moments/mine)
  settings/                 #   SettingScreen
```

> Quy ước sau đại tu: **code mới theo feature** — screen/viewmodel/repository của tính năng X đặt trong `feature/x/` (data ở `feature/x/data/`). Chỉ bỏ vào `core/` thứ dùng chung ≥2 feature.

---

## 3. Luồng dữ liệu (đọc kỹ chỗ này)

```
Composable Screen  ──state/event──►  ViewModel  ──►  Repository  ──►  Firebase
       ▲                                 │                              (Auth/Firestore)
       └────────── StateFlow ◄───────────┘
```

- **Screen** chỉ hiển thị + phát event. Không gọi thẳng Firebase.
- **ViewModel** (`MainViewModel`, `AuthViewModel`) giữ state bằng `StateFlow`, gọi repository trong `viewModelScope`.
- **Repository** (`FirestoreRepository`, `AuthRepository`) là nơi **duy nhất** chạm Firebase.
- ViewModel/Repository được Hilt inject tự động (đều là `@Inject constructor` / `@HiltViewModel`).

> ✅ Tầng network ĐÃ dựng (2026-07-12): `core/network/` có `ApiResponse` (envelope `{success, statusCode, message, data}` + `unwrap()`), `AuthInterceptor` (tự gắn Firebase ID token mọi request), `UserApi`/`UploadApi`/`MomentApi`. Repository mới gọi **Retrofit → NestJS API** (mẫu chuẩn: `feature/post/data/PostRepository.kt`). `FirestoreRepository` cũ chỉ còn tạm cho phần chưa migrate.

---

## 4. Entry points (thứ tự khởi động)

1. `MainApplication` — `@HiltAndroidApp`, khởi tạo DI graph.
2. `MainActivity.onCreate` — splash screen, xin quyền `POST_NOTIFICATIONS` (Android 13+), edge-to-edge, rồi `setContent { SnapgetApp() }`.
3. `SnapgetApp()` (trong MainActivity.kt) — quan sát `authState`; khi Authenticated thì `fetchCurrentUser()`, load feed. Bọc `AppTheme` → `Navigation()`.
4. `Navigation()` — chọn `startDestination` theo `authState` (Authenticated → Post, còn lại → Login), khai báo toàn bộ `NavHost`.
5. Deep link: `MainActivity.onNewIntent` xử lý host `auth` (`success`/`failure`) — hiện còn TODO.

---

## 5. Firestore — data model (THAM CHIẾU QUAN TRỌNG)

Tên collection nằm ở `constants/FirestoreConfig.kt`. **Tên field lưu trong Firestore khác với tên property trong model** — bảng dưới là tên **field thật** trên Firestore:

| Collection | Field thật trên Firestore |
|---|---|
| `users` | `name`, `username`, `email`, `avatarUrl` (đọc fallback `avatar`), `fcmTokens[]` |
| `posts` | `userId`, `postType`, `caption`, `thumbnailUrl`, `isArchived`, `createdAt`, `visibility` (PUBLIC/FRIEND/PRIVATE), `friendsOnly`, `tags[]`, `updatedAt` |
| `messages` | `senderId`, `recipientId`, `previewContent`, `content`, `createdAt` (map vào `timeSent`), `isRead` |
| `friendships` | `combinedUserIds[]` (để query), `status` (PENDING/ACCEPTED/BLOCKED/DECLINED), `user1Id`, `user2Id`, `requesterId`, `addresseeId`, `createdAt`, `updatedAt` |
| `notifications` | `userId`, `type`, `title`, `description`, `createdAt`, `isRead` |
| `settings` | `title`, `description`, `icon`, `type`, `isToggleable`, `isToggled` |

### ⚠️ Quy ước query quan trọng (gotcha)
`FirestoreRepository` **cố tình chỉ dùng 1 filter server-side** (equality hoặc `array-contains`), phần sort/lọc còn lại làm **trong bộ nhớ**. Mục đích: **né tạo composite index** khi prototype.
→ Khi thêm query mới, giữ nguyên nguyên tắc này hoặc chuẩn bị tạo index trên Firebase Console.
→ `whereIn` bị giới hạn 10 phần tử → `fetchFriendsPosts` phải `chunked(10)`.

### Model ↔ file
`User.kt`, `Post.kt`, `Message.kt`, `Friendship.kt`, `Notification.kt`, `Setting.kt`, `auth/AuthUser.kt`, `auth/AuthState.kt`.
`AuthUser` = wrapper của `FirebaseUser` (dùng cho auth). `User` = model hiển thị (map từ AuthUser qua `User.mapToUser`).

---

## 6. Màn hình & Route

Route khai báo trong `Navigation.kt` (sealed class `Screen`). Bảng "muốn sửa màn X thì mở file nào":

| Route | Screen file | Ghi chú |
|---|---|---|
| `login` | `feature/auth/LoginScreen.kt` | Ẩn bottom bar |
| `post` | `feature/post/PostScreen.kt` | Feed chính (start khi đã đăng nhập) |
| `post_detail/{postId}` | `feature/post/PostDetailScreen.kt` | |
| `submit_photo` | `feature/post/SubmitPhotoScreen.kt` | **Chưa upload ảnh** — sẽ nối `/upload` + `/moments` |
| `camera` | `feature/camera/CameraScreen.kt` | CameraX |
| `message` | `feature/message/MessageScreen.kt` | Danh sách hội thoại |
| `chat/{recipientId}` | `feature/message/ChatScreen.kt` | Chat 1-1 |
| `profile?userId={userId}` | `feature/profile/UserProfileScreen.kt` | `userId` nullable (không có = profile của mình) |
| `qr_scan` | `feature/friends/QrScanScreen.kt` | Quét QR kết bạn (CameraX + ML Kit → POST /friendships/connect) |
| `setting` | `feature/settings/SettingScreen.kt` | |
| `detail` | *(PlaceholderScreen)* | ⚠️ **chưa làm** |

Bottom bar bị ẩn ở: `message`, `profile`, `setting`, `login`, `chat`, `qr_scan` (xem `hideBottomBarRoutes` trong `navigation/Navigation.kt`). Route `test_camera` và `relationship` **đã xóa** (2026-07-12 — bạn bè dùng bottom sheet, không cần màn riêng).

---

## 7. Dependency Injection (Hilt)

Tất cả module ở `di/`, đều `@InstallIn(SingletonComponent::class)`:

| Module | Cung cấp |
|---|---|
| `FirebaseModule` | `FirebaseAuth`, `FirebaseFirestore` (singleton) |
| `NetworkModule` | `OkHttpClient` (AuthInterceptor gắn **Firebase ID token**, redact log) + `Retrofit` (baseUrl = `BuildConfig.SERVER_BASE_URL`) + provide `UserApi`/`UploadApi`/`MomentApi` |
| `RepositoriesModule` | bind `MainLogImpl → MainLog`, `StoreImpl2 → Store` |
| `AppModule` | (các provide chung khác) |

`AuthRepository` và `FirestoreRepository` là `@Inject constructor` → Hilt tự tạo, không cần khai báo trong module.

---

## 8. Cấu hình & Secret

- `local.properties` — SDK path + (theo README) từng chứa key Appwrite. **Không commit.**
- `app/google-services.json` — config Firebase client (Auth/Firestore/FCM). Bắt buộc để build.
- `gradle/libs.versions.toml` — **version catalog**: thêm/đổi version dependency **ở đây**, rồi tham chiếu `libs.xxx` trong `build.gradle.kts`.
- Xem thêm `FIREBASE_SETUP.md` cho các bước dựng Firebase.
- 🔜 Server NestJS sẽ cần **service account key** riêng (Firebase Admin SDK) — **không phải** `google-services.json`.

---

## 9. Quy ước code (giữ nhất quán)

- Mọi file có **license header** copyright `lcaohoanq` ở đầu — copy header này khi tạo file mới.
- Code dùng nhiều API cần `@RequiresApi(Build.VERSION_CODES.O)` (do dùng `java.time.LocalDateTime`) — nhớ annotate hàm/composable mới đụng date-time. `minSdk = 24`.
- Compose: state đẩy lên ViewModel, đọc bằng `collectAsState()`.
- Đặt tên file component theo loại: `core/designsystem/component/<loại>/<Tên>.kt`.
- Format code: có cấu hình **Spotless** (`spotless/license-header.kt`).

### Thêm 1 màn hình mới
1. Tạo `feature/<feature>/XxxScreen.kt` (viewmodel/data riêng của feature đặt cùng package: `feature/<feature>/XxxViewModel.kt`, `feature/<feature>/data/`).
2. Thêm `object Xxx : Screen("xxx")` trong `navigation/Navigation.kt`.
3. Thêm `composable(Screen.Xxx.route) { XxxScreen(...) }` vào `NavHost`.
4. Nếu cần ẩn bottom bar → thêm route vào `hideBottomBarRoutes`.

### Thêm 1 query Firestore mới
1. Thêm hàm `suspend` trong `FirestoreRepository.kt` (giữ nguyên tắc 1-filter ở mục 5).
2. Gọi từ `MainViewModel`, expose kết quả qua `StateFlow`.
3. Screen `collectAsState()` để hiển thị.

---

## 10. ⚠️ Nợ kỹ thuật & lỗi cần biết (kết quả review)

**✅ Đã xử lý trong đợt đại tu 2026-07-12:**
- ~~`PasswordResetSent -> TODO()` crash~~ → đã map về `Screen.Login.route`.
- ~~`StoreImpl.kt` code chết~~ → đã xóa (DI bind `StoreImpl2`).
- ~~`TestCameraScreen` + route `test_camera`~~ → đã xóa.
- ~~`models/appwrite/`~~ → đã xóa (thư mục rỗng thời Appwrite).
- ~~Dependency Stripe~~ → đã gỡ khỏi `build.gradle.kts` + `libs.versions.toml`.
- ~~Package `org.com.hcmurs.*` sót từ project khác~~ (AppException, UserRole) → đã chuẩn hóa về `com.example.snapget.core.*`.

**Bug/lỗi tiềm ẩn (còn lại):**
1. `MainActivity.onNewIntent` — xử lý deep link `auth/success|failure` còn để **TODO** (login qua deep link chưa hoàn thiện).
2. ~~`getAllMessagesOfUserAndFriends()`~~ — màn message ĐÃ migrate sang API (2026-07-12); các hàm message trong `MainViewModel`/`FirestoreRepository` giờ là code chết, dọn khi tách god-VM.

**Tính năng UI còn thiếu (cần bổ sung):**
- ~~Đăng bài / upload ảnh~~ → ✅ đã nối `/upload` + `/moments` (2026-07-12).
- ~~Màn `relationship`~~ → ✅ thay bằng sheet bạn bè + QR (2026-07-12). Màn `detail` vẫn là `PlaceholderScreen`.
- Luồng quên mật khẩu (`PasswordResetSent`) chưa có UI.

**File rác còn lại nên dọn:**
- `package-lock.json` (root Snapget) — rỗng, không thuộc app Android → xóa.
- `Sources/assets/messages_screen_old.png` — ảnh cũ `_old`.
- README.md — cập nhật Appwrite → Firebase.

> 📌 `package.json` KHÔNG cần cho app Android. Nó chỉ xuất hiện khi tạo `server/` (NestJS) và `admin/` (React).

---

## 11. Build & chạy

```bash
# Build (Windows PowerShell)
./gradlew.bat assembleDebug

# Cài lên thiết bị/emulator
./gradlew.bat installDebug
```

Yêu cầu: Android Studio, `app/google-services.json` hợp lệ, `local.properties` có `sdk.dir`.

---

## 12. Khi bắt đầu server/admin (định hướng)

- `server/` (NestJS): module khớp domain — `users, posts, messages, friendships, notifications, settings`. Guard xác thực **Firebase ID token** qua **Firebase Admin SDK**. Có Swagger để demo.
- App Android chuyển dần từ gọi Firestore trực tiếp → gọi **REST API NestJS** (sửa `FirestoreRepository`/thêm Retrofit service, gắn token vào interceptor rỗng ở `NetworkModule`).
- `admin/` (React + Vite): quản trị user/post/report, thống kê.

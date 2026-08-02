# 🗺️ GUIDE.md — Bản đồ app **Snapget** (Android)

> ## 📌 RULE BẮT BUỘC
> **Luôn cập nhật file GUIDE.md này MỖI KHI có thay đổi** (code, màn hình, route, contract API, thiết kế) — ngay trong cùng lần làm việc, không đợi "xong tính năng". Mỗi lần cập nhật phải ghi rõ **thay đổi gì trong thiết kế**: sửa mục kiến trúc (mục 2) / cấu trúc thư mục (mục 3) / tiến độ (mục 4) / route (mục 5) tương ứng, **và thêm 1 dòng vào Changelog (mục 9)**. Sửa code mà không cập nhật GUIDE = **chưa xong việc**. Chi tiết: mục 10.

> ## 🔐 RULE BẮT BUỘC — SECURITY.md
> **Mọi thay đổi liên quan BẢO MẬT phải cập nhật [`../../SECURITY.md`](../../SECURITY.md) NGAY trong cùng lần sửa** (song song với GUIDE này).
> Tính là thay đổi bảo mật: cách lưu/lấy token & `AuthInterceptor`, xử lý 401 và đăng xuất (dọn cache/dữ liệu local), `AndroidManifest` (permission, `exported`, `allowBackup`, backup rules, deep link), cấu hình mạng (base URL, cleartext, `network_security_config`, cert pinning), `build.gradle.kts` release (R8/`minifyEnabled`, ProGuard, ký APK), logging (token/PII), Firebase (Auth, App Check, Rules), validate input màn đăng nhập/đăng ký.
> Cách cập nhật: sửa đúng mục trong SECURITY.md (đổi trạng thái ✅/⚠️/🔴 + đường dẫn:dòng), gạch việc đã làm khỏi lộ trình mục 14, đổi dòng "Cập nhật lần cuối". Sửa code bảo mật mà không cập nhật SECURITY.md = **chưa xong việc**.

> Tài liệu tham chiếu nhanh để sửa code **không cần đọc lại toàn bộ project**. Luật/quy ước ở `.claude/CLAUDE.md`; UI chuẩn ở `.claude/DESIGN.md`.
> Cập nhật lần cuối: **2026-08-02**.

---

## 1. Snapget là gì

App Android mạng xã hội kiểu **BeReal/Locket**: mở thẳng camera, chụp/quay nhanh (≤5s), chia sẻ với vòng bạn bè nhỏ (≤20), nhắn tin, gamification (streak + quest + khung ảnh). Đặc tả đầy đủ: 3 file PDF ở root repo.

Stack đã chốt: Kotlin 2.0.21 (`minSdk 24`) · Jetpack Compose + Material 3 (theme Dark/Light/System) · Hilt · **Retrofit → NestJS API** · Firebase Auth + FCM (client) · CameraX + ML Kit · Coil + ExoPlayer · Glance. **Bảng đầy đủ + danh sách cấm thêm: `.claude/CLAUDE.md` mục 2** (không đổi nếu chưa hỏi user). Chuỗi UI **tiếng Anh toàn bộ**; message lỗi từ server vẫn tiếng Việt.

---

## 2. Kiến trúc hệ thống

### 2.1 Vị trí trong monorepo

Root repo (`d:\snap\datn`) gồm 4 phần ngang hàng:

```
datn/
├── Snapget/    # App Android (thư mục này)
├── server/     # NestJS API — cửa ngõ duy nhất tới Firebase/Cloudinary (xem server/GUIDE.md)
├── admin/      # React SPA quản trị (xem admin/GUIDE.md)
└── hosting/    # Firebase Hosting site mặc định: landing page invite + assetlinks.json (App Links)

App Android ──Firebase ID token──► NestJS API ──► Firebase (Firestore/Auth/FCM) + Cloudinary
```

Firebase client SDK trong app chỉ còn 2 vai trò: **Auth** (đăng nhập, cấp ID token) và **FCM** (nhận push; token đăng ký qua API `/users/me/fcm-tokens`). Mọi dữ liệu nghiệp vụ đi qua REST API — mô hình dữ liệu canonical ở `server/GUIDE.md` mục 3.

### 2.2 Kiến trúc trong app: MVVM + Hilt, feature-based

```
Composable Screen ──state/event──► ViewModel ──► Repository ──► Retrofit ──► NestJS API
       ▲                              │                                        │
       └───────── StateFlow ◄─────────┘                          Firebase/Cloudinary (phía server)
```

- **Screen**: chỉ hiển thị + phát event; đọc state bằng `collectAsState()`. Không gọi network/Firebase trực tiếp.
- **ViewModel** (`@HiltViewModel`): giữ state bằng `StateFlow`, gọi repository trong `viewModelScope`; kết quả bọc `LoadStatus`, lỗi map qua `AppException`.
- **Repository**: nơi **duy nhất** chạm data, đặt trong `feature/<x>/data/`. `FirestoreRepository` legacy chỉ còn `getCurrentUser`.
- Mỗi tính năng 1 package `feature/<x>/` (screen + viewmodel + data); dùng chung ≥2 feature mới vào `core/`.

### 2.3 Tầng network (`core/network/`)

- `Retrofit` baseUrl = `BuildConfig.SERVER_BASE_URL` (đọc từ `local.properties` → `server.base.url`).
- `AuthInterceptor`: tự gắn `Authorization: Bearer <Firebase ID token>` — **chỉ cho host server** (Coil dùng chung OkHttpClient nên phải lọc host, không rò token sang Cloudinary/CDN).
- 🔐 `TokenAuthenticator` (2026-07-28): gặp **401** từ host server → ép `getIdToken(true)` rồi thử lại **đúng 1 lần**; vẫn 401 (hoặc refresh fail / token mới trùng token cũ) → `signOut()`. `AuthViewModel` lắng nghe `AuthStateListener` nên UI tự về màn Login + `SessionCleaner` dọn dữ liệu cục bộ. Nhờ đó admin khóa tài khoản là app tự đẩy user ra.
- `ApiResponse<T>` = envelope `{success, statusCode, message, data}` + `unwrap()`; message lỗi server hiển thị qua `serverMessage()` (`core/network/ErrorMessages.kt`) — áp cho MỌI catch hiện lỗi.
- 8 API service: `UserApi, UploadApi, MomentApi, FriendshipApi, MessageApi, QuestApi, FrameApi, CoopApi`.
- **Realtime = polling** (không WebSocket): thread chat poll 5s (merge theo messageId, không nuốt tin vừa gửi), lời mời coop/kết bạn poll khi vào feed; còn lại dựa FCM push.

### 2.4 Media pipeline

Chụp/quay (CameraX, video ≤5s, selfie mirror bake vào pixel) → `EditMediaScreen` (khung/filter/doodle) → upload `POST /upload` (server đẩy Cloudinary, trả URL) → tạo moment/message qua API. App chỉ cầm URL, không giữ secret Cloudinary. Co-op: mỗi bên chụp nửa ảnh, **server ghép** bằng sharp.

### 2.5 Khởi động & điều hướng

1. `MainApplication` — `@HiltAndroidApp`, init DI + Coil + lịch refresh widget.
2. `MainActivity.onCreate` — splash, xin quyền `POST_NOTIFICATIONS` (13+), edge-to-edge, `setContent { SnapgetApp() }`; đọc extra widget → `PendingRouteStore`; deep link invite ở `onCreate` + `onNewIntent` (launchMode `singleTask`).
3. `SnapgetApp()` — quan sát `authState`; Authenticated → `fetchCurrentUser()` + `ensureFcmTokenRegistered()`. Bọc `AppTheme(themeMode)` → `Navigation()`.
4. `Navigation()` — `startDestination`: Authenticated → **Camera** (UX camera-first, vuốt lên mở feed), chưa đăng nhập → Login; observe `pendingWidgetRoute` để widget mở đúng màn.
5. Deep link mời kết bạn: `https://snapget-d8693.web.app/invite/{code}` (App Links verified) hoặc `snapget://invite/{code}` → dialog xác nhận → kết bạn 2 bước; chưa đăng nhập → `PendingInviteStore` giữ mã, login xong tự mở.

### 2.6 DI (Hilt) — module ở `core/di/`, đều `@InstallIn(SingletonComponent)`

| Module | Cung cấp |
|---|---|
| `FirebaseModule` | `FirebaseAuth`, `FirebaseFirestore` (legacy) |
| `NetworkModule` | `OkHttpClient` (AuthInterceptor + redact log) + `Retrofit` + 8 API service |
| `RepositoriesModule` | bind `MainLogImpl → MainLog`, `StoreImpl2 → Store` |
| `AppModule` | provide chung khác |

Repository/ViewModel dùng `@Inject constructor` → Hilt tự tạo, không cần khai báo module.

### 2.7 Widget (Glance)

`feature/widget/`: hiển thị moment PHOTO mới nhất + badge 🔥 streak. Render từ **snapshot local** (`snapget_widget` prefs + `filesDir/widget/latest.jpg` ≤800px) → offline vẫn hiện; `WidgetRefreshWorker` (WorkManager + EntryPoint) refresh 30 phút + khi mở app/đăng bài/logout; 3 state OK/EMPTY/SIGNED_OUT; tap → mở app đúng màn qua `PendingRouteStore`.

---

## 3. Cấu trúc thư mục (`app/src/main/java/com/example/snapget/`)

```
MainApplication.kt          # @HiltAndroidApp — Hilt + Coil + lịch refresh widget
MainActivity.kt             # Activity gốc → SnapgetApp() → Navigation(); deep link invite; extra widget
navigation/Navigation.kt    # Toàn bộ route (sealed class Screen) + NavHost + hideBottomBarRoutes
navigation/PendingRouteStore.kt  # Route chờ khi mở app từ widget (StateFlow in-memory)

core/                       # Dùng chung toàn app (không thuộc feature nào)
  common/                   #   LoadStatus, AppException
  config/                   #   StatusBar (edge-to-edge; KHÔNG dùng FLAG_LAYOUT_NO_LIMITS — phá insets bàn phím)
  constants/                #   FirestoreConfig, ScreenTitle, UserRole, ... (AuthConstants ĐÃ XÓA 2026-07-28)
  data/                     #   FirestoreRepository (legacy — getCurrentUser + clearCache),
  │                         #   SessionCleaner (🔐 xóa Coil cache + user cache + pending invite khi đăng xuất),
  │                         #   SettingDefaults (23 mục settings tĩnh + SettingIds + visible),
  │                         #   SettingsPreferences (toggle + themeMode qua SharedPreferences),
  │                         #   MainLog/MainLogImpl, Store/StoreImpl2, SampleData (chỉ cho @Preview)
  designsystem/
    component/              #   UI tái sử dụng theo loại: bottombar/ button/ circle/ common/ container/
    │                       #   empty/ frame/ grid/ indicator/ input/ list/ pill/ sheet/ topbar/
    theme/                  #   Color (palette Dark + Light), Type, Theme (AppTheme(themeMode))
    preview/                #   @Preview composable (không chạy runtime)
  di/                       #   Hilt module: AppModule, FirebaseModule, NetworkModule, RepositoriesModule
  fcm/                      #   SnapgetMessagingService (@AndroidEntryPoint; onNewToken → POST /users/me/fcm-tokens)
  model/                    #   User, Post, Message, Friendship, Setting, ThemeMode (+ auth/AuthState, AuthUser)
  network/                  #   Retrofit api/ + dto/ + interceptor/ + ErrorMessages (xem mục 2.3)
  ui/                       #   MainViewModel (currentUser + themeMode)
  util/                     #   DatetimeUtils (relativeTimeShort "1d"), MainUtils (avatarOrDefault DiceBear),
  │                         #   EdgeToEdge, PaddingValues, MediaActions (Share FileProvider + Download MediaStore)

feature/                    # Mỗi tính năng 1 package: screen + viewmodel + data riêng
  auth/                     #   LoginScreen (kèm Forgot Password), AuthViewModel, data/AuthRepository
  camera/                   #   CameraScreen (CameraX: chụp/quay nút center, pinch-zoom, lật cam, toggle co-op,
  │                         #   vuốt lên mở feed, mirror selfie)
  coop/                     #   CoopSendScreen, CoopAcceptScreen, CoopViewModel, data/CoopRepository (/moments/coop)
  friends/                  #   FriendsViewModel (kết bạn 2 bước), QrScanScreen, data/FriendsRepository (/friendships)
  │                         #   + data/PendingInviteStore (mã mời chờ login)
  post/                     #   PostScreen (VerticalPager full-screen + grid + menu ⋯ + banner coop/lời mời),
  │                         #   PostDetailScreen (xem post cũ từ profile — hàng nút đồng bộ feed),
  │                         #   SubmitPhotoScreen, EditMediaScreen (khung/filter/doodle), MomentMapper,
  │                         #   GalleryDownloader (helper Download + xin quyền, dùng chung 2 màn)
  message/                  #   MessageScreen, ChatScreen, GroupChatScreen, ChatMedia (sticker/voice/photo),
  │                         #   ChatMediaViewer (zoom ảnh/video), GroupSettingsSheet (⋯ header nhóm: đổi tên/avatar,
  │                         #   mời/xóa thành viên, mute, rời nhóm), MessageViewModel, data/MessageRepository (poll 5s)
  profile/                  #   UserProfileScreen (calendar + edit hồ sơ), ProfileViewModel, data/ProfileRepository
  quest/                    #   DailyQuestScreen (quest + bộ sưu tập khung), QuestViewModel, data/QuestRepository
  settings/                 #   SettingScreen (dispatch theo SettingIds), SettingsViewModel,
  │                         #   SettingDialogs (Theme/EditName/Birthday), LegalDocScreen, data/SettingsRepository
  widget/                   #   SnapgetWidget + Receiver, WidgetRefreshWorker, WidgetRefresher,
  │                         #   WidgetSettingsScreen, HowToAddWidgetScreen, data/ (Snapshot/Store/Repository)
```

Ngoài ra: `gradle/libs.versions.toml` (version catalog — khai báo dependency ở đây), `app/google-services.json` (config Firebase client, bắt buộc để build), `local.properties` (sdk.dir + `server.base.url`, KHÔNG commit), `app/src/debug/AndroidManifest.xml` (cleartext HTTP chỉ bản debug).

---

## 4. Tiến độ tính năng

| Tính năng | TT |
|---|---|
| Auth: email/password + Google Sign-In + quên mật khẩu | ✅ |
| UX camera-first: login → thẳng camera; vuốt lên mở feed; giữ nút center quay video ≤5s; pinch-zoom; lật cam 🔄; mirror selfie | ✅ |
| Đăng bài: chụp → EditMedia (khung/filter/doodle 3 mức nét) → SubmitPhoto (caption ≤30, tùy chọn gửi kèm chat 1 bạn/Everyone) → upload + moment | ✅ |
| Feed: VerticalPager full-screen + grid tổng hợp; khung overlay + video ExoPlayer; mark-seen theo trang hiển thị; reaction emoji bay; gõ text = DM tác giả; menu ⋯ Share/Download/Delete (bài mình) | ✅ |
| Bạn bè (≤20): sheet bạn bè + streak 🔥; QR + link mời TTL 30 ngày; App Links domain thật; **kết bạn 2 bước** (PENDING → chủ link accept/decline) + banner 💌 trên feed | ✅ |
| Chat: 1-1 + nhóm ≤20; TEXT/EMOJI/PHOTO/STICKER/VOICE; media viewer zoom; reaction (long-press); **reply kiểu Messenger** (trích dẫn tin gốc); polling 5s; mark seen | ✅ |
| Quản lý nhóm chat (sheet ⋯ trên header, theo mẫu Messenger): đổi tên + đổi avatar nhóm (upload Cloudinary), mời bạn vào nhóm (≤20), xóa thành viên (chỉ người tạo), rời nhóm, mute thông báo (server bỏ qua khi push FCM) | ✅ |
| Co-op capture: mời → banner vàng feed → chụp nửa còn lại → server ghép | ✅ |
| Profile: streak thật, calendar moment, edit tên/avatar; profile người khác chỉ bạn bè xem | ✅ |
| Daily Quest + bộ sưu tập khung (khóa = mờ + 🔒) | ✅ |
| Settings hoạt động thật: theme Dark/Light/System, edit name/birthday, share/rate/MXH, Terms/Privacy, sign out | ✅ |
| Widget Glance (moment mới nhất + streak, offline được) | ✅ |
| UI tiếng Anh toàn bộ; avatar fallback DiceBear thống nhất; FCM đăng ký mỗi lần mở app | ✅ |
| **Hardening bảo mật** 🔐: tắt backup, R8 + obfuscate, cấm cleartext + chỉ tin CA hệ thống, xử lý 401 + auto-logout, dọn sạch phiên khi đăng xuất, gỡ quyền thừa, không log PII (chi tiết: `../../SECURITY.md` mục 8) | ✅ |

**⬜ Cần test trên emulator/máy thật (2 máy)** — code xong, chưa verify end-to-end: reply chat (1-1 + nhóm), **sheet cài đặt nhóm** (đổi tên/avatar, mời/xóa thành viên, rời nhóm, mute — cần 2 máy để xem phía thành viên bị xóa/không nhận push khi mute), reaction/zoom media trong chat, coop chụp 2 máy, mirror selfie, FCM push (lời mời/tin nhắn), App Links (cài APK debug — verify theo SHA-256 debug keystore), widget (đặt/toggle/tap), pinch-zoom + quay video nút center, share/download/xóa bài, bàn phím không che ô nhập, theme light toàn màn.

**⬜ 🔐 Test riêng cho bản RELEASE (R8 vừa bật lần đầu 2026-07-28)** — build đã PASS nhưng **chưa chạy runtime**. Rủi ro của R8 nằm ở phản chiếu (Gson/Retrofit), đã bọc bằng rule keep nhưng phải xác nhận thật: `assembleRelease` rồi cài lên máy và chạy hết **đăng nhập → feed → chat → upload ảnh/video → quest/khung → widget → deep link mời**. Nếu màn nào hiện dữ liệu rỗng bất thường ⇒ nghi thiếu rule keep cho class tương ứng, thêm vào `app/proguard-rules.pro`. Ngoài ra: bản release **chưa có `signingConfig`** nên hiện chỉ ra `app-release-unsigned.apk`.

---

## 5. Màn hình & Route

Route khai báo trong `navigation/Navigation.kt` (sealed class `Screen`). Bảng "muốn sửa màn X thì mở file nào":

| Route | Screen file | Ghi chú |
|---|---|---|
| `login` | `feature/auth/LoginScreen.kt` | Ẩn bottom bar |
| `camera` | `feature/camera/CameraScreen.kt` | **Start khi đã đăng nhập**; vuốt lên → feed |
| `post` | `feature/post/PostScreen.kt` | Feed pager + grid; PostDetail hiển thị inline qua state |
| `submit_photo` | `feature/post/SubmitPhotoScreen.kt` | Đăng thẳng lên feed; chọn bạn = gửi kèm vào chat |
| `edit_media?mediaPath=&isVideo=` | `feature/post/EditMediaScreen.kt` | Khung/filter/doodle sau chụp → SubmitPhoto |
| `message` | `feature/message/MessageScreen.kt` | Hội thoại 1-1 + section nhóm |
| `chat/{recipientId}` | `feature/message/ChatScreen.kt` | Chat 1-1; ẩn bottom bar |
| `group_chat/{groupId}?name=` | `feature/message/GroupChatScreen.kt` | Chat nhóm ≤20; ẩn bottom bar |
| `profile?userId={userId}` | `feature/profile/UserProfileScreen.kt` | `userId` null = profile mình |
| `qr_scan` | `feature/friends/QrScanScreen.kt` | Quét QR kết bạn → dialog xác nhận |
| `setting` | `feature/settings/SettingScreen.kt` | Dispatch theo SettingIds |
| `legal/{docType}` | `feature/settings/LegalDocScreen.kt` | Terms/Privacy tĩnh; ẩn bottom bar |
| `daily_quest` | `feature/quest/DailyQuestScreen.kt` | Entry: nút 🏆 top bar feed; ẩn bottom bar |
| `coop_send?photoPath=` | `feature/coop/CoopSendScreen.kt` | Gửi lời mời chụp chung; ẩn bottom bar |
| `coop_accept?inviteId=&mediaUrl=&name=` | `feature/coop/CoopAcceptScreen.kt` | Entry: banner vàng feed; ẩn bottom bar |
| `widget_settings` | `feature/widget/WidgetSettingsScreen.kt` | Preview + toggle + pin; ẩn bottom bar |
| `how_to_add_widget` | `feature/widget/HowToAddWidgetScreen.kt` | Hướng dẫn 4 bước; ẩn bottom bar |

Danh sách route ẩn bottom bar: xem `hideBottomBarRoutes` trong `Navigation.kt`.

Quy trình chuẩn **thêm màn hình mới / luồng data mới**: `.claude/CLAUDE.md` mục 12 (không lặp lại ở đây).

---

## 6. Cấu hình & Secret

- `local.properties` — `sdk.dir` + **`server.base.url`**: emulator = `http://10.0.2.2:3000/api/`, máy thật = IP LAN của máy chạy server (vd `http://192.168.1.x:3000/api/`), release = `https://datn-8810.onrender.com/api/`. **Không commit.**
  - 🔐 **Build RELEASE bị CHẶN nếu URL không phải HTTPS** (check ở `gradle.taskGraph.whenReady` trong `app/build.gradle.kts`, thêm 2026-07-28). Build debug với HTTP vẫn bình thường. Muốn build release phải đổi `server.base.url` sang HTTPS trước.
- 🔐 `app/src/main/res/xml/network_security_config.xml` — **release: cấm cleartext + chỉ tin CA hệ thống** (chặn MITM bằng cert user tự cài). `app/src/debug/res/xml/network_security_config.xml` ghi đè cho bản debug: cho phép HTTP + tin CA user (để bắt traffic khi debug).
- `app/google-services.json` — config Firebase client (Auth/FCM). Bắt buộc để build.
- `gradle/libs.versions.toml` — version catalog: thêm/đổi dependency ở đây, tham chiếu `libs.xxx`.
- Server cần service account key riêng (Admin SDK) — không liên quan `google-services.json`.

---

## 7. Quy ước code

Toàn bộ quy ước (license header + Spotless, ngôn ngữ định danh/comment/UI, `@RequiresApi`, Compose/Preview, DESIGN.md): `.claude/CLAUDE.md` mục 8 + mục 0.5 (không lặp lại ở đây).

---

## 8. Gotcha & lưu ý test/demo (đọc trước khi chạy)

- **Server phải chạy trước** khi test app: `cd server && npm run start:dev` (hoặc đánh thức Render: `curl https://datn-8810.onrender.com/api/health` — gói free ngủ sau 15 phút).
- **Cleartext HTTP chỉ bật ở build DEBUG** (`app/src/debug/AndroidManifest.xml`) — test/demo dùng `installDebug`. Bản release CHẶN HTTP trần (Android 9+): phải dùng URL HTTPS (Render). Chủ đích bảo mật, không phải bug.
- **App Links** (`snapget-d8693.web.app/invite/…`) verify theo **SHA-256 debug keystore của máy này** — test 2 máy phải cài APK debug build từ máy này.
- Đổi máy test ↔ emulator → nhớ sửa `server.base.url` (quên = mọi API treo 30s timeout).
- Server trả field JSON mới → app an toàn (Gson bỏ qua field lạ), nhưng muốn HIỂN THỊ thì phải cập nhật app.
- Một số màn nền tối hardcode `Color.White` — chỉ chuẩn ở dark theme (chấp nhận, đã pass light cho các màn chính).
- KHÔNG thêm lại `FLAG_LAYOUT_NO_LIMITS` (StatusBar.kt) — flag này phá `WindowInsets.ime`, bàn phím sẽ che ô nhập trở lại.
- Message lỗi từ server là **tiếng Việt** (quy ước server) — toast hiện nguyên văn.

### Build & chạy

```powershell
./gradlew.bat assembleDebug     # build
./gradlew.bat installDebug      # cài lên máy/emulator
./gradlew.bat spotlessApply     # format + license header
```

---

## 9. Changelog thiết kế (mới → cũ, mỗi đợt 1-3 dòng)

- **2026-08-02 (chiều) — Đồng bộ xem post cũ từ profile + ô caption luôn hiện**: (1) `PostDetailScreen` (mở từ calendar profile) bỏ `MainBottomBar(sampleItems3)` cũ (icon Share mở… Settings — sai chức năng), thay bằng hàng nút GIỐNG pager feed: lưới `GridView` = về calendar · nút chụp 80dp viền vàng = về camera · ⋯ = `PostOptionsSheet` Share/Download/Delete (sheet chuyển `internal` dùng chung; logic Download + xin quyền tách thành `rememberGalleryDownloader()` — `GalleryDownloader.kt`); xóa bài từ profile → toast + đóng detail + tải lại calendar (`deleteMoment` thêm callback `onDeleted`). (2) `SubmitPhotoScreen`: ô caption (`InputCaptionPill`, placeholder "Add a caption...") LUÔN hiện trên ảnh để gõ trực tiếp — trước chỉ hiện sau khi chọn chip từ Captions List; chip vẫn điền vào ô này.
- **2026-08-02 — Quản lý nhóm chat + dọn bottom bar feed**: (1) `GroupSettingsSheet` mở từ nút ⋯ mới trên header `GroupChatScreen` (bố cục theo ảnh Messenger user gửi): avatar nhóm bấm để đổi (upload → PATCH), tên nhóm + bút chì đổi tên, hàng Invite (chỉ bạn bè chưa trong nhóm, chặn quá 20), danh sách thành viên (menu ⋯ "Remove from group" — CHỈ người tạo thấy), card Mute notifications (Switch) + Leave group (đỏ, confirm); subtitle header = "N members"; `GroupItem` ở MessageScreen hiện avatar nhóm thật. 6 endpoint mới phía server (xem `server/GUIDE.md`), thêm hằng `MAX_GROUP_SIZE=20` vào `core/constants/GroupConstants.kt`. (2) Xóa icon video (SmartDisplay → Setting, thừa) bên phải nút chụp ở bottom bar grid feed (`sampleItems2` — thay bằng placeholder giữ nút chụp ở giữa).
- **2026-07-28 — Hardening bảo mật app** 🔐: `allowBackup="false"` + backup rules đầy đủ (trước đó refresh token Firebase Auth lọt vào Google Drive backup); **bật R8** cho release (`isMinifyEnabled`/`isShrinkResources` + `proguard-rules.pro` thật — giữ DTO cho Gson, strip `Log.d/v/i`); `network_security_config.xml` cấm cleartext + chỉ tin CA hệ thống ở release, overlay `src/debug/res/xml/` vẫn cho HTTP để dev; chặn build release khi `server.base.url` không phải HTTPS; **`TokenAuthenticator`** xử lý 401 (ép refresh → thử lại → hết thì signOut) + `AuthViewModel` lắng nghe `AuthStateListener` để đẩy về Login; **`SessionCleaner`** xóa Coil cache 512MB + `currentUserCache` + pending invite khi đăng xuất; gỡ 2 quyền LOCATION thừa; bỏ email khỏi log; mật khẩu chuyển từ `rememberSaveable` → `remember`; validate email/độ dài mật khẩu ở LoginScreen; xóa `AuthConstants` + `provideCookieManager` chết. **Chi tiết đầy đủ: `../../SECURITY.md` mục 8.** Build debug + release đều PASS — ⚠️ **cần test runtime bản release trên máy thật** (R8 mới bật lần đầu).
- **2026-07-28 — Reply tin nhắn kiểu Messenger**: long-press bubble → hàng 😊|↩ bên cạnh; thanh "Replying to X" trên ô nhập; bubble vẽ khối trích dẫn tin gốc (server snapshot `replyTo*`); áp dụng 1-1 + nhóm, mọi loại tin.
- **2026-07-27 (chiều) — QA bổ sung**: xóa `FLAG_LAYOUT_NO_LIMITS` (fix gốc bàn phím che ô nhập mọi máy); ẩn pill react/reply với bài của chính mình; Settings chỉ còn 1 nút back.
- **2026-07-27 — QA + hoàn thiện chat**: media viewer zoom (ảnh/video) trong chat; reaction tin nhắn (long-press); reply bài đăng gửi kèm media; coop lấy lại nút chụp; xóa toggle W/N + mirror selfie trước; FCM đăng ký mỗi lần mở app + banner 💌 lời mời trên feed; pass light theme các màn chat/profile.
- **2026-07-26 — Hoàn thiện hệ thống**: nút lật camera hoạt động (rebind CameraX theo selector); Download xin quyền storage runtime API < 29.
- **2026-07-26 — Vá lỗi sau review monorepo**: onUserSelected vào LaunchedEffect (hết spam API mỗi recompose); PendingRouteStore thành StateFlow (widget tap khi app đang chạy); AuthInterceptor lọc host (token không rò sang CDN); pager fix (getOrNull, reset pullDown, key MessageInputPill theo bài); chặn double-tap nút chụp.
- **2026-07-26 — Settings thật + Widget Glance + Theme**: SettingScreen dispatch theo SettingIds, Sign Out về Login, theme Dark/Light/System, Edit Name/Birthday (PATCH /users/me), legal/share/rate; widget Glance snapshot local + WorkManager 30'.
- **2026-07-26 — Đại tu camera + feed kiểu Locket**: bỏ nút chụp trong preview (quay video chuyển sang nút center); fix pinch-zoom (pointerInput ≥2 ngón); PostScreen → VerticalPager full-screen + grid + menu ⋯ (Share/Download/Delete); MessageInputPill gửi DM thật tới tác giả.
- **2026-07-26 — Fix settings trống + avatar camera**: settings = config tĩnh local (`SettingDefaults` + `SettingsPreferences`, bỏ hẳn Firestore); CameraScreen fetchCurrentUser + fix lambda lồng + avatarOrDefault.
- **2026-07-19 — Đợt lớn**: UI chuyển toàn bộ sang **tiếng Anh** (~134 chuỗi); UX **camera-first** (login → camera, vuốt lên mở feed); SubmitPhoto = đăng thẳng + tùy chọn gửi kèm chat; App Links domain thật (`hosting/` + assetlinks) + **kết bạn 2 bước** + pendingInviteCode; avatar mình overlay từ `/users/me`, fallback DiceBear thống nhất; fix máy thật (IP LAN, nút center chụp/Send).
- **2026-07-16 — Dọn Compose Preview**: mọi @Preview gọi screen có hiltViewModel đều fail render → thay bằng mock stateless / xóa preview không cứu được.
- **2026-07-13 — Đợt lớn**: Daily Quest + bộ sưu tập khung; Co-op Capture; feed hiển thị khung + video; deep link mời kết bạn; sửa hồ sơ; chat nhóm + ảnh/sticker/voice; migrate NỐT feed You/bạn bè sang API + dọn god-object (MainViewModel/FirestoreRepository); doodle độ dày nét; xóa Locket Gold (miễn phí toàn bộ); đợt dò lỗi 3 vòng (~25 fix); viết lại README.
- **2026-07-12 — Đại tu nền móng**: cấu trúc feature-based; `core/network` (Retrofit + envelope + AuthInterceptor); migrate auth/post/message/friends/profile sang API; caption + reaction; DESIGN.md; màn bạn bè + QR.

---

## 10. Cách cập nhật file này (bắt buộc — xem RULE đầu file)

1. **Mỗi thay đổi** (dù nhỏ) → cập nhật NGAY trong cùng lần làm việc, ghi rõ **thay đổi gì trong thiết kế** vào **Changelog mục 9** (1-3 dòng, mới nhất lên đầu).
2. Màn hình/route mới → sửa **bảng mục 5**; file/package mới → sửa **cây mục 3**; đổi luồng/kiến trúc → sửa **mục 2**.
3. Cập nhật **bảng tiến độ mục 4** (kèm mục "cần test" nếu chưa verify máy thật).
4. Đổi contract API → đồng bộ `server/GUIDE.md` + báo user (3 nơi: app/server/admin).
5. Đổi ngày "Cập nhật lần cuối" ở đầu file.

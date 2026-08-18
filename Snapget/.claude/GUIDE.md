# 🗺️ GUIDE.md — Bản đồ app **Snapget** (Android)

> ## 📌 RULE BẮT BUỘC
> **Luôn cập nhật file GUIDE.md này MỖI KHI có thay đổi** (code, màn hình, route, contract API, thiết kế) — ngay trong cùng lần làm việc, không đợi "xong tính năng". Mỗi lần cập nhật phải ghi rõ **thay đổi gì trong thiết kế**: sửa mục kiến trúc (mục 2) / cấu trúc thư mục (mục 3) / tiến độ (mục 4) / route (mục 5) tương ứng, **và thêm 1 dòng vào Changelog (mục 9)**. Sửa code mà không cập nhật GUIDE = **chưa xong việc**. Chi tiết: mục 10.

> ## 🔐 RULE BẮT BUỘC — SECURITY.md
> **Mọi thay đổi liên quan BẢO MẬT phải cập nhật [`../../SECURITY.md`](../../SECURITY.md) NGAY trong cùng lần sửa** (song song với GUIDE này).
> Tính là thay đổi bảo mật: cách lưu/lấy token & `AuthInterceptor`, xử lý 401 và đăng xuất (dọn cache/dữ liệu local), `AndroidManifest` (permission, `exported`, `allowBackup`, backup rules, deep link), cấu hình mạng (base URL, cleartext, `network_security_config`, cert pinning), `build.gradle.kts` release (R8/`minifyEnabled`, ProGuard, ký APK), logging (token/PII), Firebase (Auth, App Check, Rules), validate input màn đăng nhập/đăng ký.
> Cách cập nhật: sửa đúng mục trong SECURITY.md (đổi trạng thái ✅/⚠️/🔴 + đường dẫn:dòng), gạch việc đã làm khỏi lộ trình mục 14, đổi dòng "Cập nhật lần cuối". Sửa code bảo mật mà không cập nhật SECURITY.md = **chưa xong việc**.

> Tài liệu tham chiếu nhanh để sửa code **không cần đọc lại toàn bộ project**. Luật/quy ước ở `.claude/CLAUDE.md`; UI chuẩn ở `.claude/DESIGN.md`.
> Cập nhật lần cuối: **2026-08-18**.

---

## 1. Snapget là gì

App Android mạng xã hội kiểu **BeReal/Locket**: mở thẳng camera, chụp ảnh / giữ nút quay **"ảnh GIF" ≤3s**, chia sẻ với vòng bạn bè nhỏ (≤20), nhắn tin, gamification (streak + quest + khung ảnh). Đặc tả đầy đủ: 3 file PDF ở root repo.

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

Chụp ảnh / giữ nút quay **"ảnh GIF" ≤3s** (CameraX, không tiếng, selfie mirror bake vào pixel) → `EditMediaScreen` (khung/filter/doodle; GIF chỉ chọn khung) → upload `POST /upload` (server đẩy Cloudinary, trả URL) → tạo moment/message qua API. App chỉ cầm URL, không giữ secret Cloudinary. Co-op: mỗi bên chụp nửa ảnh, **server ghép** bằng sharp.

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
  │                         #   SessionCleaner (🔐 xóa Coil cache + user cache + pending invite + RESET
  │                         #   skin/hiệu ứng khi đăng xuất — vật phẩm gắn với tài khoản, không gắn với máy),
  │                         #   SettingDefaults (23 mục settings tĩnh + SettingIds + visible),
  │                         #   SettingsPreferences (toggle + **skinId** + **touchEffectId** qua SharedPreferences),
  │                         #   MainLog/MainLogImpl, Store/StoreImpl2, SampleData (chỉ cho @Preview)
  designsystem/
    component/              #   UI tái sử dụng theo loại: bottombar/ button/ circle/ common/ container/
    │                       #   empty/ frame/ grid/ indicator/ input/ list/ pill/ sheet/ topbar/
    collectible/            #   (trong component/) CollectibleItem — ô lưới dùng chung cho cả 3 tab Appearance
    effect/                 #   **(2026-08-05 — P4; 2026-08-11 làm lại bằng SPRITESHEET)** HIỆU ỨNG TOUCH:
    │                       #   TouchEffect (model: sheet/frameCount/columns/fps/durationMs/sizeDp/
    │                       #   fadeStart/thumbFrame + rows & playbackMs suy ra) + TouchEffectRegistry
    │                       #   (None + Flower; tối đa 10 hiệu ứng, sheet ở drawable-nodpi/effectN_sheet.png) +
    │                       #   TouchEffectOverlay (bọc NavHost, Initial pass KHÔNG consume; đồng hồ
    │                       #   withFrameMillis CHỈ chạy khi còn animation; drawTouchEffectFrame cắt frame
    │                       #   bằng drawImage srcOffset/srcSize — KHÔNG còn particle system, xem Changelog)
    │                       #   + TouchEffectController (tắt tạm theo ngữ cảnh — màn camera bật cờ lúc quay GIF)
    skin/                   #   **(2026-08-05 — P0/P1)** ENGINE SKIN: AppSkin + SkinColors/Icons/Shapes/Images,
    │                       #   LocalAppSkin + `SkinTheme` (cổng đọc token), SkinIcon (fallback Material),
    │                       #   SkinShapeDefaults, SkinRegistry (skin bundled, find() chịu được id lạ),
    │                       #   skins/: DefaultSkin (0 — thay hẳn Color.kt cũ) · SnowSkin (1) · ForestSkin (2)
    theme/                  #   Type, Theme (AppTheme(skin)) — Color.kt đã XÓA, palette Light đã gỡ hẳn
    preview/                #   @Preview composable (không chạy runtime)
  di/                       #   Hilt module: AppModule, FirebaseModule, NetworkModule, RepositoriesModule
  fcm/                      #   SnapgetMessagingService (@AndroidEntryPoint; onNewToken → POST /users/me/fcm-tokens)
  model/                    #   User, Post, Message, Friendship, Setting (+ auth/AuthState, AuthUser)
  │                         #   — ThemeMode đã XÓA 2026-08-05 (gỡ giao diện Light)
  network/                  #   Retrofit api/ + dto/ + interceptor/ + ErrorMessages (xem mục 2.3)
  ui/                       #   MainViewModel (currentUser + **skin** + **touchEffect** — map id → object cho
  │                         #   AppTheme và TouchEffectOverlay ở MainActivity)
  util/                     #   DatetimeUtils (relativeTimeShort "1d"), MainUtils (avatarOrDefault DiceBear),
  │                         #   EdgeToEdge, PaddingValues, MediaActions (Share FileProvider + Download MediaStore)

feature/                    # Mỗi tính năng 1 package: screen + viewmodel + data riêng
  appearance/               #   **(2026-08-05 — P3)** AppearanceScreen (3 tab Frames|Skins|Effects) +
  │                         #   AppearanceViewModel (khoá theo sở hữu — lớp thứ 2 sau lớp UI)
  gacha/                    #   **(2026-08-05 — G4/G6)** GachaScreen (quay x1/x10, popup Rule, nút `+` nạp),
  │                         #   GachaResultSheet (overlay lá bài lật lần lượt + Skip), GachaRarity (4 màu bậc —
  │                         #   CỐ Ý không nằm trong SkinColors), TopupSheet (popup gói nạp + banner chờ
  │                         #   thanh toán + popup đã cộng), GachaViewModel (gồm cả state luồng nạp),
  │                         #   data/GachaRepository + data/TopupRepository
  │                         #   GachaLayout.kt **(2026-08-06)** = TOÀN BỘ toạ độ bám theo ảnh nền
  │                         #   (khung Astrite vẽ sẵn, 2 nút quay, lưới lá bài 2-3-3-2) + lớp BgAnchor
  │                         #   đổi toạ độ-trong-ảnh → toạ độ-màn-hình. Sửa vị trí UI chỉ đụng file này.
  auth/                     #   LoginScreen (kèm Forgot Password), AuthViewModel, data/AuthRepository
  camera/                   #   CameraScreen (CameraX: chụp/quay nút center, pinch-zoom, lật cam, nút Co-op
  │                         #   mở popup chọn bạn gửi lời mời, vuốt lên mở feed, mirror selfie)
  coop/                     #   CoopCaptureScreen (màn chụp coop nửa camera + CoopFriendPickerDialog),
  │                         #   CoopViewModel (poll 2.5s), data/CoopRepository (/moments/coop)
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
| UX camera-first: login → thẳng camera; vuốt lên mở feed; **giữ nút center = quay "ảnh GIF" ≤3s** (không tiếng, không đồng hồ/vòng tiến độ, tự dừng ở 3s, giữ tối thiểu 0.8s); pinch-zoom; lật cam 🔄; mirror selfie | ✅ |
| Đăng bài: chụp → EditMedia (khung/filter/doodle 3 mức nét) → SubmitPhoto (caption ≤30, tùy chọn gửi kèm chat 1 bạn/Everyone) → upload + moment | ✅ |
| Feed: VerticalPager full-screen + grid tổng hợp; khung overlay + video ExoPlayer; mark-seen theo trang hiển thị; reaction emoji bay; gõ text = DM tác giả; menu ⋯ Share/Download/Delete (bài mình) | ✅ |
| Bạn bè (≤20): sheet bạn bè + streak 🔥; QR + link mời TTL 30 ngày; App Links domain thật; **kết bạn 2 bước** (PENDING → chủ link accept/decline) + banner 💌 trên feed | ✅ |
| Chat: 1-1 + nhóm ≤20; TEXT/EMOJI/PHOTO/STICKER/VOICE; media viewer zoom; reaction (long-press); **reply kiểu Messenger** (trích dẫn tin gốc); polling 5s; mark seen | ✅ |
| Quản lý nhóm chat (sheet ⋯ trên header, theo mẫu Messenger): đổi tên + đổi avatar nhóm (upload Cloudinary), mời bạn vào nhóm (≤20), xóa thành viên (chỉ người tạo), rời nhóm, mute thông báo (server bỏ qua khi push FCM) | ✅ |
| Co-op capture **(redesign 2026-08-02)**: nút Co-op → popup chọn bạn → lời mời 5 phút → accept → màn chụp coop (nửa camera + nửa xám chờ, nút chụp/again/send) → server ghép → cả 2 vào luồng edit → đăng bài thường | ✅ |
| Profile: streak thật, calendar moment, edit tên/avatar; profile người khác chỉ bạn bè xem | ✅ |
| Daily Quest; thưởng 2/2 quest = **+60 Astrite**; banner mở màn Gacha (2026-08-05); **(2026-08-15)** quest thứ 3 `AI_CHALLENGE` do AI tạo — icon 🎯, hoàn thành bằng đăng bài thường (server AI-xác-minh ảnh), toast kết quả (+30 Astrite / thử lại) ở `SubmitPhotoScreen` | ✅ (⬜ chưa thấy trên máy — server còn tắt env AI) |
| **Appearance** 3 tab: Frames (bộ sưu tập khung) · Skins (Default/Snow/Forest) · Effects (None + 5, ô demo chạy thật) — khoá theo sở hữu | ✅ |
| **Hiệu ứng touch** toàn app (overlay bọc NavHost, không chặn nút/pager/giữ-quay-GIF) | ✅ |
| **Gacha**: quay x1/x10, popup Rule sinh từ server, kết quả lật lần lượt + Skip, hoàn Astrite khi trùng | ✅ |
| **Nạp Astrite** (PayOS — TIỀN THẬT): nút `+` cạnh số dư → popup gói nạp → Chrome Custom Tabs → app poll đơn → popup đã cộng | ✅ |
| Settings hoạt động thật: theme Dark/Light/System, edit name/birthday, share/rate/MXH, Terms/Privacy, sign out | ✅ |
| Widget Glance (moment mới nhất + streak, offline được) | ✅ |
| UI tiếng Anh toàn bộ; avatar fallback DiceBear thống nhất; FCM đăng ký mỗi lần mở app | ✅ |
| **Hardening bảo mật** 🔐: tắt backup, R8 + obfuscate, cấm cleartext + chỉ tin CA hệ thống, xử lý 401 + auto-logout, dọn sạch phiên khi đăng xuất, gỡ quyền thừa, không log PII (chi tiết: `../../SECURITY.md` mục 8) | ✅ |

**⬜ Cần test trên emulator/máy thật (2 máy)** — code xong, chưa verify end-to-end: reply chat (1-1 + nhóm), **sheet cài đặt nhóm** (đổi tên/avatar, mời/xóa thành viên, rời nhóm, mute — cần 2 máy để xem phía thành viên bị xóa/không nhận push khi mute), reaction/zoom media trong chat, **coop flow mới 2 máy** (mời → accept → chụp 2 nửa → ghép → cả 2 vào edit; hết hạn 5 phút; hủy lời mời), mirror selfie, FCM push (lời mời/tin nhắn), App Links (cài APK debug — verify theo SHA-256 debug keystore), widget (đặt/toggle/tap), pinch-zoom + quay video nút center, share/download/xóa bài, bàn phím không che ô nhập, theme light toàn màn.

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
| `daily_quest` | `feature/quest/DailyQuestScreen.kt` | Entry: nút 🏆 top bar feed; ẩn bottom bar. **(2026-08-05)** có banner Gacha ở trên cùng; bỏ lưới Frame collection |
| `appearance` | `feature/appearance/AppearanceScreen.kt` | **(2026-08-05)** 3 tab Frames/Skins/Effects. Entry: mục **Appearance** trong Settings (thay mục Theme cũ); ẩn bottom bar |
| `gacha` | `feature/gacha/GachaScreen.kt` | **(2026-08-05)** Quay x1/x10 + popup Rule + overlay kết quả + **popup nạp Astrite** (nút `+` cạnh số dư, G6). Entry: banner trên `daily_quest`; ẩn bottom bar. **Màn DUY NHẤT hiện số dư Astrite** |
| `coop_capture?inviteId=&name=` | `feature/coop/CoopCaptureScreen.kt` | Màn chụp coop (chờ accept → chụp nửa ảnh → chờ ghép); entry: nút Co-op camera (inviter) / banner vàng feed (invitee); ẩn bottom bar |
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

- **2026-08-18 — Cắm art THẬT của skin 1 (Snow) + đồng bộ nút chụp ở 4 màn**. Skin Snow hết placeholder: 12 icon + nút chụp + thumbnail đều là art của user.
  - 🖼️ **12 icon skin 1 chuyển sang WebP, KHÔNG phải vector XML** — user gửi `.svg` nhưng ruột 12 file đó là **PNG bitmap nhúng trong vỏ SVG** (`<image xlink:href="data:image/png;base64,…">` + `<mask>` + `feColorMatrix`; file 0.3–5.8MB, tổng 26MB). Vector drawable của Android không đỡ được `<image>`/`<mask>`/`<filter>`, convert thẳng chỉ còn lại mấy path mũi tên xanh phẳng, mất sạch tuyết/chim cánh cụt/thông. Cách làm: render bằng **Chrome headless 2 lần (nền trắng + nền đen) rồi giải hệ ra alpha** (`a = 1−(W−B)`, `C = B/a`) — chính xác tuyệt đối kể cả rìa khử răng cưa, không phải key màu nền. Xuất WebP lossless RGBA vào `drawable-nodpi/`: **158KB cho cả 12 icon**. Tên resource không đổi (`R.drawable.skin1_ic_*`) nên **không sửa dòng code nào**.
  - 📐 Cỡ px = **4× cỡ dp lớn nhất icon đó thực sự được vẽ** → 192×192 cho 11 icon (tối đa 40dp ở bottom bar), **288×288 cho `camera`** (có thể nằm ở nút center 66dp). Chọn cỡ theo nhu cầu thật thay vì một cỡ to dùng chung là để tiết kiệm RAM: bitmap 320² tốn 410KB RAM/icon, `minSdk 24` vẫn phải chạy máy yếu.
  - 📁 SVG gốc (26MB) **chuyển khỏi `res/`** sang `Sources/skin-assets/skin1_snow/` — để trong `res/drawable/` là aapt2 fail build ngay. Thư mục `res-skins/skin1_snow/drawable/` giờ rỗng và đã xoá.
  - 🔘 **`CaptureButton` mới** (`core/designsystem/component/button/`) — gom chỗ quyết định "vẽ ảnh nút chụp của skin hay vòng tròn viền accent" về **một** nơi. Trước đó chỉ bottom bar màn camera đọc `SkinImages.captureButton`, còn feed / post detail / coop vẫn vẽ `Circle` cứng bằng code → cắm art thật vào là 4 chỗ nhìn lệch hẳn nhau. Nay cả 4 dùng chung; nhánh nút center **có icon** (không phải nút chụp) giữ nguyên `Circle` + icon như cũ.

- **2026-08-16 — Phạm vi AI quest chốt lại (không đổi code app)**: AI chỉ **xác minh ảnh**, nội dung quest từ bộ mẫu server (72 câu, 9 lớp ra đề); AI service chạy Google Cloud Run thay HF Space. App không đụng — `type`/`targetClass`/`aiQuest` vẫn như 2026-08-15. Xem `QUEST_AI_PLAN.md` mục 0, 2.3, 17.
- **2026-08-15 — Đồng bộ contract AI Daily Quest (M5 của `QUEST_AI_PLAN.md`)** — 3 điểm chạm nhỏ, không màn hình mới, không dependency mới. (1) `TodayQuestDto` thêm `targetClass: String? = null` (chỉ để đọc, UI không hiện); comment ghi rõ `type` có thêm `AI_CHALLENGE`. (2) `MomentDto` thêm `aiQuest: AiQuestResultDto? = null` (`result` MATCHED/NOT_MATCHED/SKIPPED, `score?`, `questContent?`) — chỉ có trong response `POST /moments`, null ở feed. (3) `DailyQuestScreen` icon `when(type)`: POST_MOMENT 📸 · **AI_CHALLENGE 🎯** · else 👋. (4) `PostViewModel.submitPhoto` **bắt đầu đọc body createMoment** → `aiQuestMessage` StateFlow → `SubmitPhotoScreen` toast: MATCHED "🎯 Challenge complete! +30 Astrite", NOT_MATCHED "Photo doesn't match today's challenge — post another one to try again!", SKIPPED/null im lặng (đặt trước khi `submitStatus=Success` để toast kịp trước khi điều hướng về feed). App cũ gặp quest AI chỉ sai icon, không crash (type là String, render động).

- **2026-08-14 — Nối nốt 7 icon skin còn treo + icon skin KHÔNG bị tint nữa (user chốt)**. Trước đợt này chỉ **5/12** icon Nhóm 1 thực sự vẽ ra màn hình (`gallery`, `flipCamera`, `close`, `captions` ở bottom bar camera; `back` ở `SimpleTopBar`); 7 icon còn lại khai đủ trong `SkinIcons` nhưng **không call site nào đọc** → thiết kế thật cắm vào cũng không đổi gì. Nay đủ 12/12 qua 13 call site.
  - 🎨 **Bỏ tint cho icon của skin** (đây là thay đổi hành vi, không chỉ nối dây): `SkinIcon` vẽ nhánh skin bằng `tint = Color.Unspecified`, `MainBottomBar` cũng vậy. User tự tô màu cả bộ icon cho khớp bảng màu skin → app tint vào là bẹt hết về 1 màu. `tint` giờ **chỉ còn tác dụng ở nhánh fallback Material** (vector đơn sắc, không tint thì chìm vào nền). Quy cách thiết kế cập nhật ở `SKIN_PLAN.md` mục 6.13.1.
  - 🔌 **Call site mới**: `grid` + `more` (PostScreen, PostDetailScreen — hàng nút đáy feed) · `chevronRight` (MessageScreen ×2, FriendListComponent, MainTopBar ×2) · `chevronDown` (MainTopBar — pill "Everyone ⌄") · `chat` (MainTopBar — nút góc phải) · `send` (ChatScreen, MessagePill, nút center 80dp của `submitPhotoBar`, CoopCaptureScreen) · `camera` (ô "Camera" trong PostGrid + nút center khi skin không có `captureButton`) · `more` (GroupSettingsSheet).
  - 🧩 **`IconSetting` thêm `skinRes`** để icon skin đi được qua `Circle` (nút Send tròn 80dp xoay −45° dùng `iconSetting`, không nhận painter); `Circle` gọi `SkinIcon` thay `Icon` nên fallback + luật tint dùng chung một chỗ.
  - ⛔ **Cố ý KHÔNG đổi**: `SettingScreen` `PhotoCamera` (là logo Instagram, không phải icon camera) · `UserProfileTopBar` `KeyboardArrowRight` (nút điều hướng Home, khác vai trò với chevron cuối hàng danh sách) · `ShowMoreShowLessButton` (cần cả `ExpandLess`/`ExpandMore`, skin mới có 1 chiều) · các `@Preview`.

- **2026-08-11 — Hiệu ứng chạm làm lại bằng SPRITESHEET one-shot; xoá hẳn particle system (user chốt)**. User xoá 5 PNG hạt cũ khỏi `res/drawable-nodpi/` (app không build được vì `R.drawable.effectN_particle` mất) và cấp sheet mới `effect1_sheet.png` — **Flower, id 1**. Mô hình mới: **1 lần chạm = 1 animation tại điểm chạm**, chuyển động do ART quyết định chứ không do code.
  - 🗑️ **Xoá khỏi `TouchEffect`**: `particleCount`, `distanceDp`, `swayDp`, `spinDegPerSec`, `scaleFrom/To`, `useSkinAccent`, `particleAsset` và **cả enum `EmitDirection`**. Xoá `ParticleStyle` + `rememberParticleStyle` + `drawEmission`; `drawParticle` (vòng lặp hạt + `sin/cos` tính quỹ đạo + `rotate` từng hạt) thay bằng `drawTouchEffectFrame` cắt frame bằng `drawImage(srcOffset, srcSize)`. Thêm: `sheet`, `frameCount`, `columns`, `fps`, `sizeDp` (cỡ cả animation, không phải 1 hạt), `thumbFrame` + 2 thuộc tính suy ra `rows`/`playbackMs`.
  - ⏱️ **`durationMs` giờ là TỔNG vòng đời, dài hơn thời gian chạy frame**: frame index suy trực tiếp từ `fps` rồi ép trần về frame cuối, nên "giữ frame cuối" không cần nhánh xử lý riêng. Cần vậy vì sheet `Flower` **không tự tan** (frame 7 là frame đặc nhất) → `fadeStart = 0.6f` cho code fade hộ, nếu không hoa biến mất đột ngột. Test khoá `playbackMs <= durationMs` (không thì animation bị cắt đuôi) + `rows × columns >= frameCount` (không thì frame cuối nằm ngoài lưới).
  - 🎁 **Ảnh đại diện gacha**: vẽ cả sheet bằng `painterResource` sẽ ra một cái lưới 8 ô → `localItemAsset` tách thành `localSkinThumbnail` (vẫn drawable đơn) + `localEffect` (trả `TouchEffect`) và thêm `EffectThumbnail` cắt đúng `thumbFrame`. **Bỏ `ColorFilter.tint` theo màu bậc** cho EFFECT — sheet đã có màu sẵn, tint là bẹt cả bó hoa nhiều màu thành một khối.
  - 🖼️ Ô demo tab Effects lấy cỡ vẽ theo **kích thước ô** (`minOf(w,h) * 0.9f`) thay cho hack `density * 0.55f` cũ.
  - 🌸 **Cùng ngày, user cấp thêm 3 sheet** → kho hiện có **4 hiệu ứng**: `Flower` (1) · `Snowflake` (2) · `Leaf` (3) · `Magic` (4). Cả 3 sheet mới cũng là 768×384 lưới 4×2, nhưng **ô thứ 8 để trống hoàn toàn** (alpha 0 tuyệt đối) → khai `frameCount = 7`, không phải 8; khai 8 thì 1/8 chặng cuối vẽ một frame rỗng (hiệu ứng chớp tắt rồi đứng đó hết `durationMs`). Art của 3 cái này **tự loãng ở frame 6** rồi cắt phựt sang rỗng, nên giữ frame 6 làm frame cuối + `fadeStart` tan nốt. Đuôi tĩnh giữ ~260–285ms (bản nháp đầu để >300ms — quá ngưỡng đọc thành "đứng hình").
  - ⚠️ **Server còn nợ** (script seed **chỉ thêm item thiếu, không sửa item đã có**): trên Firestore đã seed bản cũ phải sửa tay ở trang admin cho khớp `TouchEffectRegistry` — đổi `itemName` của `refId '1'→Flower`, `'2'→Snowflake`, `'3'→Leaf`, `'4'→Magic`, và **`isActive = false` cho `refId '5'`** (id 5 chưa có sheet). [seed-gacha.ts](server/scripts/seed-gacha.ts) đã cập nhật cho lần seed mới.

- **2026-08-07 — Dọn sạch 175 lint warning → `0 errors, 0 warnings`; bật `warningsAsErrors`**. Từ nay `./gradlew :app:lintDebug` **fail nếu có bất kỳ warning nào** — có cảnh báo mới thì sửa, không để dồn.
  - ⚡ **2 lỗi hiệu năng thật**: `Modifier.offset(x, y)` với giá trị state ở overlay emoji bay (`PostDetailScreen`, chạy animation mỗi frame) và vòng lấy nét camera (`CameraPreview`) → đổi sang **bản lambda** `offset { IntOffset(...) }`, chỉ chạy lại khâu đặt vị trí thay vì recompose cả composable mỗi frame.
  - 🖼️ **`android.media.ExifInterface` → `androidx.exifinterface`** (5 chỗ trong `normalizeCapturedPhoto`): bản androidx đọc được tag trên mọi bản Android; dependency đã có sẵn từ trước, chỉ chưa dùng.
  - 🌍 **`String.format("%.1f")` → kèm `Locale.US`** (2 chỗ hiện mức zoom): máy đặt locale Đức/Pháp hiện "1,5x" thay vì "1.5x".
  - 🔑 **Google Sign-In bắt riêng `NoCredentialException`**: máy chưa có tài khoản Google nào thì trước đây rơi vào catch chung và hiện "Google sign-in failed: <lỗi kỹ thuật>" — nay báo "No Google account on this device…".
  - 🧹 **KTX 16 chỗ** (`prefs.edit { }`, `String.toUri()`, `createBitmap()`); **`modifier` về đúng vị trí tham số tuỳ chọn đầu tiên** ở 11 composable (quy ước Compose, mọi call site đều dùng named arg nên không phá gì); **guava** chuyển từ hardcode version vào `libs.versions.toml` (đúng luật CLAUDE.md mục 2).
  - 🗑️ **Resource**: xoá 7 màu mặc định Android Studio + `Theme.MVVM.SplashTheme` + `messenger.png` + 5 file `ic_launcher_monochrome.png` (bản sao y hệt foreground → `<monochrome>` trỏ thẳng vào foreground) + `ic_launcher_round.xml` + 4 webp `*_default`; 5 PNG bitmap chuyển từ `drawable/` sang `drawable-nodpi/` (đúng chỗ cho ảnh không co giãn); 2 file `drawable/ic_launcher_*.xml` template của AS bị xoá, `CirclePreview` trỏ sang `R.mipmap.ic_launcher`.
  - 🤐 **4 nhóm cố ý tắt, có ghi lý do tại chỗ**: 116 cảnh báo "có bản thư viện mới hơn" (`GradleDependency`/`SimilarGradleDependency`/`AndroidGradlePluginVersion` — phiên bản đã chốt trong TOML, nâng hàng loạt ngay trước bảo vệ là rủi ro thuần tuý); `UnusedAttribute` ở widget (attr API 31+ là progressive enhancement); 2 cảnh báo ở `src/debug/.../network_security_config.xml` (file **chỉ** vào bản debug, release dùng file ở `src/main`); `DiscouragedApi` + `CredentialManagerSignInWithGoogle` ở `AuthRepository` (cái sau lint **báo nhầm** — code có dùng `GoogleIdTokenCredential`).

- **2026-08-06 — Soát sau đợt res-skins + chạy Android Lint lần đầu: vá 3 lỗi (1 crash thật)**. Merge resource chạy lại từ đầu sạch (APK đủ 28 resource skin, không trùng tên — mấy cặp `ic_launcher*` "trùng" chỉ là biến thể mật độ mipmap, bình thường). Lint `10 errors → 0`:
  - 🔴 **App crash lúc mở trên Android 7.0/7.1**: `SnapgetMessagingService.createDefaultChannel` gọi `NotificationChannel` (class chỉ có từ API 26) không rào SDK, mà hàm này chạy ngay trong `MainApplication.onCreate` → `NoClassDefFoundError` trên máy API 24/25. Thêm rào `SDK_INT < O → return`.
  - 🟠 2 chỗ dùng API `UnstableApi` của media3 (`RESIZE_MODE_ZOOM/FIT` trong `GifVideoPlayer` + `MediaViewerDialog`) chưa gắn `@androidx.annotation.OptIn` — giờ opt-in có chủ đích, lint hết chặn. Còn 175 warning (locale, ExifInterface cũ…) — không chặn, để đợt dọn riêng.
- **2026-08-06 — Asset skin gom về `res-skins/<skin>/` (user chốt) + đăng ký với Gradle**. User di chuyển 24 icon XML + nút chụp + thumbnail từ `res/` vào `app/src/main/res-skins/skin1_snow|skin2_forest/` (mỗi skin 1 thư mục, bên trong vẫn là `drawable/`, `drawable-nodpi/` chuẩn) — nhưng Gradle chưa biết thư mục này nên build gãy "Unresolved reference R.drawable.skin1_ic_*" và tưởng nhầm là mất file. Fix: khai `sourceSets { res.srcDirs("src/main/res", "src/main/res-skins/skin1_snow", "src/main/res-skins/skin2_forest") }` trong `app/build.gradle.kts` — AAPT gộp cả 3 thư mục lúc build (đã kiểm APK chứa đủ 28 resource skin). ➜ **Từ giờ asset của skin nào nằm gọn trong thư mục skin đó**; thêm skin 3 = tạo `res-skins/skin3_x/` + thêm 1 dòng srcDirs. ⚠️ Sự cố cùng đợt: thư mục `Sources/skin-assets` bị xoá kéo theo cả các file đã move (nằm trong đó do kéo-thả nhầm trước khi commit `upd` sắp xếp lại) — đã khôi phục từ git; `Sources/skin-assets` giờ chỉ còn bản gốc SVG/spec, xoá được an toàn.

- **2026-08-06 — P5 nối dây nốt: nút chụp theo skin + đường ống SkinIcon (5 test mới, 21 test pass)**. Phát hiện khi rà: 24 SVG icon + 2 nút chụp trong `Sources/skin-assets/skin1_snow|skin2_forest/` đều là **PLACEHOLDER** (ruột y hệt icon Material fallback) — cắm icon vào cũng không đổi gì trên màn hình, nên phần "làm được thật" là nối ĐƯỜNG ỐNG cho asset thật sau này:
  - 📸 **Nút chụp theo skin chạy thật**: copy 2 webp vào `drawable-nodpi`, khai `images = SkinImages(captureButton=...)` ở Snow/Forest; nhánh center "Take a picture"/"Camera" của `MainBottomBar` vẽ ảnh đó (Circle chỉ còn làm khung bắt tap/giữ/thả, không viền) — đổi skin là nút chụp đổi ảnh ngay. Default giữ nút vẽ bằng token như cũ. Thay ảnh thật = ghi đè `skinN_btn_capture.webp`.
  - 🔌 **`SkinIcon` trước đó 0 chỗ gọi** — giờ: `SimpleTopBar` back đi qua `SkinIcon` (1 chỗ sửa ăn mọi màn dùng top bar); `BottomNavItem` thêm `skinIcon: (SkinIcons) -> Int?` (lambda chọn field theo skin ĐANG dùng lúc runtime, không phải id cứng) — đã khai cho Photo Library→gallery, Change camera→flipCamera, Cancel→close, Captions List→captions. Skin chưa vẽ icon → fallback Material, không màn nào vỡ.
  - ✅ `SkinRegistryTest` (5 test): id không trùng, `find` id lạ rơi về Default, Snow/Forest có captureButton còn Default thì không, và **từng selector `skinIcon` trỏ đúng field** (SkinIcons giả mỗi field một giá trị — chọn nhầm field là compile vẫn qua nhưng test đỏ ngay).
  - 📌 **(bổ sung cùng ngày — user chốt)** 24 icon placeholder cũng đã convert SVG→VectorDrawable (`res/drawable/skin1_ic_*.xml` + `skin2_ic_*.xml`, script `svg2vd.py` xử lý path/rect/circle + stroke) và khai **đủ 12 field** `icons = SkinIcons(...)` trong cả SnowSkin lẫn ForestSkin. Test mới "skin gacha khai đủ 12 icon" (22 test pass). ➜ **Thay thiết kế thật = ghi đè file cùng tên, KHÔNG sửa code**: icon vào `res/drawable/skinN_ic_*.xml` (SVG thì convert qua Android Studio Vector Asset hoặc nhờ Claude), nút chụp vào `res/drawable-nodpi/skinN_btn_capture.webp`, thumbnail `skinN_thumb.webp`. P5 giờ chỉ còn chờ file thiết kế.

- **2026-08-06 — Soát 3 vòng màn Gacha mới trước khi xuất APK test: vá 6 lỗi + 9 unit test layout**.
  - 🔴 **Số Astrite đè lên dấu `+`**: ô chữ tính bề rộng theo icon nằm đè mép khung nhưng icon lại bị offset vào trong 34px → chồng 28px (bắt được bằng render phép toán thật lên ảnh nền). Bỏ offset — tâm `+` đặt đúng `BAR_RIGHT`, nửa trong nửa ngoài viền như mock đã duyệt.
  - 🟠 `isRolling: Boolean` → `rollingTimes: Int?` — trước đó bấm quay x1 thì **cả 2 nút cùng hiện spinner** (nhìn như treo app); giờ spinner chỉ nằm trên nút vừa bấm, nút kia mờ đi.
  - 🟠 Vùng chạm dưới chuẩn 48dp: thanh Astrite vẽ sẵn chỉ cao ~24dp, icon back/luật ~35dp → `GlassIconButton` nhận **tâm** và tự nới vùng chạm ra ≥48dp quanh tâm (phần vẽ giữ nguyên); thanh Astrite cũng nới cao ≥48dp đối xứng quanh trục khung.
  - 🟠 **Lộ kết quả sớm ở màn lật bài**: tên vật phẩm hiện ngay khi bắt đầu lật (mặt trước ~190ms sau mới lộ) → caption chỉ hiện khi `rotation > 90°`; dòng "+X Astrite returned" + Balance hiện từ đầu (nhìn tổng hoàn đoán được số lá trùng) → chỉ hiện khi đã lật hết.
  - ⭐→🖼️ 3 dòng Duplicate trong popup Rule là chỗ ⭐ emoji cuối cùng → `RuleRow(astriteIcon=true)` vẽ `ic_astrite` 14dp. (⭐ trong `SampleData.kt` là caption bài mẫu, không phải icon tiền — giữ.)
  - ✅ `GachaLayoutTest` (9 test): bất biến Crop+TopCenter của `BgAnchor` trên 4 tỉ lệ màn (19.5:9 / 20:9 / 16:9 / 720p), `cardRows` tổng đúng mọi count 1..30, hình học khung Astrite (chữ không được chạm dấu `+`), bề ngang 2 nút khớp bản thiết kế (chấp nhận lệch <8px — bản vẽ gốc lệch trái 4px). **16 test app pass.**
- **2026-08-06 — Màn Gacha dựng lại theo bộ asset thật (2 nút quay + 8 lá bài)**. User cấp `gacha_bg`/`gacha_banner` bản mới, `gacha_1rollbutton`/`gacha_10rollbutton` (SVG → PNG, xem dưới), 8 lá bài `gacha_front{n,r,sr,ssr}card` + `gacha_backside{n,r,sr,ssr}card` (217×364), kèm 2 ảnh preview làm chuẩn bố cục. Toạ độ trong file mới gom vào [GachaLayout.kt](Snapget/app/src/main/java/com/example/snapget/feature/gacha/GachaLayout.kt) — sửa vị trí chỉ đụng 1 file.
  - 📐 **`BgAnchor` — neo UI theo toạ độ TRONG ảnh nền, không theo % màn hình.** `gacha_bg` **đã vẽ sẵn khung số Astrite** ở góc phải trên; ảnh vẽ bằng `ContentScale.Crop` nên máy tỉ lệ khác 19.5:9 sẽ phóng to rồi cắt bớt — neo theo % màn hình là số Astrite trượt ra ngoài khung vẽ sẵn. `BgAnchor` lặp lại đúng phép biến đổi của `Crop` + **`Alignment.TopCenter`** (cắt đều 2 bên, giữ nguyên mép trên) nên hàng header không bao giờ bị cắt. Riêng 2 nút quay neo theo **đáy màn hình**: máy 16:9 bị cắt phần dưới ảnh, neo theo ảnh là nút nằm ngoài màn.
  - 🎛️ Nút back + icon luật đặt cùng hàng với khung Astrite (user chọn), dấu `+` nạp tiền đặt đúng mép phải khung. Cỡ chữ số dư **giảm dần theo độ dài** — bề ngang còn lại giữa viên pha lê vẽ sẵn và dấu `+` chỉ ~145px, gói test 5.201.314 để cỡ cố định là mất chữ số.
  - 🃏 **Màn kết quả dùng lá bài thật**: mặt sau **màu theo bậc** (bộ asset không có mặt sau trung tính → thấy viền vàng là biết sắp ra SSR), lật 3D bằng `graphicsLayer{rotationY}` + `cameraDistance` (thiếu `cameraDistance` thì lá bài phình méo giữa chừng). Ảnh vật phẩm đặt vào ô trống giữa khung (đo từ tâm lá bài ra: x 22..193, y 55..270); tên vật phẩm nằm **dưới lá bài** (user chọn) trong ô cao cố định để lá bài không giật vị trí lúc lật xong.
  - 🎯 Bố cục x10 giữ **2–3–3–2** như bản thiết kế nhưng **căn giữa tuyệt đối** — bản gốc lệch trái ~15px (user chỉ ra). Cụm 10 lá + tên cao ~730dp ở cỡ gốc nên có bước **tự thu nhỏ đều** khi chiều cao còn lại không đủ, thay vì để hàng cuối bị cắt.
  - ⚠️ **2 nút quay là SVG nhưng ruột là bitmap 1024×351 nhúng base64** (Android không đọc SVG) → đã tách ra PNG. Vẽ bằng `FillBounds` vào khung 386×139 chứ không `Fit`: file SVG có transform làm ảnh cao hơn tỉ lệ bitmap, chỉ `FillBounds` mới ra đúng hình dạng trong ảnh preview. Nút là **pill trơn không có chữ** nên "x1 / x10" + giá do code vẽ đè.

- **2026-08-06 — Hiệu ứng chạm vẽ bằng ẢNH THẬT + vá crash/lag khi bấm liên tục**. Copy 5 PNG `Sources/skin-assets/effects/effectN_particle.png` → `res/drawable-nodpi/`, `TouchEffect.particleAsset` được cắm cho cả 5 hiệu ứng. **Xoá hẳn** `ParticleShape` + 2 hàm dựng `Path`. Thêm 6 unit test (`TouchEffectRegistryTest`) khoá bất biến "hiệu ung quay ra được thì phải có ảnh hạt". Tham số Ember chỉnh lại đúng phiếu `EFFECTS.md` (10 hạt, scale→0.4, fade 0.55).
  - 🎨 **Hạt không giống ảnh gốc.** Cả 5 hiệu ứng đang vẽ tay bằng `Canvas` (`CIRCLE/RING/SPARK/LEAF`) nên bông tuyết 6 cánh và ember hình thoi đều ra **hình tròn**. Nay vẽ thẳng ảnh bằng `drawImage` + `ColorFilter.tint` (ăn màu skin y như cũ).
  - 🔴 **Bấm liên tục ở tab Effects → sập app.** `LaunchedEffect(emissionSeq)` gọi `emissions.last()` NGAY SAU `withFrameMillis`; trong lúc chờ frame, nhánh `LaunchedEffect(active)` có thể vừa `clear()` danh sách → `NoSuchElementException`. Kịch bản thật: bấm nhanh vài ô rồi bấm **None**. Nay dùng `lastOrNull() ?: break`.
  - 🟠 **Bấm nhiều → ô demo tắt ngúm.** `EffectDemoCell` cho `round` tăng vô hạn rồi `animateFloatAsState` chạy đuổi theo; bấm 10 phát/giây làm `cycle = progress - (round-1)` âm cả chục → không vẽ gì. Đổi sang `Animatable` + `snapTo(0)`: luôn đúng 1 animation sống.
  - ⚡ **Lag khi hạt đang bay.** (1) `sparkPath()`/`leafPath()` cấp phát `Path` mới cho **mỗi hạt mỗi frame** (~4.800 object/giây → GC liên tục) — nay không còn Path nào; (2) Canvas hạt thêm `graphicsLayer()` để có RenderNode riêng, không bắt cả cây UI ghi lại display list mỗi frame; (3) `pointerInput` đổi key `effect.id` → `Unit` (đọc qua `rememberUpdatedState`) nên đổi hiệu ứng không huỷ/dựng lại bộ bắt sự kiện; (4) `seeds` đổi `List<Float>` → `FloatArray` (hết boxing); (5) `applyEffect` bỏ qua khi bấm lại đúng ô đang dùng — trước đó mỗi lần bấm đều ghi SharedPreferences + bắn StateFlow toàn app.
  - 🎁 **Quay trúng skin/hiệu ứng chỉ hiện chữ "SR".** Server không có `imageUrl` cho SKIN/EFFECT (asset nằm trong APK). `GachaResultSheet.localItemAsset(itemType, refId)` tra ảnh nội bộ từ `SkinRegistry`/`TouchEffectRegistry`; hạt (PNG trắng) được tô theo màu phẩm chất để không chìm vào nền thẻ.
  - 🛡️ **Quay xong mất luôn màn kết quả.** `GachaViewModel.roll` gọi `repository.getState()` **ngoài** `runCatching`, nên chỉ cần bước đọc lại số dư lỗi mạng là cả lượt quay rơi vào `catch` → người dùng đã trả Astrite, đã nhận vật phẩm mà chỉ thấy "Roll failed". Nay kết quả luôn được hiện; lỗi quay thật thì đọc lại state để số dư/pity không lệch.
- **2026-08-06 — P5 một phần: cắm asset gacha + thumbnail skin, cân lại màn Gacha**. Copy từ `Sources/skin-assets/` vào `res/drawable-nodpi/`: `gacha_bg` (nền toàn màn Gacha), `gacha_banner` (banner trang Daily — giữ đúng tỉ lệ 1080×608 thay vì cao cố định, chữ đè trên dải mờ gradient), `ic_astrite` (thay ⭐ emoji ở pill số dư, nút quay, popup nạp), `skin1_thumb`/`skin2_thumb` (tab Skins — `SkinThumbnail` tự ưu tiên ảnh khi `AppSkin.thumbnail != null`). Màn Gacha đại tu: pill Astrite đặt giữa header, tiêu đề kéo lên ~1/3 màn, chip **SSR pity** chuyển xuống ngay trên 2 nút quay (đúng ngữ cảnh trước khi bấm), dải gradient chân màn cho nút/chữ đọc được. ⚠️ Chữ trong màn Gacha giờ nằm ĐÈ LÊN ảnh nền không đổi theo skin → dùng trắng cố định (quy tắc "trắng vì nằm trên ảnh"). Còn lại của P5: icon vector + nút chụp theo skin (PNG hạt hiệu ứng đã cắm ngày 2026-08-06 — xem mục trên).
- **2026-08-05 — Soát lại luồng mới: vá 4 lỗi ở overlay hiệu ứng, đăng xuất và poll đơn nạp**.
  - 🔴 **Chọn hiệu ứng xong bị văng về màn camera.** `TouchEffectOverlay` có nhánh `if (!active) { Box { content() }; return }` riêng, nên bật/tắt hiệu ứng làm Compose huỷ và dựng lại cả cây bên dưới → `rememberNavController` trong `Navigation()` sinh lại → back stack mất. Nay `content()` **luôn ở đúng một vị trí gọi**, chỉ modifier và Canvas đổi theo `active`.
  - 🔴 **Cụm hạt tự phát lại mỗi 10 giây.** Mốc thời gian lấy từ đồng hồ `rememberInfiniteTransition` chạy vòng 10s; sau đúng 1 vòng, `tick - startMs` quay về ~0 nên cụm cũ vẽ lại từ đầu. Nay mốc lấy từ `System.nanoTime()` (không lặp vòng), và đồng hồ đổi sang `withFrameMillis` **chỉ chạy khi còn cụm đang bay** — trước đây nó vẽ lại mỗi frame suốt vòng đời app dù không có gì để vẽ.
  - 🟠 **Skin/hiệu ứng theo máy chứ không theo tài khoản.** `SessionCleaner` không reset `skinId`/`touchEffectId` → tài khoản đăng nhập sau trên cùng máy dùng miễn phí skin SSR của tài khoản trước. Thêm `SettingsPreferences.resetAppearance()`.
  - 🟠 **Kết quả poll cũ đè lên đơn nạp mới.** `refreshPendingOrder` không đối chiếu lại `orderCode` sau khi mạng trả về; thêm chốt so sánh trước khi ghi state.
  - ⚙️ Bổ sung 2 thứ đã chốt trong SKIN_PLAN nhưng chưa nối dây: `TouchEffectController` + `LocalTouchEffectController` (**tắt hiệu ứng trong lúc quay GIF**, mục 2.5.4) và **bấm ô Effects để chạy lại demo** — với ô chưa sở hữu thì đó là tác dụng duy nhất, thiếu nó thì bấm vào không có gì xảy ra.
- **2026-08-05 — G6: nạp Astrite bằng TIỀN THẬT (PayOS)**. `core/network/dto/TopupDtos.kt` + `api/TopupApi.kt` + `feature/gacha/data/TopupRepository.kt`; `TopupSheet.kt` (popup gói nạp + dải "đang chờ thanh toán" + popup "đã cộng N Astrite"); `GachaViewModel` thêm `TopupUiState`. Nút `+` cạnh số dư Astrite **bật lại** (G4 tạm ẩn vì chưa có luồng nạp). Không thêm dependency nào — `androidx.browser` đã có sẵn trong `libs.versions.toml`.
  - 💳 **App chỉ gửi `packageId`** — số tiền và số Astrite do server tra từ `topupPackages`. Gửi kèm số tiền là mở đường cho "tôi trả 1đ, cộng cho tôi 5 triệu Astrite".
  - 🔎 **Nguồn sự thật là webhook PayOS → server, KHÔNG phải URL trình duyệt chuyển về**: người dùng sửa được thanh địa chỉ thành `?status=PAID`. App chỉ hỏi `GET /topup/orders/:orderCode` và chỉ tin khi server trả `PAID`.
  - ⏱️ Poll đặt trong `repeatOnLifecycle(STARTED)`: lúc người dùng đang ở trang PayOS thì app ở background → **ngừng hỏi**; quay lại app là hỏi ngay, đúng lúc cần nhất. Trần 60 lần/lượt foreground (~3 phút) vì `getOrder` không tự chuyển đơn sang EXPIRED — không có trần sẽ poll mãi. Vào lại màn hình là số dư được đọc lại từ server nên không mất gì.
  - 🔒 `orderCode` dùng **`Long`** (mili giây × 100), `Int` sẽ tràn.
- **2026-08-05 — P3+P4+G4+P5+G5: màn Appearance · hiệu ứng touch · màn Gacha**. `feature/appearance/` (3 tab **Frames | Skins | Effects**, vào từ mục **Appearance** mới trong Settings) dùng chung component `CollectibleItem` — khác nhau chỉ ở tỉ lệ ô và số cột. `feature/gacha/` (`GachaScreen` + popup Rule + overlay kết quả lật lần lượt 250ms/ô có nút Skip), vào từ **banner trên trang Daily Quest**. `SkinRegistry` thêm **Snow (1)** và **Forest (2)** với bảng màu từ `Sources/skin-assets/README.md`.
  - 🎯 **Hiệu ứng touch** (`core/designsystem/effect/`): overlay bọc ngoài `NavHost` nên viết 1 lần chạy mọi màn. Dùng `PointerEventPass.Initial` và **không `consume()`** — chỉ "nghe lỏm" nên nút/`VerticalPager`/giữ-để-quay-GIF/pinch-zoom bên dưới nhận đủ sự kiện như cũ. Một `rememberInfiniteTransition` chung cho MỌI hạt (chạm 20 phát liên tiếp không sinh 20 coroutine) + trần `MAX_LIVE_EMISSIONS = 8`. 5 hiệu ứng vẽ bằng Canvas (`CIRCLE/RING/SPARK/LEAF`) nên **chạy được trước khi có asset thật**; `particleAsset` để sẵn cho lúc cắm PNG.
  - 🔒 **Khoá theo sở hữu (G5)** chặn ở **2 lớp**: UI hiện `alpha 0.35` + 🔒, và `AppearanceViewModel.applySkin/applyEffect` tự bỏ qua nếu chưa sở hữu. Ô hiệu ứng chưa sở hữu **vẫn chạm thử được** (để người dùng thấy có đáng quay không) nhưng bấm không áp dụng.
  - 🎲 **App KHÔNG random gì cả** — mọi kết quả đến từ `POST /gacha/roll`. Astrite đổi được bằng tiền thật nên random ở client là lỗ hổng. `isRolling` chặn bấm kép: server có transaction nên không tiêu quá số dư, nhưng bấm 2 lần sẽ thành **2 lượt quay thật**. Sau mỗi lượt đọc lại `/gacha/state` thay vì tự trừ ở app (pity chỉ server biết).
  - 📄 Popup Rule **sinh từ chính `/gacha/state`** → sửa tỉ lệ ở server là popup tự đúng theo, không phải sửa app. Hiện **tỉ lệ gốc** (đúng chuẩn công bố), và **chỉ hiện pity SSR** (user chốt).
  - 🎨 4 màu phẩm chất (`GachaRarity`) **cố ý không nằm trong `SkinColors`** — là màu hệ thống của gacha, đổi skin mà SSR đổi màu thì mất nghĩa "cam = cực hiếm".
  - 🧹 Trang Daily bỏ lưới "Frame collection" (đã có ở tab Frames) → `QuestViewModel` bớt 1 call mạng; `RewardBanner` đổi sang "+60 Astrite". Nút `+` cạnh số Astrite **tạm ẩn** cho tới khi xong G6 (chưa có luồng nạp).

- **2026-08-05 — P0–P2: Skin engine + gom màu về token + gỡ hẳn giao diện Light** (SKIN_PLAN.md mục 2–4.4). Package mới `core/designsystem/skin/`: `AppSkin` (colors/icons/shapes/images) · `LocalAppSkin` + `SkinTheme` (cổng đọc, dùng như `MaterialTheme.colorScheme`) · `SkinIcon` (skin thiếu icon thì tự fallback về Material — thêm skin mới với 3 icon vẫn chạy được toàn app) · `SkinRegistry.find(id)` chịu được id lạ từ server (rơi về Default thay vì crash) · `DefaultSkin` (skin 0) **bằng đúng hex đang chạy** nên giao diện không xê dịch. `AppTheme(skin)` bơm song song `LocalAppSkin` + `MaterialTheme.colorScheme` để component M3 chưa refactor vẫn đổi màu theo skin. **≈305 điểm hardcode → token** (102 textPrimary · 50 accent · 30 shapes.image · 27 pill · 22 textSecondary…) trên 40 file.
  - 🗑️ **Gỡ Light**: xóa `ThemeMode.kt`, `DarkBrownTheme.kt` (theme chết) và **cả `theme/Color.kt`** — palette Gray/SnapYellow/BackgroundPreview giờ là token trong `DefaultSkin`. `SettingsPreferences.themeMode` → `skinId` + `touchEffectId`; bỏ mục Theme + `ThemeDialog` trong Settings (mục **Appearance** thay thế ở P3). Prefs cũ `theme_mode` để lại vô hại, **không cần migration**.
  - ⚠️ **Không đổi mù**: 68 chỗ `Color.White` CỐ Ý giữ — chữ/icon nằm đè lên **ảnh hoặc camera** của người dùng (CameraPreview, EditMedia, Coop, QrScan, ChatMediaViewer, SubmitPhoto, PostGrid, PageIndicator, InputCaptionPill…) phải trắng thật ở mọi skin, đổi theo token là skin nền sáng làm chữ chìm vào ảnh. Cả 12 file đó **đã ghi chú lý do ngay đầu file**. Tương tự: bảng màu bút doodle (`DOODLE_COLORS`) và `textColor` của chip caption (nằm trên gradient riêng) giữ hardcode.
  - 📌 **Luật mới**: cấm hardcode màu mới trong `feature/`; cần màu mới thì **thêm token vào `SkinColors` trước**. Token thêm ở đợt này ngoài plan: `pillTranslucent` (nền pill mờ trên camera), `textSecondary`, `onAccent`.
  - Shape gom 4 vai trò có token (`pill` 50 · `image` 20dp · `sheet` 24dp · `input` 16dp = 71 chỗ); các giá trị lẻ (8/12/14dp) giữ nguyên vì là bo góc cục bộ của từng component, không phải vai trò dùng chung.

- **2026-08-05 — Đồng bộ contract G2: thưởng daily quest đổi sang Astrite**. `GET /quests/today` trả `rewardAstrite: Int?` thay `rewardFrameId: String?` → sửa `QuestDtos.kt`, `QuestUiState`, và `DailyQuestScreen`: `RewardBanner` giờ hiện "Daily quests complete! / +60 Astrite" (bỏ tham số `FrameDto`), `FrameItem` bỏ tham số `isNewReward` + nhãn "NEW" (không còn khung nào là "vừa được thưởng"), empty state đổi thành "roll the gacha to collect them!". Lưới **Frame collection giữ nguyên** — khung vẫn mở qua gacha/mốc streak. ⏭️ Đại tu màn Daily (thêm banner gacha, bỏ lưới khung) nằm ở phase G4 của `GACHA_PLAN.md`.
- **2026-08-04 — Fix ảnh coop camera sau bị zoom ~3x ngay sau khi chụp**: 🐞 ảnh CameraX camera SAU lưu pixel NẰM NGANG + tag EXIF "xoay 90°"; nửa ảnh coop là chỗ duy nhất hiển thị file đó trực tiếp bằng Coil (`CoopCaptureScreen` → `AsyncImage` Crop) — tag không được áp → ảnh ngang 1280×720 crop vào ô dọc 1:2 chỉ thấy ~28% chiều rộng, trông như phóng to ~3 lần so với preview (luồng thường không dính vì `EditMediaScreen.decodeUprightBitmap` tự xử lý EXIF). Fix: `mirrorPhotoFile` (chỉ camera trước) đổi thành **`normalizeCapturedPhoto(file, mirror)`** trong `CameraPreview.kt` — bake EXIF rotation vào pixel cho MỌI ảnh chụp (camera trước lật ngang thêm), ảnh đã đúng chiều thì giữ nguyên file không re-encode. Bonus: ảnh upload coop lên server đã đúng chiều sẵn → hết phụ thuộc fix `.rotate()` sharp phía server (Render chưa deploy vẫn ghép đúng).
- **2026-08-03 (4) — Fix 3 lỗi user test**: (1) 🐞🔐 **Đăng xuất kẹt lại + mọi API báo "Thieu token"**: `LoginScreen` trước đây dùng `hiltViewModel()` mặc định → tạo `AuthViewModel` RIÊNG (scope theo entry "login"); đăng nhập trong phiên chỉ đổi state instance riêng đó, instance chung mà `Navigation`/`SnapgetApp` quan sát vẫn `Unauthenticated` → lần Sign Out sau `StateFlow` không emit (giá trị y hệt) → không điều hướng về Login dù Firebase đã signOut. Fix: `Navigation` truyền `authViewModel` chung vào `LoginScreen` (giống SettingScreen) — logout luôn về màn Login, đồng thời `fetchCurrentUser()`/FCM/pending-invite chạy đúng khi re-login cùng phiên. (2) **Bạn mới kết không nhắn tin được**: màn Messages chỉ hiện `/messages/conversations` (cần ≥1 tin) → bạn vừa accept không có lối vào chat; `MessageViewModel.loadConversations()` giờ nối thêm bạn bè CHƯA có hội thoại vào cuối danh sách (preview "Say hi to your new friend! 👋", không hiện giờ) — bấm mở `chat/{uid}` như thường. (3) **Đổi tên xong logout/login lại bị mất**: `signInWithGoogle` trước đây MỖI lần re-login đều PATCH `fullName/avatar` theo profile Google/Firebase Auth, ghi đè tên user đã đổi trong app; giờ chỉ sync tên/avatar khi `additionalUserInfo.isNewUser` (lần tạo tài khoản), các lần sau chỉ `syncWithServer()` (ensureUser + FCM token).
- **2026-08-03 (3) — Fix ảnh ghép xoay 90° + đăng bài chậm/timeout/đăng trùng**: server thêm `.rotate()` EXIF khi ghép (ảnh camera sau hết xoay 90°) + `POST /moments` trả nhanh (2 hook nặng chạy sau response); app truyền **`clientRequestId`** (UUID `remember` mỗi lần vào `SubmitPhotoScreen`, giữ nguyên khi bấm đăng lại) qua `submitPhoto` → `CreateMomentRequest` — retry sau timeout được server trả lại bài đã đăng thay vì tạo bản sao.
- **2026-08-03 (2) — Rà soát coop toàn diện (độ mượt + chống race)**: (1) `CoopViewModel.preferNewer()` — response poll VỀ MUỘN không ghi đè được state mới hơn (trước đó GET đan xen với submit làm nửa ảnh vừa nộp "biến mất", nút chụp hiện lại, dễ nộp trùng); đã có `mergedMediaUrl` thì không bao giờ lùi. (2) **Rời màn chụp = HỦY phiên cho cả 2 bên** (BackHandler + nút back top bar): PENDING hủy thẳng, ACCEPTED hiện dialog xác nhận "Leave co-op capture?" — trước đó back chỉ pop, đối phương "waiting for X" vô hạn (server thêm decline-từ-ACCEPTED cùng đợt). (3) Poll đổi sang `repeatOnLifecycle(STARTED)` — app xuống background là NGỪNG poll, quay lại tự refresh; trạng thái kết thúc thì dừng hẳn. (4) `downloadToCacheFile` thêm timeout 10s/15s (URL.openStream mặc định KHÔNG timeout — mạng treo là kẹt "Merging photos" vô hạn). (5) Feed poll `loadPending` 10s/lần — banner lời mời (TTL 5 phút) hiện kịp mà không cần rời/vào lại feed. (6) Picker bạn bè hiện spinner khi đang tải (hết lóe "No friends yet").
- **2026-08-03 — "Ảnh GIF" (thay video 5s) + fix coop kẹt "Merging photos"**: (1) 🐞 **Fix coop treo**: `CoopCaptureScreen` đặt `navigatedToEdit` VÀO KEY của chính `LaunchedEffect` rồi set nó bên trong → Compose hủy đúng coroutine đang chạy → `downloadToCacheFile` không bao giờ xong → kẹt "Merging photos…" vĩnh viễn. Bỏ cờ khỏi key, thêm retry tải ảnh 3 lần + nút "Tap to retry" khi server ghép lỗi (revert về ACCEPTED) qua `CoopViewModel.retryMerge`. (2) **Ảnh GIF**: `MAX_VIDEO_SECONDS` 5 → **3** (app + server); GIỮ nút chụp = quay GIF, thả tay = dừng (ép tối thiểu 0.8s để CameraX không trả ERROR_NO_VALID_DATA), **XÓA đồng hồ đếm giây + vòng tiến độ đỏ**, **bỏ ghi âm** (GIF không tiếng, hết xin RECORD_AUDIO); QualitySelector HD→SD→LOWEST + FallbackStrategy (trước ép cứng HD nên nhiều máy bind fail = mất hẳn chức năng quay); màn camera KHÔNG có dòng hint nào dưới nút chụp (user chốt). Component mới `component/video/GifVideoPlayer.kt` (tự phát, lặp vô hạn, muted, không controls) dùng ở feed/pager/xem post cũ + preview EditMedia/SubmitPhoto; grid đổi ô đen + nút play → **poster frame Cloudinary + badge "GIF"**. Enum/contract giữ nguyên (`.mp4`, `PostType.VIDEO`).
- **2026-08-02 (tối) — Đại tu Co-op Capture + pager xem post cũ**: (1) Co-op flow MỚI (user chốt): nút Co-op trên camera → `CoopFriendPickerDialog` chọn bạn → gửi lời mời KHÔNG kèm ảnh (TTL 5 phút) → cả 2 vào `CoopCaptureScreen` (route `coop_capture?inviteId=&name=`): nửa màn camera của mình (trái = người mời/phải = người nhận, khớp thứ tự ghép server), nửa kia xám + vòng xoay chờ; chụp → nút SEND tròn mũi tên + icon AGAIN bên trái để chụp lại; send xong ẩn nút hiện "waiting for X"; đủ 2 nửa server ghép → tự tải ảnh ghép về (`downloadToCacheFile` mới trong FileUtils) → vào `EditMedia` đăng bài như thường; banner feed giờ mở dialog Accept/Decline; poll 2.5s. XÓA `CoopSendScreen` + `CoopAcceptScreen` + 2 route cũ. Server: 6 endpoint coop (xem `server/GUIDE.md`). (2) `PostDetailScreen` (profile) thành VerticalPager giống feed: nhận toàn bộ post cũ, mở đúng post của ngày bấm, vuốt xem tiếp; icon lưới mở PostGrid tổng hợp thật (trước đó chỉ đóng màn); back: grid → pager → calendar.
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

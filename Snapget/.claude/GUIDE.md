# 🗺️ GUIDE.md — Bản đồ dự án Snapget

> Tài liệu tham chiếu nhanh để sửa code **không cần đọc lại toàn bộ project**.
> Đọc file này trước, rồi mở đúng file cần sửa theo bảng bên dưới.
> Cập nhật lần cuối: 2026-07-13.

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
| **Màn Daily Quest** 🏆 (`feature/quest/`): 2 quest cố định/ngày từ `GET /quests/today` (server tự hoàn thành — vào màn = xong quest LOGIN), banner streak cá nhân, banner khung vừa thưởng, **bộ sưu tập khung** (`GET /frames`, khóa = mờ + 🔒, mốc streak = nhãn 🔥). Entry: nút cúp 🏆 vàng trên top bar PostScreen (`MainTopBar.onQuestClick`). Route `daily_quest` (ẩn bottom bar) | ✅ 2026-07-13 — build OK, **cần test emulator + server** |
| **Co-op Capture** 👥 (`feature/coop/`): toggle "Chup chung" trên CameraScreen → chụp nửa ảnh → `CoopSendScreen` (chọn 1 bạn, gửi lời mời qua `POST /moments/coop`) → bạn thấy **banner vàng trên feed** (PostScreen poll `GET /moments/coop/pending` khi đăng nhập) → `CoopAcceptScreen` (trái = nửa ảnh người mời, phải = camera chụp nửa mình, ✓ = accept → server ghép sharp thành 1 moment chung, ✕ = decline). Chỉ hỗ trợ ẢNH (server ghép ảnh, không video) | ✅ 2026-07-13 — build OK, **cần test 2 máy + server** |
| **Feed hiển thị khung + video**: `PostGrid` overlay khung (map frameId→imageUrl từ `GET /frames`) + ô video nền đen icon ▶ (URL .mp4 không decode được bằng Coil); `PostDetailScreen` đã phát video ExoPlayer + overlay khung từ trước | ✅ 2026-07-13 |
| **Deep link mời kết bạn** 🔗: intent-filter `https://snapget.app/invite/{code}` → `MainActivity.handleInviteDeepLink` (cả cold start ở `onCreate` lẫn `onNewIntent` vì `singleTask`) → `POST /friendships/connect` + Toast kết quả; chưa đăng nhập → Toast nhắc đăng nhập. Nút share: `ShareYourLinkComponent(inviteLink)` trong sheet bạn bè mở share chooser hệ thống. ⚠️ **Lưu ý demo**: domain `snapget.app` không có thật → không verify App Links được (không có `assetlinks.json`), Android sẽ hiện **hộp thoại chọn app** khi bấm link thay vì mở thẳng — chọn Snapget là vào luồng kết bạn bình thường | ✅ 2026-07-13 |
| **Sửa hồ sơ** ✏️: nút Edit cạnh tên (chỉ profile mình) → `EditProfileDialog` (đổi tên ≤30 ký tự + chọn avatar từ thư viện `GetContent` → copy cache → upload `/upload` → `PATCH /users/me`). `ProfileViewModel.updateProfile` + `updateStatus` | ✅ 2026-07-13 |
| **Chat nhóm + media** 💬: section "Nhóm chat" trên MessageScreen (`GET /messages/groups`), nút GroupAdd top bar → `CreateGroupDialog` (tên + tick bạn bè, ≤20 server enforce) → `GroupChatScreen` (route `group_chat/{groupId}?name=`, polling 5s, dùng chung `MessageBubble`/`ChatInputPill`). Gửi **ảnh** 📷 (GetContent→upload→PHOTO), **sticker** 😊 (khay Twemoji CDN, content=URL), **voice** 🎤 (`rememberVoiceRecorder` MediaRecorder m4a→upload→VOICE, bubble phát bằng MediaPlayer) cho cả 1-1 lẫn nhóm — tiện ích ở `feature/message/ChatMedia.kt` | ✅ 2026-07-13 — build OK, **cần test 2 máy + server** |
| **Xóa Locket Gold** 💸: bỏ nút "Get Locket Gold" (UserProfileTopBar) + setting "Restore Purchases" (SampleData) — **toàn bộ tính năng miễn phí, không có thanh toán** (quyết định 2026-07-13) | ✅ 2026-07-13 |
| **Migrate feed tab You/bạn bè sang API** 🧹: PostScreen giờ đọc HẾT qua server — Everyone = `/moments/feed`, You = `/moments/mine`, bạn = `/moments/user/:uid` (`PostViewModel.loadUserMoments`); dropdown top bar + resolve tác giả dùng bạn bè từ `/friendships` (FriendsViewModel). **Dọn god-object**: `MainViewModel` chỉ còn currentUser + settings, `FirestoreRepository` chỉ còn getCurrentUser + settings (xóa ~500 dòng đọc posts/friends/messages/notifications Firestore trực tiếp); SubmitPhotoScreen bỏ fallback hiển thị bài Firestore cũ | ✅ 2026-07-13 |
| **Dọn route legacy**: xóa `detail` (PlaceholderScreen) + `post_detail/{postId}` (không màn nào điều hướng tới — PostDetailScreen hiển thị inline qua state); xóa nhánh deep link `auth/success|failure` chết trong MainActivity (manifest không đăng ký host `auth`; quên mật khẩu đi qua email Firebase — luồng này ĐÃ có UI đầy đủ trong LoginScreen: nút "Forgot Password?" → gửi mail → snackbar) | ✅ 2026-07-13 |
| README.md viết lại đúng thực tế (bỏ Appwrite) + xóa preview hỏng `MainBottomBarAutoItemsPreview` (items = TODO()) | ✅ 2026-07-13 |
| **Doodle chọn độ dày nét**: 3 mức (8/12/20px) trên toolbar vẽ tay của EditMediaScreen (spec TODO.md Phase 3 yêu cầu "bảng màu + độ dày nét + undo/xóa" — undo/xóa/màu đã có sẵn) | ✅ 2026-07-13 |
| **Đợt dò lỗi 3 vòng trước tổng test** (8 finder + 2 verifier agent): fix ~25 lỗi. Nổi bật phía app: toast lỗi hiện message tiếng Việt của server thay vì "HTTP 400" (`core/network/ErrorMessages.kt` — `serverMessage()`, áp cho MỌI catch hiện lỗi); **mark-seen theo item thực sự hiển thị** (PostGrid.onPostVisible, bỏ mark cả feed + bỏ mark bài mình + guard lúc currentUser chưa load); poll chat không nuốt tin vừa gửi (mergeThread theo messageId + sendTime); decline co-op không bị hủy bởi popBackStack (NonCancellable); cleartext HTTP chuyển sang debug-manifest; EditMediaScreen decode ảnh ở IO + inSampleSize 2048; dedup: `uploadFile()` (4 repo), `copyUriToCacheFile()` (core/util), `SnapYellow` (theme), MAX_VIDEO_SECONDS (constants); dọn UI chết CameraScreen; SubmitPhoto nhận `frameUrl` qua route (bỏ tải lại catalog); loadFriendsIfNeeded cho 2 màn chat; MomentDto thêm `coopUserId` | ✅ 2026-07-13 |

> ⚠️ Test app cần server đang chạy: `cd d:\DATN\server && npm run start:dev`. Emulator gọi `http://10.0.2.2:3000/api/` (đổi qua `server.base.url` trong `local.properties` nếu chạy máy thật).
>
> ⚠️ **Cleartext HTTP chỉ bật ở build DEBUG** (`app/src/debug/AndroidManifest.xml`) — mọi test/demo dùng `installDebug`. Bản **release** sẽ CHẶN toàn bộ HTTP (Android 9+): muốn build release chạy được phải deploy server có **HTTPS** và đổi `server.base.url`. Đây là chủ đích bảo mật (token không đi qua HTTP trần), không phải bug.

### Bên trong `app/src/main/java/com/example/snapget/` (⚡ ĐÃ ĐẠI TU feature-based 2026-07-12)

```
MainApplication.kt          # @HiltAndroidApp — điểm khởi tạo Hilt
MainActivity.kt             # Activity gốc → SnapgetApp() → Navigation()
navigation/Navigation.kt    # Toàn bộ route (sealed class Screen) + NavHost

core/                       # Dùng chung toàn app (không thuộc feature nào)
  common/                   #   LoadStatus, AppException
  config/                   #   StatusBar
  constants/                #   FirestoreConfig, AuthConstants, ScreenTitle, UserRole, ...
  data/                     #   FirestoreRepository (ĐÃ DỌN 2026-07-13 — chỉ còn currentUser + settings),
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
  ui/                       #   MainViewModel (ĐÃ DỌN 2026-07-13 — chỉ còn currentUser + settings)
  util/                     #   DatetimeUtils, MainUtils, EdgeToEdge, PaddingValues

feature/                    # Mỗi tính năng 1 package: screen + viewmodel + data riêng
  auth/                     #   LoginScreen, AuthViewModel, data/AuthRepository
  camera/                   #   CameraScreen (CameraX, toggle chế độ Chụp chung)
  coop/                     #   CoopSendScreen, CoopAcceptScreen, CoopViewModel, data/CoopRepository (API /moments/coop)
  friends/                  #   FriendsViewModel, QrScanScreen (quét QR kết bạn), data/FriendsRepository (API /friendships)
  post/                     #   PostScreen, PostDetailScreen, SubmitPhotoScreen, EditMediaScreen, MomentMapper
  message/                  #   MessageScreen, ChatScreen, GroupChatScreen, ChatMedia (sticker/voice/photo), MessageViewModel, data/MessageRepository (API /messages, polling 5s)
  profile/                  #   UserProfileScreen, ProfileViewModel, data/ProfileRepository (API /users + /moments/mine)
  quest/                    #   DailyQuestScreen, QuestViewModel, data/QuestRepository (API /quests/today + /frames + /users/me)
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
| `chat/{recipientId}` | `feature/message/ChatScreen.kt` | Chat 1-1 (TEXT/EMOJI/PHOTO/STICKER/VOICE) |
| `group_chat/{groupId}?name=` | `feature/message/GroupChatScreen.kt` | Chat nhóm ≤20; entry = section "Nhóm chat" màn Messages; ẩn bottom bar |
| `profile?userId={userId}` | `feature/profile/UserProfileScreen.kt` | `userId` nullable (không có = profile của mình) |
| `qr_scan` | `feature/friends/QrScanScreen.kt` | Quét QR kết bạn (CameraX + ML Kit → POST /friendships/connect) |
| `setting` | `feature/settings/SettingScreen.kt` | |
| `daily_quest` | `feature/quest/DailyQuestScreen.kt` | Quest hôm nay + bộ sưu tập khung; ẩn bottom bar; entry = nút 🏆 top bar feed |
| `coop_send?photoPath=` | `feature/coop/CoopSendScreen.kt` | Gửi lời mời chụp chung (sau khi chụp ở chế độ Co-op); ẩn bottom bar |
| `coop_accept?inviteId=&mediaUrl=&name=` | `feature/coop/CoopAcceptScreen.kt` | Chấp nhận lời mời: chụp nửa còn lại → server ghép; entry = banner vàng trên feed; ẩn bottom bar |
| `edit_media?mediaPath=&isVideo=` | `feature/post/EditMediaScreen.kt` | Chỉnh sửa sau chụp/quay (khung/filter/vẽ tay) → SubmitPhoto |

Route `detail` và `post_detail/{postId}` **đã xóa** (2026-07-13 — legacy, không màn nào điều hướng tới; PostDetailScreen hiển thị inline qua state trong PostScreen/UserProfile).

Bottom bar bị ẩn ở: `message`, `profile`, `setting`, `login`, `chat`, `qr_scan`, `daily_quest`, `coop_send`, `coop_accept` (xem `hideBottomBarRoutes` trong `navigation/Navigation.kt`). Route `test_camera` và `relationship` **đã xóa** (2026-07-12 — bạn bè dùng bottom sheet, không cần màn riêng).

---

## 7. Dependency Injection (Hilt)

Tất cả module ở `di/`, đều `@InstallIn(SingletonComponent::class)`:

| Module | Cung cấp |
|---|---|
| `FirebaseModule` | `FirebaseAuth`, `FirebaseFirestore` (singleton) |
| `NetworkModule` | `OkHttpClient` (AuthInterceptor gắn **Firebase ID token**, redact log) + `Retrofit` (baseUrl = `BuildConfig.SERVER_BASE_URL`) + provide `UserApi`/`UploadApi`/`MomentApi`/`FriendshipApi`/`MessageApi`/`QuestApi`/`FrameApi`/`CoopApi` |
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
1. ~~`MainActivity.onNewIntent` deep link `auth/success|failure` TODO~~ → **đã xóa** (2026-07-13 — nhánh chết, manifest không đăng ký host `auth`; quên mật khẩu đi qua email Firebase, UI đã đủ trong LoginScreen).
2. ~~Code chết message/post trong god-VM~~ → **đã dọn** (2026-07-13 — MainViewModel/FirestoreRepository chỉ còn currentUser + settings).

**Tính năng UI còn thiếu (cần bổ sung):**
- ~~Đăng bài / upload ảnh~~ → ✅ đã nối `/upload` + `/moments` (2026-07-12).
- ~~Màn `relationship`/`detail`~~ → ✅ đã xóa (sheet bạn bè + QR thay thế; `detail` legacy).
- ~~Luồng quên mật khẩu~~ → ✅ đã có sẵn trong LoginScreen (Forgot Password? → gửi mail → snackbar).

**File rác còn lại nên dọn:**
- `package-lock.json` (root Snapget) — rỗng, không thuộc app Android → xóa.
- `Sources/assets/messages_screen_old.png` — ảnh cũ `_old`.
- ~~README.md Appwrite~~ → ✅ đã viết lại (2026-07-13).

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

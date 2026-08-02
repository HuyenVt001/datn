# DESIGN.md — Hệ thống thiết kế Snapget

> Nguồn chuẩn giao diện: **bộ ảnh demo trong `Sources/assets/`** — mọi màn hình/tính năng mới PHẢI bám theo phong cách này để app đồng nhất.
> Đối chiếu code: theme thật ở `core/designsystem/theme/` (Color.kt, Theme.kt, Type.kt).
> ⚠️ **Luật bắt buộc:** làm tính năng UI **chưa có sẵn trong code** → đọc file này, rồi **HỎI USER THẬT CHI TIẾT** (bố cục, hành vi, vị trí vào màn nào) trước khi viết dòng code nào.

---

## 1. Triết lý thiết kế (nhìn từ assets)

1. **Dark-first, ảnh là nhân vật chính.** Nền đen gần tuyệt đối; UI chrome (bar, nút) tối giản, chìm xuống để ảnh nổi bật. Không dùng light theme (AppTheme đang force dark).
2. **Vàng (yellow/gold) là accent DUY NHẤT.** Chỉ dùng cho: viền nút chụp, viền avatar (bạn bè/chưa xem), link nhấn mạnh (Sign Up), thành phần gamification (streak, badge số, "Gold"). Không thêm màu accent khác trừ chip trang trí (mục 7.8).
3. **Bo góc lớn ở mọi nơi.** Ảnh bo ~20–28dp, pill/nút bo tròn hoàn toàn (CircleShape/50%), card bo 16–24dp. Không có góc vuông.
4. **Overlay đen mờ trên ảnh.** Mọi thứ đè lên ảnh (caption, nút flash, zoom) = nền `Color.Black.copy(alpha = 0.5f–0.6f)` bo tròn, chữ/icon trắng.
5. **Ít chữ, nhiều icon tròn.** Nút hành động = icon trong hình tròn nền tối; label chỉ khi cần.

---

## 2. Bảng màu (token ↔ code ↔ chỗ dùng trong ảnh)

### Nền & bề mặt (đã có trong `Color.kt`)
| Token code | Hex | Dùng cho (theo ảnh) |
|---|---|---|
| `GrayBackground` | `#121212` | Nền mọi màn hình (feed, chat, settings…) |
| `GraySurface` | `#1A1A1A` | Card đăng nhập, item settings, bottom sheet |
| `GraySurfaceVariant` | `#2C2C2C` | Pill "Everyone", bubble chat đến, input "Send message…", chip caption mặc định, panel dropdown bạn bè |
| `GrayOnBackground` | `#FFFFFF` | Chữ chính (tên, tiêu đề) — **Bold** |
| `GrayOnSurfaceVariant` | `#B0B0B0` | Chữ phụ (subtitle, "Now", preview tin nhắn, placeholder) |
| `GrayError` | `#CF6679` | Lỗi |

### Accent vàng — GIÁ TRỊ THẬT đang dùng trong code (chưa token hóa — khi refactor gom về `Color.kt`)
| Giá trị trong code | Dùng ở đâu (đã xác minh trong code) |
|---|---|
| `Color.Yellow` (`#FFFF00`) | Viền nút chụp giữa bottom bar (`MainBottomBar`+`CircleComponent`, border 3dp), viền avatar **đang chọn** trong `FriendList`, flash bật (`CameraPreview`) |
| `Color(0xFFFFD700)` (Gold) | Link "Sign Up" (`LoginScreen:477`), chữ + viền pill Gold (`UserProfileTopBar`), viền avatar profile, stat pill Lockets/streak (`UserProfileScreen`) |
| Gradient gold `#FFD700 → #FFA500 → #FFE55C` và `#FFD700 → #FFF380 → #FFD700` | Viền avatar profile + viền pill "Get Gold" (`Brush.linearGradient`) |

> Token đề xuất khi dọn: `SnapYellow = Color.Yellow` (selection/capture) và `SnapGold = #FFD700` (gamification). Đừng thêm hex vàng thứ 3.

### Màu "chìm" đặc trưng khác (THẬT trong code — dễ đoán nhầm từ ảnh)
| Giá trị | Dùng ở đâu |
|---|---|
| `Color(0xFF404137)` (xám ô liu tối) | **Nền pill "Everyone"** (`MainPill`), nền avatar `FriendList`, viền avatar không chọn (biến thể 2) |
| `Color(0xFFB8B8B8)` | Viền avatar **không** chọn trong `FriendList` |
| `Color(0x80F0F0F0)` (trắng mờ 50%) | Nền `InputCaptionPill` — caption nhập trên ảnh, **chữ ĐEN 16sp** |
| `Color.Black.copy(alpha = 0.5f–0.6f)` | Caption hiển thị trên ảnh (bo 24dp), overlay play video 72dp (`PostDetailScreen`) |
| Gradient `#CC424242 → #CC616161` + `blur(16dp)` | `BlurredContainer` (panel mờ, bo 20dp) |

### Màu chức năng phụ (chip caption "Decorative" — là GRADIENT, không phải solid)
Xanh dương `#4FC3F7→#0288D1` (Weather) · Xanh lá `#81C784→#388E3C` (Party) · Cam `#FFA726→#F57C00` (Good morning) · Xám `#444444→#222222`, `#888888→#444444`, `DarkGray→Black` (chips thường). KHÔNG dùng các màu này cho UI chung.

---

## 3. Typography

`Type.kt` chỉ định nghĩa **3 style** (FontFamily.Default/Roboto):
`bodyLarge` = 16sp Normal, lineHeight 24 · `titleLarge` = 22sp **Normal**, lineHeight 28 · `labelSmall` = 11sp Medium.
→ Độ đậm KHÔNG nằm trong Typography — screens tự set `fontWeight` tại chỗ. Quy ước thực tế:

| Vai trò | Style thật trong code |
|---|---|
| Tiêu đề màn ("Messages", "Settings", "Send to…") | `titleLarge` (22sp) + `FontWeight.Bold` set thủ công, trắng, căn giữa |
| Tiêu đề lớn ("Welcome to…", "X out of 20 friends") | 28–32sp + `FontWeight.Bold` trắng (custom fontSize) |
| Tên người dùng / label item | `bodyLarge`/16sp + `FontWeight.Bold` trắng |
| Nội dung / bubble chat | `bodyLarge` 16sp Normal trắng |
| Phụ đề, timestamp, placeholder | 14–16sp Normal, `onSurfaceVariant` `#B0B0B0` |
| Chữ pill "Everyone" | `Color.White` + `FontWeight.Medium` (không Bold) |
| Item settings title | `FontWeight.Medium` |

> Khi thêm text mới: dùng `MaterialTheme.typography.*` + set `fontWeight` như bảng, đừng chế fontSize lạ.

---

## 4. Hình khối & bo góc (shape scale)

Giá trị THẬT trong code (đối chiếu 2026-07-12):

| Thành phần | Bo góc thật | File |
|---|---|---|
| Pill "Everyone", `MainPill`, `InputCaptionPill` | `RoundedCornerShape(50)` (tròn hoàn toàn) | `MainPill.kt`, `MainTopBar.kt` |
| Ảnh feed grid + ảnh post detail | **20dp** | `PostGrid.kt:93`, `PostDetailScreen.kt:125` |
| Camera preview | clip **50dp** (CameraScreen bọc ngoài) | `CameraScreen.kt` |
| Panel dropdown bạn bè (topbar) | **24dp**, maxHeight 450dp | `MainTopBar.kt:180` |
| Thanh "Send message…" + caption hiển thị | **24dp** (không tròn hẳn) | `Frame.kt`, `MessagePill.kt`, `PostDetailScreen.kt:158` |
| Card form login | **20dp**; input/nút bên trong `cornerShape = 12dp` | `LoginScreen.kt:88,288` |
| Item settings | **8dp** (ảnh trông ~16 — code là 8) | `SettingScreen.kt:196` |
| Bubble chat | **Động**: 20dp góc mở đầu nhóm, 16dp giữa, **6dp góc "đuôi"** phía người gửi | `ChatScreen.kt:395` |
| Ô ngày calendar (`EmptyDayItem`) | **12dp**, aspectRatio 1:1, dot 4dp | `EmptyDayItem.kt` |
| Card calendar profile | **20dp** | `UserProfileScreen.kt` |
| Badge video trên grid | **12dp** | `PostGrid.kt:147` |
| Pill Gold + icon profile topbar | **10dp** (`roundCorner`) | `UserProfileTopBar.kt:58` |
| `BlurredContainer` | **20dp** + blur 16dp | `BlurredContainer.kt` |
| Avatar | `CircleShape` tuyệt đối | mọi nơi |

**Kích thước chuẩn (thật trong code):**
- Nút chụp/Send giữa bottom bar: **80dp** (`customSizeCenter`), viền **3dp `Color.Yellow`** khi không icon, gap 7dp (Camera) / 20dp + rotate -45° (Send) — `MainBottomBar.kt`
- `Circle` mặc định: outer 56dp, viền 2dp `Color.Yellow` — `CircleComponent.kt`
- Nút shutter trong `CameraPreview`: 72dp (⚠️ chưa viền vàng — xem mục 9)
- Avatar: topbar/list = `avatarWidth` **40dp** (`MainTopBar.kt:76`) · overlay play video 72dp · icon trong settings 40dp · icon nhỏ 16–20dp
- Pill "Everyone": height 40dp, padding trong h12/v6, chevron 18dp
- Nút ⊕ trong thanh message: 40dp

---

## 5. Spacing

Giá trị thật trong code:
- Lề màn hình ngang: **16dp** (chat, settings); top bar chính: **20dp** (`MainTopBar.kt:137`)
- Gap feed grid: **3dp** cả 2 chiều (`PostGrid.kt:65-66`)
- `FriendList` ngang: các item cách nhau **20dp**, tên cách avatar **5dp**
- Thanh message (`Frame`/`MessagePill`): padding ngoài 15dp, trong ngang 16dp / dọc 10–12dp
- `PageIndicator`: chấm cách nhau **6dp**, active `Color.White`, inactive `Gray 30%`
- Khoảng cách card/item dọc: 12–16dp; nội dung cách tiêu đề section: 12dp

---

## 6. Iconography

- **Material Icons** (đã có `material-icons-extended`): Filled khi active/nổi bật, Outlined khi phụ.
- Icon đè trên ảnh: đặt trong hình tròn `Black 60%` (flash, zoom "1.0x", play video).
- Icon hành động chính (chat, share, grid, flip camera): trắng, 24–32dp, nền tròn tối hoặc trần trên nền đen.
- Emoji dùng trực tiếp trong UI (reaction ❤️🔥😄, streak 🔥, lockets ❤️) — không thay bằng icon vector.

---

## 7. Catalog component (ảnh ↔ code hiện có)

### 7.1 Top bar chính (feed/camera) — `component/topbar/MainTopBar.kt`
Ảnh: `post_screen.png`, `take_photo_screen.png`. 3 khối: **avatar tròn `avatarWidth`=40dp trái** (mở profile) · **pill "Everyone ⌄" giữa** (nền thật `#404137`, `RoundedCornerShape(50)`, chữ trắng **Medium**, chevron 18dp, padding h12/v6) · **icon chat tròn phải**. Padding bar: ngang 20dp. **Dropdown chọn bạn nằm NGAY TRONG MainTopBar** (panel bo 24dp, maxHeight 450dp, hàng = avatar 40dp + tên + chevron). Top bar phụ: `CommonTopBar.kt`, `SimpleTopBar.kt`, `MessageTopBar.kt`, `SettingTopBar.kt`, `UserProfileTopBar.kt`.

### 7.2 Friend list NGANG (chọn người nhận) — `component/list/FriendListComponent.kt` (`FriendList`)
Ảnh: `submit_photo_screen.png` (hàng avatar dưới cùng). Item cách nhau 20dp; avatar nền `#404137`, **viền `Color.Yellow` khi được chọn / `#B8B8B8` khi không**; tên trắng cách avatar 5dp. ("Everyone" đầu tiên.) *Lưu ý: dropdown trên feed là của `MainTopBar` (7.1), không phải file này.*

### 7.3 Feed grid — `component/grid/PostGrid.kt`
Ảnh: `post_screen.png`. `GridCells.Fixed(3)`, ảnh vuông (`aspectRatio 1f`) bo **20dp**, gap **3dp**; video có overlay play + badge bo 12dp; top bar đè lên trên (grid padding top).

### 7.4 Post feed pager + detail — `feature/post/PostScreen.kt` + `PostDetailScreen.kt` (⚡ ĐẠI TU 2026-07-26 theo 2 ảnh mẫu Locket của user)
- **Feed = VerticalPager full-screen** (mặc định khi vuốt lên từ camera): mỗi post 1 trang mới→cũ; ở post mới nhất vuốt xuống → về camera; icon lưới (dưới trái) → grid tổng hợp, bấm 1 ô → về pager đúng post.
- **Nội dung 1 post** (`PostDetailContent` — dùng chung pager + màn profile): ảnh lớn `aspectRatio 1f` bo **20dp**; caption đè đáy ảnh (nền `Black 50%` bo 24dp); video = overlay play 72dp nền `Black 60%`; dưới ảnh: avatar 40dp + **tên Bold** + thời gian tương đối NGẮN kiểu **"1d"** (`relativeTimeShort`).
- Thanh **"Send message…"** = `pill/MessagePill.kt` (bo 24dp, CỐ ĐỊNH không cuộn theo trang): **gõ text = gửi DM thật tới tác giả** (BasicTextField, có text → nút Send thay chỗ emoji); 3 emoji nhanh **💛😂💕** (đổi theo mẫu 2026-07-26) **bấm = thả reaction**, highlight vàng 25% khi đã thả, ⊕ xòe 8 emoji mở rộng, emoji bay 34sp (`FlyingEmojiOverlay`).
- Hàng nút đáy pager: icon lưới `GridView` · **nút giữa 80dp viền vàng 3dp** (→ camera) · **⋯ `MoreHoriz`** mở `PostOptionsSheet` (bottom sheet chữ căn giữa: Share / Download / **Delete đỏ chỉ bài mình** / Cancel — khớp popup mẫu; KHÔNG có Report).
- **`PostDetailScreen` (xem post cũ từ calendar profile) dùng ĐÚNG hàng nút này** (đồng bộ 2026-08-02): lưới = về calendar profile, nút chụp = về camera, ⋯ = cùng `PostOptionsSheet`; xóa bài → đóng detail + tải lại calendar. KHÔNG dùng lại `sampleItems3` (bản cũ có icon Share mở Settings — đã bỏ).
⚠️ `Frame.kt` là THANH MESSAGE cũ (bản trùng, không dùng) — đừng nhầm với tính năng "frame" phần thưởng.

### 7.5 Camera — `feature/camera/CameraScreen.kt` + `preview/CameraPreview.kt` (⚡ 2026-07-26)
Ảnh: `take_photo_screen.png`. Preview vuông bo lớn; flash pill tròn đen mờ (góc trên trái), zoom "1.0x" + "N" pill đen mờ (góc phải); **pinch 2 ngón trong preview = zoom** (pointerInput chỉ ăn khi ≥2 ngón — vuốt 1 ngón vẫn mở feed); dưới preview: icon gallery · **nút center bottom bar 80dp viền vàng: BẤM = chụp, GIỮ = quay video ≤5s, THẢ = dừng** · icon flip. ⚠️ Nút chụp 72dp TRONG preview đã XÓA (2026-07-26 — trùng nút); khi quay chỉ còn vòng tiến độ đỏ + đồng hồ đếm giây.

### 7.6 Submit photo — `feature/post/SubmitPhotoScreen.kt`
Ảnh: `submit_photo_screen.png`. Title "Send to…" giữa + icon download phải; ảnh preview bo 20dp (ảnh vừa chụp ưu tiên, overlay loading `Black 50%` khi đang gửi); **ô caption `InputCaptionPill` LUÔN hiện trên ảnh** (từ 2026-08-02 — placeholder "Add a caption...", gõ trực tiếp; chip từ Captions List điền vào ô này); **`PageIndicator`** (chấm 6dp gap, active trắng/inactive xám 30%); bottom bar `submitPhotoBar`: ✕ Cancel · **nút Send 80dp** (icon máy bay xoay -45°, viền vàng khi không icon) · Ⓐ Captions List; cuối: **FriendList ngang** (7.2), chọn = viền `Color.Yellow`.

### 7.7 Caption sheet — `component/sheet/CaptionBottomSheet.kt` (✅ nối vào Send 2026-07-12)
Ảnh: `submit_photo_add_caption.png`. Bottom sheet, title "Captions"; section "General"/"Decorative" Bold trái; **chip = GRADIENT** (không solid): thường `#444→#222`/`DarkGray→Black`, Weather `#4FC3F7→#0288D1`, Party `#81C784→#388E3C`, Good morning `#FFA726→#F57C00`; icon + chữ trắng. **Bấm chip → nội dung điền vào `InputCaptionPill` trên ảnh** (chip "Text" → pill rỗng tự gõ); pill = nền trắng mờ `#80F0F0F0`, **chữ đen** 16sp, bo 50, ≤30 ký tự, state hoisted; Send gửi caption kèm moment (server nhận ≤500).

### 7.8 Messages list — `feature/message/MessageScreen.kt` (✅ API + unread 2026-07-12)
Ảnh: `messages_screen.png`. Top: back · "Messages" Bold giữa · search phải. Item: avatar 50dp — **viền `Color.Yellow` khi có tin CHƯA ĐỌC** (tin cuối gửi đến mình, `isSeen=false`; preview cũng trắng SemiBold) + tên Bold + giờ xám cùng dòng, preview xám dưới, chevron `>` phải. Nền đen thuần, không card. Data: `/messages/conversations`, tên/avatar resolve từ danh sách bạn bè.

### 7.9 Chat 1-1 — `feature/message/ChatScreen.kt` (✅ API + gửi tin thật 2026-07-12)
Ảnh: `one_to_one_message_screen.png`. Top bar: back · avatar + tên · menu ⋮ (dùng colorScheme `surface`/`onSurface`). **Bubble bo góc ĐỘNG theo vị trí trong nhóm tin**: 20dp góc mở đầu, 16dp giữa, **6dp góc "đuôi"** cạnh người gửi; timestamp `HH:mm` xám nhỏ (giờ server = UTC → parse `Instant` về giờ máy). Input đáy = **`ChatInputPill`** (cùng style MessagePill bo 24dp): ô gõ text trắng 16sp; **rỗng → 3 emoji gửi nhanh (tin EMOJI, bubble = emoji trần 36sp)**, có text → nút Send trắng. **Tin mới bằng POLLING 5s** (REST thuần); lỗi gửi hiện Toast message server ("Chỉ nhắn tin được với bạn bè…"); mở thread tự mark seen.

### 7.10 Profile — `feature/profile/UserProfileScreen.kt` (✅ DATA THẬT 2026-07-12)
Ảnh: `my_profile_screen.png`. Top (`UserProfileTopBar.kt`): pill "Gold" nền `#74512D` 40% + **viền gradient gold** + chữ `#FFD700` Bold, bo 10dp · icon bạn bè/settings/`>`. Header: tên to Bold + email xám (**email CHỈ hiện ở profile của mình** — server giấu email người khác); **avatar viền `Brush.linearGradient` gold** (`#FFD700→#FFA500→#FFE55C`). Calendar: card bo 20dp, ô ngày = `EmptyDayItem` (bo 12dp, 1:1, dot 4dp), ngày có bài hiện thumbnail + đếm — data từ **`/moments/mine`** (hoặc `/moments/user/:uid`, server chặn người lạ 403). Đáy: stat pill "🧡 N **Moments** | 🔥 Nd streak" — N = số moment thật, streak = `personalStreak` từ API; **profile người khác CHỈ hiện ô streak** (user chốt 2026-07-12). `ProfileViewModel` + `data/ProfileRepository`.

### 7.11 Settings — `feature/settings/SettingScreen.kt`
Ảnh: `settings_screen.png`. Title giữa; section: icon + label Bold; item card `colorScheme.surface` bo **8dp**: icon tròn 40dp + title **Medium** + subtitle xám + chevron 16dp/toggle.

### 7.12 Friend sheet (quản lý bạn bè) — ✅ ĐÃ NỐI API + QR (2026-07-12)
Ảnh: `user_detail_bottom_sheet.png` → `component/sheet/UserDetailBottomSheet.kt`. Bottom sheet full: **"X out of 20 friends"** to Bold giữa; "Invite a friend to continue" phụ; **pill "Add new friend"** = MỞ `AddFriendQrDialog` (khác asset: không search — server chỉ hỗ trợ mã mời); "Your Friends": avatar + tên + **🔥 streak vàng `#FFD700`** (khi > 0) + **✕ xóa (có AlertDialog xác nhận, nút Xóa màu `GrayError`)**; "Find Friend From other Apps" + "Share your Snapget link" (trang trí). Data: `FriendsViewModel` → API `/friendships`. Entry: hàng "Add friends" cuối dropdown MainTopBar (feed) + icon 👥 `UserProfileTopBar`.

### 7.12b Kết bạn QR — `AddFriendQrDialog.kt` + `feature/friends/QrScanScreen.kt`
- **Dialog QR**: surface `GraySurface` bo 20dp; title "Add new friend" Bold; QR đen-trên-trắng (sinh bằng zxing từ invite link) trong thẻ trắng bo 16dp; mã mời chữ xám letterSpacing 2sp; divider "hoặc"; **nút pill TRẮNG chữ đen** "Quét QR của bạn bè" (cùng họ nút Sign In / Google của login).
- **Màn quét** (`qr_scan`): full đen, CameraX + ML Kit; **khung ngắm 260dp viền 3dp `Color.Yellow` bo 24dp** (accent duy nhất); hint trong pill đen 60%; nút ✕ tròn đen mờ; đang connect → overlay đen 50% + spinner vàng. Quét trúng → POST /friendships/connect → Toast message tiếng Việt của server.

### 7.13 Login — `feature/auth/LoginScreen.kt`
Ảnh: `login_screen.png`. Logo tròn trắng 120dp (lá vàng nhạt) giữa; "Welcome to Snapget" Bold 30sp; subtitle xám; **card `#1A1A1A` bo 24dp** chứa: input outline bo tròn icon trái (Email ✉, Password 🔒 + mắt); nút "Sign In" pill (disabled `#333` chữ xám → enabled trắng chữ đen); "Forgot Password?" trắng trái + **"Sign Up" VÀNG** phải; divider "OR"; **nút Google pill TRẮNG chữ đen** logo Google.

### 7.14 Splash/logo
Ảnh: `splash_screen.png`, `logo.png`. Nền trắng ngà `#F7F7F5`, logo lá 2 cánh vàng-xanh nhạt `#E3E6C4` giữa màn. (Splash là màn DUY NHẤT nền sáng.)

### 7.15 Group settings sheet — `feature/message/GroupSettingsSheet.kt` (✅ 2026-08-02, theo ảnh Messenger user gửi)
Entry: nút **⋯ (MoreVert)** bên phải header `GroupChatScreen` (subtitle header = "N members"). `ModalBottomSheet` nền `surface`, nội dung căn giữa: **avatar nhóm 96dp** (ảnh thật hoặc icon Groups vàng trên nền `#404137`; BẤM = đổi ảnh qua GetContent → upload → PATCH; spinner vàng đè khi busy) · **tên nhóm Bold + bút chì** (dialog đổi tên style CreateGroupDialog) · hàng **Invite** (vòng tròn NÉT ĐỨT `#B0B0B0` + dấu cộng, subtitle "New members can see previous messages"; dialog tick bạn bè chưa trong nhóm, chặn quá 20) · **danh sách thành viên** (avatar 44dp + tên + "(You)"/"Group creator"; menu **⋯ "Remove from group"** màu error — CHỈ người tạo thấy, kèm AlertDialog xác nhận) · **card `#2C2C2C` bo 20dp**: hàng Mute notifications (Switch trung tính trắng/xám — KHÔNG vàng) + divider + hàng **Leave group màu error** (confirm dialog). Mọi mutation → `MessageViewModel.mutateGroup` refetch detail + refresh danh sách nhóm; rời nhóm thành công → `navigateUp()`.

---

## 8. Tính năng CHƯA có asset — bắt buộc HỎI USER trước khi thiết kế

Các tính năng sau **không có ảnh mẫu**; khi làm phải: (1) đề xuất mockup chữ dựa trên ngôn ngữ thiết kế ở trên, (2) hỏi user chốt từng chi tiết (bố cục, vị trí entry point, hành vi), (3) mới code:

- ~~Reaction emoji bay~~ → ✅ đã làm ở post detail (7.4, user chốt 2026-07-12: bay + highlight, không đếm; ⊕ = hàng emoji mở rộng)
- **Doodle / vẽ tay lên ảnh** trước khi đăng
- **Khung ảnh (frame)** — chọn khung sau khi chụp + hiển thị khung quanh moment + catalog khung thưởng
- ~~Co-op capture (chụp chung)~~ → ✅ đã làm theo spec chữ user chốt 2026-08-02: popup chọn bạn (dialog `#2C2C2C`, avatar viền vàng khi chọn) → `CoopCaptureScreen` nửa camera + nửa xám `#2C2C2C` spinner chờ; nút chụp 80dp viền vàng → nút Send tròn mũi tên (xoay -45° như màn đăng bài) + icon Again trái; ảnh ghép → luồng edit/đăng bài thường
- **Daily Quest** — màn quest, nộp ảnh, trạng thái hoàn thành
- ~~Chat nhóm~~ → ✅ màn chat nhóm + **sheet cài đặt nhóm** đã làm (7.15 — user gửi ảnh mẫu Messenger 2026-08-02: đổi tên/avatar, mời/xóa thành viên, mute, rời nhóm); tạo nhóm vẫn là dialog GroupAdd ở Messages (chưa có asset riêng)
- ~~Friend streak cạnh tên bạn~~ → ✅ đã làm trong sheet bạn bè (7.12); nếu muốn hiện thêm chỗ khác (dropdown, chat) thì hỏi lại
- ~~Màn `relationship`~~ → ✅ đã làm = sheet 7.12 + QR 7.12b (user chốt 2026-07-12: chỉ mã mời, QR, bottom sheet)
- **Thông báo (notifications) UI**

Gợi ý mặc định khi đề xuất: tái dùng component mục 7, màu mục 2, shape mục 4; gamification (streak/quest/badge) luôn dùng **vàng `SnapGold`**.

---

## 9. Khác biệt assets ↔ code cần nhớ

1. **Branding:** ảnh ghi "Nocket"/"Locket" (app tham khảo) → mọi chữ trong code dùng **"Snapget"**. Chữ "Lockets" ở profile (`UserProfileScreen`) cũng cần đổi → "Moments".
2. ~~Profile calendar + streak pill chạy mock~~ → ✅ **đã nối data thật** (2026-07-12): `personalStreak` + moments từ API; chữ "Lockets" đã đổi "Moments". Friend streak hiện ở sheet bạn bè (7.12).
3. `messages_screen_old.png` là bản CŨ — bỏ qua, chỉ theo `messages_screen.png`.
4. Code hiện có `DarkBrownTheme.kt` (nâu + Amber) **không khớp assets** — chuẩn là Gray scheme trong `Theme.kt`; đừng dùng DarkBrown cho màn mới. (Dấu vết nâu còn sót: pill `#404137`, Gold pill `#74512D` — chấp nhận, không lan thêm.)
5. **Nút chụp giữa bottom bar ĐÃ viền vàng** (`MainBottomBar` → `Circle`, 80dp/3dp/`Color.Yellow`). ~~Nút shutter trong `CameraPreview.kt`~~ → **đã XÓA hẳn** (2026-07-26, trùng nút — chụp/quay đều qua nút center bottom bar).
6. **Login input/nút bo 12dp trong code** (`cornerShape = 12.dp`) nhưng ảnh trông tròn hơn (~28dp) — ảnh là chuẩn; khi chỉnh LoginScreen cân nhắc tăng, nhưng đừng đổi lắt nhắt một mình nó.
7. Item settings bo **8dp** trong code, ảnh trông ~16dp — lệch nhỏ, chấp nhận được; nếu làm lại màn settings thì theo ảnh.
8. Màu nền pill trong ảnh trông `#2C2C2C` nhưng code thật là **`#404137`** (ô liu) — khi thêm pill mới dùng `#404137` cho đồng bộ với pill hiện có.

---

> **Checklist trước khi merge UI mới:** nền `GrayBackground`? bo góc theo mục 4? accent chỉ vàng? overlay đen mờ 50–60%? chữ Bold trắng/phụ xám `#B0B0B0`? component tái dùng từ mục 7? nếu tính năng thuộc mục 8 → đã hỏi user chưa?

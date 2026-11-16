# DESIGN.md — Hệ thống thiết kế Snapget

> Nguồn chuẩn giao diện: **bộ ảnh demo trong `Sources/assets/`** — mọi màn hình/tính năng mới PHẢI bám theo phong cách này để app đồng nhất.
> Đối chiếu code: theme thật ở `core/designsystem/theme/` (Color.kt, Theme.kt, Type.kt).
> ⚠️ **Luật bắt buộc:** làm tính năng UI **chưa có sẵn trong code** → đọc file này, rồi **HỎI USER THẬT CHI TIẾT** (bố cục, hành vi, vị trí vào màn nào) trước khi viết dòng code nào.

---

## 0. 🎮 Định hướng v2 — "Playful Dark" (2026-11-15, ĐANG LÊN PLAN)

> **Mục tiêu (user chốt 2026-11-15):** app có cảm giác **sinh động, vui, "gây nghiện" kiểu app học ngôn ngữ gamified**
> — mỗi hành động đều có phản hồi, mỗi thành tựu đều được ăn mừng.
> **Nguồn:** skill `ui-ux-pro-max` (style **Claymorphism**: khối dày, bo lớn, nút có chiều sâu, nhấn "lún";
> motion "Stagger + back-out"; font tròn) + skill `mobile-android-design` (M3 motion, haptic, 48dp, semantics).
> Skill gợi ý palette **nền sáng cam** → **KHÔNG áp**: Snapget giữ dark-first + vàng là màu thương hiệu;
> chỉ mượn *hình khối, chuyển động, nhịp phản hồi*.
> ⚠️ Lấy **nguyên tắc** của các app gamified, **không sao chép** tài sản của app cụ thể nào (linh vật, màu
> thương hiệu, font riêng, âm thanh, minh họa).
>
> Ký hiệu: ✅ = đã chốt, áp ngay cho UI mới · ⏳ = đề xuất, **chờ user chốt** (xem `UI_UPGRADE_PLAN.md`) ·
> Mục 1–9 phía dưới vẫn là **hiện trạng code**; chỗ nào v2 thay đổi thì mục 0 thắng khi đã ✅.

### 0.1 Năm nguyên tắc "Playful Dark"
1. ✅ **Mọi chạm đều có phản hồi** — nhấn = lún/co (scale 0.94–0.96) + haptic nhẹ; không còn nút "chết" bấm không thấy gì (`indication = null` bị cấm cho phần tử bấm được).
2. ✅ **Ăn mừng thành tựu, không ăn mừng thao tác vặt** — streak +1, xong quest, mua item, đăng bài đầu ngày = khoảnh khắc lớn (mục 0.5); bấm nút thường chỉ micro-interaction. Skill: *"animate 1–2 key elements per view"*.
3. ✅ **Tiến độ luôn nhìn thấy được** — streak, quest, mốc khung hiển thị dạng thanh/vòng tiến độ chứ không chỉ con số.
4. ✅ **Khối dày, tròn, "đồ chơi"** — nút chính có đáy dày tạo chiều sâu (mục 0.3), bo lớn, icon to, chữ đậm.
5. ✅ **Ảnh vẫn là nhân vật chính** — playful nằm ở *chrome* (nút, sheet, tab, gamification); màn camera/feed giữ tối giản, không đè hiệu ứng lên ảnh người dùng.

### 0.2 Motion system (token chuyển động — thêm vào `core/designsystem/motion/`)
| Token | Giá trị | Dùng cho |
|---|---|---|
| `Motion.press` | scale → 0.95, `spring(dampingRatio = 0.5f, stiffness = 800f)` | Mọi nút/ô bấm được |
| `Motion.pop` | scale 0.6 → 1.08 → 1, `spring(0.45f, 400f)` | Badge mới, emoji reaction, item vừa mua, check quest |
| `Motion.enter` | fade + slide 16dp, 300ms `FastOutSlowIn`, stagger **40ms/item** (tối đa 8 item) | List/grid lần đầu hiện (Shop, Quest, Messages) |
| `Motion.exit` | fade 150ms (exit **nhanh hơn** enter) | Đóng sheet/dialog, xóa item |
| `Motion.celebrate` | 1.2–2s, confetti/particle + số đếm tăng dần | Chỉ các khoảnh khắc mục 0.5 |
| `Motion.shake` | lắc ngang 3 nhịp ±8dp, 300ms | Lỗi nhập (sai mật khẩu, không đủ Astrite) |

- ✅ **Reduced motion bắt buộc:** đọc `Settings.Global.ANIMATOR_DURATION_SCALE` (= 0 → tắt) ⇒ bỏ confetti/stagger, giữ fade ngắn. Hiệu ứng **không bao giờ** là cách duy nhất truyền thông tin.
- ✅ Animate bằng API Compose (`animate*AsState`, `AnimatedVisibility`, `updateTransition`), logic animation nằm ở UI, **không** ở ViewModel (skill stack jetpack-compose).
- ✅ Chỉ animate `graphicsLayer` (scale/alpha/translation) — không animate width/height gây re-layout.

### 0.3 Nút "chunky" (component mới `core/designsystem/component/button/ChunkyButton.kt`) — ✅ chốt 2026-11-15
- Thân nút màu `accent`, **đáy dày 4dp** màu `accent` tối hơn ~30% (token mới `accentShadow`); nhấn → thân dịch xuống 4dp, đáy biến mất = cảm giác "lún". Bo `shapes.input` (16dp), cao **52dp**, chữ Bold 16sp.
- Biến thể: `Primary` (accent) · `Secondary` (nền `pill`, đáy `surfaceVariant`) · `Danger` (error) · `Disabled` (phẳng, không đáy, `textSecondary`).
- Áp cho CTA chính: Sign In, Buy/Apply (Shop), Next (Edit), Accept (coop), Retry. **Không** áp cho nút chụp 80dp (giữ vòng tròn viền vàng — bản sắc camera).

### 0.4 Phản hồi xúc giác (haptic) — ✅
| Sự kiện | Haptic |
|---|---|
| Chụp ảnh | `HapticFeedbackType.LongPress` |
| Bắt đầu / dừng quay GIF | `LongPress` lúc bắt đầu, `TextHandleMove` lúc dừng |
| Thả reaction, gửi tin | `TextHandleMove` (nhẹ) |
| Hoàn thành quest / streak +1 / mua thành công | `Confirm` (API 30+), fallback `LongPress` |
| Lỗi (shake) | `Reject` (API 30+), fallback không rung |
Tôn trọng cài đặt rung hệ thống (dùng `LocalHapticFeedback`, không gọi `Vibrator` trực tiếp). ⏳ Có thể thêm toggle "Haptics" trong Settings.

**Âm thanh — ❌ KHÔNG CÓ (user chốt 2026-11-15, đổi ý sau khi đã làm):** app **không phát bất kỳ âm thanh nào** (đã xóa `SoundEffects`, `res/raw/sfx_*`, setting "Sound effects"). Phản hồi chỉ bằng **hình (Lottie/motion) + rung**. Đừng thêm âm thanh lại nếu user chưa yêu cầu.

### 0.5 Khoảnh khắc ăn mừng (celebration moments) — ✅ chốt "kết hợp" 2026-11-15
- **Lớn** (streak +1, mốc 3/7/14/30, mở khóa khung) = **overlay toàn màn** + Lottie + haptic `Confirm` (không âm thanh).
- **Nhỏ** (xong 1 quest, mua item, kết bạn, AI challenge) = **hiệu ứng tại chỗ** (`pop`, lấp lánh, số đếm) — không cắt ngang luồng.
- Animation = **Lottie** (`lottie-compose`, user cho phép 2026-11-15) lấy từ LottieFiles free; **không dùng linh vật**. File JSON đặt `res/raw/lottie_*.json`; đổi màu theo skin bằng `LottieDynamicProperties` (key màu chính → `accent`/`accentGold`). Quy trình tìm & thêm file: `UI_UPGRADE_PLAN.md` mục "Lottie".
| Khoảnh khắc | Đề xuất hiển thị |
|---|---|
| Đăng moment đầu tiên trong ngày → **streak +1** | Màn phủ toàn màn: 🔥 to nảy (`pop`), số streak **đếm lên** (N-1 → N), chữ "N day streak!", tự đóng 2s hoặc chạm để đóng |
| Đạt mốc streak 3/7/14/30 | Như trên + confetti vàng + preview **khung vừa mở khóa** + nút "Use this frame" |
| Xong quest (từng quest) | Card quest: icon check `pop` + viền vàng chớp; số "+30 Astrite" bay lên số dư |
| Xong 2/2 quest ngày | Banner thưởng trượt vào + số Astrite đếm lên |
| Mua item ở Shop | Item `pop` + lấp lánh, số dư **đếm xuống** |
| Kết bạn thành công (QR) | Avatar 2 người chụm vào nhau + confetti nhỏ |
| AI challenge đúng | Thay toast bằng sheet "Challenge complete! 🎯" |

### 0.6 Tiến độ & gamification hiển thị — ✅ nguyên tắc / ⏳ bố cục
- Streak: **thanh tiến độ tới mốc kế tiếp** ("5 / 7 → unlock frame"), không chỉ số trần.
- Quest: vòng tiến độ tổng `completed/total`; phần thưởng (+60 / +30 Astrite) **hiện trước** khi làm, không chỉ sau.
- Card quest chưa xong có **CTA đi làm ngay** (vd "Post a moment" → camera).
- ✅ Nhắc giữ streak = **push notification** từ server (user chốt 2026-11-15), không chèn banner/chấm trong app; chi tiết + contract `POST /moments.streak`: `UI_UPGRADE_PLAN.md`.

### 0.7 Màu bổ sung — ✅ chốt 2026-11-15 (nới nguyên tắc 1.2: vàng vẫn là màu THƯƠNG HIỆU duy nhất; thêm màu CHỨC NĂNG)
**Token chức năng** (không phải accent trang trí), có giá trị riêng mỗi skin:
| Token | Default skin | Contrast / `#121212` | Vai trò |
|---|---|---|---|
| `success` | `#58CC7A` (xanh lá dịu) | 9.2:1 | Quest xong, thao tác thành công, check |
| `streak` | `#FF9F1C` (cam lửa) | 9.1:1 | Riêng streak 🔥 — tách khỏi `accentGold` |
| `info` | `#4FC3F7` | — | Thông tin trung tính, link phụ |
| `badge` | = **`accentGold`** (✅ user chốt) | — | Badge chưa đọc ở MỌI nơi (bỏ đỏ `0xFFFF3B30` của footer), chữ `onAccent` |
| `accentShadow` | accent × 0.7 | — | Đáy nút chunky (0.3) |
Kiểm contrast ≥ 4.5:1 cho chữ, ≥ 3:1 cho icon/viền trên `#121212`.

**Tông vàng mới — ✅ chốt A · Honey Gold (2026-11-15):** `accent = #F5C542`, `accentGold = #E8A93A`, `accentShadow = #AB892E` (trang so sánh: https://claude.ai/artifact/CQEpwAGRYGdpcp8Y3hDgDq). Lý do đổi: `#FFFF00` là vàng chanh thuần, quá chói trên nền tối. Mọi chỗ trong file này còn ghi `#FFFF00`/`#FFD700`/`Color.Yellow` = giá trị CŨ, đọc là `accent`/`accentGold`.
| Phương án | `accent` | `accentGold` | Contrast accent / gold |
|---|---|---|---|
| Hiện tại | `#FFFF00` | `#FFD700` | 17.4 / 13.4 |
| A · Honey Gold (đề xuất) | `#F5C542` | `#E8A93A` | 11.6 / 9.1 |
| B · Champagne Gold | `#E6C068` | `#D4A24C` | 10.8 / 8.1 |
| C · Antique Gold | `#D4A94E` | `#B8893A` | 8.6 / 6.0 |
Đổi = sửa 2 dòng trong `skins/DefaultSkin.kt` (mọi chỗ đã đọc qua token) + gradient gold avatar profile (mục 2, chưa token hóa) cho cùng tông.

### 0.8 Typography v2 — ✅ chốt **Nunito cho cả app** (2026-11-15)
- Nunito (Google Fonts, OFL, đủ dấu tiếng Việt) — file `.ttf` các weight 400/600/700/800/900 vào `res/font/`, khai `FontFamily` trong `Type.kt` (không dùng Downloadable Fonts để app chạy offline). `AppSkin.fontFamily` vẫn cho skin ghi đè.
- Bổ sung đủ thang M3 vào `Type.kt` (display/headline/title/body/label) **kèm fontWeight** — bỏ thói quen set `fontWeight` tại chỗ.
- Số gamification (streak, Astrite, đếm quest) dùng style riêng `numberDisplay`: 28–40sp ExtraBold, `fontFeatureSettings = "tnum"` để số đếm lên không nhảy bề ngang.
- ✅ Chữ tối thiểu **12sp**; không dùng alpha để làm mờ chữ (dùng `textSecondary`).

### 0.8b Thay đổi điều hướng đã chốt (2026-11-15)
- **Footer 5 tab:** giữ **chỉ icon** (không nhãn chữ). Tab đang chọn: đổi màu `navBarActive` **và phình to** 26dp → 32dp bằng `Motion.pop` (spring nảy nhẹ); bỏ `indication = null`, có semantics `Role.Tab` + `selected`.
- **Camera top bar:** pill giữa **không còn là dropdown "Everyone"** → hiển thị **"N Friends"** (số bạn bè thật); bấm = mở **sheet bạn bè** (`UserDetailBottomSheet`, giống icon 👥 ở profile).
- **Camera bottom bar:** **xóa** icon "Photo Library" (trước mở nhầm Feed). Còn lại: nút chụp giữa + nút lật camera.

### 0.9 Giọng văn (UX copy) — ✅
Ngắn, ấm, khích lệ, ngôi thứ hai. Empty/error state luôn có **1 hành động**:
- ❌ "No posts to show" → ✅ "Nothing here yet — be the first to share today!" + nút **Open camera**
- ❌ (lỗi mạng hiện như rỗng) → ✅ "Couldn't load. Check your connection." + **Retry**
- Thành công: "Posted! 🔥 Streak: 6 days" thay vì "Posted successfully!"

### 0.10 Baseline accessibility & chất lượng (từ `UI_REVIEW.md`) — ✅ áp cho mọi UI mới/sửa
- Touch target ≥ **48dp** (`minimumInteractiveComponentSize()`), giữ kích thước hiển thị.
- Icon trang trí cạnh chữ: `contentDescription = null`; tab/chip có `selected` + `Role` trong semantics.
- 3 trạng thái tách bạch **Loading (skeleton) / Error (+Retry) / Empty (+CTA)**; danh sách có pull-to-refresh.
- Không xóa nội dung người dùng trước khi thao tác thành công; màn chỉnh sửa có "Discard changes?".
- Phản hồi = **Snackbar dùng chung** qua `rememberMessenger().show(...)` (`component/feedback/AppMessenger.kt`, host ở `MainActivity`) — **không dùng Toast** trong Composable mới.
- Loading = **skeleton** (`component/state/Skeleton.kt`) giữ đúng bố cục, không spinner giữa màn (trừ thao tác ngắn trong nút).
- Màu mới → token `SkinColors` trước (luật SKIN), không hardcode trong `feature/`.

---

## 1. Triết lý thiết kế (nhìn từ assets)

1. **Dark-first, ảnh là nhân vật chính.** Nền đen gần tuyệt đối; UI chrome (bar, nút) tối giản, chìm xuống để ảnh nổi bật. Không dùng light theme (AppTheme đang force dark).
2. **Vàng (yellow/gold) là accent DUY NHẤT.** Chỉ dùng cho: viền nút chụp, viền avatar (bạn bè/chưa xem), link nhấn mạnh (Sign Up), thành phần gamification (streak, badge số, "Gold"). Không thêm màu accent khác trừ chip trang trí (mục 7.8).
3. **Bo góc lớn ở mọi nơi.** Ảnh bo ~20–28dp, pill/nút bo tròn hoàn toàn (CircleShape/50%), card bo 16–24dp. Không có góc vuông.
4. **Overlay đen mờ trên ảnh.** Mọi thứ đè lên ảnh (caption, nút flash, zoom) = nền `Color.Black.copy(alpha = 0.5f–0.6f)` bo tròn, chữ/icon trắng.
5. **Ít chữ, nhiều icon tròn.** Nút hành động = icon trong hình tròn nền tối; label chỉ khi cần.

---

## 2. Bảng màu (token ↔ code ↔ chỗ dùng trong ảnh)

> ### 🎨 CẬP NHẬT 2026-08-05 — màu đã TOKEN HÓA (P0–P2, `SKIN_PLAN.md`)
> Không còn `Color.kt` (đã xóa) và **không còn giao diện Light**. Mọi màu đọc qua
> **`SkinTheme.colors.xxx`** (`core/designsystem/skin/`); giá trị cụ thể của giao diện đen
> hiện tại nằm trong **`skins/DefaultSkin.kt`** — **hex giữ nguyên y hệt bảng dưới**, chỉ đổi tên.
>
> **Cần màu mới → thêm TOKEN vào `SkinColors` trước, cấm hardcode trong `feature/`.**
> Lý do: đổi skin phải đồng bộ — khai `accent` xanh thì *cả 50 chỗ* vàng đổi cùng lúc, không sót.

### Nền & bề mặt
| Token | Hex (DefaultSkin) | Tên cũ | Dùng cho (theo ảnh) |
|---|---|---|---|
| `colors.background` | `#121212` | `GrayBackground` | Nền mọi màn hình (feed, chat, settings…) |
| `colors.surface` | `#1A1A1A` | `GraySurface` | Card đăng nhập, item settings, bottom sheet |
| `colors.surfaceVariant` | `#2C2C2C` | `GraySurfaceVariant` | Bubble chat đến, input "Send message…", chip caption mặc định, panel dropdown bạn bè |
| `colors.textPrimary` | `#FFFFFF` | `Color.White` | Chữ/icon chính (tên, tiêu đề) — **Bold** |
| `colors.textSecondary` | `#B0B0B0` | `GrayOnSurfaceVariant` | Chữ phụ (subtitle, "Now", preview tin nhắn, placeholder) |
| `colors.onSurfaceVariant` | `#B0B0B0` | `GrayOnSurfaceVariant` | Viền avatar, chữ trên bề mặt phụ |
| `colors.navBarActive` | `#FFFFFF` | — | Icon Footer 5 tab đang chọn (đổi màu theo skin: Snow `#7EC8FF`, Forest `#E8D9A8`) |
| `colors.navBarInactive` | `#757575` | — | Icon Footer 5 tab chưa chọn |
| `colors.actionContent` | = `navBarActive` | — | **(2026-11-16)** Icon + chữ của NÚT HÀNH ĐỘNG nổi trên chrome (top bar, nút sửa, gợi ý "History"). ❌ Đừng dùng `textPrimary`/`onBackground` cho các nút này — 2 token đó gần trắng ở mọi skin nên đổi skin không thấy khác |
| `colors.error` | `#CF6679` | `GrayError` | Lỗi |

### Accent vàng — 2 vai trò, đừng gộp làm một
| Token | Hex | Dùng ở đâu |
|---|---|---|
| `colors.accent` | `#FFFF00` | Viền nút chụp giữa bottom bar (`MainBottomBar`+`CircleComponent`, border 3dp), viền avatar **đang chọn** trong `FriendList`, flash bật (`CameraPreview`) |
| `colors.accentGold` | `#FFD700` | Link "Sign Up" (`LoginScreen`), chữ + viền pill Gold (`UserProfileTopBar`), viền avatar profile, stat pill Lockets/streak, nút cúp Daily Quest |
| Gradient gold `#FFD700 → #FFA500 → #FFE55C` và `#FFD700 → #FFF380 → #FFD700` | — | Viền avatar profile + viền pill "Get Gold" (`Brush.linearGradient`) — **chưa token hóa** |

> Vì sao tách 2: trong giao diện đen chúng là 2 sắc thái khác nhau (chọn/chụp vs gamification).
> Skin mới chỉ cần khai 2 giá trị **cùng hệ màu** là vẫn "đổi đồng bộ" đúng ý. **Đừng thêm hex vàng thứ 3.**

### Màu "chìm" đặc trưng khác (THẬT trong code — dễ đoán nhầm từ ảnh)
| Token / Giá trị | Dùng ở đâu |
|---|---|
| `colors.pill` = `#404137` (xám ô liu tối) | **Nền pill "Everyone"** (`MainPill`), nền avatar `FriendList`, nút top bar |
| `colors.pillTranslucent` = `#424242` @50% | Nền pill **mờ trên camera/ảnh** (top bar) — tên cũ `BackgroundPreview` |
| `colors.onSurfaceVariant` (trước là `#B8B8B8`) | Viền avatar **không** chọn trong `FriendList` |
| `Color(0x80F0F0F0)` (trắng mờ 50%) | Nền `InputCaptionPill` — caption nhập trên ảnh, **chữ ĐEN 16sp** |
| `Color.Black.copy(alpha = 0.5f–0.6f)` | Caption hiển thị trên ảnh (bo 24dp), overlay play video 72dp (`PostDetailScreen`) |
| Gradient `#CC424242 → #CC616161` + `blur(16dp)` | `BlurredContainer` (panel mờ, bo 20dp) |

### Màu chức năng phụ (chip caption "Decorative" — là GRADIENT, không phải solid)
Xanh dương `#4FC3F7→#0288D1` (Weather) · Xanh lá `#81C784→#388E3C` (Party) · Cam `#FFA726→#F57C00` (Good morning) · Xám `#444444→#222222`, `#888888→#444444`, `DarkGray→Black` (chips thường). KHÔNG dùng các màu này cho UI chung.

---

## 3. Typography

**LUẬT CHỮ (chốt 2026-11-16 — tham khảo ui-ux-pro-max: `text-styles-system`, `weight-hierarchy`, `readable-font-size`, Compose "No hardcoded text style").**
Font **Nunito** cả app; thang đầy đủ + độ đậm nằm SẴN trong `core/designsystem/theme/Type.kt` (`appTypography`). Mỗi chữ trên màn hình thuộc **đúng 1 vai trò** dưới đây:

| Vai trò | Style M3 | Cỡ · độ đậm | Dùng cho |
|---|---|---|---|
| **Hero** | `headlineMedium` | 26 · ExtraBold | Tiêu đề chính của màn KHÔNG có top bar: tên ở Profile, "Welcome to Snapget" (Login). Mỗi màn tối đa 1 |
| **Tiêu đề màn / popup** | `titleLarge` | 22 · ExtraBold | Title top bar, tiêu đề dialog/sheet/popup ("Create group chat", "Top up Astrite") |
| **Tiêu đề mục** | `titleMedium` | 17 · Bold | Header nhóm/section ("💌 Friend requests", tháng lịch), trạng thái lớn ("Merging photos…"), số lượng nổi bật |
| **Tiêu đề dòng** | `titleSmall` | 15 · Bold | Dòng chính trong list/card (tên bạn bè, thành viên, setting, gói nạp), toast/banner |
| **Nội dung** | `bodyLarge` | 16 · Regular | Tin nhắn, ô nhập, nội dung chính (mặc định của `Text`) |
| **Mô tả** | `bodyMedium` | 14 · Regular | Mô tả dưới tiêu đề, chữ trong dialog, trợ giúp |
| **Chú thích** | `bodySmall` | 13 · Regular | Thời gian, "Valid until…", ghi chú nhỏ |
| **Nút** | `labelLarge` | 15 · ExtraBold | MỌI nút/CTA/TextButton ("Save", "Retry", "Show more") |
| **Chip / thẻ** | `labelMedium` | 13 · Bold | Chip, tab, tag, đồng hồ đếm ngược nhỏ |
| **Badge** | `labelSmall` | 12 · Bold | Số đếm trên badge, nhãn tí hon ("In use") |
| **Số gamification** | `NumberDisplay` | 40 · Black, `tnum` | Số streak/Astrite cỡ lớn |

**Luật:**
1. Thứ bậc: trong 1 màn, tiêu đề (Hero hoặc Tiêu đề màn) là chữ **to nhất + đậm nhất**; không chữ nào khác được to hơn nó.
2. ❌ **Cấm `fontSize = N.sp`** trong `Text` (feature + component). Ngoại lệ DUY NHẤT: phóng to **emoji/glyph trang trí** (reaction bay, emoji chat lớn, 🔒) — kèm comment.
3. ❌ **Cấm ghi đè `fontWeight`** — độ đậm đi theo vai trò. Cần đậm hơn ⇒ đổi sang vai trò cao hơn (vd tên trong list: `bodyLarge + Bold` → `titleSmall`). `FontWeight.Medium` vô nghĩa (Nunito không có file 500).
4. `.copy(...)` chỉ để thêm `fontFeatureSettings = "tnum"` (số đếm), màu, hoặc lineHeight — không đổi cỡ/độ đậm.
5. Tối thiểu **12sp** (`labelSmall`); chữ đọc liên tục ≥ 14sp.
6. Màu chữ theo token (`textPrimary` / `textSecondary` / `actionContent` / `onAccent`) — xem mục 2.
7. Ngoại lệ có chủ đích: widget Glance (`SnapgetWidget.kt`) dùng `TextStyle` của Glance, không đi qua `MaterialTheme`.

---

## 4. Hình khối & bo góc (shape scale)

Giá trị THẬT trong code (đối chiếu 2026-07-12).

> **2026-08-05:** 4 bo góc **dùng chung** đã token hóa — dùng token thay vì gõ lại số:
> `shapes.pill` = 50 (tròn) · `shapes.image` = 20dp · `shapes.sheet` = 24dp · `shapes.input` = 16dp.
> Các giá trị lẻ dưới đây (8/10/12/14dp…) **giữ nguyên hardcode**: đó là bo góc cục bộ của
> từng component, gom vào token sẽ làm mất sắc thái.

| Thành phần | Bo góc thật | File |
|---|---|---|
| Pill "Everyone", `MainPill`, `InputCaptionPill` | `shapes.pill` (tròn hoàn toàn) | `MainPill.kt`, `MainTopBar.kt` |
| Ảnh feed grid + ảnh post detail | `shapes.image` (**20dp**) | `PostGrid.kt`, `PostDetailScreen.kt` |
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
- Nút chụp/Send giữa bottom bar: **80dp** (`customSizeCenter`), viền **3dp `colors.accent`** khi không icon, gap 7dp (Camera) / 20dp + rotate -45° (Send) — `MainBottomBar.kt`
- `Circle` mặc định: outer 56dp, viền 2dp `colors.accent` — `CircleComponent.kt`
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

- **Material Icons** (đã có `material-icons-extended`) — **đồng bộ theo style icon footer (chốt 2026-11-15):**
  - **Mặc định = `Icons.Outlined.*`** (và `Icons.AutoMirrored.Outlined.*` cho mũi tên/send/message…) ở **mọi** nơi: top bar, setting row, sheet, nút hành động, camera, chat.
  - **`Filled` CHỈ cho icon mang TRẠNG THÁI**: tab/item đang chọn (`selectedIcon` của footer/bottom bar), `Favorite` (= đã thích), `CheckCircle` (= đã chọn/thành công). Muốn thêm ngoại lệ → ghi vào đây trước.
  - ❌ Không dùng `Icons.Default.*` / `Icons.Filled.*` cho icon trung tính (`Default` chính là Filled). Không dùng Rounded/Sharp/TwoTone.
  - Icon riêng của skin (`SkinIcons`, Nhóm 1) vẽ theo art của skin, không bị luật này ràng buộc; nhánh fallback Material của `SkinIcon` vẫn là Outlined.
- **Kích thước icon skin (chốt 2026-11-16):** mọi `SkinIcon` dùng 1 bậc của `SkinIconSize` theo VAI TRÒ — `Inline` 16dp (chevron cuối dòng) · `Small` 20dp (cạnh chữ) · `Medium` 24dp (top bar, IconButton, nút gửi ở ô nhập — **mặc định khi không truyền size**) · `Large` 32dp (nút grid/⋯ trên feed, xem bài). ❌ Không đặt số lẻ (20/26/28dp…). Lý do: icon Snow là WebP `drawable-nodpi` (192–288px) — thiếu size là vẽ theo pixel → phình to.
- Icon skin **không xoay** (art đã vẽ đúng tư thế). Xoay −45° chỉ cho mũi tên Material của nút Send (`MainBottomBar`).
- Icon đè trên ảnh: đặt trong hình tròn `Black 60%` (flash, zoom "1.0x", play video).
- Icon hành động chính (chat, share, grid, flip camera): trắng, 24–32dp, nền tròn tối hoặc trần trên nền đen.
- Emoji dùng trực tiếp trong UI (reaction ❤️🔥😄, streak 🔥, lockets ❤️) — không thay bằng icon vector.

### 6.1 Nền skin & bề mặt kính (Skin v2 — chốt 2026-11-15, chi tiết `SKIN_PLAN.md` đầu file)

- **Nền app = `SkinBackdrop`** vẽ 1 lần trong `Navigation.kt` (ảnh/gradient + scrim + glow + hạt bay — **KHÔNG còn ornament**, 2026-11-16). Màn **mới**:
  - Màn **giao diện/danh sách** (settings, list, quest…) **và cả Camera + Feed** (từ 2026-11-16) → `Scaffold(containerColor = Color.Transparent)`, **không** `.background(colors.background)` ở gốc.
  - Màn chụp/xem ảnh muốn tắt hạt bay → thêm route vào `routesCoveringBackdrop` (Navigation.kt). Camera + Feed lộ nền skin VÀ có hạt bay (user chốt 2026-11-16). Màn sửa/đăng ảnh, QR, coop, Login vẫn nền đặc.
- **Ornament CHỈ ở popup + thông báo + sheet** (user chốt 2026-11-16), không ở màn toàn màn hình:
  - Dialog → dùng **`SkinAlertDialog`** thay `AlertDialog` (tham số y hệt; dải ornament 24dp nằm trong padding trên). `Dialog` tự dựng → `Modifier.skinPopupOrnament(containerColor, shape)` trên Surface (padding trên ≥ 24dp).
  - Banner thông báo (mời coop, lời mời kết bạn) → `skinPopupOrnament(..., band = <padding trên>)`.
  - Bottom sheet → `SkinSheetDragHandle` (dòng dưới).
- **Card nằm trên backdrop** → `Modifier.skinGlass(shape)` (hoặc `glassColor()` khi API cần `Color`) thay cho `colors.surface`. 3 bậc: `Low` 6% (mặc định) · `Base` 10% · `High` 14%.
- **Giữ ĐẶC:** dialog, bottom sheet (dùng `surface`/`surfaceContainer*`), nút chunky có lớp đáy (card Shop), mọi thứ đè lên ảnh.
- Top bar trên backdrop: nền trong suốt (`SimpleTopBar` đã vậy). Footer: `background` 90%.
- **Bottom sheet mới** → `dragHandle = { SkinSheetDragHandle(sheetColor = <đúng containerColor của sheet>) }` (hoặc dùng `AnimatedBottomSheet` — mặc định đã có). Handle riêng thì truyền vào tham số `handle`.
- **Màn chat/hội thoại mới** → gắn `Modifier.skinChatWallpaper()` vào modifier gốc (trước `imePadding`).
- **Loading cấp màn/khối** → `SkinLoading()` (không dùng `CircularProgressIndicator` trần; spinner ≤ 24dp trong nút thì giữ). **Khoảnh khắc ăn mừng** → `SkinParticleBurst(trigger = …)`, và chỉ chạy Lottie confetti/sparkle khi `!hasSkinParticle`.

---

## 7. Catalog component (ảnh ↔ code hiện có)

### 7.1 Top bar chính (feed/camera) — `component/topbar/MainTopBar.kt`
Ảnh: `post_screen.png`, `take_photo_screen.png`. 3 khối: **avatar tròn `avatarWidth`=40dp trái** (mở profile) · **pill "Everyone ⌄" giữa** (nền thật `#404137`, `RoundedCornerShape(50)`, chữ trắng **Medium**, chevron 18dp, padding h12/v6) · **icon chat tròn phải**. Padding bar: ngang 20dp. **Dropdown chọn bạn nằm NGAY TRONG MainTopBar** (panel bo 24dp, maxHeight 450dp, hàng = avatar 40dp + tên + chevron). Top bar phụ: `CommonTopBar.kt`, `SimpleTopBar.kt`, `MessageTopBar.kt`, `SettingTopBar.kt`, `UserProfileTopBar.kt`.

### 7.2 Friend list NGANG (chọn người nhận) — `component/list/FriendListComponent.kt` (`FriendList`)
Ảnh: `submit_photo_screen.png` (hàng avatar dưới cùng). Item cách nhau 20dp; avatar nền `#404137`, **viền `Color.Yellow` khi được chọn / `#B8B8B8` khi không**; tên trắng cách avatar 5dp. ("Everyone" đầu tiên.) *Lưu ý: dropdown trên feed là của `MainTopBar` (7.1), không phải file này.*

### 7.3 Feed grid — `component/grid/PostGrid.kt`
Ảnh: `post_screen.png`. `GridCells.Fixed(3)`, ảnh vuông (`aspectRatio 1f`) bo **20dp**, gap **3dp**; **"ảnh GIF" hiện poster frame** (đổi đuôi `.mp4`→`.jpg` của Cloudinary) + **badge chữ "GIF"** góc trên phải nền `Black 60%` bo 8dp (2026-08-03 — trước là ô đen + nút play giữa; grid KHÔNG tự phát để không nuôi hàng chục ExoPlayer); top bar đè lên trên (grid padding top).

### 7.4 Post feed pager + detail — `feature/post/PostScreen.kt` + `PostDetailScreen.kt` (⚡ ĐẠI TU 2026-07-26 theo 2 ảnh mẫu Locket của user)
- **Feed = VerticalPager full-screen** (mặc định khi vuốt lên từ camera): mỗi post 1 trang mới→cũ; ở post mới nhất vuốt xuống → về camera; icon lưới (dưới trái) → grid tổng hợp, bấm 1 ô → về pager đúng post.
- **Nội dung 1 post** (`PostDetailContent` — dùng chung pager + màn profile): ảnh lớn `aspectRatio 1f` bo **20dp**; caption đè đáy ảnh (nền `Black 50%` bo 24dp); **"ảnh GIF" (moment VIDEO) = `GifVideoPlayer` tự phát + lặp vô hạn + muted, KHÔNG nút play** (2026-08-03 — overlay play 72dp đã xóa); dưới ảnh: avatar 40dp + **tên Bold** + thời gian tương đối NGẮN kiểu **"1d"** (`relativeTimeShort`).
- Thanh **"Send message…"** = `pill/MessagePill.kt` (bo 24dp, CỐ ĐỊNH không cuộn theo trang): **gõ text = gửi DM thật tới tác giả** (BasicTextField, có text → nút Send thay chỗ emoji); 3 emoji nhanh **💛😂💕** (đổi theo mẫu 2026-07-26) **bấm = thả reaction**, highlight vàng 25% khi đã thả, ⊕ xòe 8 emoji mở rộng, emoji bay 34sp (`FlyingEmojiOverlay`).
- Hàng nút đáy pager: icon lưới `GridView` · **nút giữa 80dp viền vàng 3dp** (→ camera) · **⋯ `MoreHoriz`** mở `PostOptionsSheet` (bottom sheet chữ căn giữa: Share / Download / **Delete đỏ chỉ bài mình** / Cancel — khớp popup mẫu; KHÔNG có Report).
- **`PostDetailScreen` (xem post cũ từ calendar profile) dùng ĐÚNG hàng nút này** (đồng bộ 2026-08-02): lưới = về calendar profile, nút chụp = về camera, ⋯ = cùng `PostOptionsSheet`; xóa bài → đóng detail + tải lại calendar. KHÔNG dùng lại `sampleItems3` (bản cũ có icon Share mở Settings — đã bỏ).
⚠️ `Frame.kt` là THANH MESSAGE cũ (bản trùng, không dùng) — đừng nhầm với tính năng "frame" phần thưởng.

### 7.5 Camera — `feature/camera/CameraScreen.kt` + `preview/CameraPreview.kt` (⚡ 2026-07-26)
Ảnh: `take_photo_screen.png`. Preview vuông bo lớn; flash pill tròn đen mờ (góc trên trái), zoom "1.0x" + "N" pill đen mờ (góc phải); **pinch 2 ngón trong preview = zoom** (pointerInput chỉ ăn khi ≥2 ngón — vuốt 1 ngón vẫn mở feed); dưới preview: icon gallery · **nút center bottom bar 80dp viền vàng: BẤM = chụp ảnh, GIỮ = quay "ảnh GIF" ≤3s, THẢ = dừng** · icon flip. ⚠️ Nút chụp 72dp TRONG preview đã XÓA (2026-07-26 — trùng nút); **không có dòng hint nào dưới nút chụp** (user chốt 2026-08-03). **Khi quay KHÔNG hiện gì thêm** (đồng hồ đếm giây + vòng tiến độ đỏ đã XÓA 2026-08-03 — GIF chỉ là ảnh biết chuyển động nên giữ UI sạch như lúc chụp ảnh).

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
Ảnh: `user_detail_bottom_sheet.png` → `component/sheet/UserDetailBottomSheet.kt`. Bottom sheet full: **"X out of 20 friends"** to Bold giữa; "Invite a friend to continue" phụ; **pill "Add new friend"** = MỞ `AddFriendQrDialog` (khác asset: không search — server chỉ hỗ trợ mã mời); "Your Friends": avatar + tên + **✕ xóa (có AlertDialog xác nhận, nút Xóa màu `GrayError`)**; "Find Friend From other Apps" + "Share your Snapget link" (trang trí). Data: `FriendsViewModel` → API `/friendships`. Entry: hàng "Add friends" cuối dropdown MainTopBar (feed) + icon 👥 `UserProfileTopBar`.

### 7.12b Kết bạn QR — `AddFriendQrDialog.kt` + `feature/friends/QrScanScreen.kt`
- **Dialog QR**: surface `GraySurface` bo 20dp; title "Add new friend" Bold; QR đen-trên-trắng (sinh bằng zxing từ invite link) trong thẻ trắng bo 16dp; mã mời chữ xám letterSpacing 2sp; divider "hoặc"; **nút pill TRẮNG chữ đen** "Quét QR của bạn bè" (cùng họ nút Sign In / Google của login).
- **Màn quét** (`qr_scan`): full đen, CameraX + ML Kit; **khung ngắm 260dp viền 3dp `Color.Yellow` bo 24dp** (accent duy nhất); hint trong pill đen 60%; nút ✕ tròn đen mờ; đang connect → overlay đen 50% + spinner vàng. Quét trúng → POST /friendships/connect → Toast message tiếng Việt của server.

### 7.13 Login — `feature/auth/LoginScreen.kt`
Ảnh: `login_screen.png`. Logo tròn trắng 120dp (lá vàng nhạt) giữa; "Welcome to Snapget" Bold 30sp; subtitle xám; **card `#1A1A1A` bo 24dp** chứa: input outline bo tròn icon trái (Email ✉, Password 🔒 + mắt); nút "Sign In" pill (disabled `#333` chữ xám → enabled trắng chữ đen); "Forgot Password?" trắng trái + **"Sign Up" VÀNG** phải; divider "OR"; **nút Google pill TRẮNG chữ đen** logo Google.

### 7.14 Splash/logo
Ảnh: `splash_screen.png`, `logo.png`. Nền trắng ngà `#F7F7F5`, logo lá 2 cánh vàng-xanh nhạt `#E3E6C4` giữa màn. (Splash là màn DUY NHẤT nền sáng.)

### 7.15 Group settings sheet — `feature/message/GroupSettingsSheet.kt` (✅ 2026-08-02, theo ảnh Messenger user gửi)
Entry: nút **⋯ (MoreVert)** bên phải header `GroupChatScreen` (subtitle header = "N members"). `ModalBottomSheet` nền `surface`, nội dung căn giữa: **avatar nhóm 96dp** (ảnh thật hoặc icon Groups vàng trên nền `#404137`; BẤM = đổi ảnh qua GetContent → upload → PATCH; spinner vàng đè khi busy) · **tên nhóm Bold + bút chì** (dialog đổi tên style CreateGroupDialog) · hàng **Invite** (vòng tròn NÉT ĐỨT `#B0B0B0` + dấu cộng, subtitle "New members can see previous messages"; dialog tick bạn bè chưa trong nhóm, chặn quá 20) · **danh sách thành viên** (avatar 44dp + tên + "(You)"/"Group creator"; menu **⋯ "Remove from group"** màu error — CHỈ người tạo thấy, kèm AlertDialog xác nhận) · **card `#2C2C2C` bo 20dp**: hàng Mute notifications (Switch trung tính trắng/xám — KHÔNG vàng) + divider + hàng **Leave group màu error** (confirm dialog). Mọi mutation → `MessageViewModel.mutateGroup` refetch detail + refresh danh sách nhóm; rời nhóm thành công → `navigateUp()`.

### 7.16 Footer 5 tab — `component/bottombar/AppFooterBar.kt` (✅ 2026-11-13)
Hiển thị tại `Navigation.kt` cho các màn tab chính: **Quest (`daily_quest`) · Chat (`message`) · Home (`camera` & `post` pager/grid) · Shop (`shop`) · Profile (`profile`)**. Nền `SkinTheme.colors.background` + `navigationBarsPadding()`, cao `64.dp`. Icon active/inactive dùng token `navBarActive`/`navBarInactive` (Filled khi active, Outlined khi inactive); nút giữa Home là khối bo góc `RoundedCornerShape(16.dp)` nền `surfaceVariant` chứa dấu `+`; icon Chat hỗ trợ badge tròn đỏ (`0xFFFF3B30`) khi có tin chưa đọc.

### 7.17 Shop — `feature/shop/ShopScreen.kt` (✅ 2026-11-15 — thay màn Gacha, SHOP_PLAN.md)
Tab footer → **không nút Back**. `TopAppBar` nền `surface` kiểu `SimpleTopBar` (tiêu đề "Shop" `titleLarge` Medium) + bên phải **ô số dư** pill nền `pill`: `ic_astrite` 18dp + số `accentGold` Bold 14sp + `AddCircle` màu `accent` (bấm = sang tab Astrite). Dưới là `TabRow` y hệt Appearance (nền `background`, chỉ báo `accent` cao 3dp, tab chọn Bold `accent`, tab khác `textSecondary`): **Astrite · Skins · Frames · Effects**, mở vào **Skins**. Tab vật phẩm = `LazyVerticalGrid` **2 cột**, padding 16, khoảng 12/16; mỗi ô = `CollectibleItem` (skin 9:16 · khung & hiệu ứng 1:1 — hình từ `imageUrl`, không có thì `SkinThumbnail`/`EffectDemoCell` vẽ từ APK; bấm ô hiệu ứng = chạy lại demo) + **nút pill cao 36dp** dưới ô: chưa mua = nền `accent` + `ic_astrite` + giá `onAccent` Bold (thiếu tiền thì mờ 0.6 nhưng VẪN bấm được) · đã mua skin/effect = `Apply` chữ `accent` trên nền `pill` · đang dùng = `In use` / khung = `Owned` chữ `textSecondary` trên `surfaceVariant`. **Không dùng trạng thái LOCKED** (mờ + 🔒) ở Shop. Tab Astrite = danh sách thẻ gói nạp (thẻ `surfaceVariant` bo `card`, số Astrite `accentGold`, giá VND pill `accent`) — chuyển nguyên từ popup nạp cũ. Dialog (AlertDialog nền `surface`): "Buy X?" (Buy/Cancel) · "Not enough Astrite" (Top up/Cancel) · "Apply now?" (Apply/Later) sau khi mua skin/effect; mua khung → toast. Đang chờ PayOS → dải "Waiting for payment confirmation..." ngay dưới TabRow.

---

## 8. Tính năng CHƯA có asset — bắt buộc HỎI USER trước khi thiết kế

Các tính năng sau **không có ảnh mẫu**; khi làm phải: (1) đề xuất mockup chữ dựa trên ngôn ngữ thiết kế ở trên, (2) hỏi user chốt từng chi tiết (bố cục, vị trí entry point, hành vi), (3) mới code:

- ~~Reaction emoji bay~~ → ✅ đã làm ở post detail (7.4, user chốt 2026-07-12: bay + highlight, không đếm; ⊕ = hàng emoji mở rộng)
- **Doodle / vẽ tay lên ảnh** trước khi đăng
- **Khung ảnh (frame)** — chọn khung sau khi chụp + hiển thị khung quanh moment + catalog khung thưởng
- ~~Co-op capture (chụp chung)~~ → ✅ đã làm theo spec chữ user chốt 2026-08-02: popup chọn bạn (dialog `#2C2C2C`, avatar viền vàng khi chọn) → `CoopCaptureScreen` nửa camera + nửa xám `#2C2C2C` spinner chờ; nút chụp 80dp viền vàng → nút Send tròn mũi tên (xoay -45° như màn đăng bài) + icon Again trái; ảnh ghép → luồng edit/đăng bài thường
- **Daily Quest** — màn quest, nộp ảnh, trạng thái hoàn thành
- ~~Chat nhóm~~ → ✅ màn chat nhóm + **sheet cài đặt nhóm** đã làm (7.15 — user gửi ảnh mẫu Messenger 2026-08-02: đổi tên/avatar, mời/xóa thành viên, mute, rời nhóm); tạo nhóm vẫn là dialog GroupAdd ở Messages (chưa có asset riêng)
- ~~Friend streak~~ → ❌ **ĐÃ BỎ HẲN (user chốt 2026-11-15)** — chỉ còn streak cá nhân; không hiện 🔥 cạnh bạn bè ở sheet/Messages/Chat
- ~~Màn `relationship`~~ → ✅ đã làm = sheet 7.12 + QR 7.12b (user chốt 2026-07-12: chỉ mã mời, QR, bottom sheet)
- **Thông báo (notifications) UI**

Gợi ý mặc định khi đề xuất: tái dùng component mục 7, màu mục 2, shape mục 4; gamification (streak/quest/badge) luôn dùng **vàng `SnapGold`**.

---

## 9. Khác biệt assets ↔ code cần nhớ

1. **Branding:** ảnh ghi "Nocket"/"Locket" (app tham khảo) → mọi chữ trong code dùng **"Snapget"**. Chữ "Lockets" ở profile (`UserProfileScreen`) cũng cần đổi → "Moments".
2. ~~Profile calendar + streak pill chạy mock~~ → ✅ **đã nối data thật** (2026-07-12): `personalStreak` + moments từ API; chữ "Lockets" đã đổi "Moments". (Friend streak đã bỏ 2026-11-15.)
3. `messages_screen_old.png` là bản CŨ — bỏ qua, chỉ theo `messages_screen.png`.
4. Code hiện có `DarkBrownTheme.kt` (nâu + Amber) **không khớp assets** — chuẩn là Gray scheme trong `Theme.kt`; đừng dùng DarkBrown cho màn mới. (Dấu vết nâu còn sót: pill `#404137`, Gold pill `#74512D` — chấp nhận, không lan thêm.)
5. **Nút chụp giữa bottom bar ĐÃ viền vàng** (`MainBottomBar` → `Circle`, 80dp/3dp/`Color.Yellow`). ~~Nút shutter trong `CameraPreview.kt`~~ → **đã XÓA hẳn** (2026-07-26, trùng nút — chụp/quay đều qua nút center bottom bar).
6. **Login input/nút bo 12dp trong code** (`cornerShape = 12.dp`) nhưng ảnh trông tròn hơn (~28dp) — ảnh là chuẩn; khi chỉnh LoginScreen cân nhắc tăng, nhưng đừng đổi lắt nhắt một mình nó.
7. Item settings bo **8dp** trong code, ảnh trông ~16dp — lệch nhỏ, chấp nhận được; nếu làm lại màn settings thì theo ảnh.
8. Màu nền pill trong ảnh trông `#2C2C2C` nhưng code thật là **`#404137`** (ô liu) — khi thêm pill mới dùng `#404137` cho đồng bộ với pill hiện có.

---

> **Checklist trước khi merge UI mới:** nền `GrayBackground`? bo góc theo mục 4? accent chỉ vàng (hoặc token chức năng mục 0.7 khi đã chốt)? overlay đen mờ 50–60%? chữ Bold trắng/phụ xám `#B0B0B0`? component tái dùng từ mục 7? nếu tính năng thuộc mục 8 → đã hỏi user chưa?
>
> **Checklist Playful v2 (mục 0):** mọi phần tử bấm được có press feedback + vùng chạm ≥ 48dp? haptic đúng bảng 0.4? animation dùng token 0.2 và tắt được khi reduced motion? ≤ 2 phần tử animate/màn? có Loading/Error+Retry/Empty+CTA? chữ ≥ 12sp, không làm mờ chữ bằng alpha? icon trang trí `contentDescription = null`?

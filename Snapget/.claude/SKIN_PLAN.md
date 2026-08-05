# SKIN_PLAN.md — Kế hoạch chức năng "Đổi giao diện" (Skin) + màn Appearance + Gacha

> **Trạng thái (cập nhật 2026-08-05): ✅ XONG P0–P5** — skin engine, token màu/shape, gỡ Light, màn Appearance 3 tab, engine hiệu ứng touch, 2 skin mới (Snow/Forest) đều đã chạy; app build + APK sạch.
> Còn lại duy nhất: **cắm file thiết kế thật** (icon vector, nút chụp, thumbnail 9:16, PNG hạt) — app hiện chạy bằng **bảng màu token + hình vẽ Canvas** nên không thiếu gì về chức năng, thay file vào là đẹp hơn. Quy cách file: mục 6.13 + `Sources/skin-assets/README.md`.
> Đọc kèm: `.claude/DESIGN.md` (ngôn ngữ thiết kế hiện tại) và `.claude/CLAUDE.md` (luật).
> **⚠️ Đọc cùng `.claude/GACHA_PLAN.md`** — skin và hiệu ứng touch **chính là vật phẩm SSR/SR của gacha**. Lộ trình gộp của cả hai tính năng nằm ở **GACHA_PLAN mục 9** (bảng đó là bản chính; mục 7 dưới đây chỉ là phần skin tách riêng).
> **Asset đã dựng sẵn placeholder** tại `Sources/skin-assets/` — xem `Sources/skin-assets/README.md` để biết cách thay file.
> Khi bắt tay thi công: cập nhật `GUIDE.md` sau mỗi phase + thêm mục mới vào `DESIGN.md`, chạy `codegraph init`.

---

## 0. Quyết định đã chốt với user

| Hạng mục | Chốt |
|---|---|
| Phạm vi skin | **Toàn app** (mọi màn) |
| Nguồn skin | **Đóng gói sẵn trong APK** (không tải từ server) |
| Định dạng asset | **Hỗn hợp**: icon = vector XML (từ SVG); nền/nút có texture = WebP @4x |
| Entry point | Settings → **màn Appearance riêng** (số tab xem dòng dưới) |
| Mở khóa | Qua **gacha** (cơ chế chi tiết user cấp sau) |
| Tab Skins | **2 cột, thumbnail dọc 9:16** |
| Tab Frames | **Chỉ xem** bộ sưu tập (không đụng luồng chọn khung lúc đăng bài) |
| Animation gacha | **Compose animation thuần** — KHÔNG thêm dependency (Lottie bị loại) |
| Dark/Light | **XOÁ HẲN giao diện Light** (chốt 2026-08-05). Không còn `ThemeMode`, không còn mục Theme trong Settings |
| Giao diện mặc định | Giao diện **đen hiện tại = `DefaultSkin`**, là **mục đầu tiên trong tab Skins** (luôn sở hữu, không cần gacha) |
| **Độ sâu thay đổi** | **Có tầng** (chốt 2026-08-05) — không phải màn nào cũng vẽ asset riêng. Xem mục 0.1 |
| **Đổi màu đồng bộ** | Mọi chỗ dùng cùng 1 vai trò màu phải đổi **cùng lúc**: skin xanh → **tất cả** chỗ vàng thành xanh. Đây chính là mục đích của hệ token (mục 3) |
| **Hiệu ứng touch** | **CÓ** (mới, chốt 2026-08-05) — chạm vào đâu trên màn hình thì hiện hiệu ứng ở đó. Là **vật phẩm sưu tầm ĐỘC LẬP** với skin: có tab riêng, mở khoá riêng qua gacha, chọn riêng. Xem mục 2.5 |
| Số tab màn Appearance | **3 tab**: `Frames` \| `Skins` \| `Effects` |
| 3 loại vật phẩm gacha | **Khung ảnh** (đã có full-stack) · **Skin** (mới) · **Hiệu ứng touch** (mới) |
| Nguồn hiệu ứng touch | **User tự cung cấp** — app chỉ dựng sẵn `None`. Hợp đồng đầu vào: **mục 2.5.6** |
| Hiệu ứng chạy khi nào | **Mọi thao tác chạm** (kể cả bấm nút); **TẮT** ở camera lúc đang quay GIF |
| **Kiểu ID** | `skinId` và `effectId` là **`Int`**: `0, 1, 2, …` (chốt 2026-08-05). `frameId` giữ nguyên `String` — hợp đồng server đã chạy, không đổi |
| **Quy mô bản đầu** | **2 skin mới** (id 1, 2 — cộng id 0 mặc định) + **5 hiệu ứng touch** (id 1–5 — cộng id 0 = None) |

### 0.2 Bảng ID (chốt tạm 2026-08-05 — asset placeholder đã dựng theo bảng này)

| skinId | Concept | Bảng màu | Thư mục asset | Bậc gacha |
|---|---|---|---|---|
| **0** | `Default` — giao diện đen hiện tại | đen · vàng | ❌ không cần asset | luôn sở hữu |
| **1** | **`Snow`** | đen · xanh lam đậm · trắng | `Sources/skin-assets/skin1_snow/` | **SSR** |
| **2** | **`Forest`** | xanh lá đậm · vàng be · trắng | `Sources/skin-assets/skin2_forest/` | **SSR** |

| effectId | Concept | File | Bậc gacha |
|---|---|---|---|
| **0** | `None` — không hiệu ứng | ❌ không cần asset | luôn sở hữu |
| **1** | `Snowfall` | `effect1_particle.png` | **SR** |
| **2** | `Leaf` | `effect2_particle.png` | **SR** |
| **3** | `Sparkle` | `effect3_particle.png` | **SR** |
| **4** | `Bubble` | `effect4_particle.png` | **SR** |
| **5** | `Ember` | `effect5_particle.png` | **SR** |

Bảng màu 11 token của Snow và Forest: xem `Sources/skin-assets/README.md` mục 1.2. Tham số 5 hiệu ứng: `Sources/skin-assets/effects/EFFECTS.md`.
Tên concept đổi thoải mái — asset đặt tên theo **số id** nên đổi tên không phải đổi file.

⚠️ ID là **số**, nhưng `displayName` hiện trên UI phải là **chữ tiếng Anh** (luật CLAUDE.md mục 8). Tên file asset dùng **số** cho khớp ID → sau này đổi tên concept không phải đổi tên file.

### 0.1 Độ sâu thay đổi theo từng màn

| Tầng | Đổi những gì | Áp cho |
|---|---|---|
| **Tier A — asset riêng** | Icon, nút, nền thanh bar, nền màn + toàn bộ token màu | **Bộ icon dùng chung** (bottom bar, top bar, icon hành động) · **nút** (nút chụp 80dp, nút chính) · **màn chính** (feed/post pager + grid) · **camera** · **chat + danh sách tin nhắn** |
| **Tier B — chỉ token màu** | Chỉ màu nền + màu chữ + màu accent (icon vẫn dùng bộ icon chung của Tier A) | **Profile** · **Settings** · **mọi popup/dialog/bottom sheet** · login · quest · friends/QR · coop · chính màn Appearance |

**Hệ quả tốt cho khối lượng việc:**
- Số asset phải vẽ giảm mạnh — mục 6 đã đánh dấu rõ asset nào **cần vẽ (A)** và asset nào **bỏ (B)**.
- Tier B **không cần refactor gì thêm** ngoài việc đổi màu hardcode sang token — vốn đã nằm trong P0–P2.
- Icon là bộ **dùng chung toàn app**: vẽ 1 lần ở Tier A thì Settings/Profile/popup tự ăn theo, không phải vẽ riêng.

---

## 1. Hiện trạng — audit số thật (đếm 2026-08-05, 154 file `.kt`)

| Thứ cần gom về token | Số lần xuất hiện |
|---|---|
| `Color.White` hardcode | **142** |
| `Color.Black` hardcode | **44** |
| `Color.Yellow` (accent) | **33** |
| `Color(0xFF404137)` (nền pill ô liu) | **28** |
| `0xFFFFD700` / `SnapGold` | **19** |
| `RoundedCornerShape(...)` rải rác | **110** |
| Lượt dùng `Icons.Filled/Outlined/AutoMirrored` | **78** (trong 38 file) |

**≈ 266 điểm màu hardcode + 78 điểm icon** → đây là toàn bộ khối lượng refactor. Đây là lý do phải làm **token trước, asset sau**.

### File "nặng" nhất, ưu tiên refactor theo thứ tự này

| # | File | Điểm hardcode |
|---|---|---|
| 1 | `core/designsystem/component/list/FriendListComponent.kt` | 28 |
| 2 | `feature/post/EditMediaScreen.kt` | 22 |
| 3 | `core/designsystem/preview/CameraPreview.kt` | 20 |
| 4 | `feature/quest/DailyQuestScreen.kt` | 19 |
| 5 | `feature/message/GroupSettingsSheet.kt` | 16 |
| 6 | `core/designsystem/component/topbar/MainTopBar.kt` | 16 |
| 7 | `feature/coop/CoopCaptureScreen.kt` | 14 |
| 8 | `core/designsystem/component/circle/CircleComponent.kt` | 14 |
| 9 | `feature/friends/QrScanScreen.kt` | 12 |
| 10 | `feature/profile/UserProfileScreen.kt` | 10 |

### Cái đã có sẵn, KHÔNG phải làm lại

- **Khung ảnh chạy full-stack rồi**: server module `frames` (`GET /frames` trả catalog + `isUnlocked`, `users.unlockedFrames[]`, 6 `unlockType`, admin CRUD/grant) → app có `FrameApi`/`FrameDto`, lưới 3 cột owned/locked ở `feature/quest/DailyQuestScreen.kt` (`FrameItem`), chọn khung lúc đăng ở `EditMediaScreen`, overlay ở feed/grid/detail.
- **Cơ chế persist + recompose toàn app**: `SettingsPreferences.themeMode` là `StateFlow` trên singleton, `MainViewModel`/`MainActivity` đọc → đổi là app vẽ lại ngay. `skinId` bê nguyên pattern này.

---

## 2. Kiến trúc skin engine

### 2.1 Cây file mới

```
core/designsystem/skin/
├── AppSkin.kt          — data class AppSkin + SkinColors/SkinIcons/SkinShapes/SkinImages
├── LocalAppSkin.kt     — staticCompositionLocalOf + ProvideAppSkin()
├── SkinIcon.kt         — composable helper: có asset riêng thì vẽ, không thì fallback Material icon
├── SkinRegistry.kt     — danh sách skin bundled + lookup theo id
└── skins/
    ├── DefaultSkin.kt  — CHÍNH XÁC giao diện đen hiện tại; luôn sở hữu, đứng đầu tab Skins
    └── <XxxSkin>.kt    — skin mới có asset riêng

core/designsystem/effect/          ← ĐỘC LẬP với skin (mục 2.5)
├── TouchEffect.kt                 — model 1 hiệu ứng + tham số vẽ
├── TouchEffectRegistry.kt         — danh sách hiệu ứng bundled
├── TouchEffectOverlay.kt          — lớp phủ bắt chạm + Canvas vẽ, bọc ngoài NavHost
└── renderers/                     — RippleRenderer, SparkleRenderer, ParticleRenderer…
```

### 2.2 Mô hình dữ liệu (phác thảo)

```kotlin
@Immutable
data class AppSkin(
    val id: Int,                    // 0 = Default, 1, 2, … (chốt 2026-08-05)
    val displayName: String,        // hiện ở tab Skins (tiếng Anh — luật CLAUDE.md)
    @DrawableRes val thumbnail: Int,// ảnh 9:16 trong tab Skins
    val colors: SkinColors,
    val icons: SkinIcons,
    val shapes: SkinShapes,
    val images: SkinImages,
    val fontFamily: FontFamily? = null,   // null = Roboto mặc định
)

@Immutable
data class SkinColors(
    val background: Color, val surface: Color, val surfaceVariant: Color,
    val onBackground: Color, val onSurface: Color, val onSurfaceVariant: Color,
    val accent: Color,          // thay 33 chỗ Color.Yellow
    val accentGold: Color,      // thay 19 chỗ #FFD700 / SnapGold (gamification)
    val pill: Color,            // thay 28 chỗ #404137
    val overlay: Color,         // thay Color.Black.copy(alpha = .5f) trên ảnh
    val textPrimary: Color,     // thay phần lớn 142 chỗ Color.White
    val error: Color,
)

/**
 * CHỈ 12 icon Nhóm 1 (mục 6.13.1) — đây là những icon user vẽ riêng cho skin.
 * Icon Nhóm 2/3 KHÔNG có ở đây: giữ Material icon, chỉ đổi `tint` theo SkinColors.
 * null = skin chưa vẽ icon đó -> tự fallback về Material icon như hiện tại.
 */
@Immutable
data class SkinIcons(
    @DrawableRes val camera: Int? = null,        // skin<N>_ic_camera
    @DrawableRes val send: Int? = null,          // skin<N>_ic_send
    @DrawableRes val gallery: Int? = null,       // skin<N>_ic_gallery
    @DrawableRes val flipCamera: Int? = null,    // skin<N>_ic_flip_camera
    @DrawableRes val close: Int? = null,         // skin<N>_ic_close
    @DrawableRes val captions: Int? = null,      // skin<N>_ic_captions
    @DrawableRes val grid: Int? = null,          // skin<N>_ic_grid
    @DrawableRes val more: Int? = null,          // skin<N>_ic_more
    @DrawableRes val chat: Int? = null,          // skin<N>_ic_chat
    @DrawableRes val chevronDown: Int? = null,   // skin<N>_ic_chevron_down
    @DrawableRes val chevronRight: Int? = null,  // skin<N>_ic_chevron_right
    @DrawableRes val back: Int? = null,          // skin<N>_ic_back
)

/** Tất cả đều tuỳ chọn trừ captureButton + thumbnail (mục 6.13.2). */
@Immutable
data class SkinImages(
    @DrawableRes val captureButton: Int? = null,          // skin<N>_btn_capture      — BẮT BUỘC
    @DrawableRes val captureButtonPressed: Int? = null,   // skin<N>_btn_capture_pressed
    @DrawableRes val captureButtonRecording: Int? = null, // skin<N>_btn_capture_recording
    @DrawableRes val bottomBarBackground: Int? = null,    // skin<N>_bg_bottombar.9
    @DrawableRes val pillBackground: Int? = null,         // skin<N>_bg_pill.9
    @DrawableRes val cardBackground: Int? = null,         // skin<N>_bg_card.9
    @DrawableRes val bubbleOutgoing: Int? = null,         // skin<N>_bg_bubble_out.9
    @DrawableRes val bubbleIncoming: Int? = null,         // skin<N>_bg_bubble_in.9
    @DrawableRes val screenBackground: Int? = null,       // skin<N>_bg_screen — null = colors.background phẳng
)

@Immutable
data class SkinShapes(
    val pill: Shape, val card: Shape, val image: Shape,
    val sheet: Shape, val input: Shape,
)
```

### 2.3 Cách bơm vào app

```kotlin
val LocalAppSkin = staticCompositionLocalOf { DefaultSkin }

@Composable
fun AppTheme(skin: AppSkin = DefaultSkin, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppSkin provides skin) {
        MaterialTheme(
            colorScheme = skin.colors.toColorScheme(),   // giữ M3 vẫn đúng
            typography  = skin.toTypography(),
            content     = content,
        )
    }
}
```

`AppTheme` giữ nguyên chữ ký cũ có default → **mọi `@Preview` hiện tại không phải sửa**.

### 2.4 Helper fallback — chìa khoá để làm dần

```kotlin
@Composable
fun SkinIcon(
    res: Int?,                    // LocalAppSkin.current.icons.xxx
    fallback: ImageVector,        // icon Material đang dùng
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalAppSkin.current.colors.textPrimary,
) { /* res != null -> painterResource, ngược lại -> Icon(imageVector) */ }
```

→ Skin nào chưa vẽ đủ icon vẫn chạy được, không vỡ màn nào.

---

## 2.5 Hiệu ứng touch (mới — vật phẩm ĐỘC LẬP với skin)

### 2.5.1 Nguyên tắc

Hiệu ứng touch **không nằm trong `AppSkin`**. Nó là loại vật phẩm thứ 3, có vòng đời riêng:

| | Skin | Hiệu ứng touch |
|---|---|---|
| Lưu lựa chọn | `SettingsPreferences.skinId` | `SettingsPreferences.touchEffectId` |
| Sở hữu (server) | `users.unlockedSkins[]` | `users.unlockedEffects[]` |
| Chọn ở đâu | tab **Skins** | tab **Effects** |
| Mặc định | **`0`** = `Default` (giao diện đen) | **`0`** = `None` (không hiệu ứng) — luôn sở hữu |

→ Người dùng **trộn tự do**: skin xanh + hiệu ứng lửa, skin mặc định + hiệu ứng tuyết…

### 2.5.2 Model

```kotlin
@Immutable
data class TouchEffect(
    val id: Int,                    // 0 = None, 1..5 (chốt 2026-08-05)
    val displayName: String,             // tiếng Anh
    val renderer: TouchEffectRenderer,   // cách vẽ (tab Effects demo trực tiếp, không cần thumbnail)
    val particleCount: Int = 8,
    val durationMs: Int = 600,
    val useSkinAccent: Boolean = true,   // true = ăn màu accent của skin đang dùng
    @DrawableRes val particleAsset: Int? = null, // null = vẽ vector thuần bằng Canvas
)
```

`useSkinAccent = true` → hiệu ứng tự đổi màu theo skin, khỏi phải vẽ mỗi hiệu ứng nhiều bản màu.

### 2.5.3 Cách hiện thực (Compose thuần, không thêm dependency)

Đặt `TouchEffectOverlay` **bọc ngoài `NavHost`** trong `MainActivity` → mọi màn đều có, viết 1 lần.

```kotlin
@Composable
fun TouchEffectOverlay(effect: TouchEffect, content: @Composable () -> Unit) {
    val emissions = remember { mutableStateListOf<Emission>() }
    Box(
        Modifier.fillMaxSize().pointerInput(effect.id) {
            awaitPointerEventScope {
                while (true) {
                    // PointerEventPass.Initial + KHÔNG consume -> chỉ "nghe lỏm",
                    // nút/scroll/pager bên dưới vẫn nhận đủ sự kiện như cũ
                    val down = awaitPointerEvent(PointerEventPass.Initial)
                    down.changes.firstOrNull { it.pressed && it.previousPressed.not() }
                        ?.let { emissions.add(Emission(it.position)) }
                }
            }
        },
    ) {
        content()
        Canvas(Modifier.fillMaxSize()) { /* vẽ emissions, không nhận touch */ }
    }
}
```

**3 điểm bắt buộc phải đúng, nếu sai là hỏng cả app:**

1. **KHÔNG được `consume()`** sự kiện chạm — nếu consume thì mọi nút, scroll, `VerticalPager` của feed và **nút chụp giữ-để-quay GIF** sẽ chết.
2. Dùng `PointerEventPass.Initial` để nghe trước, nhưng chỉ đọc toạ độ rồi thả cho tầng dưới xử lý.
3. `Canvas` vẽ đè phải **không có modifier nhận input** (không `clickable`, không `pointerInput`).

### 2.5.4 Hiệu năng & an toàn

- Giới hạn **tối đa ~8 hiệu ứng sống cùng lúc**, quá thì bỏ cái cũ nhất — tránh spam khi vuốt nhanh.
- `emissions` là `mutableStateListOf` chỉ `Canvas` đọc → **không recompose cây UI phía dưới**.
- Mỗi hiệu ứng tự huỷ sau `durationMs`.
- **Toggle "Touch effect" trong Settings** để tắt hẳn — cần cho máy yếu và cho người khó chịu với hiệu ứng động (`SettingsPreferences` đã có sẵn cơ chế toggle).
- **Chạy ở MỌI thao tác chạm** (chốt 2026-08-05) — kể cả khi bấm nút, cuộn, vuốt. Nhất quán, không phân biệt vùng trống hay không.
- **TẮT ở màn camera khi đang quay GIF** (chốt 2026-08-05) — đúng tinh thần `DESIGN.md` 7.5 "khi quay KHÔNG hiện gì thêm". Hiện thực:

```kotlin
// core/designsystem/effect/TouchEffectController.kt
class TouchEffectController { val suppressed = mutableStateOf(false) }
val LocalTouchEffectController = staticCompositionLocalOf { TouchEffectController() }

// CameraScreen — bật/tắt quanh phiên quay, tự nhả khi rời màn
val controller = LocalTouchEffectController.current
DisposableEffect(isRecording) {
    controller.suppressed.value = isRecording
    onDispose { controller.suppressed.value = false }
}
```

`TouchEffectOverlay` đọc `suppressed` → đang bật thì bỏ qua emission mới **và** xoá emission đang sống. `onDispose` là bắt buộc: rời màn giữa lúc quay mà không nhả thì hiệu ứng chết vĩnh viễn tới khi mở lại app.

### 2.5.5 Danh mục hiệu ứng

**effectId 0 = `None`** (không có gì) — hiệu ứng **bắt buộc phải có**, mặc định, luôn sở hữu, không quay gacha ra được. App tự dựng, không cần asset.

**effectId 1–5 do user tự cung cấp** (chốt 2026-08-05). Hợp đồng đầu vào ở mục 2.5.6.
Tham khảo thứ vẽ được 100% bằng Canvas nếu bạn chọn Dạng 3 (không cần gửi file): vòng tròn lan · tia sáng bắn ra · bong bóng nổi lên · sóng lan toả.

### 2.5.6 HỢP ĐỒNG ĐẦU VÀO — user cần chuẩn bị gì cho mỗi hiệu ứng

Mỗi hiệu ứng = **1 file ảnh (hoặc không có file) + 1 phiếu tham số**. Nhận 1 trong 3 dạng:

#### Dạng 1 — Ảnh hạt tĩnh ⭐ khuyến nghị

| Hạng mục | Yêu cầu |
|---|---|
| Số file | **1** |
| Định dạng | **PNG-32 có alpha** (nền trong suốt hoàn toàn) |
| Kích thước file ảnh | **192×192px** (= 48dp @4x) |
| Bố cục trong canvas | Hình nằm **giữa**, chừa lề trống **≥10%** mỗi cạnh (~20px) để lúc xoay/phóng không bị cắt |
| Màu | **Trắng thuần `#FFFFFF`**, dùng **alpha** để tạo sắc độ → app tint theo `accent` của skin. Muốn màu cố định không đổi theo skin thì gửi bản màu và ghi rõ ở phiếu |
| Cấm | Bóng đổ / viền màu nướng sẵn (sẽ hỏng khi tint), nền trắng đục, viền đen |
| Dung lượng | ≤ 100KB |
| Tên file | `effect<N>_particle.png` với `N` = effectId 1–5 — ví dụ `effect3_particle.png` |

#### Dạng 2 — Sprite sheet (hạt có hoạt ảnh riêng)

| Hạng mục | Yêu cầu |
|---|---|
| Số file | **1** ảnh ngang chứa toàn bộ frame |
| Mỗi frame | **192×192px**, xếp **1 hàng ngang**, đều nhau, không có khoảng đệm giữa các frame |
| Số frame | **4–24** (ghi rõ ở phiếu) |
| Tổng kích thước | `N × 192` rộng × `192` cao — ví dụ 12 frame → 2304×192px |
| Màu / cấm / dung lượng | Như Dạng 1; dung lượng ≤ 400KB |
| Tên file | `effect<N>_sheet.png` với `N` = effectId 1–5 |

#### Dạng 3 — Không có file, chỉ mô tả hình học

Hiệu ứng chỉ gồm hình cơ bản (tròn, vòng cung, tia, sóng, đường kẻ) → **không cần gửi ảnh**, chỉ cần mô tả bằng chữ + phiếu tham số, tôi vẽ bằng Canvas.

#### Phiếu tham số — điền cho MỖI hiệu ứng

```
effectId:            3                     (SỐ, từ 1 đến 5)
displayName:         Snow                  (TIẾNG ANH — luật CLAUDE.md mục 8)
Dạng:                1 / 2 / 3
File kèm theo:       effect3_particle.png
Số frame + fps:      (chỉ Dạng 2) 12 frame @ 24fps
Số hạt mỗi lần chạm: 8                     (khuyến nghị 4–12, tối đa 20)
Cỡ hạt hiển thị:     24dp                  (dải hợp lý 16–48dp)
Thời lượng:          900ms                 (dải 300–1500ms)
Hướng bay:           toả đều 360° | rơi xuống | bay lên | bắn ra rồi rơi | đứng yên
Quãng đường:         60dp
Xoay:                có, 180°/giây          | không
Scale theo thời gian: 1.0 → 0.4
Fade:                mờ dần từ mốc 70% thời lượng
Ăn màu skin:         có (ảnh vẽ trắng)      | không (giữ màu gốc)
Bậc hiếm gacha:      thường | hiếm | rất hiếm
```

**Không cần chuẩn bị**: thumbnail cho tab Effects (ô demo chạy hiệu ứng thật, không dùng ảnh tĩnh) · nhiều bản màu (app tint) · nhiều mật độ màn hình (1 file @4x là đủ).

---

## 3. Bản đồ token — hardcode nào đổi thành gì

> **Đây là nơi hiện thực yêu cầu "đổi màu đồng bộ".** Mỗi màu được gom về **một** token theo **vai trò** (không theo giá trị). Skin xanh chỉ cần khai `accent = #00E5FF` là **cả 33 chỗ vàng** trong app đổi cùng lúc — không sót chỗ nào, không phải sửa từng màn.
> Ràng buộc kèm theo: sau P2, **cấm viết màu hardcode mới** trong `feature/**`; màu mới phải thêm token vào `SkinColors` trước.

| Hardcode hiện tại | Token mới | Số điểm |
|---|---|---|
| `Color.Yellow` | `skin.colors.accent` | 33 |
| `Color(0xFFFFD700)` / `SnapGold` | `skin.colors.accentGold` | 19 |
| `Color(0xFF404137)` | `skin.colors.pill` | 28 |
| `Color.White` (chữ/icon chính) | `skin.colors.textPrimary` | ~142 (trừ chỗ cố ý trắng trên ảnh) |
| `Color.Black.copy(alpha = .5f/.6f)` | `skin.colors.overlay` | ~20 |
| `Color(0xFFB8B8B8)` (viền avatar) | `skin.colors.onSurfaceVariant` | rải rác |
| `RoundedCornerShape(20.dp)` ảnh | `skin.shapes.image` | ~30 |
| `RoundedCornerShape(24.dp)` sheet/pill message | `skin.shapes.sheet` | ~20 |
| `RoundedCornerShape(50)` | `skin.shapes.pill` | ~25 |

⚠️ **Không đổi mù**: một số `Color.White` là chữ đè lên ảnh người dùng (caption, overlay camera) — phải trắng ở **mọi** skin. Lúc refactor phải phân biệt "trắng vì theme" và "trắng vì nằm trên ảnh".

**Vì sao tách `accent` và `accentGold` thay vì gộp 1**: trong `DefaultSkin` chúng là 2 vai trò khác nhau — `accent` (`#FFFF00`) cho chọn/chụp, `accentGold` (`#FFD700`) cho gamification (streak, badge, pill Gold). Gộp lại sẽ mất sắc thái ở màn Profile/Quest. Skin mới chỉ cần khai 2 giá trị **cùng một hệ màu** (ví dụ skin xanh: `accent = #00E5FF`, `accentGold = #00B8D4`) là vẫn "đồng bộ" đúng như yêu cầu.

---

## 4. Màn Appearance (mới)

### 4.1 Điều hướng

- `Screen.Appearance : Screen("appearance")` trong `navigation/Navigation.kt`.
- Entry: item **"Appearance"** trong `SettingScreen` (thay chỗ mục Theme cũ).
- Có top bar back + title "Appearance" (dùng `SimpleTopBar` sẵn có).
- Thêm route vào `hideBottomBarRoutes`.

### 4.2 Bố cục

```
┌─ ← Appearance ──────────────────┐
│ [Frames] [Skins] [Effects]      │  ← TabRow 3 tab, cao 48dp, indicator 3dp
├─────────────────────────────────┤
│  (nội dung tab)                 │
└─────────────────────────────────┘
```

**Tab Frames** — lưới 3 cột, ô 1:1 bo 16dp, ảnh khung padding 6dp, chưa sở hữu thì `alpha 0.35` + icon 🔒, badge "NEW" góc trên phải, tên 12sp dưới ô, nhãn `🔥 Nd` nếu là khung mốc streak.
→ Tách `FrameItem` từ `feature/quest/DailyQuestScreen.kt:315` sang `core/designsystem/component/frame/FrameItem.kt`, cả 2 màn dùng chung. Data: `GET /frames` (đã có).

**Tab Skins** — lưới 2 cột, thumbnail 9:16 bo 16dp; skin đang dùng có viền `accent` 3dp + nhãn "In use"; skin chưa sở hữu `alpha 0.35` + 🔒 + nhãn điều kiện. Bấm skin đã sở hữu = áp dụng ngay (app đổi tức thì, không restart).
**Ô đầu tiên luôn là "Default"** — giao diện đen hiện tại, không bao giờ khoá, không quay gacha ra được (đây là chỗ thay thế mục Theme cũ trong Settings).

**Tab Effects** (mới) — lưới **2 cột, ô 1:1** bo 16dp. Mỗi ô là **khu vực demo sống**: chạm vào chính ô đó thì hiệu ứng chạy ngay trong ô để xem thử **trước khi** áp dụng — không cần thumbnail tĩnh, cũng không cần thoát ra thử.
- Ô đầu tiên luôn là **"None"** (không hiệu ứng) — mặc định, luôn sở hữu.
- Hiệu ứng đang dùng: viền `accent` 3dp + nhãn "In use". Chưa sở hữu: `alpha 0.35` + 🔒, **vẫn chạm thử được** (cho người dùng thấy đáng quay gacha hay không) nhưng nút Apply bị khoá.
- Hiệu ứng ăn màu `accent` của skin đang dùng (`useSkinAccent`), nên ô demo tự khớp màu skin hiện tại.

**Cả 3 tab dùng chung 1 component ô**: `CollectibleItem` (ảnh/preview + tên + trạng thái owned/locked/in-use) — khác nhau chỉ ở tỉ lệ ô (1:1 · 9:16 · 1:1) và số cột (3 · 2 · 2).

### 4.3 Code phía app

| File | Việc |
|---|---|
| `feature/appearance/AppearanceScreen.kt` | 3 tab + lưới |
| `feature/appearance/AppearanceViewModel.kt` | state: `frames`, `skins`, `effects`, `currentSkinId`, `currentEffectId`, `ownedSkinIds`, `ownedEffectIds` |
| `feature/appearance/data/AppearanceRepository.kt` | `FrameApi.list()` + `UserApi` (`unlockedSkins`, `unlockedEffects`) |
| `core/designsystem/component/collectible/CollectibleItem.kt` | ô dùng chung cho cả 3 tab |
| `core/data/SettingsPreferences.kt` | thêm `skinId` **và** `touchEffectId` + `StateFlow` (y hệt `themeMode`) |
| `MainActivity` / `core/ui/MainViewModel.kt` | đọc cả 2 id; bọc `NavHost` bằng `TouchEffectOverlay` |

### 4.4 Gỡ bỏ giao diện Light (chốt 2026-08-05 — xoá hẳn, không chuyển thành skin)

App quay về **dark-first thuần** đúng như `DESIGN.md` mục 1 mô tả. Việc phải làm ở P3, theo thứ tự:

| File | Việc |
|---|---|
| `core/model/ThemeMode.kt` | **Xoá file** |
| `core/designsystem/theme/Color.kt` | Xoá khối `LightPrimary … LightOnError` (17 hằng số, dòng 26–44) |
| `core/designsystem/theme/Theme.kt` | Xoá `GrayLightColorScheme` + tham số `themeMode`; `AppTheme` nhận `skin: AppSkin` thay thế |
| `core/data/SettingsPreferences.kt` | Bỏ `themeMode`/`setThemeMode`/`KEY_THEME_MODE`; thay bằng `skinId`/`setSkinId`/`KEY_SKIN_ID` (giữ nguyên kiểu `StateFlow`) |
| `core/ui/MainViewModel.kt` | Đọc `skinId` thay `themeMode` |
| `feature/settings/SettingScreen.kt` | Bỏ item Theme → thay bằng item **"Appearance"** điều hướng sang màn mới |
| `feature/settings/SettingDialogs.kt` | Xoá dialog chọn Theme |
| `feature/settings/SettingsViewModel.kt` | Bỏ state/handler của theme |
| `core/designsystem/preview/PostScreenPreviews.kt` | Bỏ tham số `ThemeMode` trong preview |
| `core/designsystem/theme/DarkBrownTheme.kt` | Dọn luôn — `DESIGN.md` mục 9.4 đã ghi là theme chết, không dùng |

Hệ quả chấp nhận: **mất chế độ SYSTEM** (theo cài đặt máy). Người dùng chỉ chọn skin.
Prefs cũ (`theme_mode` trong `snapget_settings`) để lại cũng vô hại — không cần migration, lần đọc đầu tiên không thấy `skin_id` thì mặc định **`0`** (và `touch_effect_id` mặc định **`0`**).

---

## 5. Phần server cho gacha (BẮT BUỘC làm trước phần app — luật CLAUDE.md mục 4)

> 📌 **Mục này đã được thay thế bằng `.claude/GACHA_PLAN.md`** (lập 2026-08-05, dựa trên file `Snapget - Hệ thống gacha và tiền tệ.xlsx`).
> Ở đó có đầy đủ: tiền tệ **Astrite**, gói nạp, **PayOS sandbox**, 4 bậc N/R/SR/SSR, pity 10/50/100, quy tắc hoàn, mô hình dữ liệu, thuật toán quay, endpoint, trang admin, và lộ trình gộp cả 2 tính năng.
> **Chỗ nào 2 file lệch nhau thì GACHA_PLAN đúng.** Phần bên dưới giữ lại làm tham chiếu lịch sử — lưu ý bản cũ dùng khái niệm "vé quay" (`gachaTickets`), bản chốt dùng **Astrite**.

Server hiện **chưa có gì về gacha**: `UNLOCK_TYPES` chỉ có `QUEST_RANDOM / STREAK_MILESTONE / POST_COUNT / FRIEND_COUNT / COOP_FIRST / DEFAULT`, không có currency, không có endpoint roll.

| Việc | Chi tiết |
|---|---|
| `users.unlockedSkins[]` | mảng `skinId`, y hệt `unlockedFrames[]` (mục 6.1 CLAUDE.md server) |
| `users.unlockedEffects[]` | mảng `effectId` — hiệu ứng touch sở hữu |
| `users.gachaTickets` | số vé quay (kiểu int) — **nguồn vé cần user chốt** |
| `UNLOCK_TYPES` thêm `'GACHA'` | khung chỉ ra từ gacha |
| `GET /gacha/state` | trả `{ tickets, pool: [...] }` |
| `POST /gacha/roll` | transaction: trừ vé → random theo tỉ lệ → ghi `unlockedFrames` / `unlockedSkins` / `unlockedEffects` tuỳ loại trúng → trả `{ itemType: 'FRAME'\|'SKIN'\|'EFFECT', itemId, isDuplicate }` |
| `GET /users/me` | thêm `unlockedSkins` + `unlockedEffects` (thêm field mới = an toàn, Gson bỏ qua field lạ) |
| Nguồn vé | hook vào `quests` (xong 2/2 quest) và/hoặc mốc streak — **chờ user chốt** |
| Audit | ghi log roll để demo DATN |
| Test | unit test cho tỉ lệ + trừ vé + trùng lặp |

**Skin và hiệu ứng đều nằm trong APK, server chỉ giữ quyền sở hữu (id dạng số).** Server không cần biết chúng trông thế nào → không phải upload asset, không phải làm CDN. (Khác với **khung ảnh** — khung là `imageUrl` thật trên Cloudinary do admin quản lý, đã chạy sẵn.)

Ràng buộc: app phải chịu được id lạ từ server (vật phẩm của bản app mới hơn) → `SkinRegistry.find(id) ?: DefaultSkin` (id 0) và `TouchEffectRegistry.find(id) ?: NoneEffect` (id 0).

Kiểu dữ liệu: `unlockedSkins` và `unlockedEffects` là **mảng số** (`number[]` ở TS, `List<Int>` ở Kotlin); `unlockedFrames` giữ nguyên `string[]`.

---

## 6. Bảng kích thước asset (bàn giao cho khâu thiết kế)

> ⚠️ **Mục 6.1–6.12 là BẢNG TRA KÍCH THƯỚC** dùng lúc refactor code — **không phải danh sách bàn giao**.
> **Danh sách bàn giao chính thức (bạn vẽ đúng những file này, không hơn) là mục 6.13.** Chỗ nào 2 mục lệch nhau thì **6.13 đúng**.
>
> **1dp = 1px @mdpi.** Cột px dưới đây là **@4x (xxxhdpi)**. Icon đơn sắc → xuất **SVG** rồi convert sang vector XML (không bao giờ vỡ nét). Có texture/gradient → **WebP @4x**.
> Quy ước đặt tên file: `skin<N>_<loại>_<tên>` với `N` = skinId (1, 2) — loại ∈ `ic` (icon vector) · `bg` (nền) · `btn` (nút) · `thumb` (ảnh đại diện skin).
> Ví dụ: `skin1_ic_camera.svg`, `skin1_btn_capture.webp`, `skin1_thumb.webp`.

### 6.0 Cần vẽ những mục nào — sau khi giảm độ sâu (mục 0.1)

| Mục | Tầng | Mỗi skin có phải vẽ không? |
|---|---|---|
| 6.1 Branding & hệ thống | — | ❌ **BỎ QUA** (user chốt) — launcher/splash dùng chung cả app, không đổi theo skin (lý do: mục 8.1–8.2) |
| 6.2 Bottom bar | **A** | ✅ **Có** — ưu tiên số 1 (nút chụp + icon Nhóm 1) |
| 6.3 Top bar | **A** | ✅ **Có** — 2 chevron thuộc Nhóm 1; nền pill là tuỳ chọn |
| 6.4 Feed & post | **A** | ✅ **Có** — icon lưới/⋯ thuộc Nhóm 1; các nền là tuỳ chọn |
| 6.5 Camera | **A** | ⚪ Chỉ nền/khung là tuỳ chọn — icon flash thuộc Nhóm 2 (**không vẽ**) |
| 6.6 Message & chat | **A** | ⚪ Chỉ 9-patch bubble + input, đều **tuỳ chọn** |
| 6.7 Profile | **B** | ❌ **Không** — chỉ đổi màu nền/chữ qua token |
| 6.8 Settings & login | **B** | ❌ **Không** — chỉ đổi màu nền/chữ qua token |
| 6.9 Nền màn hình | **A** | ⚪ Tuỳ chọn — chỉ cho các màn Tier A |
| 6.10 Màn Appearance | **B** | ⚪ Chỉ cần **thumbnail 9:16 của chính skin đó** |
| 6.11 Gacha | — | ❌ 1 bộ dùng chung, không theo skin |
| 6.12 Hiệu ứng touch | — | ❌ Không thuộc skin — vật phẩm riêng, **user cung cấp cả 5** (mục 2.5.6) |
| **Khung ảnh** | — | ❌ Không thuộc skin — vật phẩm gacha riêng, admin upload lên Cloudinary như hiện tại |

**Tóm lại mỗi skin mới BẮT BUỘC chỉ có 14 file: 12 icon vector (Nhóm 1) + `btn_capture.webp` + `thumb.webp`** — cộng 11 mã màu. Mọi thứ khác là tuỳ chọn hoặc tự đổi màu theo token.

### 6.1 Branding & hệ thống — **BỎ QUA** (user chốt 2026-08-05)

Icon launcher, splash, icon notification, widget preview **giữ nguyên 1 bộ dùng chung cho cả app**, không đổi theo skin. Lý do kỹ thuật ở mục 8.1–8.2 (Android không đổi được launcher/splash lúc chạy nếu không dùng `activity-alias`, mà cách đó gây nhấp nháy icon và mất shortcut).


### 6.2 Bottom bar — nhận diện mạnh nhất

| Asset | Kích thước thật | Xuất @4x |
|---|---|---|
| **Nút chụp / Send (center)** | **80×80dp**, viền 3dp | 320×320px |
| ↳ vùng icon trong | Camera gap 7dp → **66dp** · Send gap 20dp → **40dp** (xoay −45°) | 264 / 160px |
| Nút center các bar khác | 60×60dp | 240×240px |
| Nút chụp — 3 trạng thái | 80dp × 3 (idle / pressed / đang quay GIF) | 320×320px mỗi bản |
| Icon 2 bên bottom bar | **40×40dp** | 160×160px |
| ↳ icon cần vẽ (Nhóm 1) | gallery · flip camera · ✕ · send · captions · grid · ⋯ | vector |
| ↳ icon KHÔNG vẽ (Nhóm 2, chỉ tint) | home · profile · settings · message · share | — |
| Nền thanh bar | cao **80dp**, full width | cao 320px, rộng ≥1440px, 9-patch |

### 6.3 Top bar

| Asset | Kích thước | Xuất @4x |
|---|---|---|
| Khung avatar (avatar 40dp + viền 2dp) | vẽ khung ngoài 48dp | 192×192px |
| Nền pill "Everyone" | cao **40dp**, bo 50%, padding trong h12/v6 | cao 160px, 9-patch |
| Chevron ⌄ | 18×18dp | 72×72px |
| Nút tròn phải (chat / 🏆) | **40dp**, icon trong **24dp** | 160 / 96px |
| Badge số chưa đọc | 20×20dp (số >9: rộng ≥24, cao 20dp) | 80×80px |
| Nền panel dropdown bạn bè | rộng **250dp**, bo 24dp, max cao 450dp | rộng 1000px, 9-patch |
| Chevron `>` | 16×16dp | 64×64px |

### 6.4 Feed & post

| Asset | Kích thước | Xuất |
|---|---|---|
| Overlay/khung ô lưới | **1:1**, bo 20dp, gap 3dp, 3 cột (cạnh ≈118dp @màn 360dp) | 1080×1080px |
| Nhãn "GIF" | cao ~20dp, bo 8dp | cao 80px |
| Khung ảnh post detail | **1:1**, bo 20dp | 1080×1080px |
| Nền caption đè ảnh | bo 24dp, cao ~40dp | 9-patch |
| Nền thanh "Send message…" | bo **24dp**, cao ~44–48dp, lề ngoài 15dp | 9-patch, cao 192px |
| Nút ⊕ mở emoji | 40×40dp | 160×160px |
| Nút emoji nhanh | 32×32dp | 128×128px |
| Chấm PageIndicator (2 state) | 8×8dp, cách nhau 6dp | 32×32px |

### 6.5 Camera

| Asset | Kích thước | Xuất @4x |
|---|---|---|
| Khung viền preview | **1:1**, bo **50dp** | 1080×1080px, chừa vùng an toàn |
| Nút flash / pill zoom | 48dp (icon 24dp) và 40dp (icon 20dp) | 192 / 160px |
| Khung ngắm QR | **260×260dp**, viền 3dp, bo 24dp | 1040×1040px |

### 6.6 Message & chat

| Asset | Kích thước | Xuất |
|---|---|---|
| Bubble chat | cao tối thiểu ~44dp, bo động 20/16/**6dp** góc đuôi | **bắt buộc 9-patch**, 2 file (gửi/nhận) |
| Nền input chat | bo 24dp, cao ~48dp | 9-patch |


### 6.7 Profile & gamification — **Tier B: KHÔNG vẽ asset theo skin**

Profile chỉ đổi **màu nền + màu chữ + accent** qua token. Các kích thước dưới đây giữ lại để tham chiếu khi refactor màu, **không phải danh sách cần vẽ**:
avatar **96dp** (vòng viền 112dp) · pill "Gold" cao 40dp bo 10dp · ô ngày calendar 1:1 bo 12dp, dot 4dp · card calendar bo 20dp · stat pill cao ~30dp bo 50% · badge streak 24–32dp.


### 6.8 Settings & login — **Tier B: KHÔNG vẽ asset theo skin**

Chỉ đổi màu nền/chữ. Kích thước giữ để tham chiếu lúc refactor:

| Asset | Kích thước | Xuất @4x |
|---|---|---|
| Icon tròn item settings | **40dp** (icon trong 20dp) | 160 / 80px |
| Nền card item settings | bo **8dp** | 9-patch |
| Logo login | vòng tròn **120dp**, ảnh trong **80dp** | 480 / 320px |
| Nút chính (Sign In / Google) | cao **56dp**, bo 12dp | 9-patch, cao 224px |
| Logo Google / FB / Insta | **20×20dp** | 80×80px (đã là PNG trong `res/drawable`) |

### 6.9 Nền & font

| Asset | Kích thước |
|---|---|
| Ảnh nền toàn màn | 1080×2340px (9:19.5) — hoặc pattern tile 256×256px |
| Nền bottom sheet / dialog | 9-patch, bo 20–24dp |
| Font riêng | `.ttf`/`.otf` vào `res/font/` (hiện chỉ Roboto: 16sp / 22sp / 11sp) |

### 6.10 Màn Appearance (3 tab)

| Asset | Kích thước | Xuất @4x | Ai vẽ |
|---|---|---|---|
| **Thumbnail skin** | **9:16**, bo 16dp, 2 cột | **540×960px** | mỗi skin 1 ảnh |
| Ô khung (tab Frames) | **1:1**, bo 16dp, 3 cột, ảnh padding 6dp | — | dùng ảnh khung sẵn có |
| Ô demo hiệu ứng (tab Effects) | **1:1**, bo 16dp, 2 cột | — | không cần ảnh, chạm là chạy hiệu ứng thật |
| Viền "In use" | bo 16dp, viền 3dp | vẽ bằng code (`accent`) | ❌ không cần file |
| Icon 🔒 khoá | 24×24dp | Material icon + tint | ❌ không cần file |
| Badge "NEW" | cao 20dp, bo 50% | shape + màu `accentGold` | ❌ không cần file |
| Nút Apply / "In use" | pill cao 40dp | shape + màu token | ❌ không cần file |
| Tab indicator | 3 tab, cao 48dp, gạch 3dp | màu `accent` | ❌ không cần file |

### 6.11 Gacha

| Asset | Kích thước | Xuất @4x |
|---|---|---|
| Hộp/túi quay | **200×200dp** | 800×800px |
| Icon vé/xu | 24dp + 48dp | 96 / 192px |
| Viền rarity (3–4 bậc) | **1:1** quanh item | 1080×1080px |
| Tia sáng / nền hiệu ứng | **1:1** | 1080×1080px |
| Nút "Roll" | pill cao 56dp | 9-patch |

### 6.12 Hiệu ứng touch — **user tự cung cấp**

Đặc tả đầy đủ ở **mục 2.5.6** (hợp đồng đầu vào + phiếu tham số). Tóm tắt kích thước:

| Dạng | File | Kích thước | Dung lượng |
|---|---|---|---|
| 1 — Ảnh hạt tĩnh ⭐ | 1 × PNG-32 alpha | **192×192px** (48dp @4x), hình nằm giữa, lề ≥10% | ≤100KB |
| 2 — Sprite sheet | 1 × PNG-32 alpha, N frame 1 hàng ngang | mỗi frame **192×192px**, tổng `N×192 × 192`, N = 4–24 | ≤400KB |
| 3 — Hình học | không có file | — | — |

Vẽ **trắng thuần `#FFFFFF` + alpha** để app tint theo `accent` của skin đang dùng → không phải làm nhiều bản màu, không phải làm nhiều mật độ màn hình.

---

## 6.13 MANIFEST BÀN GIAO — tên file chính xác (2 skin + 5 hiệu ứng)

> **Đây là danh sách chốt để bạn đặt tên asset.** Quy tắc tên resource Android: **chỉ chữ thường `a–z`, số, gạch dưới `_`; phải bắt đầu bằng chữ cái.** Sai tên là build fail.
> Thay `N` bằng `1` hoặc `2` theo skinId. Ví dụ dưới đây dùng skin 1.

### 6.13.1 Icon — vector (gửi `.svg`, tôi convert sang XML)

**Quy cách chung cho MỌI icon:**

| Hạng mục | Yêu cầu |
|---|---|
| Canvas | **24×24** đơn vị (viewport 24×24) |
| Vùng an toàn | Nét nằm trong **20×20** giữa, chừa lề 2 mỗi cạnh |
| Màu | **Trắng thuần `#FFFFFF`** — app tự tint theo token màu. KHÔNG dùng gradient, KHÔNG dùng nhiều màu |
| Nét | Độ dày ≥ **1.5** đơn vị (mỏng hơn sẽ mờ ở màn hình nhỏ) |
| Định dạng | `.svg` — đã **outline stroke thành path**, không còn text/khối chưa flatten |
| Cấm | Bóng đổ, hiệu ứng blur, ảnh nhúng bitmap |

**Nhóm 1 — BẮT BUỘC (12 icon).** Đây là icon to, luôn nhìn thấy; thiếu là skin trông "nửa vời":

| # | Tên file | Icon hiện tại | Dùng ở đâu | Cỡ hiển thị |
|---|---|---|---|---|
| 1 | `skin1_ic_camera.svg` | `CameraAlt` | Nút chụp giữa bottom bar | 66dp |
| 2 | `skin1_ic_send.svg` | `Send` (máy bay giấy) | Nút gửi bài (xoay −45°) + gửi tin nhắn | 40dp / 24dp |
| 3 | `skin1_ic_gallery.svg` | `PhotoLibrary` | Bottom bar màn camera (trái) | 40dp |
| 4 | `skin1_ic_flip_camera.svg` | `Cached` | Bottom bar màn camera (phải) | 40dp |
| 5 | `skin1_ic_close.svg` | `Close` | Huỷ ở màn đăng bài | 40dp |
| 6 | `skin1_ic_captions.svg` | `MotionPhotosAuto` | Mở danh sách caption | 40dp |
| 7 | `skin1_ic_grid.svg` | `GridView` | Về lưới ở feed | 40dp |
| 8 | `skin1_ic_more.svg` | `MoreHoriz` (⋯) | Menu bài đăng | 40dp |
| 9 | `skin1_ic_chat.svg` | `ChatBubbleOutline` | Nút tin nhắn góc phải top bar | 24dp |
| 10 | `skin1_ic_chevron_down.svg` | `ExpandMore` | Pill "Everyone ⌄" | 18dp |
| 11 | `skin1_ic_chevron_right.svg` | `ArrowForwardIos` | Mũi tên cuối các hàng danh sách | 16dp |
| 12 | `skin1_ic_back.svg` | `ArrowBack` | Nút back mọi top bar phụ | 24dp |

**Nhóm 2 — KHÔNG VẼ (9 icon).** Giữ Material icon, chỉ đổi màu bằng code. Bảng dưới chỉ để bạn biết chúng nằm ở đâu — **và để sẵn tên file phòng khi sau này bạn muốn vẽ thêm**:

| # | Tên file (nếu sau này vẽ) | Icon hiện tại | Dùng ở đâu | Cỡ |
|---|---|---|---|---|
| 13 | `skin1_ic_message.svg` | `Message` | Bottom bar (biến thể profile) | 40dp |
| 14 | `skin1_ic_home.svg` | `Home` / `ViewCozy` | Bottom bar về feed | 40dp |
| 15 | `skin1_ic_profile.svg` | `Person` | Bottom bar → profile | 40dp |
| 16 | `skin1_ic_settings.svg` | `Settings` | Bottom bar → settings | 40dp |
| 17 | `skin1_ic_add_circle.svg` | `AddCircle` (⊕) | Xoè emoji ở thanh message | 40dp |
| 18 | `skin1_ic_emoji.svg` | `EmojiEmotions` | Bàn phím emoji trong chat | 24dp |
| 19 | `skin1_ic_more_vert.svg` | `MoreVert` (⋮) | Menu header chat | 24dp |
| 20 | `skin1_ic_flash_on.svg` | `FlashOn` | Camera — flash bật | 24dp |
| 21 | `skin1_ic_flash_off.svg` | `FlashOff` | Camera — flash tắt | 24dp |

> ✅ **CHỐT (user, 2026-08-05): Nhóm 2 KHÔNG VẼ.** Giữ nguyên icon Material, **chỉ đổi màu bằng code** sao cho không trùng màu nền. Chỉ khi nào code không đổi được màu mới tính đến chuyện vẽ lại.
>
> **Kỹ thuật — tin tốt: đổi màu bằng code luôn làm được với 100% icon Nhóm 2.** Material icon trong Compose là vector đơn sắc, `Icon(imageVector = …, tint = …)` tô lại toàn bộ. Skin chỉ cần khai token màu là icon tự đổi theo:
>
> | Icon Nhóm 2 nằm ở đâu | Tint lấy từ token |
> |---|---|
> | Bottom bar, top bar, header chat | `colors.textPrimary` |
> | Icon phụ / trạng thái tắt (flash off) | `colors.onSurfaceVariant` |
> | Icon đang active / được chọn | `colors.accent` |
>
> → Chống trùng màu nền là **trách nhiệm của bảng màu** (mục 6.13.3), không phải của file ảnh. Điều kiện cần: `textPrimary` và `onSurfaceVariant` phải tương phản đủ với `background`/`surface`.
>
> ⚠️ Trường hợp DUY NHẤT phải vẽ lại: bạn muốn icon **đổi hình dáng** theo skin (ví dụ skin pixel-art cần icon vuông vức) — lúc đó gửi thêm SVG theo đúng tên file ở bảng trên, app tự ưu tiên file bạn gửi.
>
> **Nhóm 3 (icon ở màn Tier B: quest, group, share, reply, image, lock…) — ĐÃ BỎ HẲN**, xử lý y như Nhóm 2: giữ Material icon + tint theo token.


### 6.13.2 Nút & nền — raster

| # | Tên file | Kích thước | Định dạng | Bắt buộc? |
|---|---|---|---|---|
| 1 | `skin1_btn_capture.webp` | **320×320px** | WebP alpha | ✅ Bắt buộc — nút chụp 80dp, "bộ mặt" của skin |
| 2 | `skin1_btn_capture_pressed.webp` | 320×320px | WebP alpha | ⚪ Tuỳ chọn (không có → dùng bản thường + hiệu ứng scale) |
| 3 | `skin1_btn_capture_recording.webp` | 320×320px | WebP alpha | ⚪ Tuỳ chọn (trạng thái đang quay GIF) |
| 4 | `skin1_bg_bottombar.9.png` | rộng ≥1440px × cao **320px** | **9-patch** | ⚪ Tuỳ chọn (không có → nền trong suốt như hiện tại) |
| 5 | `skin1_bg_pill.9.png` | cao **160px**, rộng ≥400px | **9-patch** | ⚪ Tuỳ chọn (không có → dùng màu `pill`) |
| 6 | `skin1_bg_card.9.png` | ≥400×400px | **9-patch** | ⚪ Tuỳ chọn |
| 7 | `skin1_bg_bubble_out.9.png` | ≥400×176px | **9-patch** | ⚪ Tuỳ chọn — bong bóng chat mình gửi |
| 8 | `skin1_bg_bubble_in.9.png` | ≥400×176px | **9-patch** | ⚪ Tuỳ chọn — bong bóng chat nhận |
| 9 | `skin1_bg_screen.webp` | **1080×2340px** | WebP | ⚪ Tuỳ chọn — nền toàn màn (không có → màu `background` phẳng) |
| 10 | `skin1_thumb.webp` | **540×960px** (9:16) | WebP | ✅ Bắt buộc — ảnh hiện trong tab Skins |

> **Nếu không xuất được 9-patch**: bỏ hết mục 4–8, tôi dùng shape bo góc + màu token. Skin vẫn khác biệt rõ nhờ icon + nút chụp + bảng màu.

### 6.13.3 Bảng màu — 11 giá trị hex mỗi skin (không phải file, chỉ cần điền)

Đây là thứ tạo ra hiệu ứng "đổi đồng bộ" bạn yêu cầu. Điền 1 phiếu cho mỗi skin:

```
skinId:            1
displayName:       Neon                 (TIẾNG ANH)

background:        #0A0E27              nền mọi màn                (Default: #121212)
surface:           #141A3D              card, bottom sheet         (Default: #1A1A1A)
surfaceVariant:    #1E2650              nền phụ, bubble chat nhận  (Default: #2C2C2C)
onBackground:      #FFFFFF              chữ chính                  (Default: #FFFFFF)
onSurface:         #E0E4FF              chữ trên card              (Default: #E0E0E0)
onSurfaceVariant:  #8A93C7              chữ phụ, timestamp         (Default: #B0B0B0)
accent:            #00E5FF   ← thay MỌI chỗ vàng #FFFF00           (viền nút chụp, avatar đang chọn, khung QR)
accentGold:        #00B8D4   ← thay MỌI chỗ gold #FFD700           (streak, badge, pill Gold)
pill:              #1E2650   ← thay MỌI chỗ #404137                (nền pill "Everyone", nền avatar)
overlay:           #000000 @ 50%        lớp mờ đè lên ảnh          (Default: đen 50%)
error:             #FF5370              lỗi, nút xoá               (Default: #CF6679)
```

⚠️ **Kiểm tra tương phản trước khi chốt màu**: `onBackground` trên `background` và `onSurfaceVariant` trên `surface` phải đọc được. Chữ đè lên ảnh người dùng luôn giữ trắng ở mọi skin (mục 3).

### 6.13.4 Hiệu ứng touch — 5 hiệu ứng

Đặc tả kỹ thuật ở **mục 2.5.6**. Tên file:

| effectId | Dạng 1 (ảnh hạt) | Dạng 2 (sprite sheet) | Dạng 3 |
|---|---|---|---|
| 1 | `effect1_particle.png` | `effect1_sheet.png` | không có file |
| 2 | `effect2_particle.png` | `effect2_sheet.png` | không có file |
| 3 | `effect3_particle.png` | `effect3_sheet.png` | không có file |
| 4 | `effect4_particle.png` | `effect4_sheet.png` | không có file |
| 5 | `effect5_particle.png` | `effect5_sheet.png` | không có file |

Mỗi hiệu ứng gửi kèm **1 phiếu tham số** (mẫu ở mục 2.5.6). Mỗi hiệu ứng chọn **một** dạng, không trộn.

### 6.13.5 Tổng kết khối lượng bàn giao

| Hạng mục | Bắt buộc | Tuỳ chọn |
|---|---|---|
| **Mỗi skin** (×2) | **12 icon SVG (Nhóm 1)** + `btn_capture.webp` + `thumb.webp` + **11 mã màu** | 2 state nút chụp + 6 file nền/9-patch (`bottombar`, `pill`, `card`, `bubble_out`, `bubble_in`, `screen`) |
| **Hiệu ứng** (×5) | 1 file ảnh **hoặc** không file (Dạng 3) + 1 phiếu tham số | — |
| **TỔNG TỐI THIỂU** | **24 SVG + 4 ảnh WebP + 22 mã màu + 5 hiệu ứng** | |

**Thứ KHÔNG cần chuẩn bị:**
- Icon **Nhóm 2 và Nhóm 3** — giữ Material icon, chỉ tint theo token màu (mục 6.13.1)
- Icon launcher / splash — không đổi theo skin (mục 8.1–8.2, user chốt bỏ qua)
- Thumbnail cho hiệu ứng — tab Effects demo trực tiếp
- Asset cho **Profile / Settings / popup / login / quest / friends / coop** — Tier B chỉ đổi màu
- Viền "In use", badge "NEW", icon khoá, tab indicator ở màn Appearance — vẽ bằng code
- Nhiều mật độ màn hình (1 bản @4x là đủ) · nhiều bản màu cho hiệu ứng (app tự tint)

---

## 7. Lộ trình thi công — mỗi phase để app vẫn chạy được

| Phase | Nội dung | Xong thì app trông thế nào | Phụ thuộc |
|---|---|---|---|
| **P0** | Gom màu hardcode về `Color.kt` thành hằng số có tên (chưa có skin) | **Y HỆT hiện tại** | — |
| **P1** | Dựng skin engine (`AppSkin`, `LocalAppSkin`, `SkinIcon`, `DefaultSkin`, `SkinRegistry`); wire vào `AppTheme`; refactor toàn bộ `core/designsystem/component/**` sang đọc skin | **Y HỆT hiện tại** (DefaultSkin = giao diện cũ) | P0 |
| **P2** | Refactor `feature/**` sang token — **Tier A** làm kỹ (icon + nút + nền), **Tier B** chỉ đổi màu nền/chữ. Thứ tự theo bảng 10 file nặng ở mục 1 | **Y HỆT hiện tại** | P1 |
| **P3** | `skinId` + `touchEffectId` vào `SettingsPreferences`; **màn Appearance 3 tab** (tách `FrameItem` → `CollectibleItem` dùng chung); **gỡ sạch giao diện Light** theo bảng mục 4.4 | App chỉ còn dark; Appearance đủ 3 tab, mọi vật phẩm **mở sẵn** | P2 |
| **P4** | **Hiệu ứng touch**: `TouchEffectOverlay` bọc `NavHost` + `TouchEffectController` (tắt lúc quay GIF) + toggle trong Settings + `none`. **Engine dựng trước, hiệu ứng của user cắm vào sau** — 2 việc không chặn nhau | Tab Effects chạy được; cắm thêm hiệu ứng chỉ là thêm 1 file khai báo | P3 |
| **P5** | Nhận asset thật → dựng **skin 1 và skin 2** theo manifest 6.13 + cắm **5 hiệu ứng** theo phiếu 2.5.6 | 3 skin (0/1/2) × 6 hiệu ứng (0–5), **chưa khoá gì** | P3/P4 + asset |
| **P6** | **Server**: `unlockedSkins[]`, `unlockedEffects[]`, `gachaTickets`, `unlockType: 'GACHA'`, `GET /gacha/state`, `POST /gacha/roll` (3 loại vật phẩm), hook nguồn vé, audit, test | App chưa đổi | user chốt cơ chế gacha |
| **P7** | App: màn/dialog gacha + animation Compose thuần; khoá khung/skin/hiệu ứng theo sở hữu thật | Chức năng hoàn chỉnh | P5 + P6 |

**Điểm cắt an toàn để bảo vệ DATN**: xong **P5** đã là chức năng trọn vẹn, demo được (đổi giao diện + hiệu ứng chạm + xem bộ sưu tập khung). P6–P7 là phần mở rộng phụ thuộc cơ chế gacha bạn chưa chốt.

**P4 rẻ bất ngờ**: 4 hiệu ứng đầu không cần asset nào, chỉ là code Canvas — nhưng lại là thứ **gây ấn tượng ngay** khi demo. Nếu cần thứ tự ưu tiên khác, có thể kéo P4 lên trước P3.

---

## 8. Rủi ro & giới hạn kỹ thuật (biết trước để khỏi vỡ kế hoạch)

1. **Icon launcher KHÔNG đổi theo skin lúc chạy.** Android chỉ đổi được bằng `activity-alias` bật/tắt qua `PackageManager` — gây nhấp nháy icon ngoài home, mất shortcut, một số launcher không cập nhật. → **Bản đầu: giữ 1 icon launcher duy nhất.**
2. **Splash screen cũng vậy** — theme splash được đọc từ manifest lúc khởi động, trước khi code chạy. Muốn splash theo skin thì cũng phải `activity-alias`. → **Giữ 1 splash.**
3. **Widget Glance không nhận `LocalAppSkin`.** Glance chạy composition riêng trong process launcher → phải đọc `skinId` từ SharedPreferences rồi tự map sang resource id. Code riêng, không tái dùng được. → Xếp vào P7 hoặc bỏ ngoài phạm vi.
4. **Dung lượng APK.** Mỗi skin đầy đủ (icon vector + vài ảnh WebP @4x) ≈ 2–8MB. 3 skin ≈ +10–20MB. Nếu vượt ngưỡng chấp nhận được thì bật Android App Bundle / density split.
5. **Phân biệt "trắng vì theme" và "trắng vì nằm trên ảnh"** (mục 3) — refactor mù 142 chỗ `Color.White` sẽ làm chữ trên ảnh biến mất ở skin sáng.
6. **`@Preview` phải bọc `AppTheme`** để lấy skin; preview nào đang gọi `MaterialTheme {}` trần (ví dụ `MainBottomBarPreview`) sẽ không thấy skin — cần sửa khi tới file đó.
7. **9-patch** phải làm đúng (`.9.png`) nếu muốn nền pill/bubble/nút co giãn không méo. Đây là thứ dễ sai nhất khi bàn giao asset — cần xác nhận khâu thiết kế xuất được 9-patch, nếu không thì tôi thay bằng vector/shape + màu.
8. **Hiệu ứng touch nuốt mất thao tác** — rủi ro nghiêm trọng nhất của P4. Nếu overlay `consume()` sự kiện chạm thì nút bấm, cuộn danh sách, `VerticalPager` của feed và **nút chụp giữ-để-quay GIF** đều chết. Bắt buộc theo đúng mục 2.5.3, và **test tay 4 chỗ này** trước khi coi P4 là xong: nút chụp (bấm + giữ), vuốt feed, cuộn chat, pinch zoom camera.
9. **Hiệu ứng touch trên máy yếu** — giới hạn 8 hiệu ứng sống cùng lúc + toggle tắt trong Settings (mục 2.5.4).
10. **Trước khi kết thúc mỗi phase**: `spotlessApply` (license header), cập nhật `GUIDE.md`, `codegraph init`.

---

## 9. Câu hỏi còn mở — cần user chốt trước khi tới phase tương ứng

| # | Câu hỏi | Chặn phase |
|---|---|---|
| ~~1~~ | ✅ **CHỐT 2026-08-05**: **2 skin** (id 1, 2) + **5 hiệu ứng touch** (id 1–5). Tên concept điền vào bảng 0.2 khi bạn chốt | — |
| ~~2~~ | ✅ **CHỐT 2026-08-05**: **KHÔNG đổi font** — giữ Roboto ở mọi skin. `AppSkin.fontFamily` để `null`, chưa dùng | — |
| ~~3~~ | ✅ Không chặn nữa — manifest 6.13.2 để 3 trạng thái nút chụp là **tuỳ chọn**: gửi thì dùng, không gửi thì fallback (bản thường + hiệu ứng scale khi bấm) | — |
| ~~4~~ | ✅ Không chặn nữa — 9-patch là **tuỳ chọn** ở 6.13.2: không xuất được thì bỏ hết file nền, dùng shape bo góc + màu token | — |
| ~~4a~~ | ✅ **CHỐT 2026-08-05**: có, tắt hiệu ứng ở camera khi đang quay GIF (hiện thực ở mục 2.5.4) | — |
| ~~4b~~ | ✅ **CHỐT 2026-08-05**: chạy ở **mọi thao tác chạm**, kể cả bấm nút — cho nhất quán | — |
| ~~4c~~ | ✅ **CHỐT 2026-08-05**: **user tự cung cấp toàn bộ hiệu ứng**; app chỉ dựng sẵn `none`. Hợp đồng đầu vào ở mục 2.5.6 | — |
| ~~4d~~ | ✅ **CHỐT 2026-08-05**: skin 1 = `Snow`, skin 2 = `Forest`; hiệu ứng 1–5 = `Snowfall`/`Leaf`/`Sparkle`/`Bubble`/`Ember` (bảng 0.2). Asset placeholder đã dựng | — |
| ~~5–9~~ | ✅ **Toàn bộ câu hỏi về gacha đã chốt** trong `.claude/GACHA_PLAN.md` mục 0 (tiền tệ Astrite thay "vé", tỉ lệ, pity, hoàn trùng, 1 pool chung). Câu hỏi gacha còn mở nằm ở GACHA_PLAN mục 11 | — |
| ~~10~~ | ✅ **CHỐT 2026-08-05**: icon launcher/splash **cố định 1 bộ**, không đổi theo skin (mục 6.1 "bỏ qua") | — |
| 11 | Bảng màu 11 giá trị cho skin 1 và skin 2 (mẫu ở 6.13.3) — cần trước P5 | P5 |





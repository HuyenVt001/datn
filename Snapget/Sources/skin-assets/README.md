# skin-assets — Kho asset cho Skin, Hiệu ứng touch và Gacha

> **Toàn bộ file trong đây là PLACEHOLDER do Claude sinh tự động (2026-08-05).**
> Bạn chỉ cần **ghi đè file cùng tên** bằng thiết kế thật — không đổi tên, không đổi kích thước, không đổi thư mục. Code sẽ tự ăn.
>
> Đặc tả gốc: `.claude/SKIN_PLAN.md` mục **6.13** (manifest) và **2.5.6** (hợp đồng hiệu ứng) · `.claude/GACHA_PLAN.md` (gacha).

---

## Quy tắc vàng khi thay file

1. **Giữ nguyên tên file.** Tên file = tên resource Android, đổi tên là build fail hoặc mất asset.
2. **Giữ nguyên kích thước** đã ghi ở bảng dưới. Sai kích thước → lệch bố cục.
3. **Icon và hạt hiệu ứng phải là TRẮNG THUẦN `#FFFFFF`** (dùng alpha để tạo sắc độ). App tự tô màu theo skin đang dùng. Vẽ sẵn màu → tint sẽ hỏng.
4. **Không nướng sẵn bóng đổ / viền màu** vào icon và hạt hiệu ứng.
5. Thư mục này là **kho bàn giao**, chưa phải resource của app. Lúc thi công tôi sẽ copy sang `app/src/main/res/` và convert `.svg` → vector XML.

---

## 1. Skin

| skinId | Thư mục | Concept | Bảng màu |
|---|---|---|---|
| **1** | `skin1_snow/` | **Snow** | Đen · xanh lam đậm · trắng |
| **2** | `skin2_forest/` | **Forest** | Xanh lá đậm · vàng be · trắng |

*(skinId 0 = `Default` — giao diện đen hiện tại, không có asset)*

### 1.1 Mỗi thư mục skin chứa 14 file

| File | Kích thước | Ghi chú |
|---|---|---|
| `skin<N>_ic_camera.svg` | 24×24 | Nút chụp giữa bottom bar (hiển thị 66dp) |
| `skin<N>_ic_send.svg` | 24×24 | Gửi bài (xoay −45°) + gửi tin nhắn |
| `skin<N>_ic_gallery.svg` | 24×24 | Thư viện ảnh |
| `skin<N>_ic_flip_camera.svg` | 24×24 | Đổi camera trước/sau |
| `skin<N>_ic_close.svg` | 24×24 | Huỷ |
| `skin<N>_ic_captions.svg` | 24×24 | Danh sách caption |
| `skin<N>_ic_grid.svg` | 24×24 | Về lưới feed |
| `skin<N>_ic_more.svg` | 24×24 | ⋯ menu bài đăng |
| `skin<N>_ic_chat.svg` | 24×24 | Nút tin nhắn top bar |
| `skin<N>_ic_chevron_down.svg` | 24×24 | Pill "Everyone ⌄" |
| `skin<N>_ic_chevron_right.svg` | 24×24 | Mũi tên cuối hàng danh sách |
| `skin<N>_ic_back.svg` | 24×24 | Back |
| `skin<N>_btn_capture.webp` | **320×320px** | Nút chụp 80dp — nền trong suốt |
| `skin<N>_thumb.webp` | **540×960px** | Ảnh đại diện skin trong tab Skins (9:16) |

**Quy cách icon SVG**: viewBox `0 0 24 24` · nét nằm trong ô 20×20 giữa · độ dày nét ≥1.5 · đã outline stroke thành path · `fill="#FFFFFF"` · không gradient, không nhiều màu.

### 1.2 Bảng màu — 11 mã hex mỗi skin

Đây là thứ tạo hiệu ứng "đổi màu đồng bộ toàn app". Placeholder đang dùng bộ dưới đây; sửa lại nếu bạn muốn khác.

| Token | Đổi cái gì trong app | **Snow (skin 1)** | **Forest (skin 2)** | Default (skin 0) |
|---|---|---|---|---|
| `background` | Nền mọi màn | `#0B0F1A` | `#0E2416` | `#121212` |
| `surface` | Card, bottom sheet | `#10192B` | `#14301F` | `#1A1A1A` |
| `surfaceVariant` | Nền phụ, bubble chat nhận | `#1B3A6B` | `#1E4530` | `#2C2C2C` |
| `onBackground` | Chữ chính | `#FFFFFF` | `#FFFFFF` | `#FFFFFF` |
| `onSurface` | Chữ trên card | `#E8F0FF` | `#F2EEDF` | `#E0E0E0` |
| `onSurfaceVariant` | Chữ phụ, timestamp | `#8FA8CC` | `#A8BFA8` | `#B0B0B0` |
| **`accent`** | **MỌI chỗ vàng `#FFFF00`** — viền nút chụp, avatar đang chọn, khung QR | `#7EC8FF` | `#E8D9A8` | `#FFFF00` |
| **`accentGold`** | **MỌI chỗ gold `#FFD700`** — streak, badge, pill Gold | `#BFE4FF` | `#D4BE78` | `#FFD700` |
| **`pill`** | **MỌI chỗ `#404137`** — nền pill "Everyone", nền avatar | `#1B3A6B` | `#1E4530` | `#404137` |
| `overlay` | Lớp mờ đè lên ảnh | đen 50% | đen 50% | đen 50% |
| `error` | Lỗi, nút xoá | `#FF6B81` | `#E57373` | `#CF6679` |

⚠️ `onBackground` phải tương phản đủ với `background`, và `onSurfaceVariant` phải đọc được trên `surface`. Chữ đè lên ảnh người dùng luôn giữ trắng ở mọi skin.

### 1.3 File TUỲ CHỌN — chưa sinh placeholder, thêm vào khi bạn muốn

Bỏ qua hoàn toàn cũng được; không có thì app dùng shape bo góc + màu token.

| File | Kích thước | Dùng cho |
|---|---|---|
| `skin<N>_btn_capture_pressed.webp` | 320×320px | Nút chụp lúc đang bấm |
| `skin<N>_btn_capture_recording.webp` | 320×320px | Nút chụp lúc đang quay GIF |
| `skin<N>_bg_bottombar.9.png` | ≥1440×320px | Nền thanh bottom bar (9-patch) |
| `skin<N>_bg_pill.9.png` | ≥400×160px | Nền pill "Everyone" (9-patch) |
| `skin<N>_bg_card.9.png` | ≥400×400px | Nền card (9-patch) |
| `skin<N>_bg_bubble_out.9.png` | ≥400×176px | Bong bóng chat mình gửi (9-patch) |
| `skin<N>_bg_bubble_in.9.png` | ≥400×176px | Bong bóng chat nhận (9-patch) |
| `skin<N>_bg_screen.webp` | 1080×2340px | Ảnh nền toàn màn |

---

## 2. Hiệu ứng touch — `effects/`

5 hiệu ứng, tất cả đều là **vật phẩm bậc SR** trong gacha. Chi tiết tham số từng hiệu ứng: **`effects/EFFECTS.md`**.

| effectId | Concept | File | Kích thước |
|---|---|---|---|
| 1 | Snowfall | `effect1_particle.png` | 192×192px |
| 2 | Leaf | `effect2_particle.png` | 192×192px |
| 3 | Sparkle | `effect3_particle.png` | 192×192px |
| 4 | Bubble | `effect4_particle.png` | 192×192px |
| 5 | Ember | `effect5_particle.png` | 192×192px |

*(effectId 0 = `None` — không hiệu ứng, không có asset)*

**Quy cách hạt**: PNG-32 có alpha · hình nằm giữa, chừa lề ≥10% (~20px) mỗi cạnh · **trắng thuần `#FFFFFF`** · ≤100KB.
Muốn hạt có hoạt ảnh riêng thì đổi sang sprite sheet: `effect<N>_sheet.png`, các frame 192×192 xếp 1 hàng ngang sát nhau, 4–24 frame (ghi rõ số frame + fps vào `EFFECTS.md`).

---

## 3. Gacha — `gacha/`

| File | Kích thước | Ghi chú |
|---|---|---|
| `ic_astrite.png` | 360×360px | **Icon tiền tệ Astrite — trích từ file Excel của bạn, KHÔNG phải placeholder** |
| `gacha_banner.png` | 1080×608px (16:9) | Banner ở trang Daily, bấm vào → màn Gacha. Admin đổi được qua trang quản trị |
| `gacha_bg.png` | 1080×2340px | Nền màn Gacha |

### Asset gacha còn thiếu — chưa sinh, cần bạn quyết

| Asset | Kích thước đề xuất | Ghi chú |
|---|---|---|
| Viền/khung phẩm chất N · R · SR · SSR | 1080×1080px hoặc vẽ bằng code | Màu: N xám · R xanh dương · SR tím · **SSR cam** (SSR bạn đã chốt, 3 màu kia đang là đề xuất) |
| Hiệu ứng nền lúc quay ra SSR | 1080×1080px | Có thể vẽ bằng Canvas nếu không muốn tốn asset |
| Nút quay x1 / x10 | 9-patch cao 224px | Không có thì dùng shape + màu token |

---

## 4. Trạng thái hiện tại

| Nhóm | Số file | Trạng thái |
|---|---|---|
| Skin 1 — Snow | 14 | 🟡 Placeholder |
| Skin 2 — Forest | 14 | 🟡 Placeholder |
| Hiệu ứng touch | 5 | 🟡 Placeholder |
| Gacha | 3 | 🟡 2 placeholder + 1 thật (`ic_astrite.png`) |
| **Tổng** | **36** | |

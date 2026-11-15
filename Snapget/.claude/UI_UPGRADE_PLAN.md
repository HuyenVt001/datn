# UI_UPGRADE_PLAN.md — Nâng cấp UI/UX "Playful Dark" (NHÁP 2026-11-15)

> Mục tiêu: app sinh động, vui, có cảm giác gamified (mỗi chạm có phản hồi, mỗi thành tựu được ăn mừng).
> Quy chuẩn thiết kế: `DESIGN.md` **mục 0**. Danh sách lỗi hiện tại: `UI_REVIEW.md`.
> Trạng thái (2026-11-15): **P0 → P4 ĐÃ CODE XONG** (app + server, build/test sạch) — ⬜ chưa thử trên máy thật.
> P5 (đánh bóng) ✅: skeleton, Snackbar dùng chung, chuyển màn mới.
>
> | Phase | Trạng thái |
> |---|---|
> | P0 Nền móng | ✅ |
> | P1 Sửa lỗi UX | ✅ (chat dùng 44dp thay 48dp — xem `ChatTouchTarget`) |
> | P2 Phản hồi tương tác | ✅ |
> | P3 Gamification + thiết kế lại Quest/Shop/Chat | ✅ |
> | P4 Ăn mừng + push nhắc streak | ✅ (server `POST /moments.streak` + cron `POST /users/streak-reminders/send`) |
> | P5 Đánh bóng | ✅ skeleton (Feed/Messages/Quest/Shop), Snackbar dùng chung (`rememberMessenger`), chuyển màn fade+scale |

## Lộ trình (user chốt thứ tự: nền móng → sửa lỗi → vui)

| Phase | Nội dung | Ghi chú |
|---|---|---|
| **P0 — Nền móng** | Motion tokens (DESIGN 0.2) · helper haptic (~~sound~~ — đã bỏ) · check reduced-motion · token màu `success/streak/info/badge/accentShadow` vào `SkinColors` (+ Snow/Forest) · **tông vàng mới** · Nunito vào `res/font` + `Type.kt` đủ thang M3 · `ChunkyButton` · thêm `lottie-compose` vào version catalog | Đổi vàng = 2 dòng `DefaultSkin.kt` |
| **P1 — Sửa lỗi UX** | P0 của `UI_REVIEW.md`: touch target 48dp · Loading/Error+Retry/Empty+CTA · pull-to-refresh · không xóa text trước khi gửi xong · "Discard changes?" ở Edit · `rememberSaveable` · Camera: pill "N Friends" mở sheet bạn bè · xóa icon Photo Library | |
| **P2 — Phản hồi tương tác** | Press feedback + haptic mọi nút · footer chỉ icon, tab chọn phình 26→32dp + `Role.Tab` · badge thống nhất `accentGold` · stagger list (Shop/Quest/Messages) · Snackbar thay Toast · áp `ChunkyButton` cho CTA | |
| **P3 — Gamification hiển thị** | Thanh tiến độ streak tới mốc · vòng quest · thưởng hiện trước · CTA trên quest card · nhắc giữ streak | |
| **P4 — Ăn mừng** | Overlay lớn (streak +1, mốc, mở khóa khung) + hiệu ứng tại chỗ (quest, mua item, kết bạn, AI) · Lottie (không âm thanh) | Cần biết "streak vừa tăng" — xem câu hỏi |
| **P5 — Đánh bóng** | Copy mới (DESIGN 0.9) · skeleton · empty state có Lottie nhỏ | |

## Quyết định đã chốt (2026-11-15)
| Chủ đề | Quyết định |
|---|---|
| Màu | Thêm token chức năng `success` / `streak` / `info`; vàng vẫn là màu thương hiệu |
| Tông vàng | **Honey Gold**: accent `#F5C542` · accentGold `#E8A93A` · shadow `#AB892E` |
| Badge chưa đọc | `accentGold` ở mọi nơi |
| Font | Nunito cho cả app |
| Nút chính | Chunky 3D (đáy 4dp, nhấn lún) — trừ nút chụp 80dp |
| Ăn mừng | Kết hợp: lớn = overlay, nhỏ = tại chỗ |
| Animation | Lottie (`lottie-compose` được phép), nguồn LottieFiles free, **không linh vật** |
| Âm thanh | ❌ **BỎ HOÀN TOÀN** (user đổi ý 2026-11-15, sau khi đã làm) — đã xoá engine + file + setting |
| Camera top bar | Pill "N Friends" → mở sheet bạn bè |
| Photo Library icon | Xóa |
| Footer | Chỉ icon; tab chọn đổi màu + phình to |

## Lottie — tìm & thêm animation free (hướng dẫn cho user)

**Tìm ở đâu**
1. https://lottiefiles.com/free-animations (và trang Popular user gửi). Lọc **Free**; gõ từ khóa tiếng Anh.
2. Mỗi animation free trên LottieFiles theo **Lottie Simple License** — dùng thương mại được, không bắt buộc ghi nguồn. **Vẫn mở trang chi tiết kiểm tra license từng file** trước khi tải.

**Từ khóa gợi ý theo từng chỗ dùng**
| Chỗ dùng | Từ khóa | Yêu cầu |
|---|---|---|
| Streak +1 | `fire`, `flame`, `streak fire` | Ngọn lửa loop hoặc bùng lên 1 lần, nền trong suốt |
| Mốc streak / mở khóa khung | `confetti`, `celebration`, `trophy`, `achievement` | Chạy 1 lần 1.5–2.5s |
| Xong quest | `success check`, `checkmark`, `done` | Ngắn < 1s |
| Mua item | `sparkle`, `stars burst`, `coin` | Ngắn, dùng quanh item |
| Mở rương / quà | `gift box open`, `reward` | 1 lần |
| Kết bạn | `hearts`, `high five`, `friends` | Nhỏ gọn |
| Empty state | `empty box`, `no data`, `camera` | Loop nhẹ, ít chi tiết |
| Lỗi mạng | `no internet`, `error`, `wifi` | Loop nhẹ |
| Loading | `loading dots` | Loop |

**Tiêu chí chọn** (để hợp app dark + skin): nền **trong suốt**; ít màu (2–3 màu, dễ đổi sang vàng skin); dung lượng **< 100KB**; ≤ 60fps; không có chữ tiếng Anh cứng trong animation; phong cách **nhất quán** giữa các file (cùng flat hoặc cùng nét viền — đừng trộn).

**Cách tải & đặt vào project**
1. Trang animation → **Download** → chọn **Lottie JSON** (hoặc `.lottie` nếu chọn dotLottie — thống nhất 1 loại, mặc định JSON).
2. Đổi tên theo mẫu `lottie_<chỗ dùng>.json` (chữ thường, gạch dưới), vd `lottie_streak_fire.json`, `lottie_confetti.json`.
3. Chép vào `Snapget/app/src/main/res/raw/`.
4. Báo mình tên file + chỗ dùng → mình nối vào code (`rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.lottie_confetti))`) và đổi màu theo skin bằng `LottieDynamicProperties`.
5. Muốn xem trước / đổi màu trước khi tải: dùng **Lottie Editor** trên LottieFiles (đổi màu layer sang tông vàng đã chọn).


## Khoảnh khắc ăn mừng — chốt đủ cả 4 nhóm (2026-11-15)
| Loại | Khoảnh khắc |
|---|---|
| **Lớn** (overlay toàn màn) | Streak +1 · mốc 3/7/14/30 (+ confetti + preview khung vừa mở khóa) |
| **Nhỏ** (tại chỗ) | Xong quest / đủ 2/2 · Mua item ở Shop · Kết bạn qua QR · AI challenge đúng (sheet thay toast) |

## Thiết kế lại 3 màn (user chốt 2026-11-15)
| Màn | Hướng | Chi tiết |
|---|---|---|
| **Quest** | **Hero streak + rương** (làm lại bố cục) | Hero: Lottie lửa + số streak lớn + **dải 7 ngày** (T2→CN, chấm đầy = đã đăng; suy từ `personalStreak` + quest `POST_MOMENT.completed`, không cần API mới) · thẻ quest có **thanh tiến độ 0/1**, chip thưởng (AI: 💎+30; quest cố định: "counts toward chest"), nút **GO** chunky (LOGIN: ẩn; POST_MOMENT / AI → mở Camera) · **Rương ngày** 💎+60: khóa `x/2`, xong 2/2 → bấm mở (Lottie gift + số đếm + sfx; thưởng server đã cộng, mở rương chỉ là hiệu ứng; nhớ "đã mở hôm nay" bằng SharedPreferences) · **Đường mốc** 3·7·14·30 với chấm đã đạt |
| **Shop** | **Chỉ nâng cấp ô item** | Giữ TabRow + lưới 2 cột · ô item → thẻ chunky (đáy dày 4dp, nhấn lún) · huy hiệu góc ✓ Owned / Equipped · nút giá = `ChunkyButton` nhỏ · mua thành công: item `pop` + Lottie sparkle + số dư đếm xuống |
| **Chat** | ~~Streak bạn bè nổi bật~~ → **đánh bóng** (friend streak ĐÃ BỎ 2026-11-15) | Messages: item 1-1 chấm vàng chưa đọc + nhấn-lún + stagger, kéo-tải-lại, Error/Empty · Chat 1-1: header chỉ tên (bỏ "Active now" giả + nút ⋮ chết) · **bubble của mình nền `accent` chữ `onAccent`** · nút gửi tròn chunky · chat trống: Lottie + nút "Say hi 👋" |

## Thay đổi server cần làm (đổi contract → ghi GUIDE 3 nơi, server trước, app sau)
1. **`POST /moments` trả thêm field `streak`** (thêm field = an toàn, app cũ bỏ qua):
   ```json
   "streak": { "personalStreak": 6, "increased": true, "milestone": 7, "unlockedFrameId": "frame_streak_7" }
   ```
   `increased` = true chỉ ở bài **đầu tiên trong ngày** làm streak tăng; `milestone`/`unlockedFrameId` = null nếu không chạm mốc.
   App: `MomentDto.streak` → `PostViewModel` phát event → `SubmitPhotoScreen` hiện overlay trước khi về camera.
2. **Push nhắc giữ streak** (user chọn thông báo thay vì nhắc trong app): cron server mỗi ngày, gửi FCM cho user có `personalStreak > 0` mà **hôm nay chưa đăng bài**. Nội dung tiếng Anh, vd *"🔥 Your 5-day streak ends at midnight — snap something!"*. Bấm thông báo → mở thẳng Camera (đã có `PendingRouteStore`). Mặc định gửi **20:00 giờ máy người dùng** (cần lưu timezone của user ở server — kiểm tra đã có chưa). App: toggle **"Streak reminders"** trong Settings (gửi lên server).

## Asset tạm đã tải (2026-11-15 — Claude chọn theo metadata, user sẽ thay sau)
Tất cả trong `app/src/main/res/raw/`. Thay = chép đè **đúng tên file**, không phải sửa code.

| File | Dùng cho | Nguồn | License |
|---|---|---|---|
| `lottie_streak_fire.json` (16KB) | Overlay streak +1 | [Fire Streak Orange — Dhawal](https://lottiefiles.com/free-animation/fire-streak-orange-HEj0rht12V) | Lottie Simple |
| `lottie_confetti.json` (76KB) | Mốc streak, kết bạn | [Confetti — Shubh Dubey](https://lottiefiles.com/free-animation/confetti-3ofTs67sBx) | Lottie Simple |
| `lottie_trophy.json` (100KB) | Mốc streak 3/7/14/30 | [Trophy — Mahendra](https://lottiefiles.com/free-animation/trophy-yEGPe40FVr) | Lottie Simple |
| `lottie_success_check.json` (4KB) | Xong quest, AI challenge | [Success Check — Pranav Patil](https://lottiefiles.com/free-animation/success-check-zSLSFlcAJq) | Lottie Simple |
| `lottie_sparkle.json` (7KB) | Mua item | [Sparkle — Akiko](https://lottiefiles.com/free-animation/sparkle-rNRoE1hcCt) | Lottie Simple |
| `lottie_gift_open.json` (16KB) | Thưởng 2/2 quest | [Gift box — Priyal Nagpal](https://lottiefiles.com/free-animation/gift-box-KlruD7Z5l6) | Lottie Simple |
| `lottie_friends.json` (33KB) | Kết bạn qua QR | [High five — SoumyaRanjan](https://lottiefiles.com/free-animation/high-five-onJe5OolzT) | Lottie Simple |
| `lottie_empty.json` (17KB) | Empty state | [Empty Box — Animo Arts](https://lottiefiles.com/free-animation/empty-box-uNf2uP0OdP) | Lottie Simple |
| `lottie_no_internet.json` (14KB) | Error state | [No Internet Connection — manju](https://lottiefiles.com/free-animation/no-internet-connection-MMED6fERQn) | Lottie Simple |
| `lottie_loading.json` (3KB) | Loading nhỏ | [Loading Dots — Animo Arts](https://lottiefiles.com/free-animation/loading-dots-tpHnmYPoJn) | Lottie Simple |

## Thay đổi quyết định 2026-11-15 (sau khi đã code P0–P4)
- **Bỏ hoàn toàn âm thanh** — xoá `SoundEffects`, 7 file `res/raw/sfx_*.ogg`, setting "Sound effects"; `Feedback` chỉ còn rung.
- **Bỏ friend streak** (cả server lẫn app) — chỉ giữ streak cá nhân. Server: xoá `registerInteraction`/`updateStreak`/`STREAK_WINDOW_HOURS`, `GET /friendships` không trả `friendStreak`. App: bỏ hàng "Streaks" ở Messages, 🔥 trên hội thoại/header chat/sheet bạn bè.

## Còn chờ chốt (không chặn việc code — mặc định ghi trong ngoặc)
- [ ] Giờ gửi nhắc streak (mặc định 20:00 giờ địa phương)
- [ ] Haptic có toggle riêng trong Settings không (mặc định: không, theo cài đặt rung hệ thống)

## Việc user cần làm (checklist vận hành)
- [x] ~~Tải Lottie~~ — Claude đã tải bản tạm (bảng "Asset tạm"); muốn thay thì chép đè đúng tên file
- [ ] **Deploy server** (Render) cùng đợt phát APK mới: `POST /moments` có thêm `streak` (APK cũ bỏ qua an toàn) + endpoint cron mới
- [ ] **Tạo job cron-job.org**: `POST https://<server>/api/users/streak-reminders/send` mỗi ngày **13:00 UTC** (20:00 VN), header `x-cron-secret: <CRON_SECRET>` (dùng lại secret của job quest AI)
- [ ] Test trên máy thật:
  - [ ] Đăng bài đầu ngày → overlay streak +1 (rung); đăng bài thứ 2 → KHÔNG overlay
  - [ ] Streak chạm 3/7/14/30 → confetti + cúp + ảnh khung (cần admin đã tạo khung STREAK_MILESTONE cho mốc đó)
  - [ ] Quest: dải 7 ngày đúng, nút GO mở camera, mở rương sau 2/2, mở lại app không mở lại được rương
  - [ ] Shop: mua → thẻ nảy + lấp lánh + số dư đếm xuống
  - [ ] "Remove animations" (Accessibility) → Lottie/stagger dừng, app vẫn dùng được
  - [ ] Tắt "Streak reminders" → push nhắc không hiện; bấm push → mở Camera
  - [ ] Máy nhỏ 360dp: ô chat đủ chỗ gõ, nút Next màn Edit luôn thấy

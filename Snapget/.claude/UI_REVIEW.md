# UI_REVIEW.md — Review UI/UX toàn bộ màn hình Snapget (2026-11-15)

> Dùng skill `ui-ux-pro-max` (checklist ưu tiên 1→10 + pre-delivery checklist app native) và
> `mobile-android-design` (Material 3 + Compose best practices).
> **Tôn trọng các quyết định đã chốt trong DESIGN.md** — không đề xuất: light mode, dynamic color,
> thêm accent màu khác, bỏ emoji reaction/streak (emoji là chủ ý của sản phẩm).
> Đường dẫn tính từ `app/src/main/java/com/example/snapget/`.

**Đánh giá chung:** nền tảng tốt — skin token hóa, dark-first nhất quán, hầu hết icon đã có
`contentDescription`, các thao tác xóa đều có dialog xác nhận, Login validate tại chỗ. Điểm yếu lặp lại
ở nhiều màn: **(1) touch target nhỏ**, **(2) lỗi mạng hiển thị như "trống"**, **(3) mất nội dung khi
gửi lỗi / thoát màn**, **(4) accessibility semantics gần như bằng 0**.

---

## 🔴 P0 — Nên sửa trước (ảnh hưởng trực tiếp tới người dùng)

### 1. Touch target < 48dp ở các thao tác chính
M3 yêu cầu ≥ 48dp. Các vị trí bấm nhiều nhất đang nhỏ hơn rất nhiều:

| Vị trí | Kích thước hiện tại | File |
|---|---|---|
| Emoji reaction trên feed (💛😂💕 + 8 emoji mở rộng) | ~28×24dp | `core/designsystem/component/pill/MessagePill.kt:186-207` |
| Nút Send trong pill feed | 32dp | `MessagePill.kt:120-127` |
| Emoji gửi nhanh trong chat | ~28×24dp | `feature/message/ChatScreen.kt:619-626` |
| Nút ảnh / sticker / mic / send trong chat | 32dp | `ChatScreen.kt:630, 641, 657, 674` |
| Nút hủy reply | 28dp | `ChatScreen.kt:516` |
| Hàng React / Reply sau khi giữ tin | 32dp | `ChatScreen.kt:1011, 1021` |
| Ô màu + độ dày nét doodle | 26dp | `feature/post/EditMediaScreen.kt:415-460` |
| Nút Home giữa footer | 56×40dp | `core/designsystem/component/bottombar/AppFooterBar.kt:122-127` |
| Nút mua ở Shop | cao 36dp | `feature/shop/ShopScreen.kt:455` |
| Nút tròn top bar (avatar / quest / message / profile icons) | 40dp | `topbar/MainTopBar.kt:377, 428` · `topbar/UserProfileTopBar.kt:73` |

**Cách sửa:** giữ nguyên kích thước *hiển thị*, chỉ mở rộng vùng chạm — `Modifier.minimumInteractiveComponentSize()`
hoặc bỏ `Modifier.size(32.dp)` trên `IconButton` (IconButton mặc định đã 48dp). Không làm lệch giao diện.

### 2. Lỗi mạng hiển thị như "không có dữ liệu", không có nút thử lại
- **Feed tab Everyone:** lỗi tải feed vẫn hiện *"No posts to show"* — người dùng tưởng bạn bè không đăng gì.
  `feature/post/PostScreen.kt:687-694` (nhánh `isEveryoneTab` bỏ qua `feedStatus is LoadStatus.Error`).
- **Messages:** lỗi hiện dưới tiêu đề "No messages yet" — `feature/message/MessageScreen.kt:145-180`.
- **Chat:** tương tự — `ChatScreen.kt:248-264`.
- **Không màn nào có pull-to-refresh** (grep `PullToRefresh` = 0) → cách duy nhất để thử lại là thoát ra vào lại.

**Cách sửa:** tách 3 trạng thái *Loading / Error / Empty*; Error = message server + nút **Retry**
(đã có mẫu tốt ở `DailyQuestScreen` và `ShopScreen`). Thêm `PullToRefreshBox` (M3) cho Feed grid,
Messages, Profile.

### 3. Mất nội dung người dùng đã nhập
- **Chat / feed reply:** ô nhập bị xóa **ngay khi bấm Send**, trước khi biết gửi thành công
  (`ChatScreen.kt:189-196`, `MessagePill.kt:124-125`) → gửi lỗi (toast) là mất chữ.
  Sửa: chỉ clear khi success, hoặc khôi phục text khi lỗi.
- **EditMedia:** bấm ✕ hoặc Back thì mất hết nét vẽ/khung/filter mà không hỏi
  (`EditMediaScreen.kt:567`, không có `BackHandler`). Sửa: khi đã có chỉnh sửa → dialog *"Discard changes?"*.
- **State mất khi xoay màn / process bị kill:** cả app chỉ 2 file dùng `rememberSaveable` (68 chỗ
  `remember { mutableStateOf }`). Nên chuyển sang `rememberSaveable` cho: text đang gõ trong chat, caption
  ở SubmitPhoto, tab đang chọn ở Shop/Feed (riêng mật khẩu Login giữ `remember` như hiện tại là đúng).

### 4. Màn Camera: dropdown "Everyone" không có tác dụng
`feature/camera/CameraScreen.kt:72-80, 134` — chọn bạn trong dropdown chỉ gán vào `selectedUser`, biến này
**không được dùng ở đâu**. Người dùng chọn rồi không thấy gì thay đổi. Hoặc ẩn dropdown ở Camera, hoặc dùng
lựa chọn đó làm người nhận mặc định khi sang SubmitPhoto. **→ cần user chốt hành vi (luật DESIGN.md).**

### 5. Icon "Photo Library" ở camera thực chất mở Feed
`core/designsystem/component/bottombar/MainBottomBar.kt:370-377` — icon thư viện ảnh + nhãn TalkBack
"Photo Library", nhưng `route = Screen.Post.route`. Người dùng sẽ tưởng là chọn ảnh từ máy.
Hoặc đổi icon/nhãn thành "Feed/History", hoặc cho mở photo picker thật. **→ cần user chốt.**

---

## 🟠 P1 — Nhất quán & phản hồi tương tác

### 6. Footer 5 tab (`AppFooterBar.kt`)
- Tab thường dùng `indication = null` (dòng 141-145) → bấm **không có ripple/phản hồi**. Nút giữa lại có ripple → không đồng nhất.
- Tab Home hiển thị icon **"+"** (dòng 65-70) nhưng nghĩa là "về camera" — icon "+" thường hiểu là "tạo mới". Có thể chấp nhận vì camera = tạo moment, nhưng nhãn TalkBack nên là "Camera" thay vì "Home".
- Thiếu semantics tab: thêm `Modifier.selectable(selected = isSelected, role = Role.Tab)` để TalkBack đọc "đã chọn". Hiện cũng **không có nhãn chữ** nên người mới khó đoán (icon Send xoay -25° cho Chat, cúp cho Quest).
- Badge chưa đọc màu **đỏ hardcode `0xFFFF3B30`** (dòng 170), trong khi badge top bar (`MainTopBar.kt:407`) dùng **vàng `accentGold`** → 2 màu cho cùng một ý nghĩa. Chọn một, thêm token (`colors.badge`) theo luật SKIN. `contentDescription` của tab Chat nên kèm số: "Chat, 3 unread".

### 7. Màn Feed (`PostScreen.kt`)
- Padding **số cứng** `top = 110.dp, bottom = 165.dp` (377), grid `top = 130.dp` (321), banner `top = 140.dp` (497). Trên máy nhỏ (360×640) hoặc khi người dùng tăng cỡ chữ hệ thống, ảnh bị thu nhỏ mạnh / banner đè lên ảnh. Nên đo chiều cao top bar / bottom row (`onSizeChanged`) hoặc dùng `Scaffold` slots.
- Banner lời mời coop / kết bạn: `Modifier.clickable` đặt **ngoài** `Surface` (505, 531) → ripple hình chữ nhật tràn ra ngoài góc bo; không có nút đóng banner. Dùng `Surface(onClick = …)`.
- Placeholder "Send message..." để **Bold** (`MessagePill.kt:91`) — placeholder in đậm dễ bị nhầm là chữ đã nhập. Đề xuất Normal.
- Empty state "Tap the camera button…" (706) — nút chụp ở hàng này đã bỏ; nên nói rõ "Tap + below".

### 8. Màn Chat (`ChatScreen.kt`)
- **Tự cuộn xuống đáy mỗi khi có tin mới** (167-171), kể cả khi người dùng đang kéo lên đọc tin cũ (polling 5s) → bị giật về cuối. Chỉ tự cuộn khi đang ở gần đáy; còn lại hiện chip "↓ New messages".
- Reply / React **chỉ mở được bằng giữ lâu** — khó phát hiện. Thêm swipe-to-reply hoặc gợi ý lần đầu. Đồng thời thêm `onLongClickLabel = "Message options"` trong `combinedClickable` để TalkBack đọc được.
- Bubble text dùng `bodyMedium` 14sp (949) — DESIGN.md mục 3 quy định bubble = `bodyLarge` 16sp.
- Timestamp `onSurfaceVariant.copy(alpha = 0.6f)` (966) ≈ `#717171` trên `#121212` → **contrast ~3.8:1 < 4.5:1** (WCAG AA cho chữ nhỏ). Bỏ alpha, dùng thẳng `textSecondary`.
- Màu ghi âm `Color.Red` hardcode (591, 663) → dùng `SkinTheme.colors.error`.

### 9. Màn Messages (`MessageScreen.kt`)
- Empty state dùng icon **mũi tên `ArrowForward`** 64dp vàng mờ (155-160) — không mang nghĩa gì. Đề xuất `ChatBubbleOutline`, và thêm CTA "Add friends" (người không có bạn thì không thể chat).
- Item nhóm chat không có preview tin cuối / trạng thái chưa đọc như item 1-1 → 2 loại item lệch nhau.

### 10. Màn Login (`feature/auth/LoginScreen.kt`)
- **Lỗi hiện 2 lần**: Snackbar (223-229) + dòng đỏ inline (576-596), mà `clearError()` gọi ngay sau snackbar làm dòng inline biến mất. Giữ inline (gần field), bỏ snackbar cho lỗi.
- Nút **Sign In màu vàng** (theo `colorScheme.primary = accent`, 440-447) — DESIGN.md 7.13 chốt là *trắng chữ đen* (cùng họ nút Google). Lệch design.
- Dòng "By continuing, you agree to our Terms… Privacy Policy" bị `maxLines = 1` (608) → bị cắt trên máy hẹp, và **không bấm được** dù app đã có `LegalDocScreen`. Bỏ maxLines, link 2 cụm chữ tới `legal/{docType}`.
- Phím Done ở chế độ Forgot password gọi `onResetPassword(email)` trực tiếp (367-372), **bỏ qua validate** + trim. Gọi `submit()` như phím Done của ô password.
- Spinner trong nút Google dùng `colorScheme.primary` (vàng) trên nền trắng (547-551) → gần như không thấy. Dùng `Color.Black`.
- Chưa hỗ trợ **autofill / password manager**: thêm `Modifier.semantics { contentType = ContentType.EmailAddress / Password }` (Compose 1.8+) — checklist "Authentication allows password managers".

### 11. Màn Settings (`feature/settings/SettingScreen.kt`)
- Icon section + icon mọi item + tiêu đề section đều **vàng** (`colorScheme.primary`, 247-256, 302-320) → vàng xuất hiện dày đặc, trái nguyên tắc DESIGN.md 1.2 ("vàng chỉ cho nút chụp, avatar chọn, gamification"). Đề xuất icon `textPrimary` trên nền `pill`, giữ vàng cho toggle đang bật.
- Icon trang trí cạnh chữ có `contentDescription` trùng với title (251, 313) → TalkBack đọc 2 lần. Đặt `null`.

### 12. Màn Edit (`feature/post/EditMediaScreen.kt`)
- Nút **Next nằm trong `verticalScroll`** cùng nội dung → máy nhỏ phải cuộn mới thấy CTA chính. Ghim hàng ✕ / Next ở đáy (ngoài vùng cuộn).
- Ô màu doodle không có nhãn TalkBack, trạng thái chọn chỉ thể hiện bằng viền màu. Thêm `semantics { contentDescription = "Red"; selected = … }`.
- Toast lỗi in `e.message` thô ra cho người dùng (245) → message thân thiện.

---

## 🟡 P2 — Đánh bóng

13. **Cỡ chữ < 12sp:** badge 10sp (`AppFooterBar.kt:177`, `MainTopBar.kt:415`), "In use" 10sp (`CollectibleItem.kt:91`), số ngày lịch **10sp + alpha 0.5** (`EmptyDayItem.kt:44-48` — vừa nhỏ vừa contrast ~3:1), nhãn khung 11sp (`EditMediaScreen.kt:519, 650`), `TopupComponents.kt:112`. Tối thiểu 12sp; số ngày lịch bỏ alpha.
14. **Dropdown bạn bè (`MainTopBar.kt`):** mỗi item lại phủ thêm nền `pillTranslucent` lên menu cũng `pillTranslucent` (210-212) → độ đậm cộng dồn, viền item lộ; chevron "›" ở mọi dòng gợi ý *điều hướng* chứ không phải *chọn* — đề xuất dấu ✓ ở người đang chọn (hiện không biết đang chọn ai). Chevron trang trí có `contentDescription "Select"/"Dropdown"` (182, 274) → TalkBack đọc thừa; để pill tổng có `Role.DropdownList`.
15. **Profile top bar:** `contentDescription = "FRIEND"` (`UserProfileTopBar.kt:30`) → "Friends".
16. **Daily Quest:** *"days streak"* (`DailyQuestScreen.kt:191`) → "day streak". Card quest chưa xong không có CTA (vd quest "đăng 1 bài" → bấm mở camera); phần thưởng (+60 / +30 Astrite) chỉ hiện *sau khi* xong — hiện trước sẽ tạo động lực. Streak banner có thể thêm thanh tiến độ tới mốc kế tiếp (vd 5/7).
17. **Shop:** nút giá mờ 0.6 khi thiếu tiền nhưng vẫn bấm được — mờ thường được hiểu là *disabled*. Chủ ý (dẫn tới dialog Top up) thì ổn, nhưng cân nhắc thêm nhãn nhỏ "Not enough".
18. **Nút Co-op ở camera** dùng emoji "👥" làm icon + `clickable` ngoài `Surface` (`CameraScreen.kt:209-224`). Đề xuất `Icons.Filled.Group` + `Surface(onClick=…)`, cao ≥ 48dp.
19. **Phản hồi xúc giác:** 0 chỗ dùng haptic. Chụp ảnh, bắt đầu quay GIF, thả reaction là 3 chỗ rất hợp với `HapticFeedbackType.LongPress/TextHandleMove`.
20. **Toast vs Snackbar:** 15 file dùng Toast cho lỗi + thành công. Toast không có action (Retry/Undo), không gắn layout. Gom dần về `SnackbarHostState` ở các màn Scaffold (Login đã làm).
21. **Loading:** toàn spinner giữa màn (20 file), không skeleton. Feed grid + Messages + Profile calendar dùng skeleton xám (`surfaceVariant`) sẽ đỡ "nhảy layout".
22. **Hardcode màu trong `feature/`:** `EditMediaScreen` 26 chỗ, `QrScanScreen` 11, `CoopCaptureScreen` 8… Phần lớn là chữ/icon đè lên ảnh (trắng có chủ ý — đã ghi chú ở `CameraScreen.kt:53-56`), nhưng `Color.Gray` / `Color.DarkGray` / `Color.Red` nên về token.

---

## Đã làm tốt (giữ nguyên)
- Skin token hóa đầy đủ, `toColorScheme()` để component M3 tự đổi màu theo skin.
- Mọi hành động xóa (post, bạn bè, thành viên nhóm, rời nhóm) đều có dialog xác nhận, nút nguy hiểm màu `error`.
- Login: validate email / độ dài mật khẩu tại chỗ, không lưu mật khẩu vào `savedInstanceState`, có ImeAction Next/Done.
- Chặn double-tap: nút chụp, nút Send ở SubmitPhoto (`submitStatus is Loading`).
- Feed ẩn ô reply/react trên bài của chính mình; `key(post.id)` để chữ đang gõ không dính sang bài khác.
- DailyQuest + Shop đã có đủ Loading / Error + Retry / Content — dùng làm mẫu cho các màn khác.
- Contrast token chính đạt chuẩn: `textSecondary #B0B0B0` / `#121212` ≈ 8.6:1, `error #CF6679` / `#1A1A1A` ≈ 4.9:1.

## Câu hỏi cần user chốt trước khi sửa (luật DESIGN.md)
1. Dropdown "Everyone" ở màn Camera: ẩn đi, hay dùng làm người nhận mặc định? (mục 4)
2. Icon Photo Library ở camera: đổi thành nút mở Feed, hay làm photo picker thật? (mục 5)
3. Badge chưa đọc: thống nhất **đỏ** hay **vàng**? (mục 6)
4. Footer có thêm nhãn chữ dưới icon không? (mục 6)
5. Nút Sign In: theo DESIGN.md (trắng) hay giữ vàng hiện tại? (mục 10)

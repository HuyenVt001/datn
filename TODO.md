# TODO.md — Lộ trình hoàn thiện Snapget (DATN)

> Bản đồ việc còn lại, đối chiếu từ 3 tài liệu thiết kế (`Snapget - Tổng quát.pdf`, `Phân tích thiết kế app.pdf`, `thiết kế các lớp thực thể trong database.pdf`) với code hiện có (audit 2026-07-13).
> Trạng thái: ✅ xong · 🔄 đang làm · ⬜ chưa làm. Chi tiết từng module xem `server/GUIDE.md`, `Snapget/.claude/GUIDE.md`, `admin/GUIDE.md`.

---

## Quyết định đã chốt (2026-07-13 — hỏi user, không tự đổi)

| Chủ đề | Quyết định |
|---|---|
| AI sinh quest | **Tạm bỏ qua AI.** Chỉ có **2 quest cố định mỗi ngày**: (1) đăng nhập vào app, (2) đăng 1 ảnh/video bất kỳ. Hoàn thành **tự động** phía server, không cần nộp ảnh xác minh. |
| Sinh quest hằng ngày | **Lazy on-demand**: request đầu tiên trong ngày (`GET /quests/today`) tự tạo quest của ngày. Không cron. |
| Thưởng khung ảnh | Hoàn thành **2/2 quest trong ngày** → mở khóa ngẫu nhiên 1 khung chưa có. **Streak cá nhân đạt mốc 3/7/14/30** ngày → mở khung của mốc đó. Hết khung thì thôi. |
| Khung ảnh ban đầu | Generate sẵn vài khung mẫu (seed). Về sau admin tự thêm/sửa/xóa qua trang **Quản lý khung ảnh** (server cần thêm PATCH /frames/:id). |
| Màn chọn khung + filter | Hiện **sau khi chụp/quay xong**, giao diện giống `Snapget/Sources/assets/submit_photo_screen.png` nhưng: nút **Tiếp** thay nút đăng, picker khung + filter nằm **dưới ảnh**, **không có ô caption**. Bấm Tiếp → sang màn SubmitPhoto (caption + gửi) như cũ. |
| Filter camera | Có làm (bộ filter màu áp sau khi chụp, cùng màn chọn khung). |
| Không làm (user đã loại) | Đăng nhập bằng username; reply bài đăng. |
| Admin web | React + Vite + TS + Ant Design. **UI tiếng Việt, theme sáng**, accent màu brand. |
| Admin đầu tiên | Seed custom claim `admin=true` cho **viethoang5301314@gmail.com** (script `npm run seed:admin -- <email>` trong server). |
| Entry màn Daily Quest | **Nút trên top bar của PostScreen** (màn feed chính). |
| Link mời kết bạn *(chốt 2026-07-19)* | Domain thật **`https://snapget-d8693.web.app/invite/{code}`** (Firebase Hosting, App Links verify) + scheme dự phòng `snapget://invite/{code}`. **Mỗi user 1 link, hiệu lực cố định 30 ngày** — trong hạn ai có link đều gửi lời mời được (không giới hạn lượt); hết hạn server tự sinh mã mới, link cũ vô hiệu. **Kết bạn 2 BƯỚC**: người bấm link thấy dialog (tên + avatar chủ link) → bấm "Gửi lời mời" tạo PENDING → **CHỦ LINK chấp nhận hoặc từ chối** (section 💌 trong sheet bạn bè, có FCM báo) thì mới thành bạn. Ngoại lệ: 2 bên cùng mời nhau → thành bạn luôn. Từ chối = xóa im lặng, người gửi có thể mời lại. |

---

## Phase 1 — Admin web (API server đã có sẵn ~90%)

### Server (bổ trợ)
- ✅ `PATCH /frames/:id` — sửa khung (admin cần thêm/**sửa**/xóa). *(2026-07-13)*
- ✅ `POST /upload/admin` — admin JWT upload ảnh khung. *(2026-07-13)*
- ✅ `GET /frames/admin` — admin xem catalog (route `GET /frames` cũ chỉ nhận Firebase token). *(2026-07-13)*
- ✅ Script `npm run seed:admin -- <email>` (mặc định viethoang5301314@gmail.com). *(2026-07-13)*
- ✅ CORS đã sẵn `http://localhost:5173` trong `.env`.

### Admin web (`admin/`)
- ✅ Scaffold Vite 5 + React 18 + TS + AntD 5 (UI tiếng Việt, theme sáng, accent #8C6239). *(2026-07-13)*
- ✅ Đăng nhập 2 bước: Firebase web SDK → `POST /auth/admin/login` → JWT localStorage + interceptor, 401 tự logout.
- ✅ AdminLayout (sidebar 3 mục + header đăng xuất) + RequireAuth.
- ✅ **Dashboard**: 6 thẻ thống kê từ `GET /admin/stats` (card quest chờ Phase 2).
- ✅ **Quản lý người dùng**: bảng + tìm kiếm + phân trang, khóa/mở khóa, cấp quyền admin.
- ✅ **Quản lý khung ảnh**: grid + thêm (upload) / sửa / xóa / cấp khung cho user.
- ✅ `admin/.claude/CLAUDE.md` + `admin/GUIDE.md`.
- ⬜ **Test end-to-end** — chờ user cung cấp `server/firebase-service-account.json` (máy này chưa có, `.env` server đang trỏ nhầm email thay vì file) + tài khoản viethoang5301314@gmail.com đã đăng ký trong app.

## Phase 2 — Daily Quest (không AI)

### Server (`quests`) — ✅ XONG 2026-07-13 (47/47 test pass)
- ✅ Entity + repository: `dailyQuests/{date}_{type}`, `userQuests/{date}_{uid}_{type}` (id cố định, idempotent).
- ✅ `GET /quests/today` — lazy tạo 2 quest của ngày, trả kèm trạng thái + `rewardFrameId`.
- ✅ Auto-complete: LOGIN hoàn thành khi gọi `GET /quests/today` (app gọi lúc mở PostScreen); POST_MOMENT hook trong `MomentsService.create`.
- ✅ Thưởng: 2/2 trong ngày → khung ngẫu nhiên chưa sở hữu (1 lần/ngày); mốc streak 3/7/14/30 → khung có field `milestone` (đặt khi admin tạo khung).
- ✅ Thống kê `questCompletionsToday` trong `GET /admin/stats` + thẻ trên Dashboard admin.

### App — ✅ XONG 2026-07-13 (assembleDebug OK)
- ✅ `QuestApi` + `FrameApi` + `QuestRepository` + `QuestViewModel`.
- ✅ Màn `DailyQuestScreen` (feature/quest): banner streak, 2 quest + trạng thái, banner khung vừa thưởng, **bộ sưu tập khung** (khóa = mờ + 🔒, mốc = nhãn 🔥) — bộ sưu tập đặt ở màn quest thay vì Profile.
- ✅ Nút cúp 🏆 (vàng gold) trên top bar `PostScreen` → route `daily_quest`.
- ⬜ Test thật trên emulator (cần server chạy + service account).

### Seed khung — ✅ XONG 2026-07-13
- ✅ 8 khung PNG 1080×1080 nền trong suốt trong `server/assets/frames/` (4 thưởng quest + 4 mốc streak) + `npm run seed:frames` (cần service account + Cloudinary khi chạy thật).

## Phase 3 — Hoàn thiện camera — ✅ XONG 2026-07-13 (assembleDebug OK)

- ✅ Quay video ≤5s: CameraX `VideoCapture` (giữ nút chụp để quay), đồng hồ đếm + vòng tiến độ, **tự dừng ở 5s**, upload `contentType=VIDEO` (server enforce ≤5s từ metadata).
- ✅ Phát video trong feed/post detail (Media3 ExoPlayer, lặp lại, chạm để dừng); ô video trong grid = nền đen + icon ▶.
- ✅ Màn **chọn khung + filter** sau khi chụp/quay (`EditMediaScreen`): filter ColorMatrix + overlay khung, nút Tiếp → SubmitPhoto; filter/doodle **bake** vào ảnh, khung gửi kèm `frameId` (video chỉ chọn khung).
- ✅ **Doodle**: vẽ tay lên ảnh — bảng 7 màu + **3 mức độ dày nét** + undo + xóa hết, bake vào ảnh upload.
- ✅ Feed hiển thị khung (`frameId`) đè lên ảnh (PostGrid + PostDetail, map từ `GET /frames`).

## Phase 4 — Chụp chung (Co-op Capture) — ✅ XONG 2026-07-13 (9/9 test coop pass)

- ✅ Server: `POST /moments/coop` (lời mời + nửa ảnh, chỉ bạn bè ACCEPTED) → FCM `COOP_INVITE` → `POST /moments/coop/:id/accept` (nộp nửa còn lại) → **ghép 2 ảnh** (sharp, 540×1080 mỗi nửa, trái = người mời) thành 1 moment tác giả = người mời + `coopUserId` = người nhận (cả 2 được streak/quest/friend-interaction) → FCM `COOP_DONE`; `GET /moments/coop/pending`; **từ chối** (decline) + **hết hạn 24h** (không hiện + không trả lời được).
- ✅ App: toggle "Chụp chung" trong CameraScreen → `CoopSendScreen` (chọn bạn gửi lời mời); banner vàng trên feed → `CoopAcceptScreen` (chụp nửa còn lại, ✓ ghép / ✕ từ chối).

## Phase 5 — Các phần dở dang nhỏ — ✅ XONG 2026-07-13 (assembleDebug OK)

- ✅ **Deep link mời kết bạn**: intent-filter `https://snapget.app/invite/*`, xử lý cả cold start (`onCreate`) lẫn `onNewIntent` → `POST /friendships/connect` + Toast; nút "Share your link" mở share chooser hệ thống với link thật. ⚠️ Domain không có thật → Android 12+ mở browser thay vì app (không verify App Links được) → **xử lý ở Phase 6**.
- ✅ **Sửa hồ sơ**: nút ✏️ trên profile của mình → dialog đổi tên (≤30 ký tự) + chọn avatar từ thư viện → upload → `PATCH /users/me`.
- ✅ **Chat nhóm**: nút tạo nhóm trên màn Messages (tick chọn bạn, ≤20), section "Nhóm chat", `GroupChatScreen` polling 5s.
- ✅ **Tin nhắn voice/sticker/ảnh**: ghi âm MediaRecorder → gửi VOICE (bubble phát MediaPlayer), khay sticker Twemoji → STICKER, chọn ảnh thư viện → PHOTO — cho cả 1-1 lẫn nhóm.
- ✅ **Wire mark-seen feed**: `PostScreen` tự gọi `markSeen` khi moment hiện trên feed (idempotent theo phiên).

## Ngoài lộ trình (yêu cầu thêm 2026-07-13)

- ✅ **Xóa Locket Gold**: bỏ nút "Get Locket Gold" + setting "Restore Purchases" — toàn bộ tính năng miễn phí, không có thanh toán.
- ✅ **Dọn god-object**: PostScreen đọc 100% qua API (Everyone/You/bạn = feed/mine/user/:uid); `MainViewModel` + `FirestoreRepository` chỉ còn currentUser + settings; xóa route `detail`/`post_detail` legacy + nhánh deep link `auth` chết; README viết lại.

## Phase 6 — Hoàn thiện link mời kết bạn (chốt 2026-07-19)

> Theo quyết định trong bảng đầu file: domain thật + scheme fallback, TTL 30 ngày, dialog xác nhận.

### Server (`friendships` + `users`) — ✅ XONG 2026-07-19 (77/77 test pass, build + lint sạch)
- ✅ **Kết bạn 2 bước (yêu cầu bổ sung 2026-07-19)**: `connect` giờ TẠO LỜI MỜI `status=PENDING` + `requesterUid` (transaction check cặp + limit 20 hai phía; PENDING ngược chiều → tự ACCEPTED); `GET /friendships/requests` (lời mời chờ mình xác nhận, kèm profile người gửi); `POST /friendships/requests/:uid/accept` (transaction check LẠI limit 20) / `.../decline` (xóa im lặng); FCM báo chủ link khi có lời mời + báo người gửi khi được chấp nhận. Guard messages/moments/coop đều check `status === 'ACCEPTED'` nên PENDING không lộ quyền bạn bè.
- ✅ **TTL 30 ngày**: hằng `INVITE_LINK_TTL_DAYS = 30` + `INVITE_LINK_BASE_URL`; user thêm `inviteCodeExpiresAt`; `getOrCreateInviteCode` tự sinh mã mới khi chưa có/hết hạn (mã cũ chưa có hạn = coi như hết hạn); `GET /friendships/invite-link` trả thêm `expiresAt` + link theo domain mới; connect/invite-info từ chối mã hết hạn ("Link mời đã hết hạn…").
- ✅ **`GET /friendships/invite-info/:code`** — trả tên + avatar người mời + hạn link (app hiện dialog xác nhận trước khi connect).
- ✅ **Chống race giới hạn 20 bạn**: `FriendshipsRepository.createIfUnderLimit` chạy trong Firestore transaction (kiểm tra cặp + đếm bạn 2 phía + tạo doc atomic); xóa `assertUnderLimit`/`countAccepted`/`create` cũ.
- ✅ Cập nhật unit test `friendships` (9 test connect/invite) + `users` (3 test TTL) + lint.

### Hosting (`hosting/` — Firebase Hosting của project snapget-d8693) — ✅ ĐÃ DEPLOY 2026-07-19
- ✅ Landing page `/invite/*` (nút "Mở app Snapget" qua `intent://`→`snapget://`, hướng dẫn cài app) + `.well-known/assetlinks.json` (package `com.example.snapget`, SHA-256 debug keystore máy này) + `firebase.json`/`.firebaserc`.
- ✅ **Đã deploy** bằng service account (`GOOGLE_APPLICATION_CREDENTIALS` → `firebase deploy --only hosting`) — https://snapget-d8693.web.app live, assetlinks.json trả 200 application/json. Deploy lại sau này: cùng lệnh trong `hosting/`. Lưu ý: 2 máy test phải cài APK debug build từ máy này (App Links verify theo SHA-256 keystore).

### App — ✅ XONG 2026-07-19 (spotless + assembleDebug OK)
- ✅ **Kết bạn 2 bước**: dialog đổi thành "Gửi lời mời kết bạn"; toast phân biệt "Đã gửi lời mời — chờ X xác nhận" (PENDING) vs "Kết bạn thành công" (2 bên cùng mời); section **💌 Lời mời kết bạn** trong sheet bạn bè (avatar + tên + nút ✓ chấp nhận / ✕ từ chối, `GET /friendships/requests` tải khi mở sheet).
- ✅ Manifest: intent-filter `android:autoVerify="true"` cho host `snapget-d8693.web.app` + intent-filter scheme `snapget://invite`; bỏ host giả `snapget.app`.
- ✅ **pendingInviteCode**: bấm link lúc chưa đăng nhập → `PendingInviteStore` (SharedPreferences) lưu mã → đăng nhập xong tự hiện dialog xác nhận (không bắt bấm lại link).
- ✅ **Dialog xác nhận kết bạn** `InviteConfirmDialog` (avatar + tên người mời từ `invite-info` + hạn link) dùng chung cho deep link (overlay `SnapgetApp`, VM scope Activity) **và** quét QR; `AddFriendQrDialog` hiện "Có hiệu lực đến dd/MM/yyyy".
- ✅ Build `assembleDebug` + `spotlessApply`.

### Docs
- ✅ Cập nhật `server/GUIDE.md` + `Snapget/.claude/GUIDE.md` + `codegraph init` 2 thư mục.

### Còn lại của Phase 6 (user test)
- ⬜ Test 2 máy: A mở sheet bạn bè → "Share your link" → gửi link qua Zalo/Messenger; B bấm link → dialog "Kết bạn với A?" → **Gửi lời mời** → A nhận FCM + thấy section 💌 trong sheet bạn bè → **Chấp nhận** (hoặc Từ chối) → hai bên thành bạn. Thử thêm: B chưa đăng nhập (mã được lưu, login xong tự hiện dialog), B chưa cài app (link mở landing page), quét QR (cũng ra dialog gửi lời mời), 2 bên cùng mời nhau (thành bạn luôn không cần xác nhận).

## Đợt sửa lỗi QA 2026-07-27 (feedback test máy thật của user) — ✅ XONG (build + spotless OK, server 111/111 test)

- ✅ **Nhắn tin**: thông báo lời mời kết bạn (đăng ký lại FCM token mỗi lần mở app + onNewToken đi qua API + banner 💌 trên feed); bubble người gửi hết lệch (bỏ Spacer 40dp); **thả react tin nhắn** (long-press → picker; server mới `POST /messages/:id/reactions`, toggle); **bấm ảnh trong chat → xem full ảnh** (pinch-zoom, không crop vuông); bàn phím hết che ô nhập (imePadding ChatScreen + GroupChatScreen).
- ✅ **Reply bài đăng**: tin nhắn reply **gửi kèm ảnh/video bài đăng** (server thêm `attachmentUrl/attachmentType` trên message; bubble hiện media + text, video có nút ▶); bàn phím mở chỉ ô text nổi lên, không kéo theo hàng nút chụp.
- ✅ **Coop**: người được mời chụp được lại (nút center CoopAcceptScreen = nút chụp khi chưa có ảnh — nút chụp trong preview đã bị xóa từ 2026-07-26).
- ✅ **Camera**: xóa icon "N" (toggle wide-mode); ảnh camera trước tự lật ngang khớp preview.
- ✅ **Profile**: avatar đồng bộ ở màn xem ảnh từ lịch (PostDetailScreen tự fetchCurrentUser).
- ✅ **Giao diện Sáng**: icon profile top bar + MessageScreen + thanh nhập chat đổi theo theme (hết trắng-trên-nền-trắng); các chỗ trắng còn lại đều nằm trên overlay/nền tối cố định nên giữ.
- ⬜ User test lại trên máy thật: react/zoom ảnh chat, reply kèm ảnh, coop chụp, selfie mirror, push lời mời kết bạn, light theme.

## Bổ sung 2026-07-28: REPLY tin nhắn trong chat (kiểu Messenger) — ✅ XONG (build OK, server 17 test messages pass)

- ✅ Nhấn giữ tin nhắn (1-1 + nhóm) → hàng icon **😊 | ↩** hiện bên cạnh bubble (khớp ảnh mẫu user): 😊 = thả reaction, ↩ = reply; chạm tin để ẩn.
- ✅ Thanh "Replying to X" (+ preview, nút ✕ hủy) trên ô nhập; tin gửi đi hiện **khối trích dẫn tin gốc** phía trên bubble (text mờ / thumbnail ảnh-sticker, bubble đè nhẹ lên như Messenger).
- ✅ Server: `POST /messages` nhận `replyToId`, validate tin gốc **cùng hội thoại/nhóm**, snapshot type/content/sender của tin gốc vào tin mới.
- ⬜ User test 2 máy: reply text/ảnh/voice trong chat 1-1 và nhóm.

## Còn lại duy nhất: TEST CHẠY THẬT (user tự tổng test)

- ⬜ Chuẩn bị `server/firebase-service-account.json` + `.env` (xem `admin/GUIDE.md` mục 5) → `npm run seed:admin` + `npm run seed:frames` → chạy server.
- ⬜ Tổng test: admin web (đăng nhập → user/frame/stats), app trên emulator/2 máy (đăng bài ảnh+video, khung/filter/doodle, quest, co-op, chat 1-1/nhóm + voice/sticker/ảnh, deep link, sửa hồ sơ, streak).

---

> Làm xong mục nào → đổi ⬜ thành ✅ tại đây **và** cập nhật GUIDE.md của phần tương ứng. Quy tắc chung xem `.claude/CLAUDE.md` của từng phần (Snapget / server / admin).

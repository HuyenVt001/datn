# 🗺️ GUIDE.md — Bản đồ Snapget Server (NestJS)

> Bản đồ **sống** của server: đọc trước khi sửa, **cập nhật sau MỖI lần sửa code** (cây thư mục + task + tiến độ).
> Luật/quy ước đầy đủ ở `.claude/CLAUDE.md`. File này = "đang có gì, ở đâu, làm tới đâu".
> Cập nhật lần cuối: 2026-07-26 — 2 đợt trong ngày:
> (1) ĐẠI TU TRANG ADMIN: role admin hoàn thiện (cột admin + revoke-admin + chặn tự khóa/tự thu quyền + guard re-check MỖI request + login trả uid); khung ảnh 6 điều kiện mở khóa `unlockType` wire tự mở vào moments/friendships/coop; `GET /frames/:id/owners`; `GET /admin/stats/daily`.
> (3) ĐỢT HOÀN THIỆN HỆ THỐNG (107 unit + 7 e2e pass): **kiểm duyệt bài đăng** `GET /admin/moments` + `DELETE /admin/moments/:id`; **audit log** module `audit/` (collection `adminLogs`, ghi best-effort mọi hành động admin: khóa/mở user, cấp/thu quyền, CRUD/grant khung, xóa bài) + `GET /admin/logs`; **sync displayName/photoURL lên Firebase Auth** khi PATCH /users/me (hết lệch tên Auth↔Firestore); **helmet** (CSP off cho Swagger) + **@nestjs/throttler** (120 req/60s toàn cục, login admin siết 10/60s); **UsersService.pushToUids** = helper FCM duy nhất (gộp 4 bản sao ở moments/messages/coop/friendships — các service này bỏ dependency FirebaseService); **e2e smoke** `test/app.e2e-spec.ts` (supertest boot AppModule thật: envelope/validation/guard/404 — không cần emulator; hướng dẫn mở rộng bằng `firebase emulators:exec` trong comment đầu file).
> (2) ĐỢT VÁ LỖI SAU REVIEW (101 test pass): **ensureUser** không ghi `avatar: undefined` (tài khoản email/password hết 500 ở GET /users/me) + backfill doc STUB/prototype (giữ unlockedFrames, không ghi đè tên cũ); **revokeAdmin** post-check chống race 2 admin thu quyền lẫn nhau (về 0 admin thì tự khôi phục claim); **listUsers** search theo fullName Firestore (tên đang hiển thị) — repo đổi `getFullNames`→`getAllFullNames`; **createGroup** chỉ cho thêm bạn bè; **seen/reaction** chỉ người thấy được bài trên feed (chủ bài/coop partner/bạn của 1 trong 2) — `GET :id/reactions` giờ cần biết người gọi; disabled uid lạ trả 404; coop ghi momentId best-effort; declineRequest chạy transaction; xóa moment chunk batch 450; chặn birthday tương lai; dev-streak.ts theo unlockType + guard catalog rỗng.

---

## 0. Trạng thái tổng thể

| | |
|---|---|
| Giai đoạn | 🟢 **Server hoàn chỉnh TẤT CẢ domain** — co-op capture (Phase 4) đã xong 2026-07-13 |
| Tiến độ tổng | **Hạ tầng 100%** · **Domain 100%** (users ✅, friendships ✅, upload ✅, moments ✅, coop ✅, messages ✅, frames ✅, admin ✅, quests ✅) |
| Đã verify | `npm run build` sạch · **8/8 suite unit test pass** (54 test, kèm coop.service.spec, 2026-07-13; script test đã pin `--runInBand`) · server boot OK, map đủ route · Cloudinary OK. ⚠️ Máy này **thiếu `firebase-service-account.json` + `.env`** → chức năng Firebase chưa chạy thật được (xem admin/GUIDE.md mục 5) |
| Việc kế tiếp | Test end-to-end app + server trên emulator/2 máy (co-op, chat nhóm, deep link) |
| Blocker | ✅ Hết |

---

## 1. Server là gì

Cửa ngõ **App/Admin → NestJS → Firebase (Admin SDK) → Firestore/Auth/FCM + Cloudinary**.
Domain lấy từ *Phân tích thiết kế app* + *thiết kế các lớp thực thể trong database*.
Actor: **Member** → (User, Admin) + **AI** (daily quest, làm cuối).

---

## 2. Cây thư mục (ý nghĩa từng phần)

> ✅ = đã có & build được. Thêm file mới → thêm dòng ở đây kèm 1 câu ý nghĩa, cập nhật trạng thái.

```
server/
├── .claude/CLAUDE.md        ✅ ruler (luật + quy ước)
├── GUIDE.md                 ✅ file này (bản đồ + tiến độ)
├── .env / .env.example      ✅ config (.env đã gitignore)
├── firebase-service-account.json  ⬜ 🔒 key Admin (gitignore) — THIẾU trên máy này (tải từ Firebase Console, xem admin/GUIDE.md mục 5)
├── package.json             ✅ deps + scripts npm
├── tsconfig*.json / nest-cli.json / .eslintrc.js / .prettierrc  ✅ toolchain
├── src/
│   ├── main.ts              ✅ bootstrap: prefix /api, CORS, ValidationPipe, envelope, filter, Swagger /docs (Bearer: firebase + admin)
│   ├── app.module.ts        ✅ root: import hạ tầng + 7 domain module
│   ├── app.controller.ts    ✅ GET /api/health (public)
│   ├── config/
│   │   ├── config.module.ts     ✅ @Global ConfigModule
│   │   └── env.validation.ts    ✅ Joi validate .env (fail-fast)
│   ├── firebase/
│   │   ├── firebase.module.ts   ✅ @Global
│   │   └── firebase.service.ts  ✅ Admin SDK: auth()/firestore()/messaging(); boot OK cả khi thiếu key (warn)
│   ├── common/
│   │   ├── constants.ts         ✅ MAX_FRIENDS=20, MAX_GROUP_SIZE=20, MAX_VIDEO_SECONDS=5, STREAK_WINDOW_HOURS=24, DAILY_QUESTS_PER_DAY=3, Collections, Roles
│   │   ├── decorators/          ✅ @Public, @Roles, @CurrentUser (AuthUser)
│   │   ├── dto/                 ✅ ApiResponseDto (envelope), PaginationDto + PaginatedResult
│   │   ├── filters/             ✅ AllExceptionsFilter (envelope lỗi; 2026-07-19: log cả exception KHÔNG phải Error — vd Cloudinary SDK reject plain object, trước đây bị nuốt im lặng)
│   │   ├── interceptors/        ✅ ResponseInterceptor (envelope thành công)
│   │   └── guards/              ✅ FirebaseAuthGuard, AdminJwtGuard, RolesGuard
│   ├── auth/                    ✅ POST /api/auth/admin/login (verify Firebase token → check claim admin HIỆN HÀNH qua getUser + check disabled → phát JWT, trả kèm uid). Export JwtModule. **AdminJwtGuard re-check quyền admin + disabled trên Firebase MỖI request** (2026-07-26) — thu quyền/khóa là mất phiên admin ngay, không đợi JWT hết hạn
│   ├── users/                   ✅ me (GET/PATCH), fcm-tokens (POST/DELETE), GET /:uid, ensureUser (tự tạo doc), streak cá nhân, inviteCode (TTL 30 ngày — hết hạn tự sinh mã mới). Có spec. (2026-07-19: fix PATCH /users/me crash "not a valid Firestore document" — UpdateUserDto là class instance, phải chuyển plain object trước khi ghi Firestore) (2026-07-26: thêm field `birthday` yyyy-MM-dd — entity + UpdateUserDto @Matches + service patch + repo toEntity; KHÔNG lộ ra PublicUser; app dùng cho mục Edit Birthday trong Settings)
│   ├── friendships/             ✅ list, invite-link (domain hosting + expiresAt), invite-info/:code, connect = GỬI LỜI MỜI PENDING (transaction chống race limit 20 + từ chối mã hết hạn; mutual → ACCEPTED luôn), requests + accept/decline (chủ link xác nhận, FCM 2 chiều), remove; registerInteraction (friend streak reset 24h). Có spec
│   ├── upload/                  ✅ POST /api/upload (Firebase) + POST /api/upload/admin (Admin JWT — ảnh khung từ trang admin); multipart ≤25MB → Cloudinary (folder snapget/); enforce video ≤5s từ metadata, quá thì xóa asset + báo lỗi
│   ├── moments/                 ✅ đăng bài (wire personal streak + FCM cho bạn bè), feed (mình + bạn, chunked 10), mine + user/:uid (profile — chỉ bạn bè xem được), mark seen (subcol views), reactions (subcol, wire friend streak). Có spec
│   │   └── (coop)               ✅ CO-OP CAPTURE (2026-07-13): coop.controller/service/repository + entities/coop-invite + dto/coop — mời chụp chung (bạn bè ACCEPTED, FCM COOP_INVITE) → accept (tải 2 nửa ảnh, **sharp** ghép side-by-side 1080×1080, upload snapget/coop, tạo 1 moment tác giả = người mời + coopUserId = người nhận, streak/quest/interaction cho CẢ 2, FCM COOP_DONE) / decline. CoopController đứng TRƯỚC MomentsController trong module (tránh nuốt route bởi :id). Có spec (7 test)
│   ├── messages/                ✅ gửi 1-1 (chỉ bạn bè, wire friend streak, FCM) + nhóm ≤20 (member-only, FCM), thread 1-1/nhóm, conversations, markSeen (chỉ người nhận), tạo/list nhóm. Có spec
│   ├── frames/                  ✅ catalog + isUnlocked cho user; admin thêm/sửa/xóa/grant khung; **6 điều kiện mở khóa `unlockType`** (2026-07-26): QUEST_RANDOM (pool thưởng quest) · STREAK_MILESTONE (mốc 3/7/14/30, giữ field legacy `milestone` đồng bộ cho app) · POST_COUNT (đủ N bài) · FRIEND_COUNT (đủ N bạn, mở cả 2 phía) · COOP_FIRST (chụp chung lần đầu, mở cả 2) · DEFAULT (mở sẵn — isUnlocked luôn true); doc cũ chỉ có `milestone` được repo suy ngược tương thích; `unlockByThreshold()`/`unlockCoopFrames()` cho hook tự mở; `GET /frames/:id/owners` (admin xem user sở hữu). Có spec
│   ├── quests/                  ✅ (2026-07-13, KHÔNG AI) 2 quest cố định/ngày (LOGIN + POST_MOMENT), lazy tạo khi có request đầu tiên; GET /quests/today tự hoàn thành LOGIN; MomentsService gọi registerMomentPosted → hoàn thành POST_MOMENT; thưởng 2/2 quest → khung ngẫu nhiên (1 lần/ngày, đánh dấu doc DAILY_REWARD), mốc streak 3/7/14/30 → khung có field `milestone`. Có spec
│   ├── audit/                   ✅ (2026-07-26) audit log: AuditService.log (best-effort) + AuditRepository (collection adminLogs) — AdminModule/FramesModule dùng chung, không vòng lặp DI
│   └── admin/                   ✅ list/search user (Auth + enrich Firestore, kèm cờ `admin` + `lastSignInAt`), stats (count aggregate) + **stats/daily** (moment + user mới theo ngày cho biểu đồ), khóa/mở user (chặn TỰ khóa; khóa = thu hồi luôn refresh token), grant-admin + **revoke-admin** (chặn tự thu quyền → hệ thống luôn còn ≥1 admin). Có admin.repository.ts + spec (2026-07-26)
│   └── (spec)               ✅ users, friendships, moments, messages, frames, admin, quests — 47 test pass (2026-07-13)
├── assets/frames/           ✅ 8 khung PNG mẫu (4 thưởng quest + 4 mốc streak 3/7/14/30) + manifest.json cho seed:frames
├── scripts/
│   ├── seed-admin.ts        ✅ `npm run seed:admin -- <email>` — set custom claim admin=true cho admin ĐẦU TIÊN (mặc định viethoang5301314@gmail.com; sau khi seed phải đăng nhập lại để token mang claim)
│   ├── seed-frames.ts       ✅ `npm run seed:frames` — upload assets/frames lên Cloudinary + tạo doc `frames` (bỏ qua trùng tên)
│   └── dev-streak.ts        ✅ `npm run dev:streak -- --email <email> [--streak N | --unlock-all | --lock-all]` — DEV TOOL test mở khung: set streak (lastStreakDate=hôm-qua-UTC → đăng 1 bài = +1 streak, chạm mốc là server tự mở khung), mở/khóa toàn bộ khung, không tham số = xem trạng thái (2026-07-26)
└── test/                    ⬜ chưa có e2e (jest unit config sẵn trong package.json)
```

Ký hiệu: ✅ xong · 🔨 skeleton (khung có, logic TODO) · ⬜ chưa có.
Mỗi domain module: `*.module.ts` + `*.controller.ts` + `*.service.ts` + `*.repository.ts` (nơi duy nhất chạm Firestore) + `dto/` + `entities/`.

---

## 3. Mô hình dữ liệu (thực thể → Firestore)

Chi tiết field ở `.claude/CLAUDE.md` mục 6. Tên collection tập trung ở `src/common/constants.ts`.

| Collection | Vai trò |
|---|---|
| `users/{uid}` | hồ sơ + game (personalStreak, unlockedFrames[], inviteCode + inviteCodeExpiresAt (TTL 30 ngày), fcmTokens[]) |
| `friendships/{pairId}` | quan hệ 2 user, friendStreak, lastInteractionAt, status (PENDING = lời mời chờ chủ link xác nhận), requesterUid (người gửi lời mời) |
| `posts/{id}` (Moment) | bài đăng; `+ /views/{viewerId}`, `+ /reactions/{id}` (subcollection) |
| `messages/{id}` | tin nhắn (receiverId? / groupId?), messageType, isSeen |
| `chatGroups/{id}` | nhóm chat (memberIds[] ≤20) |
| `coopInvites/{id}` | lời mời chụp chung (inviterId, inviteeId, inviterMediaUrl, status PENDING/COMPLETED/DECLINED, momentId?) |
| `frames/{id}` | khung ảnh (admin quản lý): `frameName`, `imageUrl`, `unlockType` + `unlockValue` (điều kiện mở khóa, 2026-07-26), `milestone` (legacy = unlockValue khi STREAK_MILESTONE — app đọc nhãn 🔥) |
| `dailyQuests/{id}` + `userQuests/{id}` | quest + bài nộp (proofUrl, status) |

---

## 4. Bảng task & tiến độ

Ký hiệu: ✅ xong · 🔄 đang làm · ⬜ chưa làm

### Hạ tầng ✅ XONG
| Task | TT |
|---|---|
| Init NestJS (npm, TS strict, ESLint/Prettier) | ✅ |
| `config/` + validate `.env` (Joi) | ✅ |
| `firebase/` FirebaseService (Admin SDK) | ✅ |
| `common/` envelope + exception filter + guards + constants | ✅ |
| `auth/` verify Firebase token (guard app) + JWT admin | ✅ |
| Swagger `/docs` + 2 Bearer (firebase/admin) | ✅ |
| Health check + verify chạy thật | ✅ |

### Domain modules
| Module | Scaffold | CRUD/logic | Business rule | Test | TT |
|---|---|---|---|---|---|
| users | ✅ | ✅ (me GET/PATCH, fcm-tokens, /:uid, ensureUser) | personalStreak (hàm sẵn, chờ moments gọi) | ✅ | ✅ |
| friendships | ✅ | ✅ (list, invite-link, invite-info, connect, requests, accept, decline, remove) | **kết bạn 2 bước** ✅ (connect tạo PENDING → chủ link accept/decline; mutual → ACCEPTED luôn; FCM 2 chiều), limit 20 trong Firestore TRANSACTION ✅ (cả lúc gửi lời mời lẫn lúc accept), mã mời TTL 30 ngày ✅, friend streak reset 24h (hàm sẵn) | ✅ | ✅ |
| moments | ✅ | ✅ (create, feed, mine, user/:uid, **delete (chủ bài)**, seen, reactions) | video≤5s (ở upload) ✅, isSeen ✅, personal+friend streak wired ✅, FCM bạn bè ✅, moment người khác chỉ bạn bè xem ✅, xóa chỉ chủ bài ✅ | ✅ | ✅ |
| messages | ✅ | ✅ (send 1-1/nhóm, threads, conversations, seen, groups) | group≤20 ✅, isSeen ✅, chỉ-bạn-bè ✅, friend streak wired ✅, FCM ✅ | ✅ | ✅ |
| frames | ✅ | ✅ (list+isUnlocked, admin thêm/sửa/xóa, grant, owners) | 6 điều kiện `unlockType` ✅ (2026-07-26): POST_COUNT hook ở moments.create, FRIEND_COUNT hook ở friendships accept/mutual (cả 2 phía), COOP_FIRST hook ở coop.accept (cả 2), DEFAULT mở sẵn, pool thưởng quest = CHỈ QUEST_RANDOM | ✅ | ✅ |
| quests | ✅ | ✅ (today, auto-complete, thưởng) | 2 quest cố định/ngày (KHÔNG AI), lazy, thưởng 2/2 + mốc streak | ✅ | ✅ |
| admin | ✅ | ✅ (list/search kèm role, stats + stats/daily, khóa/mở, grant/revoke-admin) | chặn tự khóa/tự thu quyền ✅ (luôn còn ≥1 admin), guard re-check quyền mỗi request ✅, khóa = revoke refresh token ✅ (2026-07-26) | ✅ | ✅ |
| upload (Cloudinary) | ✅ | ✅ (POST /upload + /upload/admin, uploadBuffer cho ảnh ghép) | video≤5s enforce tại upload ✅ | ⬜ | ✅ |
| coop (chụp chung) | ✅ | ✅ (invite, pending, accept ghép sharp, decline) | chỉ bạn bè ACCEPTED ✅, chỉ invitee trả lời ✅, chỉ PENDING ✅, **hết hạn 24h** ✅ (không hiện trong pending + không trả lời được, `INVITE_TTL_HOURS`), streak/quest cả 2 ✅, FCM ✅ | ✅ (9 test) | ✅ |

> Cặp đôi với app: mỗi module server xong endpoint → app viết lại repository gọi API cho module đó.

---

## 5. Endpoint đã có

| Method | Path | Mô tả | Guard |
|---|---|---|---|
| GET | `/api/health` | Kiểm tra server sống | Public |
| POST | `/api/auth/admin/login` | Admin đổi Firebase token lấy JWT server | Public |
| GET | `/api/users/me` | Lấy hồ sơ của mình (tự tạo doc nếu login lần đầu) | Firebase |
| PATCH | `/api/users/me` | Cập nhật hồ sơ (fullName/avatar/birthday `yyyy-MM-dd`) | Firebase |
| POST | `/api/users/me/fcm-tokens` | Đăng ký FCM token | Firebase |
| DELETE | `/api/users/me/fcm-tokens/:token` | Gỡ FCM token | Firebase |
| GET | `/api/users/:uid` | Xem hồ sơ công khai user khác | Firebase |
| GET | `/api/friendships` | Danh sách bạn bè (kèm friend streak) | Firebase |
| GET | `/api/friendships/invite-link` | Lấy link mời của mình (`https://snapget-d8693.web.app/invite/{code}`, hiệu lực 30 ngày, trả kèm `expiresAt`; hết hạn tự sinh mã mới) | Firebase |
| GET | `/api/friendships/invite-info/:code` | Tên + avatar người mời + hạn link (app hiện dialog xác nhận trước khi connect) | Firebase |
| POST | `/api/friendships/connect` | GỬI LỜI MỜI kết bạn qua inviteCode → PENDING chờ chủ link xác nhận (mutual → ACCEPTED luôn; từ chối mã hết hạn; limit 20 cả 2 phía trong transaction; FCM báo chủ link) | Firebase |
| GET | `/api/friendships/requests` | Lời mời kết bạn đang chờ mình (chủ link) xác nhận, kèm profile người gửi | Firebase |
| POST | `/api/friendships/requests/:requesterUid/accept` | Chấp nhận lời mời → thành bạn (transaction check lại limit 20; FCM báo người gửi) | Firebase |
| POST | `/api/friendships/requests/:requesterUid/decline` | Từ chối lời mời (xóa im lặng — người gửi có thể mời lại) | Firebase |
| DELETE | `/api/friendships/:friendUid` | Xóa bạn bè | Firebase |
| POST | `/api/upload` | Upload ảnh/video (multipart `file`, ≤25MB, video ≤5s) → trả `{url, publicId, resourceType, duration}` | Firebase |
| POST | `/api/upload/admin` | [Admin] Upload ảnh (khung ảnh...) — cùng format với /upload | Admin JWT |
| POST | `/api/moments` | Đăng moment (mediaUrl từ /upload; tăng personal streak; FCM bạn bè) | Firebase |
| GET | `/api/moments/feed` | Feed mình + bạn bè (`?page&limit`) | Firebase |
| GET | `/api/moments/mine` | Moment của chính mình — profile calendar + đếm tổng (`?page&limit`) | Firebase |
| GET | `/api/moments/user/:uid` | Moment của 1 user — CHỈ bạn bè (hoặc chính mình), 403 nếu không | Firebase |
| POST | `/api/moments/coop` | Gửi lời mời chụp chung (`friendUid`, `mediaUrl` nửa ảnh; bạn bè ACCEPTED; FCM) | Firebase |
| GET | `/api/moments/coop/pending` | Lời mời chụp chung đang chờ mình (kèm tên/avatar người mời) | Firebase |
| POST | `/api/moments/coop/:id/accept` | Nộp nửa ảnh còn lại → server ghép sharp → 1 moment chung cho cả 2 | Firebase |
| POST | `/api/moments/coop/:id/decline` | Từ chối lời mời | Firebase |
| DELETE | `/api/moments/:id` | Xóa moment — **CHỈ chủ bài** (403 nếu không); xóa cả subcollection views/reactions (mới 2026-07-26, phục vụ menu ⋯ trên app) | Firebase |
| POST | `/api/moments/:id/seen` | Đánh dấu đã xem (chỉ người thấy được bài — 403 với người lạ, 2026-07-26) | Firebase |
| POST | `/api/moments/:id/reactions` | Thả emoji (chỉ người thấy được bài; cập nhật friend streak với chủ bài) | Firebase |
| GET | `/api/moments/:id/reactions` | Danh sách reaction (chỉ người thấy được bài) | Firebase |
| POST | `/api/messages` | Gửi tin 1-1 (`receiverId`, chỉ bạn bè, +streak) hoặc nhóm (`groupId`) | Firebase |
| GET | `/api/messages/conversations` | Danh sách hội thoại 1-1 (tin mới nhất từng người) | Firebase |
| GET | `/api/messages/with/:friendUid` | Thread 1-1 (`?page&limit`, page 1 = mới nhất) | Firebase |
| PATCH | `/api/messages/:id/seen` | Đánh dấu đã xem (chỉ người nhận) | Firebase |
| POST | `/api/messages/groups` | Tạo nhóm chat (≤20 thành viên) | Firebase |
| GET | `/api/messages/groups` | Danh sách nhóm của mình | Firebase |
| GET | `/api/messages/groups/:groupId` | Thread nhóm (member-only) | Firebase |
| GET | `/api/quests/today` | 2 quest hôm nay + trạng thái (lazy tạo; gọi = tự hoàn thành quest LOGIN; `rewardFrameId` nếu vừa được thưởng) | Firebase |
| GET | `/api/frames` | Catalog khung + `isUnlocked` của mình (khung DEFAULT luôn mở; trả kèm `unlockType`/`unlockValue`) | Firebase |
| GET | `/api/frames/admin` | [Admin] Toàn bộ catalog khung (cho trang quản lý) | Admin JWT |
| GET | `/api/frames/:id/owners` | [Admin] Danh sách user đang sở hữu khung (`{frame, owners[]}`) | Admin JWT |
| POST | `/api/frames` | [Admin] Thêm khung (`frameName/imageUrl/unlockType/unlockValue`) | Admin JWT |
| PATCH | `/api/frames/:id` | [Admin] Sửa khung (tên/ảnh/điều kiện mở khóa; đổi loại cần gửi kèm ngưỡng mới) | Admin JWT |
| DELETE | `/api/frames/:id` | [Admin] Xóa khung | Admin JWT |
| POST | `/api/frames/:id/grant/:uid` | [Admin] Mở khóa khung cho user (demo thưởng) | Admin JWT |
| GET | `/api/admin/moments` | [Admin] Danh sách bài đăng mới nhất (kiểm duyệt; kèm tên tác giả) | Admin JWT |
| DELETE | `/api/admin/moments/:id` | [Admin] Xóa bài vi phạm (kèm subcollection; ghi audit log) | Admin JWT |
| GET | `/api/admin/logs` | [Admin] Nhật ký hành động admin (audit log, `?page&limit`) | Admin JWT |
| GET | `/api/admin/users` | Danh sách user (`?search&page&limit`, tìm email/tên; kèm `admin`, `lastSignInAt`) | Admin JWT |
| GET | `/api/admin/stats` | Thống kê (users/moments/messages/friendships/groups + momentsToday + questCompletionsToday) | Admin JWT |
| GET | `/api/admin/stats/daily` | Thống kê theo ngày (`?days=1..30`, mặc định 7): `[{date, moments, newUsers}]` cho biểu đồ | Admin JWT |
| PATCH | `/api/admin/users/:uid/disabled` | Khóa/mở khóa user (không tự khóa mình; khóa = thu hồi refresh token) | Admin JWT |
| POST | `/api/admin/users/:uid/grant-admin` | Cấp quyền admin (giữ các claim khác) | Admin JWT |
| POST | `/api/admin/users/:uid/revoke-admin` | Thu hồi quyền admin (không tự thu mình; hiệu lực NGAY do guard re-check) | Admin JWT |

Swagger: `http://localhost:3000/docs`.

---

## 5b. Hạn chế đã biết (review 2026-07-13 — chấp nhận ở scale đồ án, KHÔNG phải bug)

- `ResponseInterceptor` nhận diện `{message, data}` bằng duck-typing — endpoint tương lai trả object có 2 field trùng tên sẽ bị bóc nhầm. Fix chuẩn: decorator `@ResponseMessage()`.
- `getConversations` (messages) quét toàn bộ tin gửi + nhận của user để dựng danh sách hội thoại — chấp nhận vì không có composite index; scale lớn thì thêm `conversationId` trên message.
- `countCompletionsByDate` (quests, admin stats) đọc mọi doc userQuests của ngày rồi lọc trong bộ nhớ — có thể chuyển sang Firestore count() aggregation.
- FCM multicast được viết 3 nơi (moments/messages/coop) — nên gom về `FirebaseService.pushToUids()` khi rảnh (đổi payload/prune token phải sửa 3 chỗ).
- **Tin nhắn prototype cũ** (field `recipientId` thời app đọc Firestore trực tiếp) KHÔNG hiện trong thread/hội thoại — query mới lọc theo `receiverId` trên server. Chấp nhận: xóa collection `messages` cũ trước khi test thật (không có data người dùng thật).

## 6. Quyết định đã chốt

- ✅ Quyền admin: **Firebase custom claims `{admin:true}`**.
- ✅ **Role 2 cấp user/admin hoàn thiện (chốt 2026-07-26)**: cấp + THU quyền qua trang admin; chặn tự khóa/tự thu quyền chính mình (⇒ hệ thống luôn còn ≥1 admin — không cần check "admin cuối cùng" riêng); `AdminJwtGuard` gọi `getUser()` MỖI request để re-check claim + disabled (đánh đổi 1 read Auth/request — chấp nhận ở scale DATN); login admin cũng check claim hiện hành qua `getUser()` (không tin claim trong ID token cũ).
- ✅ **Điều kiện mở khóa khung (chốt 2026-07-26)**: 6 loại `unlockType` — QUEST_RANDOM / STREAK_MILESTONE (3/7/14/30) / POST_COUNT (N≥1) / FRIEND_COUNT (1..20) / COOP_FIRST / DEFAULT. Ngưỡng N nhập tự do ở trang admin. Doc frames cũ (chỉ có `milestone`) được suy ngược tự động, KHÔNG cần migration. ⚠️ App Android: `GET /frames` thêm field mới (Gson bỏ qua field lạ → không hỏng); khung DEFAULT hiện `isUnlocked=true` sẵn — muốn hiện NHÃN loại điều kiện mới trong bộ sưu tập khung thì cập nhật app (hiện app chỉ hiện nhãn 🔥 theo `milestone`).
- ✅ Admin ĐẦU TIÊN: seed bằng `npm run seed:admin -- <email>` (mặc định **viethoang5301314@gmail.com**) — lối thoát "con gà-quả trứng" vì endpoint grant-admin yêu cầu quyền admin.
- ✅ Daily Quest (chốt 2026-07-13, xem `../TODO.md`): **KHÔNG AI** — 2 quest cố định/ngày (LOGIN + POST_MOMENT), sinh **lazy** khi có request đầu tiên trong ngày, hoàn thành tự động; thưởng: 2/2 quest/ngày → khung ngẫu nhiên, mốc streak 3/7/14/30 → khung mốc.
- ✅ Service account key: env `FIREBASE_SERVICE_ACCOUNT` → `./firebase-service-account.json` (đã có).
- ⏳ Tên collection Moment: tạm **giữ `posts`** (tương thích app cũ) — có thể đổi `moments` sau, sửa `Collections.POSTS`.

---

## 7. Lệnh chạy

```bash
npm install
npm run start:dev     # dev watch
npm run build && npm run start:prod
npm run lint | npm run format
npm run test | npm run test:e2e   # test đã pin --runInBand (máy ít RAM — worker song song crash native)
npm run seed:admin -- <email>   # cấp quyền admin đầu tiên (mặc định viethoang5301314@gmail.com)
npm run seed:frames             # seed 8 khung ảnh mẫu (Cloudinary + Firestore)
```

---

## 8. Cách cập nhật file này (bắt buộc sau mỗi lần sửa code)

1. File/thư mục mới → thêm vào **cây mục 2** kèm ý nghĩa 1 dòng, cập nhật ✅/🔨/⬜.
2. Cập nhật **bảng task mục 4** + **tiến độ tổng mục 0**.
3. Thêm endpoint → ghi **mục 5**. Đổi mô hình dữ liệu → sửa **mục 3** + CLAUDE.md mục 6.
4. Đổi ngày "Cập nhật lần cuối" ở đầu file.

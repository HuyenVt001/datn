# 🗺️ GUIDE.md — Bản đồ Snapget Server (NestJS)

> ## 📌 RULE BẮT BUỘC
> **Luôn cập nhật file GUIDE.md này MỖI KHI có thay đổi** (code, endpoint, data model, luồng nghiệp vụ, thiết kế) — ngay trong cùng lần làm việc, không đợi "xong tính năng". Mỗi lần cập nhật phải ghi rõ **thay đổi gì trong thiết kế**: sửa mục kiến trúc (mục 1) / cây thư mục (mục 2) / data model (mục 3) / bảng task (mục 4) / endpoint (mục 5) tương ứng, **và thêm 1 dòng vào Changelog (mục 9)** mô tả thay đổi. Sửa code mà không cập nhật GUIDE = **chưa xong việc**. Chi tiết cách cập nhật: mục 10.

> ## 🔐 RULE BẮT BUỘC — SECURITY.md
> **Mọi thay đổi liên quan BẢO MẬT phải cập nhật [`../SECURITY.md`](../SECURITY.md) NGAY trong cùng lần sửa** (song song với GUIDE này).
> Tính là thay đổi bảo mật: guard/`@Roles`/`@Public()`, luồng xác thực & vòng đời token, kiểm tra ownership (IDOR), DTO/`ValidationPipe`, CORS · helmet · rate limit · `trust proxy` · security headers · Swagger, giới hạn & loại file upload, biến env/nơi lưu bí mật/xoay khóa, Firestore & Storage Rules, App Check, danh sách hành động ghi audit log.
> Cách cập nhật: sửa đúng mục trong SECURITY.md (đổi trạng thái ✅/⚠️/🔴 + đường dẫn:dòng), gạch việc đã làm khỏi lộ trình mục 14, đổi dòng "Cập nhật lần cuối". Sửa code bảo mật mà không cập nhật SECURITY.md = **chưa xong việc**.

> Bản đồ **sống** của server: đọc trước khi sửa. Luật/quy ước đầy đủ ở `.claude/CLAUDE.md`. File này = "đang có gì, ở đâu, làm tới đâu".
> Cập nhật lần cuối: **2026-08-03**.

---

## 0. Trạng thái tổng thể

| | |
|---|---|
| Giai đoạn | 🟢 **Server hoàn chỉnh TẤT CẢ domain** (users, friendships, upload, moments, coop, messages, frames, quests, admin, audit) |
| Đã verify | `npm run build` + `npm run lint` sạch · unit test 9 suite pass (lần gần nhất 2026-08-02, messages 30 + coop 20 test) · e2e smoke pass (`test/app.e2e-spec.ts`) · Cloudinary OK · service account key **đã có** trên máy (`snapget-d8693-firebase-adminsdk-fbsvc-d08b18f0f5.json`, `.env` trỏ qua `FIREBASE_SERVICE_ACCOUNT`) |
| Deploy | Đã chạy trên Render: `https://datn-8810.onrender.com/api` (gói free ngủ sau 15 phút — gọi `/api/health` để đánh thức trước demo). Hướng dẫn: `../DEPLOY.md` |
| Việc kế tiếp | Test end-to-end app + server (co-op, chat nhóm, reply, deep link) trên 2 máy/emulator |
| Blocker | ✅ Không có |

---

## 1. Kiến trúc hệ thống

### 1.1 Vị trí trong monorepo

Server là **cửa ngõ duy nhất** giữa client và hạ tầng dữ liệu — app/admin KHÔNG chạm Firestore trực tiếp:

```
App Android (Kotlin/Compose) ──Firebase ID token──┐
                                                  ├─► NestJS API (server này, prefix /api)
Admin React (Vite SPA) ────────JWT server─────────┘        │
                                                           ├─► Firebase Admin SDK ─► Firestore (data)
                                                           │                        Auth (user/claims)
                                                           │                        FCM (push)
                                                           ├─► Cloudinary (lưu ảnh/video)
                                                           └─► sharp (ghép ảnh co-op phía server)
```

- **Firebase Auth** vẫn do client SDK đảm nhận việc đăng nhập (app + admin login); server chỉ **verify** token và phát hành/kiểm quyền.
- Domain lấy từ 3 file PDF ở root repo (*Phân tích thiết kế app*, *thiết kế các lớp thực thể trong database*, *Snapget - Tổng quát*). Actor: **Member** → (User, Admin).

### 1.2 Kiến trúc phân tầng trong server

Mỗi domain module tuân thủ đúng 1 chuỗi (bắt buộc, xem CLAUDE.md mục 3):

```
Controller (HTTP + DTO + guard + Swagger)
   └─► Service (business logic, phân quyền chi tiết, wire streak/quest/FCM)
          └─► Repository (NƠI DUY NHẤT chạm Firestore của domain; map doc ↔ entity, Timestamp ↔ ISO)
```

**Vòng đời 1 request** (pipeline global khai báo ở `main.ts` + `app.module.ts`):

```
helmet (security headers, CSP off cho Swagger)
  → CORS (origins từ env CORS_ORIGINS)
  → ThrottlerGuard (120 req/60s toàn cục; /auth/admin/login siết 10/60s)
  → Route guard: FirebaseAuthGuard (app) | AdminJwtGuard + RolesGuard (admin) | @Public()
  → ValidationPipe (whitelist + forbidNonWhitelisted + transform — chặn field thừa)
  → Controller → Service → Repository → Firestore
  → ResponseInterceptor (bọc envelope {success, statusCode, message, data})
  → (nếu lỗi) AllExceptionsFilter (envelope lỗi, message tiếng Việt; log cả exception không phải Error)
```

### 1.3 Hai luồng xác thực (tách biệt hoàn toàn)

| | Luồng App | Luồng Admin |
|---|---|---|
| Client | App Android | Admin React |
| Token gửi lên | **Firebase ID token** (client SDK cấp) | **JWT do server phát** |
| Guard | `FirebaseAuthGuard` — `verifyIdToken()` mỗi request → `req.user = {uid, email}` | `AdminJwtGuard` — verify JWT **+ gọi `getUser()` re-check claim `admin` + `disabled` MỖI request** (thu quyền/khóa hiệu lực ngay) |
| Cấp token | Firebase Auth (login/refresh phía app) | `POST /auth/admin/login`: verify Firebase token → check claim admin HIỆN HÀNH (`getUser()`, không tin claim trong token cũ) + check disabled → phát JWT (`@nestjs/jwt`), trả kèm `uid` |
| Quyền admin | — | Firebase **custom claim `{admin: true}`**; admin đầu tiên seed bằng `npm run seed:admin` |

### 1.4 Bản đồ module & phụ thuộc

| Module | Trách nhiệm | Phụ thuộc đáng chú ý |
|---|---|---|
| `config` (@Global) | Nạp + validate `.env` bằng Joi (fail-fast) | — |
| `firebase` (@Global) | Khởi tạo Admin SDK; expose `auth()/firestore()/messaging()` | boot được cả khi thiếu key (warn) |
| `common` | Envelope, filter, interceptor, guards, decorators, constants (MAX_FRIENDS=20, MAX_GROUP_SIZE=20, MAX_VIDEO_SECONDS=3 (ảnh GIF)…) | dùng chung, không business logic |
| `auth` | Login admin, phát JWT; export JwtModule cho AdminJwtGuard | firebase |
| `users` | Hồ sơ, fcm-tokens, personal streak, inviteCode TTL 30 ngày, **`pushToUids()` = helper FCM DUY NHẤT toàn server** | moments/messages/coop/friendships gọi qua UsersService |
| `friendships` | Kết bạn 2 bước qua invite link (PENDING → accept/decline), limit 20 trong transaction, friend streak reset 24h | users (FCM, profile) |
| `upload` | Multipart ≤25MB → Cloudinary; enforce ảnh GIF ≤3s từ metadata; route riêng cho admin | cloudinary |
| `moments` | Đăng bài, feed (mình + bạn), seen/reactions (subcollection), xóa (chủ bài); wire personal+friend streak, quest, FCM; **kèm coop (redesign 2026-08-02)**: mời (TTL 5 phút, không kèm ảnh) → accept → 2 bên nộp nửa ảnh → sharp ghép 1080×1080 → `mergedMediaUrl` (mỗi người tự đăng bài với ảnh ghép) | users, friendships, frames, upload |
| `messages` | Chat 1-1 (chỉ bạn bè) + nhóm ≤20, reaction (toggle emoji), **reply tin nhắn (snapshot tin gốc)**, attachment (reply bài đăng), conversations, markSeen | users, friendships |
| `frames` | Catalog khung + 6 điều kiện mở khóa `unlockType`; hook tự mở đặt trong moments/friendships/coop; CRUD + grant + owners cho admin | users, audit |
| `quests` | 2 quest cố định/ngày (LOGIN + POST_MOMENT), lazy tạo, tự hoàn thành; thưởng khung (2/2 quest + mốc streak 3/7/14/30) | frames, users |
| `admin` | List/search user, stats (+ daily), khóa/mở, grant/revoke admin, kiểm duyệt bài đăng, đọc audit log | users, moments, audit |
| `audit` | `AuditService.log()` best-effort → collection `adminLogs` (mọi hành động admin) | dùng bởi admin + frames, không vòng lặp DI |

### 1.5 Triển khai

- **Local dev**: `npm run start:dev` → `http://localhost:3000/api`, Swagger `/docs` (2 Bearer scheme: firebase + admin).
- **Production**: Render (`datn-8810.onrender.com`), env cấu hình trên dashboard, service account qua Secret File; admin SPA chạy trên Firebase Hosting site riêng → nhớ thêm origin vào `CORS_ORIGINS`. Chi tiết `../DEPLOY.md`.

---

## 2. Cây thư mục (ý nghĩa từng phần)

> ✅ = đã có & build được. Thêm file mới → thêm dòng ở đây kèm 1 câu ý nghĩa.

```
server/
├── .claude/CLAUDE.md        ✅ ruler (luật + quy ước)
├── GUIDE.md                 ✅ file này (bản đồ + tiến độ)
├── .env / .env.example      ✅ config (.env đã gitignore)
├── snapget-d8693-firebase-adminsdk-*.json  ✅ 🔒 key Admin SDK (gitignore, .env trỏ qua FIREBASE_SERVICE_ACCOUNT)
├── package.json             ✅ deps + scripts npm
├── tsconfig*.json / nest-cli.json / .eslintrc.js / .prettierrc  ✅ toolchain
├── src/
│   ├── main.ts              ✅ bootstrap: helmet, prefix /api, CORS, ValidationPipe, envelope, filter, Swagger /docs
│   ├── app.module.ts        ✅ root: hạ tầng + throttler + domain modules
│   ├── app.controller.ts    ✅ GET /api/health (public)
│   ├── config/              ✅ @Global ConfigModule + Joi validate .env (fail-fast)
│   ├── firebase/            ✅ @Global FirebaseService: auth()/firestore()/messaging()
│   ├── common/              ✅ constants, decorators (@Public/@Roles/@CurrentUser), dto (envelope + pagination),
│   │                           filters (AllExceptionsFilter), interceptors (ResponseInterceptor),
│   │                           guards (FirebaseAuthGuard, AdminJwtGuard, RolesGuard)
│   ├── auth/                ✅ POST /auth/admin/login (verify Firebase → check claim admin hiện hành + disabled → JWT + uid)
│   ├── users/               ✅ me (GET/PATCH kèm birthday, sync displayName/photoURL lên Auth), fcm-tokens, /:uid,
│   │                           ensureUser (tự tạo/backfill doc), personal streak, inviteCode TTL 30 ngày, pushToUids (FCM helper)
│   ├── friendships/         ✅ list, invite-link/-info, connect (PENDING, transaction limit 20), requests accept/decline, remove,
│   │                           registerInteraction (friend streak 24h)
│   ├── upload/              ✅ POST /upload (app) + /upload/admin; Cloudinary; ảnh GIF ≤3s enforce
│   ├── moments/             ✅ đăng bài, feed, mine, user/:uid, delete (chủ bài), seen, reactions
│   │   └── (coop)           ✅ coop.controller/service/repository — mời (TTL 5')/accept/nộp nửa ảnh (sharp ghép)/poll :id/decline
│   │                           (CoopController đứng TRƯỚC MomentsController trong module — tránh nuốt route bởi :id)
│   ├── messages/            ✅ send 1-1/nhóm (+ replyToId snapshot, attachment), threads, conversations, seen,
│   │                           reactions (toggle), groups
│   ├── frames/              ✅ catalog + isUnlocked; 6 unlockType; unlockByThreshold()/unlockCoopFrames() cho hook;
│   │                           admin CRUD + grant + owners
│   ├── quests/              ✅ GET /quests/today (lazy, tự hoàn thành LOGIN); thưởng khung
│   ├── audit/               ✅ AuditService.log (best-effort) + AuditRepository (collection adminLogs)
│   └── admin/               ✅ users list/search, stats + stats/daily, disabled, grant/revoke-admin, moments (kiểm duyệt), logs
├── assets/frames/           ✅ 8 khung PNG mẫu + manifest.json cho seed:frames
├── scripts/
│   ├── seed-admin.ts        ✅ npm run seed:admin -- <email> — cấp claim admin ĐẦU TIÊN
│   ├── seed-frames.ts       ✅ npm run seed:frames — upload khung mẫu + tạo doc frames
│   └── dev-streak.ts        ✅ npm run dev:streak — DEV TOOL set streak / mở-khóa khung để test
└── test/
    └── app.e2e-spec.ts      ✅ e2e smoke (supertest boot AppModule thật: envelope/validation/guard/404, không cần emulator;
                                hướng dẫn mở rộng bằng firebase emulators:exec trong comment đầu file)
```

Mỗi domain module: `*.module.ts` + `*.controller.ts` + `*.service.ts` + `*.repository.ts` + `dto/` + `entities/`. Unit spec (`*.service.spec.ts`) đặt cạnh file nguồn — đủ 9 domain.

---

## 3. Mô hình dữ liệu (thực thể → Firestore)

Chi tiết field ở `.claude/CLAUDE.md` mục 6. Tên collection tập trung ở `src/common/constants.ts`.

| Collection | Vai trò |
|---|---|
| `users/{uid}` | hồ sơ + game (personalStreak, unlockedFrames[], inviteCode + inviteCodeExpiresAt TTL 30 ngày, fcmTokens[], birthday) |
| `friendships/{pairId}` | quan hệ 2 user, friendStreak, lastInteractionAt, status (PENDING = lời mời chờ chủ link xác nhận), requesterUid |
| `posts/{id}` (Moment) | bài đăng; `+ /views/{viewerId}`, `+ /reactions/{id}` (subcollection) |
| `messages/{id}` | tin nhắn (receiverId? / groupId?), messageType, isSeen, reactions{uid: emoji}, attachmentUrl/Type, replyTo* (snapshot tin gốc) |
| `chatGroups/{id}` | nhóm chat (memberIds[] ≤20, `avatar?` URL Cloudinary, `mutedBy[]` uid tắt thông báo, `createdBy` = người quản lý) |
| `coopInvites/{id}` | lời mời chụp chung (inviterId, inviteeId, `inviterMediaUrl?`/`inviteeMediaUrl?` 2 nửa ảnh, `mergedMediaUrl?` ảnh ghép, status PENDING/ACCEPTED/COMPLETED/DECLINED/EXPIRED) |
| `frames/{id}` | khung ảnh: frameName, imageUrl, `unlockType` + `unlockValue`; `milestone` legacy (suy ngược tương thích doc cũ) |
| `dailyQuests/{id}` + `userQuests/{id}` | quest cố định/ngày + trạng thái hoàn thành/thưởng của từng user |
| `adminLogs/{id}` | audit log hành động admin (ai làm gì lên đối tượng nào lúc nào) |

---

## 4. Bảng task & tiến độ

Hạ tầng (config/firebase/common/auth/Swagger/health): ✅ **XONG toàn bộ**.

| Module | CRUD/logic | Business rule chính | Test | TT |
|---|---|---|---|---|
| users | ✅ | personal streak, inviteCode TTL, sync Auth, ensureUser backfill | ✅ | ✅ |
| friendships | ✅ | kết bạn 2 bước, limit 20 transaction 2 phía, streak 24h, mã hết hạn | ✅ | ✅ |
| moments | ✅ | ảnh GIF ≤3s (tại upload), seen/reaction chỉ người thấy được bài, xóa chỉ chủ bài, streak + FCM wired | ✅ | ✅ |
| coop | ✅ | chỉ bạn bè ACCEPTED; TTL **5 phút**; accept chỉ invitee (hủy được cả 2 phía); mỗi bên nộp nửa ảnh riêng; sharp ghép → mergedMediaUrl (KHÔNG tự tạo moment); friend streak + khung COOP_FIRST lúc ghép | ✅ | ✅ |
| messages | ✅ | chỉ-bạn-bè, nhóm ≤20 (chỉ thêm bạn bè — cả lúc tạo lẫn lúc mời thêm), seen, reaction toggle, reply cùng-hội-thoại, quản lý nhóm (rename/avatar/mời/xóa — chỉ creator/rời/mute) | ✅ | ✅ |
| frames | ✅ | 6 unlockType + hook tự mở ở moments/friendships/coop | ✅ | ✅ |
| quests | ✅ | 2 quest/ngày lazy, thưởng 2/2 + mốc streak | ✅ | ✅ |
| admin | ✅ | chặn tự khóa/tự thu quyền (luôn ≥1 admin), guard re-check mỗi request, khóa = revoke refresh token, audit log | ✅ | ✅ |
| upload | ✅ | ≤25MB, ảnh GIF ≤3s enforce | ⬜ | ✅ |
| audit | ✅ | ghi best-effort, không chặn hành động chính | — | ✅ |

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
| GET | `/api/friendships/invite-link` | Link mời của mình (`https://snapget-d8693.web.app/invite/{code}`, TTL 30 ngày, kèm `expiresAt`) | Firebase |
| GET | `/api/friendships/invite-info/:code` | Tên + avatar người mời + hạn link (dialog xác nhận trước khi connect) | Firebase |
| POST | `/api/friendships/connect` | GỬI LỜI MỜI kết bạn qua inviteCode → PENDING (mutual → ACCEPTED luôn; limit 20 transaction; FCM) | Firebase |
| GET | `/api/friendships/requests` | Lời mời kết bạn đang chờ mình xác nhận, kèm profile người gửi | Firebase |
| POST | `/api/friendships/requests/:requesterUid/accept` | Chấp nhận lời mời → thành bạn (re-check limit 20; FCM) | Firebase |
| POST | `/api/friendships/requests/:requesterUid/decline` | Từ chối lời mời (xóa im lặng) | Firebase |
| DELETE | `/api/friendships/:friendUid` | Xóa bạn bè | Firebase |
| POST | `/api/upload` | Upload ảnh/video (multipart `file`, ≤25MB, ảnh GIF ≤3s) → `{url, publicId, resourceType, duration}` | Firebase |
| POST | `/api/upload/admin` | [Admin] Upload ảnh (khung ảnh…) — cùng format với /upload | Admin JWT |
| POST | `/api/moments` | Đăng moment (mediaUrl từ /upload; tăng streak + quest rồi trả NGAY — mở khung POST_COUNT + FCM bạn bè chạy sau response; `clientRequestId?` chống đăng trùng khi retry) | Firebase |
| GET | `/api/moments/feed` | Feed mình + bạn bè (`?page&limit`) | Firebase |
| GET | `/api/moments/mine` | Moment của chính mình (profile calendar, `?page&limit`) | Firebase |
| GET | `/api/moments/user/:uid` | Moment của 1 user — CHỈ bạn bè (hoặc chính mình), 403 nếu không | Firebase |
| POST | `/api/moments/coop` | Gửi lời mời chụp chung (`{friendUid}` — KHÔNG kèm ảnh, hiệu lực 5 phút) | Firebase |
| GET | `/api/moments/coop/pending` | Lời mời chụp chung đang chờ mình (kèm tên/avatar người mời) | Firebase |
| GET | `/api/moments/coop/:id` | Chi tiết lời mời — 2 bên poll trạng thái ở màn chụp coop | Firebase |
| POST | `/api/moments/coop/:id/accept` | Chấp nhận → ACCEPTED, cả 2 vào màn chụp coop | Firebase |
| POST | `/api/moments/coop/:id/decline` | Từ chối lời mời PENDING / **hủy phiên ACCEPTED** (cả 2 phía — rời màn chụp = hủy) | Firebase |
| POST | `/api/moments/coop/:id/media` | Nộp nửa ảnh của mình (`{mediaUrl}`) — đủ 2 nửa server ghép → `mergedMediaUrl` | Firebase |
| DELETE | `/api/moments/:id` | Xóa moment — CHỈ chủ bài (403 nếu không); xóa cả subcollection | Firebase |
| POST | `/api/moments/:id/seen` | Đánh dấu đã xem (chỉ người thấy được bài) | Firebase |
| POST | `/api/moments/:id/reactions` | Thả emoji (chỉ người thấy được bài; cập nhật friend streak) | Firebase |
| GET | `/api/moments/:id/reactions` | Danh sách reaction (chỉ người thấy được bài) | Firebase |
| POST | `/api/messages` | Gửi tin 1-1 (`receiverId`, chỉ bạn bè) hoặc nhóm (`groupId`); optional `attachmentUrl/Type` + `replyToId` | Firebase |
| GET | `/api/messages/conversations` | Danh sách hội thoại 1-1 (tin mới nhất từng người) | Firebase |
| GET | `/api/messages/with/:friendUid` | Thread 1-1 (`?page&limit`, page 1 = mới nhất) | Firebase |
| PATCH | `/api/messages/:id/seen` | Đánh dấu đã xem (chỉ người nhận) | Firebase |
| POST | `/api/messages/:id/reactions` | Thả reaction (`{emoji}`, người trong hội thoại; thả lại cùng emoji = gỡ) | Firebase |
| POST | `/api/messages/groups` | Tạo nhóm chat (≤20 thành viên, chỉ bạn bè) | Firebase |
| GET | `/api/messages/groups` | Danh sách nhóm của mình | Firebase |
| GET | `/api/messages/groups/:groupId` | Thread nhóm (member-only) | Firebase |
| GET | `/api/messages/groups/:groupId/detail` | Chi tiết nhóm + hồ sơ công khai từng thành viên (member-only) | Firebase |
| PATCH | `/api/messages/groups/:groupId` | Đổi tên/ảnh đại diện nhóm (`{groupName?, avatar?}` — mọi thành viên) | Firebase |
| POST | `/api/messages/groups/:groupId/members` | Thêm thành viên (`{memberIds[]}` — phải là bạn bè của người mời, tổng ≤20) | Firebase |
| DELETE | `/api/messages/groups/:groupId/members/:memberUid` | Xóa thành viên (CHỈ người tạo nhóm; không tự xóa mình) | Firebase |
| POST | `/api/messages/groups/:groupId/leave` | Rời nhóm (creator rời → chuyển quyền; người cuối rời → xóa nhóm) | Firebase |
| PATCH | `/api/messages/groups/:groupId/mute` | Bật/tắt thông báo nhóm cho riêng mình (`{muted}` — mutedBy bị loại khỏi FCM nhóm) | Firebase |
| GET | `/api/quests/today` | 2 quest hôm nay + trạng thái (lazy; gọi = tự hoàn thành LOGIN; `rewardFrameId` nếu vừa được thưởng) | Firebase |
| GET | `/api/frames` | Catalog khung + `isUnlocked` của mình (kèm `unlockType`/`unlockValue`) | Firebase |
| GET | `/api/frames/admin` | [Admin] Toàn bộ catalog khung | Admin JWT |
| GET | `/api/frames/:id/owners` | [Admin] User đang sở hữu khung (`{frame, owners[]}`) | Admin JWT |
| POST | `/api/frames` | [Admin] Thêm khung (`frameName/imageUrl/unlockType/unlockValue`) | Admin JWT |
| PATCH | `/api/frames/:id` | [Admin] Sửa khung (đổi loại cần gửi kèm ngưỡng mới) | Admin JWT |
| DELETE | `/api/frames/:id` | [Admin] Xóa khung | Admin JWT |
| POST | `/api/frames/:id/grant/:uid` | [Admin] Mở khóa khung cho user | Admin JWT |
| GET | `/api/admin/users` | Danh sách user (`?search&page&limit`; kèm `admin`, `lastSignInAt`) | Admin JWT |
| GET | `/api/admin/stats` | Thống kê tổng (users/moments/messages/friendships/groups + momentsToday + questCompletionsToday) | Admin JWT |
| GET | `/api/admin/stats/daily` | Thống kê theo ngày (`?days=1..30`): `[{date, moments, newUsers}]` cho biểu đồ | Admin JWT |
| PATCH | `/api/admin/users/:uid/disabled` | Khóa/mở khóa user (không tự khóa mình; khóa = thu hồi refresh token) | Admin JWT |
| POST | `/api/admin/users/:uid/grant-admin` | Cấp quyền admin (giữ các claim khác) | Admin JWT |
| POST | `/api/admin/users/:uid/revoke-admin` | Thu hồi quyền admin (không tự thu mình; hiệu lực NGAY do guard re-check) | Admin JWT |
| GET | `/api/admin/moments` | [Admin] Bài đăng mới nhất (kiểm duyệt, kèm tên tác giả) | Admin JWT |
| DELETE | `/api/admin/moments/:id` | [Admin] Xóa bài vi phạm (kèm subcollection; ghi audit log) | Admin JWT |
| GET | `/api/admin/logs` | [Admin] Audit log hành động admin (`?page&limit`) | Admin JWT |

Swagger: `http://localhost:3000/docs`.

---

## 6. Hạn chế đã biết (chấp nhận ở scale đồ án, KHÔNG phải bug)

- `ResponseInterceptor` nhận diện `{message, data}` bằng duck-typing — endpoint tương lai trả object có 2 field trùng tên sẽ bị bóc nhầm. Fix chuẩn: decorator `@ResponseMessage()`.
- `getConversations` (messages) quét toàn bộ tin gửi + nhận của user để dựng danh sách hội thoại — chấp nhận vì không tạo composite index; scale lớn thì thêm `conversationId` trên message.
- `countCompletionsByDate` (quests, admin stats) đọc mọi doc userQuests của ngày rồi lọc trong bộ nhớ — có thể chuyển sang Firestore count() aggregation.
- Tin nhắn prototype cũ (field `recipientId` thời app đọc Firestore trực tiếp) KHÔNG hiện trong thread/hội thoại — xóa collection `messages` cũ trước khi test thật (không có data người dùng thật).

## 7. Quyết định đã chốt

- ✅ Quyền admin: Firebase custom claims `{admin: true}`; admin đầu tiên seed bằng `npm run seed:admin -- <email>` (mặc định viethoang5301314@gmail.com).
- ✅ Role 2 cấp user/admin: cấp + THU quyền qua trang admin; chặn tự khóa/tự thu quyền (⇒ luôn còn ≥1 admin); `AdminJwtGuard` re-check claim + disabled mỗi request (đánh đổi 1 read Auth/request — chấp nhận).
- ✅ Điều kiện mở khóa khung: 6 loại `unlockType` — QUEST_RANDOM / STREAK_MILESTONE (3/7/14/30) / POST_COUNT / FRIEND_COUNT / COOP_FIRST / DEFAULT. Doc frames cũ (chỉ có `milestone`) suy ngược tự động, không cần migration.
- ✅ Daily Quest: 2 quest cố định/ngày (LOGIN + POST_MOMENT), sinh lazy, hoàn thành tự động. Thiết kế gốc **3 quest/ngày** (quest thứ 3 do AI tạo) — AI hoãn nên tạm giữ `DAILY_QUESTS_PER_DAY=2`; làm AI thì nâng lên 3.
- ✅ Service account key: env `FIREBASE_SERVICE_ACCOUNT` → file json (đã có, gitignore).
- ⏳ Tên collection Moment: giữ `posts` (tương thích app cũ) — muốn đổi thì sửa `Collections.POSTS`.

---

## 8. Lệnh chạy

```bash
npm install
npm run start:dev     # dev watch — http://localhost:3000/api, Swagger /docs
npm run build && npm run start:prod
npm run lint | npm run format
npm run test | npm run test:e2e   # test đã pin --runInBand (máy ít RAM — worker song song crash native)
npm run seed:admin -- <email>     # cấp quyền admin đầu tiên
npm run seed:frames               # seed 8 khung ảnh mẫu (Cloudinary + Firestore)
npm run dev:streak -- --email <email> [--streak N | --unlock-all | --lock-all]   # dev tool test khung/streak
```

---

## 9. Changelog thiết kế (mới → cũ, mỗi đợt 1-3 dòng)

- **2026-08-03 (2) — Fix 2 lỗi user gặp khi coop**: (1) **Ảnh ghép xoay 90°**: `mergeSideBySide` thêm `.rotate()` (auto-orient theo EXIF) trước resize — ảnh CameraX camera sau lưu pixel ngang + cờ EXIF, sharp mặc định bỏ qua EXIF nên ghép nguyên pixel ngang. (2) **POST /moments chậm → client timeout dù bài đã lên**: 2 hook nặng nhất (đếm tổng bài mở khung POST_COUNT + FCM cả danh sách bạn) chuyển sang fire-and-forget SAU response (streak + quest vẫn await); thêm **idempotency `clientRequestId`** (DTO optional + query 2-equality `userId+clientRequestId`, không cần composite index) — retry sau timeout trả lại bài cũ thay vì tạo bản sao. 139 test pass. App sync cùng đợt (UUID sinh mỗi lần vào màn đăng).
- **2026-08-03 — Rà soát coop (3 fix race + đổi ngữ nghĩa decline)**: (1) đánh dấu EXPIRED ở `getInvite`/`listPending` chuyển từ `update` trần sang **`transition('PENDING','EXPIRED')` transactional** — update trần có thể ghi đè EXPIRED lên phiên VỪA accept đúng mốc 5 phút; thua transition thì đọc lại trạng thái thật. (2) `submitMedia` thua transaction ghép giờ **đọc lại DB** thay vì đoán COMPLETED (transition fail còn có thể do phiên vừa bị hủy). (3) `decline` cho phép **hủy cả phiên ACCEPTED** (2 phía) — trước đó 1 bên thoát màn chụp là bên kia "đợi bạn bè chụp" vô hạn. 22 test coop pass (137 toàn suite). App sync cùng đợt (xem `Snapget/.claude/GUIDE.md`).
- **2026-08-03 — "Ảnh GIF" thay video 5s**: `MAX_VIDEO_SECONDS` 5 → **3** (`common/constants.ts`) — clip ngắn giờ là "ảnh GIF" (lặp vô hạn, không tiếng, app phát tự động). `upload.service.ts` đổi message lỗi sang "Anh GIF toi da 3 giay" (vẫn +0.5s dung sai). Không đổi enum/contract: file vẫn `.mp4`, `contentType=VIDEO`.
- **2026-08-02 — Đại tu Co-op Capture (theo yêu cầu user)**: lời mời KHÔNG kèm ảnh + TTL **5 phút** (`COOP_INVITE_TTL_MINUTES=5` thay `COOP_INVITE_TTL_HOURS`); thêm status `ACCEPTED`; entity thêm `inviteeMediaUrl` + `mergedMediaUrl`; endpoint mới `GET /moments/coop/:id` (2 bên poll) + `POST /moments/coop/:id/media` (nộp nửa ảnh — đủ 2 nửa thì khóa transaction ACCEPTED→COMPLETED rồi sharp ghép → `mergedMediaUrl`); **server KHÔNG tự tạo moment nữa** — mỗi người cầm ảnh ghép đăng bài theo luồng thường (streak/quest tính lúc đăng); friend streak + khung COOP_FIRST cộng lúc ghép; decline giờ cho CẢ inviter hủy. 20 test coop pass. App Android đã sync (màn CoopCapture mới).
- **2026-08-02 — Quản lý nhóm chat**: `ChatGroup` thêm `avatar?` + `mutedBy[]`; 6 endpoint mới (detail / PATCH rename+avatar / thêm thành viên / xóa thành viên / rời nhóm / mute). Phân quyền: rename/avatar/mời = mọi thành viên (người được mời PHẢI là bạn bè của người mời — giữ rào chắn createGroup 2026-07-26); xóa thành viên = CHỈ `createdBy`; creator rời → chuyển `createdBy` cho thành viên đầu tiên còn lại, người cuối rời → xóa nhóm; `sendToGroup` bỏ qua `mutedBy` khi push FCM. 30 test messages pass. App Android đã sync (sheet cài đặt nhóm).
- **2026-07-28 — Reply tin nhắn kiểu Messenger**: `SendMessageDto.replyToId` — tin mới reply 1 tin cũ CÙNG hội thoại (1-1: tin gốc giữa đúng 2 người; nhóm: cùng `groupId`; sai → 400). Service snapshot `replyToType/Content/SenderId` vào tin mới (app vẽ trích dẫn không cần lookup). 17 test messages pass.
- **2026-07-27 — Message reaction + attachment**: `POST /messages/:id/reactions` (map `reactions{uid: emoji}`, toggle); `SendMessageDto` thêm `attachmentUrl/attachmentType` (reply bài đăng kèm media).
- **2026-07-26 — Hoàn thiện hệ thống** (107 unit + 7 e2e pass): kiểm duyệt bài đăng + audit log module `audit/`; sync displayName/photoURL lên Firebase Auth khi PATCH /users/me; helmet + throttler (120/60s, login 10/60s); gộp FCM về `UsersService.pushToUids`; e2e smoke `test/app.e2e-spec.ts`.
- **2026-07-26 — Vá lỗi sau review**: ensureUser hết 500 với tài khoản email/password + backfill doc stub; revokeAdmin post-check chống race về 0 admin; listUsers search theo fullName Firestore; createGroup chỉ thêm bạn bè; seen/reaction chỉ người thấy được bài; declineRequest transaction; xóa moment chunk 450; chặn birthday tương lai.
- **2026-07-26 — Đại tu phục vụ admin**: revoke-admin + chặn tự khóa/tự thu quyền + AdminJwtGuard re-check mỗi request; 6 unlockType khung + hook tự mở; `GET /frames/:id/owners`; `GET /admin/stats/daily`.
- **2026-07-13 — Co-op capture (Phase 4)** + quests (2 quest cố định/ngày, không AI) hoàn thành toàn bộ domain.

---

## 10. Cách cập nhật file này (bắt buộc — xem RULE đầu file)

1. **Mỗi thay đổi** (dù nhỏ) → cập nhật NGAY trong cùng lần làm việc, kèm mô tả **thay đổi gì trong thiết kế** vào **Changelog mục 9** (1-3 dòng, mới nhất lên đầu).
2. File/thư mục mới → thêm vào **cây mục 2** kèm ý nghĩa 1 dòng. Đổi kiến trúc/luồng/module → sửa **mục 1**.
3. Cập nhật **bảng task mục 4** + **trạng thái mục 0**.
4. Thêm/đổi endpoint → sửa **mục 5** (và báo đồng bộ app/admin). Đổi data model → sửa **mục 3** + CLAUDE.md mục 6.
5. Đổi ngày "Cập nhật lần cuối" ở đầu file.

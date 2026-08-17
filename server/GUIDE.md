# 🗺️ GUIDE.md — Bản đồ Snapget Server (NestJS)

> ## 📌 RULE BẮT BUỘC
> **Luôn cập nhật file GUIDE.md này MỖI KHI có thay đổi** (code, endpoint, data model, luồng nghiệp vụ, thiết kế) — ngay trong cùng lần làm việc, không đợi "xong tính năng". Mỗi lần cập nhật phải ghi rõ **thay đổi gì trong thiết kế**: sửa mục kiến trúc (mục 1) / cây thư mục (mục 2) / data model (mục 3) / bảng task (mục 4) / endpoint (mục 5) tương ứng, **và thêm 1 dòng vào Changelog (mục 9)** mô tả thay đổi. Sửa code mà không cập nhật GUIDE = **chưa xong việc**. Chi tiết cách cập nhật: mục 10.

> ## 🔐 RULE BẮT BUỘC — SECURITY.md
> **Mọi thay đổi liên quan BẢO MẬT phải cập nhật [`../SECURITY.md`](../SECURITY.md) NGAY trong cùng lần sửa** (song song với GUIDE này).
> Tính là thay đổi bảo mật: guard/`@Roles`/`@Public()`, luồng xác thực & vòng đời token, kiểm tra ownership (IDOR), DTO/`ValidationPipe`, CORS · helmet · rate limit · `trust proxy` · security headers · Swagger, giới hạn & loại file upload, biến env/nơi lưu bí mật/xoay khóa, Firestore & Storage Rules, App Check, danh sách hành động ghi audit log.
> Cách cập nhật: sửa đúng mục trong SECURITY.md (đổi trạng thái ✅/⚠️/🔴 + đường dẫn:dòng), gạch việc đã làm khỏi lộ trình mục 14, đổi dòng "Cập nhật lần cuối". Sửa code bảo mật mà không cập nhật SECURITY.md = **chưa xong việc**.

> Bản đồ **sống** của server: đọc trước khi sửa. Luật/quy ước đầy đủ ở `.claude/CLAUDE.md`. File này = "đang có gì, ở đâu, làm tới đâu".
> Cập nhật lần cuối: **2026-08-15**.

---

## 0. Trạng thái tổng thể

| | |
|---|---|
| Giai đoạn | 🟢 **Server hoàn chỉnh TẤT CẢ domain** (users, friendships, upload, moments, coop, messages, frames, quests, **ai** (2026-08-15), astrite, gacha, topup, admin, audit) |
| Đã verify | `npm run lint` + `tsc --noEmit` sạch · unit test **13 suite / 255 test pass** (astrite 8 + gacha 29 + topup 21 + frames 21 + **quests 35 + ai 9 + ai-quest-templates 7 + admin +3**) · e2e smoke **12 test pass** (`test/app.e2e-spec.ts`, có case webhook PayOS chữ ký rác → 401, cron secret sai → 401/503) · Cloudinary OK · service account key **đã có** trên máy (`snapget-d8693-firebase-adminsdk-fbsvc-d08b18f0f5.json`, `.env` trỏ qua `FIREBASE_SERVICE_ACCOUNT`) |
| Deploy | Đã chạy trên Render: `https://datn-8810.onrender.com/api` (gói free ngủ sau 15 phút — gọi `/api/health` để đánh thức trước demo). Hướng dẫn: `../DEPLOY.md` |
| Việc kế tiếp | **AI Daily Quest — code server/Space/app xong M0–M5 (2026-08-15)** (kế hoạch: `Snapget/.claude/QUEST_AI_PLAN.md`, checklist việc user ở mục 17 của plan). Còn lại **việc của user**: train model trên Colab (`ml/notebooks/snapget12_train.ipynb`) → tạo HF Space + upload model → điền `AI_SERVICE_URL`/`AI_SERVICE_API_KEY`/`CRON_SECRET` → tạo 2 cron trên cron-job.org (generate 00:05 UTC + keep-warm /health 10') → M6 (Snapget-12, v1, ablation). PayOS: điền 3 khoá `PAYOS_*` → đăng ký webhook (GACHA_PLAN mục 12) |
| Blocker | ⚠️ Luồng nạp tiền TẮT cho tới khi có 3 khoá `PAYOS_*` — `POST /topup/orders` trả 503. ⚠️ Quest AI TẮT cho tới khi có `AI_SERVICE_URL` + `AI_SERVICE_API_KEY` — `GET /quests/today` vẫn trả 2 quest như cũ. Toàn bộ phần còn lại chạy bình thường |

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
| `frames` | Catalog khung + 6 điều kiện mở khóa `unlockType`; hook tự mở đặt trong moments/friendships/coop; CRUD + grant + owners cho admin; **(2026-08-11)** tự đồng bộ kho gacha khi CRUD khung (GACHA ⇒ có mặt trong `gachaItems`, khác/xóa ⇒ rút khỏi kho) | users, audit, gacha (forwardRef) |
| `quests` | **3 quest/ngày (2026-08-15)**: 2 quest cố định (LOGIN + POST_MOMENT) lazy tạo, tự hoàn thành, xong 2/2 = **+60 Astrite** (giữ nguyên) + **quest AI `AI_CHALLENGE`** ("Chụp một chiếc cốc"): nội dung do LLM trên HF Space viết (cron `POST /quests/ai/generate`) hoặc template fallback; hoàn thành khi ảnh user đăng lên feed được model AI xác minh có chứa `targetClass` → **+30 Astrite riêng**; log mọi lần verify vào `aiVerifications`; mốc streak 3/7/14/30 vẫn thưởng khung | astrite, frames, **ai** |
| `ai` | **(2026-08-15, sửa 2026-08-16)** `AiService` = HTTP client DUY NHẤT gọi AI service (FastAPI + ONNX trên **Google Cloud Run**, code ở `ml/ai-service/`): `verify(imageUrl, targetClass)` timeout 3s · `health()` · `generate(avoid)` (điểm cắm LLM — hiện service tắt, trả 503 → bộ mẫu); auth `X-API-Key`; `toVerifyImageUrl()` chèn transform Cloudinary `w_224,h_224,c_fill`. Không controller, không repository. Thiếu env → `enabled=false`, không verify (quest AI vẫn hiện, không tick) | config |
| `astrite` | **(2026-08-05)** Ví Astrite: `credit`/`debit`/`grantSignupBonusOnce` — MỌI thay đổi số dư đi qua đây để sổ cái luôn khớp | firebase |
| `gacha` | **(2026-08-05)** Catalog `gachaItems` + quay 1/x10: pity ưu tiên bậc cao & chỉ reset bậc trúng, SR/SSR không trùng khi chưa full, random ở SERVER; **G3**: CRUD kho vật phẩm + lịch sử toàn hệ thống cho admin | astrite, auth, frames, users, audit |
| `topup` | **(2026-08-05 — G6)** Nạp Astrite bằng **tiền thật** qua PayOS: `topupPackages` (admin CRUD) · tạo đơn + link thanh toán · **webhook verify chữ ký + idempotent theo `orderCode`** · `/topup/simulate` chỉ chạy ở dev · lịch sử + doanh thu cho admin. `PayosService` là nơi DUY NHẤT gọi SDK `@payos/node` | astrite, auth, users, audit |
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
│   │                           guards (FirebaseAuthGuard, AdminJwtGuard, RolesGuard, **CronSecretGuard** — header `x-cron-secret`, 2026-08-15)
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
│   ├── quests/              ✅ GET /quests/today (lazy, tự hoàn thành LOGIN; 3 quest khi bật AI); thưởng 2/2 = +60 Astrite, mốc streak = khung;
│   │                           **(2026-08-15)** POST /quests/ai/generate (cron, `CronSecretGuard`); `verifyAiQuest()` hook từ moments;
│   │                           `entities/ai-quest-templates.ts` (12 lớp × 4 câu fallback + `pickFallbackQuest` seed theo ngày),
│   │                           `entities/ai-verification.entity.ts` (log `aiVerifications`)
│   ├── ai/                  ✅ **(2026-08-15)** `AiService` — HTTP client gọi AI service trên Cloud Run (verify/health, timeout, `enabled` fail-safe;
│   │                           `generate` = điểm cắm LLM, hiện tắt). Model + code service nằm ở `../ml/` (root monorepo), KHÔNG trong server
│   ├── astrite/            ✅ **(2026-08-05)** ví tiền tệ Astrite: credit/debit trong transaction + sổ cái `astriteTransactions`;
│   │                           không có controller — users/quests/gacha/topup gọi qua AstriteService
│   ├── gacha/              ✅ **(2026-08-05)** GET state/items/history + POST roll; thuật toán 4 bậc N/R/SR/SSR,
│   │                           pity 10/50/100, hoàn trùng, tất cả trong 1 transaction Firestore.
│   │                           **G3**: CRUD kho vật phẩm + GET history/admin (lọc uid/bậc/ngày) cho trang quản trị
│   ├── topup/              ✅ **(2026-08-05 — G6)** nạp Astrite qua PayOS (TIỀN THẬT): GET packages · POST orders ·
│   │                           GET orders/:orderCode · GET history · POST webhook (@Public, verify chữ ký, idempotent)
│   │                           · POST simulate (chỉ dev) · GET return|cancel (trang HTML PayOS chuyển về) · CRUD gói + lịch sử admin.
│   │                           `payos.service.ts` = NƠI DUY NHẤT gọi SDK; thiếu khoá → luồng nạp tắt, server vẫn boot
│   ├── audit/               ✅ AuditService.log (best-effort) + AuditRepository (collection adminLogs)
│   └── admin/               ✅ users list/search (kèm astrite), stats (+ lượt quay) + stats/daily, disabled,
│                                grant/revoke-admin, moments (kiểm duyệt), logs
├── assets/frames/           ✅ 8 khung PNG mẫu + manifest.json cho seed:frames
├── scripts/
│   ├── seed-admin.ts        ✅ npm run seed:admin -- <email> — cấp claim admin ĐẦU TIÊN
│   ├── seed-frames.ts       ✅ npm run seed:frames — upload khung mẫu + tạo doc frames
│   ├── seed-gacha.ts        ✅ npm run seed:gacha — catalog vật phẩm + chuẩn hóa unlockType khung
│   ├── seed-topup.ts        ✅ npm run seed:topup — 5 gói nạp (GACHA_PLAN mục 0.1), idempotent theo `seedKey`
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
| `users/{uid}` | hồ sơ + game (personalStreak, unlockedFrames[], inviteCode + inviteCodeExpiresAt TTL 30 ngày, fcmTokens[], birthday) · **(2026-08-05)** `astrite` số dư, `unlockedSkins[]`/`unlockedEffects[]` (id kiểu số), `gachaPity{R,SR,SSR}`, `signupBonusClaimed` |
| `friendships/{pairId}` | quan hệ 2 user, friendStreak, lastInteractionAt, status (PENDING = lời mời chờ chủ link xác nhận), requesterUid |
| `posts/{id}` (Moment) | bài đăng; `+ /views/{viewerId}`, `+ /reactions/{id}` (subcollection) |
| `messages/{id}` | tin nhắn (receiverId? / groupId?), messageType, isSeen, reactions{uid: emoji}, attachmentUrl/Type, replyTo* (snapshot tin gốc) |
| `chatGroups/{id}` | nhóm chat (memberIds[] ≤20, `avatar?` URL Cloudinary, `mutedBy[]` uid tắt thông báo, `createdBy` = người quản lý) |
| `coopInvites/{id}` | lời mời chụp chung (inviterId, inviteeId, `inviterMediaUrl?`/`inviteeMediaUrl?` 2 nửa ảnh, `mergedMediaUrl?` ảnh ghép, status PENDING/ACCEPTED/COMPLETED/DECLINED/EXPIRED) |
| `frames/{id}` | khung ảnh: frameName, imageUrl, `unlockType` + `unlockValue`; `milestone` legacy (suy ngược tương thích doc cũ, kể cả `QUEST_RANDOM` → `GACHA`) |
| `dailyQuests/{id}` + `userQuests/{id}` | quest/ngày + trạng thái hoàn thành của từng user; doc `{date}_{uid}_DAILY_REWARD` = claim thưởng ngày (**field `astrite`** — doc trước 2026-08-05 dùng `frameId`). **(2026-08-15)** `dailyQuests/{date}_AI_CHALLENGE` thêm `targetClass`, `source` (LLM/FALLBACK), `generatedAt`; `userQuests/{date}_{uid}_AI_CHALLENGE` thêm `momentId`, `aiScore`, `modelVersion` |
| `aiVerifications/{id}` | **(2026-08-15)** log MỌI lần AI xác minh ảnh quest (kể cả trượt/SKIPPED): uid, momentId, date, targetClass, outcome, score, threshold, scores{12}, modelVersion, latencyMs, roundTripMs, error — số liệu accuracy production cho báo cáo; ghi best-effort |
| `adminLogs/{id}` | audit log hành động admin (ai làm gì lên đối tượng nào lúc nào) |
| `astriteTransactions/{id}` | **(2026-08-05)** sổ cái tiền tệ Astrite — MỌI thay đổi số dư ghi 1 dòng (uid, type, amount ±, balanceAfter, refId) |
| `gachaItems/{id}` | **(2026-08-05)** catalog vật phẩm quay ra: itemName, itemType (FRAME/EFFECT/SKIN), rarity (R/SR/SSR), imageUrl, `refId` (frameId / skinId / effectId), isActive |
| `gachaRolls/{id}` | **(2026-08-05)** lịch sử quay — 1 doc = 1 lần bấm nút (x10 vẫn 1 doc, 10 phần tử `results[]`), kèm cost/refundTotal/balanceAfter |
| `topupPackages/{id}` | **(2026-08-05 — G6)** gói nạp: name, astrite, priceVnd, isActive, isTest, sortOrder (+ `seedKey` do script seed ghi) |
| `topupOrders/{orderCode}` | **(2026-08-05 — G6)** đơn nạp — **doc id CHÍNH LÀ `orderCode`**, đây là cơ chế chống cộng tiền 2 lần. uid, packageId/packageName, astrite, amountVnd, status (PENDING/PAID/CANCELLED/EXPIRED), payosPaymentLinkId, checkoutUrl, payosReference, isSimulated?, createdAt, paidAt |

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
| quests | ✅ | 2 quest/ngày lazy; **(G2)** thưởng 2/2 = +60 Astrite (claim atomic 1 lần/ngày; cộng tiền fail → trả lại claim, cộng xong → GIỮ claim để không cộng 2 lần); mốc streak = khung. **(2026-08-15 — AI)** quest thứ 3 `AI_CHALLENGE`: sinh idempotent (create atomic, không ghi đè), fallback template seed theo ngày tránh lặp 3 ngày; verify chỉ PHOTO + chưa xong; MATCHED → complete atomic + **+30 Astrite** (`AI_QUEST_REWARD`), cộng fail → xoá doc quest để thử lại; Space lỗi → SKIPPED; LLM trả rác → fallback | ✅ 33 test | ✅ |
| **ai** | ✅ | **(2026-08-15)** fail-safe env (`enabled`), fetch + AbortController timeout, không log key, transform URL Cloudinary 224 | ✅ 9 test | ✅ |
| admin | ✅ | chặn tự khóa/tự thu quyền (luôn ≥1 admin), guard re-check mỗi request, khóa = revoke refresh token, audit log | ✅ | ✅ |
| upload | ✅ | ≤25MB, ảnh GIF ≤3s enforce | ⬜ | ✅ |
| audit | ✅ | ghi best-effort, không chặn hành động chính | — | ✅ |
| **astrite** | ✅ | **(2026-08-05 — G0)** ví Astrite: credit/debit trong transaction + sổ cái luôn khớp số dư; thưởng tân thủ 1600 idempotent | ✅ 8 test | ✅ |
| **gacha** | ✅ | **(2026-08-05 — G1)** 4 bậc N/R/SR/SSR; pity 10/50/100 (ưu tiên bậc cao, chỉ reset bậc trúng); SR/SSR không trùng khi chưa full; x10 cập nhật sở hữu ngay trong vòng lặp; pool bậc rỗng → trả Astrite thay vì lỗi. **(G3)** admin chỉ thêm được FRAME, refId phải có thật + chưa trong kho; sửa không đổi được `itemType`/`refId` | ✅ 25 test | ✅ |
| **topup** | ✅ | **(2026-08-05 — G6)** body chỉ nhận `packageId` (giá tra ở server); ghi đơn TRƯỚC khi gọi PayOS; webhook sai chữ ký → 401; **idempotent theo `orderCode`** (gọi 3 lần cộng đúng 1 lần); số tiền lệch → KHÔNG cộng, giữ PENDING để đối soát tay; đơn EXPIRED **vẫn cộng** nếu PayOS báo đã trả; dọn đơn quá hạn bằng **transaction có điều kiện**; lưu checkoutUrl bằng `patchOrder` (không đụng `status`); `/topup/simulate` chỉ cộng vào ví CHÍNH MÌNH và chặn hẳn ở production | ✅ 21 test | ✅ |

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
| POST | `/api/moments` | Đăng moment (mediaUrl từ /upload; tăng streak + quest rồi trả NGAY — mở khung POST_COUNT + FCM bạn bè chạy sau response; `clientRequestId?` chống đăng trùng khi retry) **(2026-08-15)** response thêm `aiQuest?: {result: MATCHED\|NOT_MATCHED\|SKIPPED, score?, questContent?}` khi có quest AI & user chưa xong (Gson app cũ bỏ qua) | Firebase |
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
| GET | `/api/quests/today` | Quest hôm nay + trạng thái (lazy; gọi = tự hoàn thành LOGIN; **`rewardAstrite`** nếu đã xong 2/2 — ⚠️ đổi tên từ `rewardFrameId` ngày 2026-08-05). **(2026-08-15)** khi server bật AI: phần tử thứ 3 `type:'AI_CHALLENGE'` kèm `targetClass`, `source` — app cũ vẫn parse (type là String) | Firebase |
| POST | `/api/quests/ai/generate` | **(2026-08-15) [Cron]** sinh quest AI hôm nay + ngày mai (LLM trên Space, lỗi → template); idempotent, không ghi đè; AI tắt → `[]`; thiếu `CRON_SECRET` → 503 | `@Public` + **CronSecretGuard** (`x-cron-secret`) |
| GET | `/api/gacha/state` | **(2026-08-05)** Số dư Astrite + pity + giá quay + tỉ lệ gốc — app tự sinh popup Rule từ đây | Firebase |
| GET | `/api/gacha/items` | **(2026-08-05)** Catalog vật phẩm đang bật + `isOwned` của mình | Firebase |
| GET | `/api/gacha/history` | **(2026-08-05)** Lịch sử quay của mình (`?limit`, mặc định 50, tối đa 200) | Firebase |
| POST | `/api/gacha/roll` | **(2026-08-05)** Quay 1 hoặc 10 lần — trừ Astrite, random ở server, pity, mở khoá, ghi sổ cái trong 1 transaction | Firebase |
| GET | `/api/gacha/items/admin` | **(2026-08-05)** [Admin] Toàn bộ kho vật phẩm, kể cả đang tắt | Admin JWT |
| GET | `/api/gacha/history/admin` | **(2026-08-05)** [Admin] Lịch sử quay toàn hệ thống (`?uid&tier&date&limit`, lọc trong bộ nhớ; kèm `fullName`) | Admin JWT |
| POST | `/api/gacha/items` | **(2026-08-05)** [Admin] Thêm vật phẩm — **CHỈ `itemType=FRAME`**, refId phải là frame có thật và chưa nằm trong kho | Admin JWT |
| PATCH | `/api/gacha/items/:id` | **(2026-08-05)** [Admin] Sửa vật phẩm (tên/phẩm chất/ảnh/bật-tắt/thứ tự — KHÔNG đổi `itemType`/`refId`) | Admin JWT |
| DELETE | `/api/gacha/items/:id` | **(2026-08-05)** [Admin] Xoá khỏi kho quay (người đã sở hữu vẫn giữ) | Admin JWT |
| GET | `/api/gacha/items/:id/owners` | **(2026-08-06)** [Admin] Danh sách user đang sở hữu vật phẩm (drawer "Ai đang sở hữu?") | Admin JWT |
| POST | `/api/gacha/items/:id/grant/:uid` | **(2026-08-06)** [Admin] **Tặng vật phẩm** cho user (kho thưởng) — idempotent, uid phải tồn tại, ghi audit `GACHA_ITEM_GRANT` | Admin JWT |
| GET | `/api/topup/packages` | **(2026-08-05)** Gói nạp đang bật | Firebase |
| POST | `/api/topup/orders` | **(2026-08-05)** Tạo đơn + link PayOS. Body **chỉ** `{packageId}` — giá tra ở server. Chưa cấu hình khoá → 503 | Firebase |
| GET | `/api/topup/orders/:orderCode` | **(2026-08-05)** Trạng thái đơn của mình (đơn người khác → 403) | Firebase |
| GET | `/api/topup/history` | **(2026-08-05)** Lịch sử nạp của mình (`?limit`) | Firebase |
| POST | `/api/topup/simulate` | **(2026-08-05)** [DEV] Giả lập PayOS báo đã trả — `{packageId}` (tạo + trả luôn) hoặc `{orderCode}` (phát lại). **403 ở production** | Firebase |
| POST | `/api/topup/webhook` | **(2026-08-05)** PayOS gọi. Verify chữ ký (sai → 401) · idempotent theo `orderCode` · mã đơn lạ vẫn trả 200 (payload thử lúc đăng ký webhook) | **Public** |
| GET | `/api/topup/return` · `/api/topup/cancel` | **(2026-08-05)** Trang HTML PayOS chuyển trình duyệt về. App KHÔNG đọc trang này để biết kết quả | **Public** |
| GET | `/api/topup/packages/admin` | **(2026-08-05)** [Admin] Toàn bộ gói nạp, kể cả đang tắt | Admin JWT |
| GET | `/api/topup/history/admin` | **(2026-08-05)** [Admin] `{rows, summary}` — lọc `?uid&status&date&limit`; doanh thu tính trên TOÀN BỘ tập đã lọc, không phải trên `rows` đã cắt | Admin JWT |
| POST/PATCH/DELETE | `/api/topup/packages[/:id]` | **(2026-08-05)** [Admin] CRUD gói nạp — mọi thao tác ghi audit log (`TOPUP_PACKAGE_*`) | Admin JWT |
| GET | `/api/frames` | Catalog khung + `isUnlocked` của mình (kèm `unlockType`/`unlockValue`) | Firebase |
| GET | `/api/frames/admin` | [Admin] Toàn bộ catalog khung | Admin JWT |
| GET | `/api/frames/:id/owners` | [Admin] User đang sở hữu khung (`{frame, owners[]}`) | Admin JWT |
| POST | `/api/frames` | [Admin] Thêm khung (`frameName/imageUrl/unlockType/unlockValue`); khung GACHA **tự vào kho gacha** | Admin JWT |
| PATCH | `/api/frames/:id` | [Admin] Sửa khung (đổi loại cần gửi kèm ngưỡng mới); đổi sang/khỏi GACHA → **tự thêm/rút kho gacha** | Admin JWT |
| DELETE | `/api/frames/:id` | [Admin] Xóa khung (đang trong kho gacha thì rút luôn) | Admin JWT |
| POST | `/api/frames/:id/grant/:uid` | [Admin] Mở khóa khung cho user | Admin JWT |
| GET | `/api/admin/users` | Danh sách user (`?search&page&limit`; kèm `admin`, `lastSignInAt`, **`astrite`**) | Admin JWT |
| GET | `/api/admin/stats` | Thống kê tổng (users/moments/messages/friendships/groups + momentsToday + questCompletionsToday + **gachaRollsToday/Total**) | Admin JWT |
| GET | `/api/admin/stats/daily` | Thống kê theo ngày (`?days=1..30`): `[{date, moments, newUsers}]` cho biểu đồ | Admin JWT |
| PATCH | `/api/admin/users/:uid/disabled` | Khóa/mở khóa user (không tự khóa mình; khóa = thu hồi refresh token) | Admin JWT |
| POST | `/api/admin/users/:uid/grant-admin` | Cấp quyền admin (giữ các claim khác) | Admin JWT |
| POST | `/api/admin/users/:uid/revoke-admin` | Thu hồi quyền admin (không tự thu mình; hiệu lực NGAY do guard re-check) | Admin JWT |
| GET | `/api/admin/moments` | [Admin] Bài đăng mới nhất (kiểm duyệt, kèm tên tác giả) | Admin JWT |
| DELETE | `/api/admin/moments/:id` | [Admin] Xóa bài vi phạm (kèm subcollection; ghi audit log) | Admin JWT |
| GET | `/api/admin/logs` | [Admin] Audit log hành động admin (`?page&limit`) | Admin JWT |
| GET | `/api/admin/ai-verifications` | **(2026-08-16)** Log AI xác minh ảnh quest (mới nhất trước) — thumbnail 224, điểm 12 lớp, ngưỡng, model, latency; lọc `outcome`/`date`/`uid`; phân trang trong bộ nhớ (quét ≤500 log). `GET /admin/stats` thêm `aiVerificationsToday`, `aiMatchedToday` | Admin JWT |

Swagger: `http://localhost:3000/docs`.

---

## 6. Hạn chế đã biết (chấp nhận ở scale đồ án, KHÔNG phải bug)

- `ResponseInterceptor` nhận diện `{message, data}` bằng duck-typing — endpoint tương lai trả object có 2 field trùng tên sẽ bị bóc nhầm. Fix chuẩn: decorator `@ResponseMessage()`.
- `getConversations` (messages) quét toàn bộ tin gửi + nhận của user để dựng danh sách hội thoại — chấp nhận vì không tạo composite index; scale lớn thì thêm `conversationId` trên message.
- `countCompletionsByDate` (quests, admin stats) đọc mọi doc userQuests của ngày rồi lọc trong bộ nhớ — có thể chuyển sang Firestore count() aggregation.
- Tin nhắn prototype cũ (field `recipientId` thời app đọc Firestore trực tiếp) KHÔNG hiện trong thread/hội thoại — xóa collection `messages` cũ trước khi test thật (không có data người dùng thật).
- **(2026-08-15)** Verify quest AI chạy **đồng bộ** trong `POST /moments` (await, timeout cứng 3s) — chỉ khi hôm nay có quest AI và user chưa xong; p95 cần đo khi Space chạy thật. Chụp lại màn hình máy khác đang hiển thị vật thể **không chặn được** ở v1 (limitation nêu trong báo cáo — QUEST_AI_PLAN mục 10).

## 7. Quyết định đã chốt

- ✅ Quyền admin: Firebase custom claims `{admin: true}`; admin đầu tiên seed bằng `npm run seed:admin -- <email>` (mặc định viethoang5301314@gmail.com).
- ✅ Role 2 cấp user/admin: cấp + THU quyền qua trang admin; chặn tự khóa/tự thu quyền (⇒ luôn còn ≥1 admin); `AdminJwtGuard` re-check claim + disabled mỗi request (đánh đổi 1 read Auth/request — chấp nhận).
- ✅ Điều kiện mở khóa khung: 6 loại `unlockType` — **GACHA** / STREAK_MILESTONE (3/7/14/30) / POST_COUNT / FRIEND_COUNT / COOP_FIRST / DEFAULT. `QUEST_RANDOM` đã bỏ (2026-08-05); `FramesRepository.toEntity` map doc cũ (`QUEST_RANDOM` hoặc chỉ có `milestone`) sang loại mới khi đọc → **không cần migration, không cần chờ chạy seed**.
- ✅ **(2026-08-11)** Kho gacha đồng bộ TỰ ĐỘNG theo `unlockType` của khung: `FramesService.syncGachaPool` (best-effort, idempotent) — tạo/sửa khung thành GACHA ⇒ thêm vật phẩm FRAME (phẩm chất R, đang bật) vào `gachaItems`; đổi khỏi GACHA hoặc xóa khung ⇒ rút vật phẩm khỏi kho (ai đã sở hữu vẫn giữ). Admin bỏ nút "Thêm khung vào kho"; `POST /gacha/items` vẫn giữ (idempotent-check trùng refId như cũ).
- ✅ Daily Quest: 2 quest cố định/ngày (LOGIN + POST_MOMENT), sinh lazy, hoàn thành tự động. Thưởng 2/2 quest = **+60 Astrite** (2026-08-05, trước là khung ngẫu nhiên — khung giờ mở qua gacha). **(2026-08-15)** Quest thứ 3 do **AI** tạo đã làm theo thiết kế gốc → `DAILY_QUESTS_PER_DAY=3`: `AI_CHALLENGE` hoàn thành bằng **hook vào đăng bài** (không màn nộp ảnh riêng), thưởng **+30 riêng** (`QUEST_AI_ASTRITE`), 60 của 2/2 giữ nguyên, tối đa 90/ngày; `UserQuest.status` vẫn chỉ `'COMPLETED'` (ảnh không khớp = chưa xong). 12 lớp vật thể `AI_QUEST_CLASSES` (COCO). Cron sinh **hôm nay + ngày mai** để không có cửa sổ user mở app trước cron; **không ghi đè** quest đã có. Chi tiết: `Snapget/.claude/QUEST_AI_PLAN.md`.
- ✅ **(2026-08-15)** AI Space **không nằm trong server**: NestJS là cửa ngõ duy nhất (app không biết Space); Render không load model (giữ 512MB). Code Space + train ở `../ml/`. Ngưỡng per-class (`thresholds.json`) là artifact **đi cùng model trên Space**, không hardcode ở server.
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
npm run seed:gacha                # seed catalog gacha: 2 skin + 5 hiệu ứng + khung; chuẩn hóa unlockType khung cũ → GACHA (idempotent)
npm run seed:topup                # seed 5 gói nạp Astrite (idempotent theo seedKey) — chạy 1 lần sau khi có .env Firebase
npm run dev:streak -- --email <email> [--streak N | --unlock-all | --lock-all]   # dev tool test khung/streak
```

---

## 9. Changelog thiết kế (mới → cũ, mỗi đợt 1-3 dòng)

- **2026-08-16 (2) — Trang admin xem log AI verify (đóng câu hỏi 15.3 của QUEST_AI_PLAN)**: `GET /admin/ai-verifications` (DTO `ListAiVerificationsDto`: outcome/date/uid + phân trang) → `QuestsService.listAiVerifications` (repo `listAiVerifications(limit 500)` orderBy `createdAt` desc — 1 field, không cần index; lọc trong bộ nhớ) và `getAiVerificationStatsToday` (repo `listAiVerificationsByDate` where 1 field) → `GET /admin/stats` thêm `aiVerificationsToday`/`aiMatchedToday` (best-effort, lỗi → 0). Entity `AiVerification` thêm **`mediaUrl`** (URL 224 đã gửi cho service — ghi lúc log để admin hiện thumbnail không cần join `posts`), thêm `AiVerificationRecord` (kèm id) + `AiVerificationDailyStats`. Test mới: `ai-quest-templates.spec.ts` (7 — khoá 72 câu: bắt đầu "Chụp", ≤80 ký tự, không trùng, seed xác định, avoid, 9 ⊂ 12), quests +2, admin +3 → **255 test / 13 suite**. Admin React: `AiVerificationsPage` (`/ai-verifications`, menu "AI quest") + 2 ô dashboard. `ml/ai-service/dev_fake_model.py`: model ONNX giả để chạy service local (ảnh sáng → khớp `motorcycle`, tối → khớp `cup`) — test end-to-end server↔service↔app không cần Colab.
- **2026-08-16 — Gọn phạm vi AI quest theo 3 quyết định của user** (chi tiết `Snapget/.claude/QUEST_AI_PLAN.md` mục 0, 2.3, 17, changelog): (1) **Bỏ LLM sinh quest** — AI chỉ **xác minh ảnh**; quest lấy từ bộ mẫu `AI_QUEST_TEMPLATES` (nay **9 lớp × 8 = 72 câu**, câu chữ hướng ảnh dễ nhận diện). `generateAiQuests()` + `POST /quests/ai/generate` + `AiService.generate()` **giữ nguyên làm điểm cắm** (service trả 503 → server dùng bộ mẫu), cron sinh trước thành tuỳ chọn. (2) **`AI_QUEST_CLASSES` 12 → 9** (bỏ `book`/`backpack`/`keyboard` — nhãn COCO nhiễu / lẫn laptop); thêm `AI_MODEL_CLASSES` (12, khớp output model). Không retrain, không đổi contract (`targetClass` vẫn String). (3) **AI service deploy Google Cloud Run** thay HF Space (HF thu phí Docker Space) — server chỉ đổi `AI_SERVICE_URL`; code service ở `ml/ai-service/` (đổi tên từ `ml/space/`), model artifact tải từ HF Model repo. Chỉ sửa comment/Swagger + hằng số; **243 test pass**.
- **2026-08-15 — AI Daily Quest (M0–M4 phía server, QUEST_AI_PLAN.md)**: hoàn thiện actor **AI** hoãn từ 2026-07-13. Module mới `ai/` (`AiService`, không controller/repo); `quests` thêm `AI_CHALLENGE`: `getTodayQuests` trả 3 quest khi AI bật (cron chưa sinh → tạo FALLBACK ngay, không gọi LLM trong request user), `verifyAiQuest()` hook từ `MomentsService.create` (chỉ PHOTO + chưa xong; Space timeout 3s → SKIPPED; MATCHED → `completeUserQuest` atomic + `AstriteService.credit(30,'AI_QUEST_REWARD')`, cộng fail → `deleteUserQuest` để thử lại), `generateAiQuests()` cho cron (hôm nay + ngày mai, LLM → validate 12 lớp/avoid/độ dài → fallback, `createAiQuestIfAbsent` atomic không ghi đè). Repo thêm `getAiQuest/createAiQuestIfAbsent/getRecentAiTargets/hasCompletedQuest/deleteUserQuest/addAiVerification`. Guard mới `CronSecretGuard` (timingSafeEqual, thiếu env → 503). Constants: `DAILY_QUESTS_PER_DAY=3`, `QUEST_AI_ASTRITE=30`, `AI_QUEST_CLASSES` (12), `AI_QUEST_AVOID_RECENT=3`, `AI_QUEST_CONTENT_MAX=80`, `AI_VERIFY_TIMEOUT_MS=3000`, `AI_GENERATE_TIMEOUT_MS=90000`, `Collections.AI_VERIFICATIONS`. `ASTRITE_TX_TYPES` thêm `AI_QUEST_REWARD`. Env mới (đều optional): `AI_SERVICE_URL`, `AI_SERVICE_API_KEY`, `CRON_SECRET`. Swagger thêm scheme `cron`. 36 test mới (**243 pass**, 12 suite), e2e +2 (**12 pass**). 🔐 SECURITY.md mục 18.
  - ⚠️ **Contract (thêm field, không breaking)**: `GET /quests/today` có thể có phần tử thứ 3 `AI_CHALLENGE` (+ `targetClass`, `source`); `POST /moments` response thêm `aiQuest?`. Đã sync app (`QuestDtos.kt`, `MomentDto.kt` + `AiQuestResultDto`, `DailyQuestScreen` icon 🎯, `PostViewModel`/`SubmitPhotoScreen` toast). Admin không đổi.
  - 🧭 **Fail-safe 3 tầng**: thiếu env → `AiService.enabled=false` (2 quest như cũ); LLM hỏng → template `AI_QUEST_TEMPLATES` (12×4 câu, chọn seed theo ngày, tránh 3 ngày gần nhất); verify hỏng → SKIPPED, đăng bài không bao giờ fail vì AI.
  - 🧭 **Cron sinh cả ngày mai** (thay vì chỉ hôm nay như plan 1.2): xoá cửa sổ 00:00–00:05 UTC user mở app trước cron bị khoá fallback cả ngày; cron gọi lặp/giờ nào cũng an toàn (idempotent, không ghi đè quest user đã thấy).
  - Model + Space + notebook train nằm ở **`ml/` root monorepo** (mới): `ml/space/` (FastAPI, 7 smoke test pytest với ONNX giả), `ml/snapget12/` + `ml/scripts/01–08` + `ml/notebooks/snapget12_train.ipynb`. Xem `ml/README.md`.

- **2026-08-11 — App làm lại hiệu ứng touch bằng spritesheet ⇒ danh mục EFFECT trong `seed-gacha.ts` viết lại (5 → 4, đổi hết tên)**. App xoá 5 hiệu ứng particle cũ (Snowfall/Leaf/Sparkle/Bubble/Ember); kho mới: `Flower` `'1'` · `Snowflake` `'2'` · `Leaf` `'3'` · `Magic` `'4'`; user bổ sung dần, **trần 10** (xem `Snapget/.claude/GUIDE.md` cùng ngày). Server **không đổi contract** — `refId` của EFFECT vẫn là số dạng chuỗi, khớp `TouchEffectRegistry` trong APK.
  - ⚠️ **Việc phải làm tay trên Firestore đã seed** (script chỉ *thêm* item còn thiếu, không sửa/xoá item đã có), qua trang admin: đổi `itemName` `refId '1' → Flower`, `'2' → Snowflake`, `'3' → Leaf`, `'4' → Magic`; đặt **`isActive = false` cho `refId '5'`** (id 5 chưa có sheet trong app). Bỏ bước này thì người chơi quay ra hiệu ứng **không tồn tại trong app** — `TouchEffectRegistry.find()` fallback về `None` nên không crash, nhưng coi như mất lượt SR; tệ hơn là tên item hiện sai hẳn so với hiệu ứng nhận được.
  - Thêm hiệu ứng mới về sau: thêm 1 dòng vào `EFFECTS` trong `scripts/seed-gacha.ts` rồi chạy lại `npm run seed:gacha` (idempotent theo `itemType:refId`).

- **2026-08-11 — Kho gacha tự đồng bộ theo điều kiện mở khóa khung**. `FramesService` thêm `syncGachaPool` (inject `GachaRepository`, `FramesModule` ↔ `GachaModule` nối bằng `forwardRef`): tạo/sửa khung với `unlockType=GACHA` ⇒ tự thêm vật phẩm FRAME (R, đang bật, idempotent theo `itemType:refId`) vào `gachaItems`; đổi khỏi GACHA hoặc xóa khung ⇒ tự rút vật phẩm khỏi kho. Best-effort: lỗi đồng bộ chỉ ghi log, không fail CRUD khung — lần sửa kế tiếp tự kéo về đúng. 8 test mới (**207 test pass**, 11 suite). Admin đã sync: bỏ nút "Thêm khung vào kho" ở `GachaItemsPage` (xem `admin/GUIDE.md`); `POST /gacha/items` giữ nguyên contract. `pickTier` nay **chỉ chọn bậc**, không tự reset bộ đếm; `roll` chỉ `state.pity[tier] = 0` sau khi chắc chắn có vật phẩm để phát. Trước đó: admin tắt hết vật phẩm SSR (tính năng "ẩn" của kho thưởng vừa thêm) → người chơi chạm mốc 100 lượt, nhận Astrite thay vật phẩm nhưng **bộ đếm vẫn về 0** ⇒ mất trắng bảo hiểm đã quay 100 lần mới có. Nay mở lại vật phẩm là đổi thưởng ngay lượt sau. 1 test mới (**199 test pass**, 11 suite).
- **2026-08-06 — Kho thưởng: tặng vật phẩm + owners; seed dữ liệu lần đầu**. `POST /gacha/items/:id/grant/:uid` (tặng thẳng vào tài khoản — demo/đền bù; KHÔNG liên quan Astrite, không ghi sổ cái) + `GET /gacha/items/:id/owners`; audit action mới `GACHA_ITEM_GRANT`. `UsersRepository` thêm `unlockCollectible`/`listByCollectible` dùng chung 3 mảng sở hữu. 4 test mới (**198 test pass**). Đã chạy `seed:gacha` (10 vật phẩm: 3 khung + 5 hiệu ứng + 2 skin; 3 khung `QUEST_RANDOM` cũ chuẩn hoá về `GACHA`) và `seed:topup` (5 gói nạp) — trước đó kho trống nên app quay báo "Kho vật phẩm đang trống" và popup nạp rỗng.
 - ⚠️ **SKIN/EFFECT tặng bằng SỐ, FRAME bằng chuỗi** — app so id kiểu Int với `SkinRegistry`/`TouchEffectRegistry`; tặng dạng chuỗi là app không nhận ra vật phẩm đã mở (đã khoá bằng test).
- **2026-08-05 — Soát lại luồng G6: vá 3 đường cộng tiền 2 lần**. Rà toàn bộ module `topup` sau khi hoàn thành; 4 test mới (**194 test pass**).
  - 🔴 **Dọn đơn quá hạn ghi đè trạng thái `PAID`**. `expireStaleOrders` đọc danh sách rồi ghi thẳng `EXPIRED`; giữa 2 bước, webhook thật có thể vừa chuyển đơn sang `PAID` — lệnh ghi kéo nó về `EXPIRED`, và webhook gọi lại sau đó **không còn `PAID` để chặn nên cộng tiền lần hai**. Thay bằng `TopupRepository.expireOrderIfPending` chạy trong transaction, chỉ đổi khi doc **vẫn còn** `PENDING`.
  - 🟠 **`createOrder` ghi lại `status: 'PENDING'`** khi lưu `checkoutUrl` — cùng kiểu rủi ro. Tách `patchOrder` (không bao giờ đụng `status`).
  - 🟠 **`/topup/simulate` không kiểm tra chủ đơn** — đoán đúng `orderCode` là cộng được vào ví người khác. Đổi sang dùng `getOrderForUser` (403 nếu không phải của mình). Chỉ chạy ở dev nhưng không có lý do gì để hở.
  - ⚠️ `PayosService` báo lỗi rõ khi có đủ 3 khoá nhưng **thiếu `PAYOS_RETURN_URL`/`PAYOS_CANCEL_URL`** (trước đây để PayOS trả 400 khó hiểu lúc người dùng bấm nạp) — cảnh báo ngay lúc khởi động + 503 kèm thông báo tiếng Việt.
- **2026-08-05 — G6: Nạp Astrite bằng TIỀN THẬT qua PayOS**. Module `topup` mới (13 route), dependency mới duy nhất `@payos/node@2`. 5 env `PAYOS_*` (đều optional → **thiếu khoá thì server vẫn boot**, chỉ luồng nạp tắt, `POST /topup/orders` trả 503 — nhờ vậy CI/e2e chạy được offline). 21 test mới, **194 test pass** (11 suite); e2e thêm 3 case (webhook chữ ký rác → 401, `/topup/packages` không token → 401, `/topup/cancel` → HTML). Admin có 2 trang mới, app có popup gói nạp + nút `+` cạnh số Astrite. 🔐 SECURITY.md thêm **mục 17** — mô hình đe doạ riêng cho tiền thật.
  - 🛡️ **Ba bất biến của tiền thật**: (1) body tạo đơn **chỉ có `packageId`**, giá tra từ `topupPackages` ở server; (2) **doc id = `orderCode`** ⇒ webhook gọi lại bao nhiêu lần cũng trỏ về đúng 1 doc, transaction chỉ cộng khi chưa `PAID`; (3) chữ ký sai → 401 trước khi đọc tới `orderCode`.
  - 🧭 **Đơn `EXPIRED` vẫn được cộng** nếu webhook thật báo đã trả: `EXPIRED` là phỏng đoán của server (hết TTL 30'), còn webhook là sự thật từ PayOS — tiền đã vào tài khoản MB. Chỉ `PAID` mới chặn. Thà cộng muộn còn hơn người dùng mất tiền.
  - 🧭 **Mã đơn lạ → trả 200, không phải 404**: lúc đăng ký webhook, PayOS gửi một payload THỬ với `orderCode` không có thật; trả 404 thì PayOS coi webhook hỏng và **không lưu URL**.
  - 🧭 **Số tiền lệch → không tự đoán**: ghi `logger.error`, giữ đơn `PENDING` để admin đối soát tay. Đây là tiền thật, không có "hoàn tác".
  - 💸 **`/topup/simulate` là công cụ dev chính**, không phải phương án dự phòng: gói FREE-100 chỉ cho 100 giao dịch thành công, mỗi lần test thật tiêu 1 và không hoàn lại. Endpoint này **ký payload rồi gọi đúng `handleWebhook`** nên vẫn đi qua verify chữ ký + transaction idempotent; chặn hẳn khi `NODE_ENV=production`.
  - ⚠️ **Ba khoá `PAYOS_*` không bao giờ được log**: `PayosService` chỉ log `orderCode`, và chỗ bắt lỗi chỉ lấy `error.message` chứ không in object.
- **2026-08-05 — G3: Endpoint quản trị gacha**. 5 route admin mới trên `gacha` (`GET items/admin`, `GET history/admin`, `POST/PATCH/DELETE items`), mọi thao tác ghi `AuditService` (3 action mới `GACHA_ITEM_*`). `GET /admin/users` thêm `astrite`, `GET /admin/stats` thêm `gachaRollsToday/Total`. 9 test gacha mới, **173 test pass**. Admin đã sync: `GachaItemsPage` + `GachaHistoryPage` (xem `admin/GUIDE.md`).
  - 🛡️ **Rào chắn nghiệp vụ ở service, không chỉ ở UI**: chỉ tạo được `itemType=FRAME` (skin/hiệu ứng có asset trong APK, tạo mới sẽ ra vật phẩm quay trúng được nhưng app không hiển thị nổi); `refId` phải trỏ frame có thật; **một frame không được vào kho 2 lần** (tỉ lệ nổ gấp đôi); sửa không đổi được `itemType`/`refId` (đổi = thứ người chơi đã sở hữu bỗng thành thứ khác).
  - 🐞 **E2E bắt được lỗi wiring**: `GachaModule` thiếu `AuthModule` nên `AdminJwtGuard` không resolve được `JwtService` — unit test không phát hiện vì không dựng module graph. Đây đúng là việc của `test/app.e2e-spec.ts`.
  - `AdminRepository.getAllFullNames` refactor qua `getAllUserSummaries()` (tên + astrite trong **1** lần đọc collection, không đọc 2 lần).
  - ⏭️ **Bỏ `GachaBannerPage`** (user chốt 2026-08-05): banner hardcode trong APK, không cần doc `config/gachaBanner` lẫn endpoint `/gacha/banner`.
- **2026-08-05 — G2: Thưởng quest đổi sang Astrite + khung về pool gacha**. `maybeGiveDailyReward` bỏ `pickRandomLockedFrame`, gọi `AstriteService.credit(uid, 60, 'QUEST_REWARD', date)`; repo `setDailyRewardFrame` → `setDailyRewardAstrite`, doc claim đổi field `frameId` → `astrite`. `UNLOCK_TYPES` bỏ `QUEST_RANDOM`, thêm `GACHA`. **QuestsService bỏ phụ thuộc `UsersRepository`** (không còn phải đọc `unlockedFrames`), thêm `AstriteModule`. 164 test pass.
  - ⚠️ **Đổi contract**: `GET /quests/today` trả `rewardAstrite: number|null` thay `rewardFrameId: string|null`. Đã sync app (`QuestDtos.kt`, `QuestViewModel.kt`, `DailyQuestScreen.kt` — RewardBanner hiện "+60 Astrite") và admin (`UnlockType`, `FramesPage`).
  - 🔁 **Không cần migration**: `FramesRepository.toEntity` map `QUEST_RANDOM` (và doc chỉ có `milestone`) sang loại mới **khi đọc**, nên server chạy đúng ngay cả trước khi chạy `seed:gacha`. Script seed chỉ dọn dữ liệu cho gọn — và đã sửa để suy y hệt repo, tránh trường hợp khung server coi là GACHA nhưng không nằm trong catalog → không bao giờ quay ra được.
  - 🔒 **Chống cộng 2 lần**: cộng Astrite FAIL → trả lại claim để lần sau thử lại; cộng XONG rồi ghi mốc thưởng fail → **giữ claim** (tiền đã vào ví, xóa claim là lần gọi sau cộng thêm 60 nữa). Khác bản cũ vì mở khung `arrayUnion` idempotent còn cộng tiền thì không.
- **2026-08-05 — G1: Lõi gacha** (module `gacha/`). Catalog `gachaItems` (itemType FRAME/EFFECT/SKIN × rarity R/SR/SSR, `refId` trỏ tới frameId/skinId/effectId) + `gachaRolls` (lịch sử). 4 endpoint: `GET /gacha/state|items|history` + `POST /gacha/roll`. Thuật toán: pity **ưu tiên bậc cao** và **chỉ reset bộ đếm bậc vừa trúng** (đúng spec); **SR/SSR không bao giờ ra trùng khi chưa sở hữu hết**, R được trùng tự do; **quay x10 cập nhật tập sở hữu ngay trong vòng lặp** (nếu không sẽ phát trùng skin trong cùng lượt); bậc rỗng vật phẩm → trả Astrite thay vì lỗi. Random bằng `crypto.randomInt` ở SERVER, tách 3 hàm `protected` để test điều khiển được. Trừ tiền + hoàn + mở khoá + ghi sổ cái nằm trong **1 transaction**. Script `npm run seed:gacha` (idempotent) seed 2 skin + 5 hiệu ứng + khung, và đổi khung `QUEST_RANDOM` → `GACHA`. 16 test gacha, **163 test toàn suite pass**.
  - Ảnh đại diện của SKIN/EFFECT để trống khi seed — asset thật nằm trong APK, admin tự upload ảnh đại diện qua trang quản trị (G3).
- **2026-08-05 — G0: Nền tảng tiền tệ Astrite** (mở màn hệ thống Gacha — kế hoạch đầy đủ ở `Snapget/.claude/GACHA_PLAN.md`). Module mới `astrite/`: sổ cái `astriteTransactions` + ví trên user doc. `User` thêm `astrite`, `unlockedSkins[]`, `unlockedEffects[]`, `gachaPity{R,SR,SSR}`, `signupBonusClaimed` (doc cũ thiếu field → mặc định an toàn trong `toEntity`, không cần migration). `ensureUser` tặng **1600 Astrite tân thủ** idempotent qua cờ `signupBonusClaimed` đọc-ghi trong cùng transaction. `common/constants.ts` thêm toàn bộ hằng số gacha (giá quay 160/1440, tỉ lệ 4/0,9/0,1%, pity 10/50/100, hoàn 160/1000/2000, bậc N 1–60, quest 60, tân thủ 1600) — **user chốt hardcode, không cho admin sửa qua web**. 8 test astrite + 147 test toàn suite pass.
  - ⚠️ **Ngoại lệ kiến trúc có chủ ý**: các field ví (`astrite`, `signupBonusClaimed`) nằm trên `users/{uid}` nhưng do `AstriteRepository` ghi, KHÔNG phải `UsersRepository`. Lý do: mọi thay đổi số dư bắt buộc chạy cùng transaction với dòng sổ cái, mà Firestore chỉ cho 1 callback `runTransaction`. `UsersRepository` chỉ đọc chúng ra entity. Đã ghi chú trong header cả 2 repository.
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

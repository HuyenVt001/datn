# CLAUDE.md — Ruler cho **Snapget Server** (NestJS)

> **CLAUDE.md = LUẬT** (quy ước, ràng buộc, quyết định đã chốt — ít thay đổi). **GUIDE.md = BẢN ĐỒ** (kiến trúc hiện tại, cây thư mục, endpoint, tiến độ, changelog — cập nhật liên tục).
> Đọc file này **trước**, rồi mở `server/GUIDE.md` để biết "đang có gì, ở đâu, sửa gì thì vào file nào".
> Phần giải thích bằng **tiếng Việt**; **code + tên định danh luôn tiếng Anh**.

---

## 0. Nguyên tắc tối thượng

1. **Luôn hỏi** vài câu trước khi bắt đầu việc gì còn mơ hồ (không tự đoán khi chưa chắc).
2. **Luôn đọc & cập nhật `server/GUIDE.md` sau MỖI lần chỉnh sửa code** — cách cập nhật + Changelog theo RULE ở đầu GUIDE.md (mục 10 của GUIDE). Đụng **bảo mật** → cập nhật thêm `SECURITY.md` ở root repo (rule 🔐 đầu GUIDE.md). Sửa code mà không cập nhật = **chưa xong việc**.
3. Server là **một phần của monorepo DATN**; contract API phải khớp app Android (`Snapget/`) và admin (`admin/`). Đổi contract → ghi GUIDE + nhắc user đồng bộ 2 client.
4. **Sau khi hoàn thành MỖI yêu cầu của user có sửa code trong `server/`** → chạy `codegraph init` cho thư mục này (`cd server && codegraph init`).

---

## 1. Server này là gì

Server **NestJS (Node + TypeScript)** làm **cửa ngõ duy nhất** giữa client (app Android + admin React) và Firebase/Cloudinary — client không chạm Firestore trực tiếp. Server verify auth, validate input, enforce business rule (mục 16), rồi mới chạm Firebase.

- Domain modules: `users`, `friendships`, `upload`, `moments` (kèm coop), `messages`, `frames`, `quests`, `admin`, `audit`. Hạ tầng: `auth`, `firebase`, `common`, `config`.
- Actor (theo phân tích thiết kế): **Member** → **User** (app) + **Admin** (quản trị); **AI** (tạo daily quest thứ 3) có trong thiết kế nhưng **hoãn — chưa làm** (mục 14).
- Sơ đồ kiến trúc, pipeline request, bản đồ module: **GUIDE.md mục 1**.

---

## 2. Tech stack (đã CHỐT — không đổi nếu chưa hỏi user)

| Hạng mục | Lựa chọn |
|---|---|
| Runtime | **Node 20 LTS** |
| Package manager | **npm** (dùng `package-lock.json`, không xài pnpm/yarn) |
| Framework | **NestJS** (TypeScript strict) |
| Persistence | **Cloud Firestore** qua **`firebase-admin`** SDK (KHÔNG dùng ORM/SQL) |
| Auth app | Verify **Firebase ID token** (Admin SDK) trong Guard |
| Auth admin | Server **tự phát JWT riêng** (`@nestjs/jwt`) |
| Validation | `class-validator` + `class-transformer` + global `ValidationPipe` |
| Docs | **Swagger** (`@nestjs/swagger`) tại `/docs` |
| Test | **Jest** — unit (`*.spec.ts`) + e2e (`test/*.e2e-spec.ts`) |
| Lint/format | **ESLint + Prettier** (config chuẩn NestJS) |
| Commit | **Conventional Commits** |
| Config | `@nestjs/config` + `.env` (validate bằng Joi) |

> ❌ Không thêm: SQL/Prisma/TypeORM, Docker (giai đoạn này), license header. Nếu cần → hỏi user trước.

---

## 3. Cấu trúc & phân tầng (bắt buộc)

Cây thư mục thực tế + ý nghĩa từng file: **GUIDE.md mục 2**. Luật ở đây:

- `Controller` (nhận request, DTO, guard, Swagger) → `Service` (business logic) → `Repository` (**NƠI DUY NHẤT** chạm Firestore của domain). Controller **không** gọi thẳng Repository; Service **không** đụng `req`/`res`.
- Khung 1 domain module: `<domain>.module.ts` + `<domain>.controller.ts` + `<domain>.service.ts` + `<domain>.repository.ts` + `dto/` + `entities/` + `*.spec.ts` cạnh file nguồn.
- `common/` chỉ chứa tiện ích dùng chung (envelope, guards, decorators, constants) — KHÔNG business logic.
- FCM push: **chỉ** qua `UsersService.pushToUids()` — không gọi `messaging()` rải rác ở service khác.

---

## 4. Quy ước code

- **Tên định danh tiếng Anh, chuẩn NestJS.** Comment + Swagger description + GUIDE = **tiếng Việt**.
- File `kebab-case` (`users.service.ts`); class `PascalCase`; biến/hàm `camelCase`.
- **TypeScript strict**: không `any` tùy tiện. Dữ liệu ra/vào API luôn qua DTO có `class-validator`.
- `async/await`, không để promise lơ lửng (`no-floating-promises`).
- Không log secret/token. Dùng `Logger` của Nest, không `console.log` trong code chạy thật.
- Mỗi endpoint có Swagger decorator (`@ApiOperation`, `@ApiResponse`) mô tả **tiếng Việt** — Swagger là công cụ demo chính khi bảo vệ DATN.
- Chạy `npm run lint` + `npm run format` trước khi coi là xong.

---

## 5. Xác thực & phân quyền (2 luồng riêng — sơ đồ chi tiết: GUIDE.md mục 1.3)

- **App**: `FirebaseAuthGuard` verify Firebase ID token mỗi request → `req.user`. Endpoint mở gắn `@Public()`; lấy user qua `@CurrentUser()`.
- **Admin**: `AdminJwtGuard` + `RolesGuard` (`@Roles('admin')`). Quyền admin = **Firebase custom claim `{admin: true}`** (ĐÃ CHỐT).
- **Bất biến phải giữ khi sửa**: `AdminJwtGuard` re-check claim admin + `disabled` qua `getUser()` **MỖI request** (thu quyền/khóa hiệu lực ngay); login admin check claim HIỆN HÀNH (không tin claim trong ID token cũ); chặn tự khóa/tự thu quyền chính mình (⇒ hệ thống luôn còn ≥1 admin).
- **KHÔNG** nhận Firebase ID token ở route admin; **KHÔNG** nhận JWT admin ở route app.

---

## 6. Tầng dữ liệu — mô hình thực thể → Firestore (CANONICAL)

- **Repository là nơi DUY NHẤT** import/chạm `firestore()`.
- Nguồn chuẩn domain = *"thiết kế các lớp thực thể trong database"* + *"Phân tích thiết kế app"*. Thiết kế gốc quan hệ (PK/FK, bảng trung gian) hiện thực trên Firestore (NoSQL): **bảng trung gian → subcollection hoặc mảng**.
- Tên collection tập trung ở `src/common/constants.ts`.

### 6.1 Thực thể → Firestore collection

| Thực thể | Firestore | Field chính | Ghi chú |
|---|---|---|---|
| **User** | `users/{uid}` | `email`, `fullName`, `avatar`, `birthday?`, `joinDate`, `personalStreak`, `inviteCode` + `inviteCodeExpiresAt` (TTL 30 ngày), `unlockedFrames[]`, `fcmTokens[]` | `uid` = Firebase Auth uid. Password do Firebase Auth giữ, KHÔNG lưu Firestore. User_Frame → mảng `unlockedFrames[]` |
| **Friendship** | `friendships/{pairId}` | `userIds[]` (sort để query), `user1Id`, `user2Id`, `friendStreak`, `lastInteractionAt`, `status` (PENDING/ACCEPTED), `requesterUid`, `createdAt` | `pairId` = 2 uid ghép sort. PENDING = lời mời chờ chủ link xác nhận |
| **Moment** | `posts/{id}` | `userId`, `contentType` (PHOTO/VIDEO), `mediaUrl`, `frameId?`, `caption?`, `coopUserId?`, `postTime`, `createdAt` | Giữ tên collection `posts` (tương thích). `coopUserId` = người chụp chung |
| **Moment_View** | `posts/{id}/views/{viewerId}` | `viewerId`, `isSeen`, `seenAt` | Junction → subcollection |
| **Reaction** | `posts/{id}/reactions/{id}` | `reactorId`, `emojiType`, `createdAt` | Junction → subcollection |
| **Chat_Group** | `chatGroups/{id}` | `groupName`, `memberIds[]` (≤20) | Group_Member gộp vào `memberIds[]` |
| **Message** | `messages/{id}` | `senderId`, `receiverId?`/`groupId?` (một trong hai), `messageType` (TEXT/VOICE/EMOJI/STICKER/PHOTO), `content`, `sendTime`, `isSeen`, `reactions{uid: emoji}`, `attachmentUrl?`+`attachmentType?`, `replyToId?`+`replyToType/Content/SenderId` (snapshot tin gốc) | Reply chỉ trong CÙNG hội thoại; snapshot để client vẽ trích dẫn không cần lookup |
| **Coop_Invite** | `coopInvites/{id}` | `inviterId`, `inviteeId`, `inviterMediaUrl`, `status` (PENDING/COMPLETED/DECLINED), `momentId?` | TTL 24h |
| **Frame** | `frames/{id}` | `frameName`, `imageUrl`, `unlockType` + `unlockValue` (6 loại điều kiện mở khóa), `milestone` (legacy — repo suy ngược cho doc cũ) | Catalog do admin quản lý |
| **Daily_Quest** | `dailyQuests/{date}_{type}` | `type` (LOGIN/POST_MOMENT), `content`, `releaseDate` | Id cố định → lazy-create idempotent |
| **User_Quest** | `userQuests/{date}_{uid}_{type}` | `questId`, `userId`, `type`, `status`, `completedAt` | Hoàn thành TỰ ĐỘNG. Doc `_DAILY_REWARD` đánh dấu đã thưởng khung ngày đó |
| **Admin_Log** | `adminLogs/{id}` | actor, action, target, thời điểm | Audit log, ghi best-effort |

### 6.2 Quy tắc query

- Server có Admin SDK → **được phép tạo composite index**; khi thêm query cần index: tạo trên Firebase Console (ghi vào GUIDE) hoặc giữ pattern 1-filter + lọc bộ nhớ. **Nêu rõ lựa chọn trong commit.**
- `whereIn` giới hạn 10 phần tử → `chunked(10)`. Batch write giới hạn 500 → chunk 450.

### 6.3 Mapping

- Repository map Firestore doc ↔ entity; xử lý `Timestamp` ↔ ISO string ở tầng này, không rò rỉ kiểu Firestore ra Controller.

---

## 7. Response envelope & xử lý lỗi (CHỐT)

Mọi response qua global `ResponseInterceptor`; mọi lỗi qua global `AllExceptionsFilter`:

```ts
{ "success": true,  "statusCode": 200, "message": "OK", "data": { /* ... */ } }
{ "success": false, "statusCode": 404, "message": "Không tìm thấy bài viết", "data": null }
```

- Ném lỗi bằng `HttpException` của Nest (`NotFoundException`, `BadRequestException`, …).
- `message` lỗi cho client viết **tiếng Việt, thân thiện**; chi tiết kỹ thuật vào log.
- Phân trang → `data = { items, page, limit, total }` (chuẩn hóa trong `PaginationDto`).

---

## 8. Validation / DTO

- Mọi body/query/param qua DTO có `class-validator`; global `ValidationPipe({ whitelist: true, transform: true, forbidNonWhitelisted: true })`.
- DTO tạo/sửa tách riêng (`Create*Dto`, `Update*Dto` — `Update` dùng `PartialType`).
- Không nhận field thừa; không tin dữ liệu client.

---

## 9. Config & Secrets

- Toàn bộ config qua `@nestjs/config`, đọc `.env`, **validate lúc khởi động** (Joi) — thiếu biến thì fail sớm.
- Firebase Admin credential: service account key, đường dẫn trong env `FIREBASE_SERVICE_ACCOUNT` (file `snapget-*-firebase-adminsdk-*.json` — **khác** `google-services.json` của app).
- 🔒 **Không commit:** `.env`, file service account (`.gitignore` có pattern `*-firebase-adminsdk-*.json`). Thêm biến env mới → cập nhật `.env.example` + `env.validation`.
- Biến tối thiểu: `PORT`, `NODE_ENV`, `CORS_ORIGINS`, `FIREBASE_SERVICE_ACCOUNT`, `FIREBASE_PROJECT_ID`, `JWT_SECRET`, `JWT_EXPIRES_IN` (+ Cloudinary keys).

---

## 10. Testing

- **Unit** (`*.spec.ts` cạnh file nguồn): test Service (mock Repository/FirebaseService) — không gọi Firebase thật.
- **E2E** (`test/*.e2e-spec.ts`): supertest; không đụng dữ liệu production.
- Thêm/đổi logic nghiệp vụ → cập nhật/thêm test tương ứng. Lệnh chạy: GUIDE.md mục 8 (test pin `--runInBand`).

---

## 11. Git & Commit

- **Conventional Commits**: `feat(moments): ...`, `fix(auth): ...`, `docs(guide): ...`, `chore: ...`. Commit nhỏ, theo module.
- Không commit secret/build artifact. Chỉ commit/push khi user yêu cầu.

---

## 12. Quy trình chuẩn

### Thêm 1 module domain mới
1. Module + controller + service + `<name>.repository.ts` (khung mục 3).
2. Entity + DTO (`class-validator`, `@ApiProperty` tiếng Việt).
3. Repository map Firestore ↔ entity (đúng tên field mục 6.1).
4. Service (business logic + phân quyền) → Controller (route + guard + Swagger).
5. Unit test + e2e.
6. Cập nhật `GUIDE.md` (+ `SECURITY.md` nếu đụng bảo mật).

### Thêm 1 endpoint
1. DTO + validate. 2. Method Service. 3. Query Repository (nêu rõ nếu cần index). 4. Route + guard + Swagger. 5. Test. 6. Đúng envelope. 7. Cập nhật GUIDE mục 5 (+ báo đồng bộ app/admin nếu đổi contract).

---

## 13. Contract với client (đừng phá)

- App Android + admin đã gọi **100% qua REST API này** — đổi/xóa field hoặc endpoint là breaking change: ghi GUIDE + báo user đồng bộ 3 nơi.
- **THÊM field mới vào response là an toàn** (Gson phía app bỏ qua field lạ); đổi tên/xóa field thì KHÔNG.
- Giữ tên field & enum khớp mục 6.1 và khớp DTO client.

---

## 14. Nghiệp vụ & business rules (server là nơi ENFORCE thật)

Client chỉ chặn UX; **server mới validate ràng buộc thật**. Luật rút từ *Phân tích thiết kế app*:

| Luật | Chi tiết | Nơi enforce |
|---|---|---|
| Giới hạn bạn bè | **≤ 20** mỗi user; kiểm tra giới hạn **cả 2 phía** trong transaction (lúc gửi lời mời lẫn lúc accept) | `friendships` |
| Kết bạn qua link | 2 bước: mở link/QR → gửi lời mời PENDING → chủ link accept/decline (mutual → ACCEPTED luôn); mã mời TTL 30 ngày | `friendships` |
| Nhóm chat | thành viên **≤ 20**, chỉ thêm bạn bè | `messages` |
| Video | **≤ 5 giây** (kiểm tra metadata lúc upload, quá thì xóa asset) | `upload` |
| Friend streak | mỗi cặp bạn 1 streak chung; +1 khi tương tác qua lại trong 24h; **>24h → reset 0** (dựa `lastInteractionAt`) | `friendships` |
| Personal streak | ngày liên tiếp đăng ≥1 moment; mỗi user 1 streak | `users` |
| Moment view / Message seen | tự đánh dấu `isSeen`; seen/reaction **chỉ người thấy được bài** (chủ bài/coop partner/bạn bè); message seen chỉ người nhận | `moments` / `messages` |
| Xóa moment | chỉ chủ bài (admin xóa qua route kiểm duyệt riêng, có audit log) | `moments` / `admin` |
| Co-op capture | chỉ bạn bè ACCEPTED; chỉ invitee trả lời; lời mời TTL 24h; server ghép 2 nửa ảnh (sharp) → 1 moment chung, streak/quest cho CẢ 2 | `moments` (coop) |
| Daily quest | **(CHỐT 2026-07-13)** Thiết kế gốc **3 quest/ngày** (quest thứ 3 do **AI** tạo) — AI hoãn nên **tạm giữ 2** quest cố định/ngày: LOGIN (tự xong khi `GET /quests/today`) + POST_MOMENT (tự xong khi đăng bài); sinh lazy. Thưởng: đủ quest/ngày → 1 khung QUEST_RANDOM; mốc streak 3/7/14/30 → khung mốc. Làm AI → nâng `DAILY_QUESTS_PER_DAY` lên 3 | `quests` |
| Mở khóa khung | 6 loại `unlockType`: QUEST_RANDOM · STREAK_MILESTONE (3/7/14/30) · POST_COUNT · FRIEND_COUNT (mở cả 2 phía) · COOP_FIRST (cả 2) · DEFAULT (mở sẵn) — hook tự mở đặt ở moments/friendships/coop | `frames` |

**Phân quyền admin**: quản lý user (list/search, khóa/mở = `updateUser({disabled})` + revoke refresh token, cấp/THU quyền), thống kê (+ theo ngày), quản lý khung (CRUD/grant/owners), **kiểm duyệt bài đăng**, **audit log** mọi hành động admin.

> Hằng số giới hạn đặt tên trong `common/constants.ts` — không hardcode: `MAX_FRIENDS=20`, `MAX_GROUP_SIZE=20`, `MAX_VIDEO_SECONDS=5`, `STREAK_WINDOW_HOURS=24`, `DAILY_QUESTS_PER_DAY=2` (tạm — thiết kế 3 khi có AI), `STREAK_MILESTONES=[3,7,14,30]`, `COOP_INVITE_TTL_HOURS=24`, `INVITE_LINK_TTL_DAYS=30`.

---

> **Nhắc lại:** còn phân vân → **hỏi user**. Sửa code (dù nhỏ) → **cập nhật `server/GUIDE.md`** (+ `SECURITY.md` nếu đụng bảo mật).

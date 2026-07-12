# CLAUDE.md — Ruler cho **Snapget Server** (NestJS)

> File quy ước cho toàn bộ code trong `d:\DATN\server`.
> Đọc file này **trước** khi tạo/sửa bất kỳ thứ gì trong server.
> Bằng tiếng Việt cho phần giải thích; **code + tên định danh luôn tiếng Anh**.

---

## 0. Nguyên tắc tối thượng (kế thừa từ root)

1. **Luôn hỏi** vài câu trước khi bắt đầu việc gì còn mơ hồ (không tự đoán khi chưa chắc).
2. **Luôn đọc & cập nhật `server/GUIDE.md` sau MỖI lần chỉnh sửa code** — không đợi "xong tính năng". Bắt buộc giữ đồng bộ 3 thứ trong GUIDE:
   - **Cây thư mục + ý nghĩa từng thư mục/file** (để lần đọc code sau nhanh hơn, không phải quét lại cả project).
   - **Bảng task**: ✅ đã làm · 🔄 đang làm · ⬜ chưa làm.
   - **Tiến độ** từng module (%, hoặc trạng thái).
   Sửa code mà không cập nhật GUIDE = **chưa xong việc**.
3. Server là **một phần của monorepo DATN**; contract API phải khớp app Android (`Snapget/`) và admin (`admin/`). Khi đổi contract → ghi chú vào GUIDE và nhắc user cập nhật app.

---

## 1. Server này là gì

Server **NestJS (Node + TypeScript)** làm **cửa ngõ duy nhất** giữa app và Firebase.

```
App Android ─┐
             ├─► NestJS API (server này) ─► Firebase Admin SDK ─► Firestore / Auth / FCM
Admin (React)┘
```

- App **không** gọi Firestore trực tiếp nữa → gọi REST API của server.
- Server verify auth, validate input, áp business rule, rồi mới chạm Firebase.
- Domain modules (theo thiết kế thực thể — xem mục 6): `users`, `friendships`, `moments` (bài đăng/ảnh — code cũ gọi `posts`), `reactions`, `messages` (+ nhóm chat), `frames` (khung ảnh phần thưởng), `quests` (daily quest), `admin` (quản lý user + thống kê). Cộng module hạ tầng: `auth`, `firebase`, `common`, `config`.
- Actor (theo phân tích thiết kế): **Member** (đăng nhập/đăng xuất) → kế thừa bởi **User** (dùng app) và **Admin** (quản trị); **AI** (tạo + xác minh daily quest, làm cuối). Phân quyền map qua role — xem mục 5 & 16.

---

## 2. Tech stack (đã CHỐT — không đổi nếu chưa hỏi user)

| Hạng mục | Lựa chọn |
|---|---|
| Runtime | **Node 20 LTS** |
| Package manager | **npm** (dùng `package-lock.json`, không xài pnpm/yarn) |
| Framework | **NestJS** (TypeScript strict) |
| Persistence | **Cloud Firestore** qua **`firebase-admin`** SDK (KHÔNG dùng ORM/SQL) |
| Auth app | Verify **Firebase ID token** (Admin SDK) trong Guard |
| Auth admin | Server **tự phát JWT riêng** cho luồng admin (`@nestjs/jwt`) |
| Validation | `class-validator` + `class-transformer` + global `ValidationPipe` |
| Docs | **Swagger** (`@nestjs/swagger`) tại `/docs` |
| Test | **Jest** — unit (`*.spec.ts`) + e2e (`test/*.e2e-spec.ts`), **đầy đủ** |
| Lint/format | **ESLint + Prettier** (config chuẩn NestJS) |
| Commit | **Conventional Commits** |
| Config | `@nestjs/config` + `.env` (validate bằng schema) |

> ❌ Không thêm: SQL/Prisma/TypeORM, Docker (giai đoạn này), license header. Nếu cần → hỏi user trước.

---

## 3. Cấu trúc thư mục

```
server/
├── src/
│   ├── main.ts                  # bootstrap: global pipe/filter/interceptor, Swagger, CORS, prefix /api
│   ├── app.module.ts            # root module, import ConfigModule + tất cả feature module
│   │
│   ├── config/                  # cấu hình tập trung
│   │   ├── config.module.ts
│   │   └── env.validation.ts    # validate biến môi trường (Joi/zod)
│   │
│   ├── firebase/                # tích hợp Firebase Admin (dùng chung toàn app)
│   │   ├── firebase.module.ts   # @Global — provide FirebaseAdmin
│   │   └── firebase.service.ts  # khởi tạo app admin, expose auth(), firestore(), messaging()
│   │
│   ├── common/                  # tiện ích dùng chung, KHÔNG chứa business logic
│   │   ├── decorators/          # @CurrentUser(), @Roles(), @Public()
│   │   ├── dto/                  # PaginationDto, ApiResponseDto (envelope)
│   │   ├── filters/             # AllExceptionsFilter (global)
│   │   ├── interceptors/        # ResponseInterceptor (bọc envelope)
│   │   ├── guards/              # FirebaseAuthGuard, AdminJwtGuard, RolesGuard
│   │   └── utils/
│   │
│   ├── auth/                    # xác thực: /auth/admin/login, verify token...
│   │   ├── auth.module.ts
│   │   ├── auth.controller.ts
│   │   ├── auth.service.ts
│   │   └── strategies/          # nếu dùng passport
│   │
│   └── <domain>/                # users, posts, messages, friendships, notifications, settings
│       ├── <domain>.module.ts
│       ├── <domain>.controller.ts
│       ├── <domain>.service.ts       # business logic
│       ├── <domain>.repository.ts    # NƠI DUY NHẤT chạm Firestore của domain
│       ├── dto/
│       │   ├── create-<x>.dto.ts
│       │   └── update-<x>.dto.ts
│       └── entities/            # kiểu dữ liệu domain (map từ Firestore doc)
│
├── test/                        # e2e specs
├── .env / .env.example
├── firebase-service-account.json   # 🔒 KHÔNG commit (xem mục 9)
├── nest-cli.json
├── package.json
├── tsconfig.json
├── .eslintrc.js / .prettierrc
└── GUIDE.md                     # bản đồ server (bắt buộc duy trì)
```

**Quy tắc phân tầng (bắt buộc):**
`Controller` (nhận request, DTO, HTTP) → `Service` (business logic) → `Repository` (Firestore). Controller **không** gọi thẳng Repository; Service **không** đụng `req`/`res`.

---

## 4. Quy ước code

- **Tên định danh (biến/hàm/class/file) tiếng Anh, chuẩn NestJS.** Comment giải thích + Swagger description + GUIDE = **tiếng Việt**.
- File đặt tên `kebab-case`: `users.service.ts`, `create-post.dto.ts`. Class `PascalCase`, biến/hàm `camelCase`.
- **TypeScript strict**: không `any` tùy tiện; ưu tiên type/interface rõ ràng. Dữ liệu ra/vào API luôn qua DTO có `class-validator`.
- Async: dùng `async/await`, không để promise nổi lơ lửng (bật `no-floating-promises`).
- Không log secret/token. Dùng `Logger` của Nest, không `console.log` trong code chạy thật.
- Mỗi endpoint public phải có Swagger decorator (`@ApiOperation`, `@ApiResponse`) mô tả **tiếng Việt**.
- Chạy `npm run lint` + `npm run format` trước khi coi là xong.

---

## 5. Xác thực & phân quyền (2 luồng riêng)

### 5.1 App Android → Firebase ID token
- App gửi header `Authorization: Bearer <firebase-id-token>`.
- `FirebaseAuthGuard` verify bằng `admin.auth().verifyIdToken(token)` → gắn `req.user = { uid, email, ... }`.
- Endpoint cần đăng nhập gắn guard này; endpoint mở gắn `@Public()`.
- Lấy user hiện tại qua decorator `@CurrentUser()`.

### 5.2 Admin (React) → JWT của server
- Admin đăng nhập `/auth/admin/login`. Server verify danh tính qua Firebase, **kiểm tra quyền admin** (xem giả định bên dưới), rồi **tự phát JWT** (`@nestjs/jwt`, có `access` + tùy chọn `refresh`).
- `AdminJwtGuard` + `RolesGuard` bảo vệ route admin. Dùng `@Roles('admin')`.

> **Giả định cần user xác nhận:** quyền admin xác định bằng **Firebase custom claims** `{ admin: true }` **hoặc** field `role: 'admin'` trong Firestore collection `users`. Mặc định dùng **custom claims**; đổi nếu user muốn khác.

---

## 6. Tầng dữ liệu — mô hình thực thể → Firestore

- **Repository là nơi DUY NHẤT** import/chạm `firestore()`. Service/Controller tuyệt đối không query Firestore trực tiếp.
- **Nguồn chuẩn của domain** = file *"thiết kế các lớp thực thể trong database"* + *"Phân tích thiết kế app"*. Thiết kế gốc **quan hệ (PK/FK, bảng trung gian)**; ta hiện thực trên **Firestore (NoSQL)** nên **bảng trung gian → subcollection hoặc mảng** (không tạo collection nối riêng trừ khi cần query ngược).

### 6.1 Thực thể → Firestore collection (canonical)

| Thực thể (thiết kế) | Firestore | Field chính (camelCase khi lưu) | Ghi chú NoSQL |
|---|---|---|---|
| **User** | `users/{uid}` | `email`, `fullName`, `avatar`, `joinDate`, `personalStreak`, `inviteLink`, `fcmTokens[]` | `uid` = Firebase Auth uid. `password` do Firebase Auth giữ, **KHÔNG** lưu ở Firestore. |
| **Frame** | `frames/{id}` | `frameName` (tên/URL khung) | Catalog do **admin** quản lý (thêm/xóa) |
| **User_Frame** | `users/{uid}.unlockedFrames[]` | mảng `frameId` | Junction → **mảng trên user doc** |
| **Friendship** | `friendships/{pairId}` | `userIds[]` (2 uid, sort để query), `user1Id`, `user2Id`, `friendStreak`, `lastInteractionAt`, `status`, `createdAt` | `pairId` = 2 uid ghép sort. `lastInteractionAt` phục vụ reset streak >24h |
| **Moment** (code cũ: post) | `posts/{id}` | `userId`, `contentType` (PHOTO/VIDEO), `mediaUrl`, `frameId?`, `postTime`, `createdAt` | = "bài đăng/khoảnh khắc" |
| **Moment_View** | `posts/{id}/views/{viewerId}` | `viewerId`, `isSeen`, `seenAt` | Junction → **subcollection** |
| **Reaction** | `posts/{id}/reactions/{id}` | `reactorId`, `emojiType`, `createdAt` | Junction → **subcollection** |
| **Chat_Group** | `chatGroups/{id}` | `groupName`, `memberIds[]` (≤20) | Group_Member gộp vào `memberIds[]` |
| **Message** | `messages/{id}` | `senderId`, `receiverId?`, `groupId?`, `messageType` (TEXT/VOICE/EMOJI/STICKER/PHOTO), `content`, `sendTime`, `isSeen` | 1-1 → có `receiverId`; nhóm → có `groupId` (một trong hai null) |
| **Daily_Quest** | `dailyQuests/{id}` | `content`, `releaseDate` | **Mỗi ngày hệ thống tạo 3 quest chung**. AI làm cuối |
| **User_Quest** | `userQuests/{id}` | `questId`, `userId`, `proofUrl`, `status` (PENDING/COMPLETED) | Ảnh nộp để AI xác minh |

> ⚠️ **Prototype hiện tại lệch với thiết kế**: code app cũ đang có collection `posts` với field `postType/caption/thumbnailUrl/visibility/tags`, và có `notifications`, `settings`. Khi migrate: **theo thiết kế thực thể ở trên là chuẩn**; field cũ nào không có trong thiết kế thì giữ lại nếu app còn dùng, hoặc dọn dần (ghi rõ trong GUIDE). `notifications` giữ cho FCM; `settings` là cấu hình UI của app, không thuộc thiết kế CSDL lõi.

### 6.2 Gotcha kế thừa từ app (giữ nguyên trừ khi user cho tạo index)
- App cố tình **chỉ dùng 1 filter server-side** (equality hoặc `array-contains`) để né composite index; phần sort/lọc còn lại làm trong bộ nhớ.
- **Server có Admin SDK → được phép tạo composite index** và query đầy đủ hơn. Khi thêm query cần index: hoặc tạo index trên Firebase Console (ghi vào GUIDE), hoặc giữ pattern 1-filter. **Nêu rõ lựa chọn trong PR/commit.**
- `whereIn` giới hạn 10 phần tử → `chunked(10)` khi query nhiều id.

### 6.3 Mapping
- Repository chịu trách nhiệm map Firestore doc → entity (và ngược lại). Xử lý `Timestamp` ↔ ISO string ở tầng này, không rò rỉ kiểu Firestore ra Controller.

---

## 7. Response envelope & xử lý lỗi (CHỐT)

Mọi response bọc envelope thống nhất qua **global `ResponseInterceptor`**; mọi lỗi qua **global `AllExceptionsFilter`**.

```ts
// Thành công
{ "success": true,  "statusCode": 200, "message": "OK", "data": { /* ... */ } }
// Lỗi
{ "success": false, "statusCode": 404, "message": "Không tìm thấy bài viết", "data": null }
```

- Ném lỗi bằng `HttpException` của Nest (`NotFoundException`, `BadRequestException`, ...). Filter tự chuẩn hóa về envelope.
- `message` lỗi trả cho client viết **tiếng Việt, thân thiện**; chi tiết kỹ thuật cho vào log.
- Có phân trang → `data` gồm `{ items, page, limit, total }` (chuẩn hóa trong `PaginationDto`).

---

## 8. Validation / DTO

- Mọi body/query/param vào API đi qua DTO có `class-validator`. Bật global `ValidationPipe({ whitelist: true, transform: true, forbidNonWhitelisted: true })`.
- DTO tạo/sửa tách riêng (`Create*Dto`, `Update*Dto` — `Update` dùng `PartialType`).
- Không nhận field thừa; không tin dữ liệu client.

---

## 9. Config & Secrets

- Toàn bộ config qua `@nestjs/config`, đọc `.env`, **validate lúc khởi động** (`env.validation.ts`) — thiếu biến thì fail sớm.
- **Firebase Admin credential:** dùng service account key. Mặc định đọc từ đường dẫn trong env `GOOGLE_APPLICATION_CREDENTIALS` hoặc `FIREBASE_SERVICE_ACCOUNT` (JSON/base64).
- 🔒 **Không commit:** `.env`, `firebase-service-account.json`. Luôn cập nhật `.gitignore`.
- Service account của server **khác** `google-services.json` của app — đây là **Admin** credential.

Biến môi trường tối thiểu:
```
PORT=3000
NODE_ENV=development
CORS_ORIGINS=http://localhost:5173      # admin React (Vite)
FIREBASE_SERVICE_ACCOUNT=./firebase-service-account.json
FIREBASE_PROJECT_ID=...
JWT_SECRET=...                          # cho luồng admin
JWT_EXPIRES_IN=1d
```

---

## 10. Testing (đầy đủ)

- **Unit** (`*.spec.ts`) cạnh file nguồn: test Service (mock Repository), Guard, util. Không gọi Firebase thật trong unit → **mock** `FirebaseService`.
- **E2E** (`test/*.e2e-spec.ts`): test endpoint qua `supertest`. Dùng Firebase emulator hoặc mock; **không đụng dữ liệu production**.
- Lệnh: `npm run test`, `npm run test:e2e`, `npm run test:cov`.
- Thêm/đổi logic nghiệp vụ → cập nhật hoặc thêm test tương ứng.

---

## 11. Swagger

- Bật tại `/docs`. Group theo tag = tên domain. Mọi DTO có `@ApiProperty` mô tả tiếng Việt.
- Khai báo Bearer auth (cả Firebase token lẫn admin JWT) để test ngay trên UI.
- Swagger là công cụ demo chính khi bảo vệ DATN → giữ luôn đầy đủ, cập nhật.

---

## 12. Git & Commit

- **Conventional Commits**: `feat(posts): ...`, `fix(auth): ...`, `docs(guide): ...`, `test(users): ...`, `chore: ...`.
- Commit nhỏ, theo module. Không commit secret/build artifact.
- Chỉ commit/push khi user yêu cầu.

---

## 13. Lệnh thường dùng

```bash
npm install
npm run start:dev      # dev, watch mode
npm run start:prod
npm run build
npm run lint
npm run format
npm run test           # unit
npm run test:e2e
npm run test:cov
```

---

## 14. Quy trình chuẩn

### Thêm 1 module domain mới
1. `nest g module <name>` → `resource`/controller/service, tạo thêm `<name>.repository.ts`.
2. Định nghĩa entity + DTO (`class-validator`, `@ApiProperty` tiếng Việt).
3. Repository: map Firestore ↔ entity (giữ đúng tên field mục 6).
4. Service: business logic + phân quyền.
5. Controller: route + guard + Swagger.
6. Viết unit test (service) + e2e (endpoint).
7. Cập nhật `GUIDE.md`.

### Thêm 1 endpoint
1. DTO input + validate. 2. Method trong Service. 3. Query trong Repository (nêu rõ nếu cần index). 4. Route + guard + Swagger. 5. Test. 6. Đảm bảo trả đúng envelope.

---

## 15. Contract với app Android (đừng phá)

- App hiện gọi Firestore trực tiếp; đích đến là gọi **REST API server này** (sửa `FirestoreRepository`/thêm Retrofit service, gắn Bearer token vào interceptor rỗng trong `NetworkModule`).
- Giữ tên field & enum khớp app để app chuyển đổi mượt. Đổi contract → ghi GUIDE + báo user.
- FCM push: gửi qua Admin SDK (`messaging()`), token lấy từ `users.fcmTokens[]`.

---

## 16. Nghiệp vụ & business rules (server là nơi ENFORCE thật)

Client chỉ chặn UX; **server mới là nơi validate ràng buộc thật**. Các luật rút từ *Phân tích thiết kế app*:

| Luật | Chi tiết | Nơi enforce |
|---|---|---|
| Giới hạn bạn bè | **≤ 20** mỗi user; kết bạn phải **kiểm tra giới hạn của cả 2 phía** trước khi thêm | `friendships` service |
| Kết bạn qua link | User tạo `inviteLink`; người nhận mở link → xác nhận → nếu cả 2 chưa đầy 20 thì thêm vào danh sách của nhau | `friendships` |
| Nhóm chat | thành viên **≤ 20** (≤ số bạn bè) | `messages`/`chatGroups` |
| Video | **≤ 5 giây** (client cắt, server có thể kiểm tra metadata) | `moments` upload |
| Friend streak | tăng +1 khi **A đăng moment hiển thị cho B, hoặc A phản hồi B trong 24h** (tương tác qua lại). **>24h không tương tác → reset 0**. Mỗi cặp bạn **1 streak chung**. | `friendships` (dựa `lastInteractionAt`) |
| Personal streak | ngày liên tiếp mở app + upload ≥1 moment. Mỗi user **1 streak cá nhân**. | `users` |
| Moment view | hệ thống tự đánh dấu `isSeen` khi bạn bè lướt qua feed | `moments` |
| Message seen | cập nhật `isSeen` khi người nhận mở xem | `messages` |
| Co-op capture | 1 user chụp nửa ảnh + gửi yêu cầu → bạn chấp nhận chụp nửa còn lại → **server ghép 2 ảnh** thành 1 moment đăng cho cả 2 | `moments` (giai đoạn sau) |
| Daily quest | **mỗi ngày tạo 3 quest chung**; user nộp `proofUrl` → **AI xác minh** → `COMPLETED` → thưởng frame. AI làm **cuối cùng**. | `quests` + AI |
| Reward frame | user sở hữu nhiều frame, áp lên nhiều moment | `frames`/`users.unlockedFrames` |

### Phân quyền (actor → chức năng)
- **User**: đăng ký, quản lý hồ sơ, quản lý bạn bè (≤20), đăng moment, chụp chung, xem/ tương tác feed, nhắn tin, streak, daily quest.
- **Admin** (route riêng, `AdminJwtGuard` + `@Roles('admin')`): **quản lý user** (xem danh sách, tìm kiếm, **khóa/mở khóa** — dùng `admin.auth().updateUser({disabled})`), **xem thống kê**, **quản lý frame** (thêm/xóa), **tạo tài khoản admin mới**.
- **AI**: tạo daily quest + xác minh ảnh quest (module `quests`, tích hợp cuối).

> Đặt các hằng số giới hạn thành constant có tên trong `common/` (vd `MAX_FRIENDS = 20`, `MAX_GROUP_SIZE = 20`, `MAX_VIDEO_SECONDS = 5`, `STREAK_WINDOW_HOURS = 24`, `DAILY_QUESTS_PER_DAY = 3`) — không hardcode rải rác.

---

> **Nhắc lại:** còn phân vân → **hỏi user**. Sửa code (dù nhỏ) → **cập nhật `server/GUIDE.md`** (cây thư mục + task + tiến độ).

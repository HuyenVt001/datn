# 🔐 SECURITY.md — Hệ thống bảo mật Snapget

> **Phạm vi:** app Android (`Snapget/`) · server NestJS (`server/`) · web admin (`admin/`) · Firebase Hosting (`hosting/`, `admin/`).
> **Cập nhật lần cuối:** 2026-08-05 — thêm **[mục 17 — Thanh toán PayOS & tiền tệ Astrite](#17-thanh-toán-payos--tiền-tệ-astrite)**: đây là phần đầu tiên của hệ thống đụng **tiền thật**, nên có mô hình đe doạ riêng (webhook giả mạo, cộng tiền 2 lần, client tự khai số tiền). Trước đó 2026-08-03 — vá luồng đăng xuất phía app: `LoginScreen` dùng chung `AuthViewModel` scope Activity (trước đây instance riêng làm Sign Out không điều hướng về Login khi login/logout cùng phiên → kẹt lại với 401 "Thieu token", xem [3.4](#34-phản-ứng-khi-phiên-bị-thu-hồi-app)); Google re-login không còn PATCH tên/avatar đè hồ sơ server. Trước đó 2026-08-02 — (1) nhóm chat quản lý được (rename/avatar/mời/xóa/rời/mute): ma trận ownership [4.2](#42-ma-trận-kiểm-soát-quyền-sở-hữu-ownership) — mọi thao tác nhóm qua `requireMembership`, thêm thành viên tái dùng rào chắn bạn-bè `assertAllFriendsOf`, xóa thành viên chỉ `createdBy`; (2) đại tu co-op: poll/nộp nửa ảnh chỉ 2 người trong lời mời (`assertParticipant`), accept chỉ invitee, decline mở cho cả inviter hủy lời mời PENDING của mình, TTL rút còn 5 phút (thu hẹp cửa sổ tấn công). Trước đó 2026-07-28: siết `.gitignore` 5 lớp ([10.3](#103-hàng-rào-gitignore-5-lớp-đã-siết-2026-07-28)) + hardening app Android ([mục 8](#8-bảo-mật-app-android)).
> **Trạng thái:** tài liệu sống — **bắt buộc cập nhật mỗi khi có thay đổi liên quan bảo mật** (xem [mục 16](#16-quy-tắc-bảo-trì-tài-liệu-bắt-buộc)).

> ⚠️ **Tài liệu này KHÔNG chứa giá trị bí mật thật** (JWT secret, Cloudinary secret, service account key…). Bí mật chỉ nằm trong `.env` / Secret Files trên môi trường chạy, không bao giờ ghi vào tài liệu hay commit vào git.

---

## 0. Cách đọc tài liệu này

Mỗi biện pháp bảo mật được đánh dấu trạng thái:

| Ký hiệu | Ý nghĩa |
|---|---|
| ✅ | Đã triển khai, đang hoạt động |
| ⚠️ | Thiếu hoặc chưa đầy đủ — cần bổ sung, chưa gây rủi ro trực tiếp |
| 🔴 | Rủi ro cao — cần xử lý sớm |
| 🧭 | Quyết định thiết kế có chủ đích (chấp nhận đánh đổi, ghi rõ lý do) |

Mọi tham chiếu code đều là **đường dẫn thật + số dòng**, bấm được trong VS Code.

---

## 1. Kiến trúc bảo mật & ranh giới tin cậy

### 1.1 Sơ đồ tổng thể

```
┌──────────────────┐                                    ┌────────────────────┐
│  App Android     │  Firebase ID token (Bearer)        │  Firebase Auth     │
│  (Kotlin)        │───────────────┐                    │  (định danh gốc)   │
└──────────────────┘               │                    └────────────────────┘
                                   ▼                              ▲
┌──────────────────┐      ┌──────────────────────┐                │ Admin SDK
│  Web Admin       │      │   NestJS API         │                │ (toàn quyền)
│  (React SPA)     │─────►│   ⚑ CỬA NGÕ DUY NHẤT │────────────────┤
└──────────────────┘ JWT  │   guard→validate→    │                ▼
                   server │   business rule      │    ┌────────────────────┐
                          └──────────┬───────────┘    │  Cloud Firestore   │
                                     │                └────────────────────┘
                                     ▼                ┌────────────────────┐
                          ┌────────────────────┐      │  FCM (push)        │
                          │  Cloudinary (media)│      └────────────────────┘
                          └────────────────────┘
```

### 1.2 Nguyên tắc nền tảng

1. **Server là cửa ngõ duy nhất.** App Android **không** đọc/ghi Firestore trực tiếp nữa (dọn xong 2026-07-13, xác nhận: `grep firestore.collection` trong mã Kotlin = 0 kết quả). Web admin cũng **không** chạm Firestore — chỉ gọi REST API ([admin/src/auth/firebase.ts](admin/src/auth/firebase.ts) chỉ dùng Firebase Web SDK cho bước đăng nhập).
2. **Client chỉ chặn UX, server mới enforce thật.** Mọi ràng buộc nghiệp vụ (≤20 bạn, ≤20 thành viên nhóm, video ≤5s, chỉ bạn bè xem moment…) đều được kiểm tra lại ở server.
3. **Không tin claim cũ.** Với luồng admin, quyền được đọc lại từ Firebase **mỗi request**, không tin `role` nằm trong JWT đã phát.
4. **Bí mật không rời server.** Cloudinary API secret, Firebase service account chỉ tồn tại phía server; app và admin không bao giờ cầm.

### 1.3 Ranh giới tin cậy (trust boundaries)

| # | Ranh giới | Ai kiểm soát | Cơ chế bảo vệ |
|---|---|---|---|
| B1 | App Android → NestJS | Người dùng cuối (không tin) | `FirebaseAuthGuard` verify ID token |
| B2 | Web admin → NestJS | Quản trị viên (không tin trình duyệt) | `AdminJwtGuard` + `RolesGuard` |
| B3 | NestJS → Firebase | Server (tin cậy, dùng Admin SDK) | Service account, **bỏ qua mọi Firestore Rules** |
| B4 | NestJS → Cloudinary | Server (tin cậy) | API key/secret trong env |
| B5 | **Client → Firebase trực tiếp** | ⚠️ Không đi qua server | Chỉ còn Firebase **Auth** (đăng nhập). Firestore/Storage phải bị chặn bằng Rules — xem [mục 12](#12-ranh-giới-nằm-ngoài-nestjs-điểm-yếu-chí-mạng-nếu-bỏ-quên) |

> 🔴 **Điểm quan trọng nhất của toàn bộ tài liệu:** ranh giới **B5**. Mọi công sức phòng thủ trong NestJS sẽ **vô nghĩa** nếu Firestore Security Rules đang mở (test mode), vì Firebase API key là công khai và kẻ tấn công có thể nói chuyện thẳng với Firestore, bỏ qua toàn bộ guard.

---

## 2. Tài sản cần bảo vệ & mô hình mối đe dọa

### 2.1 Tài sản (xếp theo mức thiệt hại nếu mất)

| Hạng | Tài sản | Nơi lưu | Thiệt hại nếu lộ |
|---|---|---|---|
| 1 | **Firebase service account key** | `server/` (dev), Render Secret Files (prod) | Toàn quyền Firestore + Auth + FCM, bỏ qua mọi rule → mất trắng dữ liệu người dùng |
| 2 | **JWT_SECRET** | `server/.env` | Giả mạo JWT admin bất kỳ → chiếm toàn bộ quyền quản trị |
| 3 | **PAYOS_CHECKSUM_KEY** | `server/.env` (dev), Render env (prod) | Ký được webhook giả → **tự cộng Astrite cho mình mà không trả đồng nào**. Xem [17](#17-thanh-toán-payos--tiền-tệ-astrite) |
| 4 | **PAYOS_API_KEY + PAYOS_CLIENT_ID** | như trên | Tạo/huỷ link thanh toán dưới danh nghĩa kênh "Snapget", đọc lịch sử giao dịch |
| 5 | **Cloudinary API secret** | `server/.env` | Xóa/thay toàn bộ ảnh, video của người dùng |
| 6 | **Ảnh/video riêng tư của user** | Cloudinary | Rò rỉ đời tư — tài sản nhạy cảm nhất với người dùng cuối |
| 7 | **Nội dung tin nhắn** | Firestore `messages` | Rò rỉ đời tư |
| 8 | **PII**: email, tên, ngày sinh, danh sách bạn bè | Firestore `users`, Firebase Auth | Rò rỉ định danh |
| 9 | **Phiên admin** (JWT trong localStorage) | Trình duyệt admin | Chiếm quyền quản trị trong tối đa 24h |

### 2.2 Kẻ tấn công giả định

| Tác nhân | Năng lực | Mục tiêu điển hình |
|---|---|---|
| **A1 — Người dùng hợp lệ tò mò** | Có tài khoản thật, đọc được mọi request app gửi đi, sửa được request | Xem moment/tin nhắn của người lạ (IDOR), lách giới hạn bạn bè, spam |
| **A2 — Người ngoài không tài khoản** | Gọi được API công khai + Firebase API key công khai. 🔴 **Quan trọng: repo GitHub là PUBLIC** (`HuyenVt001/datn`) ⇒ kẻ tấn công **đọc được toàn bộ mã nguồn, biết chính xác mọi endpoint, mọi điều kiện guard, và đọc được cả lịch sử git** | Truy cập Firestore trực tiếp, brute-force đăng nhập, quét endpoint, **thu hoạch secret trong lịch sử commit** |
| **A3 — Kẻ chiếm được thiết bị Android** | Cầm máy đã mở khóa hoặc bản backup | Trích xuất token, đọc cache ảnh, đọc SharedPreferences |
| **A4 — Kẻ chiếm được phiên admin** | XSS trên trang admin hoặc lấy được JWT | Xóa bài, khóa user, **tự cấp thêm quyền admin** |
| **A5 — Kẻ nghe lén mạng** | Wi-Fi công cộng, MITM | Bắt token nếu traffic đi HTTP trần |

### 2.3 Bản đồ mối đe dọa → biện pháp đối phó

| Mối đe dọa | Đối phó chính | Mục |
|---|---|---|
| Giả mạo danh tính | Verify Firebase ID token / JWT server mỗi request | [3](#3-định-danh--xác-thực) |
| Truy cập tài nguyên của người khác (IDOR) | Ownership check ở service, 7/7 module | [4.2](#42-ma-trận-kiểm-soát-quyền-sở-hữu-ownership) |
| Leo thang đặc quyền | `AdminJwtGuard` re-check quyền, chống thu quyền admin cuối cùng | [4.3](#43-bảo-vệ-chức-năng-quản-trị) |
| Mass assignment | `forbidNonWhitelisted` + build patch tường minh | [5](#5-xác-thực-đầu-vào--chống-lạm-dụng-dữ-liệu) |
| Injection (SQL/NoSQL) | Không có SQL; Firestore query tham số hóa | [5.3](#53-injection) |
| DoS / spam | Rate limit toàn cục + siết riêng login | [6.3](#63-chống-lạm-dụng-rate-limiting) |
| Nghe lén | HTTPS bắt buộc, chặn cleartext ở release | [6.1](#61-tầng-vận-chuyển) · [8.2](#82-tầng-mạng) |
| Trích xuất từ thiết bị | Firebase SDK giữ token; cần tắt backup | [8.1](#81-lưu-trữ-token--dữ-liệu-nhạy-cảm) |
| XSS chiếm phiên admin | React auto-escape + cần CSP | [9.4](#94-xss--csp) |
| Rò rỉ media | ⚠️ URL Cloudinary công khai | [7.3](#73-điểm-yếu-đã-biết-của-tầng-media) |
| **Webhook thanh toán giả mạo** | Verify chữ ký HMAC bằng `PAYOS_CHECKSUM_KEY`, sai → 401 | [17.3](#173-webhook--bề-mặt-tấn-công-nguy-hiểm-nhất) |
| **Cộng tiền 2 lần (replay webhook)** | Doc id = `orderCode` + transaction chỉ cộng khi chưa `PAID` | [17.4](#174-idempotent--không-bao-giờ-cộng-hai-lần) |
| **Client tự khai số tiền** | Body chỉ nhận `packageId`; giá tra từ `topupPackages` ở server | [17.2](#172-luồng-tạo-đơn--server-là-nơi-định-giá) |

---

## 3. Định danh & xác thực

Hệ thống có **2 luồng xác thực hoàn toàn tách biệt**. Đây là quyết định thiết kế cốt lõi — không được trộn lẫn.

### 3.1 Luồng APP (Android) — Firebase ID token

```
User ──email/password──► Firebase Auth ──► ID token (JWT của Google, TTL 1h)
                                              │
App ── Authorization: Bearer <ID token> ──────┴──► NestJS FirebaseAuthGuard
                                                   verifyIdToken() ──► req.user = {uid, email, admin}
```

| Hạng mục | Hiện trạng | Vị trí |
|---|---|---|
| Đăng ký / đăng nhập | ✅ Firebase Auth (email/password + Google Sign-In) — **server không có endpoint đăng nhập user** | [AuthRepository.kt](Snapget/app/src/main/java/com/example/snapget/feature/auth/data/AuthRepository.kt) |
| Gắn token | ✅ Interceptor tự lấy `getIdToken(false)`, SDK tự refresh khi hết hạn | [AuthInterceptor.kt:38](Snapget/app/src/main/java/com/example/snapget/core/network/interceptor/AuthInterceptor.kt#L38) |
| **Lọc host trước khi gắn token** | ✅ 🧭 Chỉ gắn cho host server; OkHttpClient dùng chung với Coil nên không lọc thì token rò sang Cloudinary/DiceBear/Twemoji | [AuthInterceptor.kt:33](Snapget/app/src/main/java/com/example/snapget/core/network/interceptor/AuthInterceptor.kt#L33) |
| Verify phía server | ✅ `admin.auth().verifyIdToken()` | [firebase-auth.guard.ts:34](server/src/common/guards/firebase-auth.guard.ts#L34) |
| Endpoint mở | ✅ Chỉ qua `@Public()`, hiện chỉ `/api/health` và `/auth/admin/login` | [public.decorator.ts](server/src/common/decorators/public.decorator.ts) |
| Xử lý 401 phía app | ✅ **Đã thêm 2026-07-28** — [TokenAuthenticator.kt](Snapget/app/src/main/java/com/example/snapget/core/network/interceptor/TokenAuthenticator.kt): gặp 401 → ép `getIdToken(true)` rồi thử lại; vẫn 401 → `signOut()`. Xem [3.4](#34-phản-ứng-khi-phiên-bị-thu-hồi-app) | — |

### 3.2 Luồng ADMIN (React) — JWT của server

```
Admin ──email/password──► Firebase Auth ──► ID token
      ──POST /auth/admin/login {idToken}──► NestJS
                                             ├─ verifyIdToken()
                                             ├─ getUser() → đọc claim admin HIỆN TẠI + disabled
                                             └─ ký JWT riêng HS256 {sub, email, role:'admin'}, TTL 1 ngày
Admin ── Authorization: Bearer <JWT server> ──► AdminJwtGuard (verify + RE-CHECK Firebase mỗi request)
```

| Hạng mục | Hiện trạng | Vị trí |
|---|---|---|
| Đổi ID token lấy JWT | ✅ | [auth.service.ts:18-41](server/src/auth/auth.service.ts#L18-L41) |
| **Không tin claim trong token cũ** | ✅ 🧭 Đọc quyền bằng `getUser()` chứ không đọc `decoded.admin` | [auth.service.ts:26-29](server/src/auth/auth.service.ts#L26-L29) |
| Chặn tài khoản bị khóa | ✅ | [auth.service.ts:30-32](server/src/auth/auth.service.ts#L30-L32) |
| Thuật toán / TTL | ✅ HS256, `JWT_EXPIRES_IN=1d`, secret bắt buộc từ env | [auth.module.ts](server/src/auth/auth.module.ts) |
| Rate limit riêng cho login | ✅ 10 request/60s | [auth.controller.ts:14](server/src/auth/auth.controller.ts#L14) |
| **Re-check quyền mỗi request** | ✅ 🧭 Thu quyền/khóa tài khoản có hiệu lực **tức thì**, không phải đợi JWT hết hạn | [admin-jwt.guard.ts:39-51](server/src/common/guards/admin-jwt.guard.ts#L39-L51) |
| Refresh token | ⚠️ Không có — admin phải đăng nhập lại sau 1 ngày (hạn chế UX, không phải lỗ hổng) | — |
| Logout phía server / denylist | ⚠️ Không có `jti`, không revoke được JWT đã phát | — |
| MFA cho admin | ⚠️ Không có | — |

> 🧭 **Vì sao thiếu refresh token vẫn chấp nhận được:** cơ chế thu hồi thực chất nằm ở `AdminJwtGuard` — mỗi request đều hỏi lại Firebase "người này còn quyền admin không, có bị khóa không". Muốn cắt phiên của một admin ngay lập tức: thu quyền admin của họ trong trang Người dùng, phiên chết ở request kế tiếp.

### 3.3 Vòng đời & thu hồi phiên

| Sự kiện | Hệ quả | Độ trễ |
|---|---|---|
| Admin bị thu quyền | Mọi request admin trả 401 | Tức thì (request kế tiếp) |
| User bị khóa (`disabled`) | `revokeRefreshTokens(uid)` được gọi → ID token không refresh được nữa | ≤ 1 giờ (hết hạn ID token hiện tại) — [admin.service.ts:126-128](server/src/admin/admin.service.ts#L126-L128) |
| User đăng xuất trên app | `auth.signOut()` + gỡ FCM token khỏi server | Tức thì |
| JWT admin bị lộ | Vẫn dùng được đến khi hết 1 ngày **hoặc** đến khi thu quyền admin của tài khoản đó | — |

### 3.4 Phản ứng khi phiên bị thu hồi (app)

✅ **Thêm 2026-07-28.** Trước đó app **không hề xử lý 401**: token hết hạn giữa chừng hoặc bị server thu hồi thì mọi request đều 401, UI chỉ hiện lỗi chung chung, người dùng kẹt ở màn hình trống mà không biết phải đăng nhập lại. Tài khoản bị admin khóa cũng không bị đẩy ra.

**Chuỗi xử lý mới:**

```
Request → 401 từ host server
   ├─ Lần 1: getIdToken(true) ép làm mới → gắn token mới → thử lại request
   └─ Vẫn 401 (hoặc refresh thất bại / token mới trùng token cũ)
            → FirebaseAuth.signOut()
            → AuthStateListener bắn sự kiện
            → AuthViewModel: SessionCleaner.clear() + widget về "Sign in" + authState = Unauthenticated
            → Navigation tự điều hướng về màn đăng nhập
```

| Chi tiết thiết kế | Lý do |
|---|---|
| Chỉ can thiệp vào **host server** | Cùng bộ lọc với `AuthInterceptor` — không đụng vào Cloudinary/CDN dùng chung OkHttpClient |
| Đếm `priorResponse` để chặn lặp vô hạn | Chỉ thử lại **đúng 1 lần**, không tạo vòng lặp 401 |
| Token mới **trùng** token cũ ⇒ đăng xuất luôn | Nghĩa là token còn hạn nhưng server chủ động từ chối — thử lại vô ích |
| `signOut()` post lên main thread | `AuthStateListener` được gọi lại ở main looper |
| Chỉ coi là "bị đăng xuất" khi state đang là `Authenticated` | `AuthStateListener` cũng bắn ngay lúc đăng ký listener; lúc chưa đăng nhập không được hiểu nhầm là bị thu hồi |

> Nhờ đó, khi **admin khóa tài khoản** (server gọi `revokeRefreshTokens`), app tự đăng xuất và dọn sạch dữ liệu cục bộ trong vòng ≤1 giờ (khi ID token hiện tại hết hạn) thay vì kẹt vô thời hạn.

✅ **Vá bổ sung 2026-08-03** — chuỗi trên từng bị hỏng khi user **đăng nhập rồi đăng xuất trong cùng phiên app**: [LoginScreen.kt](Snapget/app/src/main/java/com/example/snapget/feature/auth/LoginScreen.kt) dùng `hiltViewModel()` mặc định nên tạo `AuthViewModel` **riêng** (scope theo back-stack entry "login"); đăng nhập chỉ đổi state instance riêng đó, instance chung mà `Navigation` quan sát vẫn `Unauthenticated` → lần Sign Out sau `StateFlow` không emit (giá trị không đổi) → **không điều hướng về màn Login dù Firebase đã signOut**, mọi API sau đó 401 "Thieu token xac thuc.". Fix: [Navigation.kt](Snapget/app/src/main/java/com/example/snapget/navigation/Navigation.kt) truyền `authViewModel` chung (scope Activity) vào `LoginScreen` — một nguồn `authState` duy nhất cho login/logout/thu hồi phiên.

### 3.5 Mật khẩu

🧭 **Server không bao giờ chạm mật khẩu.** Không có `bcrypt`/`argon2` trong [server/package.json](server/package.json); entity `User` không có field password ([user.entity.ts:3](server/src/users/entities/user.entity.ts#L3) ghi rõ *"password do Firebase Auth giu, KHONG luu o Firestore"*). Đây là lựa chọn đúng: không tự chế crypto, ủy quyền cho Firebase (scrypt).

| Hạng mục | Hiện trạng |
|---|---|
| Hash | ✅ Firebase Auth quản lý |
| Chính sách độ mạnh | ⚠️ Đang dùng mặc định Firebase (**chỉ ≥6 ký tự**) → nên bật Password Policy trong Firebase Console (≥8 ký tự, có chữ hoa/số) |
| Validate phía app | ⚠️ Chỉ kiểm tra `isNotBlank()`, không validate định dạng email, không có ô xác nhận mật khẩu — [LoginScreen.kt:429-435](Snapget/app/src/main/java/com/example/snapget/feature/auth/LoginScreen.kt#L429-L435) |
| Quên mật khẩu | ✅ Qua email Firebase (`sendPasswordResetEmail`) |

---

## 4. Phân quyền (Authorization)

### 4.1 Bộ guard

| Guard | Dùng cho | Đặc điểm | File |
|---|---|---|---|
| `FirebaseAuthGuard` | Luồng app | Verify ID token, tôn trọng `@Public()` | [firebase-auth.guard.ts](server/src/common/guards/firebase-auth.guard.ts) |
| `AdminJwtGuard` | Luồng admin | Verify JWT + **re-check Firebase** | [admin-jwt.guard.ts](server/src/common/guards/admin-jwt.guard.ts) |
| `RolesGuard` | Sau xác thực | Kiểm tra `@Roles('admin')` | [roles.guard.ts](server/src/common/guards/roles.guard.ts) |
| `ThrottlerGuard` | Toàn cục | Rate limit — guard **duy nhất** đăng ký `APP_GUARD` | [app.module.ts:39](server/src/app.module.ts#L39) |

#### Bảng phủ guard theo controller (rà toàn bộ 11 controller — 2026-07-28)

| Controller | Bảo vệ bởi |
|---|---|
| `app.controller.ts` | `@Public()` — chỉ `/api/health`, trả `{status:'ok'}` |
| `auth.controller.ts` | `@Public()` + throttle 10/60s |
| `users` · `friendships` · `moments` · `coop` · `messages` · `quests` | `FirebaseAuthGuard` (cấp controller) |
| `frames` | Route user: `FirebaseAuthGuard` · Route admin: `AdminJwtGuard + RolesGuard + @Roles('admin')` |
| `upload` | `POST /upload`: `FirebaseAuthGuard` · `POST /upload/admin`: bộ 3 admin |
| `admin` | `AdminJwtGuard + RolesGuard + @Roles('admin')` (cấp controller → phủ mọi endpoint) |

> ⚠️ **Rủi ro kiến trúc "mặc định mở":** `FirebaseAuthGuard` **không** phải global guard, mà gắn tay bằng `@UseGuards()` ở từng controller. Hiện tại 11/11 controller đều đúng, nhưng **một controller mới quên gắn guard sẽ public hoàn toàn mà không có cảnh báo nào**. Khuyến nghị: chuyển sang `APP_GUARD` (guard đã hỗ trợ `@Public()` sẵn tại [firebase-auth.guard.ts:19-25](server/src/common/guards/firebase-auth.guard.ts#L19-L25)) để mặc định là "đóng". Lưu ý khi làm: route admin dùng JWT server, cần cơ chế bỏ qua guard app cho nhóm route đó.

> ⚠️ **Bẫy tiềm ẩn trong `RolesGuard`:** [roles.guard.ts:27](server/src/common/guards/roles.guard.ts#L27) chấp nhận `user.admin === true`, mà giá trị này ở luồng app được lấy từ **claim trong ID token** ([firebase-auth.guard.ts:39](server/src/common/guards/firebase-auth.guard.ts#L39)) — có thể cũ tới 1 giờ. Hiện **chưa bị khai thác** vì mọi route admin đều dùng `AdminJwtGuard`. Nhưng nếu sau này ai đó gắn `@Roles('admin')` lên một route dùng `FirebaseAuthGuard`, admin vừa bị thu quyền vẫn qua được trong ≤1h. **Quy tắc: `@Roles('admin')` chỉ được dùng cùng `AdminJwtGuard`.**

### 4.2 Ma trận kiểm soát quyền sở hữu (ownership)

Đây là lớp phòng thủ chống **IDOR** — đã rà đủ 7 module.

| Module | Hành động | Điều kiện được phép | Vị trí |
|---|---|---|---|
| **moments** | Xóa moment | Chỉ chủ bài | [moments.service.ts:139-148](server/src/moments/moments.service.ts#L139-L148) |
| | Xem moment người khác | Phải là bạn bè | [moments.service.ts:96-112](server/src/moments/moments.service.ts#L96-L112) |
| | Seen / React / Xem reactions | Chủ bài **hoặc** người chụp chung **hoặc** bạn của một trong hai | [moments.service.ts:183-202](server/src/moments/moments.service.ts#L183-L202) |
| **messages** | Gửi 1-1 | Phải là bạn bè | [messages.service.ts:61-63](server/src/messages/messages.service.ts#L61-L63) |
| | Gửi / đọc / quản lý nhóm | Phải là thành viên nhóm — helper `requireMembership` dùng chung cho MỌI thao tác nhóm (send, thread, detail, rename/avatar, mời, xóa, rời, mute) | [messages.service.ts:363-372](server/src/messages/messages.service.ts#L363-L372) |
| | Đọc thread 1-1 | Query scope cứng theo cặp `(sender, receiver)` → chỉ trả tin mà mình là một bên | [messages.repository.ts:44-52](server/src/messages/messages.repository.ts#L44-L52) |
| | Reply | Tin gốc phải **cùng hội thoại** (chặn leak nội dung qua `replyToContent`) | [messages.service.ts:182-185](server/src/messages/messages.service.ts#L182-L185) |
| | Mark seen | Chỉ người nhận | [messages.service.ts:232-235](server/src/messages/messages.service.ts#L232-L235) |
| | Tạo nhóm / **thêm thành viên** (2026-08-02) | **Người được thêm phải là bạn của người tạo/người mời** (chặn lách luật "chỉ nhắn với bạn bè" bằng nhóm 2 người) — helper `assertAllFriendsOf` dùng chung cho cả 2 luồng | [messages.service.ts:378-385](server/src/messages/messages.service.ts#L378-L385) |
| | Xóa thành viên khỏi nhóm (2026-08-02) | **Chỉ người tạo nhóm** (`createdBy`); không tự xóa mình (phải dùng rời nhóm) | [messages.service.ts:316-322](server/src/messages/messages.service.ts#L316-L322) |
| | Xem chi tiết nhóm (hồ sơ thành viên) | Member-only; chỉ trả `PublicUser` từng thành viên (ẩn email/fcmTokens) | [messages.service.ts:264-278](server/src/messages/messages.service.ts#L264-L278) |
| **friendships** | Accept/decline | Transaction theo uid hiện tại; không tự kết bạn với mình; giới hạn 20 kiểm tra **cả 2 phía** | [friendships.service.ts:64-68](server/src/friendships/friendships.service.ts#L64-L68) · [:119-148](server/src/friendships/friendships.service.ts#L119-L148) |
| **users** | Sửa hồ sơ | Mọi route ghi đều là `me`, uid lấy từ token — **không nhận uid từ client** | [users.controller.ts:22-40](server/src/users/users.controller.ts#L22-L40) |
| | Xem user khác | Chỉ trả `PublicUser` (uid, fullName, avatar, personalStreak) — **ẩn email, fcmTokens, inviteCode, birthday** | [users.repository.ts:92-99](server/src/users/users.repository.ts#L92-L99) |
| **coop** | Mời chụp chung | Phải là bạn | [coop.service.ts:54-57](server/src/moments/coop.service.ts#L54-L57) |
| | Accept | Chỉ đúng người được mời, lời mời còn PENDING + chưa hết hạn 5 phút; chống race bằng transaction (đánh dấu EXPIRED cũng qua transition — không ghi đè được accept vừa thắng, fix 2026-08-03) | [coop.service.ts:253-269](server/src/moments/coop.service.ts#L253-L269) |
| | Poll trạng thái / nộp nửa ảnh (2026-08-02) | Chỉ 2 người trong lời mời (`assertParticipant`); nộp nửa ảnh chỉ khi ACCEPTED; ghép khóa transaction ACCEPTED→COMPLETED (2 bên nộp cùng lúc chỉ 1 bên ghép) | [coop.service.ts:241-251](server/src/moments/coop.service.ts#L241-L251) |
| | Decline / hủy phiên | 2 phía đều hủy được khi PENDING **hoặc ACCEPTED** (2026-08-03 — rời màn chụp = hủy phiên, tránh đối phương chờ vô hạn); transition transactional, không ghi đè được COMPLETED | [coop.service.ts:158-168](server/src/moments/coop.service.ts#L158-L168) |
| **frames** | Ghi (CRUD, cấp khung) | Chỉ admin | [frames.controller.ts](server/src/frames/frames.controller.ts) |
| **quests** | Xem quest hôm nay | uid lấy từ token, không nhận input người dùng | [quests.controller.ts:19](server/src/quests/quests.controller.ts#L19) |

> ✅ **Lịch sử:** đợt rà soát 2026-07-26 đã vá **2 IDOR thật**: (1) ai cầm `momentId` cũng seen/react được kể cả người lạ; (2) tạo nhóm 2 người với người lạ để lách luật chỉ-nhắn-với-bạn-bè. Comment giải thích còn nguyên trong code tại [moments.service.ts:177-182](server/src/moments/moments.service.ts#L177-L182) và [messages.service.ts:239-243](server/src/messages/messages.service.ts#L239-L243). Đợt thêm quản lý nhóm 2026-08-02 **tái dùng đúng rào chắn này** cho `addMembers` (helper `assertAllFriendsOf`) — không mở lại lỗ hổng.

### 4.3 Bảo vệ chức năng quản trị

| Biện pháp | Hiện trạng | Vị trí |
|---|---|---|
| Không tự khóa chính mình | ✅ Kiểm tra ở **cả** client lẫn server | [UsersPage.tsx:123-125](admin/src/pages/UsersPage.tsx#L123-L125) + [admin.service.ts:121-123](server/src/admin/admin.service.ts#L121-L123) |
| Không tự thu quyền chính mình | ✅ | [admin.service.ts:153-155](server/src/admin/admin.service.ts#L153-L155) |
| **Chống mất admin cuối cùng** | ✅ 🧭 Sau khi thu quyền, đếm lại số admin còn hoạt động; nếu về 0 thì **khôi phục claim** và báo lỗi (xử lý cả trường hợp 2 admin thu quyền lẫn nhau đồng thời) | [admin.service.ts:162-168](server/src/admin/admin.service.ts#L162-L168) |
| Khóa user → cắt phiên | ✅ Gọi `revokeRefreshTokens()` | [admin.service.ts:126-128](server/src/admin/admin.service.ts#L126-L128) |
| Không xóa cứng tài khoản | ✅ 🧭 Chỉ `disabled` — hạn chế thiệt hại nếu admin bị chiếm | — |
| Ghi audit log | ✅ 9 loại hành động | [mục 11](#11-nhật-ký--truy-vết-audit) |
| **Phân tầng quyền admin** | ⚠️ **Không có super-admin.** Mọi admin đều `grant-admin` được cho bất kỳ ai → 1 admin bị chiếm = mất toàn hệ thống | [admin.api.ts:24-30](admin/src/api/admin.api.ts#L24-L30) |
| Bootstrap admin đầu tiên | ✅ 🧭 Script offline dùng service account (`npm run seed:admin`), giải bài toán con-gà-quả-trứng | [server/scripts/seed-admin.ts](server/scripts/seed-admin.ts) |

---

## 5. Xác thực đầu vào & chống lạm dụng dữ liệu

### 5.1 ValidationPipe toàn cục

```ts
// server/src/main.ts:26-33
new ValidationPipe({
  whitelist: true,            // loại field không khai báo trong DTO
  forbidNonWhitelisted: true, // gửi field lạ → 400, không im lặng bỏ qua
  transform: true,
  transformOptions: { enableImplicitConversion: true },
})
```

✅ **`forbidNonWhitelisted` chặn mass assignment ở tầng global** — đây là cấu hình đúng nhất có thể. Cộng thêm phòng thủ tầng hai: [users.service.ts:90-93](server/src/users/users.service.ts#L90-L93) xây object patch bằng cách **liệt kê tường minh từng field** thay vì spread DTO.

### 5.2 Bảng DTO (15/15 đều có validator)

| DTO | Ràng buộc chính |
|---|---|
| `admin-login.dto.ts` | `@IsString @IsNotEmpty` |
| `create-moment.dto.ts` | `@IsIn(['PHOTO','VIDEO'])`, `@IsUrl`, `@MaxLength(500)` |
| `send-message.dto.ts` | `@IsIn` type, `@MaxLength(2000)` content, `@MaxLength(128)` replyToId |
| `create-group.dto.ts` | `@IsArray @ArrayMinSize(1)`, `@MaxLength(100)` |
| `update-user.dto.ts` | `@MaxLength(100)`, `@Matches(/^\d{4}-\d{2}-\d{2}$/)` cho birthday |
| `create-frame.dto.ts` | `@IsUrl`, `@IsIn(UNLOCK_TYPES)`, `@IsInt @Min(1)` |
| `pagination.dto.ts` | `@IsInt @Min(1) @Max(100)` — **có trần limit** |
| `daily-stats.dto.ts` | `@IsInt @Min(1) @Max(30)` |
| … (react, fcm-token, connect-friend, coop, react-message, list-users, set-disabled) | đều có |

**Khoảng trống:**

| # | Vấn đề | Mức | Vị trí |
|---|---|---|---|
| V1 | `avatar` chỉ `@IsString()`, **thiếu `@IsUrl()`** — trong khi `mediaUrl`/`imageUrl` đều có. User đặt được `avatar = "javascript:..."`, giá trị này hiển thị trên trang admin và đẩy lên Firebase Auth `photoURL` | ⚠️ | [update-user.dto.ts](server/src/users/dto/update-user.dto.ts) |
| V2 | **Path param không validate** (`@Param('uid')`, `@Param('id')`, `@Param('code')`…). Firestore `.doc(id)` không bị injection nên rủi ro thấp, nhưng id rỗng/quá dài/chứa `/` gây 500 thay vì 400 | ⚠️ | Mọi controller |

### 5.3 Injection

| Loại | Áp dụng? | Lý do |
|---|---|---|
| SQL injection | ❌ Không áp dụng | **Không có SQL database.** Không TypeORM, không Mongoose — chỉ Firestore qua `firebase-admin` |
| NoSQL injection | ✅ Rủi ro rất thấp | Giá trị người dùng chỉ làm **operand** của `==`/`in`/`array-contains`, không bao giờ dựng tên field hay operator |
| Trường hợp duy nhất dùng biến làm field path | ✅ An toàn | `` [`reactions.${uid}`] `` tại [messages.repository.ts:77](server/src/messages/messages.repository.ts#L77) — `uid` lấy từ **token đã verify**, không phải body |
| Path traversal | ❌ Không thể | Server không phục vụ file tĩnh, không ghi file lên đĩa; đã rà toàn bộ `readFileSync/createReadStream/sendFile/express.static` — chỉ có 1 chỗ đọc service account từ **env**, không từ input người dùng |
| SSRF | ⚠️ Blind SSRF hạn chế | [coop.service.ts:290-296](server/src/moments/coop.service.ts#L290-L296) `fetch(url)` với URL từ DTO. Có `@IsUrl()` nhưng **không giới hạn domain** → có thể quét cổng nội bộ / gọi `169.254.169.254`. Kết quả đi qua `sharp()` nên không đọc được nội dung. **Nên whitelist `res.cloudinary.com`** |

---

## 6. Tầng mạng & hạ tầng server

### 6.1 Tầng vận chuyển

| Môi trường | Giao thức | Ghi chú |
|---|---|---|
| Production API | ✅ HTTPS — `https://datn-8810.onrender.com/api` | Render tự cấp cert |
| Firebase Hosting (admin + landing) | ✅ HTTPS + tự redirect HTTP→HTTPS | Mặc định của Firebase |
| Dev | HTTP `localhost:3000` / IP LAN | 🧭 Chỉ dùng với build **debug** của app |

### 6.2 Security headers

| Header | Hiện trạng | Vị trí |
|---|---|---|
| `helmet()` trên API | ✅ Bật — noSniff, frameguard, HSTS… | [main.ts:16](server/src/main.ts#L16) |
| **CSP trên API** | ⚠️ **Tắt** (`contentSecurityPolicy: false`) để Swagger UI chạy được | [main.ts:16](server/src/main.ts#L16) |
| **Headers trên site admin** | 🔴 **Không có khối `headers` nào** trong [admin/firebase.json](admin/firebase.json) → thiếu CSP, `X-Frame-Options`, `Referrer-Policy`, `X-Content-Type-Options`, `Permissions-Policy`. **Trang admin hiện có thể bị nhúng iframe → clickjacking** | [admin/firebase.json](admin/firebase.json) |
| Headers trên landing page | 🔴 Tương tự, không có | [hosting/firebase.json](hosting/firebase.json) |

> ⚠️ Lưu ý: `helmet()` chỉ áp cho API NestJS. **Nó không bảo vệ file tĩnh trên Firebase Hosting** — hai nơi này phải cấu hình riêng.

### 6.3 Chống lạm dụng (rate limiting)

| Phạm vi | Giới hạn | Vị trí |
|---|---|---|
| Toàn cục | ✅ 120 request / 60s / IP | [app.module.ts:27](server/src/app.module.ts#L27) |
| Đăng nhập admin | ✅ 10 request / 60s | [auth.controller.ts:14](server/src/auth/auth.controller.ts#L14) |

> 🔴 **`trust proxy` chưa bật.** Server chạy trên Render (sau reverse proxy) nhưng [main.ts](server/src/main.ts) không có `app.set('trust proxy', 1)`. Hệ quả: `req.ip` mà ThrottlerGuard dùng là **IP của proxy, giống nhau cho mọi người dùng** → giới hạn 120 req/phút bị **chia sẻ chung cho toàn bộ user** (dễ gây từ chối dịch vụ oan) và giới hạn brute-force login 10 req/phút hoạt động sai lệch. **Đây là lỗi cấu hình có ảnh hưởng thật trên production, sửa bằng 1 dòng.**

### 6.4 CORS

```ts
// server/src/main.ts:22-23
const origins = (config.get('CORS_ORIGINS') ?? '').split(',').map(o => o.trim());
app.enableCors({ origin: origins, credentials: true });
```

- ✅ Allowlist đọc từ env, **không dùng `origin: true` hay `*`**.
- ⚠️ `credentials: true` là **thừa** — hệ thống không dùng cookie, auth 100% qua header Bearer.
- ⚠️ Prod phải có `https://snapget-admin-d8693.web.app` trong `CORS_ORIGINS` trên Render (`.env` local hiện chỉ có `http://localhost:5173`).

### 6.5 Giới hạn tài nguyên

| Hạng mục | Hiện trạng |
|---|---|
| Body size JSON | ⚠️ Không cấu hình → mặc định Express 100kb (tạm ổn) |
| **Body size multipart** | 🔴 Không giới hạn — xem [mục 7.2](#72--điểm-nguy-hiểm-nhất-của-tầng-upload) |
| Phân trang | ⚠️ Nhiều endpoint **đọc toàn bộ collection rồi cắt trang trong RAM** — xem [mục 14](#14-khoảng-trống-đã-biết--lộ-trình-ưu-tiên) |

### 6.6 Xử lý lỗi & rò rỉ thông tin

| Hạng mục | Hiện trạng | Vị trí |
|---|---|---|
| Che stack trace | ✅ Lỗi không lường trước chỉ log server-side, client nhận `"Da co loi xay ra, vui long thu lai."` | [all-exceptions.filter.ts:35-42](server/src/common/filters/all-exceptions.filter.ts#L35-L42) |
| Envelope thống nhất | ✅ `{success, statusCode, message, data}` | [response.interceptor.ts](server/src/common/interceptors/response.interceptor.ts) |
| **Swagger `/docs`** | ⚠️ **Public trên production, không auth** → phơi bày toàn bộ sơ đồ API, tên tham số, schema DTO. Không phải lỗ hổng trực tiếp (guard vẫn chặn) nhưng giúp kẻ tấn công lập bản đồ hệ thống | [main.ts:53-54](server/src/main.ts#L53-L54) |
| Env validation | ✅ Joi, fail-fast khi thiếu biến; `JWT_SECRET` **không có default, không fallback hardcode** → server không boot nếu thiếu | [env.validation.ts](server/src/config/env.validation.ts) |

---

## 7. Upload & media

### 7.1 Luồng

```
App/Admin ──multipart──► NestJS /upload ──► Cloudinary ──► trả secure_url ──► lưu vào Firestore
```

🧭 **App không bao giờ upload thẳng lên Cloudinary** — nếu làm vậy app phải cầm Cloudinary secret. Đây là quyết định thiết kế đúng.

| Kiểm soát | Hiện trạng | Vị trí |
|---|---|---|
| Xác thực | ✅ `/upload` cần Firebase token; `/upload/admin` cần JWT admin | [upload.controller.ts:38](server/src/upload/upload.controller.ts#L38) · [:59](server/src/upload/upload.controller.ts#L59) |
| Whitelist loại file | ⚠️ Chỉ kiểm tra `file.mimetype` — **giá trị do client tự khai, giả được** | [upload.controller.ts:77-84](server/src/upload/upload.controller.ts#L77-L84) |
| **Rule video ≤5s** | ✅ 🧭 **Không tin mimetype client** — đọc `resource_type`/`is_audio` từ kết quả phân loại **theo bytes thật** của Cloudinary; video quá dài bị `destroy()` ngay | [upload.service.ts:66-80](server/src/upload/upload.service.ts#L66-L80) |
| Sanitize tên file | ✅ Không cần — tên file client gửi bị **bỏ qua hoàn toàn**, chỉ dùng `file.buffer`; Cloudinary tự sinh `public_id` | [upload.service.ts:47](server/src/upload/upload.service.ts#L47) |

### 7.2 🔴 Điểm nguy hiểm nhất của tầng upload

```ts
// server/src/upload/upload.controller.ts:39 và :61
@UseInterceptors(FileInterceptor('file'))   // ← KHÔNG có option limits
// rồi mới:
new ParseFilePipe({ validators: [new MaxFileSizeValidator({ maxSize: 25MB })] })
```

**Trình tự thực thi:** multer buffer **toàn bộ file vào RAM** → *rồi* `ParseFilePipe` mới kiểm tra kích thước → *rồi* mới từ chối. Nghĩa là `MaxFileSizeValidator` **kiểm tra sau khi thiệt hại đã xảy ra**.

Một tài khoản hợp lệ POST file 2GB → server nạp trọn 2GB vào RAM rồi mới trả 400. **Trên Render free tier (512MB RAM), một request ~500MB là đủ giết process.**

**Khắc phục (1 dòng, cả 2 chỗ):**
```ts
@UseInterceptors(FileInterceptor('file', { limits: { fileSize: 25 * 1024 * 1024 } }))
```
Multer sẽ ngắt stream ngay khi vượt ngưỡng thay vì buffer hết.

### 7.3 Điểm yếu đã biết của tầng media

🔴 **Media trên Cloudinary công khai (BOLA ở tầng lưu trữ).**

Server bảo vệ rất kỹ việc *ai được xem moment nào* ([moments.service.ts:183-202](server/src/moments/moments.service.ts#L183-L202)), nhưng ảnh/video thật được upload với `resource_type: 'auto'` và **không set `access_mode: 'authenticated'`** ([upload.service.ts:58](server/src/upload/upload.service.ts#L58)). URL trả về là `secure_url` **công khai, không hết hạn, không cần token**.

→ Chỉ cần có URL (qua chia sẻ, log, cache trình duyệt, hoặc đoán `public_id`), **bất kỳ ai trên Internet cũng xem được ảnh riêng tư mà không cần đăng nhập**. Toàn bộ logic "chỉ bạn bè mới xem được moment" chỉ bảo vệ **metadata**, không bảo vệ **nội dung**.

**Lựa chọn xử lý:** (a) dùng Cloudinary signed/authenticated delivery URL có thời hạn, hoặc (b) chấp nhận và **ghi rõ là giới hạn đã biết** khi bảo vệ đồ án.

---

## 8. Bảo mật app Android

### 8.1 Lưu trữ token & dữ liệu nhạy cảm

✅ 🧭 **App không tự lưu access/refresh token.** Toàn bộ vòng đời token do Firebase Auth SDK quản lý (lưu trong private storage của SDK). Không có `TokenManager`/`SessionManager` tự chế — đây là lựa chọn đúng.

| Kho lưu trữ | Nội dung | Nhạy cảm? |
|---|---|---|
| Firebase Auth SDK (private prefs) | Refresh token | 🔴 **CAO** |
| `snapget_settings` | Toggle UI, theme | Không |
| `snapget_pending_invite` | Mã mời chờ đăng nhập | Thấp |
| `snapget_widget` + `filesDir/widget/` | Snapshot ảnh widget | Trung bình |
| Coil disk cache (512MB) | **Ảnh/video của user** | Trung bình |

✅ **`android:allowBackup="false"`** — đã sửa 2026-07-28 ([AndroidManifest.xml](Snapget/app/src/main/AndroidManifest.xml)).

> **Lỗ hổng trước khi sửa:** `allowBackup="true"` + hai file backup rules **rỗng hoàn toàn** (chỉ có comment mẫu) ⇒ toàn bộ SharedPreferences — **bao gồm private prefs của Firebase Auth chứa refresh token** — được Auto Backup đẩy lên Google Drive và lấy lại được bằng `adb backup`/restore sang máy khác. Chiếm được phiên đăng nhập mà không cần biết mật khẩu. Đây là lỗ hổng khai thác được thật, không phải lý thuyết.

**Phòng thủ 2 lớp đã triển khai:**
1. `allowBackup="false"` — tắt hẳn Auto Backup.
2. [backup_rules.xml](Snapget/app/src/main/res/xml/backup_rules.xml) + [data_extraction_rules.xml](Snapget/app/src/main/res/xml/data_extraction_rules.xml) vẫn được viết đầy đủ `<exclude>` cho prefs Firebase Auth, `snapget_pending_invite`, `snapget_widget`, thư mục `widget/` — để **nếu sau này ai đó bật lại backup thì các mục nhạy cảm vẫn không lọt** (data_extraction_rules chặn cả `<cloud-backup>` lẫn `<device-transfer>`).

✅ **Đã kiểm chứng trong manifest merge của bản release:** `android:allowBackup="false"`.

✅ **Logout dọn sạch dữ liệu cục bộ** — đã sửa 2026-07-28, xem [SessionCleaner.kt](Snapget/app/src/main/java/com/example/snapget/core/data/SessionCleaner.kt).

> **Trước:** đăng xuất chỉ gỡ FCM token + `signOut()` + xóa snapshot widget. Còn lại giữ nguyên trên máy: **Coil disk cache tới 512MB ảnh/video riêng tư** của tài khoản cũ (người dùng kế tiếp trên cùng máy vẫn xem được qua cache), `currentUserCache` in-memory không bao giờ reset (có thể trả về thông tin tài khoản cũ sau khi đăng nhập tài khoản mới), và mã mời đang chờ.

`SessionCleaner.clear()` xóa cả ba, chạy trên `Dispatchers.IO` và best-effort (lỗi dọn dẹp không làm hỏng luồng đăng xuất). Được gọi ở **cả hai** đường đăng xuất: người dùng tự bấm Sign Out, **và** phiên bị thu hồi từ bên ngoài.

✅ **Bổ sung 2026-08-05 — reset skin + hiệu ứng chạm khi đăng xuất.** Hai thứ này là **vật phẩm gacha mua bằng tiền thật**, gắn với tài khoản chứ không gắn với thiết bị; lựa chọn lại nằm ở `SharedPreferences`. Không reset thì tài khoản đăng nhập kế tiếp trên cùng máy dùng miễn phí skin SSR của tài khoản trước (và màn Appearance hiện skin đang áp dụng ở trạng thái bị khoá). Xem `SettingsPreferences.resetAppearance()`.

✅ **`logoutFromAllDevices()` đã được ghi chú trung thực** — Firebase client SDK không thể thu hồi phiên trên máy khác (chỉ `revokeRefreshTokens` phía server làm được, và server chưa có endpoint đó cho user thường). Hàm giữ nguyên tên để không phá API nhưng comment cảnh báo rõ **UI không được hứa "sign out from all devices"** cho tới khi có endpoint thật.

✅ **Đã xóa dead code `AuthConstants`** (`SHARED_PREFS_NAME = "auth_prefs"` / `TOKEN_KEY = "access_token"`) — không nơi nào dùng, là tàn dư của flow token thủ công cũ. Xóa để lập trình viên sau không vô tình quay lại kiểu lưu token plaintext.

### 8.2 Tầng mạng

| Kiểm soát | Hiện trạng | Vị trí |
|---|---|---|
| Base URL | ✅ Inject qua `BuildConfig`, prod là HTTPS | [build.gradle.kts](Snapget/app/build.gradle.kts) |
| **Chặn build release dùng HTTP** | ✅ **Đã thêm 2026-07-28** — `gradle.taskGraph.whenReady` ném `GradleException` nếu `server.base.url` không bắt đầu bằng `https://`. Trước đây thiếu `local.properties` là app release lặng lẽ build với `http://10.0.2.2:3000/api/` ⇒ ID token đi qua HTTP trần. Nay **fail ngay lúc build**, không phải phát hiện sau khi phát hành. Kiểm tra đặt ở taskGraph (không phải trong khối `release {}`) để build **debug** với server HTTP local vẫn chạy bình thường | [build.gradle.kts](Snapget/app/build.gradle.kts) |
| `network_security_config.xml` | ✅ **Đã thêm 2026-07-28**, khai báo tường minh trong manifest | [main](Snapget/app/src/main/res/xml/network_security_config.xml) · [debug](Snapget/app/src/debug/res/xml/network_security_config.xml) |
| Cleartext traffic | ✅ 🧭 **release: cấm tuyệt đối** (`cleartextTrafficPermitted="false"`); **debug: cho phép** qua resource overlay `src/debug/res/xml/` để server dev (10.0.2.2 / IP LAN) vẫn chạy. Đã kiểm chứng resource thực sự được merge đúng theo từng variant | — |
| **Chỉ tin CA hệ thống** | ✅ **Mới 2026-07-28** — bản release đặt `<certificates src="system" />` và **không** tin CA do người dùng cài thêm ⇒ chặn proxy MITM kiểu Burp/Charles trên máy đã bị cài cert lạ. Bản debug vẫn tin CA user để còn bắt traffic khi debug | [network_security_config.xml](Snapget/app/src/main/res/xml/network_security_config.xml) |
| Certificate pinning | ⚠️ 🧭 Không có — **cố ý**. Server chạy trên Render, cert Let's Encrypt xoay tự động ~60 ngày; pin cert là app sẽ chết khi cert đổi mà không vá nóng được | — |
| Logging interceptor | ✅ 🧭 `Level.BODY` **chỉ ở debug**, release `NONE`, kèm `redactHeader("Authorization")` → **không log token** | [NetworkModule.kt:47-56](Snapget/app/src/main/java/com/example/snapget/core/di/NetworkModule.kt#L47-L56) |
| Timeout | ✅ 30s connect/read/write | [NetworkModule.kt:58-60](Snapget/app/src/main/java/com/example/snapget/core/di/NetworkModule.kt#L58-L60) |

### 8.3 Manifest & bề mặt tấn công

| Hạng mục | Hiện trạng |
|---|---|
| Component `exported` | ✅ Chỉ `MainActivity` exported (bắt buộc vì LAUNCHER); FCM service, widget receiver, FileProvider đều `exported="false"` |
| FileProvider | ✅ `grantUriPermissions` + `file_paths.xml` chỉ expose `cache-path` |
| App Links | ✅ `autoVerify="true"` cho `https://snapget-d8693.web.app/invite/` + [assetlinks.json](hosting/public/.well-known/assetlinks.json) khớp SHA-256 |
| Custom scheme `snapget://invite` | ⚠️ Scheme tùy chỉnh có thể bị app khác hijack — tác động thấp (chỉ mang invite code) |
| `PendingIntent` | ✅ Dùng `FLAG_IMMUTABLE` |
| **Permission thừa** | ✅ **Đã gỡ 2026-07-28** — `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION` được xin nhưng không có chức năng nào dùng (grep toàn `app/src`: 0 kết quả `LocationManager`/`FusedLocation`/`requestLocationUpdates`). Đã kiểm chứng manifest merge của bản release **không còn** quyền vị trí |
| `debuggable` | ✅ Mặc định AGP (debug=true, release=false) |
| WebView | ✅ 🧭 **Không có WebView nào** trong app → loại bỏ trọn một lớp lỗ hổng. Link ngoài mở bằng Custom Tabs |

### 8.4 ✅ Build release đã được bảo vệ (R8 bật 2026-07-28)

```kotlin
// Snapget/app/build.gradle.kts
release {
    isMinifyEnabled = true       // R8: obfuscate + shrink code
    isShrinkResources = true     // bỏ resource không dùng
    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
}
```

> **Trước khi sửa:** `isMinifyEnabled = false` ⇒ R8 **không chạy**, nên `proguardFiles` hoàn toàn vô tác dụng và `proguard-rules.pro` chỉ có comment mặc định (0 rule thật). APK release giữ nguyên tên class/method/field + toàn bộ chuỗi → jadx dịch ngược ra source gần như nguyên bản; mọi `Log.d` (kể cả log email) nằm nguyên trong APK.

**Bộ rule đã viết** ([proguard-rules.pro](Snapget/app/proguard-rules.pro)) — nguyên tắc: thư viện lớn (Firebase, Retrofit, OkHttp, Media3, CameraX, WorkManager, Hilt) đều ship consumer rules trong AAR nên không khai báo lại; chỉ giữ thứ R8 **không thể tự suy ra**:

| Nhóm rule | Vì sao cần |
|---|---|
| `-keep class …core.network.dto.**` + `…core.model.**` | **Quan trọng nhất.** Gson map JSON ↔ object bằng **tên field** qua reflection. R8 đổi tên field ⇒ parse ra null hết mà **không báo lỗi lúc build**, chỉ vỡ lúc chạy |
| `-keepclassmembers enum *` | Enum trong DTO (`PHOTO/VIDEO`, `TEXT/VOICE/…`) |
| `-keep interface …core.network.api.**` | Retrofit đọc annotation trên interface bằng reflection |
| `-keepattributes Signature, InnerClasses, Runtime*Annotations…` | Generic + annotation bị xóa thì Gson/Retrofit hỏng |
| `-assumenosideeffects android.util.Log {d,v,i,wtf}` | **Xóa log khỏi bản release** — xem [8.6](#86-logging) |
| `-keepattributes SourceFile,LineNumberTable` + `-renamesourcefileattribute` | Vẫn đọc được stack trace từ crash report, nhưng giấu tên file gốc |

**Đã kiểm chứng bằng build thật** (`assembleRelease` BUILD SUCCESSFUL, 6m52s):

| Kiểm tra | Kết quả |
|---|---|
| R8 có chạy | ✅ `mapping.txt` 82MB, `usage.txt` 8MB (code bị loại bỏ) |
| Class thường **có** bị đổi tên | ✅ `AuthRepository -> H5.k` |
| DTO **giữ nguyên** tên (Gson an toàn) | ✅ `…dto.ChatGroupDto -> …dto.ChatGroupDto` |
| Chuỗi log đã biến mất khỏi APK | ✅ grep dex: không còn `"Registering new user"`, `"Logging in user"`, `"Sending password reset email"` |
| Bản debug vẫn build được | ✅ `assembleDebug` BUILD SUCCESSFUL |

⚠️ **Còn thiếu:** không có `signingConfig` cho release ⇒ hiện chỉ ra được `app-release-unsigned.apk` (27MB). Cần tạo keystore production để phát hành (`*.jks`/`keystore.properties` đã nằm trong `.gitignore`).

⚠️ **Bắt buộc test trên máy thật trước khi phát hành:** R8 chỉ được kiểm chứng ở mức build + kiểm tra mapping/dex, **chưa chạy runtime**. Rủi ro còn lại tập trung ở phản chiếu (Gson/Retrofit) — đã bọc bằng rule keep, nhưng vẫn nên chạy thử toàn bộ luồng: đăng nhập, feed, chat, upload, widget, deep link.

### 8.5 Validation phía client

✅ **Đã siết 2026-07-28** ([LoginScreen.kt](Snapget/app/src/main/java/com/example/snapget/feature/auth/LoginScreen.kt)). Trước đó toàn bộ validation chỉ là `isNotBlank()` trong thuộc tính `enabled` của nút submit.

| Kiểm tra | Hiện trạng |
|---|---|
| Định dạng email | ✅ `Patterns.EMAIL_ADDRESS` |
| Trim email/tên trước khi gửi | ✅ Trước đây khoảng trắng thừa gây lỗi khó hiểu từ Firebase |
| Độ dài mật khẩu khi **đăng ký** | ✅ ≥ 8 ký tự (Firebase mặc định chỉ yêu cầu 6) |
| Độ dài mật khẩu khi **đăng nhập** | 🧭 **Cố ý không ép** — tài khoản cũ có thể đang dùng mật khẩu 6 ký tự, ép sẽ khóa họ ra ngoài |
| Nút submit và phím Done dùng chung một hàm | ✅ Trước đây hai chỗ gọi riêng, dễ lệch điều kiện khi sửa |
| **Mật khẩu không còn vào `rememberSaveable`** | ✅ Đổi sang `remember`. `rememberSaveable` ghi giá trị vào `Bundle` savedInstanceState, mà Bundle đó có thể bị hệ thống ghi ra đĩa khi process bị kill ⇒ mật khẩu nằm plaintext trên máy |
| Ô xác nhận mật khẩu khi đăng ký | ⬜ Chưa có — **cần user quyết định về UI** (thêm field mới là thay đổi bố cục, theo `.claude/CLAUDE.md` phải hỏi trước) |
| Thông báo lỗi inline (vì sao nút bị mờ) | ⬜ Chưa có — cùng lý do trên |

🧭 Đây là lớp chặn **UX**, không phải lớp bảo mật: server + Firebase vẫn là chốt chặn thật.

### 8.6 Logging

| Hạng mục | Hiện trạng |
|---|---|
| Log token/password | ✅ **Không có dòng nào** in giá trị token hay mật khẩu |
| Redact header | ✅ `redactHeader("Authorization")` |
| **Log email (PII)** | ✅ **Đã sửa 2026-07-28 bằng 2 lớp** |

> **Trước:** `AuthRepository` log thẳng email khi đăng ký/đăng nhập/reset mật khẩu (`"Registering user with email: $email"`). Vì R8 tắt, các log này nằm nguyên trong APK release ⇒ trên máy root hoặc app có `READ_LOGS`, email của mọi user đọc được từ logcat.

**Lớp 1 — bỏ PII khỏi nội dung log:** ba dòng đó đổi thành `"Registering new user"` / `"Logging in user"` / `"Sending password reset email"`. Không còn email trong log kể cả ở bản debug.

**Lớp 2 — xóa hẳn log khỏi bản release:** `-assumenosideeffects` trong [proguard-rules.pro](Snapget/app/proguard-rules.pro) loại bỏ `Log.d/v/i/wtf` (và `System.out.print*`) ở bước optimize. **Giữ lại `Log.w`/`Log.e`** để còn chẩn đoán được sự cố — hai mức này đã được rà là không chứa PII.

✅ **Đã kiểm chứng trên APK thật:** grep dex của `app-release-unsigned.apk` không còn cả ba chuỗi log nói trên.

---

## 9. Bảo mật web admin

### 9.1 Đăng nhập & lưu phiên

| Hạng mục | Hiện trạng | Vị trí |
|---|---|---|
| Luồng 2 bước | ✅ Firebase Auth → `getIdToken()` → `POST /auth/admin/login` → JWT server | [AuthContext.tsx:44-66](admin/src/auth/AuthContext.tsx#L44-L66) |
| Nơi lưu JWT | ⚠️ **`localStorage`**, không phải httpOnly cookie | [client.ts:5-7](admin/src/api/client.ts#L5-L7) |
| TTL | 1 ngày | — |
| Logout | ⚠️ Chỉ xóa client-side; JWT vẫn hợp lệ đến hết hạn nếu đã bị copy ra ngoài | [AuthContext.tsx:68-77](admin/src/auth/AuthContext.tsx#L68-L77) |
| MFA / idle timeout | ⚠️ Không có | — |

> **Đánh đổi:** token admin trong localStorage nghĩa là **bất kỳ XSS nào cũng = chiếm quyền admin trọn 24h**. Giảm nhẹ đáng kể: `AdminJwtGuard` re-check Firebase mỗi request nên thu quyền/khóa tài khoản có hiệu lực ngay. Nhưng biện pháp phòng thủ đúng vẫn là **thêm CSP** ([mục 9.4](#94-xss--csp)).

⚠️ `uidFromToken()` ([AuthContext.tsx:25-35](admin/src/auth/AuthContext.tsx#L25-L35)) decode payload JWT bằng `atob` **không verify** — chỉ dùng cho UI. **Không bao giờ được dựa vào nó cho quyết định bảo mật.**

### 9.2 Bảo vệ route

```tsx
// admin/src/auth/RequireAuth.tsx:5-11
if (!token) return <Navigate to="/login" replace />;
```

⚠️ Guard client **chỉ kiểm tra "có chuỗi token hay không"** — không decode, không check `exp`, không check `role`. Nhét chuỗi rác vào localStorage sẽ vào được khung admin (rồi mọi API trả 401 → đá về login).

✅ **Nhưng kiểm tra quyền THẬT nằm ở server** — đây không phải kiểu "chỉ ẩn UI". Mọi endpoint admin đều qua `AdminJwtGuard + RolesGuard`.

**Nên bổ sung:** decode `exp` phía client để tự đăng xuất sớm thay vì để admin thấy khung rỗng.

### 9.3 API client

| Hạng mục | Hiện trạng | Vị trí |
|---|---|---|
| Gắn token | ✅ Request interceptor | [client.ts:17-23](admin/src/api/client.ts#L17-L23) |
| Xử lý 401 | ✅ Xóa 3 key + về `/login` (có guard tránh loop) | [client.ts:29-36](admin/src/api/client.ts#L29-L36) |
| `withCredentials` | ✅ Không bật → không gửi cookie | — |
| Timeout | ⚠️ Không đặt (Render free "ngủ" → treo lâu) | — |
| Nuốt status code | ⚠️ Chỉ ném `new Error(message)` → UI không phân biệt được 400/403/500/timeout; message server hiển thị nguyên văn trong `<Alert>` | [client.ts:38-39](admin/src/api/client.ts#L38-L39) |
| `console.log` trong `src/` | ✅ **0 kết quả** | — |

### 9.4 XSS & CSP

✅ **Quét toàn bộ `admin/src`: 0 occurrence** của `dangerouslySetInnerHTML`, `innerHTML`, `outerHTML`, `eval(`, `new Function`, `document.write`. Mọi dữ liệu người dùng (`authorName`, `caption`, `fullName`, `email`, `actorEmail`…) render qua JSX/AntD → auto-escape.

**Rủi ro còn lại:**

| # | Vấn đề | Vị trí |
|---|---|---|
| X1 | ⚠️ URL do user kiểm soát đưa thẳng vào `href`/`src`, **không allowlist domain Cloudinary** → admin bấm vào có thể mở trang lạ (phishing); ảnh load từ host lạ làm lộ IP/thời điểm duyệt bài. Nên thêm `rel="noopener noreferrer"` + kiểm tra origin | [MomentsPage.tsx:84](admin/src/pages/MomentsPage.tsx#L84) · [:100-102](admin/src/pages/MomentsPage.tsx#L100-L102) · [FramesPage.tsx:541](admin/src/pages/FramesPage.tsx#L541) |
| X2 | 🔴 **Không có Content-Security-Policy ở bất kỳ đâu** → nếu sau này lỡ thêm một chỗ `dangerouslySetInnerHTML`, không còn lớp phòng thủ nào, mà token admin nằm sẵn trong localStorage | [admin/firebase.json](admin/firebase.json) |

**Khuyến nghị tối thiểu cho `admin/firebase.json`:** thêm khối `headers` với `Content-Security-Policy`, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`.

### 9.5 CSRF

✅ **Không áp dụng.** Auth 100% qua header `Authorization: Bearer` đọc từ localStorage; không `withCredentials`, không `document.cookie` ở bất kỳ đâu trong `admin/src`. → Không cần CSRF token.

### 9.6 Bề mặt chức năng admin

| Trang | Hành động | Mức nguy hiểm |
|---|---|---|
| Tổng quan | Chỉ đọc thống kê | Thấp |
| Người dùng | Tìm kiếm, **khóa/mở khóa**, **cấp/thu quyền admin** | 🔴 Cao (leo thang đặc quyền) |
| Bài đăng | Xem mọi bài + **xóa vĩnh viễn** | Cao |
| Khung ảnh | CRUD khung, **upload file**, cấp khung | Trung bình |
| Nhật ký | Chỉ đọc audit log | Thấp |

⚠️ `GET /admin/users` liệt kê **toàn bộ user kèm email** (`listUsers(1000)` rồi lọc trong RAM — [admin.service.ts:52-74](server/src/admin/admin.service.ts#L52-L74)) → PII đổ về client hàng loạt, và **user thứ 1001 trở đi biến mất khỏi trang admin**.

### 9.7 Landing page (`hosting/`)

`hosting/` là Firebase Hosting cho site mặc định `snapget-d8693.web.app` — landing page lời mời kết bạn + `assetlinks.json` cho App Links.

⚠️ [hosting/public/index.html](hosting/public/index.html) lấy `code` từ `location.pathname` **không validate** rồi nối vào intent URL. Hiển thị dùng `textContent` nên **không có DOM-XSS**, và `#` không tồn tại trong `pathname` nên không chèn được Intent extras — nhưng vẫn nên whitelist `/^[A-Za-z0-9_-]{1,32}$/`.

---

## 10. Quản lý bí mật (secrets)

### 10.1 Tình trạng git — đã kiểm chứng

> ### 🔴🔴 CẢNH BÁO TỐI KHẨN — REPO LÀ PUBLIC VÀ CÓ PRIVATE KEY TRONG LỊCH SỬ GIT
>
> **Remote:** `https://github.com/HuyenVt001/datn.git` — kiểm chứng bằng GitHub API trả **HTTP 200** ⇒ **repo công khai với toàn thế giới**.
>
> **Rò rỉ:** file `firebase-service-account.json` chứa **private key RSA thật (PEM, 1704 ký tự)** đã được commit:
> - Thêm ở commit **`7ceff5f`** (2026-07-12, *"feat: initialize project with firebase and cloudinary setup"*)
> - Xóa ở commit **`7f88a88`** cùng ngày — **nhưng xóa file KHÔNG xóa khỏi lịch sử**: bất kỳ ai cũng lấy lại được bằng `git show 7ceff5f:firebase-service-account.json`
> - Danh tính khóa: project **`locket-9854c`** · service account `firebase-adminsdk-fbsvc@locket-9854c.iam.gserviceaccount.com` · `private_key_id` bắt đầu `812a40e9…`
>
> **Tác động:** khóa này cấp **toàn quyền Admin SDK** trên project `locket-9854c` (Firestore, Auth, Storage, FCM) và **bỏ qua mọi Security Rules**. Đây là project **khác** với project đang dùng (`snapget-d8693`) — có thể là project cũ thời còn làm bản clone Locket — nhưng nếu project đó còn tồn tại thì khóa vẫn dùng được. Repo public từ 2026-07-12 nghĩa là **các bot quét secret trên GitHub gần như chắc chắn đã thu thập khóa này**.
>
> **Phải làm ngay (theo thứ tự):**
> 1. **Thu hồi khóa**: Google Cloud Console → project `locket-9854c` → IAM & Admin → Service Accounts → `firebase-adminsdk-fbsvc@…` → Keys → **xóa khóa `812a40e9…`**. Nếu project không còn dùng → **xóa hẳn project**.
> 2. Kiểm tra log truy cập bất thường của project `locket-9854c` (Cloud Logging).
> 3. Cân nhắc viết lại lịch sử git (`git filter-repo` / BFG) rồi force-push — **nhưng việc này KHÔNG thay thế bước 1**, vì khóa đã công khai thì phải coi như đã lộ vĩnh viễn.
> 4. Vì repo public: rà lại xem project hiện tại `snapget-d8693` đã bị lộ gì chưa, và **ưu tiên tuyệt đối** cho [mục 12.1 — Firestore Rules](#121--firestore-security-rules--không-có-trong-repo) (project ID + API key của project hiện tại đều đã công khai trong repo).

| Bí mật | Trong git? | Kiểm chứng |
|---|---|---|
| 🔴 **`firebase-service-account.json`** (project `locket-9854c`) | 🔴 **CÓ TRONG LỊCH SỬ** — thêm `7ceff5f`, xóa `7f88a88`, **vẫn lấy lại được** | `git log --all --diff-filter=A -- "*service-account*.json"` |
| `server/.env` (JWT_SECRET, Cloudinary secret) | ✅ **KHÔNG** — chưa từng, kể cả trong lịch sử | `git ls-files \| grep .env` → chỉ có `.example` + `.env.production` |
| Firebase service account **của project hiện tại** (`server/*adminsdk*.json`) | ✅ **KHÔNG** | `git ls-files \| grep adminsdk` → rỗng |
| `Snapget/local.properties` | ✅ KHÔNG (gitignore) | — |
| `admin/.env` | ✅ KHÔNG (gitignore) | — |
| `server/.env.example` · `admin/.env.production.example` | ✅ An toàn — mọi trường bí mật **để trống** | Đã kiểm tra nội dung |
| `admin/.env.production` | ⚠️ **CÓ** (commit `94d1d49`) | Chứa Firebase **Web** API key (public theo thiết kế) |
| `Snapget/app/google-services.json` | ⚠️ **CÓ** (commit `146a811`) | Dòng ignore trong `.gitignore` **bị comment out** |

### 10.2 Đánh giá 2 file bị commit

🧭 Firebase **Web/Android API key không phải secret theo thiết kế** — Google công khai xác nhận điều này; key nằm sẵn trong mọi APK và mọi bundle JS. Nó chỉ **định danh project**, không cấp quyền. Phòng thủ thật nằm ở **Firestore Rules + App Check**.

**Nhưng:** vì repo hiện **không có Rules trong version control** và **không bật App Check**, key này + `project_id` cho phép bất kỳ ai gọi thẳng Firebase Auth REST API để tạo tài khoản trên project.

**Việc cần làm:**
1. Restrict API key trong Google Cloud Console (Android: theo package name + SHA-1; Web: theo HTTP referrer).
2. ✅ **Đã làm 2026-07-28** — siết toàn bộ `.gitignore`, xem [mục 10.3](#103-hàng-rào-gitignore-5-lớp-đã-siết-2026-07-28).

### 10.3 Hàng rào `.gitignore` 5 lớp (đã siết 2026-07-28)

Sự cố 2026-07-12 xảy ra vì file bí mật nằm ở **thư mục gốc — nơi khi đó không có `.gitignore` nào cả**. Nay có 5 lớp, lớp gốc là lưới an toàn phủ mọi thư mục:

| File | Vai trò | Điểm chính |
|---|---|---|
| ✅ **[.gitignore](.gitignore)** (MỚI) | **Lưới an toàn toàn repo** | Chặn service account (5 biến thể tên), `.env*`, `*.pem/key/p8/p12/pfx/jks/keystore`, `local.properties`, `credentials.json`, `client_secret*.json`, `gcp-*.json` |
| ✅ [server/.gitignore](server/.gitignore) | Lớp 2 cho server | Bổ sung `*service-account*.json`, `*firebase-adminsdk*.json`, `*.pem/key/p12/pfx`, `!.env.*.example` |
| ✅ [admin/.gitignore](admin/.gitignore) | Lớp 2 cho admin | **Đã thêm `.env.*`** (trước chỉ có `.env` → `.env.local` lọt lưới); giữ ngoại lệ có chủ đích cho `.env.production` + `.firebase/` |
| ✅ [Snapget/.gitignore](Snapget/.gitignore) | Lớp 2 cho app | Thêm pattern service account (phòng copy nhầm vào app), `keystore.properties`, `secrets.properties`, `*.pem/key/p8/p12/pfx` |
| ✅ [hosting/.gitignore](hosting/.gitignore) (MỚI) | Lớp 2 cho landing page | `.firebase/`, log CLI, chặn sẵn mọi khóa (trang tĩnh không được chứa khóa nào) |

**Đã kiểm chứng bằng `git check-ignore`:** 11/11 đường dẫn bí mật giả lập đều bị chặn; 10/10 file cần giữ (`.env.example`, `.env.production.example`, `.env.production`, `google-services.json`, source code…) đều không bị chặn nhầm.

**Hai ngoại lệ CÓ CHỦ ĐÍCH** (ghi rõ ngay trong file `.gitignore` để người sau không tưởng là sơ suất):

| File | Vì sao vẫn commit |
|---|---|
| `admin/.env.production` | Vite chỉ nhúng biến tiền tố `VITE_*` vào bundle JS ⇒ **mọi giá trị trong đó vốn đã công khai**. File cần cho `npm run build` trước khi deploy. ⚠️ Tuyệt đối không đặt bí mật thật vào đây — bí mật thật thuộc về `server/.env` |
| `Snapget/app/google-services.json` | Bắt buộc phải có thì plugin `com.google.gms.google-services` mới build được; nội dung là config client công khai theo thiết kế của Google (chỉ định danh project, không cấp quyền) |

> ⚠️ **Giới hạn của `.gitignore`:** nó chỉ chặn file **chưa được track**. File đã lỡ commit thì phải `git rm --cached <file>` mới thôi bị theo dõi, và **nội dung vẫn nằm trong lịch sử git vĩnh viễn** — khóa đã lộ thì bắt buộc phải **thu hồi**, không có cách xóa.

### 10.4 Quy tắc vận hành bí mật

| Quy tắc | Trạng thái |
|---|---|
| Hàng rào `.gitignore` phủ mọi thư mục | ✅ 5 lớp, đã kiểm chứng bằng `git check-ignore` — [mục 10.3](#103-hàng-rào-gitignore-5-lớp-đã-siết-2026-07-28) |
| Không hardcode secret trong source | ✅ Mọi truy cập env trong `server/src` đều qua `ConfigService`; 0 secret hardcode trong Kotlin |
| Validate env lúc khởi động | ✅ Joi fail-fast; nên thêm `.min(32)` cho `JWT_SECRET` |
| Service account trên prod | ✅ Render Secret Files |
| Service account trên máy dev | ⚠️ Đang nằm **trong thư mục repo** → nên chuyển ra ngoài (vd `~/.secrets/`) và trỏ bằng đường dẫn tuyệt đối |
| Secret production trên máy dev | ⚠️ `.env` local đang chứa giá trị production đang chạy → nên tách giá trị dev riêng |
| Xoay khóa (rotate) | ⬜ Chưa có quy trình định kỳ |

> ⚠️ **Khuyến nghị vận hành:** vì `server/.env` và service account key đã tồn tại trên máy dev và có thể bị bất kỳ công cụ nào chạy trên repo đọc được, nên **xoay `JWT_SECRET` + `CLOUDINARY_API_SECRET`** và **tạo service account key mới rồi thu hồi key cũ** trong Firebase Console trước khi bảo vệ/bàn giao.

---

## 11. Nhật ký & truy vết (audit)

✅ Module [server/src/audit/](server/src/audit/) ghi nhật ký hành động **admin** vào Firestore collection `adminLogs`.

**Cấu trúc:** `actorUid`, `actorEmail`, `action`, `targetId`, `targetLabel`, `createdAt` ([admin-log.entity.ts](server/src/audit/entities/admin-log.entity.ts)).

**9 hành động được ghi:**

| Action | Nơi gọi |
|---|---|
| `USER_DISABLE` / `USER_ENABLE` | [admin.service.ts:129-132](server/src/admin/admin.service.ts#L129-L132) |
| `GRANT_ADMIN` / `REVOKE_ADMIN` | [admin.service.ts:140](server/src/admin/admin.service.ts#L140) · [:169](server/src/admin/admin.service.ts#L169) |
| `MOMENT_DELETE` | [admin.service.ts:201-204](server/src/admin/admin.service.ts#L201-L204) |
| `FRAME_CREATE` / `FRAME_UPDATE` / `FRAME_DELETE` / `FRAME_GRANT` | [frames.controller.ts:58-102](server/src/frames/frames.controller.ts#L58-L102) |

**Xem log:** `GET /api/admin/logs` (phân trang, mới nhất trước) → trang Nhật ký trên web admin.

🧭 **Thiết kế best-effort:** [audit.service.ts:32-34](server/src/audit/audit.service.ts#L32-L34) bắt mọi exception và chỉ `logger.warn`, **không bao giờ throw** → lỗi ghi log không làm hỏng thao tác chính.

**Khoảng trống:**

| # | Vấn đề | Mức |
|---|---|---|
| L1 | ⚠️ **Không log đăng nhập admin** (thành công lẫn thất bại) — [auth.service.ts](server/src/auth/auth.service.ts) không gọi `AuditService`. Đây là sự kiện quan trọng nhất cần audit mà lại thiếu → **không phát hiện được brute-force hay đăng nhập bất thường** | Cao |
| L2 | ⚠️ Không ghi **IP / User-Agent** → không truy vết được nguồn khi có sự cố | Trung bình |
| L3 | ⚠️ Đánh đổi best-effort: hành động admin có thể thành công mà **không để lại dấu vết** nếu Firestore lỗi | Thấp (chấp nhận ở quy mô DATN) |
| L4 | ⚠️ [audit.service.ts:39-42](server/src/audit/audit.service.ts#L39-L42) load **toàn bộ** log rồi phân trang trong RAM → chậm dần theo thời gian | Thấp |

---

## 12. Ranh giới nằm ngoài NestJS (điểm yếu chí mạng nếu bỏ quên)

Ba lớp bảo mật sau **không nằm trong code NestJS** và có thể **vô hiệu hóa toàn bộ** công sức phòng thủ đã làm.

### 12.1 🔴 Firestore Security Rules — không có trong repo

**Tìm toàn bộ workspace: 0 file `firestore.rules` / `storage.rules`.** Hai file `firebase.json` ([hosting](hosting/firebase.json), [admin](admin/firebase.json)) **chỉ khai báo `hosting`**, không có khối `firestore` hay `storage`.

→ Rules (nếu có) chỉ tồn tại trên Firebase Console: **không version-control, không review được, không biết đang ở test mode hay không.**

**Giảm nhẹ:** client **không còn đọc/ghi Firestore trực tiếp** (đã xác nhận ở cả app lẫn admin), nên đường đi hợp lệ duy nhất là qua NestJS. **Nhưng** Firebase API key là công khai và project ID đã biết — nếu Rules đang là `allow read, write: if true` thì **bất kỳ ai cũng đọc/ghi được toàn bộ Firestore, bỏ qua mọi guard**.

**Việc cần làm (ưu tiên cao nhất):**
1. Mở Firebase Console → Firestore → Rules, **kiểm tra trạng thái hiện tại**.
2. Đặt `allow read, write: if false;` — Admin SDK của server **bỏ qua Rules** nên server vẫn hoạt động bình thường.
3. **Commit `firestore.rules` + `storage.rules` vào repo** và khai báo trong `firebase.json` để có version control.

### 12.2 ⚠️ Firebase App Check — chưa bật

Không có dấu vết App Check ở app hay admin. Bật App Check (Play Integrity cho Android, reCAPTCHA cho web) sẽ chặn được việc gọi Firebase API bằng key trích từ APK/bundle.

### 12.3 ⚠️ Cloudinary access mode

Xem [mục 7.3](#73-điểm-yếu-đã-biết-của-tầng-media) — media hiện public hoàn toàn.

### 12.4 ⚠️ Firebase Auth Password Policy

Đang dùng mặc định (≥6 ký tự). Bật policy mạnh hơn trong Console (Authentication → Settings → Password policy).

---

## 13. Ma trận đối chiếu OWASP

### 13.1 OWASP API Security Top 10 (2023) — server

| # | Rủi ro | Trạng thái | Ghi chú |
|---|---|---|---|
| API1 | Broken Object Level Authorization | ✅ Tốt | Ownership check đủ 7/7 module ([4.2](#42-ma-trận-kiểm-soát-quyền-sở-hữu-ownership)); 2 IDOR đã vá 2026-07-26 |
| API2 | Broken Authentication | ✅ Tốt | 2 luồng tách biệt, re-check quyền mỗi request; ⚠️ thiếu logout server-side |
| API3 | Broken Object Property Level Authorization | ✅ Tốt | `forbidNonWhitelisted` + `toPublic()` lọc email/fcmTokens/inviteCode |
| API4 | Unrestricted Resource Consumption | 🔴 **Yếu** | Upload không giới hạn buffer ([7.2](#72--điểm-nguy-hiểm-nhất-của-tầng-upload)); phân trang in-memory; `trust proxy` sai |
| API5 | Broken Function Level Authorization | ✅ Tốt | Guard phủ 11/11 controller; ⚠️ kiến trúc "mặc định mở" |
| API6 | Unrestricted Access to Sensitive Business Flows | ✅ Khá | Rate limit + giới hạn nghiệp vụ (20 bạn, 20 nhóm) |
| API7 | Server Side Request Forgery | ⚠️ Blind SSRF hạn chế | [coop.service.ts:290-296](server/src/moments/coop.service.ts#L290-L296) |
| API8 | Security Misconfiguration | ⚠️ Cần siết | Swagger public, CSP tắt, `trust proxy`, thiếu headers trên Hosting |
| API9 | Improper Inventory Management | ⚠️ | `/docs` public phơi bày toàn bộ inventory API |
| API10 | Unsafe Consumption of 3rd-party APIs | ✅ Khá | Cloudinary/Firebase là nhà cung cấp lớn; ⚠️ media public |

### 13.2 OWASP Mobile Top 10 — app Android

| # | Rủi ro | Trạng thái |
|---|---|---|
| M1 | Improper Credential Usage | ✅ Firebase SDK giữ token, không tự lưu; đã xóa dead code `AuthConstants` |
| M2 | Inadequate Supply Chain Security | ✅ Version catalog, không dependency lạ |
| M3 | Insecure Auth/Authz | ✅ **Đã vá** — `TokenAuthenticator` xử lý 401 + auto-logout khi phiên bị thu hồi ([3.4](#34-phản-ứng-khi-phiên-bị-thu-hồi-app)) |
| M4 | Insufficient Input/Output Validation | ✅ Đã siết (email format, độ dài mật khẩu khi đăng ký, trim) — ⬜ còn thiếu ô xác nhận mật khẩu (chờ quyết định UI) |
| M5 | Insecure Communication | ✅ **Đã vá** — cấm cleartext ở release, chỉ tin CA hệ thống, chặn build release dùng HTTP. 🧭 Không pin cert (có chủ đích) |
| M6 | Inadequate Privacy Controls | ✅ **Đã vá** — bỏ email khỏi log + strip log ở release; logout xóa Coil cache 512MB |
| M7 | Insufficient Binary Protection | ✅ **Đã vá** — R8 bật, obfuscate + shrink, verify bằng `mapping.txt` |
| M8 | Security Misconfiguration | ✅ **Đã vá** — `allowBackup="false"` + backup rules đầy đủ; gỡ quyền vị trí thừa |
| M9 | Insecure Data Storage | ✅ **Đã vá** — không còn rò qua backup; mật khẩu không vào `savedInstanceState` |
| M10 | Insufficient Cryptography | ✅ Không tự chế crypto |

---

## 14. Khoảng trống đã biết & lộ trình ưu tiên

### 14.1 🔴 Ưu tiên 1 — làm trước khi demo/bàn giao

| # | Việc | Nơi sửa | Công sức |
|---|---|---|---|
| **P0** ⏸️ | **THU HỒI private key service account đã lộ trong lịch sử git của repo PUBLIC** (project `locket-9854c`, key `812a40e9…`). 🧭 **User đã quyết định 2026-07-28: chấp nhận rủi ro, không thu hồi** — các khóa hiện tại được đánh giá là không quan trọng. Khuyến nghị kỹ thuật vẫn giữ nguyên: thu hồi chỉ mất ~2 phút và project `locket-9854c` có thể vẫn đang sống. Chi tiết: [mục 10.1](#101-tình-trạng-git--đã-kiểm-chứng) | Google Cloud Console | Nhỏ |
| P1 | **Kiểm tra Firestore Rules trên Console**, đặt deny-all, commit `firestore.rules` vào repo | Firebase Console + `firebase.json` | Nhỏ, giá trị cao nhất |
| P2 | **Giới hạn upload**: `FileInterceptor('file', { limits: { fileSize: 25*1024*1024 } })` | [upload.controller.ts:39](server/src/upload/upload.controller.ts#L39) · [:61](server/src/upload/upload.controller.ts#L61) | 1 dòng ×2 |
| P3 | **Bật `trust proxy`**: `app.set('trust proxy', 1)` | [server/src/main.ts](server/src/main.ts) | 1 dòng |
| ~~P4~~ | ~~Tắt backup Android~~ → ✅ **XONG 2026-07-28** — `allowBackup="false"` + backup rules đầy đủ ([8.1](#81-lưu-trữ-token--dữ-liệu-nhạy-cảm)) | — | — |
| ~~P5~~ | ~~Bật R8 cho release~~ → ✅ **XONG 2026-07-28** — minify + shrinkResources + 6 nhóm rule, verify bằng build thật ([8.4](#84--build-release-đã-được-bảo-vệ-r8-bật-2026-07-28)). ⚠️ Còn phải test runtime trên máy thật | — | — |
| P6 | **Security headers cho site admin**: CSP + `X-Frame-Options: DENY` + `Referrer-Policy` | [admin/firebase.json](admin/firebase.json) | Nhỏ |

### 14.2 ⚠️ Ưu tiên 2 — hardening

| # | Việc | Nơi sửa |
|---|---|---|
| P7 | Đóng Swagger khi `NODE_ENV=production` (hoặc đặt basic auth) | [main.ts:53-54](server/src/main.ts#L53-L54) |
| P8 | Thêm `@IsUrl()` cho `avatar` | [update-user.dto.ts](server/src/users/dto/update-user.dto.ts) |
| P9 | Log sự kiện đăng nhập admin (thành công + thất bại) + IP/User-Agent vào audit | [auth.service.ts](server/src/auth/auth.service.ts) · [audit.service.ts](server/src/audit/audit.service.ts) |
| ~~P10~~ | ~~Xử lý 401 phía app~~ → ✅ **XONG 2026-07-28** — [TokenAuthenticator.kt](Snapget/app/src/main/java/com/example/snapget/core/network/interceptor/TokenAuthenticator.kt) |
| ~~P11~~ | ~~Logout xóa Coil cache + reset `currentUserCache` + `PendingInviteStore`~~ → ✅ **XONG 2026-07-28** — [SessionCleaner.kt](Snapget/app/src/main/java/com/example/snapget/core/data/SessionCleaner.kt) |
| P12 | `RequireAuth` decode `exp`, tự logout khi hết hạn | [RequireAuth.tsx](admin/src/auth/RequireAuth.tsx) |
| P13 | Whitelist domain Cloudinary cho SSRF ở co-op | [coop.service.ts:290-296](server/src/moments/coop.service.ts#L290-L296) |
| P14 | Xoay `JWT_SECRET`, `CLOUDINARY_API_SECRET`, tạo service account key mới | Env + Console |
| P15 | Restrict Firebase API key (package+SHA-1 / HTTP referrer) + bật App Check | Google Cloud Console |
| ~~P16~~ | ~~`network_security_config.xml` + bỏ fallback HTTP ở release~~ → ✅ **XONG 2026-07-28** — thêm cả bản debug (cho cleartext) lẫn release (cấm + chỉ tin CA hệ thống), kèm check chặn build release dùng HTTP ([8.2](#82-tầng-mạng)) |
| ~~P17~~ | ~~Sửa `admin/.gitignore` thêm `.env.*`~~ → ✅ **XONG 2026-07-28**: siết cả 5 lớp `.gitignore` (tạo mới ở thư mục gốc + `hosting/`), xem [mục 10.3](#103-hàng-rào-gitignore-5-lớp-đã-siết-2026-07-28) | [.gitignore](.gitignore) |
| P18 | Giới hạn số FCM token / user (chống phình document 1MB) | [users.repository.ts](server/src/users/users.repository.ts) |
| ~~P19~~ | ~~Gỡ permission LOCATION~~ → ✅ **XONG 2026-07-28** — verify bằng manifest merge của bản release |
| ~~P20~~ | ~~Xóa dead code~~ → ✅ **XONG 2026-07-28** cho `AuthConstants` (đã xóa file) và `provideCookieManager` (gỡ khỏi `NetworkModule`, vốn được provide nhưng chưa bao giờ gắn vào OkHttpClient). ⬜ `StoreImpl2` giữ lại — còn binding DI trong `RepositoriesModule`, không phải vấn đề bảo mật |

### 14.3 🧭 Giới hạn đã biết — chấp nhận ở quy mô DATN (ghi rõ khi bảo vệ)

| Giới hạn | Lý do chấp nhận |
|---|---|
| Media Cloudinary công khai | Sửa đúng cần signed URL + đổi luồng hiển thị ở cả app lẫn admin |
| Không có refresh token cho admin | `AdminJwtGuard` re-check đã thay thế được vai trò thu hồi |
| Phân trang in-memory ở nhiều vị trí | Chạy tốt ở quy mô đồ án; là vấn đề scale, không phải lỗ hổng logic |
| Không certificate pinning | Pin cert Render dễ vỡ khi xoay cert |
| Không MFA cho admin | Ngoài phạm vi đồ án |
| `logoutFromAllDevices()` chưa thật | Cần `revokeRefreshTokens` phía server |
| **Không thu hồi service account key đã lộ** (project `locket-9854c`) — quyết định của user 2026-07-28 | Khóa thuộc project cũ, không chứa dữ liệu người dùng thật của Snapget. **Đã chặn tái diễn** bằng hàng rào `.gitignore` 5 lớp ([mục 10.3](#103-hàng-rào-gitignore-5-lớp-đã-siết-2026-07-28)) — đây mới là phần quan trọng cho tương lai |

---

## 15. Quy trình vận hành bảo mật (checklist)

### 15.1 Khi thêm **endpoint mới** ở server

- [ ] Có gắn `FirebaseAuthGuard` (luồng app) **hoặc** `AdminJwtGuard + RolesGuard + @Roles('admin')` (luồng admin) chưa? Nếu cố ý public → phải gắn `@Public()` **tường minh**.
- [ ] `@Roles('admin')` **chỉ** đi cùng `AdminJwtGuard` (xem cảnh báo ở [4.1](#41-bộ-guard)).
- [ ] Mọi input có DTO + `class-validator`? Có `@MaxLength` cho chuỗi tự do?
- [ ] Endpoint đọc tài nguyên của người khác → **có kiểm tra ownership/friendship** chưa?
- [ ] uid lấy từ `@CurrentUser()`, **không** nhận uid từ body/param.
- [ ] Có phân trang + trần `limit` chưa?
- [ ] Hành động quản trị → có ghi `AuditService` chưa?
- [ ] **Cập nhật `SECURITY.md`** nếu thay đổi mô hình quyền.

### 15.2 Khi thêm **màn hình/chức năng mới** ở app

- [ ] Gọi qua Retrofit → NestJS, **không** thêm code Firestore trực tiếp mới.
- [ ] Không log token/password/PII.
- [ ] Dữ liệu nhạy cảm mới lưu local → cân nhắc backup rules + xóa khi logout.

### 15.3 Khi thêm **trang mới** ở admin

- [ ] Nằm trong `<RequireAuth>`.
- [ ] Không dùng `dangerouslySetInnerHTML`.
- [ ] URL do user kiểm soát → kiểm tra origin + `rel="noopener noreferrer"`.
- [ ] Hành động nguy hiểm → `Modal.confirm`/`Popconfirm`.

### 15.4 Trước mỗi lần deploy production

- [ ] `CORS_ORIGINS` có đủ origin admin production.
- [ ] Không có secret mới lọt vào git (`git status` + kiểm tra `.gitignore`).
- [ ] `NODE_ENV=production`.
- [ ] Firestore Rules vẫn ở trạng thái deny-all.

### 15.5 Khi nghi ngờ lộ bí mật

1. Xoay ngay `JWT_SECRET` (mọi phiên admin mất hiệu lực).
2. Xoay Cloudinary API secret.
3. Tạo service account key mới → cập nhật Secret Files trên Render → **thu hồi key cũ** trong Firebase Console.
4. Rà `adminLogs` tìm hành động bất thường.
5. Ghi lại sự cố + biện pháp vào file này.

---

## 16. Quy tắc bảo trì tài liệu (BẮT BUỘC)

> ### ⚠️ Luật: **mọi thay đổi liên quan bảo mật đều phải cập nhật `SECURITY.md` NGAY trong cùng lần sửa.**
> Sửa code bảo mật mà không cập nhật file này = **chưa xong việc**.

**Thay đổi nào tính là "liên quan bảo mật":**

| Nhóm | Ví dụ |
|---|---|
| Xác thực / phiên | Đổi TTL token, thêm refresh token, đổi luồng đăng nhập, thêm MFA |
| Phân quyền | Thêm/sửa guard, thêm `@Roles`, thêm `@Public()`, thêm role mới |
| Ownership | Thêm endpoint đọc/ghi tài nguyên của người khác, sửa điều kiện friendship |
| Validation | Thêm/sửa DTO có ràng buộc an toàn, đổi cấu hình `ValidationPipe` |
| Hạ tầng | CORS, helmet, rate limit, `trust proxy`, security headers, Swagger |
| Upload / media | Đổi giới hạn kích thước, loại file, access mode Cloudinary |
| Bí mật | Thêm biến env, đổi nơi lưu key, xoay khóa |
| Client | Thay đổi cách lưu token, backup rules, ProGuard/R8, CSP, cert pinning |
| Firebase | Sửa Firestore/Storage Rules, bật App Check, đổi password policy |
| Audit | Thêm/bớt hành động được ghi log |
| **Thanh toán / tiền tệ** | Sửa luồng nạp PayOS, đổi cách verify webhook, thêm nguồn cộng Astrite mới, đổi giá gói nạp — xem [mục 17](#17-thanh-toán-payos--tiền-tệ-astrite) |

**Cách cập nhật:**
1. Sửa đúng mục liên quan (đổi trạng thái ✅/⚠️/🔴, cập nhật đường dẫn + số dòng).
2. Nếu là việc trong [mục 14](#14-khoảng-trống-đã-biết--lộ-trình-ưu-tiên) → **gạch khỏi lộ trình** và chuyển thành biện pháp đã có.
3. Cập nhật dòng **"Cập nhật lần cuối"** ở đầu file.
4. Nếu phát hiện lỗ hổng mới → thêm vào [mục 14](#14-khoảng-trống-đã-biết--lộ-trình-ưu-tiên) với mức ưu tiên rõ ràng.

**Tài liệu liên quan:** [Snapget/.claude/GUIDE.md](Snapget/.claude/GUIDE.md) · [server/GUIDE.md](server/GUIDE.md) · [admin/GUIDE.md](admin/GUIDE.md) · [DEPLOY.md](DEPLOY.md) — cả 3 GUIDE đều có rule trỏ về file này.

---

## 17. Thanh toán PayOS & tiền tệ Astrite

> **Thêm 2026-08-05 (phase G6).** Đây là phần **đầu tiên và duy nhất** của hệ thống đụng **tiền thật** (PayOS production, không phải sandbox). Sai ở đây không còn là bug dữ liệu mà là **chênh lệch tiền thật ↔ Astrite đã phát**, và **không hoàn tác được**.

### 17.1 Tài sản & bất biến

| Bất biến | Vì sao sống còn | Enforce ở đâu |
|---|---|---|
| **Chỉ có 4 nguồn cộng Astrite**: tặng tân thủ · thưởng quest · hoàn khi quay trúng trùng · **webhook PayOS** | Thêm một nguồn thứ 5 mà quên guard = in tiền vô hạn | [server/src/astrite/astrite.service.ts](server/src/astrite/astrite.service.ts) · [server/src/topup/topup.service.ts](server/src/topup/topup.service.ts) |
| **Không endpoint nào cộng Astrite theo yêu cầu của client** | Client không tin được | Toàn bộ module `topup` — không có route nào nhận số Astrite |
| **Mọi thay đổi số dư đều ghi 1 dòng sổ cái `astriteTransactions`** | Đối soát: tổng sổ cái của 1 uid phải bằng `users.astrite` | `AstriteRepository.addEntryInTransaction` chạy trong **cùng transaction** với lệnh ghi số dư |
| **Ba khoá `PAYOS_*` không bao giờ ra khỏi server** | Lộ checksum key = ký được webhook giả | `.env` (gitignored) + Render env; [PayosService](server/src/topup/payos.service.ts) chỉ log `orderCode`, không log config/lỗi thô |

### 17.2 Luồng tạo đơn — server là nơi định giá

```
App  ──POST /topup/orders { packageId }──►  Server
                                            ├─ tra topupPackages -> giá + số Astrite
                                            ├─ ghi topupOrders/{orderCode}  (PENDING)   ← ghi TRƯỚC
                                            └─ gọi PayOS tạo link                        ← gọi SAU
App  ◄──{ orderCode, checkoutUrl }──────────┘
```

| Biện pháp | Chi tiết |
|---|---|
| ✅ Body chỉ có `packageId` | [CreateTopupOrderDto](server/src/topup/dto/create-order.dto.ts) — không có `amount`/`astrite`. `forbidNonWhitelisted` khiến mọi field lạ bị 400 |
| ✅ Giá tra ở server | `TopupService.createOrder` đọc `topupPackages`; gói đang tắt → 400 |
| ✅ Đơn chụp lại giá của chính nó | Admin đổi giá gói **không hồi tố** đơn đang trả dở |
| ✅ Ghi đơn trước, gọi PayOS sau | Nếu làm ngược, có thể tồn tại link thanh toán mà không có đơn tương ứng → tiền vào mà không biết cộng cho ai |
| ✅ `orderCode` sinh bằng `create()` | Firestore trả ALREADY_EXISTS nếu trùng → phát hiện ngay thay vì ghi đè đơn cũ |
| ✅ Link có hạn | `expiredAt` = `TOPUP_ORDER_TTL_MINUTES` (30 phút) — PayOS tự từ chối thanh toán quá hạn |
| ✅ Đọc đơn phải đúng chủ | `GET /topup/orders/:orderCode` so `order.uid` với `@CurrentUser()` → khác thì 403. `orderCode` là số **đoán được**, nên không thể dựa vào tính bí mật của nó |

### 17.3 Webhook — bề mặt tấn công nguy hiểm nhất

`POST /topup/webhook` là route `@Public()` **duy nhất chạm tới tiền**. Ai trên Internet cũng gọi được.

| Rủi ro | Biện pháp | Trạng thái |
|---|---|---|
| Webhook giả mạo | Verify chữ ký HMAC bằng `PAYOS_CHECKSUM_KEY` (SDK `@payos/node`); sai chữ ký → **401, dừng ngay**, không đọc tới `orderCode` | ✅ |
| Server chưa có khoá | Không có khoá thì **không phân biệt được thật/giả** → từ chối hẳn (401), không "tạm tin" | ✅ |
| Giao dịch không thành công | SDK chỉ kiểm tra **toàn vẹn dữ liệu**, không kiểm tra kết quả — service tự đọc `data.code === '00'` | ✅ |
| Số tiền báo về khác số tiền của đơn | Không cộng, giữ đơn `PENDING`, ghi `logger.error` để đối soát tay | ✅ 🧭 |
| Payload thử lúc đăng ký webhook | `orderCode` không tồn tại → trả **200** (không phải 404), nếu không PayOS coi webhook hỏng và không lưu URL | ✅ 🧭 |
| PayOS gọi lại liên tục | Chỉ trả lỗi khi chữ ký sai; mọi trường hợp còn lại trả 200 để PayOS không retry vô hạn | ✅ |
| Rò khoá qua log | Không log body webhook (chứa chữ ký + thông tin giao dịch), không log object lỗi thô — chỉ `error.message` | ✅ |

> 🧭 **Quyết định có chủ đích:** đơn `EXPIRED` **vẫn được cộng** nếu webhook thật báo đã trả. `EXPIRED` là phỏng đoán của server (hết TTL), còn webhook là sự thật từ PayOS — tiền đã vào tài khoản MB rồi. Thà cộng muộn còn hơn người dùng mất tiền mà không nhận được gì. Chỉ trạng thái `PAID` mới chặn.

### 17.4 Idempotent — không bao giờ cộng hai lần

PayOS **gọi lại webhook** khi timeout mạng. Nếu mỗi lần gọi cộng một lần thì trả 5.000đ có thể nhận Astrite gấp mấy lần.

```
doc id của topupOrders  ==  orderCode        ← 1 mã ⇒ đúng 1 doc, không thể sinh doc thứ hai
       │
       └─ transaction:  đọc đơn ─► status === 'PAID' ?  ─ có ─► trả ALREADY_PAID, KHÔNG ghi gì
                                                        └ không ─► cộng số dư + ghi sổ cái + set PAID
```

Firestore **serialize** các transaction chạm cùng document, nên hai webhook đến đồng thời không thể cùng nhìn thấy `PENDING`.

> 🔴 **Ba lỗ hổng đã vá khi soát lại (2026-08-05).** Cả ba đều cùng một dạng: *ghi đè trạng thái đơn ở nơi không thực sự đổi trạng thái*, làm mất lá chắn `PAID` và mở đường cộng tiền lần hai.
>
> | Chỗ | Vấn đề | Cách vá |
> |---|---|---|
> | Dọn đơn quá hạn | Đọc danh sách rồi ghi thẳng `EXPIRED`; webhook thật có thể chen vào giữa và chuyển đơn sang `PAID`, lệnh ghi kéo ngược về `EXPIRED` | `expireOrderIfPending` chạy **trong transaction**, chỉ đổi khi doc vẫn còn `PENDING` |
> | Lưu `checkoutUrl` sau khi tạo link | Ghi kèm `status: 'PENDING'` dù không đổi trạng thái | Tách `patchOrder` — **không bao giờ** ghi `status` |
> | `/topup/simulate` | Không kiểm tra chủ đơn: đoán đúng `orderCode` là cộng vào ví người khác | Dùng `getOrderForUser` (403 nếu không phải của mình) |
>
> 📏 **Luật rút ra cho mọi thay đổi sau này:** chỉ được ghi `status` của `topupOrders` ở đúng hai chỗ — transaction cộng tiền (`PENDING → PAID`) và transaction dọn quá hạn (`PENDING → EXPIRED`). Mọi cập nhật khác dùng `patchOrder`.

**Bài test bắt buộc trước khi bật production** (đã có trong [topup.service.spec.ts](server/src/topup/topup.service.spec.ts)): gọi webhook **3 lần cùng `orderCode`** → số dư tăng **đúng 1 lần**, `astriteTransactions` có **đúng 1 dòng**.

### 17.5 Endpoint giả lập `/topup/simulate`

| | |
|---|---|
| Vì sao có | Gói FREE-100 của PayOS chỉ cho **100 giao dịch thành công**, mỗi lần test thật tiêu 1 và không hoàn lại |
| Chặn ở production | `NODE_ENV === 'production'` → **403**, không có ngoại lệ |
| Có bỏ qua bảo mật không | Không: khi có khoá checksum, nó **ký payload rồi gọi đúng `handleWebhook`** — đi qua cả bước verify chữ ký lẫn transaction idempotent |
| Đơn tạo ra | Gắn cờ `isSimulated: true`, trang admin hiện nhãn "Giả lập" để không nhầm với doanh thu thật |

> ⚠️ **Checklist trước khi deploy production:** `NODE_ENV=production` phải được set trên Render. Thiếu biến này thì `/topup/simulate` **mở toang** — bất kỳ user đăng nhập nào cũng tự cộng Astrite cho mình được.

### 17.6 Quản trị & truy vết

- 3 hành động `TOPUP_PACKAGE_CREATE/UPDATE/DELETE` ghi `adminLogs` — đổi giá gói là thao tác đụng tiền, phải biết ai sửa lúc nào.
- Trang **Lịch sử nạp** hiện `orderCode` + `payosReference` (mã giao dịch ngân hàng) để đối chiếu với dashboard PayOS.
- Doanh thu chỉ tính đơn `PAID`.
- **Vật phẩm gắn với tài khoản, không gắn với máy**: skin và hiệu ứng chạm đang dùng được lưu ở `SharedPreferences`, nên `SessionCleaner` **reset về mặc định khi đăng xuất** (vá 2026-08-05). Không reset thì người đăng nhập tiếp theo trên cùng thiết bị dùng miễn phí đồ của tài khoản trước.

### 17.7 Khoảng trống đã biết

| Vấn đề | Mức | Ghi chú |
|---|---|---|
| Không có rate limit riêng cho `/topup/orders` | ⚠️ | Đang dùng rate limit toàn cục 120 req/60s/IP. Spam tạo đơn không mất tiền (link chưa trả không tiêu giao dịch) nhưng làm rác `topupOrders` |
| Dọn đơn quá hạn chạy cơ hội | 🧭 | Không dùng `@nestjs/schedule` (thêm dependency) — đơn `PENDING` được quét sang `EXPIRED` khi có người mở lịch sử nạp hoặc trang admin. Chạy trong transaction có điều kiện nên không đụng tới đơn đã `PAID` |
| Chưa có đối soát tự động sổ cái ↔ PayOS | ⚠️ | Quy mô DATN đối soát tay qua trang Lịch sử nạp. Sản phẩm thật nên có job so tổng `paidRevenueVnd` với báo cáo PayOS |
| Chênh lệch số tiền phải xử lý tay | 🧭 | `AMOUNT_MISMATCH` chỉ ghi log + giữ đơn `PENDING`. Cố tình không tự đoán: đây là tiền thật |

# 🗺️ GUIDE.md — Bản đồ **Snapget Admin** (React web)

> ## 📌 RULE BẮT BUỘC
> **Luôn cập nhật file GUIDE.md này MỖI KHI có thay đổi** (code, trang, API gọi, luồng, thiết kế) — ngay trong cùng lần làm việc, không đợi "xong tính năng". Mỗi lần cập nhật phải ghi rõ **thay đổi gì trong thiết kế**: sửa mục kiến trúc (mục 1) / cây thư mục (mục 3) / bảng task (mục 4) tương ứng, **và thêm 1 dòng vào Changelog (mục 6)**. Sửa code mà không cập nhật GUIDE = **chưa xong việc**. Chi tiết: mục 7.

> ## 🔐 RULE BẮT BUỘC — SECURITY.md
> **Mọi thay đổi liên quan BẢO MẬT phải cập nhật [`../SECURITY.md`](../SECURITY.md) NGAY trong cùng lần sửa** (song song với GUIDE này).
> Tính là thay đổi bảo mật: luồng đăng nhập admin & nơi lưu JWT, `RequireAuth`/bảo vệ route, interceptor gắn token và xử lý 401/403, render dữ liệu người dùng (XSS, `href`/`src` do user kiểm soát), security headers & CSP trong `firebase.json`, biến `VITE_*` và `.gitignore`, thêm chức năng quản trị mới (khóa user, cấp quyền, xóa nội dung).
> Cách cập nhật: sửa đúng mục trong SECURITY.md (đổi trạng thái ✅/⚠️/🔴 + đường dẫn:dòng), gạch việc đã làm khỏi lộ trình mục 14, đổi dòng "Cập nhật lần cuối". Sửa code bảo mật mà không cập nhật SECURITY.md = **chưa xong việc**.

> Đọc `admin/.claude/CLAUDE.md` (luật) trước. File này là bản đồ: kiến trúc + cây thư mục + task + tiến độ.
> Cập nhật lần cuối: **2026-07-28**.

---

## 0. Trạng thái tổng quát

- 🟢 **Admin hoàn chỉnh 6 trang** (Login / Tổng quan / Người dùng / Bài đăng / Khung ảnh / Nhật ký) — build + lint sạch.
- Deploy đích: Firebase Hosting site riêng (`snapget-admin-d8693.web.app`), server trên Render — xem `../DEPLOY.md`.
- ⬜ Còn lại: **test end-to-end** (seed admin + đăng nhập thật + thao tác từng trang).

---

## 1. Kiến trúc hệ thống

### 1.1 Vị trí trong monorepo

```
Admin (React SPA, web này) ──axios + Bearer JWT──► NestJS API (server/) ──► Firebase / Cloudinary
        │
        └── Firebase Web SDK: CHỈ dùng cho bước đăng nhập (lấy ID token đổi JWT server)
```

Admin **không** chạm Firestore trực tiếp — mọi dữ liệu đi qua REST API của server (endpoint surface: `../server/GUIDE.md` mục 5). Thiếu endpoint → làm ở server trước, không tự bịa.

### 1.2 Tech stack

Vite + React 18 + TS strict · AntD 5 (viVN, theme sáng #8C6239) · react-router v6 · axios + react-query · Firebase Web SDK (chỉ login). **Bảng stack đầy đủ + danh sách cấm thêm: `.claude/CLAUDE.md` mục 2** (đã chốt — không đổi nếu chưa hỏi user).

### 1.3 Phân tầng frontend (bắt buộc, xem CLAUDE.md mục 3)

```
Page/Component ──► hook react-query (useQuery/useMutation) ──► api/*.api.ts ──► api/client.ts (axios)
```

- `api/client.ts` là **chốt chặn duy nhất** ra HTTP: gắn `Authorization: Bearer <JWT>`, **bóc envelope** `{success, statusCode, message, data}` (lỗi ném `Error(message)` — message server đã là tiếng Việt), **401 → xóa TOKEN/EMAIL/UID_KEY + đá về /login**.
- Quy ước code chi tiết (mutation/invalidate, confirm hành động nguy hiểm…): CLAUDE.md mục 4.

### 1.4 Luồng xác thực (2 bước — đừng làm tắt)

```
1. Admin nhập email/password ─► signInWithEmailAndPassword (Firebase Web SDK)
2. getIdToken() ─► POST /auth/admin/login {idToken}
      server: verify token → check claim admin HIỆN HÀNH + disabled → trả {accessToken, uid, email}
3. AuthContext lưu JWT + email + uid (localStorage) — uid để trang Users biết "chính mình"
4. Mọi request sau đó mang JWT server (KHÔNG gửi Firebase ID token cho route admin)
5. 401/403 → logout; login fail → tự signOut phiên Firebase
```

Server re-check quyền admin + disabled **mỗi request** → bị thu quyền/khóa là mất phiên NGAY, không đợi JWT hết hạn.

### 1.5 Cây route & guard

```
/login                    ── public (LoginPage)
<RequireAuth>             ── chưa có JWT → redirect /login
  └── / (AdminLayout: Sider menu 5 mục + Header email/đăng xuất + <Outlet/>)
        ├── /        DashboardPage   (stats + 2 biểu đồ cột 7 ngày)
        ├── /users   UsersPage       (khóa/mở, cấp/thu admin)
        ├── /moments MomentsPage     (kiểm duyệt bài đăng)
        ├── /frames  FramesPage      (CRUD khung + grant + owners)
        └── /logs    LogsPage        (audit log admin)
```

### 1.6 Triển khai

- Local: server chạy trước (`cd server && npm run start:dev`), rồi `npm run dev` → `http://localhost:5173`.
- Production: build Vite → Firebase Hosting **site thứ 2** (`snapget-admin-d8693.web.app` — KHÔNG đè site mặc định đang phục vụ invite landing + assetlinks.json); server trên Render. Origin admin phải nằm trong `CORS_ORIGINS` của server. Chi tiết `../DEPLOY.md`.
- ⚠️ Render gói free **ngủ sau 15 phút** — trước khi demo gọi `curl https://datn-8810.onrender.com/api/health` để đánh thức.

---

## 2. Chạy dự án

```bash
# 1. Server phải chạy trước
cd server && npm run start:dev          # http://localhost:3000/api, Swagger /docs

# 2. Admin
cd admin && npm install && npm run dev  # http://localhost:5173
```

- Copy `.env.example` → `.env`, điền `VITE_API_BASE_URL` + config Firebase web (Firebase Console → Project settings → General → Your apps → Web).
- Tài khoản admin đầu tiên: `cd server && npm run seed:admin -- viethoang5301314@gmail.com` (email phải đã đăng ký trong app). Sau đó đăng nhập admin web bằng email/password Firebase của tài khoản đó.
- Nếu đăng nhập báo lỗi API key (key Android bị giới hạn platform): tạo **Web App** trong Firebase Console và thay 4 biến `VITE_FIREBASE_*` trong `admin/.env`.

## 3. Cây thư mục & ý nghĩa

```
admin/
├── .claude/CLAUDE.md      # luật code admin (đọc trước)
├── GUIDE.md               # file này
├── src/
│   ├── main.tsx           # bootstrap: ConfigProvider viVN + theme sáng (#8C6239), react-query, router
│   ├── App.tsx            # router: /login public; RequireAuth → AdminLayout → 5 trang; errorElement chống white-screen
│   ├── api/
│   │   ├── client.ts      # axios instance: baseURL từ env, Bearer JWT, bóc envelope, 401 → logout
│   │   ├── auth.api.ts    # adminLogin(idToken) → POST /auth/admin/login (trả kèm uid)
│   │   ├── admin.api.ts   # listUsers / getStats / getDailyStats / setUserDisabled / grantAdmin / revokeAdmin
│   │   │                  #   + listMoments / deleteMoment / listLogs (kiểm duyệt + audit log)
│   │   ├── frames.api.ts  # listFrames / createFrame / updateFrame (unlockType+unlockValue) / deleteFrame / grantFrame / listFrameOwners
│   │   └── upload.api.ts  # uploadImage(file) → POST /upload/admin (multipart, Admin JWT)
│   ├── auth/
│   │   ├── firebase.ts    # init Firebase web app từ VITE_FIREBASE_*
│   │   ├── AuthContext.tsx# giữ JWT + email + uid (localStorage) + login()/logout(); uid fallback decode từ payload JWT
│   │   └── RequireAuth.tsx# guard route, chưa đăng nhập → /login
│   ├── layouts/AdminLayout.tsx  # Sider menu 5 mục + Header (email admin, đăng xuất) + <Outlet/>
│   ├── pages/
│   │   ├── LoginPage.tsx      # form email/password → Firebase → đổi JWT; fail → signOut Firebase
│   │   ├── DashboardPage.tsx  # thẻ thống kê GET /admin/stats + 2 biểu đồ cột 7 ngày (SVG thuần DailyBarChart,
│   │   │                      #   màu #A85A1E / #0C7BB3 đã validate dataviz light+dark)
│   │   ├── UsersPage.tsx      # bảng user: search, phân trang server-side, cột Vai trò/Đăng nhập cuối,
│   │   │                      #   khóa/mở, cấp + THU quyền admin, chặn thao tác lên chính mình (tag "Bạn")
│   │   ├── MomentsPage.tsx    # KIỂM DUYỆT: lưới bài mọi user (ảnh/video, tác giả, caption) + xóa bài vi phạm
│   │   ├── FramesPage.tsx     # grid khung: thêm/sửa (6 điều kiện mở khóa + ngưỡng N, reset ngưỡng khi đổi loại),
│   │   │                      #   xóa, cấp cho user (search debounce 300ms), drawer 👥 "Ai đang sở hữu?"
│   │   └── LogsPage.tsx       # NHẬT KÝ admin: bảng audit log từ GET /admin/logs
│   ├── types/index.ts     # AdminUser, AdminStats, DailyStat, Frame, FrameOwner, AdminMoment, AdminLog, Paginated<T>… khớp DTO server
│   └── components/        # (trống — chỉ thêm khi dùng lại ≥2 trang)
├── .env.example / .env.production.example
├── firebase.json / .firebaserc    # deploy Hosting site admin (xem ../DEPLOY.md)
└── vite.config.ts
```

## 4. Bảng task & tiến độ

| Task | TT | Ghi chú |
|---|---|---|
| Scaffold Vite + React + TS + AntD (viVN, #8C6239) | ✅ | |
| Auth 2 bước Firebase → JWT + AuthContext + RequireAuth + 401 auto-logout | ✅ | |
| AdminLayout + router 5 trang + errorElement | ✅ | |
| DashboardPage: stats + 2 biểu đồ cột 7 ngày (SVG thuần) | ✅ | |
| UsersPage: search/phân trang/khóa/cấp + thu admin/chặn tự thao tác | ✅ | |
| FramesPage: CRUD + 6 điều kiện mở khóa + grant + drawer owners | ✅ | |
| MomentsPage: kiểm duyệt + xóa bài vi phạm | ✅ | |
| LogsPage: audit log | ✅ | |
| Alert khi query GET lỗi (cả 4 trang list) | ✅ | |
| Deploy Firebase Hosting (site riêng) | ✅ config | file sẵn, cần chạy lệnh deploy (../DEPLOY.md) |
| **Test end-to-end** (seed admin → đăng nhập → thao tác từng trang) | ⬜ | key đã có; còn chạy `npm run seed:admin` + login thử |

## 5. Liên kết

- API surface đầy đủ: `../server/GUIDE.md` mục 5 + Swagger `http://localhost:3000/docs`.
- Deploy: `../DEPLOY.md`. Domain chuẩn: 3 file PDF ở root repo.

---

## 6. Changelog thiết kế (mới → cũ, mỗi đợt 1-3 dòng)

- **2026-07-26 — Đợt hoàn thiện**: thêm 2 trang MomentsPage (kiểm duyệt, `/moments`) + LogsPage (audit log, `/logs`); Sider 5 mục; API mới `listMoments/deleteMoment/listLogs`.
- **2026-07-26 — Vá lỗi sau review**: uid fallback decode từ JWT; form khung reset ngưỡng N khi đổi loại; errorElement router; xóa khung đóng drawer + invalidate; search grant debounce; Alert lỗi GET; login fail signOut Firebase.
- **2026-07-26 — Đại tu 4 trang**: Users thêm cột Vai trò/Đăng nhập cuối + thu quyền + chặn tự thao tác; Frames 6 điều kiện mở khóa + drawer owners; Dashboard 2 biểu đồ 7 ngày.

## 7. Cách cập nhật file này (bắt buộc — xem RULE đầu file)

1. **Mỗi thay đổi** (dù nhỏ) → cập nhật NGAY, ghi rõ **thay đổi gì trong thiết kế** vào **Changelog mục 6** (mới nhất lên đầu).
2. Trang/route/API-call mới → sửa **cây mục 3** + **cây route mục 1.5** + **bảng task mục 4**.
3. Đổi luồng auth/data → sửa **mục 1**. Đổi contract API → sửa ở server trước (ruler server), ghi cả 2 GUIDE.
4. Đổi ngày "Cập nhật lần cuối" ở đầu file.

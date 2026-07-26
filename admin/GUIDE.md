# GUIDE.md — Bản đồ **Snapget Admin** (React web)

> Đọc `admin/.claude/CLAUDE.md` (luật) trước. File này là bản đồ: cây thư mục + ý nghĩa, bảng task + tiến độ.
> Cập nhật sau **mỗi** lần sửa code.

---

## 1. Trạng thái tổng quát

**🟢 Code Phase 1 hoàn tất (2026-07-13)** — build + lint sạch, đủ 4 trang (Login/Dashboard/Users/Frames).
**Chưa test end-to-end** vì máy hiện tại thiếu `server/firebase-service-account.json` (xem mục 5) → chưa seed được admin, chưa đăng nhập thật được.

## 2. Chạy dự án

```bash
# 1. Server phải chạy trước
cd server && npm run start:dev          # http://localhost:3000/api, Swagger /docs

# 2. Admin
cd admin && npm install && npm run dev  # http://localhost:5173
```

- Copy `.env.example` → `.env`, điền `VITE_API_BASE_URL` + config Firebase web (lấy ở Firebase Console → Project settings → General → Your apps → Web).
- Tài khoản admin đầu tiên: `cd server && npm run seed:admin -- viethoang5301314@gmail.com` (email phải đã đăng ký trong app). Sau đó đăng nhập admin web bằng email/password Firebase của tài khoản đó.

## 3. Cây thư mục & ý nghĩa

```
admin/
├── .claude/CLAUDE.md      # luật code admin (đọc trước)
├── GUIDE.md               # file này
├── src/
│   ├── main.tsx           # bootstrap: ConfigProvider viVN + theme sáng (#8C6239), react-query, router
│   ├── App.tsx            # router: /login public; / (AdminLayout + RequireAuth) → dashboard, users, frames
│   ├── api/
│   │   ├── client.ts      # axios instance: baseURL từ env, Bearer JWT, bóc envelope {success,data,message}, 401 → logout
│   │   ├── auth.api.ts    # adminLogin(idToken) → POST /auth/admin/login
│   │   ├── admin.api.ts   # listUsers / getStats / setUserDisabled / grantAdmin
│   │   ├── frames.api.ts  # listFrames / createFrame / updateFrame / deleteFrame / grantFrame
│   │   └── upload.api.ts  # uploadImage(file) → POST /upload (multipart)
│   ├── auth/
│   │   ├── firebase.ts    # init Firebase web app từ VITE_FIREBASE_*
│   │   ├── AuthContext.tsx# giữ JWT (localStorage `snapget_admin_token`) + login()/logout()
│   │   └── RequireAuth.tsx# guard route, chưa đăng nhập → /login
│   ├── layouts/AdminLayout.tsx  # Sider menu 3 mục + Header (email admin, nút đăng xuất) + <Outlet/>
│   ├── pages/
│   │   ├── LoginPage.tsx      # form email/password → Firebase → đổi JWT
│   │   ├── DashboardPage.tsx  # thẻ thống kê GET /admin/stats
│   │   ├── UsersPage.tsx      # bảng user: search, phân trang, khóa/mở khóa, cấp admin
│   │   └── FramesPage.tsx     # grid khung ảnh: thêm (upload) / sửa / xóa / cấp cho user
│   ├── types/index.ts     # AdminUser, AdminStats, Frame, Paginated<T>… khớp DTO server
│   └── components/        # (trống — chỉ thêm khi dùng lại ≥2 trang)
├── .env.example
└── vite.config.ts
```

## 4. Bảng task & tiến độ

| Task | Trạng thái | Ghi chú |
|---|---|---|
| Scaffold Vite + React + TS + AntD | ✅ | React 18 + Vite 5 + AntD 5 (locale viVN, colorPrimary #8C6239); thay boilerplate JS cũ |
| Luồng đăng nhập Firebase → JWT server | ✅ (code) | AuthContext 2 bước; CHƯA test thật — cần service account key + seed admin |
| AdminLayout + router + guard | ✅ | Sider 3 mục + header đăng xuất; RequireAuth redirect /login; 401 → tự logout |
| DashboardPage (stats) | ✅ | 6 thẻ Statistic từ GET /admin/stats |
| UsersPage (search/pagination/lock/grant) | ✅ | Table server-side pagination 10/trang; Popconfirm khóa/mở + cấp admin |
| FramesPage (CRUD + grant + upload) | ✅ | Card grid; modal thêm/sửa (upload qua /upload/admin); grant qua Select tìm user; server đã có PATCH /frames/:id + GET /frames/admin |
| Thống kê quest trên Dashboard | ✅ | DashboardPage đã hiện thẻ `questCompletionsToday` từ GET /admin/stats (server quests xong 2026-07-13) |
| Test end-to-end (đăng nhập → thao tác) | ⬜ | chờ `server/firebase-service-account.json` + `npm run seed:admin` |

## 5. Việc cần user chuẩn bị để chạy thật

1. **`server/firebase-service-account.json`**: tải từ Firebase Console → Project settings → Service accounts → Generate new private key, lưu vào `server/`. Sửa `server/.env`: `FIREBASE_SERVICE_ACCOUNT=firebase-service-account.json` (hiện đang trỏ nhầm sang **email** service account).
2. Tài khoản `viethoang5301314@gmail.com` phải đã đăng ký trong app (Firebase Auth) → chạy `cd server && npm run seed:admin` → đăng nhập admin web bằng email/mật khẩu đó.
3. Nếu đăng nhập web báo lỗi API key (key Android bị giới hạn platform): tạo **Web App** trong Firebase Console và thay 4 biến `VITE_FIREBASE_*` trong `admin/.env` (đang mượn key từ `google-services.json`).

## 6. Liên kết

- Lộ trình chung: `../TODO.md` (root).
- API surface đầy đủ: `../server/GUIDE.md` + Swagger `http://localhost:3000/docs`.

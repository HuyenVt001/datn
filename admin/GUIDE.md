# Đánh thức server (gói free ngủ sau 15 phút — luôn chạy trước khi demo)
curl https://datn-8810.onrender.com/api/health



# GUIDE.md — Bản đồ **Snapget Admin** (React web)

> Đọc `admin/.claude/CLAUDE.md` (luật) trước. File này là bản đồ: cây thư mục + ý nghĩa, bảng task + tiến độ.
> Cập nhật sau **mỗi** lần sửa code.

---

## 1. Trạng thái tổng quát

**🟢 Đại tu trang admin hoàn tất (2026-07-26)** — build + lint sạch. 4 trang (Login/Dashboard/Users/Frames) nâng cấp toàn diện:
- **Users**: cột Vai trò (Admin/User) + cột Đăng nhập cuối; cấp **và THU** quyền admin; không thao tác được lên chính mình (tag "Bạn"); thu quyền/khóa có hiệu lực NGAY (server guard re-check mỗi request).
- **Frames**: 6 điều kiện mở khóa khi thêm/sửa khung (thưởng quest / mốc streak / đủ N bài đăng / đủ N bạn bè / chụp chung lần đầu / mở sẵn) — loại có ngưỡng N nhập số tự do; drawer "Ai đang sở hữu?" (icon 👥 trên card) liệt kê user sở hữu khung.
- **Dashboard**: thêm 2 biểu đồ cột 7 ngày (bài đăng/ngày + user mới/ngày, SVG thuần không thêm lib; màu validate dataviz: #A85A1E / #0C7BB3).
**Đợt vá lỗi sau review (2026-07-26, cùng ngày):** `uid` fallback decode từ payload JWT (phiên đăng nhập cũ không có UID_KEY vẫn chặn được thao-tác-lên-chính-mình); form khung ảnh **reset ngưỡng N khi đổi loại điều kiện** (hết dính ngưỡng loại cũ) + rule min/max; `metaOf()` fallback cho unlockType lạ + `errorElement` router (hết white-screen); xóa khung đóng drawer owners + invalidate cache; search cấp khung reset khi mở + debounce 300ms; cả 4 trang hiển thị Alert khi query GET lỗi; login fail tự signOut phiên Firebase; nhãn grid biểu đồ chỉ số nguyên.
**Đợt hoàn thiện (2026-07-26, cùng ngày):** thêm 2 trang mới — **Bài đăng** (kiểm duyệt: lưới bài mọi user + xóa bài vi phạm, route `/moments`) và **Nhật ký** (audit log admin, route `/logs`); menu Sider giờ 5 mục. API mới trong `admin.api.ts`: `listMoments`/`deleteMoment`/`listLogs`.
**Service account key đã có** trên máy (`server/.env` trỏ qua `FIREBASE_SERVICE_ACCOUNT`).
**Chưa test end-to-end** — còn cần chạy `npm run seed:admin` và đăng nhập thử (xem mục 5).

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
│   │   ├── client.ts      # axios instance: baseURL từ env, Bearer JWT, bóc envelope {success,data,message}, 401 → logout (TOKEN/EMAIL/UID_KEY)
│   │   ├── auth.api.ts    # adminLogin(idToken) → POST /auth/admin/login (trả kèm uid)
│   │   ├── admin.api.ts   # listUsers / getStats / getDailyStats / setUserDisabled / grantAdmin / revokeAdmin
│   │   ├── frames.api.ts  # listFrames / createFrame / updateFrame (unlockType+unlockValue) / deleteFrame / grantFrame / listFrameOwners
│   │   └── upload.api.ts  # uploadImage(file) → POST /upload/admin (multipart, Admin JWT)
│   ├── auth/
│   │   ├── firebase.ts    # init Firebase web app từ VITE_FIREBASE_*
│   │   ├── AuthContext.tsx# giữ JWT + email + uid (localStorage) + login()/logout() — uid để trang Users biết "chính mình"
│   │   └── RequireAuth.tsx# guard route, chưa đăng nhập → /login
│   ├── layouts/AdminLayout.tsx  # Sider menu 3 mục + Header (email admin, nút đăng xuất) + <Outlet/>
│   ├── pages/
│   │   ├── MomentsPage.tsx    # (2026-07-26) KIỂM DUYỆT bài đăng: lưới bài mọi user (ảnh/video, tác giả, caption, ngày) + xóa bài vi phạm (GET /admin/moments + DELETE /admin/moments/:id)
│   │   ├── LogsPage.tsx       # (2026-07-26) NHẬT KÝ admin: bảng audit log (ai làm gì lên đối tượng nào lúc nào) từ GET /admin/logs
│   │   ├── LoginPage.tsx      # form email/password → Firebase → đổi JWT
│   │   ├── DashboardPage.tsx  # thẻ thống kê GET /admin/stats + 2 biểu đồ cột 7 ngày (GET /admin/stats/daily, SVG thuần `DailyBarChart`)
│   │   ├── UsersPage.tsx      # bảng user: search, phân trang, cột Vai trò/Đăng nhập cuối, khóa/mở khóa, cấp + THU quyền admin, chặn thao tác lên chính mình
│   │   └── FramesPage.tsx     # grid khung ảnh: thêm/sửa (điều kiện mở khóa 6 loại + ngưỡng N) / xóa / cấp cho user / drawer user sở hữu
│   ├── types/index.ts     # AdminUser (admin, lastSignInAt), AdminStats, DailyStat, Frame (unlockType/unlockValue), FrameOwner, Paginated<T>… khớp DTO server
│   └── components/        # (trống — chỉ thêm khi dùng lại ≥2 trang)
├── .env.example
└── vite.config.ts
```

## 4. Bảng task & tiến độ

| Task | Trạng thái | Ghi chú |
|---|---|---|
| Scaffold Vite + React + TS + AntD | ✅ | React 18 + Vite 5 + AntD 5 (locale viVN, colorPrimary #8C6239); thay boilerplate JS cũ |
| Luồng đăng nhập Firebase → JWT server | ✅ (code) | AuthContext 2 bước; CHƯA test thật — key đã có, còn chờ seed admin |
| AdminLayout + router + guard | ✅ | Sider 3 mục + header đăng xuất; RequireAuth redirect /login; 401 → tự logout |
| DashboardPage (stats) | ✅ | 6 thẻ Statistic từ GET /admin/stats |
| UsersPage (search/pagination/lock/grant) | ✅ | Table server-side pagination 10/trang; Popconfirm khóa/mở + cấp admin |
| FramesPage (CRUD + grant + upload) | ✅ | Card grid; modal thêm/sửa (upload qua /upload/admin); grant qua Select tìm user; server đã có PATCH /frames/:id + GET /frames/admin |
| Thống kê quest trên Dashboard | ✅ | DashboardPage đã hiện thẻ `questCompletionsToday` từ GET /admin/stats (server quests xong 2026-07-13) |
| **Role hoàn thiện trên UsersPage** | ✅ 2026-07-26 | Cột Vai trò (Tag Admin 👑/User) + Đăng nhập cuối; nút Cấp admin ↔ Thu quyền theo role; hàng của chính mình chỉ hiện "Tài khoản của bạn" (server cũng chặn tự khóa/tự thu quyền); message nêu rõ thu quyền hiệu lực ngay |
| **Điều kiện mở khóa khung** | ✅ 2026-07-26 | Form thêm/sửa: Select 6 loại (`UNLOCK_META`) + field ngưỡng N theo loại (mốc streak Select 3/7/14/30, số bài InputNumber ≥1, số bạn 1..20); tag màu theo loại trên card |
| **Drawer user sở hữu khung** | ✅ 2026-07-26 | Icon 👥 trên card → GET /frames/:id/owners, list avatar+tên+email; khung DEFAULT có Alert giải thích "mọi user đều có" |
| **Biểu đồ 7 ngày trên Dashboard** | ✅ 2026-07-26 | 2 card: Bài đăng/ngày (#A85A1E) + Người dùng mới/ngày (#0C7BB3) từ GET /admin/stats/daily; SVG thuần (không thêm lib chart), tooltip hover, nhãn ở cột max; màu đã chạy dataviz validator PASS light+dark |
| Test end-to-end (đăng nhập → thao tác) | ⬜ | key đã có; còn chờ `npm run seed:admin` + đăng nhập thử |

## 5. Việc cần user chuẩn bị để chạy thật

1. ✅ **Service account key**: đã có — `server/snapget-d8693-firebase-adminsdk-fbsvc-d08b18f0f5.json` (tải từ Firebase Console → Project settings → Service accounts → Generate new private key). `server/.env` đã trỏ đúng: `FIREBASE_SERVICE_ACCOUNT=./snapget-d8693-firebase-adminsdk-fbsvc-d08b18f0f5.json`. File đã được `.gitignore` (pattern `*-firebase-adminsdk-*.json`).
2. Tài khoản `viethoang5301314@gmail.com` phải đã đăng ký trong app (Firebase Auth) → chạy `cd server && npm run seed:admin` → đăng nhập admin web bằng email/mật khẩu đó.
3. Nếu đăng nhập web báo lỗi API key (key Android bị giới hạn platform): tạo **Web App** trong Firebase Console và thay 4 biến `VITE_FIREBASE_*` trong `admin/.env` (đang mượn key từ `google-services.json`).

## 6. Liên kết

- Lộ trình chung: `../TODO.md` (root).
- API surface đầy đủ: `../server/GUIDE.md` + Swagger `http://localhost:3000/docs`.

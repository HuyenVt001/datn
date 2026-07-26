# CLAUDE.md — Ruler cho **Snapget Admin** (React web)

> Quy ước cho toàn bộ code trong `admin/`. Đọc file này **trước**, rồi mở `admin/GUIDE.md` để biết "sửa gì thì vào file nào".
> Phần giải thích bằng **tiếng Việt**; **code + tên định danh luôn tiếng Anh**.

---

## 0. Nguyên tắc tối thượng (kế thừa từ app/server)

1. **Luôn hỏi user vài câu** trước khi bắt đầu việc gì còn mơ hồ (không tự đoán khi chưa chắc).
2. **Luôn đọc & cập nhật `admin/GUIDE.md` sau MỖI lần chỉnh sửa code** — không đợi "xong tính năng". Giữ đồng bộ trong GUIDE:
   - **Cây thư mục + ý nghĩa từng thư mục/file**.
   - **Bảng task**: ✅ đã làm · 🔄 đang làm · ⬜ chưa làm, kèm tiến độ.
   Sửa code mà không cập nhật GUIDE = **chưa xong việc**.
3. Admin là **một phần của monorepo DATN**: `Snapget/` (app Android) ↔ `server/` (NestJS) ↔ `admin/` (web này). Admin **không tự bịa endpoint** — chỉ gọi API đã tồn tại ở server (xem `server/GUIDE.md`); thiếu endpoint → làm ở server trước (theo ruler của server), rồi mới gọi từ admin.
4. **Domain chuẩn** = 3 file PDF ở root repo + `server/.claude/CLAUDE.md` mục 6 (thực thể) & 16 (business rules). Lộ trình chung ở `TODO.md` (root).
5. **Sau khi hoàn thành MỖI yêu cầu của user có sửa code trong `admin/`** → chạy `codegraph init` cho thư mục này (`cd admin && codegraph init`) để cập nhật index CodeGraph.

---

## 1. Admin web này là gì

SPA quản trị cho quản trị viên Snapget, chức năng đúng theo tài liệu phân tích:

- **Quản lý người dùng**: xem danh sách, tìm kiếm, khóa/mở khóa tài khoản, cấp quyền admin.
- **Xem thống kê**: số user, moment, moment hôm nay, message, friendship, nhóm chat (+ thống kê quest khi Phase 2 xong).
- **Quản lý khung ảnh**: thêm / sửa / xóa khung, cấp khung thủ công cho user.

```
Admin (React SPA) ──axios──► NestJS API (server/) ──► Firebase / Cloudinary
```

Admin **không** chạm Firestore trực tiếp — mọi dữ liệu đi qua REST API của server. Firebase web SDK chỉ dùng cho **bước đăng nhập** (lấy ID token để đổi JWT).

---

## 2. Tech stack (đã CHỐT — không đổi nếu chưa hỏi user)

| Hạng mục | Lựa chọn |
|---|---|
| Build | **Vite** + **React 18** + **TypeScript strict** |
| UI kit | **Ant Design 5** — `ConfigProvider` locale **vi_VN**, **theme sáng**, `colorPrimary` nâu brand `#8C6239` |
| Ngôn ngữ UI | **Tiếng Việt** toàn bộ label/menu/thông báo |
| Routing | `react-router-dom` v6 (`createBrowserRouter`) |
| Data fetching | **axios** (instance chung + interceptor) + **@tanstack/react-query** (cache, loading, invalidate sau mutation) |
| Auth | Firebase Web SDK (email/password) → `POST /auth/admin/login` → **JWT của server** (xem mục 5) |
| Form | AntD `Form` (không thêm react-hook-form) |
| Lint/format | ESLint + Prettier (config chuẩn Vite React TS) |
| Package manager | **npm** |

> ❌ Không thêm: Redux/Zustand, SSR/Next, Tailwind, Docker, i18n framework (UI cố định tiếng Việt). Nếu cần → hỏi user trước.

---

## 3. Cấu trúc thư mục

```
admin/
├── src/
│   ├── main.tsx               # bootstrap: ConfigProvider (viVN + theme), QueryClientProvider, RouterProvider
│   ├── App.tsx                # khai báo router
│   ├── api/                   # NƠI DUY NHẤT gọi HTTP
│   │   ├── client.ts          # axios instance: baseURL, gắn Bearer JWT, bóc envelope, bắt 401
│   │   ├── auth.api.ts        # POST /auth/admin/login
│   │   ├── admin.api.ts       # GET /admin/users, /admin/stats, PATCH disabled, POST grant-admin
│   │   ├── frames.api.ts      # GET/POST/PATCH/DELETE /frames, POST /frames/:id/grant/:uid
│   │   └── upload.api.ts      # POST /upload (ảnh khung)
│   ├── auth/
│   │   ├── firebase.ts        # khởi tạo Firebase web app (config từ env)
│   │   ├── AuthContext.tsx    # giữ JWT + thông tin admin, login()/logout()
│   │   └── RequireAuth.tsx    # route guard: chưa có JWT → về /login
│   ├── layouts/
│   │   └── AdminLayout.tsx    # Sider menu (Dashboard/Người dùng/Khung ảnh) + Header + Outlet
│   ├── pages/
│   │   ├── LoginPage.tsx
│   │   ├── DashboardPage.tsx
│   │   ├── UsersPage.tsx
│   │   └── FramesPage.tsx
│   ├── types/                 # kiểu dữ liệu khớp DTO server (AdminUser, Stats, Frame, Envelope, Paginated)
│   └── components/            # component dùng lại ≥2 trang mới đặt ở đây
├── .env / .env.example        # VITE_API_BASE_URL + VITE_FIREBASE_* (🔒 .env không commit)
├── index.html
├── package.json
├── tsconfig.json
├── vite.config.ts
└── GUIDE.md                   # bản đồ admin (bắt buộc duy trì)
```

**Quy tắc phân tầng:** Page/Component → hook react-query → hàm trong `api/*.api.ts` → axios `client`. Component **không** gọi axios trực tiếp, **không** fetch trong `useEffect` chay.

---

## 4. Quy ước code

- **Tên định danh tiếng Anh**; comment + text UI **tiếng Việt**.
- File component `PascalCase.tsx`; file thường `kebab-case.ts` hoặc `camelCase.ts` theo nhóm sẵn có; hook `useXxx`.
- **TypeScript strict**: không `any` tùy tiện; response gõ kiểu qua `types/`.
- Mutation xong → `queryClient.invalidateQueries` cho list liên quan + `message.success` tiếng Việt.
- Lỗi API → hiện `message.error(err.message)` — `message` server đã là tiếng Việt thân thiện, đừng ghi đè trừ khi rỗng.
- Hành động nguy hiểm (khóa tài khoản, xóa khung, cấp admin) → bọc `Modal.confirm`/`Popconfirm`.
- Chạy `npm run lint` trước khi coi là xong.

---

## 5. Auth (luồng 2 bước — đừng làm tắt)

1. Admin nhập email/password → `signInWithEmailAndPassword` (Firebase Web SDK).
2. Lấy `getIdToken()` → gọi `POST /auth/admin/login` (body `{ idToken }`) → server kiểm tra custom claim `admin === true` → trả **JWT của server**.
3. Lưu JWT (localStorage key `snapget_admin_token`) + gắn `Authorization: Bearer <jwt>` cho mọi request qua interceptor trong `api/client.ts`.
4. Response **401/403** → xóa token, đá về `/login` (kèm thông báo phiên hết hạn).
5. **KHÔNG** gửi Firebase ID token cho các route admin — route admin chỉ nhận JWT server.

> Tài khoản admin đầu tiên: seed bằng script ở server — `npm run seed:admin -- viethoang5301314@gmail.com` (email phải đã đăng ký Firebase Auth). Cấp admin tiếp theo dùng nút trong trang Người dùng.

---

## 6. API contract (khớp server, đừng phá)

- Mọi response bọc envelope `{ success, statusCode, message, data }` — `api/client.ts` bóc sẵn `data`, lỗi ném `Error(message)`.
- Phân trang: `data = { items, page, limit, total }` — map thẳng vào AntD `Table` `pagination`.
- Endpoint hiện có cho admin (chi tiết ở `server/GUIDE.md`):
  `POST /auth/admin/login` · `GET /admin/users?search=&page=&limit=` · `GET /admin/stats` · `PATCH /admin/users/:uid/disabled` · `POST /admin/users/:uid/grant-admin` · `GET /frames` · `POST /frames` · `PATCH /frames/:id` · `DELETE /frames/:id` · `POST /frames/:id/grant/:uid` · `POST /upload`.
- Đổi/thêm contract → sửa ở server trước (ruler server mục 14), ghi cả 2 GUIDE.

---

## 7. Config & Secrets

```
VITE_API_BASE_URL=http://localhost:3000/api
VITE_FIREBASE_API_KEY=...
VITE_FIREBASE_AUTH_DOMAIN=...
VITE_FIREBASE_PROJECT_ID=...
VITE_FIREBASE_APP_ID=...
```

- 🔒 `.env` không commit; luôn cập nhật `.env.example` khi thêm biến.
- Không hardcode URL/key trong source; đọc qua `import.meta.env`.

---

## 8. Lệnh thường dùng

```bash
npm install
npm run dev       # http://localhost:5173
npm run build     # tsc + vite build
npm run lint
npm run preview
```

Server NestJS phải chạy trước (`cd server && npm run start:dev`) và CORS đã mở cho `http://localhost:5173`.

---

## 9. Quy trình chuẩn — thêm 1 trang admin

1. Thêm hàm gọi API vào `api/<domain>.api.ts` (+ type vào `types/`).
2. Tạo `pages/XxxPage.tsx` dùng react-query.
3. Thêm route vào `App.tsx` (bên trong `RequireAuth` + `AdminLayout`) + item menu ở `AdminLayout.tsx`.
4. Text UI tiếng Việt, confirm cho hành động nguy hiểm.
5. `npm run lint` + chạy thử → cập nhật `GUIDE.md` (cây thư mục + bảng task).

---

> **Nhắc lại:** còn phân vân → **hỏi user**. Sửa code (dù nhỏ) → **cập nhật `admin/GUIDE.md`**.

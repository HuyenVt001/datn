# CLAUDE.md — Ruler cho **Snapget Admin** (React web)

> **CLAUDE.md = LUẬT** (quy ước, ràng buộc, quyết định đã chốt — ít thay đổi). **GUIDE.md = BẢN ĐỒ** (kiến trúc hiện tại, cây thư mục, tiến độ, changelog — cập nhật liên tục).
> Đọc file này **trước**, rồi mở `admin/GUIDE.md` để biết "đang có gì, ở đâu, sửa gì thì vào file nào".
> Phần giải thích bằng **tiếng Việt**; **code + tên định danh luôn tiếng Anh**.

---

## 0. Nguyên tắc tối thượng (kế thừa từ app/server)

1. **Luôn hỏi user vài câu** trước khi bắt đầu việc gì còn mơ hồ (không tự đoán khi chưa chắc).
2. **Luôn đọc & cập nhật `admin/GUIDE.md` sau MỖI lần chỉnh sửa code** — cách cập nhật + Changelog theo RULE ở đầu GUIDE.md (mục 7 của GUIDE). Đụng **bảo mật** → cập nhật thêm `SECURITY.md` ở root repo (rule 🔐 đầu GUIDE.md). Sửa code mà không cập nhật = **chưa xong việc**.
3. Admin là **một phần của monorepo DATN**: `Snapget/` (app Android) ↔ `server/` (NestJS) ↔ `admin/` (web này). Admin **không tự bịa endpoint** — chỉ gọi API đã tồn tại ở server (`server/GUIDE.md` mục 5); thiếu endpoint → làm ở server trước (theo ruler của server), rồi mới gọi từ admin.
4. **Domain chuẩn** = 3 file PDF ở root repo + `server/.claude/CLAUDE.md` mục 6 (thực thể) & 14 (business rules).
5. **Sau khi hoàn thành MỖI yêu cầu của user có sửa code trong `admin/`** → chạy `codegraph init` cho thư mục này (`cd admin && codegraph init`).

---

## 1. Admin web này là gì

SPA quản trị cho quản trị viên Snapget: quản lý người dùng (khóa/mở, cấp/thu quyền admin), thống kê (+ biểu đồ theo ngày), quản lý khung ảnh (CRUD/cấp/xem chủ sở hữu), kiểm duyệt bài đăng, nhật ký audit.

Admin **không** chạm Firestore trực tiếp — mọi dữ liệu qua REST API của server; Firebase Web SDK **chỉ** dùng cho bước đăng nhập (lấy ID token đổi JWT). Sơ đồ kiến trúc, cây route, luồng auth chi tiết, triển khai: **GUIDE.md mục 1**.

---

## 2. Tech stack (đã CHỐT — không đổi nếu chưa hỏi user)

| Hạng mục | Lựa chọn |
|---|---|
| Build | **Vite** + **React 18** + **TypeScript strict** |
| UI kit | **Ant Design 5** — `ConfigProvider` locale **vi_VN**, **theme sáng**, `colorPrimary` nâu brand `#8C6239` |
| Ngôn ngữ UI | **Tiếng Việt** toàn bộ label/menu/thông báo |
| Routing | `react-router-dom` v6 (`createBrowserRouter`, có `errorElement`) |
| Data fetching | **axios** (instance chung + interceptor) + **@tanstack/react-query** (cache, loading, invalidate sau mutation) |
| Auth | Firebase Web SDK (email/password) → `POST /auth/admin/login` → **JWT của server** (mục 5) |
| Form | AntD `Form` (không thêm react-hook-form) |
| Lint/format | ESLint + Prettier (config chuẩn Vite React TS) |
| Package manager | **npm** |

> ❌ Không thêm: Redux/Zustand, SSR/Next, Tailwind, Docker, i18n framework (UI cố định tiếng Việt), lib chart (biểu đồ = SVG thuần). Nếu cần → hỏi user trước.

---

## 3. Phân tầng (bắt buộc)

```
Page/Component ──► hook react-query (useQuery/useMutation) ──► api/*.api.ts ──► api/client.ts (axios)
```

- `api/` là **NƠI DUY NHẤT** gọi HTTP; `api/client.ts` là instance axios duy nhất (gắn Bearer JWT, bóc envelope, bắt 401). Component **không** gọi axios trực tiếp, **không** fetch trong `useEffect` chay.
- `components/` chỉ chứa component dùng lại **≥2 trang**; còn lại để trong page.
- Type khớp DTO server đặt ở `types/`.
- Cây thư mục thực tế + ý nghĩa từng file: **GUIDE.md mục 3**.

---

## 4. Quy ước code

- **Tên định danh tiếng Anh**; comment + text UI **tiếng Việt**.
- File component `PascalCase.tsx`; file thường theo nhóm sẵn có; hook `useXxx`.
- **TypeScript strict**: không `any` tùy tiện; response gõ kiểu qua `types/`.
- Mutation xong → `queryClient.invalidateQueries` cho list liên quan + `message.success` tiếng Việt.
- Lỗi API → `message.error(err.message)` — message server đã là tiếng Việt thân thiện, đừng ghi đè trừ khi rỗng. Query GET lỗi → hiện `Alert`.
- Hành động nguy hiểm (khóa tài khoản, xóa khung/bài, cấp/thu admin) → bọc `Modal.confirm`/`Popconfirm`.
- Chạy `npm run lint` trước khi coi là xong.

---

## 5. Auth (luật — sơ đồ từng bước: GUIDE.md mục 1.4)

- **Luồng 2 bước, đừng làm tắt**: Firebase Web SDK chỉ để lấy ID token → đổi **JWT của server** qua `POST /auth/admin/login`; mọi request sau đó mang JWT server.
- **KHÔNG** gửi Firebase ID token cho route admin — route admin chỉ nhận JWT server.
- JWT + email + uid lưu localStorage (key `snapget_admin_token`…); 401/403 → xóa token + đá về `/login`; login fail → signOut phiên Firebase.
- Tài khoản admin đầu tiên seed ở server (lệnh + hướng dẫn: GUIDE.md mục 2); cấp admin tiếp theo dùng nút trong trang Người dùng.

---

## 6. API contract (khớp server, đừng phá)

- Mọi response bọc envelope `{ success, statusCode, message, data }` — `api/client.ts` bóc sẵn `data`, lỗi ném `Error(message)`.
- Phân trang: `data = { items, page, limit, total }` — map thẳng vào AntD `Table` `pagination`.
- Danh sách endpoint hiện có: **`server/GUIDE.md` mục 5** (nguồn duy nhất — không chép lại ở đây).
- Đổi/thêm contract → sửa ở server trước (ruler server mục 12), ghi cả 2 GUIDE.

---

## 7. Config & Secrets

```
VITE_API_BASE_URL=http://localhost:3000/api
VITE_FIREBASE_API_KEY=... / AUTH_DOMAIN / PROJECT_ID / APP_ID
```

- 🔒 `.env` không commit; thêm biến → cập nhật `.env.example` (+ `.env.production.example` cho deploy).
- Không hardcode URL/key trong source; đọc qua `import.meta.env`.

---

## 8. Quy trình chuẩn — thêm 1 trang admin

1. Thêm hàm gọi API vào `api/<domain>.api.ts` (+ type vào `types/`).
2. Tạo `pages/XxxPage.tsx` dùng react-query.
3. Thêm route vào `App.tsx` (trong `RequireAuth` + `AdminLayout`) + item menu ở `AdminLayout.tsx`.
4. Text UI tiếng Việt, confirm cho hành động nguy hiểm.
5. `npm run lint` + chạy thử → cập nhật `GUIDE.md` (+ `SECURITY.md` nếu đụng bảo mật).

Lệnh chạy dự án: GUIDE.md mục 2.

---

> **Nhắc lại:** còn phân vân → **hỏi user**. Sửa code (dù nhỏ) → **cập nhật `admin/GUIDE.md`** (+ `SECURITY.md` nếu đụng bảo mật).

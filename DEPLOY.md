# 🚀 DEPLOY.md — Hướng dẫn triển khai demo Snapget

> Chuẩn bị sẵn 2026-07-26. Mọi file config đã tạo sẵn — chỉ còn các bước cần **tài khoản của bạn** (Firebase/Render login).

## Kiến trúc khi deploy

```
App Android (release, HTTPS) ─┐
                              ├─► Server NestJS trên RENDER (https://datn-8810.onrender.com/api)
Admin React trên FIREBASE ────┘        └─► Firebase (Firestore/Auth/FCM) + Cloudinary
HOSTING (site riêng cho admin)
```

## 1. Server trên Render (ĐÃ có sẵn: `datn-8810.onrender.com`)

Server đã deploy từ trước. Sau các đợt sửa 2026-07-26, cần **redeploy** (push code mới) và rà lại Environment trên Render dashboard:

| Biến | Giá trị |
|---|---|
| `PORT` | Render tự set — giữ nguyên |
| `NODE_ENV` | `production` |
| `CORS_ORIGINS` | `https://snapget-admin-d8693.web.app` (THÊM origin admin mới, phân tách bằng dấu phẩy nếu giữ cả localhost) |
| `FIREBASE_SERVICE_ACCOUNT` | như đang cấu hình (đường dẫn file secret hoặc nội dung JSON — Render hỗ trợ Secret Files) |
| `JWT_SECRET` / `JWT_EXPIRES_IN` | như đang có |
| Cloudinary keys | như đang có |

Lưu ý: gói Render free "ngủ" sau 15 phút — trước khi demo hãy gọi `https://datn-8810.onrender.com/api/health` để đánh thức (đã ghi chú sẵn ở đầu admin/GUIDE.md).

## 2. Admin lên Firebase Hosting (site RIÊNG, không đụng site invite-link)

Site mặc định `snapget-d8693.web.app` đang phục vụ **landing page invite + assetlinks.json** (App Links của app Android) — KHÔNG deploy đè. Admin dùng **site thứ 2** qua hosting targets. File đã tạo sẵn: [admin/firebase.json](admin/firebase.json), [admin/.firebaserc](admin/.firebaserc), [admin/.env.production.example](admin/.env.production.example).

```bash
cd admin

# (lần đầu) đăng nhập + tạo site thứ 2 cho admin
npx firebase-tools login
npx firebase-tools hosting:sites:create snapget-admin-d8693   # tên site có thể đổi — nhớ sửa .firebaserc theo

# build production: copy .env.production.example -> .env.production, điền VITE_FIREBASE_*
npm run build

# deploy
npx firebase-tools deploy --only hosting:admin
```

Kết quả: `https://snapget-admin-d8693.web.app`. Sau đó **thêm origin này vào `CORS_ORIGINS` trên Render** (mục 1) rồi redeploy server.

## 3. App Android bản release (HTTPS)

Build **debug** vẫn dùng server local như cũ. Muốn app chạy với server Render (bản release không cho HTTP trần):

1. `Snapget/local.properties` → `server.base.url=https://datn-8810.onrender.com/api/`
2. `./gradlew.bat assembleRelease` (cần cấu hình signing nếu phát hành thật; demo có thể dùng `assembleDebug` với URL HTTPS này luôn).
3. Lưu ý App Links mời kết bạn vẫn trỏ `snapget-d8693.web.app/invite/...` — không đổi gì.

## 4. Checklist trước buổi demo

- [ ] `GET https://datn-8810.onrender.com/api/health` trả 200 (đánh thức server).
- [ ] Đăng nhập `https://snapget-admin-d8693.web.app` bằng tài khoản admin (đã seed).
- [ ] App trên máy thật trỏ đúng `server.base.url` (LAN khi dev, Render khi demo xa).
- [ ] Firestore đã seed khung (`npm run seed:frames`) + có ít nhất 1 admin (`npm run seed:admin`).

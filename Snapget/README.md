# 📸 Snapget App

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-blue?logo=kotlin)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Hilt](https://img.shields.io/badge/Hilt-DI-FF6F00?logo=dagger)](https://dagger.dev/hilt/)
[![Firebase](https://img.shields.io/badge/Firebase-Auth%20%2B%20FCM-FFCA28?logo=firebase)](https://firebase.google.com/)

App Android mạng xã hội kiểu **BeReal/Locket** — mở thẳng camera, chụp/quay nhanh (video ≤5s),
chia sẻ với vòng bạn bè nhỏ (≤20), nhắn tin 1-1/nhóm, gamification (streak, daily quest, khung ảnh
thưởng), chụp chung (co-op capture). **Toàn bộ tính năng miễn phí** — không có thanh toán.

Đây là phần **app Android** của monorepo đồ án (DATN):

```
Snapget/   ← app Android (Kotlin + Jetpack Compose)   — thư mục này
server/    ← NestJS API (cửa ngõ duy nhất tới Firebase/Cloudinary)
admin/     ← web quản trị (React + Vite + AntD)
```

## Kiến trúc

```
Composable Screen ──► ViewModel (StateFlow) ──► Repository ──► Retrofit ──► NestJS API ──► Firestore/Cloudinary/FCM
```

- **MVVM + Hilt**, UI **Jetpack Compose + Material 3**, điều hướng Navigation Compose.
- Auth ở client dùng **Firebase Auth** (email/password + Google Sign-In); mọi request data gắn
  Firebase ID token qua `AuthInterceptor` và đi qua **server NestJS** — app không chạm Firestore
  trực tiếp (phần còn sót đang migrate dần).
- Camera **CameraX** (chụp + quay ≤5s + quét QR kết bạn bằng ML Kit), ảnh load bằng **Coil**,
  upload media qua server → Cloudinary.

## Chạy dự án

```powershell
# 1. Server phải chạy trước
cd ../server; npm run start:dev        # http://localhost:3000/api (Swagger: /docs)

# 2. Build & cài app
./gradlew.bat assembleDebug
./gradlew.bat installDebug
./gradlew.bat spotlessApply            # format trước khi commit
```

Yêu cầu: Android Studio, `app/google-services.json` hợp lệ, `local.properties` có `sdk.dir`
(+ `server.base.url` nếu không dùng mặc định `http://10.0.2.2:3000/api/` cho emulator).

## Tài liệu

- **`.claude/GUIDE.md`** — bản đồ code chi tiết (cấu trúc thư mục, bảng route, tiến độ) — đọc file này trước khi sửa code.
- **`.claude/CLAUDE.md`** — quy ước code (luật).
- **`.claude/DESIGN.md`** — design system (màu, component, asset chuẩn ở `Sources/assets/`).

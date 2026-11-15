# SHOP_PLAN — Bỏ Gacha, thay bằng Cửa hàng (Shop)

> **Trạng thái (2026-11-15): ✅ CODE XONG — S (server) · A (admin) · P (app) · D (tài liệu)**. Còn lại là việc vận hành của user (mục "Checklist user"). ⚠️ Server đã đổi contract (`/gacha/*` biến mất) — deploy server + admin + phát APK mới **cùng đợt**.
> Plan này **thay thế** `GACHA_PLAN.md` (đã xoá 2026-11-15 — xem lại bằng `git log -- Snapget/.claude/GACHA_PLAN.md`). Phần còn giá trị (gói nạp §0.1, PayOS §4 + §12) đã chép vào **Phụ lục PayOS** cuối file.
> Đọc kèm: `SKIN_PLAN.md` (skin/effect engine trong APK), `DESIGN.md`, 3 `CLAUDE.md`.

## Kinh tế (giá mặc định Frame 320 · Effect 1600 · Skin 4800)

| Nguồn | Mua được |
|---|---|
| Tân thủ 1.600 ⭐ | ngay ngày đầu: 1 effect **hoặc** 5 khung |
| Chơi miễn phí 90 ⭐/ngày (daily 60 + AI 30) | ~4 ngày/khung · ~18 ngày/effect · ~54 ngày/skin |
| Gói 5.000đ (600 ⭐) | ~2 khung |
| Gói 24.000đ (3.000 ⭐) | 1 effect + 4 khung |
| Gói 75.000đ (9.800 ⭐) | 2 skin |

So với gacha cũ (~480 ngày chơi miễn phí mới đủ bộ): mua đứt minh bạch, không random, dễ giải thích khi bảo vệ DATN.

## Context

Gacha (quay ngẫu nhiên 160/1440 Astrite, pity, 4 bậc N/R/SR/SSR) đã chạy full-stack (GACHA_PLAN.md G0–G6). User muốn **bỏ hẳn gacha** và chuyển sang **mua đứt** trong một màn **Shop** mở từ icon Shop ở footer (`AppFooterBar` + `Screen.Shop` đã nối sẵn, `ShopScreen.kt` đang là hộp đen trống). Shop bán 4 nhóm: **gói nạp Astrite (PayOS)**, **Skin**, **Frame**, **Touch effect**; admin thêm/sửa/xoá mọi mặt hàng trên web.

Plan này chỉnh lại `implementation_plan.md` của user: sửa luồng vào (tab footer chứ không phải banner Daily Quest, không nút Back), đưa gói nạp thành **1 tab bán hàng** trong Shop, gom trang admin thành nhóm "Cửa hàng", chặn niêm yết trùng, thêm dọn dữ liệu gacha cũ, và chốt thứ tự **server → admin → app** đúng luật monorepo.

## Quyết định đã chốt (2026-11-15)

| Hạng mục | Chốt |
|---|---|
| Thanh toán vật phẩm | **Chỉ Astrite**. Tiền thật chỉ đi qua gói nạp PayOS (giữ nguyên module `topup`, webhook idempotent, `/topup/simulate`) |
| Skin / Effect | Asset vẫn **trong APK**. Admin **niêm yết theo id**: tạo/sửa/xoá *mặt hàng* (tên, giá, ảnh, bật/tắt, thứ tự) trỏ `refId` = skinId/effectId có sẵn. Skin/effect mới phải code vào app trước |
| Frame | `unlockType` **`GACHA` → `SHOP`** (repo map `GACHA`/`QUEST_RANDOM` cũ → `SHOP`). Chỉ khung SHOP được bán; khung STREAK/POST/FRIEND/COOP/DEFAULT giữ cơ chế cũ |
| Giá mặc định | **Frame 320 · Effect 1600 · Skin 4800** Astrite (admin sửa từng món) |
| Bỏ hẳn | rarity N/R/SR/SSR, `gachaPity`, pity, hoàn trùng, banner gacha + drawable gacha |
| Nguồn Astrite miễn phí | Giữ: tân thủ 1600 · daily 2/2 +60 · AI quest +30 |
| ShopScreen | Tab footer, **không nút Back**. Top bar "Shop" + ô số dư `⭐ N +`. **4 tab: Astrite · Skins · Frames · Effects**, lưới 2 cột |
| Sau khi mua | Skin/Effect → dialog **"Apply now?"**; Frame → toast "Unlocked" |
| Banner Daily Quest | **Xoá** (Shop đã ở footer) |
| Dữ liệu gacha cũ | **Script dọn sạch** (xem S7) — vật phẩm user đã quay được (`unlocked*`) **giữ nguyên** |

## Mô hình dữ liệu

- `users/{uid}`: giữ `astrite`, `unlockedFrames: string[]`, `unlockedSkins: number[]`, `unlockedEffects: number[]`, `signupBonusClaimed`. **Bỏ `gachaPity`.**
- **`shopItems/{itemId}`** (mới, thay `gachaItems`): `itemName`, `itemType` (FRAME|SKIN|EFFECT), `refId: string` (frameId, hoặc số skin/effect dạng chuỗi), `price` (int ≥0), `imageUrl?`, `isActive`, `sortOrder`, `createdAt`. **Unique theo (itemType, refId)** — chặn niêm yết trùng ở service.
- **`shopPurchases/{id}`** (mới, thay `gachaRolls`): `uid`, `itemId`, `itemName`, `itemType`, `refId`, `price`, `balanceAfter`, `createdAt` (snapshot để lịch sử không vỡ khi item bị sửa/xoá).
- `astriteTransactions.type`: thêm **`SHOP_PURCHASE`**, bỏ `GACHA_SPEND`/`GACHA_REFUND` (dòng cũ bị dọn ở S7).
- `topupPackages`, `topupOrders`: **không đổi**.

## Thuật toán mua — `POST /shop/buy { itemId }` (1 transaction)

```
item = repo.getItem(itemId); !item || !item.isActive -> 400 "Vật phẩm này hiện không mở bán."
FRAME: frame phải tồn tại và unlockType === 'SHOP'
tx:
  user = getUserInTransaction(uid)          // AstriteRepository helper
  owned(user, type, refId) -> 409 "Bạn đã sở hữu vật phẩm này."
  user.astrite < price     -> 400 "Bạn không đủ Astrite."
  setBalanceInTransaction(balance - price)
  arrayUnion vào unlockedFrames (string) | unlockedSkins/unlockedEffects (Number(refId))
  shopPurchases/new + addEntryInTransaction(SHOP_PURCHASE, -price, refId=purchaseId)
return { purchase, astriteAfter }
```
Đúng luật CLAUDE.md server mục 14: số dư chỉ đổi qua `AstriteRepository` trong transaction. Giá luôn lấy từ server, client chỉ gửi `itemId`.

---

## Thi công (theo thứ tự)

### S — Server (`server/`) — ✅ XONG 2026-11-15

1. **Module mới `src/shop/`** (khung 3 tầng như `gacha/`): `shop.module/controller/service/repository.ts`, `entities/shop-item.entity.ts` (`ITEM_TYPES`, `DEFAULT_PRICE_BY_TYPE`), `entities/shop-purchase.entity.ts`, `dto/{buy-item, create-shop-item, update-shop-item (PartialType), admin-purchases}.dto.ts`, `shop.service.spec.ts`.
   - Tái dùng: logic map type→field + `grantItem`/`listItemOwners` từ `gacha.service.ts` (L404–L470), `UsersRepository.unlockCollectible`/`listByCollectible` (`users.repository.ts` L82–103), helpers transaction của `astrite.repository.ts` (L35–L60), pattern CRUD + audit của `topup.controller.ts`.
   - **App** (FirebaseAuthGuard): `GET /shop/items` → `{ astrite, items[] (+isOwned) }` · `POST /shop/buy` · `GET /shop/history?limit`.
   - **Admin** (AdminJwtGuard + Roles): `GET /shop/items/admin` · `POST /shop/items` (SKIN/EFFECT: `refId` số nguyên ≥1; FRAME: frame tồn tại & SHOP) · `PATCH /shop/items/:id` · `DELETE /shop/items/:id` · `GET /shop/items/:id/owners` · `POST /shop/items/:id/grant/:uid` · `GET /shop/history/admin?uid&itemType&date&limit`. Audit `SHOP_ITEM_CREATE/UPDATE/DELETE/GRANT`.
   - Swagger tiếng Việt đủ mọi route.
2. **Xoá `src/gacha/`** toàn bộ; `app.module.ts`, `admin.module.ts` thay `GachaModule` → `ShopModule`; `admin.service.ts`/`admin.repository.ts`: `gachaRollsToday/Total` → `shopPurchasesToday/Total` (+ cập nhật `admin.service.spec.ts`).
3. **Frames**: `frame.entity.ts` `UNLOCK_TYPES` `GACHA`→`SHOP`; `frames.repository.ts` L49–61 map `GACHA`/`QUEST_RANDOM`→`SHOP`; `create-frame.dto.ts` default `SHOP`; `frames.service.ts` `syncGachaPool` → `syncShopCatalog` (frame SHOP → upsert shopItem giá 320 nếu chưa có, **không ghi đè giá admin đã sửa**; đổi khỏi SHOP / xoá frame → xoá shopItem); `frames.module.ts` forwardRef sang `ShopModule`; sửa `frames.service.spec.ts`.
4. **Constants** (`common/constants.ts`): xoá `GACHA_*` (L15–41); thêm `SHOP_DEFAULT_PRICE_FRAME=320/EFFECT=1600/SKIN=4800`; `Collections`: `GACHA_ITEMS/GACHA_ROLLS/CONFIG` → `SHOP_ITEMS/SHOP_PURCHASES`; sửa comment `SIGNUP_BONUS_ASTRITE`.
5. **Users/Astrite/Audit**: bỏ `GachaPity`/`emptyPity`/`gachaPity` (`user.entity.ts` L102–115, `users.service.ts` L57/L78, `users.repository.ts` toEntity); `astrite-transaction.entity.ts` thêm `SHOP_PURCHASE`, bỏ `GACHA_*`; `admin-log.entity.ts` `GACHA_ITEM_*` → `SHOP_ITEM_*`.
6. **Scripts**: xoá `seed-gacha.ts` + `seed:gacha`; thêm **`scripts/seed-shop.ts`** (`npm run seed:shop`, idempotent theo (itemType, refId)): chuẩn hoá frames `GACHA`/`QUEST_RANDOM` → `SHOP`; tạo shopItems cho Snow(1), Forest(2), Flower(1)…Magic(4) + mọi frame SHOP, mang sang `itemName/imageUrl/isActive` từ `gachaItems` nếu còn. Cập nhật `schema-metadata.ts` (Shop & Economy) → `npm run schema:export`; `dev-streak.ts` tag.
7. **Script dọn gacha `scripts/cleanup-gacha.ts`** (`npm run cleanup:gacha`): **mặc định dry-run** (in số doc sẽ xoá), chỉ xoá khi có `--confirm`: xoá collection `gachaItems`, `gachaRolls`; `FieldValue.delete()` field `gachaPity` trên mọi user; xoá dòng `astriteTransactions` type `GACHA_SPEND`/`GACHA_REFUND`. Batch chunk 450. Chạy **sau** `seed:shop` (vì seed đọc `gachaItems`). ⚠️ Xoá dòng sổ cái gacha làm tổng sổ cái ≠ số dư ở các tài khoản đã từng quay — ghi rõ trong GUIDE/SHOP_PLAN.
8. GUIDE.md server (mục 5 endpoint, cây thư mục, data model, changelog) + SECURITY.md (mục chống mua trùng / giá do server quyết).

### A — Admin (`admin/`) — ✅ XONG 2026-11-15 (chưa deploy — phải deploy SAU server)

1. `types/index.ts`: `UnlockType` `GACHA`→`SHOP`; thay khối Gacha (L125–191) bằng `ShopItemType`, `ShopItem`, `ShopItemOwner(s)Result`, `AdminPurchase`; `AdminStats` → `shopPurchasesToday/Total`; `AdminAction` `SHOP_ITEM_*`; sửa comment `astrite`.
2. `api/gacha.api.ts` → **`api/shop.api.ts`** (theo pattern `topup.api.ts`).
3. **`components/ImageUploadField.tsx`** (mới — dùng ở FramesPage + ShopItemsPage, đủ điều kiện ≥2 trang): gói `Upload` + `uploadImage()` từ `FramesPage.tsx` L419–457.
4. **`pages/ShopItemsPage.tsx`** (thay `GachaItemsPage`): Tabs lọc Tất cả/Khung/Skin/Hiệu ứng; bảng Ảnh · Tên · Loại · **Giá ⭐** · refId · Mở bán (Switch) · thứ tự · thao tác (Sửa, Người sở hữu, Tặng, Xoá — `Popconfirm`). Modal tạo: chọn loại → FRAME chọn từ danh sách khung SHOP chưa niêm yết; SKIN/EFFECT nhập số id + ghi chú "id phải có sẵn trong APK".
5. **`pages/ShopHistoryPage.tsx`** (thay `GachaHistoryPage`): lọc uid/loại/ngày.
6. `FramesPage.tsx`: option & default `GACHA` → `SHOP` ("Bán tại Cửa hàng"), dùng `ImageUploadField`. `DashboardPage.tsx` L215–217 → "Lượt mua hôm nay/Tổng lượt mua". `LogsPage.tsx` L21–24 nhãn `SHOP_ITEM_*`.
7. `App.tsx` + `AdminLayout.tsx`: menu **nhóm "Cửa hàng"** (submenu) gồm `/shop` Vật phẩm · `/topup` Gói nạp · `/shop-history` Lịch sử mua · `/topup-history` Lịch sử nạp; xoá route `/gacha`, `/gacha-history`.
8. `npm run lint` + `npm run build` → GUIDE.md admin → deploy `npx firebase-tools deploy --only hosting:admin`.

### P — App Android (`Snapget/`) — ✅ XONG 2026-11-15 (21 unit test + assembleDebug + spotlessCheck sạch; chưa chạy trên máy — cần server mới + `seed:shop`)

> Lệch so với plan: tab mở mặc định là **Skins** (không phải Astrite); thẻ vật phẩm đã mua có nút **Apply** / **In use** ngay trong Shop; Appearance dùng dòng "Get more … in the Shop →" đầu mỗi tab thay vì nút trên từng ô khoá; popup nạp chuyển thành tab (`TopupComponents.kt`), không còn `TopupSheet.kt`.


1. **Network**: xoá `GachaApi.kt`, `GachaDtos.kt`, `provideGachaApi` (`NetworkModule.kt:102`); thêm `core/network/api/ShopApi.kt` + `dto/ShopDtos.kt` (`ShopCatalogDto{astrite, items}`, `ShopItemDto`, `BuyShopItemRequest`, `BuyShopItemResultDto`, `ShopPurchaseDto`).
2. **Xoá `feature/gacha/`** (Screen, ViewModel, ResultSheet, Layout, Rarity, GachaRepository, test `GachaLayoutTest`) + drawable `gacha_*` (giữ `ic_astrite`) + mục gacha trong `AllScreensPreview.kt`. **Chuyển** `TopupSheet.kt` (`PendingPaymentBanner`, `TopupSuccessDialog`) và `data/TopupRepository.kt` sang `feature/shop/`.
3. **`feature/shop/`**: `ShopScreen.kt`, `ShopViewModel.kt` (`@HiltViewModel`, state `StateFlow`: catalog, astrite, tab, dialog, topup/pending order — chuyển logic top-up từ `GachaViewModel` L42/L138–L237), `data/ShopRepository.kt` (`LoadStatus`, `serverMessage()`).
   - Top bar: "Shop" + `AstriteBar` (số dư + `+` → nhảy tab Astrite). **Không nút Back.**
   - Tab **Astrite**: thẻ gói nạp (Astrite + giá VNĐ) → tạo đơn → Chrome Custom Tab + poll khi `STARTED` (chuyển nguyên từ `GachaScreen.kt` L119–145) + `PendingPaymentBanner`/`TopupSuccessDialog`.
   - Tab **Skins/Frames/Effects**: `LazyVerticalGrid` 2 cột; preview: `imageUrl` → `AsyncImage`, không có thì SKIN vẽ `SkinThumbnail` từ `SkinRegistry`, EFFECT vẽ `EffectDemoCell` chạm thử được (tách từ `AppearanceScreen.kt` / `GachaResultSheet.kt` L448–470 ra `core/designsystem/component/` vì dùng ≥2 feature). **Ẩn item có refId không có trong registry** (APK cũ). Nút giá `⭐ 320` / "Owned"; thiếu Astrite → bấm thì gợi ý sang tab Astrite.
   - Dialog xác nhận "Buy X for N Astrite?" → `POST /shop/buy` → cập nhật số dư + `isOwned`; Skin/Effect → dialog **"Apply now?"** gọi `SettingsPreferences.setSkinId/setTouchEffectId`; Frame → toast.
   - Màu/shape qua `SkinTheme` (DESIGN.md), chuỗi UI tiếng Anh.
4. `Navigation.kt`: xoá `Screen.Gacha` (L68–69, L350) + import; `composable(Screen.Shop.route)` truyền ViewModel.
5. `DailyQuestScreen.kt`: xoá `GachaBanner` + tham số `onGachaClick` (L79–L220). `AppearanceScreen.kt`: item khoá → nút "Get in Shop" điều hướng tab Shop; đổi text trống ("roll the gacha" → "visit the Shop"). `EditMediaScreen.kt:533` text "Complete daily quests…" → "Get frames in the Shop!". Comment gacha còn sót (`SessionCleaner`, `SettingsPreferences`, registries).
6. `spotlessApply` → GUIDE.md app (route, tiến độ, changelog) + DESIGN.md (mục Shop).

### D — Tài liệu — ✅ XONG 2026-11-15

- Tạo **`Snapget/.claude/SHOP_PLAN.md`** (nội dung plan này + phần kinh tế: tân thủ 1600 = 1 effect hoặc 5 khung; free 90⭐/ngày ≈ 4 ngày/khung, 18 ngày/effect, 54 ngày/skin) — giữ nguyên mục PayOS (GACHA_PLAN §4, §12) chuyển sang.
- Xoá `GACHA_PLAN.md`; cập nhật tham chiếu trong `SKIN_PLAN.md`, 3 `CLAUDE.md` (server mục 1/6.1/14 — bỏ `gacha`, thêm `shop`, unlockType `SHOP`; app mục 9 câu "mở khóa qua gacha"), 3 `GUIDE.md`.
- `codegraph init` ở `server/`, `admin/`, `Snapget/`.

## Verification

- **Server**: `cd server; npm test -- --runInBand; npm run lint; npm run build`. `shop.service.spec.ts` phủ: mua OK (trừ đúng giá, đúng mảng & kiểu string/number, ghi purchase + ledger `SHOP_PURCHASE`), thiếu Astrite, đã sở hữu, item tắt/không tồn tại, frame không phải SHOP, niêm yết trùng (type, refId), sync khung SHOP không ghi đè giá. Swagger `/docs`: gọi `GET /shop/items` → `POST /shop/buy` 2 lần → lần 2 bị 409.
- **Script**: `npm run seed:shop` chạy 2 lần không nhân bản; `npm run cleanup:gacha` (dry-run) in đúng số lượng → `--confirm`.
- **Admin**: `npm run lint; npm run build`; tạo/sửa giá/tắt/xoá item, tạo khung SHOP → tự xuất hiện ở trang Vật phẩm; Dashboard hiện lượt mua.
- **App**: `.\gradlew.bat spotlessApply testDebugUnitTest assembleDebug`; chạy thật: bấm icon Shop ở footer → 4 tab; mua effect → "Apply now?" → hiệu ứng chạm đổi ngay; tab Astrite → `/topup/simulate` (dev) → số dư tăng; Appearance hiện đồ vừa mua; không còn đường nào vào gacha.

## Checklist user (vận hành, làm sau)
- Deploy server → chạy `seed:shop` → `cleanup:gacha --confirm` trên Firestore thật.
- Deploy admin (`npm run build && npx firebase-tools deploy --only hosting:admin`).
- Build APK mới (APK cũ gọi `/gacha/*` sẽ lỗi 404 — breaking change, đồng bộ cả 3 nơi).

---

## Phụ lục PayOS (giữ nguyên từ GACHA_PLAN — luồng nạp KHÔNG đổi)

### 0. Gói nạp (seed `npm run seed:topup`) — chép từ GACHA_PLAN §0.1

| # | Astrite | Giá | Ghi chú |
|---|---|---|---|
| 1 | 600 | 5.000đ | |
| 2 | 3.000 | 24.000đ | |
| 3 | 9.800 | 75.000đ | |
| 4 | 20.000 | 145.000đ | |
| 5 | **5.201.314** | **2.000đ** | **Gói TEST** — `isTest=true`. **Hiện công khai trong app** và **KHÔNG tắt** (user chốt 2026-08-05) — dùng chính gói này để nạp thật lúc demo. Admin tự tắt qua `isActive` khi nào thấy cần |


### A. Nạp tiền qua PayOS (TIỀN THẬT — production) — chép từ GACHA_PLAN §4

#### A.1 Luồng

```
App: GET  /topup/packages                        → danh sách gói
App: POST /topup/orders { packageId }            → server tạo orderCode + gọi PayOS
                                                 ← { orderCode, checkoutUrl }
App: mở checkoutUrl (Chrome Custom Tabs)
User thanh toán trên PayOS sandbox
PayOS → POST /topup/webhook  (@Public, verify chữ ký)
        server: PENDING → PAID, cộng Astrite, ghi astriteTransactions
App: GET /topup/orders/:orderCode (poll) hoặc quay lại qua returnUrl → refresh số dư
```

#### A.2 Bảo mật — bắt buộc, ghi vào `SECURITY.md`

| Rủi ro | Cách chặn |
|---|---|
| Webhook giả mạo | Verify chữ ký bằng `PAYOS_CHECKSUM_KEY`; sai chữ ký → 401, không xử lý |
| Webhook gọi trùng → cộng tiền nhiều lần | Doc id = `orderCode`; transaction chỉ cộng khi `status === 'PENDING'` |
| Client tự khai số tiền | **Không nhận `astrite`/`amount` từ client** — chỉ nhận `packageId`, tra giá từ `topupPackages` ở server |
| Client tự gọi endpoint cộng tiền | Không có endpoint nào cộng Astrite trực tiếp ngoài webhook + quest + signup |
| Rò key | `PAYOS_*` chỉ ở `.env`; thêm vào `.env.example` (giá trị rỗng), không log |
| Đơn treo | Job dọn `PENDING` quá hạn → `EXPIRED` |

#### A.3 Env mới

`PAYOS_CLIENT_ID` · `PAYOS_API_KEY` · `PAYOS_CHECKSUM_KEY` · `PAYOS_RETURN_URL` · `PAYOS_CANCEL_URL`
→ Thêm vào `.env.example` + `env.validation` (Joi) theo luật server CLAUDE.md mục 9.

#### A.4 Dependency — ✅ đã duyệt

Dùng **`@payos/node`** (user duyệt 2026-08-05). Khai báo trong `server/package.json`; ký và verify chữ ký webhook do thư viện lo.
Đây là **dependency mới duy nhất** của cả hai tính năng gacha + skin. Phía app và admin không thêm dep nào.

#### A.5 ⚠️ Tiền THẬT — ba hệ quả phải xử lý

Khác hẳn sandbox. Ba thứ dưới đây không phải "nên làm" mà là **bắt buộc**:

**(1) Hạn mức 100 giao dịch của gói FREE-100 (đã dùng 6, còn 94).**
Mỗi lần bấm nạp thành công tiêu 1 giao dịch — kể cả lúc test. Cách tiết kiệm:

| Giai đoạn | Cách làm | Tốn giao dịch? |
|---|---|---|
| Dev hằng ngày | `POST /topup/simulate` — **chỉ bật khi `NODE_ENV !== 'production'`**, mô phỏng đúng payload webhook PayOS | ❌ Không |
| Kiểm thử idempotent | Gọi `/topup/simulate` nhiều lần cùng `orderCode` | ❌ Không |
| Nghiệm thu cuối | Nạp thật gói **2.000đ**, 2–3 lần | ✅ 2–3 giao dịch |
| Demo bảo vệ | Nạp thật gói 2.000đ | ✅ 1 giao dịch |

→ Endpoint `simulate` ban đầu chỉ là phương án dự phòng, giờ thành **công cụ chính khi dev**. Toàn bộ code phía sau (đơn hàng, verify, idempotent, cộng Astrite, sổ cái) dùng chung một đường, nên test bằng simulate vẫn phủ đúng logic thật.

**(2) Gói test 5.201.314 Astrite giá 2.000đ là tiền thật — và GIỮ NGUYÊN, không tắt** (user chốt 2026-08-05).
Đây chính là gói dùng để nạp thật trong buổi demo: 2.000đ đổi lấy đủ Astrite quay thoải mái trước hội đồng.
- Đặt `isTest = true` để phân biệt trong trang admin
- Admin bật/tắt qua `isActive` bất cứ lúc nào, **không cần sửa code** — user tự quyết thời điểm

**(3) Không có "hoàn tác" khi cộng nhầm Astrite.**
Tiền vào tài khoản MB là thật. Lỗi idempotent (mục 4.2) giờ không chỉ là bug dữ liệu mà là chênh lệch tiền thật ↔ Astrite đã phát. Bài test bắt buộc trước khi bật production: gọi webhook **3 lần cùng `orderCode`** → số dư chỉ tăng **1 lần**, `astriteTransactions` chỉ có **1 dòng**.

---

### B. PayOS — hướng dẫn cấu hình từng bước — chép từ GACHA_PLAN §12

> ✅ **Bạn đã xong phần khó nhất**: tài khoản đã xác minh, kênh thanh toán **"Snapget"** đang hoạt động, ngân hàng MB đã liên kết, 3 khoá đã có sẵn.
> Chỉ còn **một việc chưa làm: điền Webhook url** — nhưng phải chờ server deploy xong mới điền được.

### Trạng thái hiện tại (theo ảnh dashboard 2026-08-05)

| Mục | Trạng thái |
|---|---|
| Tài khoản | ✅ Đã xác thực |
| Kênh thanh toán "Snapget" | ✅ Đang hoạt động |
| Client ID · Api Key · Checksum Key | ✅ Đã có |
| Ngân hàng MB | ✅ Đã liên kết |
| **Webhook url** | ❌ **Chưa điền** — việc duy nhất còn lại |
| Gói dịch vụ | FREE-100 · đã dùng **6/100 giao dịch** |

---

### Bước 1 — Bạn làm NGAY (không phải chờ tôi): điền `server/.env`

Mở `server/.env`, thêm 5 dòng. Ba giá trị đầu bấm nút copy 📋 cạnh mỗi ô trong dashboard:

```
PAYOS_CLIENT_ID=<Client ID>
PAYOS_API_KEY=<Api Key>
PAYOS_CHECKSUM_KEY=<Checksum Key>
PAYOS_RETURN_URL=https://datn-8810.onrender.com/api/topup/return
PAYOS_CANCEL_URL=https://datn-8810.onrender.com/api/topup/cancel
```

⚠️ **Nhớ tiền tố `/api`** — server đặt global prefix trong `main.ts`. Thiếu `/api` thì PayOS chuyển hướng về trang 404.

🔒 **ĐỪNG dán 3 khoá vào khung chat** — kể cả ảnh chụp màn hình đã bỏ che. Điền xong chỉ cần nhắn **"đã điền .env"**, tôi không cần biết giá trị. File `.env` đã nằm trong `.gitignore`.

⚠️ **Checksum Key có nút làm mới 🔄 ngay cạnh** — đừng bấm nhầm. Bấm là khoá cũ mất hiệu lực, phải điền lại `.env` và webhook đang chạy sẽ verify sai chữ ký.

### Bước 2 — Bạn làm: deploy server để có URL công khai

PayOS gửi webhook về server. `localhost` không nhận được, và lúc đăng ký webhook PayOS sẽ **gửi một request thử** — server phải đang chạy và trả về thành công thì mới lưu được URL.

| Cách | Ghi chú |
|---|---|
| **Render** (khuyến nghị) | Đã có `DEPLOY.md` ở root repo. URL cố định dạng `https://<tên>.onrender.com` — điền webhook 1 lần là xong |
| `ngrok http 3000` | URL đổi mỗi lần chạy lại → phải sửa webhook trên dashboard mỗi lần. Chỉ dùng khi bí |

Có URL rồi thì nhắn tôi — URL không phải bí mật, gửi thẳng trong chat được.

### Bước 3 — ✅ ĐÃ XONG (2026-08-05): code module `topup`

| Việc | Trạng thái |
|---|---|
| `@payos/node@2` trong `server/package.json` | ✅ |
| 5 biến `PAYOS_*` trong `.env.example` + `env.validation` (đều optional) | ✅ |
| Module `topup`: `topupPackages` · `topupOrders` · tạo link · **webhook idempotent** | ✅ 13 route |
| Seed 5 gói nạp (Phụ lục mục 0) — `npm run seed:topup` | ✅ |
| `POST /topup/simulate` chặn hẳn khi `NODE_ENV=production` | ✅ |
| Mục thanh toán trong `SECURITY.md` | ✅ mục 17 |
| Test webhook 3 lần cùng `orderCode` → số dư tăng đúng 1 lần | ✅ 17 unit test + 3 e2e |
| 2 trang admin (Gói nạp · Lịch sử nạp) | ✅ |
| App: popup gói nạp + nút `+` cạnh số Astrite (2026-11-15 → tab **Astrite** của Shop) | ✅ |

**Vì cả 3 khoá đều optional nên server chạy được ngay bây giờ dù `.env` còn trống** — chỉ riêng `POST /topup/orders` trả 503 kèm thông báo tiếng Việt. Điền khoá vào là luồng nạp sống dậy, **không cần sửa dòng code nào**.

### Bước 4 — Bạn làm: điền Webhook url trên dashboard

Sau khi tôi code xong và bạn deploy bản mới, quay lại **Kênh thanh toán → Thông tin kênh thanh toán**, điền ô **Webhook url**:

```
https://datn-8810.onrender.com/api/topup/webhook
```

⚠️ **Nhớ `/api`** — thiếu là PayOS gọi vào route không tồn tại và báo lưu thất bại.

Bấm lưu. PayOS gửi request thử ngay lúc đó:
- ✅ Lưu thành công → xong
- ❌ Báo lỗi → chụp màn hình gửi tôi. Thường là server chưa deploy bản mới, sai đường dẫn, hoặc server đang "ngủ" (Render gói free ngủ sau 15 phút không có request — mở URL server một lần cho nó thức rồi thử lại)

### Bước 5 — Cùng nhau: nghiệm thu bằng tiền thật

Dùng **gói test 2.000đ**, tốn 1 giao dịch trong hạn mức:

1. App → Shop → tab Astrite → chọn gói 2.000đ
2. Quét QR bằng app ngân hàng, chuyển 2.000đ thật
3. Kiểm tra đủ 4 điểm:
   - Số dư Astrite tăng đúng 5.201.314
   - `topupOrders/<orderCode>` chuyển `PENDING → PAID`
   - `astriteTransactions` có **đúng 1 dòng** type `TOPUP`
   - Dashboard PayOS ghi nhận giao dịch, tiền về tài khoản MB
4. Bấm nạp lần nữa với đơn mới → vẫn đúng (tốn thêm 1 giao dịch)

Trước bước này tôi đã test cạn logic bằng `/topup/simulate` rồi, nên nạp thật chỉ là xác nhận cuối.

---

### Tóm tắt — bạn cần đưa tôi những gì

| # | Thông tin | Khi nào | Cách đưa |
|---|---|---|---|
| 1 | Xác nhận **"đã điền .env"** | Làm được ngay | Nhắn trong chat |
| 2 | **URL server công khai** | Sau khi deploy | Nhắn thẳng — không phải bí mật |
| 3 | Xác nhận **đã lưu Webhook url** | Sau khi tôi code xong | Nhắn trong chat |
| 4 | Ảnh chụp lỗi (nếu có) | Khi vướng | Gửi ảnh |

**Ba khoá PayOS: tuyệt đối không gửi cho tôi.**

---

### Trước khi coi PayOS là XONG — checklist bắt buộc

**Phần code — tôi đã làm xong và có test chứng minh:**

- [x] Webhook verify chữ ký; sai chữ ký trả 401 và không xử lý
- [x] Gọi webhook 3 lần cùng `orderCode` → Astrite chỉ tăng 1 lần *(test "gọi 3 lần cùng orderCode…")*
- [x] Client không gửi được số tiền — server chỉ nhận `packageId`
- [x] `.env` không bị commit; `.env.example` chỉ có key rỗng
- [x] Không có dòng log nào in ra 3 khoá PayOS *(chỉ log `orderCode` + `error.message`)*
- [x] `/topup/simulate` **tắt** ở production *(403 khi `NODE_ENV=production`)*
- [x] Đơn `PENDING` quá hạn được dọn thành `EXPIRED` *(quét khi mở lịch sử nạp / trang admin)*
- [x] Đã ghi mục thanh toán vào `SECURITY.md` *(mục 17)*
- [x] Gói test 2.000đ **để nguyên đang bật** — dùng để nạp thật lúc demo (user tự tắt sau nếu muốn)

**Phần cấu hình — bạn làm, chưa xong:**

- [ ] Điền 3 khoá `PAYOS_*` + 2 URL vào `server/.env` (**bước 1**)
- [ ] Deploy lại server lên Render với các biến môi trường mới (**bước 2**)
- [ ] `NODE_ENV=production` trên Render — **thiếu biến này là `/topup/simulate` mở toang**, ai đăng nhập cũng tự cộng Astrite được
- [ ] Chạy `npm run seed:topup` một lần để tạo 5 gói nạp
- [ ] Điền Webhook url trên my.payos.vn (**bước 4**)
- [ ] Nghiệm thu bằng gói 2.000đ (**bước 5**)

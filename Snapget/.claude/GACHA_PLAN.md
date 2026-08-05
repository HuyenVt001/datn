# GACHA_PLAN.md — Kế hoạch hệ thống Gacha + tiền tệ Astrite

> **Trạng thái (cập nhật 2026-08-05): ✅ XONG TOÀN BỘ G0–G6 và P0–P5 về mặt CODE** — server, admin và app chạy được đầu-cuối, kể cả luồng nạp tiền.
> Còn lại **KHÔNG phải việc code**: (1) bạn điền 3 khoá `PAYOS_*` vào `server/.env` → deploy → đăng ký webhook (**mục 12**, 3 bước, ~15 phút); (2) cắm file thiết kế thật cho skin/hiệu ứng (P5 — app đang chạy bằng bảng màu + hình Canvas nên không chặn gì).
> Server **boot bình thường khi chưa có khoá PayOS** — chỉ riêng `POST /topup/orders` trả 503. Không cần sửa dòng code nào khi điền khoá.
> Nguồn spec: `Snapget - Hệ thống gacha và tiền tệ.xlsx`, sheet **"Gacha thần thánh"**.
> **Đọc kèm `.claude/SKIN_PLAN.md`** — hai tính năng gắn chặt: skin giao diện và hiệu ứng touch **chính là vật phẩm SSR/SR của gacha**. Làm song song theo lộ trình gộp ở mục 9.
> Khi thi công: cập nhật `GUIDE.md` (app + server + admin) sau mỗi phase, `SECURITY.md` cho phần thanh toán, chạy `codegraph init`.

---

## 0. Quyết định đã chốt

| Hạng mục | Chốt |
|---|---|
| Tiền tệ | **Astrite** — icon 360×360 đã có trong file Excel |
| Cổng thanh toán | **PayOS — TIỀN THẬT (production)**, thư viện **`@payos/node`** (chốt 2026-08-05). User đã có tài khoản xác minh, kênh thanh toán **"Snapget"** đang hoạt động, liên kết ngân hàng MB |
| ⚠️ Hạn mức PayOS | Gói **FREE-100 = 100 giao dịch**, đã dùng 6. **Mỗi lần test nạp thật tiêu 1 giao dịch** → phải tiết kiệm, xem mục 4.5 |
| Thứ tự làm PayOS | **LÀM CUỐI CÙNG** (chốt 2026-08-05) — xem mục 9 (lộ trình) và mục 12 (hướng dẫn từng bước cho user) |
| Nguồn Astrite khi chưa có PayOS | **Sửa thẳng `users/{uid}.astrite` trên Firebase Console** (user chốt 2026-08-05) — không cần dựng chức năng nạp tạm nào. Lưu ý: sửa tay không sinh dòng trong `astriteTransactions`, nên sổ cái sẽ lệch số dư ở các tài khoản test — chấp nhận được, chỉ là dữ liệu dev |
| Giá quay | **160 Astrite/lần** · **1440 Astrite/10 lần** (giảm 10%) |
| Số bậc | **4 bậc**: N · R · SR · SSR |
| Loại vật phẩm | Khung ảnh = **R** · Hiệu ứng touch = **SR** · Skin giao diện = **SSR** |
| Bậc N (95%) | Trả **Astrite ngẫu nhiên 1–60** (chốt 2026-08-05 — lấp chỗ trống 95% trong sheet gốc) |
| Pity | R **10** · SR **50** · SSR **100** — trúng bậc nào **chỉ reset bộ đếm bậc đó** |
| Trùng R | Được phép trùng bất cứ lúc nào → hoàn **160 Astrite** |
| Trùng SR | **Chỉ khi đã full SR** → hoàn **1000 Astrite** |
| Trùng SSR | **Chỉ khi đã full SSR** → hoàn **2000 Astrite** |
| Thưởng tân thủ | **1600 Astrite** một lần khi tạo tài khoản (= 10 lần quay lẻ, chạm pity R) |
| Daily quest | Hoàn thành 2/2 quest/ngày → **60 Astrite** (THAY cho mở khoá khung ngẫu nhiên) |
| Khung ảnh | **Bỏ `QUEST_RANDOM`**, thêm `GACHA`; giữ nguyên `STREAK_MILESTONE` / `POST_COUNT` / `FRIEND_COUNT` / `COOP_FIRST` / `DEFAULT` |
| Lịch sử | Lưu **cả lịch sử nạp và lịch sử quay**; user xem của mình, admin xem toàn hệ thống |
| Banner | **1 banner cố định, HARDCODE trong APK** (`res/drawable/gacha_banner`) — user chốt 2026-08-05: thay ảnh trong source nhanh hơn dựng trang admin. Bỏ `GachaBannerPage`, bỏ doc `config/gachaBanner`, bỏ endpoint `/gacha/banner` |
| Quy mô pool bản đầu | 2 skin (SSR) · 5 hiệu ứng (SR) · 4 khung ảnh (R — hiện có; **user tự thêm sau qua trang admin**, dùng `FramesPage` đã có sẵn) |
| Mốc streak 3/7/14/30 | **Giữ nguyên thưởng khung ảnh**, KHÔNG thưởng Astrite (chốt 2026-08-05) |

### 0.1 Gói nạp

| # | Astrite | Giá | Ghi chú |
|---|---|---|---|
| 1 | 600 | 5.000đ | |
| 2 | 3.000 | 24.000đ | |
| 3 | 9.800 | 75.000đ | |
| 4 | 20.000 | 145.000đ | |
| 5 | **5.201.314** | **2.000đ** | **Gói TEST** — `isTest=true`. **Hiện công khai trong app** và **KHÔNG tắt** (user chốt 2026-08-05) — dùng chính gói này để nạp thật lúc demo. Admin tự tắt qua `isActive` khi nào thấy cần |

### 0.2 Màu phẩm chất

✅ **Chốt toàn bộ 2026-08-05.**

| Bậc | Màu | Hex | Vật phẩm |
|---|---|---|---|
| N | Xám | `#9E9E9E` | Astrite 1–60 |
| R | Xanh dương | `#4FC3F7` | Khung ảnh |
| SR | Tím | `#B388FF` | Hiệu ứng touch |
| **SSR** | **Cam** | `#FFA726` | Skin giao diện |

Dùng cho: viền thẻ kết quả quay · chữ tên vật phẩm · nhãn phẩm chất ở trang admin và lịch sử.
Bốn màu này **KHÔNG nằm trong `SkinColors`** — chúng là màu hệ thống của gacha, giữ nguyên ở mọi skin để người dùng luôn nhận ra bậc hiếm.

---

## 1. Kinh tế — kiểm tra bằng số (làm trước khi code, không phải sau)

### 1.1 Tỉ lệ danh nghĩa ≠ tỉ lệ thực tế

Sheet ghi R 4% · SR 0,9% · SSR 0,1%. **Nhưng pity kéo tỉ lệ thực lên đáng kể.** Số lần quay trung bình để ra 1 vật phẩm bậc X, với hard pity tại `n`:

`E = Σ(k=0..n-1) (1-p)^k`

| Bậc | Tỉ lệ gốc | Pity | Trung bình mỗi | **Tỉ lệ THỰC** |
|---|---|---|---|---|
| R | 4% | 10 | 8,4 lần | **≈ 11,9%** |
| SR | 0,9% | 50 | 40,4 lần | **≈ 2,5%** |
| SSR | 0,1% | 100 | 95,2 lần | **≈ 1,05%** |
| **N** | — | — | — | **≈ 84,5%** |

→ Khi viết popup "Rule gacha" phải ghi **tỉ lệ gốc** (đúng chuẩn công bố của game), nhưng lúc bảo vệ DATN nên biết con số thực này. Đây cũng là lý do bậc N thực tế là **84,5%** chứ không phải 95%.

### 1.2 Giá trị kỳ vọng mỗi lần quay

| Nguồn hoàn lại | Tính | Astrite |
|---|---|---|
| Bậc N | 84,5% × trung bình 30,5 | **25,8** |
| Trùng R (khi đã full khung) | 11,9% × 160 | 19,0 |
| Trùng SR (khi đã full 5 hiệu ứng) | 2,5% × 1000 | 24,8 |
| Trùng SSR (khi đã full 2 skin) | 1,05% × 2000 | 21,0 |
| **Tổng khi đã sưu tầm đủ** | | **≈ 90,6 / 160 = hoàn 57%** |

Nghĩa là người chơi đã full bộ vẫn mất ~43% mỗi lần quay — hợp lý, không tạo vòng lặp vô hạn.

### 1.3 Chi phí sưu tầm đủ bộ

| Mốc | Số lần quay | Astrite (dùng x10) |
|---|---|---|
| Đủ 2 skin (SSR) | ~190 | ~27.400 |
| Đủ 5 hiệu ứng (SR) | ~202 | ~29.100 |
| **Đủ cả hai** | **~202** | **~30.240** |

- **Nạp**: 20.000 + 9.800 + 3.000 = 32.800 Astrite ≈ **244.000đ** (chưa trừ Astrite thu lại từ bậc N — thực tế còn ~1,5 gói)
- **Miễn phí**: 1600 (tân thủ) + 60/ngày → cần **~480 ngày**

⚠️ **Đây là điểm cần bạn cân nhắc**: chơi miễn phí gần như không thể chạm tới SSR. Nếu muốn dịu hơn thì tăng thưởng daily hoặc thêm mốc streak thưởng Astrite (câu hỏi mục 11).

---

## 2. Mô hình dữ liệu (Firestore — theo luật server CLAUDE.md mục 6)

### 2.1 Bổ sung vào `users/{uid}`

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `astrite` | `number` | Số dư hiện tại. Mặc định 0, cộng 1600 khi tạo tài khoản |
| `unlockedSkins` | `number[]` | skinId đã sở hữu (skin 0 mặc định KHÔNG nằm trong đây) |
| `unlockedEffects` | `number[]` | effectId đã sở hữu (effect 0 mặc định KHÔNG nằm trong đây) |
| `gachaPity` | `{ R: number, SR: number, SSR: number }` | Bộ đếm bảo hiểm từng bậc |
| `signupBonusClaimed` | `boolean` | Chống cộng 1600 nhiều lần |

`unlockedFrames` giữ nguyên `string[]` như hiện tại.

### 2.2 Collection mới

| Collection | Field chính | Ghi chú |
|---|---|---|
| `gachaItems/{itemId}` | `itemName`, `itemType` (FRAME/EFFECT/SKIN), `rarity` (R/SR/SSR), `imageUrl`, `refId`, `isActive`, `sortOrder` | Catalog admin quản lý. `refId` trỏ tới vật phẩm thật: `frameId` (string) hoặc `skinId`/`effectId` (number) |
| `topupPackages/{id}` | `name`, `astrite`, `priceVnd`, `isActive`, `isTest`, `sortOrder` | Admin CRUD |
| `topupOrders/{orderCode}` | `uid`, `packageId`, `astrite`, `amountVnd`, `status` (PENDING/PAID/CANCELLED/EXPIRED), `payosPaymentLinkId`, `checkoutUrl`, `createdAt`, `paidAt` | `orderCode` = số nguyên PayOS yêu cầu; **doc id = orderCode** để webhook idempotent |
| `astriteTransactions/{id}` | `uid`, `type` (SIGNUP_BONUS/QUEST_REWARD/TOPUP/GACHA_SPEND/GACHA_REFUND/ADMIN_ADJUST), `amount` (+/−), `balanceAfter`, `refId`, `createdAt` | Sổ cái. **Mọi** thay đổi số dư phải ghi 1 dòng |
| `gachaRolls/{id}` | `uid`, `rollType` (SINGLE/TEN), `cost`, `results[]`, `refundTotal`, `createdAt` | 1 doc = 1 lần bấm nút (x10 vẫn là 1 doc, 10 phần tử trong `results`) |
| ~~`config/gachaBanner`~~ | — | ❌ **Bỏ** (2026-08-05): banner hardcode trong APK |

`results[]` mỗi phần tử: `{ tier, itemType, itemId?, refId?, astriteAmount?, isDuplicate, refundAstrite }`.

### 2.3 Vì sao skin/hiệu ứng vẫn cần `gachaItems` dù asset nằm trong APK

Sheet yêu cầu admin quản lý "tên vật phẩm — loại — phẩm chất — ảnh đại diện". Server giữ **metadata + ảnh đại diện** (để hiện trong kết quả quay, lịch sử, trang admin); **asset thật** (icon, nút, hiệu ứng) vẫn nằm trong APK và map theo `refId`. Admin **không tạo được skin/hiệu ứng mới** — chỉ sửa tên/ảnh/bật-tắt của những id đã có trong app. Ghi rõ điều này ngay trên trang admin để tránh hiểu nhầm.

---

## 3. Thuật toán quay (server — chạy trong 1 transaction)

```
POST /gacha/roll { times: 1 | 10 }

cost = times == 1 ? 160 : 1440
transaction:
  user = get(users/uid)
  if user.astrite < cost -> 400 "Không đủ Astrite"
  balance = user.astrite - cost
  pity = user.gachaPity
  owned = { frames: Set, effects: Set, skins: Set }
  results = []

  repeat times:
    pity.R++; pity.SR++; pity.SSR++

    # 1) Bảo hiểm ưu tiên bậc cao trước
    if   pity.SSR >= 100 -> tier = SSR
    elif pity.SR  >= 50  -> tier = SR
    elif pity.R   >= 10  -> tier = R
    else:
      r = random()                      # [0,1)
      if   r < 0.001            -> SSR
      elif r < 0.010            -> SR   # 0.001 + 0.009
      elif r < 0.050            -> R    # + 0.04
      else                      -> N

    pity[tier] = 0                       # CHỈ reset bậc vừa trúng (đúng spec)

    switch tier:
      N:   amount = randInt(1, 60); balance += amount
      R:   item = randomActive(FRAME)               # được phép trùng
           if owned.frames.has(item.refId):
              balance += 160; isDuplicate = true
           else: owned.frames.add(item.refId)
      SR:  pool = activeUnowned(EFFECT, owned.effects)
           if pool empty:  item = randomActive(EFFECT); balance += 1000; isDuplicate = true
           else:           item = random(pool); owned.effects.add(item.refId)
      SSR: pool = activeUnowned(SKIN, owned.skins)
           if pool empty:  item = randomActive(SKIN);  balance += 2000; isDuplicate = true
           else:           item = random(pool); owned.skins.add(item.refId)

    results.push(...)

  ghi users: astrite=balance, gachaPity=pity, unlockedFrames/Skins/Effects
  ghi gachaRolls (1 doc)
  ghi astriteTransactions: 1 dòng GACHA_SPEND (−cost) + 1 dòng GACHA_REFUND (+tổng hoàn) nếu > 0
```

**Bốn điểm dễ sai, phải làm đúng:**

1. **`owned` phải cập nhật NGAY trong vòng lặp** — quay x10 mà đọc trạng thái sở hữu 1 lần từ đầu thì lần 3 và lần 7 cùng ra một skin "chưa sở hữu".
2. **Chỉ reset pity của bậc trúng** (đúng spec). Hệ quả: trúng SSR ở lần 100 không reset pity R, nên nếu pity R lúc đó đã ≥10 thì lần quay kế tiếp bị ép ra R. Tự điều chỉnh, không phải bug.
3. **Toàn bộ nằm trong 1 transaction Firestore** — bấm x10 hai lần liên tiếp không được trừ tiền hai lần trên cùng số dư cũ.
4. **Random ở server, không bao giờ ở client.**

---

## 4. Nạp tiền qua PayOS (TIỀN THẬT — production)

### 4.1 Luồng

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

### 4.2 Bảo mật — bắt buộc, ghi vào `SECURITY.md`

| Rủi ro | Cách chặn |
|---|---|
| Webhook giả mạo | Verify chữ ký bằng `PAYOS_CHECKSUM_KEY`; sai chữ ký → 401, không xử lý |
| Webhook gọi trùng → cộng tiền nhiều lần | Doc id = `orderCode`; transaction chỉ cộng khi `status === 'PENDING'` |
| Client tự khai số tiền | **Không nhận `astrite`/`amount` từ client** — chỉ nhận `packageId`, tra giá từ `topupPackages` ở server |
| Client tự gọi endpoint cộng tiền | Không có endpoint nào cộng Astrite trực tiếp ngoài webhook + quest + signup |
| Rò key | `PAYOS_*` chỉ ở `.env`; thêm vào `.env.example` (giá trị rỗng), không log |
| Đơn treo | Job dọn `PENDING` quá hạn → `EXPIRED` |

### 4.3 Env mới

`PAYOS_CLIENT_ID` · `PAYOS_API_KEY` · `PAYOS_CHECKSUM_KEY` · `PAYOS_RETURN_URL` · `PAYOS_CANCEL_URL`
→ Thêm vào `.env.example` + `env.validation` (Joi) theo luật server CLAUDE.md mục 9.

### 4.4 Dependency — ✅ đã duyệt

Dùng **`@payos/node`** (user duyệt 2026-08-05). Khai báo trong `server/package.json`; ký và verify chữ ký webhook do thư viện lo.
Đây là **dependency mới duy nhất** của cả hai tính năng gacha + skin. Phía app và admin không thêm dep nào.

### 4.5 ⚠️ Tiền THẬT — ba hệ quả phải xử lý

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

## 5. Danh sách endpoint

### 5.1 App (Firebase ID token)

| Method | Path | Trả về |
|---|---|---|
| GET | `/gacha/state` | `{ astrite, pity{R,SR,SSR}, pityLimit, costSingle, costTen, rates, refunds }` (không có banner — banner hardcode trong APK) |
| POST | `/gacha/roll` | `{ results[], astriteAfter }` — body `{ times: 1\|10 }` |
| GET | `/gacha/items` | Catalog + `isOwned` của mình |
| GET | `/gacha/history` | Lịch sử quay của mình (phân trang) |
| GET | `/topup/packages` | Gói nạp đang bật |
| POST | `/topup/orders` | `{ orderCode, checkoutUrl }` — body `{ packageId }` |
| GET | `/topup/orders/:orderCode` | Trạng thái đơn (app poll) |
| GET | `/topup/history` | Lịch sử nạp của mình |
| GET | `/users/me` | **Bổ sung** `astrite`, `unlockedSkins`, `unlockedEffects` |

### 5.2 Public

| POST | `/topup/webhook` | PayOS gọi. `@Public()` + verify chữ ký |

### 5.3 Admin (JWT server)

| Method | Path |
|---|---|
| GET/POST/PATCH/DELETE | `/topup/packages` (+`/admin`) |
| GET/POST/PATCH/DELETE | `/gacha/items` (+`/admin`) |
| GET | `/gacha/history/admin` — toàn hệ thống, lọc theo uid/bậc/ngày |
| GET | `/topup/history/admin` — + thống kê doanh thu theo ngày |

Mọi hành động admin ghi `AuditService` như các module hiện có.

---

## 6. App — màn hình

### 6.1 Trang Daily gộp (sửa `feature/quest/DailyQuestScreen.kt`)

```
┌─ Daily ─────────────────────┐
│ ┌─────────────────────────┐ │
│ │   BANNER GACHA (16:9)   │ │ ← bấm → GachaScreen
│ └─────────────────────────┘ │
│  Daily Quest                │
│  ☑ Login              +60⭐ │
│  ☐ Post a moment            │
│  Streak: 🔥 7 days          │
└─────────────────────────────┘
```

- Banner **ở trên cùng**, ảnh hardcode `R.drawable.gacha_banner` (nguồn: `Sources/skin-assets/gacha/gacha_banner.png`)
- Phần **"Frame collection"** trong màn này **bỏ đi** — đã chuyển sang màn Appearance tab Frames (SKIN_PLAN mục 4.2)
- `RewardBanner` khung → đổi thành thông báo **+60 Astrite**

### 6.2 `GachaScreen` (mới) — đúng mô tả trong sheet

```
┌───────────────────────────────────┐
│ ←            [⭐ 1,600  +]     [?] │
│                                   │
│         (background gacha)        │
│                                   │
│    [ Quay x1  160 ]  [ Quay x10 ] │
│                        1440       │
└───────────────────────────────────┘
```

| Thành phần | Chi tiết |
|---|---|
| Nút Close | Góc **trên trái**, icon mũi tên trái (`skin<N>_ic_back`) |
| Ô Astrite | Số dư + icon Astrite + dấu **+** → mở popup gói nạp. **Nút `+` ẩn cho tới khi xong G6** |
| **Pity SSR** | Hiện dạng `SSR 63/100` ngay dưới ô Astrite. **Chỉ hiện bậc SSR** — pity R và SR ẩn hoàn toàn (chốt 2026-08-05) |
| Nút **?** | Mở popup rule gacha (tỉ lệ gốc, pity, quy tắc hoàn) |
| Nút x1 / x10 | Hiện giá kèm theo; disable + đổi màu khi không đủ Astrite |
| Background | `gacha_bg` |

**Đây là màn DUY NHẤT hiện số dư Astrite** (chốt 2026-08-05) — không hiện ở trang Daily, không hiện ở top bar feed.

### 6.3 Popup gói nạp
Danh sách gói từ `/topup/packages`, mỗi hàng: Astrite + giá + nút Buy → tạo đơn → mở Custom Tab.

### 6.4 Popup rule gacha
Tỉ lệ **gốc** (N 95% · R 4% · SR 0,9% · SSR 0,1%), pity 10/50/100, quy tắc hoàn 160/1000/2000, giải thích SR/SSR luôn ra đồ chưa sở hữu.

### 6.5 Màn kết quả quay (chốt 2026-08-05)

- **x1**: 1 thẻ
- **x10**: lưới 5×2, các ô **lật lần lượt** (~250ms mỗi ô) — có nút **Skip** để hiện thẳng cả lưới
- Thẻ tô theo màu phẩm chất (mục 0.2); SSR có hiệu ứng nổi bật nhất
- Vật phẩm trùng: nhãn `Duplicate +N ⭐`
- Bậc N: thẻ xám hiện thẳng số Astrite nhận được
- Animation **Compose thuần**, không thêm dependency (thống nhất với SKIN_PLAN)

### 6.6 Lịch sử
Tab "Rolls" + tab "Top-ups", vào từ nút ở `GachaScreen`.

### 6.7 Số dư Astrite — CHỈ hiện ở màn Gacha (chốt 2026-08-05)

Không hiện ở trang Daily, không hiện ở top bar feed — giữ feed sạch đúng tinh thần DESIGN.md.

Hệ quả cần lưu ý khi làm: hoàn thành daily quest được +60 Astrite nhưng **người dùng không thấy số dư ngay tại trang Daily**. Bù lại bằng dòng thông báo "**+60 ⭐ Astrite**" ngay tại mục quest vừa hoàn thành (thay chỗ `RewardBanner` khung ảnh cũ), để vẫn có phản hồi rõ ràng.

---

## 7. Admin — trang mới

| Trang | Nội dung |
|---|---|
| `TopupPackagesPage` | CRUD gói nạp (tên, Astrite, giá, bật/tắt, cờ test, thứ tự) |
| `GachaItemsPage` | CRUD vật phẩm: tên · loại · phẩm chất · ảnh đại diện (upload qua `/upload`) · `refId` · bật/tắt. **Skin/hiệu ứng: chỉ sửa, không tạo mới** |
| `GachaHistoryPage` | Lịch sử quay toàn hệ thống, lọc theo user/bậc/ngày |
| `TopupHistoryPage` | Lịch sử nạp + thống kê doanh thu. **Không cần xuất CSV / hoá đơn** (chốt 2026-08-05) |
| `FramesPage` *(đã có)* | Dùng luôn để **bổ sung khung ảnh mới** — chỉ cần bổ sung lựa chọn `unlockType = GACHA` vào form |
| `DashboardPage` | **Bổ sung** ô doanh thu + số lượt quay |
| `UsersPage` | **Bổ sung** cột số dư Astrite |

---

## 8. Sửa những thứ đang chạy

| File | Sửa gì |
|---|---|
| `server/src/quests/quests.service.ts` | `maybeGiveDailyReward` → cộng **60 Astrite** thay vì mở khung; xoá `pickRandomLockedFrame` + phụ thuộc `FramesService`/`FramesRepository` cho nhánh này |
| `server/src/quests/quests.repository.ts` | `setDailyRewardFrame` → `setDailyRewardAstrite` (giữ cơ chế claim idempotent theo ngày) |
| `server/src/frames/entities/frame.entity.ts` | `UNLOCK_TYPES`: bỏ `QUEST_RANDOM`, thêm `GACHA` |
| `server/src/frames/frames.service.ts` | Bỏ nhánh xử lý `QUEST_RANDOM` |
| `server/src/users/*` | Tạo user mới → `astrite = 1600`, `signupBonusClaimed = true`, ghi `astriteTransactions` |
| `server/src/common/constants.ts` | Thêm `GACHA_COST_SINGLE=160`, `GACHA_COST_TEN=1440`, `PITY_R=10`, `PITY_SR=50`, `PITY_SSR=100`, `REFUND_R=160`, `REFUND_SR=1000`, `REFUND_SSR=2000`, `QUEST_DAILY_ASTRITE=60`, `SIGNUP_BONUS_ASTRITE=1600`, `N_ASTRITE_MIN=1`, `N_ASTRITE_MAX=60` |
| `Snapget/…/DailyQuestScreen.kt` | Thêm banner; bỏ lưới "Frame collection"; đổi RewardBanner sang Astrite |
| `Snapget/…/QuestDtos.kt` | `rewardFrameId` → `rewardAstrite` |
| `admin/src/pages/` | 5 trang mới (mục 7) |

Đổi contract → theo luật CLAUDE.md mục 3, phải đồng bộ đủ **app + server + admin** và ghi vào cả 3 `GUIDE.md`.

---

## 9. Lộ trình gộp với SKIN_PLAN

Hai tính năng làm song song. Server (G) và app-skin (P) chạy được độc lập tới gần cuối.

**PayOS đẩy xuống cuối cùng** (user chốt 2026-08-05). Suốt G0–G5 không cần luồng nạp: muốn test thì **sửa thẳng `users/{uid}.astrite` trên Firebase Console**.

| # | Phase | Thuộc | Nội dung | Phụ thuộc |
|---|---|---|---|---|
| 1 | ✅ **G0** | server | `astrite` trên user + `astriteTransactions` + thưởng tân thủ 1600 | — |
| 2 | ✅ **P0–P2** | app | Gom token màu + skin engine + refactor (SKIN_PLAN) — **gỡ luôn giao diện Light** (mục 4.4, vốn xếp ở P3) vì `AppTheme` đã đổi sang nhận `skin` thay `themeMode` | — |
| 3 | ✅ **G1** | server | `gachaItems` + `/gacha/roll` + pity + `gachaRolls` + `/gacha/state` + `/gacha/history` + unit test tỉ lệ | G0 |
| 4 | ✅ **G2** | server | Sửa daily quest → 60 Astrite; frames bỏ `QUEST_RANDOM` thêm `GACHA` (đã sync app + admin vì đổi contract `rewardFrameId` → `rewardAstrite`) | G1 |
| 5 | ✅ **G3** | admin | `GachaItemsPage` · `GachaHistoryPage` · cột Astrite ở `UsersPage` · Dashboard (bỏ `GachaBannerPage` — banner hardcode) | G1 |
| 6 | ✅ **P3** | app | Màn Appearance 3 tab (phần gỡ Light đã làm xong ở P2) | P2 |
| 7 | ✅ **P4** | app | Engine hiệu ứng touch (SKIN_PLAN) | P3 |
| 8 | ✅ **G4** | app | `GachaScreen` + popup rule + màn kết quả + trang Daily gộp banner. **Nút `+` cạnh số Astrite tạm ẩn** (chưa có nạp) | G1, P3 |
| 9 | ⚠️ **P5** | app | Skin 1/2 đã có **bảng màu thật**, 5 hiệu ứng vẽ bằng Canvas — còn lại là **cắm file thiết kế**: icon vector, nút chụp, thumbnail, PNG hạt. Chờ asset của bạn | P4 + asset |
| 10 | ✅ **G5** | app | Khoá skin/hiệu ứng theo `unlockedSkins`/`unlockedEffects`; nối kết quả quay → mở khoá thật | G4, P5 |
| 11 | ✅ **G6** | server + admin + app | **PayOS tiền thật**: `topupPackages` · `topupOrders` (doc id = `orderCode`) · tạo đơn · **webhook verify chữ ký + idempotent** · `/topup/simulate` cho dev · `SECURITY.md` mục 17 · `TopupPackagesPage` + `TopupHistoryPage` · popup gói nạp + **nút `+` đã bật lại** trong app. ⏳ Chờ bạn: điền `.env` → deploy → đăng ký webhook (mục 12) | G5 |

**Điểm cắt demo được:**
- Xong **G3** → demo trọn vẹn gacha qua Swagger + trang admin
- Xong **G4** → demo trọn vẹn trên app; Astrite đến từ tân thủ 1600 + daily 60 (+ sửa tay trên Firebase khi test)
- Xong **G5** → skin/hiệu ứng khoá đúng theo sở hữu — **đây đã là bản hoàn chỉnh về mặt tính năng**
- Xong **G6** → có thêm luồng nạp tiền thật qua PayOS

⚠️ **Code G6 đã xong, chỉ còn cấu hình.** Nếu sát deadline mà chưa deploy/đăng ký webhook kịp, app vẫn bảo vệ bình thường: nút `+` mở popup gói nạp, bấm mua thì báo "Chức năng nạp Astrite chưa được cấu hình trên server" — không màn nào vỡ. Muốn giấu hẳn luồng nạp lúc demo thì vào trang admin **Gói nạp** tắt hết `isActive`, popup sẽ hiện "No top-up packages available right now.".

---

## 10. Rủi ro

1. **Webhook PayOS không gọi được `localhost`** — bắt buộc deploy server lên Render (đã có `DEPLOY.md`) trước khi đăng ký webhook. Lưu ý Render gói free **ngủ sau 15 phút** không có request → lúc PayOS gửi request thử có thể timeout; mở URL server cho thức rồi thử lại.
2. ~~Dependency chưa duyệt~~ → ✅ đã duyệt `@payos/node`.
3. **Cộng tiền trùng** nếu webhook idempotent làm sai — lỗi nghiêm trọng nhất của cả tính năng, và **giờ là tiền thật**. Bắt buộc test: gọi webhook 3 lần cùng `orderCode`, số dư chỉ tăng 1 lần.
3b. **Cạn hạn mức FREE-100** nếu test bằng nạp thật (mục 4.5) — dùng `/topup/simulate` khi dev, chỉ nạp thật lúc nghiệm thu và demo.
3c. **Bấm nhầm nút làm mới Checksum Key** trên dashboard → khoá cũ mất hiệu lực, webhook đang chạy verify sai chữ ký. Nút này nằm ngay cạnh nút copy.
4. **Quay x10 không cập nhật `owned` trong vòng lặp** → phát trùng skin (mục 3, điểm 1).
5. ~~Pool quá nhỏ~~ → **đã chấp nhận** (user chốt 2026-08-05): 2 SSR + 5 SR + 4 khung R. User tự bổ sung khung qua trang admin khi cần, không chặn tiến độ.
6. **Chơi miễn phí ~480 ngày mới đủ bộ** (mục 1.3) — **đã chấp nhận**, không thêm nguồn Astrite miễn phí nào ngoài daily 60 + tân thủ 1600. Đây là lựa chọn có chủ ý; nêu rõ khi bảo vệ nếu bị hỏi về cân bằng kinh tế.
7. **Random ở client** — tuyệt đối không. Client chỉ hiển thị kết quả server trả.
8. **TIỀN THẬT ngay từ đầu** (không có sandbox) — mọi lỗi cộng nhầm Astrite đều đối ứng với tiền thật đã vào tài khoản MB. Rà đủ checklist cuối mục 12 trước khi coi là xong.
9. ~~Gói test phải tắt~~ → **user chốt giữ nguyên** để nạp thật lúc demo; admin tự tắt qua `isActive` khi cần.

---

## 11. Câu hỏi còn mở

✅ **TOÀN BỘ ĐÃ CHỐT 2026-08-05 — không còn câu hỏi nào chặn việc bắt đầu.**

| # | Câu hỏi | Chốt |
|---|---|---|
| 1 | Màu 4 bậc | N xám `#9E9E9E` · R xanh dương `#4FC3F7` · SR tím `#B388FF` · SSR cam `#FFA726` (mục 0.2) |
| 2 | Thư viện PayOS | **`@payos/node`** |
| 3 | Số dư Astrite hiện ở đâu | **CHỈ màn Gacha.** Không hiện ở trang Daily, không hiện ở top bar feed (mục 6.7) |
| 4 | Hiện pity cho user | **Chỉ hiện pity SSR** (`63/100`). Pity R và SR ẩn (mục 6.2) |
| 5 | Hiển thị kết quả x10 | **Lật lần lượt từng ô + nút Skip** (mục 6.5) |
| 6 | Gói test 5.201.314 Astrite | **Hiện công khai**, gắn cờ `isTest`; admin tắt được mà không phải sửa code |
| 7 | SR/SSR có trùng khi chưa full | **KHÔNG bao giờ** — đúng như sheet. Chỉ trùng khi đã sở hữu hết bậc đó (thuật toán mục 3 đã đúng) |
| 8 | Pool khung ảnh | Hiện có **8 khung** (4 quest → chuyển sang gacha, 4 mốc streak giữ nguyên). **User tự bổ sung thêm khung sau qua trang admin** — không chặn gì |
| 9 | Mốc streak thưởng Astrite | **KHÔNG** — giữ nguyên thưởng khung 3/7/14/30 như hiện tại. Nguồn Astrite miễn phí chỉ có daily 60 + tân thủ 1600 |
| 10 | Admin cộng Astrite thủ công | **KHÔNG làm.** Test thì sửa thẳng `users/{uid}.astrite` trên Firebase Console. Vẫn giữ type `ADMIN_ADJUST` trong sổ cái phòng sau này cần |
| 11 | Hoá đơn / xuất file đối soát | **KHÔNG cần** — chỉ xem lịch sử nạp trên trang admin |

---

## 12. PayOS — hướng dẫn từng bước (phase G6)

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
| Seed 5 gói nạp (mục 0.1) — `npm run seed:topup` | ✅ |
| `POST /topup/simulate` chặn hẳn khi `NODE_ENV=production` | ✅ |
| Mục thanh toán trong `SECURITY.md` | ✅ mục 17 |
| Test webhook 3 lần cùng `orderCode` → số dư tăng đúng 1 lần | ✅ 17 unit test + 3 e2e |
| 2 trang admin (Gói nạp · Lịch sử nạp) | ✅ |
| App: popup gói nạp + nút `+` cạnh số Astrite | ✅ |

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

1. App → GachaScreen → `+` → chọn gói 2.000đ
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

# MODERATION_PLAN.md — Kế hoạch AI kiểm duyệt ảnh (model TỰ TRAIN)

> **Trạng thái (2026-08-07): 🟡 MỚI LẬP KẾ HOẠCH — chưa viết dòng code nào.**
> Đây là phần **AI của đồ án**: một bộ phân loại ảnh **do nhóm tự huấn luyện**, gắn cờ ảnh quá nhạy cảm để **admin là người quyết định cuối cùng**. AI **không bao giờ tự xóa bài**.
> Kế hoạch này **độc lập** với `GACHA_PLAN.md` và `SKIN_PLAN.md` — không phụ thuộc, làm song song được.
> Khi thi công: cập nhật `server/GUIDE.md` + `admin/GUIDE.md` sau mỗi phase, cập nhật `SECURITY.md` (mục 5 và 12 của plan này đụng bảo mật + dữ liệu nhạy cảm), chạy `codegraph init` trong `server/`.

---

## 0. Quyết định đã chốt

| Hạng mục | Chốt |
|---|---|
| Vai trò của AI | **Trợ lý, không phải trọng tài.** AI gắn cờ + chấm điểm rủi ro; **admin quyết định xóa hay giữ** |
| Cách làm | **Tự train** (transfer learning), KHÔNG gọi API bên thứ ba — đây là phần đóng góp kỹ thuật của đồ án |
| Chính sách nội dung | **Ảnh gợi cảm (sexy) VẪN ĐƯỢC ĐĂNG.** Chỉ ảnh **quá nhạy cảm (explicit)** mới bị gắn cờ |
| Ảnh bị gắn cờ | **VẪN HIỂN THỊ BÌNH THƯỜNG với bạn bè.** AI **không bao giờ ẩn bài** — admin xem hàng đợi rồi xóa sau. Tránh phạt oan người dùng khi model báo nhầm (hệ quả kỹ thuật: mục 1.3) |
| Số lớp lúc train | **3 lớp**: `SAFE` · `SUGGESTIVE` · `EXPLICIT` (xem mục 1.2 — vì sao vẫn train 3 lớp dù chính sách chỉ cần 2) |
| Điểm rủi ro | `score = P(EXPLICIT)` — một số duy nhất, dễ giải thích, dùng để sắp xếp hàng đợi |
| Kiến trúc model | **MobileNetV3-Small** (hoặc EfficientNet-Lite0), transfer learning từ ImageNet, input 224×224 |
| Nơi chạy suy luận | **`onnxruntime-node` NGAY TRONG NestJS** — không thêm service Python, giữ nguyên luật "server là cửa ngõ duy nhất" (CLAUDE.md mục 1) |
| Nơi train | **Máy local của nhóm** (xem mục 3.3 — KHÔNG dùng Colab/Kaggle, lý do ở mục 12) |
| Chiến lược dữ liệu | **C — dataset NSFW công khai**, ưu tiên nguồn học thuật có thỏa thuận sử dụng (mục 2.1) |
| Thời điểm chạy | **Bất đồng bộ** sau khi tạo moment — user đăng bài xong ngay, không phải chờ |
| Thiếu file model | Server **vẫn boot bình thường**, chỉ tắt chức năng gắn cờ (mọi bài vào hàng đợi thường như hiện tại) — đúng pattern PayOS |
| Dữ liệu cho vòng lặp cải thiện | Lưu **embedding 576 chiều**, **KHÔNG lưu ảnh người dùng** (mục 9.2) |

---

## 1. Bài toán và chính sách

### 1.1 Luồng tổng thể

```
User đăng moment
   ↓
Server tạo doc posts/{id}  →  trả về app NGAY (không chờ AI)
   ↓ (async, chạy nền)
Tải ảnh 224×224 từ Cloudinary  →  MobileNetV3 (ONNX)  →  3 xác suất
   ↓
score = P(EXPLICIT)  →  áp ngưỡng  →  ghi posts/{id}.moderation
   ↓
Admin mở MomentsPage → hàng đợi sắp theo score giảm dần → giữ lại / xóa
   ↓
Quyết định của admin → ghi nhãn → dữ liệu để train model v2
```

**Trong suốt quá trình trên, bài LUÔN hiển thị bình thường với bạn bè.** AI chỉ ghi thêm metadata; nó không có quyền tác động tới thứ người dùng nhìn thấy. Quyền xóa duy nhất nằm ở admin.

### 1.2 Vì sao train 3 lớp dù chính sách chỉ cần phân biệt 2

Chính sách sản phẩm là: sexy được đăng, explicit thì gắn cờ. Nghe như bài toán nhị phân. **Nhưng gộp `sexy` vào `SAFE` ngay lúc train là sai lầm kỹ thuật**, vì:

- `sexy` chính là **vùng biên** giữa hai lớp. Nhét nó vào `SAFE` buộc model phải vẽ ranh giới xuyên qua giữa một lớp — model sẽ học biên mờ và **báo nhầm rất nhiều trên ảnh bikini, ảnh bãi biển, selfie hở vai** (những thứ chắc chắn xuất hiện trong app thật).
- Tách thành lớp riêng cho model một "vùng đệm" để học. Sau đó **gộp ở tầng chính sách**, không phải tầng model.

**Nguyên tắc thiết kế:** model trả về 3 xác suất thô; chính sách (ngưỡng + ánh xạ) nằm ở [`constants.ts`](../../server/src/common/constants.ts). **Đổi chính sách không cần train lại model.** Đây là điểm thiết kế đáng viết vào báo cáo.

### 1.3 Một ngưỡng, và vì sao nó được phép đặt thấp

Vì AI không ẩn bài, chỉ còn **một quyết định duy nhất**: có đưa bài vào hàng đợi cho admin xem hay không.

```
score ≥ MODERATION_THRESHOLD  → status FLAGGED  → vào hàng đợi (bài VẪN hiển thị)
score < MODERATION_THRESHOLD  → status AUTO_OK  → không làm phiền admin
```

Hàng đợi tự sắp theo `score` giảm dần, nên không cần ngưỡng thứ hai để phân mức ưu tiên.

**Hệ quả kỹ thuật quan trọng.** Trong hệ thống có ẩn tự động, một false positive **phạt oan người dùng vô tội** — cái giá rất đắt, buộc phải đặt ngưỡng cao và chấp nhận bỏ sót. Ở đây false positive chỉ **tốn thêm vài giây của admin**. Cái giá rẻ đi hàng chục lần, nên được phép **hạ ngưỡng để đẩy recall lên cao**.

Nói cách khác: quyết định sản phẩm "không ẩn bài" trực tiếp cho phép một điểm vận hành kỹ thuật tốt hơn. Đây là ví dụ rõ ràng về việc **ràng buộc sản phẩm định hình hàm mục tiêu của model** — rất đáng viết thành một mục trong báo cáo.

Giá trị cụ thể của ngưỡng **chưa chốt** — chọn từ đường cong ở mục 4.3 sau khi train xong, chứ không đặt bừa.

> Nếu admin panel muốn có badge "ưu tiên cao" cho bài điểm rất cao, đó là **hằng số phía React**, không phải luật nghiệp vụ của server.

---

## 2. Dữ liệu huấn luyện

### 2.1 Nguồn — thử theo thứ tự

| Ưu tiên | Nguồn | Cách lấy | Ghi chú |
|---|---|---|---|
| 1 | **NPDI Pornography-2k** (UFMG, Brazil) | Gửi yêu cầu tới nhóm tác giả, cần email trường + thỏa thuận sử dụng | Dùng nhiều trong literature → **có baseline công bố để so sánh** |
| 2 | **LSPD** (Large-Scale Pornographic Dataset) | Form xin quyền truy cập từ tác giả | Có nhãn phân loại lẫn detection |
| 3 | `alexkimxyz/nsfw_data_scraper` | Repo GitHub cho **danh sách URL**, tự tải | Chính là dữ liệu train ra NSFWJS. **Rủi ro ở mục 12.1 nằm hoàn toàn trên nhóm** |

**Nộp yêu cầu truy cập NGAY ở phase M0** — có thể mất vài tuần chờ phản hồi. Toàn bộ phần còn lại của hệ thống làm được song song với dữ liệu tạm.

**Lớp `SAFE`** lấy từ nguồn sạch: Open Images, COCO, Places365. **Bắt buộc bổ sung ảnh tự chụp giống app thật** — selfie trong nhà, ảnh thiếu sáng, ảnh đồ ăn, ảnh chụp chung. Chính những ảnh này là nguồn báo nhầm lớn nhất nếu thiếu trong tập train.

### 2.2 Quy tắc xử lý dữ liệu — BẮT BUỘC

1. Dữ liệu thô để trong **thư mục/ổ đĩa riêng có mã hóa**, ngoài thư mục repo.
2. Thêm pattern vào `.gitignore` **trước khi tải dòng đầu tiên**.
3. **KHÔNG** để trên OneDrive/Google Drive của trường, không đẩy lên bất kỳ dịch vụ đám mây nào.
4. Pipeline **tự động hoàn toàn**: tải → tiền xử lý → trích đặc trưng → train → **xóa dữ liệu thô**. Chỉ giữ lại file trọng số và tập đặc trưng đã trích.
5. **Không ai phải ngồi xem qua từng ảnh.** Debug thì xem mẫu ngẫu nhiên ở kích thước thumbnail, không duyệt tuần tự.
6. Ghi rõ nguồn + giấy phép của từng dataset vào báo cáo.

### 2.3 Cân bằng lớp

Dataset NSFW công khai thường lệch nặng. Xử lý bằng **class weight** trong hàm loss (đơn giản, không tạo thêm dữ liệu) và báo cáo phân bố thật của từng lớp. Không dùng oversampling ảnh nhạy cảm.

---

## 3. Model và huấn luyện

### 3.1 Kiến trúc

| Thành phần | Chọn |
|---|---|
| Backbone | **MobileNetV3-Small**, pretrained ImageNet, **đóng băng** |
| Đầu ra backbone | Vector đặc trưng **576 chiều** |
| Head | `Dropout → Linear(576 → 3) → Softmax` |
| Input | 224×224 RGB, chuẩn hóa theo mean/std của ImageNet |
| Augmentation | Lật ngang, đổi sáng/tương phản nhẹ, crop ngẫu nhiên. **KHÔNG** lật dọc |

### 3.2 Chiến lược 2 giai đoạn

**Giai đoạn 1 — trích đặc trưng một lần.** Chạy toàn bộ ảnh qua backbone đóng băng, lưu vector 576 chiều ra file `.npy`. Chạy **một lần duy nhất**, mất ~30–60 phút trên CPU cho ~20k ảnh.

**Giai đoạn 2 — train head trên vector.** Mỗi lần train chỉ vài phút trên CPU. Nhờ vậy thử được **rất nhiều cấu hình** (learning rate, dropout, class weight) → nhiều thí nghiệm để viết vào báo cáo.

**Tùy chọn:** mở băng vài tầng cuối của backbone và fine-tune với learning rate thấp — cần GPU rời, tăng độ chính xác thêm chút. Làm sau khi giai đoạn 2 đã ổn.

### 3.3 Compute — train tại chỗ

| Bước | Phần cứng | Thời gian ước tính |
|---|---|---|
| Trích đặc trưng | CPU laptop | ~30–60 phút / 20k ảnh |
| Train head | CPU | vài phút mỗi lần |
| Fine-tune tầng cuối (tùy chọn) | GPU rời (GTX 1650 trở lên đủ) | ~20–40 phút |
| Xuất ONNX + quantize int8 | CPU | vài phút |

⚠️ **KHÔNG dùng Google Colab hay Kaggle** — lý do ở mục 12.2.

### 3.4 Xuất model

```
PyTorch/TF  →  ONNX (opset 17)  →  quantize int8  →  server/assets/models/moderation_v1.onnx
```

Mục tiêu: **file ≤ 20MB, RAM lúc chạy ≤ 200MB**. Phải **đo RAM thật** trước khi deploy — Render gói free chỉ có 512MB (mục 12.3).

Đặt tên file kèm phiên bản (`moderation_v1.onnx`, `moderation_v2.onnx`) và ghi `modelVersion` vào từng kết quả — để so sánh được v1 với v2 ở phase M5.

---

## 4. Đánh giá

### 4.1 Không dùng accuracy

Dataset mất cân bằng → accuracy vô nghĩa. Chỉ số bắt buộc báo cáo:

| Chỉ số | Vì sao |
|---|---|
| **Recall của `EXPLICIT`** | Quan trọng nhất — bỏ sót là hỏng nghiệp vụ |
| **Precision của `EXPLICIT`** | Thấp = admin phải duyệt rác |
| **Confusion matrix 3×3** | Xem model nhầm theo hướng nào; đặc biệt là `SUGGESTIVE ↔ EXPLICIT` |
| **Tỉ lệ ảnh phải review** | Chỉ số tự định nghĩa: % tổng ảnh bị đẩy vào hàng đợi = "chi phí vận hành" |
| **Đường cong Precision–Recall** | Để **chọn ngưỡng có căn cứ** |

### 4.2 Bộ test

Tách **trước khi train**, không đụng vào trong suốt quá trình tune. Bổ sung một **tập test "thực địa"** gồm ảnh giống app thật (tự chụp) để đo báo nhầm trong điều kiện thực — đây là con số trung thực nhất và nên nêu riêng trong báo cáo.

### 4.3 Chọn ngưỡng — biểu đồ quan trọng nhất của báo cáo

Vẽ: **trục X = tỉ lệ ảnh phải review**, **trục Y = recall của `EXPLICIT`**. Đánh dấu điểm ngưỡng đã chọn, giải thích tại sao.

Biểu đồ này trả lời trực tiếp câu hội đồng chắc chắn hỏi: *"tại sao lấy ngưỡng này mà không phải ngưỡng khác?"*

**Cách lập luận khi chọn điểm.** Vì AI không ẩn bài (mục 1.3), chi phí của false positive là thời gian admin chứ không phải trải nghiệm người dùng. Nên đặt câu hỏi theo hướng: *"admin duyệt nổi bao nhiêu ảnh mỗi ngày?"* → suy ra tỉ lệ review chấp nhận được → đọc ngược từ đồ thị ra ngưỡng → báo cáo recall đạt được ở điểm đó. Đây là lập luận **đi từ ràng buộc vận hành ra tham số kỹ thuật**, chứ không phải chọn số tròn cho đẹp.

### 4.4 So sánh với baseline

Chạy cùng bộ test qua **NSFWJS / nsfw_model** (model pretrain công khai) và so sánh. Nếu model tự train kém hơn — **ghi trung thực và phân tích tại sao** (ít dữ liệu, backbone nhỏ hơn, phân bố khác). Kết luận trung thực có giá trị hơn con số đẹp không thuyết phục.

---

## 5. Mô hình dữ liệu (Firestore)

### 5.1 Bổ sung vào `posts/{id}`

```ts
/** Ket qua kiem duyet AI — ghi bat dong bo sau khi tao moment. */
export interface MomentModeration {
  /** P(EXPLICIT) — 0..1, dung de sap xep hang doi. */
  score: number;
  /** 3 xac suat tho tu model. */
  probs: { safe: number; suggestive: number; explicit: number };
  status: ModerationStatus;
  /** Ten file model da dung — de doi chieu khi so sanh v1/v2. */
  modelVersion: string;
  checkedAt: string;
  /** uid admin da duyet — chi co khi status la REVIEWED_*. */
  reviewedBy?: string;
  reviewedAt?: string;
}

export type ModerationStatus =
  | 'PENDING'        // chua chay AI xong (hoac model chua cau hinh)
  | 'AUTO_OK'        // score < THRESHOLD — khong vao hang doi
  | 'FLAGGED'        // score >= THRESHOLD — vao hang doi. BAI VAN HIEN THI BINH THUONG
  | 'REVIEWED_KEEP'; // admin da xem va quyet dinh giu lai
```

**Không có trạng thái ẩn bài.** Không giá trị nào của `status` làm bài biến mất khỏi feed — `moderation` thuần túy là metadata cho admin.

Không có trạng thái `REVIEWED_DELETED` — vì xóa bài thì doc biến mất. Nhãn "admin đã xóa" ghi ở collection riêng (mục 5.2).

### 5.2 Collection mới `moderationLabels/{id}`

**Đây là dữ liệu để train model v2.** Ghi **trước khi** xóa moment, nếu không mất nhãn.

```ts
export interface ModerationLabel {
  labelId: string;
  momentId: string;
  /** Nhan do NGUOI gan — ground truth that. */
  humanLabel: 'SAFE' | 'SUGGESTIVE' | 'EXPLICIT';
  /** Du doan cua model luc do — de do do lech. */
  modelScore: number;
  modelVersion: string;
  /** Vector dac trung 576 chieu tu backbone. KHONG luu anh — xem muc 9.2. */
  embedding: number[];
  reviewedBy: string;
  createdAt: string;
}
```

### 5.3 Hằng số mới — [`constants.ts`](../../server/src/common/constants.ts)

```ts
export const MODERATION_THRESHOLD = 0.??;  // chot sau phase M1 — xem muc 4.3
export const MODERATION_INPUT_SIZE = 224;  // input MobileNetV3
export const MODERATION_MODEL_VERSION = 'v1';
```

Thêm vào `Collections`: `MODERATION_LABELS: 'moderationLabels'`.

---

## 6. Kiến trúc server

### 6.1 Module mới `server/src/moderation/`

```
moderation.module.ts
moderation.service.ts        # NƠI DUY NHẤT load & chạy ONNX — pattern PayosService
moderation.service.spec.ts
moderation.repository.ts     # ghi/đọc moderationLabels
entities/moderation.entity.ts
dto/
```

`ModerationService` cung cấp:

| Method | Việc |
|---|---|
| `classify(mediaUrl)` | Tải ảnh 224×224 → infer → trả `{ probs, embedding }` |
| `scoreMoment(momentId, mediaUrl)` | Gọi `classify`, áp ngưỡng, ghi `posts/{id}.moderation` |
| `recordLabel(...)` | Ghi `moderationLabels` khi admin quyết định |
| `isEnabled()` | Có file model hay không |

### 6.2 Lấy ảnh — dùng transformation của Cloudinary

Ảnh đã nằm trên Cloudinary. Thay vì tải ảnh gốc vài MB về rồi resize, chèn transformation vào URL để Cloudinary trả **đúng 224×224**:

```
https://res.cloudinary.com/<cloud>/image/upload/w_224,h_224,c_fill/<public_id>
```

Chỉ vài chục KB mỗi ảnh → tiết kiệm băng thông Render và bỏ luôn bước resize phía server.

### 6.3 Hook vào [`moments.service.ts`](../../server/src/moments/moments.service.ts)

Gọi **sau khi** đã tạo doc và trả response cho app — không `await` trong luồng chính. Lỗi thì log và để `status: 'PENDING'`, tuyệt đối không làm hỏng việc đăng bài.

### 6.4 Fail-safe

Thiếu file model → `ModerationService.isEnabled()` trả `false`, server **vẫn boot**, mọi moment giữ `status: 'PENDING'`, admin làm việc như hiện tại. Y hệt cách PayOS xử lý khi thiếu khóa. Nhờ vậy CI và e2e chạy offline được.

Đường dẫn model đọc từ env, **cho phép rỗng** — thêm vào [`env.validation.ts`](../../server/src/config/env.validation.ts):

```ts
MODERATION_MODEL_PATH: Joi.string().optional().allow(''),
```

### 6.5 Dependency mới

`onnxruntime-node` + một thư viện decode ảnh (`sharp` — **đã có sẵn** trong dự án cho việc ghép ảnh coop). Không cần thêm gì khác.

---

## 7. Endpoint

Base đã có: `GET /admin/moments` và `DELETE /admin/moments/:id` trong [`admin.controller.ts`](../../server/src/admin/admin.controller.ts).

| Method | Route | Việc |
|---|---|---|
| `GET` | `/admin/moments` | **Mở rộng**: thêm query `status`, `sortBy=risk`, `minScore`. Mặc định sắp theo `moderation.score` giảm dần |
| `POST` | `/admin/moments/:id/review` | **Mới**: admin quyết định. Body `{ decision: 'KEEP' \| 'DELETE', label: 'SAFE' \| 'SUGGESTIVE' \| 'EXPLICIT' }` → ghi `moderationLabels` → nếu `DELETE` thì xóa bài → ghi `adminLogs` |
| `GET` | `/admin/moderation/stats` | **Mới**: số bài mỗi trạng thái, phân bố điểm, số nhãn đã thu — cho dashboard và cho báo cáo |
| `POST` | `/admin/moderation/rescan` | **Mới**: chạy lại toàn bộ bài cũ với model mới (dùng ở phase M5) |

`DELETE /admin/moments/:id` hiện tại **giữ nguyên** để không phá contract — nhưng admin UI chuyển sang dùng `/review` để mỗi lần xóa đều sinh ra một nhãn.

**App Android: KHÔNG phải sửa gì.** Thêm field vào response là an toàn theo CLAUDE.md mục 13.

---

## 8. Admin UI — mở rộng [`MomentsPage.tsx`](../../admin/src/pages/MomentsPage.tsx)

| Thành phần | Mô tả |
|---|---|
| Tab hàng đợi | `Cần duyệt` (`FLAGGED`, sắp theo score giảm dần) · `Tất cả` |
| Cột điểm rủi ro | Thanh progress + số; màu theo mức (xanh/vàng/đỏ — **ngưỡng màu là hằng số phía React**, không phải luật server) |
| **Làm mờ ảnh bị gắn cờ** | Ảnh `FLAGGED` mặc định blur **trong trang admin**, admin bấm mới hiện. Lưu ý: blur chỉ ở phía admin — **người dùng vẫn thấy ảnh bình thường trong app**. Vừa là UX tốt cho người kiểm duyệt vừa là chi tiết đáng viết trong báo cáo |
| Nút hành động | `Giữ lại` / `Xóa` — cả hai đều ghi nhãn |
| Chọn nhãn khi duyệt | Radio `SAFE` / `SUGGESTIVE` / `EXPLICIT` — nhãn này quý hơn cả quyết định giữ/xóa |
| Badge "AI chưa quét" | Cho `status: PENDING` |

---

## 9. Vòng phản hồi — model tự cải thiện

### 9.1 Ý tưởng

Mỗi lần admin bấm duyệt là **một nhãn do người thật gán, trên đúng phân bố dữ liệu thật của app**.

```
dataset_v1 (công khai)  →  model_v1  →  admin duyệt  →  thu nhãn thật
                                                             ↓
dataset_v2 = v1 + nhãn thật  →  model_v2  →  đo lại  →  SO SÁNH v1 vs v2
```

Đây là **active learning**. Ngay cả khi chỉ thu được vài trăm nhãn trong thời gian làm đồ án, việc **đo được xu hướng cải thiện** đã đủ để viết thành một chương.

### 9.2 Lưu embedding, KHÔNG lưu ảnh người dùng

Vì backbone đóng băng, **train lại head chỉ cần vector đặc trưng, không cần ảnh gốc**. Nên `moderationLabels` lưu vector 576 chiều (~2.3KB) thay vì ảnh.

| | |
|---|---|
| ✅ Riêng tư | Không lưu ảnh nào của người dùng |
| ✅ Nhẹ | 2.3KB/nhãn thay vì vài MB |
| ✅ Đủ dùng | Train lại head trực tiếp trên embedding |
| ⚠️ Hạn chế | Đổi backbone thì embedding cũ vô dụng → phải nêu trong báo cáo |

Đây là một quyết định thiết kế cân bằng giữa quyền riêng tư và khả năng cải thiện model — **đáng một mục riêng trong báo cáo**.

---

## 10. Lộ trình

| Phase | Nội dung | Phụ thuộc |
|---|---|---|
| **M0** | Nộp yêu cầu truy cập dataset · dựng thư mục mã hóa + `.gitignore` · viết script tải/tiền xử lý · thu ảnh `SAFE` tự chụp | — |
| **M1** | Trích đặc trưng · train head · đánh giá · vẽ đường cong · **chốt 2 ngưỡng** · xuất ONNX + quantize · **đo RAM** | M0 |
| **M2** | Module `moderation/` · `onnxruntime-node` · hook async vào `moments` · fail-safe · env · unit test | M1 |
| **M3** | Mở rộng `GET /admin/moments` · `POST /admin/moments/:id/review` · sửa `MomentsPage.tsx` (blur, cột điểm, nút duyệt) | M2 |
| **M4** | Ghi `moderationLabels` + embedding · `GET /admin/moderation/stats` · dùng thật để tích lũy nhãn | M3 |
| **M5** | Train model v2 trên dataset mở rộng · so sánh v1/v2 · `POST /admin/moderation/rescan` | M4 + đủ nhãn |

**M0 và M2 chạy song song được** — không cần chờ dataset về mới bắt đầu code server. Dùng model giả (trả điểm ngẫu nhiên) để dựng và test toàn bộ đường ống trước.

---

## 11. Sửa những thứ đang chạy

| File | Sửa gì |
|---|---|
| [`moment.entity.ts`](../../server/src/moments/entities/moment.entity.ts) | Thêm `moderation?: MomentModeration` |
| [`moments.service.ts`](../../server/src/moments/moments.service.ts) | Hook async gọi `ModerationService.scoreMoment` sau khi tạo |
| [`moments.service.ts`](../../server/src/moments/moments.service.ts) — feed | **KHÔNG SỬA.** Logic feed giữ nguyên hoàn toàn — không lọc theo `moderation`. Đây là ràng buộc thiết kế, không phải thiếu sót |
| [`admin.controller.ts`](../../server/src/admin/admin.controller.ts) | 3 endpoint mới + mở rộng `GET /admin/moments` |
| [`admin.service.ts`](../../server/src/admin/admin.service.ts) | Logic hàng đợi, ghi nhãn, gọi `AuditService.log()` |
| [`constants.ts`](../../server/src/common/constants.ts) | 4 hằng số + collection `moderationLabels` |
| [`env.validation.ts`](../../server/src/config/env.validation.ts) | `MODERATION_MODEL_PATH` (optional, allow rỗng) |
| [`MomentsPage.tsx`](../../admin/src/pages/MomentsPage.tsx) | Tab, cột điểm, blur ảnh, nút duyệt |
| `server/.env.example` | Thêm biến mới |
| `SECURITY.md` | Ghi: dữ liệu nhạy cảm, chính sách lưu embedding thay vì ảnh, quyền truy cập hàng đợi kiểm duyệt |

---

## 12. Rủi ro

### 12.1 Dữ liệu scrape không kiểm duyệt có thể chứa nội dung bất hợp pháp

LAION-5B — một trong những dataset lớn nhất ngành ML — bị Stanford Internet Observatory phát hiện có ảnh lạm dụng trẻ em cuối 2023, phải gỡ xuống và chỉ phát hành lại sau khi làm sạch. Nếu một dataset được cả ngành soi vẫn dính, thì nhóm sinh viên tự scrape từ danh sách URL không có công cụ nào để lọc.

**Giảm thiểu:** ưu tiên tuyệt đối nguồn 1 và 2 ở mục 2.1 (có kiểm duyệt, có thỏa thuận sử dụng, có dây chuyền trách nhiệm). **Hỏi giảng viên hướng dẫn trước khi tải bất cứ thứ gì** — nhiều trường có quy định riêng, và không ai muốn phát hiện điều đó sau khi đã tải 20GB về.

### 12.2 Colab và Kaggle nhiều khả năng cấm

Điều khoản sử dụng của cả hai đều cấm nội dung khiêu dâm. Upload dataset lên đó có nguy cơ **bị khóa tài khoản giữa kỳ đồ án**. → Train tại chỗ (mục 3.3). May mắn là bài toán này không cần GPU mạnh.

### 12.3 Render gói free chỉ 512MB RAM

Phải **đo RAM thật** sau khi load ONNX trước khi deploy. Nếu chật: quantize int8, hoặc hạ xuống MobileNetV3-Small nếu đang dùng backbone lớn hơn. Có sẵn phương án lùi là tắt tính năng (mục 6.4) — không bao giờ để chết server.

### 12.4 Lệch phân bố dữ liệu

Train trên ảnh dataset công khai, chạy trên selfie chụp bằng điện thoại trong phòng thiếu sáng — hai phân bố khác nhau. Sẽ có báo nhầm. **Nêu trước trong báo cáo**, và đây chính là lý do tồn tại của vòng phản hồi ở mục 9.

**Mức độ nghiêm trọng đã được giảm bằng thiết kế.** Vì AI không ẩn bài (mục 1.3), một lần báo nhầm chỉ khiến admin mất vài giây xem rồi bấm "Giữ lại" — người dùng không hề biết và không bị ảnh hưởng gì. Đây là ví dụ về việc **thiết kế hệ thống hấp thụ điểm yếu của model** thay vì đòi model phải hoàn hảo; đáng nêu rõ trong báo cáo.

### 12.5 Thiên lệch theo tông da

Bộ phân loại dựa nhiều vào tín hiệu "hở da" có nguy cơ hoạt động không đều giữa các tông da, tùy phân bố dataset. **Nếu đo được và nêu ra, đây là mục "AI có trách nhiệm" mà rất ít đồ án sinh viên chạm tới** — điểm cộng lớn.

### 12.6 Sức khỏe tinh thần

Làm việc với dữ liệu loại này gây mệt mỏi thật sự. Pipeline tự động, không duyệt ảnh bằng tay, chia việc trong nhóm. Đây là lời khuyên thật trong ngành, không phải khách sáo.

---

## 13. Câu hỏi còn mở

| # | Câu hỏi | Ghi chú |
|---|---|---|
| ~~1~~ | ~~Bài bị gắn cờ có ẩn khỏi feed không?~~ | ✅ **ĐÃ CHỐT 2026-08-07: KHÔNG ẩn.** Bài luôn hiển thị bình thường, admin xóa sau — tránh phạt oan người dùng khi báo nhầm |
| 2 | Người đăng có được thông báo khi bài **bị admin xóa** không? | Không còn liên quan tới AI (không có ẩn tạm). Bản đầu **không**, để app không phải sửa |
| 3 | Quét lại toàn bộ bài cũ khi có model v2 — chạy tay hay theo lịch? | Bản đầu: nút bấm tay trong admin |
| 4 | Có kiểm duyệt cả ảnh trong chat và avatar không? | Bản đầu **chỉ moment**. Chat riêng tư → đụng quyền riêng tư, cần bàn kỹ |
| 5 | Chốt backbone: MobileNetV3-Small hay EfficientNet-Lite0? | Quyết sau khi đo ở M1 — **thử cả hai và so sánh là một mục báo cáo** |

---

## 14. Báo cáo — chương AI viết gì

| Mục | Nội dung | Nguồn số liệu |
|---|---|---|
| Đặt vấn đề | Kiểm duyệt thủ công không mở rộng được; AI hỗ trợ chứ không thay người | — |
| Dữ liệu | Nguồn, giấy phép, phân bố lớp, mất cân bằng và cách xử lý, **giới hạn phạm vi đã tuyên bố** | Mục 2 |
| Tiền xử lý | Resize, chuẩn hóa, augmentation, lý do từng lựa chọn | Mục 3.1 |
| Kiến trúc & huấn luyện | Sơ đồ, hyperparameter, **learning curve**, overfitting thực tế gặp | Mục 3 |
| Vì sao 3 lớp | Lập luận về vùng biên `sexy` — tách chính sách khỏi model | **Mục 1.2** |
| Đánh giá | Confusion matrix, P/R/F1 từng lớp, đường cong chọn ngưỡng | Mục 4 |
| So sánh baseline | Model tự train vs NSFWJS trên cùng bộ test — **kết luận trung thực** | Mục 4.4 |
| Thiết kế hệ thống | Human-in-the-loop, **vì sao AI không được quyền ẩn bài**, hàng đợi, làm mờ ảnh cho admin | Mục 1.3, 8 |
| Vòng phản hồi | Active learning, lưu embedding thay vì ảnh, so sánh v1 vs v2 | Mục 9, phase M5 |
| AI có trách nhiệm | Thiên lệch tông da, quyền riêng tư, giới hạn, rủi ro báo nhầm | Mục 12.4, 12.5, 9.2 |

Hai mục ăn điểm nhất mà đồ án khác hay thiếu: **đường cong chọn ngưỡng** (mục 4.3) và **so sánh v1 với v2 sau vòng phản hồi** (phase M5).

---

## 15. Changelog

| Ngày | Thay đổi |
|---|---|
| 2026-08-07 | Lập kế hoạch. Chốt: tự train, human-in-the-loop, 3 lớp train / 2 lớp chính sách (sexy được đăng), chiến lược dữ liệu C, ONNX trong NestJS, lưu embedding thay vì ảnh |
| 2026-08-07 | **Chốt: AI KHÔNG được quyền ẩn bài.** Bỏ trạng thái `HIDDEN`, gộp 2 ngưỡng thành 1 (`MODERATION_THRESHOLD`), feed giữ nguyên không lọc. Hệ quả: được phép hạ ngưỡng để tăng recall vì false positive chỉ tốn công admin (mục 1.3, 12.4) |

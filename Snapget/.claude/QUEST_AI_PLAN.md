# QUEST_AI_PLAN.md — AI Daily Quest: sinh quest + xác minh ảnh

> **Trạng thái: 📋 PLAN — chưa thi công.**
> Tính năng AI của DATN: hoàn thiện **actor AI đã có trong tài liệu phân tích thiết kế nhưng bị hoãn** (server/.claude/CLAUDE.md mục 1 và 14: *"AI (tạo daily quest thứ 3) có trong thiết kế nhưng hoãn"*, `DAILY_QUESTS_PER_DAY = 2 // khong AI`).
> Gồm 2 nửa: **(1) AI sinh quest thứ 3 mỗi ngày** ("Chụp một chiếc cốc") và **(2) AI xác minh ảnh user đăng** có chứa vật thể được yêu cầu hay không — phần xác minh do nhóm **tự train** (transfer learning), là chương ML chính của báo cáo.
> Thay thế hoàn toàn hướng kiểm duyệt NSFW (MODERATION_PLAN.md đã xóa 2026-08-14 — lý do: khó demo trước hội đồng, dữ liệu huấn luyện nhạy cảm/rủi ro pháp lý, không nằm trong thiết kế gốc).
> Khi thi công: cập nhật `GUIDE.md` (app + server) sau mỗi phase, `SECURITY.md` cho secrets mới, chạy `codegraph init`.

---

## 0. Quyết định đã chốt

| Hạng mục | Chốt |
|---|---|
| Tính năng | **AI sinh quest thứ 3 + tự train model xác minh ảnh** (chốt 2026-08-14, thay hướng NSFW) |
| Luồng hoàn thành quest | **Hook vào đăng bài** (user chốt 2026-08-14): user đăng moment như bình thường, server tự kiểm tra ảnh vừa đăng — khớp vật thể thì quest tự hoàn thành, giống cơ chế LOGIN/POST_MOMENT. **KHÔNG có màn nộp ảnh riêng**, ảnh quest chính là moment lên feed |
| Thưởng | **2/2 quest cũ giữ nguyên +60 · quest AI hoàn thành cộng riêng +30** (user chốt 2026-08-14) — không ai bị mất 60 đang có nếu không tìm được vật thể. Tổng tối đa 90 Astrite/ngày |
| Trạng thái UserQuest | **Giữ nguyên `'COMPLETED'` duy nhất** — hệ quả của luồng hook: ảnh không khớp thì quest đơn giản là *chưa xong*, không cần PENDING/REJECTED |
| Bộ từ vựng | **12 lớp vật thể** (mục 2 — user giao Claude quyết 2026-08-14, tiêu chí: độ chính xác cao nhất) |
| Model xác minh | **Multi-label classifier**: backbone MobileNetV3-Small pretrained ImageNet (đóng băng ở v0) + head tự train trên COCO subset → ONNX int8 |
| Model sinh quest | LLM nhỏ chạy CPU trên HF Space (Qwen2.5-1.5B-Instruct GGUF Q4 qua llama-cpp-python) — sinh **offline qua cron**, luôn có **fallback template tĩnh**, không bao giờ chặn request của user |
| Hosting AI | **Hugging Face Spaces free CPU** (2 vCPU / 16GB RAM) — FastAPI, cả 2 model cùng 1 Space. Render **không** load model (giữ nguyên 512MB) |
| Server ↔ Space | NestJS là **cửa ngõ duy nhất** — app không bao giờ gọi Space trực tiếp. Auth bằng header `X-API-Key` (secret trong Spaces Secrets + server .env) |
| Verify media | **Chỉ moment PHOTO** (quest ghi rõ "chụp ảnh"); VIDEO/ảnh GIF bỏ qua ở v1 |
| Ngôn ngữ quest | `content` **tiếng Việt có dấu** — theo đúng convention `QUEST_CONTENT` hiện có (quest là *dữ liệu server*, không thuộc rule "UI string tiếng Anh" của APK) |
| Chi phí | Build: **0đ** (Colab/Kaggle GPU free, HF Space free, cron-job.org free, Render free). Demo: **$7 Render Starter** bỏ sleep, bật **trước bảo vệ 3–5 ngày**; Space giữ free (keep-warm ping là đủ) |
| Fail-safe | Thiếu env AI (`AI_SERVICE_URL`…) → server boot bình thường, **quest AI không sinh, app chạy y hệt hiện tại 2 quest** — theo đúng pattern PayOS/Cloudinary |

---

## 1. Tính năng & luồng end-to-end

### 1.1 Hai nửa AI

| | Sinh quest | Xác minh ảnh |
|---|---|---|
| Model | LLM nhỏ (tích hợp, không train) | **Tự train** — chương ML của báo cáo |
| Chạy khi nào | Cron 00:05 UTC mỗi ngày (offline) | Lúc user đăng moment (online, ~0,5–2s) |
| Latency quan trọng? | Không — sinh trước khi ai mở app | Có — nằm trong response POST /moments |
| Hỏng thì sao | Fallback template tĩnh, user không biết | Bỏ qua verify, quest còn nguyên, đăng bài **không bao giờ fail vì AI** |

### 1.2 Luồng một ngày

```
00:05 UTC  cron-job.org → POST /quests/ai/generate (x-cron-secret)
           server → Space /generate (12 lớp + 3 target gần nhất để tránh lặp)
           → validate JSON → dailyQuests/{date}_AI_CHALLENGE  {targetClass:'cup', content:'Chụp một chiếc cốc bên bàn học'}
           (Space chết / JSON hỏng / cron miss → fallback template, chọn lớp seed theo ngày)

user mở app → GET /quests/today → 3 quest (LOGIN tự xong như cũ)

user chụp + đăng moment như MỌI NGÀY (không màn hình mới)
           POST /upload → Cloudinary → POST /moments
           server: tạo moment + streak/quest hook như cũ
           → nếu hôm nay có quest AI & user chưa xong & contentType=PHOTO:
               gọi Space /verify với URL Cloudinary đã resize 224
               khớp  → completeUserQuest(AI_CHALLENGE) + credit 30 Astrite
                       response kèm aiQuest:{result:'MATCHED', score}  → app toast 🎉
               không → response aiQuest:{result:'NOT_MATCHED', score}  → app toast "Ảnh chưa có cốc, thử lại nhé"
                       BÀI VẪN ĐĂNG BÌNH THƯỜNG, quest chưa tick, đăng bài khác để thử lại
               Space lỗi/timeout 3s → aiQuest:{result:'SKIPPED'}, đăng bài không ảnh hưởng
```

Điểm mấu chốt của thiết kế: **AI thất bại ở bất kỳ khâu nào cũng không phá chức năng đang chạy** — sinh hỏng có template, verify hỏng thì bỏ qua, thiếu env thì cả tính năng tắt êm.

---

## 2. Bộ từ vựng: 12 lớp — và tại sao là 12

### 2.1 Trả lời câu hỏi "bao nhiêu chủ đề?"

**12 lớp.** Lý do chọn con số này thay vì ít hơn hoặc nhiều hơn:

- **Độ chính xác giảm dần theo số lớp**: mỗi lớp thêm vào là thêm nhiễu lẫn giữa các lớp và loãng dữ liệu/ngưỡng của từng lớp. Với backbone đóng băng + head nhỏ, 10–15 lớp là vùng cho per-class accuracy cao nhất mà vẫn đủ đa dạng.
- **Mỗi lớp phải ≥ ~2.800 ảnh trong COCO train2017** — dưới mức đó head học kém. 12 lớp dưới đây đều đạt.
- **Mỗi lớp phải tìm được trong vài phút** ở nhà/trường/quán — quest bất khả thi = user mất 30 Astrite tiềm năng và ghét tính năng.
- **5–8 lớp thì quest lặp nhàm** (tuần nào cũng "chụp cốc"); nhưng độ đa dạng thật sự đến từ **câu chữ LLM** ("cốc cà phê sáng", "cốc trên bàn học", "cốc màu bạn thích nhất") chứ không phải từ cỡ từ vựng — nên không cần 30 lớp.
- Con số 12 còn khớp chu kỳ: 1 tháng mỗi lớp xuất hiện ~2–3 lần với câu chữ khác nhau.

### 2.2 Danh sách 12 lớp (id COCO chuẩn — dùng nguyên tên tiếng Anh trong code/data)

| # | COCO id | Tên hiển thị | ~ảnh COCO train | Tìm ở đâu | Demo tại phòng bảo vệ? |
|---|---|---|---|---|---|
| 1 | `cup` | cái cốc / ly | ~9.2k | mọi nhà | ✅ |
| 2 | `bottle` | chai nước | ~8.5k | mọi nhà | ✅ |
| 3 | `book` | quyển sách | ~5.3k | nhà, trường | ✅ |
| 4 | `chair` | cái ghế | ~12.7k | mọi nơi | ✅ |
| 5 | `potted plant` | chậu cây | ~4.5k | nhà, quán, hành lang | ✅ (hành lang trường) |
| 6 | `laptop` | laptop | ~3.5k | sinh viên nào cũng có | ✅ |
| 7 | `keyboard` | bàn phím | ~2.8k | cùng laptop | ✅ |
| 8 | `backpack` | ba lô | ~5.5k | sinh viên nào cũng có | ✅ |
| 9 | `clock` | đồng hồ | ~4.7k | nhà, lớp học | ✅ |
| 10 | `umbrella` | cái ô / dù | ~3.9k | nhà, ba lô mùa mưa | ✅ (mang theo) |
| 11 | `bicycle` | xe đạp | ~3.3k | sân trường, đường | ✅ (sân trường) |
| 12 | `motorcycle` | xe máy | ~3.5k | khắp Việt Nam | ✅ (bãi xe) |

Cố tình **không** chọn: `cat`/`dog` (không phải ai cũng có — để ngỏ làm v2, xem mục 15), `person` (riêng tư), `cell phone` (máy đang dùng để chụp), `spoon`/`fork` (nhỏ, tỉ lệ pixel thấp, COCO hay nhầm), `tv` (dễ gian lận nhất — chụp màn hình chiếu gì cũng được).

**Mở rộng sau này rẻ**: thêm 1 lớp = lọc thêm data COCO + train lại head (vài phút GPU) + thêm template — không đụng backbone, không đụng server.

---

## 3. Dữ liệu huấn luyện — 100% sạch, công khai

> Khác hẳn hướng NSFW cũ: **không có bất kỳ vấn đề pháp lý/đạo đức/lưu trữ nào**. Chỉ cần không commit dataset vào repo (thêm `datasets/` vào .gitignore của repo notebook nếu có).

### 3.1 Nguồn

| Nguồn | Dùng làm | License (ghi vào báo cáo) |
|---|---|---|
| **COCO 2017** train | Train + val (split 90/10) | Annotations CC-BY 4.0; ảnh Flickr theo từng license CC |
| **COCO 2017** val2017 | Test iid (không đụng lúc train) | như trên |
| **"Snapget-12"** — nhóm tự chụp | **Test domain-shift, số liệu chính khi bảo vệ** | của nhóm |

### 3.2 Pipeline (chạy trên Colab/Kaggle, không tải về máy cá nhân)

1. **FiftyOne** tải COCO 2017 **chỉ những ảnh chứa ≥1 trong 12 lớp** (`fiftyone.zoo.load_zoo_dataset("coco-2017", classes=[...])`) + ~10–15k ảnh **negative** (không chứa lớp nào trong 12) — tổng ~45–55k ảnh, ~7–9GB trên disk Colab, không đụng máy local.
2. Nhãn = vector **multi-hot 12 chiều** từ annotation (ảnh có cả cup lẫn book → cả 2 bit bật). Negative = vector 0.
3. Resize 256 → cache. **Trick tăng tốc quan trọng**: chạy backbone đóng băng 1 lần trên toàn bộ ảnh → lưu **cache embedding 576 chiều** (~50k × 2,3KB ≈ 120MB, để được trên Drive) → từ đó mọi thí nghiệm head/ngưỡng train trong **vài chục giây**, không cần tải lại COCO. (Riêng M6 fine-tune v1 cần ảnh gốc → chạy lại trên Colab.)
4. **Snapget-12**: mỗi thành viên chụp mỗi lớp ~10 ảnh **bằng chính app/điện thoại** (ảnh dọc, trong nhà, đèn vàng, tay che một phần…) + ~30 ảnh negative → ~150–400 ảnh. Đây là tập test "đúng phân phối thật" — chênh lệch COCO-val vs Snapget-12 chính là **domain shift**, một mục phân tích đắt giá trong báo cáo.

### 3.3 Mất cân bằng lớp

`chair` ~12.7k ảnh nhưng `keyboard` ~2.8k, và mỗi bit là bài toán nhị phân lệch (đa số ảnh KHÔNG chứa lớp X) → dùng **`BCEWithLogitsLoss(pos_weight=...)`** tính riêng từng lớp = (số negative / số positive của lớp đó). Ghi vào báo cáo phần xử lý imbalance.

---

## 4. Model & huấn luyện

### 4.1 Vì sao multi-label chứ không phải softmax 12 lớp

Ảnh đời thật chứa **nhiều** vật thể (bàn học có cả cốc, sách, laptop). Câu hỏi nghiệp vụ là *"ảnh có chứa X không?"* — 12 câu hỏi nhị phân độc lập → **sigmoid 12 đầu ra + BCE**, không phải softmax ép chọn 1. Verify = `sigmoid_score[targetClass] ≥ threshold[targetClass]`. (Softmax ở đây là lỗi thiết kế kinh điển — nêu trong báo cáo phần lựa chọn mô hình.)

### 4.2 Kiến trúc

```
Backbone: MobileNetV3-Small, pretrained ImageNet, input 224×224
          → global avg pool → 576-dim
          (ImageNet = nhận diện VẬT THỂ — đúng chính xác bài toán này,
           khác với NSFW nơi feature ImageNet là sai việc)
Head:     Linear(576→256) → ReLU → Dropout(0.2) → Linear(256→12)   [~150k params]
Loss:     BCEWithLogitsLoss(pos_weight per-class)
```

| Phiên bản | Backbone | Train gì | Mục đích |
|---|---|---|---|
| **v0** | đóng băng | chỉ head (~150k params) | baseline, train từ cache embedding trong ~1 phút |
| **v1** | mở block conv cuối, LR ×0.1 | head + block cuối | đo **delta cải thiện v0→v1** — số liệu so sánh cho báo cáo |

- Augmentation (v1): RandomResizedCrop(224, scale 0.6–1.0), flip ngang, ColorJitter nhẹ, **random crop tỉ lệ dọc 3:4** (mô phỏng ảnh điện thoại — nhắm thẳng vào domain shift đo được ở mục 3.4).
- Train trên **Colab/Kaggle GPU free** (T4): v0 vài phút, v1 ~30–60 phút. Notebook lưu trong repo (`ml/` — thư mục mới ở root monorepo, chỉ chứa notebook + script, KHÔNG chứa data/model nhị phân).
- Export: PyTorch → **ONNX** → quantize **int8** (~2,5MB) — chạy `onnxruntime` CPU trên Space, ~30–80ms/ảnh.
- Versioning: `modelVersion` ('v0', 'v1') nằm trong file `model_meta.json` cạnh ONNX trên Space, trả về trong mọi response verify và ghi vào log verify (mục 8).

---

## 5. Đánh giá & chọn ngưỡng

### 5.1 Metrics (tính trên cả COCO-val-test lẫn Snapget-12)

- **Per-class**: precision/recall/F1 tại ngưỡng đã chọn, average precision (AP), PR curve 12 đường.
- **Tổng**: mAP, macro-F1.
- **Latency**: ms/ảnh trên đúng CPU của Space (đo bằng script, ghi p50/p95).

### 5.2 Chọn ngưỡng — chi phí sai lệch KHÔNG đối xứng

| Sai kiểu | Hậu quả | Chi phí |
|---|---|---|
| False accept (ảnh không có cốc nhưng nhận) | user được 30 Astrite "hời" | ~0 (30 Astrite < 1/5 lần quay gacha) |
| False reject (ảnh CÓ cốc mà không nhận) | user bực, quest cảm giác "gian lận" | **cao — đây là trải nghiệm chính** |

→ Chọn **ngưỡng riêng từng lớp** trên PR curve của tập val theo quy tắc: **precision ≥ 0.80, tối đa hóa recall**. Thiên recall có chủ đích — ngược hoàn toàn với bài toán kiểm duyệt (nơi thiên precision); phân tích "policy theo chi phí sai lệch" này là một mục báo cáo hay. 12 ngưỡng nằm trong `thresholds.json` **đi cùng model trên Space** (ngưỡng là artifact của từng phiên bản model, đổi model phải calibrate lại — không hardcode ở server).

### 5.3 Ablation study — điểm nhấn của chương ML

So sánh trên **cùng** 2 tập test:

| Model | Train? | Kỳ vọng |
|---|---|---|
| v0 (backbone băng + head) | có | baseline |
| v1 (fine-tune block cuối) | có | tốt hơn v0 bao nhiêu? |
| **OWL-ViT base-patch32 zero-shot** | không | model 150M params tổng quát |

Câu hỏi trả lời trước hội đồng: *model nhỏ tự train chuyên biệt 12 lớp có thắng model lớn zero-shot trên đúng miền của nó không?* — khả năng cao là **có** (kèm chênh lệch latency ~50ms vs vài giây CPU). Kết luận ngược trực giác "cứ model to là tốt" + có số liệu = loại kết quả hội đồng nhớ.

---

## 6. Kiến trúc hệ thống

### 6.1 Sơ đồ

```
Android app ──► NestJS (Render) ──► Firestore / Cloudinary / FCM   (như hiện tại, không đổi)
                    │
                    └──► HF Space (FastAPI, free CPU 2vCPU/16GB)   [MỚI]
                          ├─ ONNX MobileNetV3+head  → POST /verify
                          ├─ Qwen2.5-1.5B GGUF Q4   → POST /generate
                          └─ GET /health
cron-job.org ──► POST /quests/ai/generate (NestJS, x-cron-secret)   00:05 UTC
cron-job.org ──► GET  <space>/health                                mỗi 10 phút (keep-warm)
```

- **App không bao giờ biết Space tồn tại** — giữ nguyên luật "server là cửa ngõ duy nhất".
- Render **không load model nào** → memory profile 512MB không đổi (rủi ro OOM của hướng cũ biến mất).
- Space sleep sau **48h không có request** → ping 10 phút/lần là không bao giờ ngủ. Space rebuild (khi push code) mất vài phút — không push sát giờ demo.

### 6.2 Verify không cần tải ảnh gốc

Lúc verify, moment đã nằm trên Cloudinary → server gửi cho Space **URL đã transform**:

```
https://res.cloudinary.com/<cloud>/image/upload/w_224,h_224,c_fill,f_jpg/<public_id>
```

Cloudinary resize hộ (~10–20KB thay vì vài MB) — Space chỉ tải đúng 224×224 rồi suy luận. Không có byte ảnh nào đi qua Render.

### 6.3 Fail-safe env (pattern PayOS)

```ts
// env.validation.ts — thêm, đều optional
AI_SERVICE_URL: Joi.string().uri().optional().allow(''),
AI_SERVICE_API_KEY: Joi.string().optional().allow(''),
CRON_SECRET: Joi.string().optional().allow(''),
```

Thiếu bất kỳ biến nào → `AiService.enabled = false` → không sinh quest AI, không verify, `GET /quests/today` trả đúng 2 quest như hôm nay. Server boot bình thường. (Cũng là **công tắc thứ tự deploy**: chỉ điền env sau khi app Android bản mới đã phát hành — xem mục 9.3.)

### 6.4 Bảo mật (khi thi công → cập nhật `SECURITY.md`)

- `X-API-Key` của Space: sinh ngẫu nhiên ≥32 ký tự, đặt trong **Spaces Secrets** (không hardcode trong `app.py`) + `server/.env`. Space từ chối request thiếu/sai key.
- `x-cron-secret` cho endpoint generate: secret riêng, chỉ cron-job.org biết. Endpoint idempotent (doc id cố định theo ngày) nên bị gọi lặp cũng vô hại.
- Không log API key, không log URL kèm key. Ảnh gửi sang Space chỉ là URL Cloudinary công khai sẵn có.
- `.env.example` thêm 3 biến mới (giá trị rỗng).

---

## 7. Contract API

### 7.1 Space (FastAPI) — nội bộ, chỉ NestJS gọi

```
GET  /health                      → { "status":"ok", "modelVersion":"v0" }

POST /verify        (X-API-Key)
     { "imageUrl":"https://res.cloudinary.com/.../w_224,h_224,c_fill,f_jpg/abc",
       "targetClass":"cup" }
     → { "matched":true, "score":0.87, "threshold":0.35,
         "scores":{"cup":0.87,"bottle":0.04,...},        // đủ 12 — để log & debug
         "modelVersion":"v0", "latencyMs":62 }

POST /generate      (X-API-Key)
     { "classes":["cup",...12], "avoid":["book","chair","clock"] }
     → { "targetClass":"bottle", "content":"Chụp một chai nước trên bàn của bạn" }
```

Prompt LLM (cố định trong `app.py`): chọn 1 lớp ngoài `avoid`, viết 1 câu tiếng Việt ≤60 ký tự bắt đầu bằng "Chụp", trả JSON đúng schema. Server **validate lại**: `targetClass` phải thuộc 12 lớp, `content` không rỗng/quá dài — sai bất kỳ điều gì → dùng fallback template, log warning.

### 7.2 Server (NestJS) — thay đổi contract với client

| Endpoint | Thay đổi | Breaking? |
|---|---|---|
| `GET /quests/today` | list `quests` có thể thêm phần tử thứ 3 `type:'AI_CHALLENGE'`; mỗi quest thêm field mới `targetClass?` | ✅ **ĐÃ XÁC NHẬN an toàn** (khảo sát app 2026-08-14): `TodayQuestDto.type` phía Kotlin là `String` thuần (không enum, không TypeAdapter) và màn quest render động theo `quests.size` — app cũ hiển thị quest thứ 3 bình thường, chỉ sai icon (👋 thay vì 📸) |
| `POST /moments` (create) | response (trong `MomentDto`) thêm field **mới** `aiQuest?: { result:'MATCHED'\|'NOT_MATCHED'\|'SKIPPED', score?:number, questContent?:string }` | ✅ an toàn (Gson bỏ qua field lạ; app hiện thậm chí không đọc body response này — xem 9.1) |
| `POST /quests/ai/generate` **[MỚI]** | guard header `x-cron-secret`; sinh/ghi quest AI của ngày; idempotent | ✅ endpoint mới |

`TodayQuestsResult` giữ nguyên `rewardAstrite` (của mốc 2/2). Quest AI thể hiện hoàn thành qua chính phần tử trong `quests` (completed=true) — app đọc như 2 quest cũ.

### 7.3 Luồng thưởng +30 (mirror pattern chống double-credit hiện có)

```
verify khớp → repo.completeUserQuest(date, uid, 'AI_CHALLENGE')   // atomic, trả isFirstTime
  isFirstTime=false → thôi (đã thưởng rồi — 2 bài đăng cùng lúc chỉ 1 lần +30)
  isFirstTime=true  → astrite.credit(uid, QUEST_AI_ASTRITE, 'AI_QUEST_REWARD', date)
                       credit FAIL → xóa userQuest doc vừa tạo (mirror deleteDailyReward)
                                     để lần đăng sau thử lại — user không mất thưởng
```

KHÔNG đụng `maybeGiveDailyReward` — logic 60 Astrite của 2/2 giữ nguyên từng dòng.

---

## 8. Firestore & constants

### 8.1 Firestore

| Doc | Field | Ghi chú |
|---|---|---|
| `dailyQuests/{date}_AI_CHALLENGE` | như DailyQuest hiện tại + `targetClass`, `source:'LLM'\|'FALLBACK'`, `generatedAt` | id cố định → cron gọi lặp idempotent |
| `userQuests/{date}_{uid}_AI_CHALLENGE` | như UserQuest hiện tại + `momentId`, `aiScore`, `modelVersion` | ảnh nào hoàn thành quest — phục vụ audit + số liệu báo cáo |
| `aiVerifications/{id}` **[collection MỚI]** | `uid, momentId, date, targetClass, scores(12), matched, modelVersion, latencyMs, createdAt` | log MỌI lần verify (kể cả trượt) — nguồn số liệu "accuracy thực tế trên production" cho báo cáo, và để debug ngưỡng. Ghi best-effort như adminLogs |

### 8.2 `common/constants.ts`

```ts
export const DAILY_QUESTS_PER_DAY = 3;      // 2 cố định + 1 AI (nâng từ 2 theo thiết kế gốc — xem CLAUDE.md mục 14)
export const QUEST_AI_ASTRITE = 30;          // thưởng riêng khi xong quest AI (chốt 2026-08-14; 60 của 2/2 GIỮ NGUYÊN)
export const AI_QUEST_CLASSES = ['cup','bottle','book','chair','potted plant','laptop',
  'keyboard','backpack','clock','umbrella','bicycle','motorcycle'] as const; // 12 lớp — mục 2
export const AI_QUEST_AVOID_RECENT = 3;      // không lặp target của 3 ngày gần nhất
export const AI_VERIFY_TIMEOUT_MS = 3000;    // quá → SKIPPED, không giữ chân response đăng bài
// Collections: thêm AI_VERIFICATIONS: 'aiVerifications'
```

`quest.entity.ts`: `QuestType = 'LOGIN' | 'POST_MOMENT' | 'AI_CHALLENGE'`; sửa comment "Khong can AI xac minh…" (đã có AI nhưng vẫn 1 trạng thái — lý do ghi ở mục 0). Template fallback đặt cạnh entity: `AI_QUEST_TEMPLATES: Record<className, string[]>` — mỗi lớp 3–5 câu viết sẵn (viết ở M0).

### 8.3 Module mới phía server

`src/ai/` — `ai.module.ts` + `ai.service.ts` (HTTP client gọi Space: verify/generate/health, timeout, `enabled` flag) — **không có repository** (không đụng Firestore); theo đúng khung mục 3 CLAUDE.md, phần ghi Firestore nằm ở `quests.repository`/collection mới qua repo tương ứng. `quests` thêm: method generate (cron endpoint + fallback), method verifyAndComplete (hook từ moments). `moments.service.create` thêm **1 lời gọi try/catch** sau registerMomentPosted.

---

## 9. Thay đổi phía app Android

> ✅ Đã khảo sát code app 2026-08-14 — kết luận: **app cũ KHÔNG crash với quest type mới**, khối lượng sửa phía Android rất nhỏ (~3 điểm chạm).

Hiện trạng liên quan (bằng chứng từ code):
- `TodayQuestDto.type` là **`String`** (`core/network/dto/QuestDtos.kt`) — không enum, không TypeAdapter tùy biến (`NetworkModule.kt` dùng Gson mặc định) → type lạ parse bình thường.
- `DailyQuestScreen.kt` render **động** theo `quests.size` (items + bộ đếm `completedCount/quests.size` đều lấy từ list) — không hardcode số 2.
- Icon quest là if/else nhị phân tại `DailyQuestScreen.kt:271`: `if (type == "POST_MOMENT") "📸" else "👋"` → `AI_CHALLENGE` rơi vào 👋 (chỉ sai cosmetic).
- `PostViewModel.submitPhoto` (dòng ~95–102) hiện **gọi createMoment rồi bỏ qua body response** → muốn có toast phải bắt đầu đọc response.
- Số Astrite trong `RewardBanner` không hardcode — render đúng số server gửi.

### 9.1 Việc phải làm (nhỏ)

1. **Icon**: đổi if/else tại `DailyQuestScreen.kt:271` thành `when (quest.type)` — `POST_MOMENT` 📸 · `AI_CHALLENGE` 🤖 (hoặc 🎯) · else 👋.
2. **DTO**: thêm `val aiQuest: AiQuestResultDto? = null` vào `MomentDto` (nullable + default — đúng khuyến cáo an toàn Gson/Unsafe; `MomentDto` cũng dùng cho feed nên field này sẽ là null ở đó, vô hại) + data class `AiQuestResultDto(result, score, questContent)`.
3. **Toast kết quả**: `PostViewModel.submitPhoto` đọc `aiQuest` từ response createMoment — `MATCHED` → "🎉 Challenge complete! +30 Astrite", `NOT_MATCHED` → "Photo doesn't match today's challenge — try again!", `SKIPPED`/null → im lặng. (Chuỗi UI **tiếng Anh** theo convention app — khớp các chuỗi sẵn có "Done today"/"Daily quests complete!" trong màn quest.)
4. Field `targetClass` trong quest DTO: bỏ qua cũng được (content đã đủ hiển thị) — không cần thêm.

### 9.2 Không phải làm

- ❌ Màn camera riêng cho quest, màn kết quả, upload đường mới — không tồn tại trong thiết kế này.
- ❌ Trạng thái quest mới — vẫn chỉ completed true/false.
- ❌ Không cần chặn gallery: luồng đăng moment vốn **chỉ đi từ CameraX** (route `edit_media` bắt buộc `mediaPath` từ callback `onPhotoTaken`/`onVideoTaken`; `GetContent()` chỉ dùng cho avatar + ảnh chat) — xem mục 10.

### 9.3 Thứ tự deploy — không còn ràng buộc cứng

App cũ gặp quest thứ 3 chỉ bị **sai icon** và **không có toast** (vì không đọc field mới) — không crash, không mất chức năng. Vẫn nên theo thứ tự: (1) server deploy với env AI rỗng → (2) app bản mới → (3) điền env; nhưng nếu lỡ bật env trước khi app cập nhật thì hậu quả chỉ là cosmetic, không phải sự cố.

---

## 10. Chống gian lận (mục báo cáo ít đồ án nào có)

| Cách gian lận | Chặn được? | Đối sách |
|---|---|---|
| Chọn ảnh có sẵn từ gallery | ✅ **đã xác nhận trong code** (2026-08-14) | Luồng đăng moment chỉ nhận ảnh từ callback CameraX (`CameraScreen.kt` → route `edit_media` bắt buộc `mediaPath`); photo picker `GetContent()` chỉ tồn tại ở avatar/chat, không dẫn vào moment |
| Chụp lại màn hình máy khác đang hiển thị cái cốc | ❌ v1 | **Nêu thành limitation trong báo cáo** + hướng xử lý (phát hiện moiré, phân tích phổ tần số) — không làm ở DATN |
| Đăng lặp cùng một cảnh nhiều ngày | ⚠️ | pHash ảnh submission, so trùng với chính user 7 ngày gần nhất — **nice-to-have M8**, không cam kết |
| Chi phí gian lận tối đa | — | 30 Astrite/ngày < 1/5 lần quay gacha → động cơ gian lận thấp bằng thiết kế kinh tế |

---

## 11. Lộ trình

> Nguyên tắc thứ tự: **end-to-end chạy được sớm nhất có thể bằng fallback template (M3), LLM là tầng nâng cấp gắn sau (M4)** — đúng tinh thần "fallback luôn tồn tại".

| Phase | Việc | Ước lượng | Ra được gì |
|---|---|---|---|
| **M0** | Chốt 12 lớp (xong — mục 2) · viết 12×4 template fallback · tạo tài khoản HF + cron-job.org · dựng notebook khung | 0,5 ngày | vocab + template |
| **M1** | Pipeline FiftyOne COCO → cache embedding → **train head v0** → eval COCO-val, PR curve, `thresholds.json` | 1–2 ngày | model v0 ONNX + ngưỡng |
| **M2** | HF Space: FastAPI `/health` + `/verify` (ONNX int8), API key, keep-warm cron | 1 ngày | Space verify chạy thật |
| **M3** | Server: `ai/` module, quest AI **sinh bằng template**, hook verify trong moments, +30 Astrite (7.3), env fail-safe, Swagger, unit test | 1–2 ngày | **end-to-end chạy được** (app cũ chưa thấy gì vì env tắt) |
| **M4** | Space: `/generate` (llama-cpp + Qwen2.5-1.5B GGUF Q4) · server: cron endpoint + validate + fallback | 1 ngày | quest do LLM viết |
| **M5** | App Android: enum + quest thứ 3 + toast (mục 9) · bật env | 1 ngày | user thấy tính năng |
| **M6** | Chụp **Snapget-12** · fine-tune **v1** · **ablation OWL-ViT** · chốt ngưỡng cuối · chạy lại số liệu | 2–3 ngày | toàn bộ số liệu báo cáo |
| **M7** | Trước bảo vệ **3–5 ngày**: bật Render Starter $7, chạy thử đúng cấu hình demo, xem trước quest ngày demo để chuẩn bị vật thể | 0,5 ngày | demo không rủi ro |
| M8 | (nice-to-have) pHash chống trùng, thêm lớp cat/dog, trang admin xem `aiVerifications` | — | — |

Tổng thi công chính (M0–M7): **~8–11 ngày công**.

---

## 12. Chi phí & hosting

| Giai đoạn | Hạng mục | Tiền |
|---|---|---|
| Build | Colab/Kaggle GPU (train) · HF Space CPU (serve) · cron-job.org · Render free | **0đ** |
| Demo | **Render Starter $7/tháng × 1 tháng** — hết sleep 15 phút, cold start biến mất | **$7** |
| Demo | HF Space: **giữ free** — ping 10 phút/lần thì không bao giờ ngủ (sleep chỉ sau 48h im lặng); không cần mua CPU nâng cấp vì verify ~60ms là đủ nhanh | 0đ |

Khớp ngân sách 5–7$ user đã chốt. ⚠️ Vận hành: **bật gói trả phí + chạy thử trước bảo vệ 3–5 ngày** (đổi tier có thể restart service, lộ vấn đề cấu hình mà free tier che mất); giữ nguyên cron ping như lớp bảo hiểm; **không push code lên Space sát giờ demo** (push = rebuild vài phút).

---

## 13. Rủi ro & đối sách

| # | Rủi ro | Đối sách |
|---|---|---|
| 13.1 | Domain shift: COCO (ảnh Flickr ngang, Tây) vs ảnh Snapget (dọc, VN, trong nhà) → accuracy thật thấp hơn số trên COCO-val | Đo tách bạch bằng Snapget-12 (M6); augmentation crop dọc 3:4; fine-tune v1; nếu vẫn kém một lớp cụ thể → bỏ lớp đó khỏi rotation (sửa `AI_QUEST_CLASSES`, không cần retrain) |
| 13.2 | LLM tiếng Việt lủng củng / JSON hỏng | Validate + fallback template; câu template do người viết nên chất lượng sàn luôn ổn; demo có thể diễn ra hoàn toàn trên fallback mà không ai nhận ra |
| 13.3 | Space chết giữa demo | Verify SKIPPED không phá đăng bài; quest ngày demo đã biết trước (sinh 00:05) — nếu Space chết vẫn demo được phần quest hiển thị + kể kiến trúc; Render đã paid nên server chính không sao |
| 13.4 | `POST /moments` chậm thêm ~1–2s vì verify sync | Timeout cứng 3s → SKIPPED; chỉ verify khi user CHƯA xong quest AI hôm đó (đa số request không verify gì); đo p95 ghi vào GUIDE |
| 13.5 | ~~App cũ crash vì enum `AI_CHALLENGE` lạ~~ **ĐÃ LOẠI TRỪ** — khảo sát 2026-08-14: type phía Kotlin là String, render động, không TypeAdapter | Còn lại chỉ cosmetic (icon 👋) — xử lý bằng thứ tự deploy 9.3 |
| 13.6 | Ảnh Cloudinary bị xóa trước khi verify kịp (user xóa moment ngay) | Verify nằm ngay trong request tạo moment — cửa sổ ~2s, chấp nhận; verify fail → SKIPPED |
| 13.7 | Double-credit +30 khi 2 bài đăng đồng thời cùng match | `completeUserQuest` atomic isFirstTime (7.3) — pattern đã dùng cho 60 Astrite, có unit test sẵn |
| 13.8 | Colab free bị giới hạn GPU giữa chừng | v0 train từ cache embedding chỉ cần CPU; v1 mới cần GPU — Kaggle (30h/tuần) làm phương án 2 |

---

## 14. Nội dung cho báo cáo DATN (chương AI)

1. **Bài toán**: xác minh ảnh chứa vật thể — multi-label, vì sao không dùng softmax.
2. **Transfer learning**: vì sao ImageNet là feature *đúng việc* ở đây (nhận diện vật thể) — kèm phản đề: cùng kỹ thuật này sẽ *sai việc* nếu áp cho bài NSFW → thể hiện hiểu bản chất, không áp công thức.
3. **Dữ liệu**: COCO subset, multi-hot, imbalance + pos_weight, negative sampling; tập test tự chụp Snapget-12 và khái niệm domain shift.
4. **Thực nghiệm**: v0 vs v1 (giá trị của fine-tuning); **ablation vs OWL-ViT zero-shot** (model nhỏ chuyên biệt vs model to tổng quát — kèm latency); PR curve + chọn ngưỡng per-class.
5. **Policy theo chi phí sai lệch**: thiên recall vì false-reject đắt hơn false-accept — quyết định ngưỡng là quyết định *sản phẩm*, không phải chỉ số ML.
6. **MLOps sinh viên**: ONNX int8, versioning model + ngưỡng đi cùng nhau, keep-warm, fail-safe env, log `aiVerifications` để đo accuracy production.
7. **Giới hạn & hướng phát triển**: chống gian lận chụp-lại-màn-hình, thêm lớp, active learning từ log verify.

---

## 15. Câu hỏi còn mở

| # | Câu hỏi | Hạn chốt |
|---|---|---|

| ~~15.2~~ | ~~Quest AI có hiện cho user chưa cập nhật app không~~ — **ĐÃ TRẢ LỜI 2026-08-14**: có hiện, an toàn, chỉ sai icon (mục 9) | ✅ xong |
| 15.3 | Trang admin xem log `aiVerifications` (bảng + ảnh + score) — làm ở M8 hay bỏ | trước M6 |

---

## 16. Changelog

| Ngày | Nội dung |
|---|---|
| 2026-08-14 | Tạo plan. Chốt: thay hướng NSFW (xóa MODERATION_PLAN.md) bằng AI quest; luồng hook-vào-đăng-bài; thưởng 2/2 giữ 60 + AI quest +30 riêng; 12 lớp vật thể; MobileNetV3-Small + head multi-label, COCO; LLM Qwen2.5-1.5B GGUF sinh quest offline + fallback template; HF Space free + Render Starter $7 lúc demo |
| 2026-08-14 | Khảo sát code app Android: xác nhận type quest phía Kotlin là String render động (không crash với type mới), luồng moment chỉ đi từ CameraX (chặn gallery sẵn), app hiện bỏ qua response createMoment. Cập nhật mục 7.2, 9, 10, 13.5; đóng câu hỏi 15.2 |

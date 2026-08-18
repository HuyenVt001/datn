# QUEST_AI_PLAN.md — AI Daily Quest: sinh quest + xác minh ảnh

> **Trạng thái: 🟡 ĐANG THI CÔNG — code M0–M5 XONG (2026-08-15), còn việc vận hành/train của user (mục 17) và M6–M7.**
> Đã có: server (`server/src/ai/`, quests `AI_CHALLENGE`, cron endpoint, 243 test pass), AI service (`ml/ai-service/`, 7 smoke test), pipeline train (`ml/scripts/`, `ml/notebooks/snapget12_train.ipynb`), app Android (icon 🎯 + `aiQuest` toast, compile + spotless OK). **Chưa có**: model đã train, service đã deploy, env đã điền — xem **mục 17**.
>
> ⚠️ **2 thay đổi so với bản plan gốc (chốt 2026-08-16, xem Changelog):**
> 1. **Hosting: Google Cloud Run (free tier)** thay Hugging Face Space — HF đã bắt Docker Space phải trả phí, chỉ Static Space còn free. Code không đổi (đọc cổng từ `PORT`); thư mục đổi tên `ml/space/` → `ml/ai-service/`. Model artifact vẫn để trên **HF Model repo** (vẫn miễn phí).
> 2. **Bỏ hẳn nửa "LLM sinh quest"** — AI chỉ làm **xác minh ảnh** (phần tự train, là chương ML của báo cáo). Nội dung quest lấy từ **bộ mẫu 72 câu** (12 lớp × 6 câu, chọn theo seed ngày, tránh lặp vật thể 3 ngày gần nhất) do nhóm biên soạn. Đường LLM (`/generate`, `source:'LLM'`) giữ lại làm điểm cắm sẵn, bật bằng env.
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
| Bộ từ vựng | **Model học 12 lớp, app RA ĐỀ 9 lớp** (mục 2 — user giao Claude quyết 2026-08-14 và tái xác nhận 2026-08-16, tiêu chí: độ chính xác xác minh cao nhất). Bỏ `book`/`backpack`/`keyboard` khỏi vòng quay (2.3). Nội dung quest = **72 câu** (9 × 8) viết để ảnh dễ nhận diện (chụp cận, vật thể ở giữa, ô đang mở…) |
| Model xác minh | **Multi-label classifier**: backbone MobileNetV3-Small pretrained ImageNet (đóng băng ở v0) + head tự train trên COCO subset → ONNX int8 |
| Model sinh quest | ~~LLM nhỏ chạy CPU trên HF Space (Qwen2.5-1.5B GGUF Q4)~~ → **ĐÃ BỎ (chốt 2026-08-16)**: nội dung quest lấy từ **bộ mẫu 72 câu** (`server/src/quests/entities/ai-quest-templates.ts`, 12 lớp × 6 câu, chọn theo seed ngày). Lý do: Cloud Run 512Mi không đủ RAM cho Qwen 1.5B, thêm phụ thuộc/API key chỉ để sinh 1 câu/ngày là không đáng; câu người viết chất lượng ổn định hơn. Kiến trúc giữ nguyên điểm cắm (`source: 'LLM' \| 'FALLBACK'`, route `/generate`) — bật lại bằng env |
| Hosting AI | ~~Hugging Face Spaces free CPU~~ → **Google Cloud Run free tier** (chốt 2026-08-16 — HF thu phí Docker Space): FastAPI + ONNX, `--memory 512Mi --min-instances 0`, cùng project Google với Firebase. Render **không** load model (giữ nguyên 512MB). Artifact model để trên **HF Model repo** (miễn phí), service tự tải lúc khởi động qua env `MODEL_REPO` |
| Server ↔ AI service | NestJS là **cửa ngõ duy nhất** — app không bao giờ gọi AI service trực tiếp. Auth bằng header `X-API-Key` (Secret Manager của Cloud Run + server .env) |
| Verify media | **Chỉ moment PHOTO** (quest ghi rõ "chụp ảnh"); VIDEO/ảnh GIF bỏ qua ở v1 |
| Ngôn ngữ quest | `content` **tiếng Việt có dấu** — theo đúng convention `QUEST_CONTENT` hiện có (quest là *dữ liệu server*, không thuộc rule "UI string tiếng Anh" của APK) |
| Chi phí | Build: **0đ** (Colab/Kaggle GPU free, Cloud Run free tier, HF Model repo free, cron-job.org free, Render free). Demo: **$7 Render Starter** bỏ sleep, bật **trước bảo vệ 3–5 ngày**; Cloud Run vẫn 0đ (2M request/tháng, dùng vài trăm) |
| Fail-safe | Thiếu env AI (`AI_SERVICE_URL`…) → server boot bình thường, **quest AI không sinh, app chạy y hệt hiện tại 2 quest** — theo đúng pattern PayOS/Cloudinary |

---

## 1. Tính năng & luồng end-to-end

### 1.1 Hai nửa AI

| | Sinh quest | Xác minh ảnh |
|---|---|---|
| Model | ~~LLM nhỏ~~ **KHÔNG dùng AI** (chốt 2026-08-16) — bộ mẫu 72 câu người viết | **Tự train** — chương ML của báo cáo, phần AI duy nhất còn lại |
| Chạy khi nào | Lazy khi user đầu tiên mở app trong ngày (hoặc cron sinh trước — tuỳ chọn) | Lúc user đăng moment (online, ~0,5–2s) |
| Latency quan trọng? | Không — 1 lần/ngày, đọc/ghi 1 doc Firestore | Có — nằm trong response POST /moments |
| Hỏng thì sao | Không có gì để hỏng (không gọi ra ngoài) | Bỏ qua verify, quest còn nguyên, đăng bài **không bao giờ fail vì AI** |

### 1.2 Luồng một ngày

```
user đầu tiên mở app trong ngày (hoặc cron tuỳ chọn gọi trước)
           → server chọn từ bộ mẫu 72 câu: lớp seed theo ngày (bỏ 3 target gần nhất) + 1 trong 6 câu của lớp
           → dailyQuests/{date}_AI_CHALLENGE {targetClass:'cup', content:'Chụp một chiếc cốc bên cạnh góc học tập', source:'FALLBACK'}
           → create() ATOMIC: 2 request cùng lúc chỉ tạo 1 doc, không ghi đè
           ▸ Cron POST /quests/ai/generate (x-cron-secret) sinh trước HÔM NAY + NGÀY MAI — nay là TUỲ CHỌN
             (hữu ích để biết trước quest ngày demo; cũng là điểm cắm nếu sau này bật LLM)

user mở app → GET /quests/today → 3 quest (LOGIN tự xong như cũ)

user chụp + đăng moment như MỌI NGÀY (không màn hình mới)
           POST /upload → Cloudinary → POST /moments
           server: tạo moment + streak/quest hook như cũ
           → nếu hôm nay có quest AI & user chưa xong & contentType=PHOTO:
               gọi AI service /verify với URL Cloudinary đã resize 224
               khớp  → completeUserQuest(AI_CHALLENGE) + credit 30 Astrite
                       response kèm aiQuest:{result:'MATCHED', score}  → app toast 🎉
               không → response aiQuest:{result:'NOT_MATCHED', score}  → app toast "Ảnh chưa có cốc, thử lại nhé"
                       BÀI VẪN ĐĂNG BÌNH THƯỜNG, quest chưa tick, đăng bài khác để thử lại
               AI service lỗi/timeout 3s → aiQuest:{result:'SKIPPED'}, đăng bài không ảnh hưởng
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

### 2.3 Quyết định 2026-08-16: model học 12, app chỉ ra đề 9 (Claude quyết theo tiêu chí user giao — chính xác nhất)

| Bỏ khỏi vòng quay | Bằng chứng |
|---|---|
| `book` | Nhãn COCO **nhiễu nhất** trong 80 lớp (kệ sách = hàng chục box li ti); AP detection thấp nhất COCO (~0.1). Model học "sách" = gáy sách trên kệ, không phải cuốn sách cầm tay → domain shift lớn nhất |
| `backpack` | AP COCO thấp thứ 2 (~0.13): ảnh COCO ba lô hầu hết **đang đeo trên lưng người**, bị che phần lớn → model học "người có quai đeo"; ba lô đặt trên bàn là phân bố khác |
| `keyboard` | Đồng xuất hiện với `laptop` gần 100% trong COCO → 2 đầu ra nhiễu chéo; bàn phím rời chụp gần lệch phân bố |

9 lớp giữ (`cup, bottle, chair, potted plant, laptop, clock, umbrella, bicycle, motorcycle`) đều **có class riêng trong ImageNet** (cup, water bottle, folding chair, pot, laptop, wall/analog clock, umbrella, mountain bike, moped) → backbone đóng băng đã tách sẵn, head chỉ học ngưỡng.

Vì sao vẫn train 12: mỗi output của head multi-label độc lập → 3 lớp thừa **không làm 9 lớp kia kém đi**, mà cho số liệu per-class để báo cáo kể *"đo rồi mới loại"* (bảng AP 12 lớp, 3 lớp thấp nhất chính là 3 lớp bị bỏ — nếu số liệu nói khác thì đảo lại, chỉ sửa `AI_QUEST_CLASSES`, không retrain). Code: `AI_MODEL_CLASSES` (12, khớp output model) ⊃ `AI_QUEST_CLASSES` (9, ra đề); AI service nhận bất kỳ lớp nào trong 12.

**Nội dung quest (72 câu = 9 × 8)** — viết theo hướng **làm ảnh dễ nhận diện**, là đòn bẩy accuracy thật ngoài model: "chụp cận cảnh", "đặt giữa khung hình", "thấy rõ cả chậu lẫn lá", "ô **đang mở**", "xe nhìn **từ bên hông**, thấy rõ 2 bánh"; tránh câu kiểu "góc học tập" (vật thể nhỏ, lẫn nhiều thứ). File: `server/src/quests/entities/ai-quest-templates.ts`.

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
                    └──► AI service (FastAPI trên Google Cloud Run, free tier)   [MỚI]
                          ├─ ONNX MobileNetV3+head  → POST /verify      ← phần AI DUY NHẤT
                          ├─ (POST /generate — tắt, điểm cắm LLM sau này)
                          └─ GET /health
                                └─ tải model.onnx/thresholds/meta từ HF Model repo (free) lúc khởi động
cron-job.org ──► GET  <cloud-run-url>/health                        mỗi 10 phút (keep-warm, tránh cold start > 3s)
cron-job.org ──► POST /quests/ai/generate (NestJS, x-cron-secret)   TUỲ CHỌN — sinh trước quest hôm nay + mai
```

- **App không bao giờ biết AI service tồn tại** — giữ nguyên luật "server là cửa ngõ duy nhất".
- Render **không load model nào** → memory profile 512MB không đổi (rủi ro OOM của hướng cũ biến mất).
- Cloud Run `--min-instances 0`: không request thì không tốn gì, nhưng cold start ~2–4s **có thể vượt timeout verify 3s** → giữ ping `/health` 10 phút/lần. Deploy lại (`gcloud run deploy`) mất 2–3 phút — không deploy sát giờ demo.

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

- `X-API-Key` của AI service: sinh ngẫu nhiên ≥32 ký tự, đặt trong **Secret Manager của Cloud Run** (`--set-secrets API_KEY=...`, không hardcode trong `app.py`) + `server/.env`. Service từ chối request thiếu/sai key. Cloud Run bật `--allow-unauthenticated` (vì server gọi bằng API key, không phải IAM token) nhưng vẫn khoá bằng key.
- `x-cron-secret` cho endpoint generate: secret riêng, chỉ cron-job.org biết. Endpoint idempotent (doc id cố định theo ngày) nên bị gọi lặp cũng vô hại.
- Không log API key, không log URL kèm key. Ảnh gửi sang AI service chỉ là URL Cloudinary công khai sẵn có.
- `.env.example` thêm 3 biến mới (giá trị rỗng).

---

## 7. Contract API

### 7.1 AI service (FastAPI trên Cloud Run) — nội bộ, chỉ NestJS gọi

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

⚠️ `/generate` **mặc định TẮT** từ 2026-08-16 (không cài `llama-cpp-python`, `ENABLE_LLM=0`) → trả 503 → server dùng bộ mẫu. Nếu bật lại: prompt cố định trong `app.py` (chọn 1 lớp ngoài `avoid`, 1 câu tiếng Việt bắt đầu bằng "Chụp"), và server **luôn validate lại** (`targetClass` thuộc 12 lớp, `content` không rỗng/quá dài ≤ `AI_QUEST_CONTENT_MAX`=80) — sai bất kỳ điều gì → bộ mẫu + log warning.

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
export const AI_QUEST_CONTENT_MAX = 80;      // (thi công) chặn content quá dài nếu bật lại LLM
export const AI_VERIFY_TIMEOUT_MS = 3000;    // quá → SKIPPED, không giữ chân response đăng bài
export const AI_GENERATE_TIMEOUT_MS = 90000; // (thi công) chỉ dùng khi bật lại LLM
// Collections: thêm AI_VERIFICATIONS: 'aiVerifications'
```

`quest.entity.ts`: `QuestType = 'LOGIN' | 'POST_MOMENT' | 'AI_CHALLENGE'`; sửa comment "Khong can AI xac minh…" (đã có AI nhưng vẫn 1 trạng thái — lý do ghi ở mục 0). Bộ mẫu đặt cạnh entity: `AI_QUEST_TEMPLATES: Record<className, string[]>` — **6 câu/lớp = 72 câu** (nâng từ 4 lên 6 ngày 2026-08-16 khi bộ mẫu thành nguồn CHÍNH thay LLM), kèm `pickFallbackQuest(date, avoid)` chọn theo seed ngày.

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
| **M0** ✅ code | Chốt 12 lớp (mục 2) · **12×4 template fallback** (`server/src/quests/entities/ai-quest-templates.ts`) · notebook khung (`ml/notebooks/`) · ⬜ **user**: tài khoản HF + cron-job.org | 0,5 ngày | vocab + template |
| **M1** ✅ code / ⬜ chạy | Pipeline `ml/scripts/01–05` + `ml/snapget12/` (FiftyOne COCO → cache embedding → head v0 → thresholds → ONNX int8) · ⬜ **user chạy notebook trên Colab** | 1–2 ngày | model v0 ONNX + ngưỡng |
| **M2** ✅ code / ⬜ deploy | AI service `ml/ai-service/app.py` (`/health` + `/verify` ONNX + API key, Dockerfile đọc `PORT`, 7 smoke test) · ⬜ **user deploy Cloud Run + secret + upload model lên HF Model repo** · keep-warm cron | 1 ngày | verify chạy thật |
| **M3** ✅ | Server: `ai/` module, quest AI sinh template (fallback), hook verify trong moments, +30 Astrite (7.3), env fail-safe, Swagger, **36 unit test + 2 e2e** | 1–2 ngày | **end-to-end chạy được** (app cũ chưa thấy gì vì env tắt) |
| ~~**M4**~~ ❌ **BỎ (2026-08-16)** | ~~LLM sinh quest~~ — quest lấy từ bộ mẫu 72 câu. Code đã viết vẫn giữ làm điểm cắm: `/generate` trong service (tắt), `generateAiQuests()` + cron endpoint + `CronSecretGuard` + validate ở server | 0 | — |
| **M5** ✅ code / ⬜ bật env | App Android: icon 🎯 + `AiQuestResultDto` + toast (mục 9) · ⬜ **user**: build APK mới → điền env server | 1 ngày | user thấy tính năng |
| **M6** | Chụp **Snapget-12** · fine-tune **v1** · **ablation OWL-ViT** · chốt ngưỡng cuối · chạy lại số liệu | 2–3 ngày | toàn bộ số liệu báo cáo |
| **M7** | Trước bảo vệ **3–5 ngày**: bật Render Starter $7, chạy thử đúng cấu hình demo, xem trước quest ngày demo để chuẩn bị vật thể | 0,5 ngày | demo không rủi ro |
| M8 | (nice-to-have) pHash chống trùng, thêm lớp cat/dog · ~~trang admin xem `aiVerifications`~~ ✅ đã làm 2026-08-16 | — | — |

Tổng thi công chính (M0–M7): **~8–11 ngày công**.

---

## 12. Chi phí & hosting

| Giai đoạn | Hạng mục | Tiền |
|---|---|---|
| Build | Colab/Kaggle GPU (train) · **Cloud Run free tier** (serve) · HF Model repo (artifact) · cron-job.org · Render free | **0đ** |
| Demo | **Render Starter $7/tháng × 1 tháng** — hết sleep 15 phút, cold start biến mất | **$7** |
| Demo | Cloud Run: **vẫn 0đ** — free tier 2M request + 180k vCPU-giây/tháng, đồ án dùng vài trăm request; ping `/health` 10 phút/lần để tránh cold start (vẫn trong free tier) | 0đ |

Khớp ngân sách 5–7$ user đã chốt. ⚠️ Vận hành: **bật gói trả phí + chạy thử trước bảo vệ 3–5 ngày** (đổi tier có thể restart service, lộ vấn đề cấu hình mà free tier che mất); giữ nguyên cron ping như lớp bảo hiểm; **không push code lên Space sát giờ demo** (push = rebuild vài phút).

---

## 13. Rủi ro & đối sách

| # | Rủi ro | Đối sách |
|---|---|---|
| 13.1 | Domain shift: COCO (ảnh Flickr ngang, Tây) vs ảnh Snapget (dọc, VN, trong nhà) → accuracy thật thấp hơn số trên COCO-val | Đo tách bạch bằng Snapget-12 (M6); augmentation crop dọc 3:4; fine-tune v1; nếu vẫn kém một lớp cụ thể → bỏ lớp đó khỏi rotation (sửa `AI_QUEST_CLASSES`, không cần retrain) |
| 13.2 | ~~LLM tiếng Việt lủng củng / JSON hỏng~~ **LOẠI TRỪ 2026-08-16** — bỏ LLM, quest lấy từ 72 câu người viết | Rủi ro còn lại: quest lặp câu nếu chạy > 1 tháng → thêm câu vào `AI_QUEST_TEMPLATES` (sửa 1 file, không đụng model) |
| 13.3 | AI service chết / cold start giữa demo | Verify SKIPPED không phá đăng bài; quest vẫn hiển thị bình thường (nội dung từ bộ mẫu, không phụ thuộc service) — vẫn demo được phần quest + kể kiến trúc; giữ ping keep-warm 10' để cold start không rơi vào lúc demo; Render đã paid nên server chính không sao |
| 13.4 | `POST /moments` chậm thêm ~1–2s vì verify sync | Timeout cứng 3s → SKIPPED; chỉ verify khi user CHƯA xong quest AI hôm đó (đa số request không verify gì); đo p95 ghi vào GUIDE |
| 13.5 | ~~App cũ crash vì enum `AI_CHALLENGE` lạ~~ **ĐÃ LOẠI TRỪ** — khảo sát 2026-08-14: type phía Kotlin là String, render động, không TypeAdapter | Còn lại chỉ cosmetic (icon 👋) — xử lý bằng thứ tự deploy 9.3 |
| 13.6 | Ảnh Cloudinary bị xóa trước khi verify kịp (user xóa moment ngay) | Verify nằm ngay trong request tạo moment — cửa sổ ~2s, chấp nhận; verify fail → SKIPPED |
| 13.7 | Double-credit +30 khi 2 bài đăng đồng thời cùng match | `completeUserQuest` atomic isFirstTime (7.3) — pattern đã dùng cho 60 Astrite, có unit test sẵn |
| 13.8 | Colab free bị giới hạn GPU giữa chừng | v0 train từ cache embedding chỉ cần CPU; v1 mới cần GPU — Kaggle (30h/tuần) làm phương án 2 |

---

## 14. Nội dung cho báo cáo DATN (chương AI)

0. **Phạm vi actor AI** (nêu ngay đầu chương): AI trong hệ thống đảm nhiệm khâu **xác minh ảnh** — quyết định quest hoàn thành hay chưa; nội dung câu quest lấy từ bộ mẫu do nhóm biên soạn (72 câu, xoay theo ngày). Quyết định 2026-08-16: bỏ LLM sinh quest vì thêm phụ thuộc/RAM chỉ để sinh 1 câu/ngày là không tương xứng; kiến trúc vẫn chừa điểm cắm (`source: 'LLM' | 'FALLBACK'`) — nêu như một ví dụ *cắt phạm vi có chủ đích*.
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
| ~~15.3~~ | ~~Trang admin xem log `aiVerifications`~~ — **ĐÃ LÀM 2026-08-16**: `/ai-verifications` trên admin (thumbnail, điểm/ngưỡng, top-3 lớp, lọc uid/kết quả/ngày) + 2 ô dashboard; endpoint `GET /admin/ai-verifications` | ✅ xong |

---

## 16. Changelog

| Ngày | Nội dung |
|---|---|
| 2026-08-14 | Tạo plan. Chốt: thay hướng NSFW (xóa MODERATION_PLAN.md) bằng AI quest; luồng hook-vào-đăng-bài; thưởng 2/2 giữ 60 + AI quest +30 riêng; 12 lớp vật thể; MobileNetV3-Small + head multi-label, COCO; LLM Qwen2.5-1.5B GGUF sinh quest offline + fallback template; HF Space free + Render Starter $7 lúc demo |
| 2026-08-14 | Khảo sát code app Android: xác nhận type quest phía Kotlin là String render động (không crash với type mới), luồng moment chỉ đi từ CameraX (chặn gallery sẵn), app hiện bỏ qua response createMoment. Cập nhật mục 7.2, 9, 10, 13.5; đóng câu hỏi 15.2 |
| 2026-08-16 | **Code thêm không cần user**: (1) trang admin **AI quest** (`/ai-verifications`) + `GET /admin/ai-verifications` + 2 ô dashboard — đóng 15.3; entity log thêm `mediaUrl` (thumbnail); (2) `ml/ai-service/dev_fake_model.py` — model ONNX giả để test end-to-end local (bước D0b); (3) `ai-quest-templates.spec.ts` khoá chất lượng 72 câu. Server **255 test / 13 suite**, admin build OK |
| 2026-08-16 | **Vocab & nội dung quest (Claude quyết, user giao)**: model học 12 lớp nhưng app **ra đề 9 lớp** — bỏ `book`/`backpack`/`keyboard` (mục 2.3); `AI_QUEST_CLASSES`=9, thêm `AI_MODEL_CLASSES`=12; bộ mẫu viết lại **72 câu = 9 × 8**, câu chữ hướng ảnh dễ nhận diện. 243 test pass |
| 2026-08-16 | **2 quyết định của user làm gọn phạm vi**: (1) **Bỏ hẳn LLM sinh quest** — AI chỉ xác minh ảnh; quest lấy từ bộ mẫu nâng lên **72 câu** (12 lớp × 6). `/generate`, `generateAiQuests()`, cron endpoint giữ nguyên làm điểm cắm (bật bằng env), cron E2 thành tuỳ chọn. Báo cáo thêm mục 0 chương AI nói rõ phạm vi actor AI. (2) **Hosting đổi HF Space → Google Cloud Run free tier** (HF thu phí Docker Space từ 2026): `ml/space/` → `ml/ai-service/`, Dockerfile đọc `PORT` (chạy được cả HF/Render/VPS), `requirements.txt` bỏ `llama-cpp-python` mặc định, service tự tải model từ **HF Model repo** (vẫn free) qua env `MODEL_REPO`. Script 05 upload sang Model repo thay Space. Mục 17 viết lại bước B (gcloud CLI, Secret Manager, `gcloud run deploy --source .`) + C5b + D1 + E |
| 2026-08-15 | **Thi công M0–M5 (code)**: server `ai/` + quests `AI_CHALLENGE` + cron endpoint + `CronSecretGuard` + 36 unit test/2 e2e (243/12 pass); HF Space `ml/space/` (FastAPI, ONNX + llama-cpp, Dockerfile, 7 smoke test); pipeline train `ml/snapget12/` + `ml/scripts/01–08` + notebook Colab; app Android icon 🎯 + `aiQuest` toast (compile + spotless OK). Khác plan: cron sinh **hôm nay + ngày mai** (đóng cửa sổ user mở app trước cron); `AI_QUEST_CONTENT_MAX=80` (plan nói ≤60 cho LLM — server nới 80 để template dài hơn không bị fallback); Space có thêm `llmLoading/llmError` trong `/health`. Thêm **mục 17 checklist việc user** (tài khoản HF/cron, tạo Space, chạy notebook, điền env, tạo cron, M6). Docs đồng bộ: `server/GUIDE.md`, `server/.claude/CLAUDE.md`, `Snapget/.claude/GUIDE.md` + `CLAUDE.md`, `SECURITY.md` mục 18, `ml/README.md` |

---

## 17. ✅ CHECKLIST VIỆC USER PHẢI TỰ LÀM (code đã xong — đây là phần vận hành/train)

> Thứ tự bên dưới là thứ tự khuyến nghị. Mỗi bước ghi rõ **làm ở đâu, làm gì, kiểm tra thế nào**. Ước lượng tổng: **1 buổi** cho A–D (không tính thời gian tải COCO), M6 thêm 2–3 ngày.

### A. Tài khoản & bí mật (10 phút)

| # | Việc | Cách làm | Kết quả |
|---|---|---|---|
| A1 | Tạo tài khoản **Hugging Face** (free) | https://huggingface.co/join → xác nhận email | user name HF, ví dụ `hoangngn` |
| A2 | Tạo **HF Access Token** quyền **write** | Settings → Access Tokens → *New token*. **Token name = nhãn tuỳ ý** (vd `snapget-colab`), HF không dùng tên này để xác thực; **quan trọng là loại: chọn `Write`** (hoặc *Fine-grained* + tick `Write access to contents/settings of repos`). Chọn *Read* → bước C5 upload model fail 403 | token `hf_...` (HF **chỉ hiện 1 lần**) — dán vào `login()` ô C5 notebook, **không commit / không dán vào chat** |
| A3 | Sinh **2 chuỗi bí mật ngẫu nhiên** ≥32 ký tự (bạn TỰ BỊA như đặt password — không trang nào cấp) | PowerShell, chạy **2 lần**: `([guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N'))` (64 ký tự hex) hoặc `-join ((48..57)+(65..90)+(97..122) \| Get-Random -Count 40 \| % {[char]$_})`. Lưu tạm vào Notepad/password manager tới hết bước E | **Chuỗi 1** = giá trị của secret `API_KEY` trên Space (B3) **và** env `AI_SERVICE_API_KEY` ở server (D1) — phải GIỐNG HỆT nhau. **Chuỗi 2** = env `CRON_SECRET` ở server (D1) **và** header `x-cron-secret` của job cron (E1). Lệch 1 ký tự → 401, quest AI không bao giờ tick |
| A4 | Tạo tài khoản **cron-job.org** (free) | https://cron-job.org/en/signup/ | dùng ở bước E |

> 🔑 **Vì sao 2 chuỗi khác nhau (A3)**: Space và endpoint cron là 2 cửa mở ra Internet, mỗi cửa khoá bằng 1 chuỗi; lộ chuỗi của HF không kéo theo cửa cron. Khi dán: không dính dấu cách/xuống dòng, trong `.env` **không bọc nháy** (`AI_SERVICE_API_KEY=abc123`), luôn copy-paste chứ đừng gõ tay. Đổi chuỗi về sau phải đổi **đồng thời cả 2 nơi** (Space rebuild 1–3 phút — không làm sát giờ demo).

### B. Deploy AI service lên Google Cloud Run (20 phút, chưa cần model)

> 🐳 **KHÔNG cần cài Docker.** `gcloud` nén thư mục gửi lên **Cloud Build**, build image trên cloud rồi deploy. Máy bạn chỉ cần `gcloud` CLI.
> ❌ **Không dùng Hugging Face Space nữa** — HF đã bắt Docker/Gradio Space phải trả phí (chỉ Static Space còn free, mà Static không chạy được FastAPI). HF vẫn dùng, nhưng chỉ để **chứa file model** (Model repo — vẫn miễn phí).

| # | Việc | Cách làm | Kiểm tra |
|---|---|---|---|
| B1 | Cài **gcloud CLI** | https://cloud.google.com/sdk/docs/install → tải bản Windows, cài xong mở PowerShell mới | `gcloud --version` in ra phiên bản |
| B2 | Đăng nhập + chọn project | `gcloud auth login` (mở trình duyệt) → `gcloud config set project snapget-d8693` (dùng chung project Firebase; `gcloud projects list` để xem id chính xác) | `gcloud config list` hiện đúng account + project |
| B3 | Bật billing cho project | Console → *Billing* → gắn thẻ. **Free tier Cloud Run vẫn miễn phí** (2M request/tháng); có thể đặt *Budget alert* $1 cho yên tâm | Console → Cloud Run mở được, không báo "billing required" |
| B4 | Bật 3 API | `gcloud services enable run.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com` | lệnh chạy xong không lỗi (lần đầu ~1 phút) |
| B5 | Tạo secret chứa API key | `gcloud secrets create snapget-ai-api-key --replication-policy=automatic` rồi `echo -n "<Chuỗi 1 ở A3>" \| gcloud secrets versions add snapget-ai-api-key --data-file=-` (PowerShell: `"<Chuỗi 1>" \| gcloud secrets versions add snapget-ai-api-key --data-file=-`) | `gcloud secrets versions list snapget-ai-api-key` có 1 version |
| B6 | Cho Cloud Run đọc secret | `gcloud secrets add-iam-policy-binding snapget-ai-api-key --member="serviceAccount:$(gcloud projects describe snapget-d8693 --format='value(projectNumber)')-compute@developer.gserviceaccount.com" --role=roles/secretmanager.secretAccessor` | in ra `Updated IAM policy` |
| B7 | **Deploy** — ⚠️ **PHẢI `cd d:\snap\datn\ml\ai-service` trước** (`--source .` = nén thư mục hiện tại; đứng ở `C:\WINDOWS\System32` là gcloud crash `Permission denied: catroot2\edb.log` và suýt upload cả System32). Log đúng phải ghi "Building using Dockerfile" | `gcloud run deploy snapget-ai --source . --region asia-southeast1 --allow-unauthenticated --memory 512Mi --cpu 1 --min-instances 0 --max-instances 3 --set-secrets API_KEY=snapget-ai-api-key:latest` | Build ~3–5 phút, cuối cùng in **Service URL** `https://snapget-ai-xxxx.a.run.app` → đây là `AI_SERVICE_URL` (bước D1) |
| B8 | Kiểm tra sống | Mở `<Service URL>/health` trên trình duyệt | `{"status":"ok","modelVersion":"none","verifierReady":false,...}` — `verifierReady:false` là ĐÚNG vì chưa upload model (làm ở C5) |

> 💡 `--allow-unauthenticated` là **bắt buộc** (server NestJS gọi bằng `X-API-Key`, không phải IAM token) nhưng service vẫn khoá: thiếu/sai key → 401. Chỉ `/health` là public — nó không trả dữ liệu người dùng.
> 💡 Deploy lại sau khi sửa code: chạy lại đúng lệnh B7. Xem log: Console → Cloud Run → `snapget-ai` → *Logs*, hoặc `gcloud run services logs read snapget-ai --region asia-southeast1`.

### C. Train model v0 trên Colab (1 buổi, phần lớn là chờ tải)

| # | Việc | Cách làm | Kiểm tra |
|---|---|---|---|
| C1 | **Push code lên GitHub** (nếu chưa) — notebook `git clone` repo này | commit thư mục `ml/` + server/app (không có binary). Repo nên **public** để Colab clone không cần token (repo không chứa secret — `.env`, service account, model đều gitignore); private thì URL clone dạng `https://<gh-user>:<PAT>@github.com/<gh-user>/<repo>.git` | repo có `ml/notebooks/snapget12_train.ipynb` |
| C2 | Mở notebook trên Colab | https://colab.research.google.com → *GitHub* → dán URL repo → chọn `ml/notebooks/snapget12_train.ipynb` → **Runtime → Change runtime type → T4 GPU** | ô 0b in `True` (CUDA) |
| C3 | Sửa 2 chỗ trong notebook | ô 0b: URL repo GitHub (vd `https://github.com/HuyenVt001/datn.git`); ô bước 5: `MODEL_REPO = '<hf-user>/snapget-ai-model'` — **`<hf-user>` = tên tài khoản Hugging Face** (A1, xem góc phải trên huggingface.co), *không phải* GitHub; `snapget-ai-model` tự đặt, script tự tạo repo. Cùng chuỗi này dùng cho Cloud Run ở C5b | — |
| C4 | Chạy ô 0a → 0c → **1** (tải COCO, 20–40 phút) → **2** (cache embedding ~10 phút) → **3** (train head ~2 phút) → **4** (ngưỡng + PR curve) | chạy tuần tự, đọc log; ô 4 hiện hình PR curve 12 lớp + bảng P/R/F1 | `artifacts/v0/thresholds.json` + `metrics_test.json` trên Drive |
| C5 | Chạy ô **5** (export ONNX int8 + upload **HF Model repo**) | `login()` dán token A2 → script tự tạo repo `<user>/snapget-ai-model` (miễn phí) + upload 3 file | Repo HF có `model.onnx`, `thresholds.json`, `model_meta.json` |
| C5b | Cho Cloud Run nạp model | Chạy lại B7 kèm `--set-env-vars MODEL_REPO=<user>/snapget-ai-model` (lần sau đổi model chỉ cần `gcloud run services update snapget-ai --region asia-southeast1 --update-env-vars MODEL_RELOAD=$(date +%s)`) | `<Service URL>/health` → **`modelVersion:"v0"`, `verifierReady:true`** |
| C6 | Test verify thật (PowerShell) | `Invoke-RestMethod -Method Post -Uri <Service URL>/verify -Headers @{'X-API-Key'='<Chuỗi 1>'} -ContentType 'application/json' -Body '{"imageUrl":"https://upload.wikimedia.org/wikipedia/commons/thumb/1/1a/Cup_of_coffee.jpg/320px-Cup_of_coffee.jpg","targetClass":"cup"}'` | `matched: True`, `score` cao, `latencyMs` vài chục ms |

> Hết disk Colab ở ô 1 → giảm `--max-per-class 3000 --negatives 8000`. Ghi lại **mAP/macro-F1 test** từ ô 4 cho báo cáo.
>
> 🔌 **Colab bị ngắt (Runtime disconnected)**: mọi thứ trên `/content` mất, kể cả ảnh COCO đang tải (FiftyOne để ảnh ở đĩa tạm — cố ý, 7–9GB không vừa Drive free). Chạy lại **0a → 0b → 0c** (3–5 phút) rồi **ô 1** (tải lại 20–40'); `data/`+`artifacts/` là symlink Drive nên manifest/embedding/artifact đã có **còn nguyên**. Sau khi **ô 2 xong** (`embeddings.npz` lên Drive) thì v0 không bao giờ cần COCO nữa — chỉ M6 (v1) mới tải lại 1 lần. Màn hình laptop tắt **không** ngắt Colab; **máy ngủ** thì có → tắt sleep khi cắm điện, cứ 20–30' click vào tab một lần, chạy ô 1+2 liền nhau (Runtime → *Run after*). Hết GPU quota vẫn chạy được ô 1–2 trên CPU (ô 2 ~30–40').

### D. Bật tính năng trên server (10 phút) — theo thứ tự deploy mục 9.3

| # | Việc | Cách làm | Kiểm tra |
|---|---|---|---|
| D0 | **Build & cài APK mới trước** (app đã có toast + icon) | Android Studio → Build APK → cài máy test | app chạy như cũ (env chưa bật nên vẫn 2 quest) |
| D0b | (Tuỳ chọn, **không cần Colab/Cloud Run**) test toàn luồng local bằng **model giả** | `cd ml/ai-service` → `python dev_fake_model.py` → `$env:API_KEY='dev-key'; python -m uvicorn app:app --port 8080` → `server/.env`: `AI_SERVICE_URL=http://localhost:8080`, `AI_SERVICE_API_KEY=dev-key` → `npm run start:dev` → app trỏ server local (GUIDE app mục 8) | Mở app thấy 3 quest; đăng **ảnh tối** khi quest là cốc/chai/ghế → toast +30; ảnh sáng → "doesn't match". Admin → menu **AI quest** thấy log kèm thumbnail. Xong thì xoá `AI_SERVICE_*` khỏi `.env` |
| D1 | Điền env | **Local** `server/.env`: `AI_SERVICE_URL=<Service URL của Cloud Run>` · `AI_SERVICE_API_KEY=<Chuỗi 1>` · `CRON_SECRET=<Chuỗi 2>` (chuỗi 2 chỉ cần nếu dùng cron tuỳ chọn E1). **Render**: Dashboard → service → *Environment* → thêm biến y hệt → *Save* (Render tự redeploy) | log boot có dòng `AI service da cau hinh — xac minh anh quest AI BAT.` |
| D2 | (Tuỳ chọn) sinh trước quest | `Invoke-RestMethod -Method Post -Uri https://datn-8810.onrender.com/api/quests/ai/generate -Headers @{'x-cron-secret'='<Chuỗi 2>'}` | `data` có 2 phần tử (hôm nay + mai), `source: "FALLBACK"`. **Bỏ qua bước này cũng được** — quest tự sinh khi mở app |
| D3 | Mở app → màn quest | thấy **3 quest**, quest thứ 3 icon 🎯 nội dung tiếng Việt | ✅ |
| D4 | Đăng 1 ảnh có vật thể của quest | toast `🎯 Challenge complete! +30 Astrite`, quest tick, số dư +30 (màn gacha); ảnh không có → toast "doesn't match…" bài vẫn lên feed | ✅ — nếu toast không hiện: kiểm tra Firestore `aiVerifications` xem `outcome`/`error` |

### E. Cron (5 phút) — cron-job.org → *Create cronjob*

| # | Job | URL | Cấu hình | Bắt buộc? |
|---|---|---|---|---|
| E1 | **Keep-warm AI service** | `<Service URL>/health` | **mỗi 10 phút**, GET, không header | ✅ **Nên có** — Cloud Run scale về 0, cold start ~2–4s có thể vượt timeout verify 3s ⇒ bài đăng đầu sau lúc rảnh sẽ bị `SKIPPED` |
| E2 | Sinh trước quest AI | `https://datn-8810.onrender.com/api/quests/ai/generate` | mỗi ngày **00:05 UTC** (07:05 VN), method **POST**, *Headers* `x-cron-secret: <Chuỗi 2>`, timeout 30s | ⬜ **Tuỳ chọn** — quest tự sinh lazy khi user đầu tiên mở app. Có job này thì biết trước quest ngày mai (tiện chuẩn bị vật thể lúc demo) |
| E3 | Đánh thức Render | `https://datn-8810.onrender.com/api/health` | mỗi 10 phút | ⬜ Tuỳ chọn — hoặc bật Render Starter $7 ở M7 |

Kiểm tra E2 (nếu dùng): tab *History* → status 201, body `"Da sinh ... quest AI moi."` (gọi lần 2 trong ngày ra `0` mới — idempotent, đúng).

### F. M6 — số liệu báo cáo (2–3 ngày, sau khi A–E chạy)

| # | Việc | Cách làm |
|---|---|---|
| F1 | Chụp tập **Snapget-12** | Mỗi thành viên chụp **bằng app/điện thoại** ~10 ảnh/lớp × 12 lớp + ~30 ảnh negative (không có lớp nào). Sắp vào thư mục `snapget12/<lớp>/…jpg` (tên lớp có dấu cách → gạch dưới: `potted_plant/`), `snapget12/negative/`. Ảnh có 2 vật thể → tên file `cup+book_01.jpg`. Zip → kéo lên Drive `snapget-ml/data/snapget12/` |
| F2 | Đo v0 trên Snapget-12 | notebook ô 6b → so với `metrics_test.json` = **domain shift** (ghi vào báo cáo) |
| F3 | Fine-tune **v1** | ô 6c (~30–60 phút T4) → ô 6d upload nếu v1 tốt hơn trên Snapget-12 (khi upload xong: Space `/health` → `modelVersion:"v1"`) |
| F4 | Ablation OWL-ViT | ô 7 (chậm — có `--max-test 1500`) |
| F5 | Bảng tổng hợp | ô 8 in bảng v0/v1/owlvit × test/snapget12 → chép vào chương AI |

### G. M7 — trước bảo vệ 3–5 ngày

- Bật **Render Starter $7** (Settings → Instance type) → chạy lại D2–D4 đúng cấu hình demo.
- Gọi `GET /api/quests/today` (Swagger) xem trước quest ngày demo → **chuẩn bị vật thể** mang theo.
- **Không `gcloud run deploy`** sát giờ demo (build 3–5 phút). Nếu AI service chết: đăng bài vẫn OK (SKIPPED), quest vẫn hiển thị đúng nội dung (bộ mẫu nằm ở server, không phụ thuộc service).
- Kiểm tra ping keep-warm (E1) còn chạy — cold start là rủi ro thật duy nhất còn lại.

### H. Khi nào cần tôi

- Bất kỳ bước nào trả lỗi → gửi tôi **nguyên văn** (log Cloud Run: Console → Cloud Run → snapget-ai → *Logs*, log Render, body response). Không dán API key/secret vào chat.
- Sau C4 gửi tôi bảng P/R/F1 (hoặc `metrics_test.json`) → tôi review ngưỡng/lớp yếu, quyết có bỏ lớp nào khỏi rotation không (chỉ sửa `AI_QUEST_CLASSES`, không retrain).
- Muốn admin xem log `aiVerifications` (câu hỏi 15.3) → nói tôi làm trang admin (M8).

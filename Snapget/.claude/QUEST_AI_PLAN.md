# QUEST_AI_PLAN.md — AI Daily Quest: xác minh ảnh bằng model tự train

> **Trạng thái (2026-11-28): 🟢 y2-s (9 lớp) ĐÃ DEPLOY lên Cloud Run (`snapget-ai-00011`) kèm model person (ngưỡng tạm 0.4, nhồi trong image); server + admin đã chạy code 7 quest gộp. Bản demo thử nghiệm — việc tuần sau: mục 11 "Việc tiếp theo sau demo".**
> Production hiện chạy **v0** (MobileNetV3 classifier, mAP COCO-test 0.647) trên Cloud Run — vẫn hoạt động, chỉ nhận diện kém. **y1** = YOLO11 detection fine-tune trên COCO + Kaggle + ảnh Google + **tập ảnh Việt Nam tự chụp**; lớp nào được ra đề quest do **số liệu trên ảnh thật** quyết định (AP / confusion matrix / F1).
> File này được **viết lại toàn bộ** ngày 2026-11-23 theo plan y1. Lịch sử v0 (bug dữ liệu, ngưỡng 0.7, int8 phá MobileNet…) giữ ở mục 2 + Changelog vì là nội dung báo cáo.
> Liên quan: [`ml/README.md`](../../ml/README.md) (pipeline) · [`ml/DATASET_SPEC.md`](../../ml/DATASET_SPEC.md) (đặc tả dữ liệu VN — **đọc trước khi đi chụp**) · `server/GUIDE.md` (module `ai`, `quests`) · `SECURITY.md` mục 18.

---

## 0. Quyết định đã chốt

| Hạng mục | Chốt |
|---|---|
| Phạm vi AI | AI = **xác minh ảnh** (quest có hoàn thành hay không). Nội dung câu quest lấy từ **bộ mẫu do nhóm viết** (`server/src/quests/entities/ai-quest-templates.ts`, 8 câu/lớp, chọn theo seed ngày, tránh lặp 3 ngày). LLM sinh quest **đã bỏ** (2026-08-16) — giữ điểm cắm `/generate`, `source: 'LLM' \| 'FALLBACK'` |
| Luồng hoàn thành | **Hook vào đăng bài**: user đăng moment PHOTO như mọi ngày → server gọi AI service xác minh → khớp thì quest tự tick. Không có màn nộp ảnh riêng. UserQuest chỉ 1 trạng thái `COMPLETED` |
| Thưởng | 2 quest cố định vẫn **+60** khi đủ 2/2; quest AI **+30 riêng** (tối đa 90/ngày) |
| Fail-safe | Thiếu env AI → server boot bình thường, quest AI không hiện. AI lỗi/timeout 3s → `SKIPPED`, **đăng bài không bao giờ fail vì AI** |
| Kiến trúc | App → NestJS (cửa ngõ duy nhất) → AI service FastAPI trên **Google Cloud Run** (free tier, 512Mi) ← model tải từ **HF Model repo** (free) lúc khởi động |
| **Model y1** (2026-11-23) | **YOLO11 detection** (Ultralytics, pretrained COCO, fine-tune). YOLO11s chính, YOLO11n so sánh. License **AGPL-3.0** — chấp nhận cho DATN |
| Lớp model học | **Giữ đúng 12 lớp cũ, cùng thứ tự** (`ml/snapget12/classes.py` = `AI_MODEL_CLASSES` ở server) → so sánh trực tiếp v0 ↔ y1, contract không đổi |
| Lớp ra đề quest | **(2026-11-27, user chốt sau khi xem số liệu y1-s)** **7 quest GỘP NHÓM** — ảnh khớp nếu **bất kỳ** lớp nào trong nhóm vượt ngưỡng riêng (tăng tỉ lệ pass, giảm loại nhầm): `bicycle` (bicycle/motorcycle) · `laptop` (laptop/keyboard) · `umbrella` · `chair` · `cup` (cup/bottle) · `book` · `person` (**≥ 2 người**). Bỏ clock, backpack, potted plant. Map ở `AI_QUEST_GROUPS` (server) = `QUEST_GROUPS` (`ml/snapget12/classes.py`). Tiêu chí mục 5.3 vẫn dùng để báo cáo, không còn quyết định danh sách |
| **Model phụ person** (2026-11-27) | Quest `person` ("Snap a photo with your friend") = có ≥ 2 người, **không lọc người lạ/kích thước** (user chốt — ưu tiên ít loại nhầm). Model riêng **YOLO11n COCO gốc, không train**, cắt head còn lớp person; điểm = độ tin cậy của **người thứ 2 sau NMS** (`"imageScore": "nth:2"`). Không nhét vào y1 vì phải gán thêm box người cho ~3.600 ảnh VN. Quest "người + vật" (vd bạn cầm ô) **để sau** — không cần train lại |
| Dữ liệu | COCO 2017 + **Kaggle** + **ảnh tự tải từ Google** + **tập VN tự chụp** (train bổ sung + test) — đặc tả chính xác ở `ml/DATASET_SPEC.md` |
| Nơi train | **Google Colab** (T4 free), notebook `ml/notebooks/snapget_yolo_train.ipynb`, checkpoint lưu Drive, resume khi bị ngắt |
| Serve | ONNX **fp32** (không int8), onnxruntime CPU, `model_meta.json` `"arch": "yolo"`. Service vẫn chạy được model v0 (`arch` mặc định `classifier`) → **rollback = upload lại 3 file v0** |
| Ảnh gửi verify | Cloudinary **`w_640,h_640,c_limit`** (giữ tỉ lệ, không cắt) — thay `w_224,h_224,c_fill` |
| Chi phí | Train 0đ (Colab free) · serve 0đ (Cloud Run free tier, HF Model repo free) · demo: Render Starter $7 trước bảo vệ 3–5 ngày |

---

## 1. Luồng end-to-end

```
user đầu tiên mở app trong ngày
   → server chọn quest từ bộ mẫu (lớp theo seed ngày ∈ AI_QUEST_CLASSES, bỏ 3 lớp gần nhất)
   → dailyQuests/{date}_AI_CHALLENGE {targetClass, content, source:'FALLBACK'}   (create() atomic)

user chụp + đăng moment như mọi ngày   (POST /upload → Cloudinary → POST /moments)
   → nếu hôm nay có quest AI & user chưa xong & contentType=PHOTO:
        server gửi AI service:  https://res.cloudinary.com/.../upload/w_640,h_640,c_limit,f_jpg,q_auto/<id>
        gửi kèm targetClasses = AI_QUEST_GROUPS[quest]  (vd cup → [cup, bottle]; person → [person])
        AI service (y1): letterbox 640 → YOLO11 → điểm lớp = max theo anchor → so ngưỡng của lớp
        (quest person: model phụ YOLO11n → NMS → điểm người thứ 2)  → khớp nếu BẤT KỲ lớp nào vượt ngưỡng
        khớp   → completeUserQuest(AI_CHALLENGE) atomic + credit 30 Astrite → app toast 🎯
        không  → aiQuest NOT_MATCHED → toast "doesn't match", bài vẫn đăng, thử lại bằng bài khác
        lỗi/timeout 3s → SKIPPED (im lặng)
   → mọi lần verify ghi log aiVerifications (điểm 12 lớp, ngưỡng, modelVersion, latency) → admin /ai-verifications
```

Cron tuỳ chọn: `POST /quests/ai/generate` (x-cron-secret) sinh trước quest hôm nay + mai; ping `/health` của Cloud Run mỗi 10' để tránh cold start.

---

## 2. Vì sao v0 nhận diện kém (bài học — đưa vào báo cáo)

v0 = MobileNetV3-Small **đóng băng** + head `576→256→12`, train trên COCO subset, chọn ngưỡng precision ≥ 0.7 trên COCO-val. Kết quả: test **mAP 0.647**, recall tại ngưỡng **15–60%/lớp**; production hay từ chối ảnh đúng (cốc thật 0.80 < ngưỡng 0.849).

| # | Nguyên nhân gốc | Cách y1 xử lý |
|---|---|---|
| 1 | **Sai hình thức bài toán**: nhãn COCO cấp ảnh "có cup" bật cả khi cốc chiếm vài pixel ở góc → classifier học trên nhãn rất nhiễu; vật nhỏ/lệch tâm gần như không nhận được | **Detection** (YOLO): học *vị trí* từng vật thể bằng box; điểm cấp ảnh = max theo anchor nên vật nhỏ ở góc vẫn cho điểm cao |
| 2 | **Backbone yếu, gần như không fine-tune** (v1 chưa từng chạy); **ngưỡng chỉ calibrate trên COCO-val**, chưa hề đo trên ảnh điện thoại thật (Snapget-12 chưa chụp) | Fine-tune toàn mạng YOLO11; **tập VN tự chụp** vừa để train bổ sung vừa để **chọn ngưỡng (real-calib) + chọn lớp (real-test)** |
| 3 | **Server tự cắt mất vật thể**: gửi `w_224,h_224,c_fill` → ảnh dọc 3:4 bị cắt vuông (mất ~25% trên/dưới) + chỉ còn 224px | `w_640,h_640,c_limit` (giữ toàn khung) + letterbox 640 ở service |

Các phát hiện kỹ thuật khác của v0 (giữ cho báo cáo):
- **Bug dữ liệu 2026-08-18**: 12 lần gọi FiftyOne dùng chung `dataset_name` → chỉ tải lớp `cup` → model học "cup = không phải negative" (mAP 0.44). Sửa: tên dataset riêng + assert phân bố nhãn. Bài học: *kiểm tra phân bố nhãn trước khi train* — script 10/11 của y1 giữ nguyên các assert này.
- **Dynamic int8 phá MobileNetV3** (depthwise conv + hardswish, `ConvInteger` per-tensor): Δlogit 0.50 → chỉ quantize head (Δ 0.006). y1 dùng **fp32**.
- **Ngưỡng 0.8 → 0.7** (2026-08-19): precision 0.8 làm recall còn 15–30%; hạ 0.7 kéo macro-F1 0.46 → 0.535. y1 chọn lại trên ảnh thật với precision ≥ 0.80 (model mạnh hơn).

---

## 3. Dữ liệu

| Tập | Nguồn | Dùng |
|---|---|---|
| COCO train/val | COCO 2017 train2017 (box 12 lớp, ≤ 2000 ảnh/lớp + ~1500 background), chia 90/10; +20% bản **cắt dọc 3:4** của ảnh ngang (mô phỏng ảnh điện thoại) | train / chọn best.pt |
| COCO test | val2017 (≤ 3000 ảnh có lớp + 500 negative) | đo iid, ablation |
| Kaggle | dataset public **có box** (YOLO / COCO JSON / VOC); tên lớp map qua `ml/yolo_class_map.yaml` | train |
| Tập train bổ sung dữ liệu bản địa | `snapget_vn_supplement.zip` trên Drive — dataset YOLO 12 lớp, ảnh bối cảnh VN gán nhãn bán tự động (số lượng/nguồn/giấy phép: `README.md` trong zip). Code thu thập đã gỡ khỏi repo sau khi đóng gói (2026-11-26) | train |
| Ảnh Google (tay) | ảnh đời thường bối cảnh VN; **pre-label bằng YOLO11x (`12`) → nhóm sửa tay** trên Roboflow | train |
| **VN-train** | ≥ 150 ảnh/lớp (≥ 50 tự chụp) + 300 negative — **lặp ×3** khi train | train |
| **VN-test** | 50 ảnh/lớp + 100 negative, **100% tự chụp**; script chia **real-calib 40%** / **real-test 60%** | chọn ngưỡng / chọn lớp + báo cáo |

- **Đặc tả chính xác** (định nghĩa lớp bối cảnh VN — ghế nhựa vỉa hè, ly trà sữa, xe số/xe ga, dù che quán, xe đạp điện…; độ đa dạng; quy tắc vẽ box; tên file; `metadata.csv`): **`ml/DATASET_SPEC.md`**.
- **Chống rò rỉ**: một vật thể/địa điểm chỉ thuộc một tập; script 11 chạy **dHash** giữa toàn bộ ảnh train bổ sung với VN-test + COCO-test → loại ảnh gần trùng; ảnh ngoài COCO không bao giờ vào test.
- **Kiểm tra tự động** (script 11): sai class id/toạ độ, thiếu nhãn, ảnh Google trong test, dưới số lượng tối thiểu → **dừng**; thiếu đa dạng (tối/xa/bối cảnh/máy/ảnh dọc, cặp dễ nhầm) → cảnh báo + `merge_report.json`.
- License: COCO annotations CC-BY 4.0; Kaggle ghi từng dataset vào `data_sources.md`; ảnh Google chỉ dùng học thuật, không đưa lên repo.

---

## 4. Model & cách verify

- **YOLO11s** (chính) / **YOLO11n** (so latency), `imgsz 640`, ~40 epoch, cosine LR, mosaic (tắt 8 epoch cuối), HSV/scale/flip.
- **Khởi tạo**: cắt head 80 lớp COCO → 12 lớp (`slice_coco_head`) — model bắt đầu đúng bằng YOLO11 COCO trên 12 lớp (đo được: lệch 0.0 so với model gốc). ⚠️ Phát hiện khi làm (2026-11-23): Detect của YOLO11 có độ rộng nhánh phân loại `c3 = max(ch0, min(nc, 100))` → với **yolo11n** đổi nc 80 → 12 làm Ultralytics dựng lại head và **bỏ 48 tensor** (`Transferred 451/499`). Sửa: `KeepHeadTrainer` train thẳng model đã cắt (kể cả khi resume).
- **Verify**: output `(1, 4+12, 8400)` (điểm lớp đã sigmoid) → **điểm cấp ảnh = max theo anchor** → `matched = score[target] ≥ threshold[target]`. Không cần NMS (anchor điểm cao nhất của lớp luôn sống sót qua NMS). Giữ nguyên shape `scores{12}` của contract.
- **Export**: ONNX opset 12, fp32, không NMS (~36MB với 11s, ~10MB với 11n); script 16 tự so khớp PyTorch ↔ ONNX (phải < 0.01; đo thực tế 0.00000) + đo latency CPU (11n ~20–35ms trên laptop).

---

## 5. Đánh giá, ngưỡng & chọn lớp quest

### 5.1 Hai cấp số liệu (script 14, cùng tập + cùng quy trình cho mọi model)
| Cấp | Chỉ số | Nguồn |
|---|---|---|
| **Detection** | mAP50, mAP50-95, P/R/F1 từng lớp, **confusion matrix 13×13** (12 lớp + background), PR/F1 curve | Ultralytics `val` → `det_<split>.json` + ảnh |
| **Verify cấp ảnh** (đúng quyết định sản phẩm) | AP, P/R/F1 tại ngưỡng, **false-reject rate**, **confusion cấp ảnh** (`P(score_Y ≥ thr_Y \| ảnh có X, không có Y)` + hàng negative), metric theo điều kiện chụp (tối/sáng, gần/xa, bối cảnh — từ `metadata.csv`) | `metrics_<split>.json` + `image_confusion_*.png`, `pr_curves_*.png` |

### 5.2 Chọn ngưỡng
Ngưỡng riêng từng lớp trên **real-calib** (ảnh thật): tối đa recall với **precision ≥ 0.80** (`MIN_PRECISION_REAL`). Lý do thiên recall giữ nguyên từ v0: false-reject (ảnh đúng bị từ chối) đắt hơn false-accept (user được 30 Astrite "hời" < 1/10 giá khung rẻ nhất). Ngưỡng là artifact của từng phiên bản model (`thresholds.json` đi cùng `model.onnx`), không hardcode ở server.

### 5.3 Tiêu chí chọn lớp ra đề (script 15, trên **real-test**)
Một lớp được ra đề khi đạt **tất cả**: AP cấp ảnh ≥ 0.85 · F1 tại ngưỡng ≥ 0.85 · detection AP50 ≥ 0.75 · false-reject ≤ 15% · không lớp nào khác (hay ảnh negative) bị nhận nhầm là lớp này > 5% · ≥ 20 ảnh test. < 6 lớp đạt → lấy top 6 theo F1 và **ghi rõ trong báo cáo**. Script in bảng markdown cho báo cáo + đoạn `AI_QUEST_CLASSES` cho server; lớp mới vào vòng quay (vd `book`/`backpack`/`keyboard` nếu số liệu tốt) phải viết thêm 8 câu mẫu.

**Kết quả y1-s trên real-test (2026-11-27):** 0 lớp đạt đủ tiêu chí (chủ yếu trượt AP50 box ≥ 0.75 — chỉ laptop/bicycle đạt) → top 6 theo F1: chair, potted plant, laptop, keyboard, umbrella, bicycle. Mạnh: bicycle (AP ảnh .961, từ chối sai 6%), keyboard .944, laptop .939, motorcycle .920 (nhưng ngưỡng 0.744 quá chặt → từ chối sai 32%). Yếu: clock .703, book .737, bottle .788, cup .821 (từ chối sai 27–49%).

### 5.4 Quest gộp nhóm (2026-11-27 — script 17)
User chốt 7 quest gộp (mục 0) thay cho danh sách của script 15. Đánh giá **theo quest** bằng `ml/scripts/17_eval_quests.py` + notebook `snapget_quest_eval.ipynb`:
- 6 quest của y1: dùng lại `scores_real_test.npz` của script 14 — điểm quest = `max_c(score_c / thr_c)`, pass khi ≥ 1 (đúng quyết định của AI service); ảnh dương = có bất kỳ lớp nào của nhóm. Bảng có cột "từ chối sai trước gộp" (từng lớp) để thấy lợi ích của việc gộp.
- Quest person: tập tự chụp `quest_test/{person,other}` (nhãn theo thư mục), calib 40% / test 60%, ngưỡng precision ≥ 0.80 → `person/thresholds.json` cho `16 --task person`.
- Ra `artifacts/quest_eval/quest_metrics.{json,md}` — bảng 7 quest cho báo cáo.

---

## 6. Ablation (chương ML của báo cáo)

So sánh trên **cùng** real-test + COCO-test, cùng quy trình chọn ngưỡng:

| Model | Train? | Câu hỏi trả lời |
|---|---|---|
| v0 MobileNetV3 classifier | có (head) | baseline đang chạy production |
| YOLO11s COCO, **không fine-tune** (cắt head 12 lớp) | không | detection "có sẵn" đã hơn classifier tự train bao nhiêu? |
| YOLO11n fine-tune | có | nhỏ 3× — đánh đổi độ chính xác ↔ latency trên Cloud Run |
| **YOLO11s fine-tune (y1)** | có | giá trị của dữ liệu VN + fine-tune |

Kèm latency p50/p95 onnxruntime CPU. Bảng in ở ô cuối notebook (`artifacts/eval/ablation.json`). OWL-ViT zero-shot (script 07) giữ làm ablation phụ nếu còn thời gian.

---

## 7. Kiến trúc hệ thống & contract API

```
Android app ──► NestJS (Render) ──► Firestore / Cloudinary / FCM
                    └──► AI service (FastAPI, Cloud Run 512Mi, min-instances 0)
                           ├─ POST /verify   ONNX: arch "yolo" (y1) | "classifier" (v0)
                           ├─ POST /generate (tắt — điểm cắm LLM)
                           └─ GET  /health   (public, keep-warm)
                           └─ tải model.onnx + thresholds.json + model_meta.json từ HF Model repo lúc boot
```

**Contract `/verify` chỉ THÊM field** (service cũ/server cũ vẫn chạy):
```
POST /verify (X-API-Key) { imageUrl, targetClass, targetClasses? }
  → { matched, score, threshold, scores{...}, modelVersion, latencyMs, box?, matchedClass? }
GET /health → { status, modelVersion, arch, verifierReady, extraModels{person}, llm, llmLoading, llmError }
```
- `targetClasses` (**2026-11-27**, quest gộp nhóm): có thì service bỏ qua `targetClass`; `matched` = bất kỳ lớp nào ≥ ngưỡng; `score/threshold` của lớp khớp (hoặc lớp có score/threshold cao nhất); `matchedClass` = lớp khớp. Service chỉ chạy model cần cho các lớp được hỏi (quest person chỉ chạy model phụ, `scores` = `{person}`); `modelVersion` nối bằng `+` nếu chạy nhiều model. Lớp của model phụ chưa upload → 503 (server SKIPPED).
- **Model phụ**: thư mục con của HF repo / `MODEL_DIR` (`EXTRA_MODELS`, mặc định `person`), cùng 3 file; `model_meta.json` có `"imageScore": "nth:2"`, `"nmsIou": 0.5`. NMS + điểm thứ n có bản sao giống hệt ở `ml/snapget12/yolo.py` (test đối chiếu trong `test_app.py`).
- `box` (**mới, tuỳ chọn**): `[x1,y1,x2,y2]` chuẩn hoá trên ảnh gốc nơi target có điểm cao nhất — chỉ model YOLO; `null` với v0. Server hiện bỏ qua (có thể dùng để admin vẽ khung sau này).
- `arch` trong `/health` (**mới**). `model_meta.json` y1: `{modelVersion:"y1", arch:"yolo", classes[12], inputSize:640, preprocess:"letterbox", padValue:114, scoreActivation:"none", ...}`.
- Server: `AiService.toVerifyImageUrl` → `w_640,h_640,c_limit,f_jpg,q_auto`. Bảo mật (API key, Secret Manager, server không tin AI service): `SECURITY.md` mục 18.

---

## 8. Firestore & constants (không đổi schema)

| Doc | Ghi chú |
|---|---|
| `dailyQuests/{date}_AI_CHALLENGE` | `targetClass` (từ 2026-11-27 = **key quest**, 1 trong 7), `source`, `generatedAt` |
| `userQuests/{date}_{uid}_AI_CHALLENGE` | `momentId`, `aiScore`, `modelVersion` |
| `aiVerifications/{id}` | log MỌI lần verify — `mediaUrl` từ 2026-11-23 là ảnh 640 giữ tỉ lệ (log cũ: 224 cắt vuông); từ 2026-11-27 thêm `targetClasses[]`, `matchedClass` |

`server/src/common/constants.ts`: `AI_QUEST_GROUPS` (7 key → lớp chấp nhận) · `AI_QUEST_CLASSES` = 7 key · `aiQuestTargetClasses(key)` (doc quest cũ, vd `clock` → `[clock]`) · `AI_MODEL_CLASSES` (12, thứ tự = output y1 — không đổi) · `AI_EXTRA_CLASSES = ['person']` · `QUEST_AI_ASTRITE=30` · `AI_VERIFY_TIMEOUT_MS=3000` · `AI_QUEST_AVOID_RECENT=3`. Bộ câu mẫu: 7 quest × 8 = **56 câu**, câu quest gộp nêu đủ lớp ("a cup or a bottle").

---

## 9. Chống gian lận, rủi ro & đối sách

| Rủi ro | Đối sách |
|---|---|
| Ảnh có sẵn từ gallery | ✅ chặn sẵn: luồng moment chỉ nhận ảnh từ CameraX |
| Chụp lại màn hình/ảnh in có vật thể | Tập VN có **hard negative màn hình/ảnh in** (nhãn rỗng) → model học từ chối; vẫn là limitation (không cam kết chặn hết) |
| Chi phí gian lận | 30 Astrite/ngày < 1/10 giá khung rẻ nhất → động cơ thấp bằng thiết kế kinh tế |
| Colab ngắt giữa lúc train | checkpoint lưu Drive mỗi epoch + `--resume` (dùng `KeepHeadTrainer`); dataset zip trên Drive, giải nén lại 3–5' |
| Nhãn sai | ảnh tự chụp soát 100% box trên Roboflow; test do người thứ 2 kiểm |
| Rò rỉ train ↔ test làm số liệu đẹp giả | quy tắc chia người/địa điểm + dHash tự loại |
| Dataset Kaggle chỉ gán một số lớp (thiếu nhãn) | ưu tiên dataset tập trung đúng vật thể; ảnh mà mọi box ngoài 12 lớp bị loại |
| Latency YOLO trên Cloud Run 1 vCPU vượt timeout 3s | YOLO11s ước ~150–300ms; nếu p95 cao → dùng YOLO11n (script 16 cảnh báo khi p95 > 1.5s) |
| Cold start dài hơn (tải model 36MB từ HF mỗi lần scale từ 0) | giữ ping `/health` 10'; nếu vẫn chậm: copy sẵn model vào image (cách 2 trong `ml/ai-service/README.md`) |
| y1 tệ hơn kỳ vọng | không deploy (script 16 chỉ chạy khi y1 > v0 trên real-test); rollback = upload lại 3 file v0 |
| Quest person: selfie 1 mình ở nơi đông người vẫn qua (người lạ phía sau) | **chấp nhận có chủ đích** (user chốt 2026-11-27) — ưu tiên không loại nhầm ảnh chụp với bạn; ghi là limitation trong báo cáo |
| Quest person: quyền riêng tư | chỉ **đếm** người (box), không nhận dạng, không lưu ảnh cắt/vector khuôn mặt — `SECURITY.md` mục 18 |
| Deploy lệch thứ tự (server mới, AI service cũ) | quest `person` → service cũ trả 400 → SKIPPED (không phá đăng bài); quest gộp → service cũ chỉ chấm lớp đầu. Deploy **AI service trước, server sau** |

---

## 10. Nội dung chương AI cho báo cáo

1. **Phạm vi actor AI**: xác minh ảnh; câu quest do nhóm viết (cắt LLM có chủ đích).
2. **Bài toán**: "ảnh có chứa X?" — multi-label, vì sao không softmax.
3. **Vì sao v0 thất bại** (mục 2) → **chuyển classifier → detection**: lý do bằng số liệu, không phải cảm tính.
4. **Dữ liệu**: COCO + Kaggle + Google + tập VN; định nghĩa lớp theo bối cảnh VN; chống rò rỉ; domain shift COCO ↔ ảnh điện thoại VN.
5. **Thực nghiệm**: ablation mục 6; mAP50/mAP50-95, confusion matrix, F1; metric theo điều kiện chụp.
6. **Chọn ngưỡng & chọn lớp quest theo chi phí sai lệch**: quyết định sản phẩm dựa trên số liệu (mục 5).
7. **MLOps sinh viên**: Colab + resume, ONNX fp32, versioning model + ngưỡng đi cùng nhau, rollback không sửa code, log `aiVerifications` đo accuracy production.
8. **Phát hiện kỹ thuật**: bug FiftyOne, int8 phá MobileNetV3, `c3` của YOLO11n khi đổi số lớp.
9. **Giới hạn & hướng phát triển**: chống chụp màn hình, active learning từ log verify, thêm lớp đặc trưng VN.

---

## 11. Lộ trình & checklist việc user

| Phase | Việc | Ai | Trạng thái |
|---|---|---|---|
| Y0 | Pipeline y1 (`ml/snapget12/yolo.py`, scripts 10–16, notebook, class map), ai-service `arch:"yolo"`, server URL 640, đặc tả dữ liệu | Claude | ✅ 2026-11-23 |
| Y0b | Tập train bổ sung dữ liệu bản địa `snapget_vn_supplement.zip` (đã đóng gói; code thu thập đã gỡ) | Claude | ✅ 2026-11-26 |
| Y1 | Thu thập + gán nhãn **VN-test** và **VN-train**; chọn dataset Kaggle; tải ảnh Google | user | ⬜ |
| Y2 | Train y1 trên Colab, đánh giá, chọn lớp | user chạy, Claude review | ✅ 2026-11-27 (y1-s, AdamW) |
| Y3a | 7 quest gộp nhóm + model phụ person: script 17, `16 --task person`, ai-service `targetClasses`/model phụ, server `AI_QUEST_GROUPS` + 56 câu, admin, notebook `snapget_quest_eval.ipynb` | Claude | ✅ 2026-11-27 |
| Y3b | Deploy y2 + person → AI service → server → admin | Claude | ✅ 2026-11-28 (bản demo) |
| Y3c | Chụp `quest_test/`, chạy notebook quest eval, chốt ngưỡng person + book, test end-to-end trên app | user + Claude | ⬜ |
| Y4 | Viết chương AI từ `ablation.json`, `class_selection.md`, confusion matrix | user | ⬜ |
| M7 | Trước bảo vệ 3–5 ngày: Render Starter, xem trước quest ngày demo, kiểm keep-warm | user | ⬜ |

### Việc tiếp theo sau demo (ghi 2026-11-28)

**Đang chạy production:** model chính `y2` (YOLO11s, 9 lớp, ONNX tải từ HF `Flowerng/snapget-ai-model`) + model phụ `person-y11n` (YOLO11n COCO gốc, ngưỡng **tạm 0.4 chưa đánh giá**). Model person **nhồi trong image** (`ml/ai-service/model/person/`, gitignore) vì chưa upload lên HF → deploy lại từ máy khác/clone mới sẽ **mất model person** cho tới khi làm việc 2. Billing GCP đã gắn `My Billing Account` (Cloud Run vẫn trong free tier — xem việc 9).

Số liệu y2-s trên `real_test` (431 ảnh): mAP ảnh 0.863 · macro-F1 0.784 · mAP50 box 0.641. Yếu: **book từ chối sai 43%** (P 0.91 / R 0.57 → ngưỡng 0.484 chặt quá do `real_calib` ít ảnh), chair 32%, bottle 31%, cup 28%. AP50 box book 0.43, cup/bottle/umbrella ~0.53.

1. **[user] Ghi nhận demo:** quest nào hay bị từ chối sai / nhận nhầm khi thử trên máy thật; admin `/ai-verifications` xem `matchedClass`, score vs threshold. Gửi Claude vài ca sai.
2. **[user] Quest person:** chụp `quest_test/person/` (~60 ảnh ≥ 2 người) + `quest_test/other/` (~60 ảnh < 2 người, có selfie một mình) → `snapget-ml/data/quest_test.zip` → chạy `snapget_quest_eval.ipynb` ô 0a → 2 (gửi Claude `quest_eval/quest_metrics.md`) → ô 3 (login HF, upload `person/`). Sau đó Claude xoá `model/person/` local + deploy lại để đọc từ HF.
3. **[user] Số liệu y1-s cùng tập test** (so sánh công bằng cho báo cáo): thêm ô `!python scripts/15_eval_report.py --eval {WORK}/artifacts/eval/y1-s` ở notebook train → gửi `eval_report.md`.
4. **[Claude] Ngưỡng book — ĐÃ HẠ TẠM cho demo (2026-11-28): 0.484 → 0.35** qua env Cloud Run `THRESHOLD_OVERRIDES={"book":0.35}` (không sửa `thresholds.json` trên HF). Chưa đo lại precision ở ngưỡng mới (có thể dưới 0.80 → nhận nhầm sách nhiều hơn). Tuần sau: xem ca sai demo + bảng 7 quest → chốt ngưỡng (có thể cả cup/bottle/chair). **Trả lại ngưỡng gốc:** `gcloud run services update snapget-ai --region asia-southeast1 --remove-env-vars THRESHOLD_OVERRIDES` (không cần build). Chốt ngưỡng mới vĩnh viễn → sửa `thresholds.json` trên HF rồi xoá env này.
5. **[user] Tăng tập test thật:** soát nhãn `bosung_review.zip` trên Roboflow → `bosung_reviewed.zip` → `snapget_vn_data.ipynb` nhiệm vụ 4 (ghi đè `vn.zip`) → nhiệm vụ 1 (`snapget_vn_supplement`). Thiếu sau nhiệm vụ 2: umbrella 40, chair 19, book 7 ảnh test.
6. **[user] Train y3 (tuỳ kết quả 4–5):** train lại notebook train với `vn.zip` mới → eval → Claude review → export `--version y3`.
7. **[user] Viết chương AI (Y4):** dùng `eval_report.md` y2, so sánh y1/y2, bảng 7 quest, ablation, confusion matrix; hạn chế: AP50 box thấp, calib nhỏ, ngưỡng person tạm.
8. **[Claude] Docs còn nợ:** `server/GUIDE.md`/`ml/ai-service/README.md` ghi revision + cách nhồi model person; cập nhật `DEPLOY.md` (URL Cloud Run mới `https://snapget-ai-934594561258.asia-southeast1.run.app` — URL cũ `…ranezfqmaa-as.a.run.app` vẫn dùng được).
9. **Billing:** Firebase "Service level spend caps" — Cloud Run từng đặt **0 ₫ → service bị pause** ("Billing disabled. Shutting down instance", 503) ngày 2026-11-28; đã hướng dẫn user nâng lên ~50.000 ₫. **[Claude]** dọn image cũ trong Artifact Registry (`cloud-run-source-deploy/snapget-ai`) để không vượt 0.5 GB free.
10. **M7 trước bảo vệ:** đánh thức Render (`/api/health`) + Cloud Run (`/health`), kiểm keep-warm, xem trước quest ngày demo.

### Checklist chi tiết (không chặn phần code)

**A. Dữ liệu** (`ml/DATASET_SPEC.md`)
1. Chia người/địa điểm cho VN-test và VN-train (chống rò rỉ — DATASET_SPEC mục A).
2. Chụp **VN-test**: 50 ảnh/lớp + 100 negative (≥ 50 hard negative), 100% tự chụp, ưu tiên bằng app Snapget, đủ đa dạng mục C.
3. Chụp phần tự chụp của **VN-train** (≥ 50 ảnh/lớp). Phần ảnh Internet đã có sẵn trong `snapget_vn_supplement.zip` (notebook train tự dùng).
4. Chọn **dataset Kaggle có box**; ghi link + license vào `data_sources.md`; tên lớp lạ thêm vào `ml/yolo_class_map.yaml`. Tạo Kaggle API token → `kaggle.json` để trong `snapget-ml/` trên Drive (**không commit, không dán vào chat**).
5. Ghi `metadata.csv` (scene/lighting/distance/phone) cho mọi ảnh tự chụp.

**B. Gán nhãn**
6. Ảnh tự chụp → upload Roboflow → vẽ/soát box 100% ảnh (DATASET_SPEC mục D) → export YOLOv8 **không resize, không augment, 100% train** → xếp vào `vn/train`, `vn/test` → zip `vn.zip` lên Drive (`snapget-ml/data/`).
7. Ảnh Google đã gán (nếu tách riêng) → `google_labeled.zip` lên Drive.

**C. Train & đánh giá** (Colab T4, `ml/notebooks/snapget_yolo_train.ipynb`)
8. Ô 0 → 1 (lần đầu tải COCO 20–40', tự zip lên Drive) → 2 → 4 (sửa hết **lỗi** tập VN nếu script dừng).
9. Ô 5a train YOLO11s (~40 epoch × 8–12'); bị ngắt → chạy lại 0 → 1 → 2 → 4 rồi **5b resume**. (Tuỳ chọn 5c YOLO11n.)
10. Ô 6 (eval + ablation) → ô 7 (chọn lớp). **Gửi Claude**: `artifacts/eval/y1-s/class_selection.json`, `metrics_real_test.json`, `det_real_test/confusion_matrix_normalized.png`, `artifacts/eval/ablation.json`.

**D. Deploy** (sau khi Claude review số liệu)
11. Ô 8: login HF (token write) → export + upload `Flowerng/snapget-ai-model`.
12. Restart Cloud Run: `gcloud run services update snapget-ai --region asia-southeast1 --update-env-vars MODEL_RELOAD=<timestamp>` → `GET <Service URL>/health` phải trả `modelVersion:"y1"`, `arch:"yolo"`, `verifierReady:true`.
13. Claude cập nhật `AI_QUEST_CLASSES` + câu mẫu → deploy server (Render). Test: đăng ảnh có vật thể **lệch góc / ở xa** khi quest là lớp đó → toast 🎯; admin `/ai-verifications` thấy `modelVersion y1`, ảnh 640 đủ khung.
14. Admin đã sửa 1 dòng mô tả (ảnh 640) — build + deploy admin khi tiện: `cd admin && npm run build && npx firebase-tools deploy --only hosting:admin`.

**E. 7 quest gộp + quest person (2026-11-27)**
15. Chụp **`quest_test/person/`** ~60 ảnh ≥ 2 người (selfie nhóm, chụp xa/gần, ảnh co-op ghép) và **`quest_test/other/`** ~60 ảnh < 2 người (**selfie một mình**, ảnh 1 người, ảnh không người) → zip thành `snapget-ml/data/quest_test.zip` trên Drive.
16. Chạy `ml/notebooks/snapget_quest_eval.ipynb` (CPU được): ô 0 → 1 → 2 (bảng 7 quest — **gửi Claude** `artifacts/quest_eval/quest_metrics.md`) → 3 (login HF → upload `person/` lên `Flowerng/snapget-ai-model`). Chưa chụp kịp: ô 3 vẫn chạy với ngưỡng tạm 0.4.
17. Notebook train ô 7: export + upload y1-s (nếu chưa).
18. Claude (cần user xác nhận): deploy **AI service trước** (code mới đọc `person/`, restart Cloud Run → `/health` có `extraModels.person`) → **server** (Render) → **admin** (hosting).

**Khi nào cần Claude**: script dừng vì lỗi dữ liệu (gửi nguyên văn log hoặc `merge_report.json`), số liệu bất thường, quyết định lớp ra đề, deploy. Không dán token/API key vào chat.

---

## 12. Changelog

| Ngày | Nội dung |
|---|---|
| 2026-11-28 | **Deploy y2 (bản demo)**: y2-s đã có trên HF (9 lớp, ngưỡng từ 14). Cloud Run trả 503 do project mất billing → gắn `My Billing Account` theo xác nhận user. Export `16 --task person` (ngưỡng mặc định 0.4, khớp PyTorch 0.00000) nhồi vào `ml/ai-service/model/person/` (chưa có token HF) → `gcloud run deploy` revision `snapget-ai-00011-cm4` (512Mi, min 0). Smoke test local: chair 0.90 / thr 0.66 khớp, cup/bottle khớp, person chạy (~1.7s/ảnh gồm tải ảnh). Server Render + admin hosting đã chạy code 7 quest gộp (kiểm qua swagger / bundle). Việc tuần sau: mục 11 "Việc tiếp theo sau demo" |
| 2026-11-27 (3) | **y2 — train lại chỉ 9 lớp** của 6 quest (cup, bottle, book, chair, laptop, keyboard, umbrella, bicycle, motorcycle); bỏ potted plant, backpack, clock. Mỗi lớp vẫn gán nhãn riêng, gộp nhóm chỉ lúc chấm quest. Code: `classes.CLASSES` 9 lớp + `CLASSES_Y1` / `DROPPED_CLASSES`; `load_class_map` nhận tên lớp cũ, `map_class` trả None cho lớp đã bỏ, `is_dropped`; script 11 bỏ box lớp đã bỏ thay vì dừng; 13 cache khởi tạo theo số lớp (`-coco9.pt`), `--init coco`, run mặc định `y2-s`; 14 so được y1-s (12 lớp) trên 9 lớp ở cấp ảnh (không detection); **`15_select_classes.py` → `15_eval_report.py`** (chỉ còn bảng đánh giá từng lớp — `metrics.class_report` thay `select_quest_classes`); 16 mặc định `--version y2`. Mới: **`18_prepare_vn_data.py`** + notebook **`snapget_vn_data.ipynb`** (lọc `snapget_vn_supplement`/`vn.zip` còn 9 lớp, gán nhãn tự động `bosung.zip` bằng y1-s + YOLO11x, chia vào phần test còn thiếu, soát Roboflow, gộp ghi đè `vn.zip`). Notebook train: COCO `coco_yolo9.zip`, run `y2-s`, đánh giá y1-s/y2-s; quest eval đọc `eval/y2-s`. Server `AI_MODEL_CLASSES` 9 lớp (chỉ đối chiếu). Test: ml 15, ai-service 15, server quests/ai 55 pass |
| 2026-11-27 (2) | **y1-s train lại (AdamW lr 0.0005) + đánh giá xong**: real-test mAP ảnh 0.855 / macro-F1 0.767 (YOLO11s COCO chưa fine-tune: 0.835 / 0.724); coco-test giảm nhẹ (0.866 → 0.827, domain shift có chủ đích). 0 lớp đạt đủ tiêu chí 5.3. **User chốt 7 quest gộp nhóm** (mục 0): bicycle/motorcycle · laptop/keyboard · umbrella · chair · cup/bottle · book · person (≥ 2 người, không lọc người lạ). Đã cân nhắc rồi bỏ: lớp `flower`/`plant` (Kaggle không có data cây nói chung; COCO/VN không có box cây → xung đột nhãn), quest nụ cười (cần model mặt + cười riêng), quest người + vật (để sau, không cần train). Code: `snapget12/yolo.py` `nms_keep` + `nth_score_from_raw`, `metrics.group_scores`, `classes.QUEST_GROUPS`; `16 --task person` (YOLO11n cắt head lớp person, ONNX 10.7MB, p50 ~70ms CPU, khớp PyTorch 0.00000); **`17_eval_quests.py`** + notebook **`snapget_quest_eval.ipynb`**; ai-service model phụ (`EXTRA_MODELS`, thư mục con HF `person/`) + `targetClasses`/`matchedClass`/`extraModels`; server `AI_QUEST_GROUPS` + `aiQuestTargetClasses` + 56 câu mẫu + log `targetClasses`/`matchedClass`; admin hiện nhóm + lớp khớp. Test: ml 15, ai-service 15, server 269 pass; admin build OK |
| 2026-11-27 | **Train y1-n sụp ở đầu**: `optimizer=auto` chọn MuSGD lr 0.01 (mức train từ đầu) → mAP50 0.44 (epoch 1) → 0.06 (epoch 3, đỉnh warmup), trọng số COCO bị phá, EarlyStopping epoch 13 với best = epoch 1. Sửa `13_train_yolo.py`: AdamW lr0 0.0005, warmup 1 epoch (tham số `--optimizer/--lr0/--warmup-epochs`), thêm `--fresh` để train lại cùng tên. Ô 5 notebook tự tìm checkpoint (kể cả lượt train dở), bỏ qua model chưa có. **Bỏ YOLO11n** (ô 4c) theo user — chỉ so y1-s với v0 (và YOLO11s COCO chưa fine-tune làm mốc). **y1-s train xong 40 epoch** (val mAP50 0.668, mAP50-95 0.473; yếu: book 0.37, backpack 0.47) nhưng Ultralytics lưu vào `y1-s-4` (thư mục `y1-s` sót từ lần hỏng) nên script không copy được `best.pt` → sửa: `exist_ok=True` + lấy `trainer.save_dir` thật. Lượt này chạy bằng code CŨ (`optimizer=auto`). |
| 2026-11-26 (2) | Notebook train bỏ ô pre-label (dữ liệu train đã có nhãn), đánh số lại 0–8; xoá `scripts/12_prelabel.py` — ảnh tự chụp gán nhãn thẳng trên Roboflow (DATASET_SPEC mục D). Viết lại toàn bộ chữ trong notebook train theo giọng ghi chép để dùng khi báo cáo. |
| 2026-11-26 | **Gỡ toàn bộ code thu thập dữ liệu** sau khi đã đóng gói `snapget_vn_supplement.zip` (user chốt — không giữ phần cào trong repo): xoá scripts 17/18/19, `snapget12/{crawl,autolabel}.py` + test, `crawl_queries.yaml`, notebook `snapget_crawl_autolabel.ipynb` + file build; notebook train chỉ còn đọc `snapget_vn_supplement.zip` + `vn.zip` (tự chụp); bỏ `ddgs/requests/pandas` khỏi requirements. Các dòng changelog 2026-11-23 (2) → 2026-11-24 (5) bên dưới là lịch sử của phần đã gỡ. |
| 2026-11-24 (5) | Tự duyệt chạy xong: 13/14 nhóm đủ (backpack 89 → thêm 8 từ khoá). **User chốt: phần cào/gán nhãn KHÔNG trình bày trong báo cáo** — chỉ nhắc tới như "tập train bổ sung dữ liệu bản địa". → `19_package_dataset.py` + ô 8 notebook cào đóng gói `snapget_vn_supplement.zip` (YOLO 12 lớp chuẩn hoá thứ tự, README thẻ dataset ghi rõ gán nhãn bán tự động, metadata, trích nguồn, stats); notebook train ưu tiên zip này. |
| 2026-11-24 (4) | **Tự duyệt ảnh review thay cho duyệt tay** (user không đủ thời gian duyệt ~3.600 ảnh): `autolabel.auto_resolve` + `17 --auto-review` (ô 4b) — cắt vùng box chưa chắc (điểm [0.75, 2.1)), phóng to, YOLO11x + YOLO-World nhìn lại: xác nhận → thay box, không → xoá; laptop tìm keyboard trong vùng laptop, không thấy → chấp nhận không có keyboard (lệch DATASET_SPEC B/D có chủ đích); hard negative/màn hình → giữ review. Chỉ lớp còn thiếu, dừng khi đủ; metadata `autolabel=auto-review`. Ô xem mẫu ảnh tự duyệt. Roboflow thành tuỳ chọn. |
| 2026-11-24 (3) | **Chạy xong notebook cào** (108'): accept đủ 100 ở 7/12 lớp; thiếu laptop 20, backpack 58, book 65, potted plant 86; ~3.600 ảnh review. Ô 1b chỉ chạy dry-run ⇒ chưa có `vn_clean.zip`. Sửa: ô 5 chỉ zip ảnh review của lớp còn thiếu (×1.5) → `vn_web_review_pick.zip`; notebook train **bỏ `vn.rar` thô** (test là ảnh Internet), `11_merge` cho phép chưa có test (14 tạm chọn ngưỡng trên val), thêm biến `VN_MIN_TRAIN`. |
| 2026-11-24 (2) | **Crawler chạy 9 tiếng chưa xong** → nguyên nhân: Openverse 504 cả buổi (breaker bật lại mỗi 15' rồi lại lỗi), key Pixabay dán 2 lần (400 không được coi là lỗi key), lớp accept thấp (book) cào hết mọi truy vấn × nguồn. Sửa: ngắt mạch lần 2 → tắt hẳn nguồn; 400 "invalid key" → `KeyRejected`; kiểm tra định dạng key Pixabay; **che key trong log** (`redact`, bỏ `raise_for_status` vì in cả URL kèm key); `--group-minutes` (40') giới hạn thời gian mỗi lớp; cảnh báo khi không có GPU; in thời gian mỗi lớp. **Treo hẳn sau "[wikimedia] … N anh moi"**: `download_many` dùng `ex.map` chờ mọi luồng, 1 ảnh kẹt (Retry-After không trần, server nhỏ giọt — timeout requests chỉ tính từng lần đọc) là cả script đứng → mỗi ảnh tối đa 60s, Retry-After trần 10s, cả lô tối đa 180s rồi bỏ luồng kẹt, chặn ảnh > 50MP; thumbnail Wikimedia 2048 → 1920 (cỡ chuẩn, cỡ lạ bị 429). |
| 2026-11-24 | **Thêm nguồn ảnh + làm sạch dữ liệu**: Openverse 504 hàng loạt → circuit breaker (lỗi 3 lần tạm tắt 15'), giữ kết quả trang trước. Nguồn mới (key free, Colab Secrets): **Google Images qua SerpApi** (Custom Search JSON API đã đóng với tài khoản mới, ngừng 01/01/2027), Flickr (CC), Pexels, Pixabay; `--sources auto`; key sai/hết quota → tắt nguồn. Số thứ tự ảnh chỉ tăng (không dùng lại, không ghi đè). `17 --import-dir` gán nhãn ảnh đã tải sẵn (đổi `self_`→`gg_`, không nhận ảnh trong `test/`). **`18_clean_dataset.py`**: ảnh hỏng/nhỏ/mờ/studio, trùng md5 + dHash, rò rỉ train↔test, ảnh test không EXIF máy ảnh (nghi ảnh Internet) → `_quarantine/` (dry-run mặc định), tính lại lighting/distance. Notebook cào: ô 1–1d (giải nén `vn.rar` → làm sạch → gán nhãn → lưu `vn_clean.zip`); notebook train ưu tiên `vn_clean.zip`. Bộ từ khoá thêm 5 câu/lớp của nhóm. Notebook sinh từ `ml/notebooks/build/`. ml **34 test** pass |
| 2026-11-23 (2) | **Tự động cào + gán nhãn ảnh VN-train** (`17_crawl_autolabel.py`, `snapget12/{autolabel,crawl}.py`, `crawl_queries.yaml`, notebook `snapget_crawl_autolabel.ipynb`). Không cào thẳng Google (chặn IP Colab + điều khoản) → Openverse/Wikimedia (CC) + DuckDuckGo. Gán nhãn = YOLO11x + YOLO-World, loại hard negative/màn hình; accept/review/reject; hard negative bỏ qua lớp bị nhầm theo `concept` (wristwatch → clock); metadata tự ước lượng; resume + `--promote`. Phát hiện khi chạy thử: Openverse lọc `size/category` mất ~90% kết quả (bỏ lọc); ảnh TMĐT có watermark (chặn tên miền); box lớp khác 1 model thấy bị accept nhầm (bàn nhựa → umbrella) → bắt review. Script 11 nhận nhiều `--vn`; `find_names` ưu tiên file nông nhất. DATASET_SPEC thêm mục G0 (chính sách nhãn tự động: accept không duyệt từng ảnh, xem mẫu 10%; test vẫn 100% người gán). ml **29 test** pass; chạy thử thật trên CPU (yolo11n + yolov8s-worldv2) với 3 nguồn |
| 2026-11-23 | **Nâng cấp y1 — viết lại toàn bộ file.** Review hệ thống AI tìm 3 nguyên nhân gốc v0 kém (mục 2). Chốt với user: YOLO11 detection, giữ 12 lớp, chọn lớp quest theo ngưỡng chất lượng trên ảnh thật, train Colab, dữ liệu bổ sung Kaggle + Google + tập VN (đặc tả `ml/DATASET_SPEC.md`). Code: `ml/snapget12/yolo.py` (letterbox, max-theo-anchor, dHash, class map, `slice_coco_head`), `metrics.py` tham số hoá lớp + `image_confusion` + `select_quest_classes` + false-reject rate, scripts **10–16**, `yolo_class_map.yaml`, notebook `snapget_yolo_train.ipynb`; ai-service hỗ trợ `arch:"yolo"` (+ `box`, `arch` trong `/health`, rollback v0 không sửa code), `dev_fake_model.py --arch yolo`; server `toVerifyImageUrl` → `w_640,h_640,c_limit`. Phát hiện: `c3` của YOLO11n đổi theo nc → `KeepHeadTrainer`. Test: ml 12, ai-service 11, server 265 pass; chạy thử end-to-end pipeline 11→16 trên dữ liệu giả (ONNX ↔ PyTorch lệch 0.00000) |
| 2026-08-19 | Ngưỡng precision ≥ 0.7 lên production (macro-F1 test 0.535); `MIN_PRECISION=0.70` mặc định |
| 2026-08-18 | v0 (data đúng) lên production Cloud Run, verify end-to-end OK (8–22ms); test mAP 0.6474. Trước đó: bug FiftyOne trùng `dataset_name` (mAP 0.44) → sửa + assert; int8 Conv phá MobileNetV3 → quantize head-only; secret `API_KEY` dính ký tự lạ do dán lệnh bash vào PowerShell |
| 2026-08-16 | Bỏ LLM sinh quest (bộ mẫu câu thay thế); hosting HF Space → Cloud Run (HF thu phí Docker Space), model để HF Model repo; app ra đề 9/12 lớp (bỏ book/backpack/keyboard); trang admin `/ai-verifications`; `dev_fake_model.py` |
| 2026-08-15 | Thi công M0–M5: server `ai/` + quest `AI_CHALLENGE` + cron, AI service FastAPI + ONNX, pipeline train v0 (scripts 01–08), app icon 🎯 + toast |
| 2026-08-14 | Tạo plan: thay hướng kiểm duyệt NSFW bằng AI quest; luồng hook-vào-đăng-bài; +30 Astrite riêng; 12 lớp; MobileNetV3 + head multi-label |

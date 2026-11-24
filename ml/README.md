# `ml/` — AI Daily Quest: model xác minh ảnh (tự train) + AI service

> Kế hoạch & quyết định đã chốt: [`Snapget/.claude/QUEST_AI_PLAN.md`](../Snapget/.claude/QUEST_AI_PLAN.md). Đặc tả dữ liệu Việt Nam (đọc trước khi chụp/gán nhãn): [`DATASET_SPEC.md`](DATASET_SPEC.md).
> Thư mục này chỉ chứa **code + notebook**, KHÔNG chứa data/model nhị phân (`.gitignore`). Server: `server/GUIDE.md` (module `ai`, `quests`); app: `Snapget/.claude/GUIDE.md`.

**Hai thế hệ model** (cùng 12 lớp, cùng contract với server):

| | v0 (production đến khi y1 lên) | **y1** (2026-11-23) |
|---|---|---|
| Kiến trúc | MobileNetV3-Small đóng băng + head 576→256→12 (classifier) | **YOLO11s detection** fine-tune (Ultralytics) |
| Dữ liệu | COCO subset | COCO + Kaggle + ảnh Google + **tập VN tự chụp** |
| Verify | sigmoid(logit) ≥ ngưỡng | **max điểm lớp theo anchor** ≥ ngưỡng |
| Ngưỡng chọn trên | COCO val | **ảnh thật (real-calib)** |
| Pipeline | scripts `01–08`, `notebooks/legacy_v0/snapget12_train.ipynb` | scripts **`10–16`**, **`snapget_yolo_train.ipynb`** |

## Cấu trúc

```
ml/
├─ README.md, DATASET_SPEC.md     ← file này + đặc tả tập VN (cho thành viên đi chụp/gán nhãn)
├─ requirements.txt               ← thư viện train/eval (Colab)
├─ yolo_class_map.yaml            ← map tên lớp dataset ngoài (mug, motorbike…) → 12 lớp chuẩn
├─ crawl_queries.yaml             ← từ khoá cào ảnh bối cảnh VN (en/vi, scene, hard negative concept)
├─ snapget12/                     ← package Python dùng chung
│   ├─ classes.py                 12 lớp (THỨ TỰ = output model = server AI_MODEL_CLASSES)
│   ├─ yolo.py                    y1: letterbox, điểm cấp ảnh = max theo anchor, nhãn YOLO, dHash chống trùng,
│   │                             class map, clean_stem (tên Roboflow), slice_coco_head (cắt head 80→12 lớp)
│   ├─ metrics.py                 AP/F1/false-reject, chọn ngưỡng per-class, confusion cấp ảnh, chọn lớp quest
│   ├─ autolabel.py               gán nhãn tự động: gộp YOLO11x + YOLO-World, loại hard negative/màn hình, accept/review/reject,
│   │                             ước lượng lighting/distance, lọc chất lượng ảnh
│   ├─ crawl.py                   nguồn ảnh (SerpApi-Google, Flickr, Pexels, Pixabay, DuckDuckGo, Wikimedia, Openverse) + tải song song
│   │                             + chặn tên miền watermark; nguồn thiếu key tự bỏ qua, key sai/hết quota tự tắt
│   ├─ model.py, data.py          v0: MobileNetV3 + head, manifest/augment
│   └─ tests_metrics.py, tests_yolo.py, tests_autolabel.py   pytest (không cần torch)
├─ scripts/
│   ├─ 10_export_coco_yolo.py     COCO 2017 → dataset YOLO 12 lớp (+ background, + bản cắt dọc 3:4)
│   ├─ 11_merge_external.py       gộp Kaggle/Google/VN, KIỂM TRA tập VN theo DATASET_SPEC, dHash chống rò rỉ → data.yaml + lists
│   ├─ 12_prelabel.py             YOLO11x gán box nháp cho ảnh chưa nhãn → sửa tay trên Roboflow
│   ├─ 13_train_yolo.py           fine-tune YOLO11 (khởi tạo head COCO đã cắt, KeepHeadTrainer, resume Colab)
│   ├─ 14_eval_yolo.py            ablation: mAP50/50-95, confusion 13×13, verify cấp ảnh, chọn ngưỡng trên real-calib
│   ├─ 15_select_classes.py       áp tiêu chí → class_selection.json/.md + AI_QUEST_CLASSES cho server
│   ├─ 16_export_yolo_onnx.py     ONNX fp32 + model_meta.json (arch "yolo") + so khớp PyTorch + upload HF
│   ├─ 17_crawl_autolabel.py      TỰ ĐỘNG cào ảnh Internet + gán nhãn → thư mục VN-train (resume, không ghi đè, --promote ảnh đã duyệt,
│   │                             --import-dir gán nhãn ảnh đã tải sẵn). Nguồn: serpapi(Google) flickr pexels pixabay ddg wikimedia openverse
│   │                             --auto-review: TỰ DUYỆT ảnh review (cắt vùng box chưa chắc, 2 model nhìn lại) thay duyệt tay
│   │                             Nguồn lỗi 2 lượt ngắt mạch → tắt hẳn; --group-minutes (mặc định 40) giới hạn thời gian mỗi lớp; key bị che trong log
│   ├─ 19_package_dataset.py      ĐÓNG GÓI ảnh cào + gán nhãn dùng được → snapget_vn_supplement.zip (YOLO 12 lớp + README thẻ dataset
│   │                             + metadata + trích nguồn) = "tập train bổ sung dữ liệu bản địa"; notebook train tự dùng
│   ├─ 18_clean_dataset.py        LÀM SẠCH tập VN trước mọi bước: hỏng/nhỏ/mờ/studio, trùng, rò rỉ train↔test, test không EXIF → _quarantine
│   └─ 01–08_*.py                 pipeline v0 (giữ làm baseline/ablation)
├─ notebooks/                    (sinh từ notebooks/build/*.py — sửa file build, đừng sửa tay .ipynb)
│   ├─ snapget_crawl_autolabel.ipynb ← **chạy TRƯỚC**: cào + gán nhãn ảnh Internet (script 17) → Drive `data/vn_web/`
│   ├─ snapget_yolo_train.ipynb   ← **y1 — Google Colab** (chạy 10→16, ô resume; tự dùng `vn_web` nếu có)
│   └─ legacy_v0/snapget12_train.ipynb  v0 (MobileNetV3) — KHÔNG chạy nữa, chỉ giữ làm baseline
└─ ai-service/                    FastAPI trên Google Cloud Run — phục vụ CẢ v0 lẫn y1 (theo model_meta.json "arch")
    ├─ app.py                     /health · /verify · /generate (tắt mặc định)
    ├─ test_app.py                smoke test (model ONNX giả, cả classifier lẫn yolo)
    ├─ dev_fake_model.py          model giả để chạy local: mặc định classifier, --arch yolo
    └─ model/                     RỖNG trong repo — tải từ HF Model repo lúc chạy (env MODEL_REPO)
```

## Luồng y1 (Google Colab T4) — chi tiết từng ô trong notebook

| Bước | Script | Ra | Ghi chú |
|---|---|---|---|
| 0 | `17_crawl_autolabel.py` (notebook riêng) | `data/vn_web/` (train/ + review/ + metadata.csv + web_attributions.csv) | 100 ảnh/lớp + 300 negative; xem mẫu 10%, sửa `review/` trên Roboflow → `--promote` |
| 1 | `10_export_coco_yolo.py` | `/content/yolo/coco` + `stats.json` | lần đầu tải 20–40', notebook tự zip lên Drive, phiên sau chỉ giải nén |
| 2 | (notebook) | Kaggle qua API, giải nén `vn.zip`, `google_labeled.zip` | token `kaggle.json` để trên Drive |
| 3 | `12_prelabel.py` | box nháp + `needs_review.txt` | tuỳ chọn, cho ảnh chưa nhãn |
| 4 | `11_merge_external.py` | `data.yaml`, `real_calib.yaml`, `real_test.yaml`, `lists/*.txt`, `merge_report.json` | **dừng** nếu tập VN sai đặc tả |
| 5 | `13_train_yolo.py` | `artifacts/yolo/y1-s/weights/best.pt` (Drive) | ~40 epoch × 8–12'; `--resume` khi Colab ngắt |
| 6 | `14_eval_yolo.py` | `artifacts/eval/<model>/…`, `ablation.json` | so v0 / YOLO COCO không FT / y1-n / y1-s |
| 7 | `15_select_classes.py` | `class_selection.json/.md` | tiêu chí: AP ảnh ≥ .85 · F1 ≥ .85 · AP50 ≥ .75 · từ chối sai ≤ 15% · bị nhầm ≤ 5% |
| 8 | `16_export_yolo_onnx.py --upload <user>/snapget-ai-model` | `model.onnx` (fp32) + `thresholds.json` + `model_meta.json` → HF | restart Cloud Run → `/health` `modelVersion:"y1"`, `arch:"yolo"` |

Kiểm thử cục bộ (không cần GPU/dữ liệu thật):
```bash
cd ml && pytest snapget12/tests_metrics.py snapget12/tests_yolo.py snapget12/tests_autolabel.py snapget12/tests_clean.py snapget12/tests_package.py -q
cd ml/ai-service && pip install -r requirements.txt onnx pytest && pytest test_app.py -q
python dev_fake_model.py --arch yolo && $env:API_KEY='dev-key'; python -m uvicorn app:app --port 8080
```

## Deploy AI service (Google Cloud Run)

Chi tiết: `ai-service/README.md`. Đổi model = upload 3 file lên HF Model repo + restart (`MODEL_RELOAD`), **không** build lại image. Rollback về v0 = upload lại 3 file của v0 (service tự chọn đường classifier vì `model_meta.json` không có `arch`).

```bash
cd ml/ai-service
gcloud run deploy snapget-ai --source . --region asia-southeast1 --allow-unauthenticated \
  --memory 512Mi --min-instances 0 --max-instances 3 \
  --set-env-vars MODEL_REPO=<user>/snapget-ai-model \
  --set-secrets API_KEY=snapget-ai-api-key:latest
```

## Contract với server (khớp `server/src/ai/ai.service.ts`)

```
GET  /health                 → { status, modelVersion, arch, verifierReady, llm, llmLoading, llmError }
POST /verify  (X-API-Key)    { imageUrl, targetClass } → { matched, score, threshold, scores{12}, modelVersion, latencyMs, box? }
POST /generate (X-API-Key)   { classes, avoid } → { targetClass, content }      (tắt mặc định)
```
- `imageUrl`: server gửi Cloudinary `w_640,h_640,c_limit,f_jpg,q_auto` (giữ tỉ lệ, không cắt).
- `box`: chỉ model YOLO — `[x1,y1,x2,y2]` chuẩn hoá trên ảnh gốc nơi target có điểm cao nhất; v0 trả `null`. Field mới, server hiện bỏ qua.
- Lỗi: 401 sai key · 400 targetClass lạ · 422 không tải/đọc được ảnh · 503 model chưa sẵn sàng. Server coi mọi lỗi/timeout 3s là `SKIPPED` — không bao giờ làm fail đăng bài.
- Model học 12 lớp; lớp **ra đề** = `AI_QUEST_CLASSES` ở server (hiện 9 — cập nhật theo `15_select_classes.py`).

> License: Ultralytics YOLO **AGPL-3.0** (chấp nhận cho DATN — mã nguồn dự án đã mở trên GitHub).

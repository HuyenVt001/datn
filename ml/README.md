# `ml/` — AI Daily Quest: model xác minh ảnh (tự train) + AI service

> Kế hoạch & quyết định đã chốt: [`Snapget/.claude/QUEST_AI_PLAN.md`](../Snapget/.claude/QUEST_AI_PLAN.md). Đặc tả dữ liệu Việt Nam (đọc trước khi chụp/gán nhãn): [`DATASET_SPEC.md`](DATASET_SPEC.md).
> Thư mục này chỉ chứa **code + notebook**, KHÔNG chứa data/model nhị phân (`.gitignore`). Server: `server/GUIDE.md` (module `ai`, `quests`); app: `Snapget/.claude/GUIDE.md`.

**Các thế hệ model** (cùng contract với server):

| | v0 (production đến khi y1 lên) | **y1** (2026-11-23) |
|---|---|---|
| Kiến trúc | MobileNetV3-Small đóng băng + head 576→256→12 (classifier) | **YOLO11s detection** fine-tune (Ultralytics) |
| Dữ liệu | COCO subset | COCO + **tập train bổ sung dữ liệu bản địa** (`snapget_vn_supplement.zip`) + **tập VN tự chụp** |
| Verify | sigmoid(logit) ≥ ngưỡng | **max điểm lớp theo anchor** ≥ ngưỡng |
| Ngưỡng chọn trên | COCO val | **ảnh thật (real-calib)** |
| Pipeline | scripts `01–08` (đã gỡ khỏi repo), `notebooks/legacy_v0/snapget12_train.ipynb` | scripts **`10–16`**, **`snapget_yolo_train.ipynb`** |

**y2** (2026-11-27): cùng kiến trúc và pipeline với y1 nhưng train lại **chỉ 9 lớp** của 6 quest — cup, bottle, book, chair, laptop, keyboard, umbrella, bicycle, motorcycle (bỏ potted plant, backpack, clock). Mỗi lớp vẫn gán nhãn riêng; các lớp chỉ gộp nhóm lúc chấm quest. Dữ liệu VN được lọc còn 9 lớp + bổ sung ảnh bằng `18_prepare_vn_data.py` (`snapget_vn_data.ipynb`).

## Cấu trúc

```
ml/
├─ README.md, DATASET_SPEC.md     ← file này + đặc tả tập VN (cho thành viên đi chụp/gán nhãn)
├─ requirements.txt               ← thư viện train/eval (Colab)
├─ yolo_class_map.yaml            ← map tên lớp dataset ngoài (mug, motorbike…) → lớp chuẩn
├─ snapget12/                     ← package Python dùng chung
│   ├─ classes.py                 9 lớp y2 (THỨ TỰ = output model = server AI_MODEL_CLASSES), CLASSES_Y1 (12 lớp cũ) + QUEST_GROUPS (7 quest gộp = server AI_QUEST_GROUPS)
│   ├─ yolo.py                    y1: letterbox, điểm cấp ảnh = max theo anchor, nhãn YOLO, dHash chống trùng,
│   │                             class map, clean_stem (tên Roboflow), slice_coco_head (cắt head 80→9 lớp),
│   │                             nms_keep + nth_score_from_raw (quest person: điểm người thứ 2 sau NMS)
│   ├─ metrics.py                 AP/F1/false-reject, chọn ngưỡng per-class, confusion cấp ảnh, bảng đánh giá từng lớp (class_report), group_scores (quest gộp)
│   ├─ model.py, data.py          v0: MobileNetV3 + head, manifest/augment
│   └─ tests_metrics.py, tests_yolo.py   pytest (không cần torch)
├─ scripts/
│   ├─ 10_export_coco_yolo.py     COCO 2017 → dataset YOLO 9 lớp (+ background, + bản cắt dọc 3:4)
│   ├─ 11_merge_external.py       gộp COCO + dữ liệu bổ sung, KIỂM TRA tập VN theo DATASET_SPEC, dHash chống rò rỉ → data.yaml + lists
│   ├─ 13_train_yolo.py           fine-tune YOLO11 (khởi tạo head COCO đã cắt, KeepHeadTrainer, resume Colab)
│   ├─ 14_eval_yolo.py            ablation: mAP50/50-95, confusion 13×13, verify cấp ảnh, chọn ngưỡng trên real-calib
│   ├─ 15_eval_report.py          bảng đánh giá từng lớp → eval_report.json/.md (cho báo cáo)
│   ├─ 16_export_yolo_onnx.py     ONNX fp32 + model_meta.json (arch "yolo") + so khớp PyTorch + upload HF;
│   │                             --task person: model phụ YOLO11n COCO (lớp person, "imageScore": "nth:2") → HF person/
│   ├─ 17_eval_quests.py          đánh giá THEO QUEST (7 quest gộp, dùng lại scores của 14) + chọn ngưỡng quest person trên quest_test/
│   ├─ 18_prepare_vn_data.py      lọc tập VN còn 9 lớp + gán nhãn tự động ảnh bổ sung (bosung.zip) + gộp vào vn.zip
│   └─ 01–08_*.py                 pipeline v0 (giữ làm baseline/ablation)
├─ notebooks/                    (sinh từ notebooks/build/*.py — sửa file build, đừng sửa tay .ipynb)
│   ├─ snapget_yolo_train.ipynb   ← **y1 — Google Colab** (chạy 10→16, ô resume; tự dùng `snapget_vn_supplement.zip`)
│   ├─ snapget_quest_eval.ipynb   ← 7 quest gộp + model person (17 → 16 --task person; chạy được trên CPU)
│   ├─ snapget_vn_data.ipynb      ← chuẩn bị dữ liệu VN cho lần train 9 lớp (18: filter → autolabel → soát Roboflow → merge)
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
| 1 | `10_export_coco_yolo.py` | `/content/yolo/coco` + `stats.json` | lần đầu tải 20–40', notebook tự zip lên Drive, phiên sau chỉ giải nén |
| 2 | (notebook) | giải nén `snapget_vn_supplement.zip`, `vn.zip` (tự chụp) | đặt trong `WORK/data/` trên Drive; đã lọc 9 lớp bằng `snapget_vn_data.ipynb` |
| 3 | `11_merge_external.py` | `data.yaml`, `real_calib.yaml`, `real_test.yaml`, `lists/*.txt`, `merge_report.json` | **dừng** nếu tập VN sai đặc tả |
| 4 | `13_train_yolo.py` | `artifacts/yolo/y2-s/weights/best.pt` (Drive) | ~40 epoch × 8–12'; `--resume` khi Colab ngắt |
| 5 | `14_eval_yolo.py` | `artifacts/eval/<model>/…`, `ablation.json` | so YOLO11s COCO chưa fine-tune / y1-s (12 lớp, chỉ cấp ảnh) / y2-s |
| 6 | `15_eval_report.py` | `eval_report.json/.md` | AP ảnh, F1/P/R, từ chối sai, AP50/AP50-95 box, lớp hay nhầm — từng lớp |
| 7 | `16_export_yolo_onnx.py --upload <user>/snapget-ai-model` | `model.onnx` (fp32) + `thresholds.json` + `model_meta.json` → HF | restart Cloud Run → `/health` `modelVersion:"y2"`, `arch:"yolo"` |

Kiểm thử cục bộ (không cần GPU/dữ liệu thật):
```bash
cd ml && pytest snapget12/tests_metrics.py snapget12/tests_yolo.py -q
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
GET  /health                 → { status, modelVersion, arch, verifierReady, extraModels{person}, llm, llmLoading, llmError }
POST /verify  (X-API-Key)    { imageUrl, targetClass, targetClasses? } → { matched, score, threshold, scores, modelVersion, latencyMs, box?, matchedClass? }
POST /generate (X-API-Key)   { classes, avoid } → { targetClass, content }      (tắt mặc định)
```
- `imageUrl`: server gửi Cloudinary `w_640,h_640,c_limit,f_jpg,q_auto` (giữ tỉ lệ, không cắt).
- `box`: chỉ model YOLO — `[x1,y1,x2,y2]` chuẩn hoá trên ảnh gốc nơi target có điểm cao nhất; v0 trả `null`. Field mới, server hiện bỏ qua.
- Lỗi: 401 sai key · 400 targetClass lạ · 422 không tải/đọc được ảnh · 503 model chưa sẵn sàng. Server coi mọi lỗi/timeout 3s là `SKIPPED` — không bao giờ làm fail đăng bài.
- `targetClasses` (2026-11-27, quest gộp nhóm): `matched` = **bất kỳ** lớp nào ≥ ngưỡng riêng; `matchedClass` = lớp khớp; `score/threshold` của lớp khớp (hoặc lớp có score/threshold cao nhất). Service chỉ chạy model cần cho các lớp được hỏi.
- **Model phụ** (`EXTRA_MODELS`, mặc định `person`): thư mục con `person/` của HF repo / `MODEL_DIR`, cùng 3 file. Chưa upload → lớp `person` trả 503 (server SKIPPED).
- y2 học 9 lớp (y1: 12); **7 quest ra đề** = `AI_QUEST_GROUPS` ở server (bicycle/motorcycle · laptop/keyboard · umbrella · chair · cup/bottle · book · person ≥ 2 người — user chốt 2026-11-27; bảng đánh giá: `17_eval_quests.py`).

> License: Ultralytics YOLO **AGPL-3.0** (chấp nhận cho DATN — mã nguồn dự án đã mở trên GitHub).

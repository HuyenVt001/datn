# `ml/` — AI Daily Quest: model xác minh ảnh (tự train) + HF Space phục vụ

> Kế hoạch & quyết định đã chốt: [`Snapget/.claude/QUEST_AI_PLAN.md`](../Snapget/.claude/QUEST_AI_PLAN.md). Thư mục này chỉ chứa **code + notebook**, KHÔNG chứa data/model nhị phân (`.gitignore`).
> Phần server (NestJS `src/ai/`, `src/quests/`) xem `server/GUIDE.md`; phần app Android xem `Snapget/.claude/GUIDE.md`.

## Cấu trúc

```
ml/
├─ README.md                    ← file này
├─ requirements.txt             ← thư viện train/eval (Colab/Kaggle)
├─ snapget12/                   ← package Python dùng chung
│   ├─ classes.py               12 lớp model học (thứ tự = output model = server AI_MODEL_CLASSES; app ra đề 9 lớp AI_QUEST_CLASSES)
│   ├─ model.py                 MobileNetV3-Small backbone + Head(576→256→12); v0 đóng băng, v1 mở block cuối
│   ├─ data.py                  manifest CSV multi-hot, Dataset, augmentation (crop dọc 3:4), Snapget-12 scanner
│   ├─ metrics.py               AP/mAP/F1, chọn ngưỡng per-class (precision ≥ 0.8, max recall), PR curve
│   └─ tests_metrics.py         pytest (không cần torch)
├─ scripts/                     ← pipeline, chạy tuần tự (mỗi script có docstring hướng dẫn)
│   ├─ 01_prepare_coco.py       FiftyOne tải COCO 2017 subset 12 lớp + negative → data/manifest.csv
│   ├─ 02_cache_embeddings.py   backbone đóng băng → data/embeddings.npz (576-dim, ~120MB)
│   ├─ 03_train_head.py         train head v0 từ cache (BCE + pos_weight) → artifacts/v0/head.pt
│   ├─ 04_eval_thresholds.py    thresholds.json (val) + metrics/PR curve (val, test)
│   ├─ 05_export_onnx.py        ONNX → int8 + model_meta.json (+ --upload lên Space)
│   ├─ 06_finetune_v1.py        fine-tune v1 (mở block conv cuối, augmentation) — cần GPU
│   ├─ 07_ablation_owlvit.py    OWL-ViT zero-shot trên cùng 2 tập test (ablation báo cáo)
│   └─ 08_eval_images.py        đánh giá bất kỳ model (.pt/.onnx) trên COCO test + Snapget-12
├─ notebooks/snapget12_train.ipynb  ← notebook Colab MỎNG gọi các script trên theo thứ tự
└─ ai-service/                  ← service phục vụ model (FastAPI) — deploy lên Google Cloud Run
    ├─ app.py                   /health · /verify (ONNX) · /generate (tắt mặc định — điểm cắm LLM)
    ├─ requirements.txt, Dockerfile (đọc $PORT — chạy được cả Cloud Run/Render/HF), README.md (hướng dẫn deploy)
    ├─ test_app.py              smoke test với model ONNX giả (pytest)
    ├─ dev_fake_model.py        tạo model ONNX giả vào model/ → chạy service local, test end-to-end trước khi train
    └─ model/                   RỖNG trong repo — tải từ HF Model repo lúc chạy (env MODEL_REPO) hoặc copy tay
```

## Luồng train (Colab T4, ~1 buổi)

Mở `notebooks/snapget12_train.ipynb` trên Colab, chạy từ trên xuống. Tóm tắt:

| Bước | Script | Ra | Thời gian |
|---|---|---|---|
| 1 | `01_prepare_coco.py` | `data/manifest.csv` (~45–55k ảnh trên disk Colab) | 20–40 phút (tải) |
| 2 | `02_cache_embeddings.py` | `data/embeddings.npz` | ~10 phút GPU |
| 3 | `03_train_head.py` | `artifacts/v0/head.pt` | ~1–2 phút |
| 4 | `04_eval_thresholds.py` | `thresholds.json`, `metrics_val/test.json`, PR curve PNG | giây |
| 5 | `05_export_onnx.py --upload <user>/snapget-ai-model` | `model.onnx` (backbone fp32 + head int8, ~3.9MB — ⚠️ int8 cả Conv phá MobileNetV3, xem docstring) + `model_meta.json` → **HF Model repo** (free) → Cloud Run tự tải | phút |
| 6 (M6) | `06_finetune_v1.py` → `08_eval_images.py` → `05 --full` | v1 + số liệu Snapget-12 | 30–60 phút GPU |
| 7 (M6) | `07_ablation_owlvit.py` | `artifacts/owlvit/metrics_*.json` | tuỳ GPU |

Số liệu cho báo cáo (mục 14 plan): `artifacts/{v0,v1,owlvit}/metrics_{test,snapget12}.json` + `pr_curves_*.png`; ô cuối notebook in bảng tổng hợp.

## Deploy AI service (Google Cloud Run)

> HF Spaces đã thu phí Docker Space (2026) → chuyển sang **Cloud Run free tier**. Chi tiết từng lệnh: `ai-service/README.md`; checklist thao tác: `Snapget/.claude/QUEST_AI_PLAN.md` mục 17.B.

```bash
cd ml/ai-service
gcloud run deploy snapget-ai --source . --region asia-southeast1 --allow-unauthenticated \
  --memory 512Mi --min-instances 0 --max-instances 3 \
  --set-env-vars MODEL_REPO=<user>/snapget-ai-model \
  --set-secrets API_KEY=snapget-ai-api-key:latest
```

Kiểm tra `GET <Service URL>/health` → `{"status":"ok","modelVersion":"v0","verifierReady":true}`. Keep-warm: cron-job.org ping `/health` mỗi 10 phút (Cloud Run scale về 0, cold start ~2–4s có thể vượt timeout verify 3s).

Test cục bộ: `cd ml/ai-service && pip install -r requirements.txt onnx pytest && pytest test_app.py -q` (không cần model thật, không cần Docker).

## Contract với server (khớp `server/src/ai/ai.service.ts`)

```
GET  /health                    → { status, modelVersion, verifierReady, llm, llmLoading, llmError }
POST /verify    (X-API-Key)     { imageUrl, targetClass } → { matched, score, threshold, scores{12}, modelVersion, latencyMs }
POST /generate  (X-API-Key)     { classes[12], avoid[] }  → { targetClass, content }     (server validate lại + fallback template)
```
Lỗi: 401 sai key · 400 targetClass lạ · 422 không tải/đọc được ảnh · 503 model/LLM chưa sẵn sàng · 502 LLM trả rác. Server coi mọi lỗi/timeout 3s là `SKIPPED` — không bao giờ làm fail đăng bài.

> ⚠️ `/generate` **tắt mặc định** (user chốt 2026-08-16 bỏ LLM sinh quest): nội dung quest lấy từ bộ mẫu 72 câu (9 lớp × 8) ở `server/src/quests/entities/ai-quest-templates.ts`. AI trong hệ thống = **xác minh ảnh**. Model học 12 lớp nhưng app chỉ ra đề 9 (bỏ book/backpack/keyboard — QUEST_AI_PLAN mục 2.3).

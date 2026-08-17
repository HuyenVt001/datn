# Snapget AI service (FastAPI)

Phục vụ 2 việc cho tính năng **AI Daily Quest** (kế hoạch: `Snapget/.claude/QUEST_AI_PLAN.md`):

| Route | Auth | Việc |
|---|---|---|
| `GET /health` | public | keep-warm + trạng thái model/LLM |
| `POST /verify` | `X-API-Key` | ONNX MobileNetV3-Small + head 12 lớp (**tự train**) — ảnh có chứa `targetClass` không |
| `POST /generate` | `X-API-Key` | (tuỳ chọn) Qwen2.5-1.5B GGUF viết câu quest tiếng Việt — mặc định **TẮT**, xem mục "LLM" |

Chỉ server NestJS gọi service này; app Android không bao giờ gọi trực tiếp.

> ⚠️ **Không còn deploy lên Hugging Face Spaces**: từ 2026 HF bắt Docker/Gradio Space phải có gói trả phí (chỉ Static Space còn free). Nơi deploy chính giờ là **Google Cloud Run (free tier)**. Code không phụ thuộc nền tảng — đọc cổng từ `PORT`, nên vẫn chạy nguyên xi trên HF Space (đặt `PORT=7860`), Render, hay VPS.

## Deploy lên Google Cloud Run

**Không cần cài Docker.** `gcloud` gửi thư mục này lên Cloud Build, build image trên cloud rồi deploy.

```bash
# 1. Cài gcloud CLI (1 lần): https://cloud.google.com/sdk/docs/install  → rồi:
gcloud auth login
gcloud config set project snapget-d8693          # dùng chung project Firebase

# 2. Bật 3 API (1 lần)
gcloud services enable run.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com

# 3. Deploy (chạy TRONG thư mục ml/ai-service)
gcloud run deploy snapget-ai \
  --source . \
  --region asia-southeast1 \
  --allow-unauthenticated \
  --memory 512Mi --cpu 1 \
  --min-instances 0 --max-instances 3 \
  --set-env-vars MODEL_REPO=<user>/snapget-ai-model \
  --set-secrets API_KEY=snapget-ai-api-key:latest
```

- `--allow-unauthenticated`: cần thiết vì NestJS gọi bằng `X-API-Key` chứ không phải IAM token. Service **vẫn khoá** — thiếu/sai key là 401.
- `--set-secrets`: tạo secret trước bằng `gcloud secrets create snapget-ai-api-key --data-file=-` rồi dán chuỗi + Ctrl+D. Muốn nhanh (kém an toàn hơn) thì dùng `--set-env-vars API_KEY=...`.
- Lệnh in ra **Service URL** dạng `https://snapget-ai-xxxxxxxx.a.run.app` → đây là `AI_SERVICE_URL` của server.
- Deploy lại sau khi sửa code: chạy lại đúng lệnh ở bước 3.

**Chi phí:** free tier Cloud Run (2 triệu request + 180k vCPU-giây + 360k GiB-giây/tháng) thừa sức cho vài trăm request/ngày của đồ án. `--min-instances 0` = không có request thì không tính tiền; keep-warm ping 10 phút/lần vẫn nằm trong free tier.

## Model artifact

3 file trong `model/` (không commit vào repo): `model.onnx` (int8 ~2.5MB), `thresholds.json`, `model_meta.json`. Hai cách nạp:

1. **HF Model repo (khuyến nghị)** — Model repo vẫn **miễn phí** (chỉ Space mới thu phí). Notebook bước 5 upload lên `<user>/snapget-ai-model`; service tự tải lúc khởi động khi có env `MODEL_REPO`. Đổi model = *Cloud Run → Edit & deploy new revision* (hoặc deploy lại), không cần build lại từ đầu.
2. **Nhồi vào image** — copy 3 file vào `model/` rồi deploy; không phụ thuộc HF lúc chạy.

Thiếu model → `/verify` trả 503, server coi là `SKIPPED` (đăng bài không bị ảnh hưởng), `/health` vẫn 200.

## LLM sinh quest (`/generate`)

Mặc định **tắt** (`ENABLE_LLM=0`, `requirements.txt` không cài `llama-cpp-python`): Qwen2.5-1.5B cần ≥2GB RAM và cold start 30–60s, không hợp với Cloud Run 512Mi. Server gọi `/generate` thấy 503 → tự dùng **template người viết** (`server/src/quests/entities/ai-quest-templates.ts`), user không thấy khác biệt.

Muốn bật tự host: bỏ comment 2 dòng `llama-cpp-python` trong `requirements.txt`, deploy với `--memory 2Gi --set-env-vars ENABLE_LLM=1` (nhớ `--timeout 300`).

## Test cục bộ (không cần Docker, không cần model thật)

```powershell
python -m venv .venv
.\.venv\Scripts\pip install -r requirements.txt onnx pytest
.\.venv\Scripts\python -m pytest test_app.py -q      # 7 test
```

### Chạy service local với **model giả** → test end-to-end server ↔ service ↔ app trước khi train

```powershell
.\.venv\Scripts\python dev_fake_model.py                 # ghi model/ (model.onnx + thresholds.json + model_meta.json, modelVersion "fake-dev")
$env:API_KEY='dev-key'; .\.venv\Scripts\python -m uvicorn app:app --port 8080
# server/.env:  AI_SERVICE_URL=http://localhost:8080   AI_SERVICE_API_KEY=dev-key   → npm run start:dev
```

Model giả chấm điểm theo **độ sáng ảnh**: ảnh sáng/trắng → khớp `motorcycle`/`bicycle`/`umbrella`/`clock`; ảnh tối → khớp `cup`/`bottle`/`chair`. Đủ để thấy cả toast "🎯 +30 Astrite" lẫn "doesn't match" trên app, log `aiVerifications` và trang admin. **Không upload model này lên HF/Cloud Run** (`model/*.onnx|json` đã gitignore).

## Biến môi trường

| Biến | Bắt buộc | Mặc định | Ý nghĩa |
|---|---|---|---|
| `API_KEY` | ✅ | — | Khoá `X-API-Key`; trùng `AI_SERVICE_API_KEY` của server. Thiếu → mọi route có auth trả 503 |
| `MODEL_REPO` | — | rỗng | HF Model repo tải artifact lúc khởi động |
| `MODEL_DIR` | — | `model` | Thư mục artifact trong container |
| `PORT` | — | `8080` | Cloud Run tự inject; HF Space đặt `7860` |
| `ENABLE_LLM` | — | `0` | `1` = bật `/generate` bằng llama-cpp |
| `LLM_REPO` / `LLM_FILE` / `LLM_THREADS` | — | Qwen2.5-1.5B-Instruct GGUF Q4 / 2 | Chỉ dùng khi `ENABLE_LLM=1` |
| `IMAGE_TIMEOUT_S` / `IMAGE_MAX_BYTES` | — | `2.5` / 8MB | Giới hạn khi tải ảnh từ URL Cloudinary |

## Contract (khớp `server/src/ai/ai.service.ts`)

```
GET  /health                 → { status, modelVersion, verifierReady, llm, llmLoading, llmError }
POST /verify   (X-API-Key)   { imageUrl, targetClass } → { matched, score, threshold, scores{12}, modelVersion, latencyMs }
POST /generate (X-API-Key)   { classes[12], avoid[] }  → { targetClass, content }
```
Lỗi: 401 sai key · 400 `targetClass` lạ · 422 không tải/đọc được ảnh · 503 model/LLM chưa sẵn sàng · 502 LLM trả rác. Server coi mọi lỗi/timeout 3s là `SKIPPED` — không bao giờ làm fail đăng bài.

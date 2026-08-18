"""
Snapget AI service — FastAPI, deploy tren Google Cloud Run (free tier).

Chay duoc nguyen xi tren HF Space / Render / VPS: chi doc bien PORT (mac dinh 8080).
Doi cho deploy = doi AI_SERVICE_URL o server, khong sua code.

2 nhiem vu (QUEST_AI_PLAN.md muc 6–7):
  POST /verify    ONNX MobileNetV3-Small + head 12 lop (TU TRAIN) — "anh co chua X khong?"
  POST /generate  Qwen2.5-1.5B-Instruct GGUF Q4 (llama-cpp) — viet cau quest tieng Viet
  GET  /health    public (cron keep-warm) — modelVersion + trang thai LLM

Chi NestJS goi service nay (header X-API-Key). App Android KHONG bao gio goi truc tiep.

Artifact model nam trong thu muc model/ — KHONG commit vao monorepo. 2 cach nap:
  1. MODEL_REPO=<user>/snapget-ai-model  -> tu tai tu HF Model repo (MIEN PHI, khac Space)
     luc khoi dong. Doi model = restart service, KHONG can build lai image.
  2. Copy san 3 file vao model/ truoc khi build image (offline, khong phu thuoc HF).
  model/model.onnx        backbone + head, input 1x3x224x224 float32, output logits 1x12
  model/thresholds.json   {"cup": 0.35, ...} nguong RIENG tung lop (precision >= 0.8, max recall)
  model/model_meta.json   {"modelVersion": "v0", "classes": [...12], "inputSize": 224,
                           "mean": [...], "std": [...], "trainedAt": "..."}
Thieu model -> /verify tra 503 (server coi la SKIPPED), /health van 200 de biet Space song.
"""
from __future__ import annotations

import io
import json
import logging
import os
import re
import secrets
import shutil
import threading
import time
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Any

import httpx
import numpy as np
from fastapi import Depends, FastAPI, Header, HTTPException
from PIL import Image, ImageOps
from pydantic import BaseModel, Field

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("snapget-ai")

# ---------------------------------------------------------------------------
# Cau hinh (env — dat trong Settings > Variables and secrets cua Space)
# ---------------------------------------------------------------------------
API_KEY = os.environ.get("API_KEY", "")  # SECRET — phai trung AI_SERVICE_API_KEY o server
MODEL_DIR = Path(os.environ.get("MODEL_DIR", "model"))
MODEL_REPO = os.environ.get("MODEL_REPO", "").strip()  # HF Model repo chua model.onnx + 2 file json
MODEL_FILES = ("model.onnx", "thresholds.json", "model_meta.json")
# Mac dinh TAT: requirements khong cai llama-cpp (Cloud Run 512MB khong du cho Qwen 1.5B).
# Sinh quest dung Gemini API / template phia server. Bat lai khi chay instance >= 2GB.
ENABLE_LLM = os.environ.get("ENABLE_LLM", "0") not in ("0", "false", "False")
LLM_REPO = os.environ.get("LLM_REPO", "Qwen/Qwen2.5-1.5B-Instruct-GGUF")
LLM_FILE = os.environ.get("LLM_FILE", "qwen2.5-1.5b-instruct-q4_k_m.gguf")
LLM_THREADS = int(os.environ.get("LLM_THREADS", "2"))
IMAGE_TIMEOUT_S = float(os.environ.get("IMAGE_TIMEOUT_S", "2.5"))
IMAGE_MAX_BYTES = int(os.environ.get("IMAGE_MAX_BYTES", str(8 * 1024 * 1024)))

# 12 lop MODEL HOC — khop AI_MODEL_CLASSES o server (common/constants.ts) va thu tu output cua model.
# Server chi ra de 9 lop (AI_QUEST_CLASSES, tap con) — service nhan bat ky lop nao trong 12.
DEFAULT_CLASSES = [
    "cup", "bottle", "book", "chair", "potted plant", "laptop",
    "keyboard", "backpack", "clock", "umbrella", "bicycle", "motorcycle",
]
IMAGENET_MEAN = [0.485, 0.456, 0.406]
IMAGENET_STD = [0.229, 0.224, 0.225]



@asynccontextmanager
async def lifespan(_: FastAPI):
    generator.load_in_background()  # tai LLM o thread rieng — /health va /verify khong bi chan
    yield


app = FastAPI(title="Snapget AI Space", version="1.0", docs_url=None, redoc_url=None, lifespan=lifespan)


# ---------------------------------------------------------------------------
# Auth: X-API-Key (timing-safe). /health public.
# ---------------------------------------------------------------------------
def require_api_key(x_api_key: str | None = Header(default=None)) -> None:
    if not API_KEY:
        raise HTTPException(status_code=503, detail="Space chua cau hinh API_KEY")
    if not x_api_key or not secrets.compare_digest(x_api_key, API_KEY):
        raise HTTPException(status_code=401, detail="Sai API key")


# ---------------------------------------------------------------------------
# Verifier: ONNX classifier
# ---------------------------------------------------------------------------
def download_model(model_dir: Path) -> None:
    """Tai artifact tu HF Model repo (mien phi) neu chua co san trong image.
    Loi tai (repo sai / mat mang) chi ghi log — /verify se tra 503, server coi la SKIPPED."""
    if not MODEL_REPO or (model_dir / "model.onnx").exists():
        return
    try:
        from huggingface_hub import hf_hub_download

        model_dir.mkdir(parents=True, exist_ok=True)
        for name in MODEL_FILES:
            src = hf_hub_download(repo_id=MODEL_REPO, filename=name)
            shutil.copyfile(src, model_dir / name)
        log.info("Da tai model tu HF repo %s", MODEL_REPO)
    except Exception as e:
        log.warning("Khong tai duoc model tu %s: %s", MODEL_REPO, e)


class Verifier:
    def __init__(self, model_dir: Path) -> None:
        self.session = None
        self.classes = DEFAULT_CLASSES
        self.thresholds: dict[str, float] = {}
        self.meta: dict[str, Any] = {"modelVersion": "none"}
        self.input_size = 224
        self.mean = np.array(IMAGENET_MEAN, dtype=np.float32).reshape(3, 1, 1)
        self.std = np.array(IMAGENET_STD, dtype=np.float32).reshape(3, 1, 1)
        self.input_name = "input"
        self._load(model_dir)

    def _load(self, model_dir: Path) -> None:
        onnx_path = model_dir / "model.onnx"
        if not onnx_path.exists():
            log.warning("Khong thay %s — /verify se tra 503 cho toi khi upload model", onnx_path)
            return
        import onnxruntime as ort  # import muon: Space van boot duoc neu thieu package

        opts = ort.SessionOptions()
        opts.intra_op_num_threads = 2
        self.session = ort.InferenceSession(str(onnx_path), opts, providers=["CPUExecutionProvider"])
        self.input_name = self.session.get_inputs()[0].name

        meta_path = model_dir / "model_meta.json"
        if meta_path.exists():
            self.meta = json.loads(meta_path.read_text(encoding="utf-8"))
            self.classes = list(self.meta.get("classes", DEFAULT_CLASSES))
            self.input_size = int(self.meta.get("inputSize", 224))
            self.mean = np.array(self.meta.get("mean", IMAGENET_MEAN), dtype=np.float32).reshape(3, 1, 1)
            self.std = np.array(self.meta.get("std", IMAGENET_STD), dtype=np.float32).reshape(3, 1, 1)
        thr_path = model_dir / "thresholds.json"
        if thr_path.exists():
            self.thresholds = {k: float(v) for k, v in json.loads(thr_path.read_text(encoding="utf-8")).items()}
        else:
            log.warning("Khong thay thresholds.json — dung nguong mac dinh 0.5 cho moi lop")
        log.info("Model %s san sang (%d lop, input %d)", self.meta.get("modelVersion"), len(self.classes), self.input_size)

    @property
    def ready(self) -> bool:
        return self.session is not None

    @property
    def model_version(self) -> str:
        return str(self.meta.get("modelVersion", "none"))

    def threshold_for(self, cls: str) -> float:
        return self.thresholds.get(cls, 0.5)

    def preprocess(self, image_bytes: bytes) -> np.ndarray:
        img = Image.open(io.BytesIO(image_bytes))
        img = ImageOps.exif_transpose(img)  # anh dien thoai luu pixel ngang + co EXIF
        img = img.convert("RGB")
        # Tuong duong Cloudinary c_fill: resize canh ngan ve input_size roi center-crop
        w, h = img.size
        scale = self.input_size / min(w, h)
        img = img.resize((max(self.input_size, round(w * scale)), max(self.input_size, round(h * scale))), Image.BILINEAR)
        w, h = img.size
        left, top = (w - self.input_size) // 2, (h - self.input_size) // 2
        img = img.crop((left, top, left + self.input_size, top + self.input_size))
        arr = np.asarray(img, dtype=np.float32) / 255.0  # HWC
        arr = arr.transpose(2, 0, 1)  # CHW
        arr = (arr - self.mean) / self.std
        return arr[None, ...].astype(np.float32)  # NCHW

    def predict(self, image_bytes: bytes) -> dict[str, float]:
        assert self.session is not None
        x = self.preprocess(image_bytes)
        logits = self.session.run(None, {self.input_name: x})[0][0]
        probs = 1.0 / (1.0 + np.exp(-logits.astype(np.float64)))
        return {cls: float(round(p, 4)) for cls, p in zip(self.classes, probs)}


download_model(MODEL_DIR)
verifier = Verifier(MODEL_DIR)


async def fetch_image(url: str) -> bytes:
    if not re.match(r"^https?://", url):
        raise HTTPException(status_code=400, detail="imageUrl phai la http(s)")
    try:
        # User-Agent ro rang: mot so host (Wikimedia...) tra 403 cho client khong co UA — Cloudinary khong can
        headers = {"User-Agent": "snapget-ai/1.0 (+https://github.com/HuyenVt001/datn)"}
        async with httpx.AsyncClient(timeout=IMAGE_TIMEOUT_S, follow_redirects=True, headers=headers) as client:
            async with client.stream("GET", url) as res:
                if res.status_code != 200:
                    raise HTTPException(status_code=422, detail=f"Khong tai duoc anh (HTTP {res.status_code})")
                buf = bytearray()
                async for chunk in res.aiter_bytes():
                    buf.extend(chunk)
                    if len(buf) > IMAGE_MAX_BYTES:
                        raise HTTPException(status_code=422, detail="Anh qua lon")
                return bytes(buf)
    except httpx.HTTPError as e:
        raise HTTPException(status_code=422, detail=f"Khong tai duoc anh: {type(e).__name__}") from e


class VerifyRequest(BaseModel):
    imageUrl: str = Field(..., description="URL anh (Cloudinary da resize 224 la tot nhat)")
    targetClass: str = Field(..., description="1 trong 12 lop")


class VerifyResponse(BaseModel):
    matched: bool
    score: float
    threshold: float
    scores: dict[str, float]
    modelVersion: str
    latencyMs: int


@app.post("/verify", response_model=VerifyResponse, dependencies=[Depends(require_api_key)])
async def verify(req: VerifyRequest) -> VerifyResponse:
    if not verifier.ready:
        raise HTTPException(status_code=503, detail="Model chua duoc upload len Space")
    target = req.targetClass.strip().lower()
    if target not in verifier.classes:
        raise HTTPException(status_code=400, detail=f"targetClass khong hop le: {req.targetClass}")
    image_bytes = await fetch_image(req.imageUrl)
    t0 = time.perf_counter()
    try:
        scores = verifier.predict(image_bytes)
    except Exception as e:  # anh hong / khong decode duoc
        raise HTTPException(status_code=422, detail=f"Khong doc duoc anh: {type(e).__name__}") from e
    latency_ms = int((time.perf_counter() - t0) * 1000)
    threshold = verifier.threshold_for(target)
    score = scores[target]
    return VerifyResponse(
        matched=score >= threshold,
        score=score,
        threshold=threshold,
        scores=scores,
        modelVersion=verifier.model_version,
        latencyMs=latency_ms,
    )


# ---------------------------------------------------------------------------
# Generator: LLM nho tren CPU (llama-cpp-python) — tai model luc boot, o thread rieng
# ---------------------------------------------------------------------------
class Generator:
    def __init__(self) -> None:
        self.llm = None
        self.error: str | None = None
        self.loading = False
        self._lock = threading.Lock()  # llama-cpp KHONG thread-safe — 1 request/lan

    def load_in_background(self) -> None:
        if not ENABLE_LLM:
            self.error = "ENABLE_LLM=0"
            return
        self.loading = True
        threading.Thread(target=self._load, daemon=True).start()

    def _load(self) -> None:
        try:
            from huggingface_hub import hf_hub_download
            from llama_cpp import Llama

            path = hf_hub_download(repo_id=LLM_REPO, filename=LLM_FILE)
            self.llm = Llama(model_path=path, n_ctx=1024, n_threads=LLM_THREADS, verbose=False)
            log.info("LLM %s/%s san sang", LLM_REPO, LLM_FILE)
        except Exception as e:  # thieu package / het RAM / khong tai duoc
            self.error = f"{type(e).__name__}: {e}"
            log.warning("Khong tai duoc LLM (%s) — /generate tra 503, server dung template", self.error)
        finally:
            self.loading = False

    @property
    def ready(self) -> bool:
        return self.llm is not None

    def generate(self, classes: list[str], avoid: list[str]) -> dict[str, str]:
        assert self.llm is not None
        candidates = [c for c in classes if c not in avoid] or classes
        vi_names = {
            "cup": "cái cốc / ly", "bottle": "chai nước", "book": "quyển sách", "chair": "cái ghế",
            "potted plant": "chậu cây", "laptop": "laptop", "keyboard": "bàn phím máy tính",
            "backpack": "ba lô", "clock": "đồng hồ", "umbrella": "cái ô (dù)",
            "bicycle": "xe đạp", "motorcycle": "xe máy",
        }
        options = "\n".join(f'- "{c}" ({vi_names.get(c, c)})' for c in candidates)
        system = (
            "Bạn viết nhiệm vụ chụp ảnh hằng ngày cho một app mạng xã hội dành cho sinh viên Việt Nam. "
            "Chỉ trả về JSON hợp lệ, không giải thích."
        )
        user = (
            "Chọn ĐÚNG MỘT vật thể trong danh sách sau (giữ nguyên chuỗi tiếng Anh làm targetClass):\n"
            f"{options}\n\n"
            "Viết MỘT câu tiếng Việt có dấu, bắt đầu bằng \"Chụp\", tối đa 60 ký tự, gợi cảm hứng, "
            "nói rõ vật thể đó (có thể thêm bối cảnh: bàn học, quán cà phê, sân trường...). "
            'Trả về đúng dạng: {"targetClass": "<tên tiếng Anh trong danh sách>", "content": "<câu tiếng Việt>"}'
        )
        with self._lock:
            out = self.llm.create_chat_completion(
                messages=[{"role": "system", "content": system}, {"role": "user", "content": user}],
                temperature=0.9,
                top_p=0.95,
                max_tokens=96,
                response_format={"type": "json_object"},
            )
        text = out["choices"][0]["message"]["content"].strip()
        m = re.search(r"\{.*\}", text, re.S)
        if not m:
            raise ValueError(f"LLM khong tra JSON: {text[:120]}")
        data = json.loads(m.group(0))
        return {"targetClass": str(data.get("targetClass", "")).strip().lower(),
                "content": str(data.get("content", "")).strip()}


generator = Generator()


class GenerateRequest(BaseModel):
    classes: list[str] = Field(default_factory=lambda: list(DEFAULT_CLASSES))
    avoid: list[str] = Field(default_factory=list)


class GenerateResponse(BaseModel):
    targetClass: str
    content: str


@app.post("/generate", response_model=GenerateResponse, dependencies=[Depends(require_api_key)])
def generate(req: GenerateRequest) -> GenerateResponse:
    if not generator.ready:
        detail = "LLM dang tai" if generator.loading else f"LLM khong san sang: {generator.error or 'unknown'}"
        raise HTTPException(status_code=503, detail=detail)
    classes = [c.strip().lower() for c in req.classes] or list(DEFAULT_CLASSES)
    avoid = [c.strip().lower() for c in req.avoid]
    last_err: Exception | None = None
    for _ in range(2):  # thu toi da 2 lan — server con validate + fallback nen khong can nhieu hon
        try:
            result = generator.generate(classes, avoid)
            if result["targetClass"] in classes and result["content"]:
                return GenerateResponse(**result)
            last_err = ValueError(f"ket qua khong hop le: {result}")
        except Exception as e:  # JSON hong, timeout...
            last_err = e
    raise HTTPException(status_code=502, detail=f"LLM tra ket qua khong dung: {last_err}")


# ---------------------------------------------------------------------------
# Health (public — cron keep-warm moi 10 phut)
# ---------------------------------------------------------------------------
@app.get("/health")
def health() -> dict[str, Any]:
    return {
        "status": "ok",
        "modelVersion": verifier.model_version,
        "verifierReady": verifier.ready,
        "llm": generator.ready,
        "llmLoading": generator.loading,
        "llmError": generator.error,
    }


@app.get("/")
def root() -> dict[str, str]:
    return {"service": "snapget-ai", "docs": "xem ml/README.md trong repo Snapget"}

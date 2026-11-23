"""
Snapget AI service — FastAPI, deploy tren Google Cloud Run (free tier).

Chay duoc nguyen xi tren HF Space / Render / VPS: chi doc bien PORT (mac dinh 8080).
Doi cho deploy = doi AI_SERVICE_URL o server, khong sua code.

2 nhiem vu (QUEST_AI_PLAN.md muc 6–7):
  POST /verify    ONNX 12 lop (TU TRAIN) — "anh co chua X khong?". 2 kien truc, chon theo
                  model_meta.json "arch":
                    "yolo"       (y1, 2026-11-23) YOLO11 detection: letterbox 640, output (1,4+12,A),
                                 diem lop = max theo anchor (khong NMS) + box tot nhat cua target
                    "classifier" (v0, mac dinh) MobileNetV3-Small + head: center-crop 224, logits -> sigmoid
                  Rollback = upload lai 3 file cua model cu, khong sua code.
  POST /generate  Qwen2.5-1.5B-Instruct GGUF Q4 (llama-cpp) — writes the English quest sentence
  GET  /health    public (cron keep-warm) — modelVersion + trang thai LLM

Chi NestJS goi service nay (header X-API-Key). App Android KHONG bao gio goi truc tiep.

Artifact model nam trong thu muc model/ — KHONG commit vao monorepo. 2 cach nap:
  1. MODEL_REPO=<user>/snapget-ai-model  -> tu tai tu HF Model repo (MIEN PHI, khac Space)
     luc khoi dong. Doi model = restart service, KHONG can build lai image.
  2. Copy san 3 file vao model/ truoc khi build image (offline, khong phu thuoc HF).
  model/model.onnx        yolo: input 1x3x640x640 (RGB/255), output 1x16xA | classifier: 1x3x224x224, logits 1x12
  model/thresholds.json   {"cup": 0.35, ...} nguong RIENG tung lop (chon tren anh that real-calib)
  model/model_meta.json   {"modelVersion": "y1", "arch": "yolo", "classes": [...12], "inputSize": 640, ...}
                          (classifier: "arch" vang mat, co "mean"/"std", inputSize 224)
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
YOLO_PAD_VALUE = 114


def letterbox(img: Image.Image, size: int, pad_value: int = YOLO_PAD_VALUE) -> tuple[np.ndarray, dict]:
    """GIONG HET ml/snapget12/yolo.py `letterbox` (= Ultralytics LetterBox auto=False, center=True):
    resize giu ti le, pad 114, RGB / 255, KHONG mean/std. Sua o day thi sua ca ben do."""
    w, h = img.size
    r = min(size / h, size / w)
    nw, nh = int(round(w * r)), int(round(h * r))
    if (nw, nh) != (w, h):
        img = img.resize((nw, nh), Image.BILINEAR)
    left, top = int(round((size - nw) / 2 - 0.1)), int(round((size - nh) / 2 - 0.1))
    canvas = Image.new("RGB", (size, size), (pad_value, pad_value, pad_value))
    canvas.paste(img, (left, top))
    arr = np.asarray(canvas, dtype=np.float32) / 255.0
    return arr.transpose(2, 0, 1)[None, ...].copy(), {"ratio": r, "pad": (left, top), "orig": (w, h)}



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
        raise HTTPException(status_code=503, detail="API_KEY is not configured on this Space")
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
        log.warning("Failed to download model from %s: %s", MODEL_REPO, e)


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
        self.arch = "classifier"
        self._load(model_dir)

    def _load(self, model_dir: Path) -> None:
        onnx_path = model_dir / "model.onnx"
        if not onnx_path.exists():
            log.warning("%s not found — /verify returns 503 until the model is uploaded", onnx_path)
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
            self.arch = str(self.meta.get("arch", "classifier"))
            if self.arch not in ("classifier", "yolo"):
                log.warning("Unknown arch '%s' in model_meta.json — /verify disabled", self.arch)
                self.session = None
                return
            self.input_size = int(self.meta.get("inputSize", 640 if self.arch == "yolo" else 224))
            self.mean = np.array(self.meta.get("mean", IMAGENET_MEAN), dtype=np.float32).reshape(3, 1, 1)
            self.std = np.array(self.meta.get("std", IMAGENET_STD), dtype=np.float32).reshape(3, 1, 1)
        thr_path = model_dir / "thresholds.json"
        if thr_path.exists():
            self.thresholds = {k: float(v) for k, v in json.loads(thr_path.read_text(encoding="utf-8")).items()}
        else:
            log.warning("thresholds.json not found — using default threshold 0.5 for every class")
        log.info("Model %s (%s) san sang (%d lop, input %d)", self.meta.get("modelVersion"), self.arch,
                 len(self.classes), self.input_size)

    @property
    def ready(self) -> bool:
        return self.session is not None

    @property
    def model_version(self) -> str:
        return str(self.meta.get("modelVersion", "none"))

    def threshold_for(self, cls: str) -> float:
        return self.thresholds.get(cls, 0.5)

    @staticmethod
    def _open(image_bytes: bytes) -> Image.Image:
        img = Image.open(io.BytesIO(image_bytes))
        img = ImageOps.exif_transpose(img)  # anh dien thoai luu pixel ngang + co EXIF
        return img.convert("RGB")

    def preprocess(self, image_bytes: bytes) -> np.ndarray:
        """Tien xu ly theo arch -> tensor NCHW float32 (dung cho ca test)."""
        img = self._open(image_bytes)
        if self.arch == "yolo":
            return letterbox(img, self.input_size, int(self.meta.get("padValue", YOLO_PAD_VALUE)))[0]
        return self._classifier_input(img)

    def _classifier_input(self, img: Image.Image) -> np.ndarray:
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

    def predict(self, image_bytes: bytes, target: str | None = None) -> tuple[dict[str, float], list[float] | None]:
        """-> (diem 12 lop, box [x1,y1,x2,y2] chuan hoa tren anh goc cua `target` — chi YOLO, else None)."""
        assert self.session is not None
        img = self._open(image_bytes)
        if self.arch == "yolo":
            x, info = letterbox(img, self.input_size, int(self.meta.get("padValue", YOLO_PAD_VALUE)))
            raw = self.session.run(None, {self.input_name: x})[0][0]  # (4+C, A)
            n = len(self.classes)
            if raw.shape[0] != 4 + n:
                raise ValueError(f"YOLO output has {raw.shape[0]} channels, expected {4 + n}")
            probs = raw[4:, :].max(axis=1)  # diem cap anh = max theo anchor (output da qua sigmoid)
            box = self._best_box(raw, self.classes.index(target), info) if target in self.classes else None
            return {cls: float(round(float(p), 4)) for cls, p in zip(self.classes, probs)}, box
        logits = self.session.run(None, {self.input_name: self._classifier_input(img)})[0][0]
        probs = 1.0 / (1.0 + np.exp(-logits.astype(np.float64)))
        return {cls: float(round(p, 4)) for cls, p in zip(self.classes, probs)}, None

    @staticmethod
    def _best_box(raw: np.ndarray, cls_idx: int, info: dict) -> list[float]:
        """Box cua anchor co diem lop cao nhat -> [x1,y1,x2,y2] chuan hoa [0,1] tren anh goc."""
        a = int(np.argmax(raw[4 + cls_idx]))
        cx, cy, bw, bh = (float(v) for v in raw[:4, a])
        (left, top), r, (w0, h0) = info["pad"], info["ratio"], info["orig"]
        xs = ((cx - bw / 2 - left) / r / w0, (cx + bw / 2 - left) / r / w0)
        ys = ((cy - bh / 2 - top) / r / h0, (cy + bh / 2 - top) / r / h0)
        return [round(float(np.clip(v, 0, 1)), 4) for v in (xs[0], ys[0], xs[1], ys[1])]


download_model(MODEL_DIR)
verifier = Verifier(MODEL_DIR)


async def fetch_image(url: str) -> bytes:
    if not re.match(r"^https?://", url):
        raise HTTPException(status_code=400, detail="imageUrl must be http(s)")
    try:
        # User-Agent ro rang: mot so host (Wikimedia...) tra 403 cho client khong co UA — Cloudinary khong can
        headers = {"User-Agent": "snapget-ai/1.0 (+https://github.com/HuyenVt001/datn)"}
        async with httpx.AsyncClient(timeout=IMAGE_TIMEOUT_S, follow_redirects=True, headers=headers) as client:
            async with client.stream("GET", url) as res:
                if res.status_code != 200:
                    raise HTTPException(status_code=422, detail=f"Failed to download image (HTTP {res.status_code})")
                buf = bytearray()
                async for chunk in res.aiter_bytes():
                    buf.extend(chunk)
                    if len(buf) > IMAGE_MAX_BYTES:
                        raise HTTPException(status_code=422, detail="Anh qua lon")
                return bytes(buf)
    except httpx.HTTPError as e:
        raise HTTPException(status_code=422, detail=f"Failed to download image: {type(e).__name__}") from e


class VerifyRequest(BaseModel):
    imageUrl: str = Field(..., description="URL anh — server gui ban Cloudinary w_640,h_640,c_limit (giu ti le, khong cat)")
    targetClass: str = Field(..., description="1 trong 12 lop")


class VerifyResponse(BaseModel):
    matched: bool
    score: float
    threshold: float
    scores: dict[str, float]
    modelVersion: str
    latencyMs: int
    # Chi model YOLO: box [x1,y1,x2,y2] chuan hoa [0,1] (tren anh da xoay EXIF) noi target co diem cao nhat.
    # Field MOI, tuy chon — server/app cu bo qua (Gson/TS khong doc) nen khong breaking.
    box: list[float] | None = None


@app.post("/verify", response_model=VerifyResponse, dependencies=[Depends(require_api_key)])
async def verify(req: VerifyRequest) -> VerifyResponse:
    if not verifier.ready:
        raise HTTPException(status_code=503, detail="Model has not been uploaded to the Space")
    target = req.targetClass.strip().lower()
    if target not in verifier.classes:
        raise HTTPException(status_code=400, detail=f"Invalid targetClass: {req.targetClass}")
    image_bytes = await fetch_image(req.imageUrl)
    t0 = time.perf_counter()
    try:
        scores, box = verifier.predict(image_bytes, target)
    except Exception as e:  # anh hong / khong decode duoc
        raise HTTPException(status_code=422, detail=f"Failed to read image: {type(e).__name__}") from e
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
        box=box,
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
            log.warning("Failed to load LLM (%s) — /generate returns 503, server uses templates", self.error)
        finally:
            self.loading = False

    @property
    def ready(self) -> bool:
        return self.llm is not None

    def generate(self, classes: list[str], avoid: list[str]) -> dict[str, str]:
        assert self.llm is not None
        candidates = [c for c in classes if c not in avoid] or classes
        options = "\n".join(f'- "{c}"' for c in candidates)
        system = (
            "You write daily photo quests for a social app for university students. "
            "Return valid JSON only, no explanation."
        )
        user = (
            "Pick EXACTLY ONE object from this list (use the string as-is for targetClass):\n"
            f"{options}\n\n"
            "Write ONE English sentence starting with \"Take a photo of\", at most 60 characters, inspiring, "
            "clearly naming that object (you may add a setting: study desk, cafe, campus...). "
            'Return exactly: {"targetClass": "<name from the list>", "content": "<English sentence>"}'
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
            raise ValueError(f"LLM did not return JSON: {text[:120]}")
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
        detail = "LLM is loading" if generator.loading else f"LLM not ready: {generator.error or 'unknown'}"
        raise HTTPException(status_code=503, detail=detail)
    classes = [c.strip().lower() for c in req.classes] or list(DEFAULT_CLASSES)
    avoid = [c.strip().lower() for c in req.avoid]
    last_err: Exception | None = None
    for _ in range(2):  # thu toi da 2 lan — server con validate + fallback nen khong can nhieu hon
        try:
            result = generator.generate(classes, avoid)
            if result["targetClass"] in classes and result["content"]:
                return GenerateResponse(**result)
            last_err = ValueError(f"invalid result: {result}")
        except Exception as e:  # JSON hong, timeout...
            last_err = e
    raise HTTPException(status_code=502, detail=f"LLM returned an invalid result: {last_err}")


# ---------------------------------------------------------------------------
# Health (public — cron keep-warm moi 10 phut)
# ---------------------------------------------------------------------------
@app.get("/health")
def health() -> dict[str, Any]:
    return {
        "status": "ok",
        "modelVersion": verifier.model_version,
        "arch": verifier.arch,
        "verifierReady": verifier.ready,
        "llm": generator.ready,
        "llmLoading": generator.loading,
        "llmError": generator.error,
    }


@app.get("/")
def root() -> dict[str, str]:
    return {"service": "snapget-ai", "docs": "xem ml/README.md trong repo Snapget"}

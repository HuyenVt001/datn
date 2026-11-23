"""
Tien ich dung chung cho pipeline YOLO y1 (QUEST_AI_PLAN.md muc 4–5) — KHONG can torch
(tru `slice_coco_head`, import torch luoi ben trong).

Quyet dinh verify voi YOLO:
    output ONNX/PyTorch eval = (N, 4 + C, A)   [cx, cy, w, h (pixel tren anh letterbox) + C diem lop DA sigmoid]
    diem cap anh cua lop c  = max theo truc anchor A cua output[:, 4 + c, :]
    -> 12 diem/anh y het classifier v0 => tai su dung nguyen metrics.py + contract /verify.
Khong can NMS: anchor co diem lop c cao nhat luon song sot qua NMS, nen max truoc NMS = max sau NMS
(va tinh tren MOI lop, khong bi "argmax 1 lop/anchor" nhu NMS mac dinh cua Ultralytics).

Letterbox: GIONG Ultralytics LetterBox(auto=False, center=True) — resize giu ti le, pad 114,
input RGB / 255, KHONG mean/std. ai-service/app.py co ban sao y het (Docker context khong import
duoc package nay) — sua o day thi sua ca ben do.
"""
from __future__ import annotations

import re
from pathlib import Path

import numpy as np
from PIL import Image, ImageOps

from .classes import CLASSES

YOLO_INPUT_SIZE = 640
PAD_VALUE = 114
IMAGE_EXTS = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}

# Chi so cua 12 lop trong 80 lop COCO cua Ultralytics (yolo11*.pt) — THU TU = CLASSES.
COCO80_INDEX = {
    "cup": 41, "bottle": 39, "book": 73, "chair": 56, "potted plant": 58, "laptop": 63,
    "keyboard": 66, "backpack": 24, "clock": 74, "umbrella": 25, "bicycle": 1, "motorcycle": 3,
}
COCO80_IDS = [COCO80_INDEX[c] for c in CLASSES]


# ---------------------------------------------------------------------------
# Tien xu ly + doc output
# ---------------------------------------------------------------------------
def load_rgb(path_or_bytes) -> Image.Image:
    """Mo anh, xoay theo EXIF (anh dien thoai), ve RGB."""
    if isinstance(path_or_bytes, (bytes, bytearray)):
        import io

        im = Image.open(io.BytesIO(path_or_bytes))
    else:
        im = Image.open(path_or_bytes)
    return ImageOps.exif_transpose(im).convert("RGB")


def letterbox(img: Image.Image, size: int = YOLO_INPUT_SIZE) -> tuple[np.ndarray, dict]:
    """PIL RGB -> (1,3,size,size) float32 [0,1] + thong tin de doi box ve anh goc."""
    w, h = img.size
    r = min(size / h, size / w)
    nw, nh = int(round(w * r)), int(round(h * r))
    if (nw, nh) != (w, h):
        img = img.resize((nw, nh), Image.BILINEAR)
    dw, dh = (size - nw) / 2, (size - nh) / 2
    left, top = int(round(dw - 0.1)), int(round(dh - 0.1))
    canvas = Image.new("RGB", (size, size), (PAD_VALUE, PAD_VALUE, PAD_VALUE))
    canvas.paste(img, (left, top))
    arr = np.asarray(canvas, dtype=np.float32) / 255.0
    x = arr.transpose(2, 0, 1)[None, ...].copy()
    return x, {"ratio": r, "pad": (left, top), "orig": (w, h), "size": size}


def image_scores_from_raw(raw: np.ndarray, num_classes: int = len(CLASSES)) -> np.ndarray:
    """(N, 4+C, A) -> (N, C): max diem tung lop tren moi anchor. Chap nhan (4+C, A) cho 1 anh."""
    raw = np.asarray(raw)
    if raw.ndim == 2:
        raw = raw[None]
    if raw.shape[1] != 4 + num_classes:
        raise ValueError(f"output YOLO phai co {4 + num_classes} kenh, nhan {raw.shape}")
    return raw[:, 4:, :].max(axis=2)


def best_box(raw: np.ndarray, cls_idx: int, info: dict) -> list[float]:
    """Box (x1,y1,x2,y2) CHUAN HOA [0,1] tren anh GOC cua anchor co diem lop cls_idx cao nhat."""
    raw = np.asarray(raw)
    if raw.ndim == 3:
        raw = raw[0]
    a = int(np.argmax(raw[4 + cls_idx]))
    cx, cy, bw, bh = (float(v) for v in raw[:4, a])
    left, top = info["pad"]
    r = info["ratio"]
    w0, h0 = info["orig"]
    x1, y1 = (cx - bw / 2 - left) / r, (cy - bh / 2 - top) / r
    x2, y2 = (cx + bw / 2 - left) / r, (cy + bh / 2 - top) / r
    return [round(float(np.clip(v, 0, 1)), 4) for v in (x1 / w0, y1 / h0, x2 / w0, y2 / h0)]


def classifier_preprocess(img: Image.Image, size: int = 224, mean=None, std=None) -> np.ndarray:
    """Tien xu ly model v0 (classifier) — y het ai-service: resize canh ngan -> center crop -> normalize."""
    from .classes import IMAGENET_MEAN, IMAGENET_STD

    mean = np.array(mean or IMAGENET_MEAN, dtype=np.float32).reshape(3, 1, 1)
    std = np.array(std or IMAGENET_STD, dtype=np.float32).reshape(3, 1, 1)
    w, h = img.size
    s = size / min(w, h)
    img = img.resize((max(size, round(w * s)), max(size, round(h * s))), Image.BILINEAR)
    w, h = img.size
    left, top = (w - size) // 2, (h - size) // 2
    img = img.crop((left, top, left + size, top + size))
    arr = np.asarray(img, dtype=np.float32).transpose(2, 0, 1) / 255.0
    return ((arr - mean) / std)[None].astype(np.float32)


# ---------------------------------------------------------------------------
# Nhan YOLO (txt: "cls cx cy w h" chuan hoa)
# ---------------------------------------------------------------------------
def label_path_for(img_path: str | Path) -> Path:
    """Quy uoc Ultralytics: .../images/.../x.jpg -> .../labels/.../x.txt"""
    p = str(img_path)
    sep = "/" if "/" in p else "\\"
    key = f"{sep}images{sep}"
    i = p.rfind(key)
    if i < 0:
        raise ValueError(f"duong dan anh phai nam trong thu muc 'images': {p}")
    return Path(p[:i] + f"{sep}labels{sep}" + p[i + len(key):]).with_suffix(".txt")


def read_yolo_labels(path: str | Path) -> list[tuple[int, float, float, float, float]]:
    p = Path(path)
    if not p.exists():
        return []
    out = []
    for line in p.read_text(encoding="utf-8").splitlines():
        parts = line.split()
        if len(parts) < 5:
            continue
        out.append((int(float(parts[0])), *(float(v) for v in parts[1:5])))
    return out


def write_yolo_labels(path: str | Path, boxes) -> None:
    p = Path(path)
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text("".join(f"{c} {x:.6f} {y:.6f} {w:.6f} {h:.6f}\n" for c, x, y, w, h in boxes), encoding="utf-8")


def multi_hot(boxes, num_classes: int = len(CLASSES)) -> np.ndarray:
    y = np.zeros(num_classes, dtype=np.int64)
    for b in boxes:
        if 0 <= b[0] < num_classes:
            y[b[0]] = 1
    return y


def validate_boxes(boxes, num_classes: int = len(CLASSES)) -> list[str]:
    """Tra danh sach loi (rong = hop le)."""
    errs = []
    for i, (c, x, y, w, h) in enumerate(boxes):
        if not 0 <= c < num_classes:
            errs.append(f"dong {i + 1}: class id {c} ngoai [0,{num_classes - 1}]")
        if not (0 <= x <= 1 and 0 <= y <= 1 and 0 < w <= 1 and 0 < h <= 1):
            errs.append(f"dong {i + 1}: toa do ngoai [0,1] hoac w/h <= 0 ({x},{y},{w},{h})")
    return errs


def portrait_crop_boxes(boxes, img_w: int, img_h: int, left_px: int, min_keep: float = 0.4):
    """Cat anh ngang thanh khung doc 3:4 bat dau tu left_px -> box moi (chuan hoa theo anh cat).
    Box con lai < min_keep dien tich ban dau thi bo (vat bi cat qua nhieu = nhan gay nhieu)."""
    crop_w = int(img_h * 3 / 4)
    out = []
    for c, cx, cy, w, h in boxes:
        x1, x2 = (cx - w / 2) * img_w, (cx + w / 2) * img_w
        nx1, nx2 = max(x1, left_px), min(x2, left_px + crop_w)
        if nx2 <= nx1 or (nx2 - nx1) < min_keep * (x2 - x1):
            continue
        out.append((c, ((nx1 + nx2) / 2 - left_px) / crop_w, cy, (nx2 - nx1) / crop_w, h))
    return out, crop_w


# ---------------------------------------------------------------------------
# Map ten lop dataset ngoai -> 12 lop chuan (ml/yolo_class_map.yaml)
# ---------------------------------------------------------------------------
def norm_name(name: str) -> str:
    return re.sub(r"[\s_\-]+", " ", str(name).strip().lower())


def load_class_map(path: str | Path) -> dict[str, str | None]:
    import yaml

    raw = yaml.safe_load(Path(path).read_text(encoding="utf-8")) or {}
    out: dict[str, str | None] = {c: c for c in CLASSES}  # ten chuan luon map ve chinh no
    for k, v in raw.items():
        v = None if v in (None, "", "null") else norm_name(v)
        if v is not None and v not in CLASSES:
            raise ValueError(f"yolo_class_map: '{k}' -> '{v}' khong phai 1 trong 12 lop {CLASSES}")
        out[norm_name(k)] = v
    return out


def map_class(name: str, class_map: dict[str, str | None]) -> int | None:
    """Ten lop ngoai -> chi so trong CLASSES; None = bo (lop ngoai 12 hoac khong co trong map)."""
    target = class_map.get(norm_name(name))
    return CLASSES.index(target) if target else None


# ---------------------------------------------------------------------------
# Perceptual hash (dHash 64-bit) — khu trung + chong ro ri train/test, khong can thu vien ngoai
# ---------------------------------------------------------------------------
def dhash(img: Image.Image, hash_size: int = 8) -> int:
    g = img.convert("L").resize((hash_size + 1, hash_size), Image.BILINEAR)
    a = np.asarray(g, dtype=np.int16)
    bits = (a[:, 1:] > a[:, :-1]).flatten()
    return int("".join("1" if b else "0" for b in bits), 2)


def hamming_matrix(a: np.ndarray, b: np.ndarray) -> np.ndarray:
    """a (n,), b (m,) uint64 -> (n,m) khoang cach Hamming."""
    x = np.bitwise_xor(a[:, None].astype(np.uint64), b[None, :].astype(np.uint64))
    return np.unpackbits(x.view(np.uint8).reshape(*x.shape, 8), axis=-1).sum(axis=-1)


def near_duplicates(query: np.ndarray, ref: np.ndarray, max_dist: int = 5, chunk: int = 2048) -> np.ndarray:
    """Chi so cac phan tu cua query co >= 1 anh gan trung trong ref (Hamming <= max_dist)."""
    if len(query) == 0 or len(ref) == 0:
        return np.array([], dtype=np.int64)
    hits = []
    for i in range(0, len(query), chunk):
        d = hamming_matrix(query[i : i + chunk], ref)
        hits.extend((np.where(d.min(axis=1) <= max_dist)[0] + i).tolist())
    return np.array(hits, dtype=np.int64)


def find_names(root: str | Path) -> list[str] | None:
    """Ten lop cua 1 dataset YOLO: data.yaml (Roboflow/Ultralytics) | classes.txt (Label Studio/script 12)
    | obj.names / _darknet.labels (CVAT/Darknet). Lay file nong nhat. None = khong co."""
    import yaml

    root = Path(root)
    pats = ("data.yaml", "data.yml", "classes.txt", "obj.names", "_darknet.labels")
    # NONG NHAT truoc (vd classes.txt o goc thang review/data.yaml ben trong), cung do sau thi theo thu tu pats
    hits = sorted(((len(p.relative_to(root).parts), k, p) for k, pat in enumerate(pats) for p in root.rglob(pat)),
                  key=lambda t: (t[0], t[1]))
    for _, _, p in hits:
        if p.suffix in (".yaml", ".yml"):
            names = (yaml.safe_load(p.read_text(encoding="utf-8")) or {}).get("names")
            if isinstance(names, dict):
                names = [names[k] for k in sorted(names, key=int)]
            if names:
                return [str(n) for n in names]
        else:
            names = [ln.strip() for ln in p.read_text(encoding="utf-8").splitlines() if ln.strip()]
            if names:
                return names
    return None


_RF_SUFFIX = re.compile(r"_(jpe?g|png|webp|bmp)\.rf\.[0-9A-Za-z]+$", re.IGNORECASE)


def clean_stem(name: str | Path) -> str:
    """Ten file goc, bo duoi Roboflow them khi export: 'self_cup_0001_jpg.rf.ab12cd.jpg' -> 'self_cup_0001'."""
    return _RF_SUFFIX.sub("", Path(name).stem)


def list_images(root: str | Path) -> list[Path]:
    root = Path(root)
    return sorted(p for p in root.rglob("*") if p.suffix.lower() in IMAGE_EXTS)


# ---------------------------------------------------------------------------
# Cat head Detect 80 lop COCO -> 12 lop (giu nguyen trong so) — baseline "khong fine-tune" + khoi tao FT
# ---------------------------------------------------------------------------
def slice_coco_head(yolo, ids: list[int] | None = None, names: list[str] | None = None):
    """
    `yolo` = ultralytics.YOLO da nap yolo11*.pt (80 lop COCO). Cat kenh lop cua Conv2d cuoi trong
    cv3 (va one2one_cv3 neu co) chi con 12 lop theo THU TU CLASSES. Diem 12 lop sau khi cat
    TRUNG KHOP tuyet doi voi model goc (sigmoid doc lap tung lop). Tra lai chinh `yolo`.

    ⚠️ Cac Conv AN cua cv3 giu nguyen do rong cu: c3 = max(ch0, min(80, 100)). Ultralytics khi train
    dung lai head tu yaml voi nc=12 -> c3 = max(ch0, 12): yolo11n (ch0=64) bi doi 80 -> 64 va MAT trong
    so cv3. Vi vay 13_train_yolo.py dung KeepHeadTrainer (train thang model da cat, khong dung lai).
    """
    import torch

    ids = ids or COCO80_IDS
    names = names or CLASSES
    dm = yolo.model
    det = dm.model[-1]
    idx = torch.tensor(ids, dtype=torch.long)
    for attr in ("cv3", "one2one_cv3"):
        branches = getattr(det, attr, None)
        if branches is None:
            continue
        for seq in branches:
            conv = seq[-1]
            new = torch.nn.Conv2d(conv.in_channels, len(ids), conv.kernel_size, conv.stride, conv.padding, bias=True)
            with torch.no_grad():
                new.weight.copy_(conv.weight[idx])
                new.bias.copy_(conv.bias[idx])
            seq[-1] = new
    det.nc = len(ids)
    det.no = det.nc + det.reg_max * 4
    dm.nc = len(ids)
    dm.names = {i: n for i, n in enumerate(names)}
    if isinstance(getattr(dm, "yaml", None), dict):
        dm.yaml["nc"] = len(ids)
    return yolo

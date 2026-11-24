"""
Tu dong gan nhan anh cao tu Internet (scripts/17_crawl_autolabel.py) — LOGIC THUAN, khong can torch.

Y tuong (ml/DATASET_SPEC.md muc B, D):
  2 detector chay tren cung 1 anh:
    - "coco"  : YOLO11x pretrained COCO — rat tot voi 12 lop chuan
    - "world" : YOLO-World (open-vocabulary) voi prompt BOI CANH VN (plastic stool, bubble tea cup, scooter...)
                + prompt HARD NEGATIVE (wristwatch, conical hat, bowl, soda can, sofa, tablet...) + MAN HINH/POSTER
  -> gom box cung lop (WBF rut gon) -> loai box trung voi hard negative (dong ho deo tay != clock, non la != umbrella)
     va box nam trong man hinh/poster (DATASET_SPEC D.4) -> quyet dinh ACCEPT / REVIEW / REJECT.

Diem cua 2 model khong cung thang do (YOLO-World thap hon) -> moi detection doi sang `score = conf / keep_thr(model)`
(score >= 1 nghia la "dang tin de giu box"). Moi quyet dinh dung score.
"""
from __future__ import annotations

from dataclasses import dataclass, field

import numpy as np
from PIL import Image

from .classes import CLASSES

# ---------------------------------------------------------------------------
# Prompt YOLO-World: ten prompt -> lop chuan (12 lop) | hard negative | man hinh
# ---------------------------------------------------------------------------
CLASS_PROMPTS: dict[str, list[str]] = {
    "cup": ["cup", "mug", "plastic cup with lid and straw", "bubble tea cup", "iced coffee glass", "paper cup"],
    "bottle": ["bottle", "plastic water bottle", "glass bottle", "thermos flask"],
    "book": ["book", "notebook", "open book", "school notebook"],
    "chair": ["chair", "plastic stool", "plastic chair", "office chair", "wooden chair"],
    "potted plant": ["potted plant", "plant in a pot", "succulent in a pot", "bonsai in a pot"],
    "laptop": ["laptop"],
    "keyboard": ["keyboard", "laptop keyboard", "computer keyboard"],
    "backpack": ["backpack", "school backpack"],
    "clock": ["wall clock", "alarm clock", "digital clock", "clock"],
    "umbrella": ["umbrella", "open umbrella", "street vendor umbrella", "patio umbrella"],
    "bicycle": ["bicycle", "electric bicycle with pedals"],
    "motorcycle": ["motorcycle", "scooter", "motorbike", "moped"],
}
HARDNEG_PROMPTS = [
    "wristwatch", "smartwatch", "conical hat", "raincoat", "bowl", "soda can", "vase", "sofa", "bench",
    "tablet", "computer monitor", "television", "handbag", "suitcase", "tote bag", "flower bouquet",
    "kick scooter", "remote control", "smartphone",
]
SCREEN_PROMPTS = ["television", "computer monitor", "smartphone", "poster", "billboard"]

# Lop chuan -> cac hard negative de nham (DATASET_SPEC muc B cot ❌)
HARDNEG_FOR: dict[str, set[str]] = {
    "cup": {"bowl", "soda can", "vase"},
    "bottle": {"soda can", "vase"},
    "book": {"tablet"},
    "chair": {"sofa", "bench"},
    "potted plant": {"vase", "flower bouquet"},
    "laptop": {"tablet", "computer monitor", "television"},
    "keyboard": {"remote control"},
    "backpack": {"handbag", "suitcase", "tote bag"},
    "clock": {"wristwatch", "smartwatch", "smartphone"},
    "umbrella": {"conical hat", "raincoat"},
    "bicycle": {"kick scooter"},
    "motorcycle": {"kick scooter"},
}


def confused_classes(concept: str) -> set[int]:
    """Hard negative `concept` (vd 'wristwatch') -> chi so cac lop hay bi nham voi no (vd clock). 'poster' -> rong."""
    return {CLASSES.index(c) for c, hs in HARDNEG_FOR.items() if concept in hs}


def world_vocabulary() -> tuple[list[str], list[tuple[str, object]]]:
    """Danh sach prompt cho YOLO-World + ban do prompt -> ("class", idx) | ("hardneg", ten)."""
    vocab, meaning = [], []
    for c, prompts in CLASS_PROMPTS.items():
        for p in prompts:
            vocab.append(p)
            meaning.append(("class", CLASSES.index(c)))
    for h in dict.fromkeys(HARDNEG_PROMPTS + SCREEN_PROMPTS):
        vocab.append(h)
        meaning.append(("hardneg", h))
    return vocab, meaning


# ---------------------------------------------------------------------------
@dataclass
class Det:
    box: np.ndarray            # xyxy chuan hoa [0,1]
    conf: float
    model: str                 # "coco" | "world"
    cls: int | None = None     # chi so 12 lop, None neu la hard negative
    hardneg: str | None = None
    score: float = 0.0         # conf / keep_thr(model)


@dataclass
class Cluster:
    cls: int
    box: np.ndarray
    score: float
    conf: float
    models: set = field(default_factory=set)


@dataclass
class Thresholds:
    keep: dict = field(default_factory=lambda: {"coco": 0.35, "world": 0.20})   # score >= 1
    low: float = 0.75       # score trong [low, 1) = box KHONG CHAC (co the la vat the bi sot)
    min_uncertain_area: float = 0.005  # box nghi van nho hon 0.5% anh -> bo qua (vat qua xa — DATASET_SPEC D.3)
    accept: float = 1.6     # vat the muc tieu: score >= accept ...
    strong: float = 2.1     # ... va (2 model cung thay HOAC score >= strong)
    merge_iou: float = 0.55
    hardneg_iou: float = 0.5
    hardneg_margin: float = 0.0   # hard negative phai co score >= score lop chuan - margin moi loai box
    screen_contain: float = 0.85  # box lop chuan nam >= 85% trong box man hinh/poster -> loai


def iou(a: np.ndarray, b: np.ndarray) -> float:
    ix = max(0.0, min(a[2], b[2]) - max(a[0], b[0]))
    iy = max(0.0, min(a[3], b[3]) - max(a[1], b[1]))
    inter = ix * iy
    ua = (a[2] - a[0]) * (a[3] - a[1]) + (b[2] - b[0]) * (b[3] - b[1]) - inter
    return float(inter / ua) if ua > 0 else 0.0


def contained(inner: np.ndarray, outer: np.ndarray) -> float:
    """Ti le dien tich `inner` nam trong `outer`."""
    ix = max(0.0, min(inner[2], outer[2]) - max(inner[0], outer[0]))
    iy = max(0.0, min(inner[3], outer[3]) - max(inner[1], outer[1]))
    area = (inner[2] - inner[0]) * (inner[3] - inner[1])
    return float(ix * iy / area) if area > 0 else 0.0


def with_scores(dets: list[Det], th: Thresholds) -> list[Det]:
    for d in dets:
        d.score = d.conf / th.keep[d.model]
    return dets


def merge(dets: list[Det], th: Thresholds) -> list[Cluster]:
    """Gom box CUNG LOP chong nhau (IoU >= merge_iou) — box = trung binh co trong so diem, score = max."""
    out: list[Cluster] = []
    for c in sorted({d.cls for d in dets if d.cls is not None}):
        pool = sorted((d for d in dets if d.cls == c), key=lambda d: -d.score)
        used = [False] * len(pool)
        for i, d in enumerate(pool):
            if used[i]:
                continue
            members = [d]
            used[i] = True
            for j in range(i + 1, len(pool)):
                if not used[j] and iou(d.box, pool[j].box) >= th.merge_iou:
                    members.append(pool[j])
                    used[j] = True
            w = np.array([m.score for m in members])
            box = (np.stack([m.box for m in members]) * w[:, None]).sum(0) / w.sum()
            out.append(Cluster(cls=c, box=box, score=float(w.max()), conf=float(max(m.conf for m in members)),
                               models={m.model for m in members}))
    return out


@dataclass
class Decision:
    status: str                       # accept | review | reject
    clusters: list[Cluster]           # box GIU LAI (score >= 1, khong bi loai)
    reasons: list[str]
    target_score: float = 0.0


def decide(dets: list[Det], target: str | None, th: Thresholds | None = None, concept: str | None = None,
           require_laptop_keyboard: bool = True) -> Decision:
    """
    target = ten lop anh duoc cao cho (vd "cup"); None = anh negative / hard negative.
    concept = (chi anh HARD NEGATIVE) khai niem cua truy van, vd "wristwatch": box cua cac lop hay nham voi no
              (clock) chinh la loi model can hoc de TU CHOI -> bo qua, khong chan accept. Ngoai le: YOLO-World
              khong thay concept o dau ca ma box lop nham lai rat manh -> review (co the la vat the that).
    ACCEPT : (target) co box target score >= accept, (2 model dong y hoac >= strong), khong box nghi van,
             khong box bi loai vi hard negative / man hinh, laptop co ban phim (DATASET_SPEC B: laptop mo -> ca keyboard)
             (negative) khong co box 12 lop nao score >= 1 va khong box nghi van
    REVIEW : con lai nhung van co the dung sau khi nguoi sua
    REJECT : (target) khong thay target o dau ca
    """
    th = th or Thresholds()
    dets = with_scores(dets, th)
    pos = [d for d in dets if d.cls is not None]
    neg = [d for d in dets if d.hardneg is not None]
    clusters = merge(pos, th)
    reasons: list[str] = []
    if concept:
        ignore = confused_classes(concept)
        seen_concept = any(h.hardneg == concept and h.score >= th.low for h in neg)
        strong_ignored = [c for c in clusters if c.cls in ignore and c.score >= th.strong and len(c.models) >= 2]
        if strong_ignored and not seen_concept:
            reasons.append(f"khong thay '{concept}' nhung co {CLASSES[strong_ignored[0].cls]} rat ro — co the la vat that")
        clusters = [c for c in clusters if c.cls not in ignore]

    kept: list[Cluster] = []
    suppressed: list[Cluster] = []
    for cl in clusters:
        if cl.score < 1:
            continue
        name = CLASSES[cl.cls]
        killer = None
        for h in neg:
            if h.score < 1:
                continue
            if h.hardneg in HARDNEG_FOR.get(name, set()) and iou(cl.box, h.box) >= th.hardneg_iou \
                    and h.score >= cl.score - th.hardneg_margin:
                killer = f"{name} trung '{h.hardneg}'"
                break
            if h.hardneg in SCREEN_PROMPTS and name != "laptop" and contained(cl.box, h.box) >= th.screen_contain:
                killer = f"{name} nam trong '{h.hardneg}' (vat the tren man hinh/anh in — DATASET_SPEC D.4)"
                break
        if killer:
            suppressed.append(cl)
            reasons.append(killer)
        else:
            kept.append(cl)

    # box nghi van: score [low, 1) khong trung box da giu -> co the la vat the bi sot
    uncertain = [cl for cl in clusters if th.low <= cl.score < 1
                 and (cl.box[2] - cl.box[0]) * (cl.box[3] - cl.box[1]) >= th.min_uncertain_area
                 and not any(k.cls == cl.cls and iou(k.box, cl.box) >= th.merge_iou for k in kept)]
    if uncertain:
        reasons.append("box diem thap: " + ", ".join(sorted({CLASSES[u.cls] for u in uncertain})))

    if target is None:
        if kept:
            reasons.insert(0, "anh negative nhung co vat the: " + ", ".join(sorted({CLASSES[k.cls] for k in kept})))
            return Decision("review", kept, reasons)
        return Decision("review" if reasons else "accept", [], reasons)

    t = CLASSES.index(target)
    tk = [k for k in kept if k.cls == t]
    if not tk:
        if any(s.cls == t for s in suppressed):
            return Decision("reject", kept, reasons + [f"'{target}' chi la hard negative"])
        return Decision("reject", kept, reasons + [f"khong thay '{target}'"])
    best = max(tk, key=lambda k: k.score)
    # box LOP KHAC cung phai dang tin — vd ban nhua do bi 1 model goi la umbrella (gap khi chay thu 2026-11-23)
    weak = [k for k in kept if k.cls != t and k.score < th.accept and len(k.models) < 2]
    if weak:
        reasons.append("box lop khac chua chac: " + ", ".join(sorted({CLASSES[k.cls] for k in weak})))
    if require_laptop_keyboard and (CLASSES[t] == "laptop" or any(CLASSES[k.cls] == "laptop" for k in kept)):
        lap = [k for k in kept if CLASSES[k.cls] == "laptop"]
        kb = [k for k in kept if CLASSES[k.cls] == "keyboard"]
        if lap and not any(contained(k.box, la.box) > 0.5 for la in lap for k in kb):
            reasons.append("laptop chua co box keyboard (laptop mo phai gan ca 2 — kiem tra laptop dang gap?)")
    ok = (best.score >= th.accept and (len(best.models) >= 2 or best.score >= th.strong)
          and not suppressed and not uncertain and not reasons)
    if not ok and best.score < th.accept:
        reasons.insert(0, f"'{target}' diem chua du cao ({best.conf:.2f})")
    if not ok and len(best.models) < 2 and best.score < th.strong:
        reasons.append("chi 1 model thay muc tieu")
    return Decision("accept" if ok else "review", kept, reasons, best.score)


# ---------------------------------------------------------------------------
# TU DUYET anh REVIEW (thay cho duyet tay): cat vung quanh box chua chac -> phong to -> 2 model nhin lai.
# Box duoc xac nhan tren vung cat -> giu (diem moi cao hon); khong xac nhan -> xoa. Laptop: tim ban phim trong vung laptop.
# KHONG tu xu ly: box bi loai vi hard negative / nam trong man hinh (can nguoi nhin) -> giu review.
MAX_RECHECK = 6


def crop_window(box: np.ndarray, margin: float = 0.3, min_side: float = 0.2) -> np.ndarray:
    """Cua so cat (xyxy chuan hoa) quanh box: noi rong `margin` moi phia, toi thieu `min_side` canh anh."""
    x0, y0, x1, y1 = (float(v) for v in box)
    w, h = x1 - x0, y1 - y0
    w2, h2 = max(w * (1 + 2 * margin), min_side) / 2, max(h * (1 + 2 * margin), min_side) / 2
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    return np.clip(np.array([cx - w2, cy - h2, cx + w2, cy + h2]), 0, 1)


def uncrop(dets: list[Det], win: np.ndarray) -> list[Det]:
    """Doi toa do box tu vung cat ve anh goc."""
    wx, wy = win[2] - win[0], win[3] - win[1]
    out = []
    for d in dets:
        b = np.array([win[0] + d.box[0] * wx, win[1] + d.box[1] * wy, win[0] + d.box[2] * wx, win[1] + d.box[3] * wy])
        out.append(Det(b, d.conf, d.model, cls=d.cls, hardneg=d.hardneg))
    return out


def auto_resolve(dets: list[Det], target: str | None, th: Thresholds, run_windows) -> tuple[Decision, str]:
    """
    run_windows(list[cua so]) -> list[list[Det]] da doi ve toa do anh goc (script cat anh + chay 2 model).
    Tra (quyet dinh moi, ghi chu). Chi anh ACCEPT o day moi duoc dua vao train.
    """
    d0 = decide(dets, target, th)
    if d0.status != "review":
        return d0, "khong doi"
    if any(" trung '" in r or " nam trong '" in r or "khong thay '" in r for r in d0.reasons):
        return d0, "hard negative / man hinh — can nguoi xem"
    dets = with_scores(dets, th)
    clusters = merge([d for d in dets if d.cls is not None], th)
    check = [c for c in clusters if th.low <= c.score < th.strong]
    laptops = [c for c in clusters if CLASSES[c.cls] == "laptop" and c.score >= 1]
    check = sorted(check, key=lambda c: -float((c.box[2] - c.box[0]) * (c.box[3] - c.box[1])))[:MAX_RECHECK]
    todo = check + [la for la in laptops if all(la is not c for c in check)]
    if not todo:
        return d0, "khong co box nao de kiem lai"
    found = run_windows([crop_window(c.box) for c in todo])
    new = list(dets)
    notes = []
    for c, fd in zip(todo, found):
        fd = with_scores(fd, th)
        if any(c is k for k in check):  # so sanh dataclass co np.array bang == se loi -> dung `is`
            members = [d for d in new if d.cls == c.cls and iou(d.box, c.box) >= 0.4]
            same = [x for x in fd if x.cls == c.cls and iou(x.box, c.box) >= 0.3 and x.score >= 1]
            new = [d for d in new if all(d is not m for m in members)]
            if same:  # xac nhan -> thay box cu bang box tren vung cat (chi 1 vat the, khong tao box trung)
                best = max(same, key=lambda x: x.score)
                new += [best] + [x for x in same if x is not best and iou(x.box, best.box) >= th.merge_iou]
                new += [m for m in members if iou(m.box, best.box) >= th.merge_iou]
                notes.append(f"+{CLASSES[c.cls]}")
            else:
                notes.append(f"-{CLASSES[c.cls]}")
        if CLASSES[c.cls] == "laptop":  # ban phim nam trong laptop (laptop mo) -> them box keyboard
            kb = [x for x in fd if x.cls is not None and CLASSES[x.cls] == "keyboard" and x.score >= 1
                  and contained(x.box, c.box) > 0.5]
            if kb:
                new.append(max(kb, key=lambda x: x.score))
                notes.append("+keyboard trong laptop")
    # Laptop khong tim thay ban phim ca khi phong to -> coi la laptop gap/goc nghieng (sai so chap nhan, ghi trong bao cao)
    d1 = decide(new, target, th, require_laptop_keyboard=False)
    return d1, " ".join(notes)


def to_yolo_lines(clusters: list[Cluster], index_of: list[int] | None = None) -> list[tuple]:
    """Cluster -> (cls, cx, cy, w, h). `index_of[c]` = chi so lop c trong file ten lop dich (neu khac thu tu CLASSES)."""
    out = []
    for cl in clusters:
        x1, y1, x2, y2 = (float(np.clip(v, 0, 1)) for v in cl.box)
        if x2 - x1 <= 0 or y2 - y1 <= 0:
            continue
        c = index_of[cl.cls] if index_of else cl.cls
        out.append((c, (x1 + x2) / 2, (y1 + y2) / 2, x2 - x1, y2 - y1))
    return out


# ---------------------------------------------------------------------------
# Metadata tu dong (DATASET_SPEC muc E) + loc chat luong anh
# ---------------------------------------------------------------------------
def estimate_lighting(img: Image.Image) -> str:
    """dark (toi/ban dem) | warm (den vang) | day. Khong phan biet duoc huynh quang -> day."""
    a = np.asarray(img.convert("RGB").resize((128, 128)), dtype=np.float32) / 255.0
    lum = float((0.299 * a[..., 0] + 0.587 * a[..., 1] + 0.114 * a[..., 2]).mean())
    if lum < 0.22:
        return "dark"
    r, b = float(a[..., 0].mean()), float(a[..., 2].mean())
    if r / max(b, 1e-3) > 1.35 and lum < 0.6:
        return "warm"
    return "day"


def estimate_distance(clusters: list[Cluster], target: str | None) -> str:
    """Theo dien tich box muc tieu lon nhat: >= 20% anh -> near, >= 3% -> mid, nho hon -> far."""
    if target is None:
        return ""
    t = CLASSES.index(target)
    areas = [float((c.box[2] - c.box[0]) * (c.box[3] - c.box[1])) for c in clusters if c.cls == t]
    if not areas:
        return ""
    a = max(areas)
    return "near" if a >= 0.20 else "mid" if a >= 0.03 else "far"


def check_quality(img: Image.Image, min_long_side: int = 1024, max_aspect: float = 3.0) -> str | None:
    """Tra ly do loai (DATASET_SPEC muc E) hoac None neu dat."""
    w, h = img.size
    if max(w, h) < min_long_side:
        return f"anh nho ({max(w, h)}px < {min_long_side}px)"
    if max(w, h) / max(min(w, h), 1) > max_aspect:
        return "ti le khung qua dai (anh ghep/banner?)"
    a = np.asarray(img.convert("RGB").resize((160, 160)), dtype=np.float32) / 255.0
    border = np.concatenate([a[:8].reshape(-1, 3), a[-8:].reshape(-1, 3), a[:, :8].reshape(-1, 3), a[:, -8:].reshape(-1, 3)])
    mx, mn = border.max(1), border.min(1)
    white = (mx > 0.92) & ((mx - mn) < 0.08)
    if white.mean() > 0.75:
        return "nen trang studio (anh san pham)"
    if float(a.std()) < 0.04:
        return "anh gan nhu mot mau"
    return None

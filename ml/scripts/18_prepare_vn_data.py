"""
18 — Chuan bi lai tap anh Viet Nam cho lan train 9 lop (notebook snapget_vn_data.ipynb).

Lan train moi chi giu cac lop thuoc 6 quest cua y1 (QUEST_GROUPS trong snapget12/classes.py):
cup, bottle, book, chair, laptop, keyboard, umbrella, bicycle, motorcycle. Bo: potted plant, backpack, clock.

    # 1-2. Loc mot tap VN (snapget_vn_supplement hoac vn.zip da giai nen) con 9 lop
    python scripts/18_prepare_vn_data.py filter --src /content/vnprep/raw/vn --out /content/vnprep/vn_9

    # 3. Anh bo sung tu chup (bosung.zip): tu ve box + gan nhan + chia vao train/test con thieu cua vn
    python scripts/18_prepare_vn_data.py autolabel --src /content/vnprep/raw/bosung --vn /content/vnprep/vn_9 \
        --out /content/vnprep/bosung_auto --y1 .../y1-s/best.pt --coco yolo11x.pt

    # 4. Gop vn da loc + anh bo sung (uu tien nhan da soat tren Roboflow neu co) -> 1 tap VN
    python scripts/18_prepare_vn_data.py merge --vn /content/vnprep/vn_9 --bosung /content/vnprep/bosung_auto \
        [--reviewed /content/vnprep/raw/reviewed] --out /content/vnprep/vnreal/vn

Quy tac loc (filter):
  - Box cua lop bi bo -> xoa khoi file nhan; chi so lop doi theo thu tu 9 lop moi (data.yaml moi).
  - Anh co ten la lop bi bo (vd self_clock_0003):
      con box lop giu lai -> GIU, doi ten theo lop giu lai nhieu box nhat (so thu tu moi, khong trung);
      khong con box: train -> XOA; test -> thanh anh negative `self_neg_xxxx` (giu lai lam anh am) —
      `--drop-test` de xoa luon.
  - metadata.csv cap nhat ten file theo; file ten lop cu (classes.txt...) thay bang data.yaml 9 lop.

Gan nhan tu dong (autolabel) — CHI LA NHAN NHAP:
  - 2 detector: y1-s (da hoc anh VN) + YOLO11x COCO. Box cung lop chong nhau duoc gop; diem = conf / nguong.
  - Lop chinh lay tu TEN THU MUC trong bosung.zip -> nguong thap hon cho lop do (it bo sot).
  - Danh dau can soat: khong thay lop chinh, co box nghi van, anh am ma detector thay vat the.
  - Chia train/test: lap phan con thieu cua VN-test (moi nhom quest --test-target anh) truoc, con lai vao train.
    Anh chup lien nhau (cung vat the) duoc giu thanh khoi lien tuc de han che ro ri train <-> test.
  - Tap TEST phai duoc nguoi soat lai (DATASET_SPEC muc D.6): `merge` tu choi anh test chua soat tru khi
    `--allow-unreviewed-test`.
"""
from __future__ import annotations

import argparse
import csv
import json
import re
import shutil
import sys
from collections import Counter, defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from PIL import Image, ImageDraw, ImageOps  # noqa: E402

from snapget12.classes import CLASSES_Y1 as CLASSES12, DROPPED_CLASSES, EXTRA_CLASSES, QUEST_GROUPS  # noqa: E402
from snapget12.yolo import (  # noqa: E402
    clean_stem,
    find_names,
    label_path_for,
    load_class_map,
    norm_name,
    read_yolo_labels,
    write_yolo_labels,
)

ROOT = Path(__file__).resolve().parents[1]
_USED = {m for k, g in QUEST_GROUPS.items() if k not in EXTRA_CLASSES for m in g}
KEEP = [c for c in CLASSES12 if c in _USED]           # 9 lop, giu thu tu cu
DROPPED = list(DROPPED_CLASSES)                      # potted plant, backpack, clock
GROUP_OF = {m: k for k, g in QUEST_GROUPS.items() for m in g if m in KEEP}
GROUPS = list(dict.fromkeys(GROUP_OF.values()))      # 6 quest cua y1
NEG_TOKENS = ("neg", "hardneg")
NAMES_FILES = ("data.yaml", "data.yml", "classes.txt", "obj.names", "_darknet.labels")
IMG_EXTS = {".jpg", ".jpeg", ".png", ".webp", ".bmp", ".heic", ".heif"}
VN_NAME = re.compile(r"^(self|gg|kg)_([a-z_]+?)_(\d{4,})$")


# ---------------------------------------------------------------------------
# Tien ich chung
# ---------------------------------------------------------------------------
def token_of(cls: str) -> str:
    return cls.replace(" ", "_")


def parse_name(stem: str) -> tuple[str | None, str | None, int | None]:
    """'self_potted_plant_0042' -> ('self', 'potted plant', 42); neg/hardneg giu nguyen token."""
    m = VN_NAME.match(stem)
    if not m:
        return None, None, None
    tok = m.group(2)
    return m.group(1), (tok if tok in NEG_TOKENS else tok.replace("_", " ")), int(m.group(3))


def list_imgs(root: Path) -> list[Path]:
    return sorted(p for p in root.rglob("*") if p.is_file() and p.suffix.lower() in IMG_EXTS)


def find_root(base: Path) -> Path | None:
    """Thu muc nong nhat co train/images hoac test/images (file zip co the boc them 1-2 tang)."""
    cands = [p for p in [base, *base.rglob("*")] if p.is_dir()
             and ((p / "train" / "images").is_dir() or (p / "test" / "images").is_dir())]
    return min(cands, key=lambda p: len(p.parts)) if cands else None


def canon_names(root: Path, class_map: dict) -> list[str | None]:
    """Ten lop cua dataset -> ten chuan 12 lop (None = khong map duoc, box se bi bo)."""
    names = find_names(root)
    if names is None:
        print(f"  !! {root}: khong co data.yaml/classes.txt — gia dinh thu tu 12 lop chuan")
        names = list(CLASSES12)
    out = [class_map.get(norm_name(n)) for n in names]
    unknown = [n for n, c in zip(names, out) if c is None]
    if unknown:
        print(f"  !! ten lop khong map duoc (box se bi bo): {unknown}")
    return out


def write_data_yaml(root: Path) -> None:
    for pat in NAMES_FILES:
        for p in root.rglob(pat):
            p.unlink()
    lines = [f"nc: {len(KEEP)}", "names:"] + [f"  - {c}" for c in KEEP]
    (root / "data.yaml").write_text("\n".join(lines) + "\n", encoding="utf-8")


def read_meta(path: Path) -> tuple[list[str], list[dict]]:
    if not path.exists():
        return [], []
    with open(path, newline="", encoding="utf-8-sig") as f:
        r = csv.DictReader(f)
        return list(r.fieldnames or []), list(r)


def write_meta(path: Path, fields: list[str], rows: list[dict]) -> None:
    with open(path, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=fields, extrasaction="ignore")
        w.writeheader()
        for row in rows:
            w.writerow({k: row.get(k, "") for k in fields})


def max_numbers(root: Path) -> dict[tuple[str, str], int]:
    """So thu tu lon nhat dang dung cho moi (nguon, token) trong train + test — de dat ten moi khong trung."""
    out: dict[tuple[str, str], int] = defaultdict(int)
    for img in list_imgs(root):
        src, prim, num = parse_name(clean_stem(img.name))
        if src:
            key = (src, prim if prim in NEG_TOKENS else token_of(prim))
            out[key] = max(out[key], num)
    return out


def image_keys(stem: str, boxes) -> set[str]:
    """Nhom quest ma anh dong gop (theo box) + 'neg'/'hardneg' theo ten file."""
    _, prim, _ = parse_name(stem)
    keys = {GROUP_OF[KEEP[b[0]]] for b in boxes if 0 <= b[0] < len(KEEP)}
    if prim in NEG_TOKENS and not boxes:
        keys.add(prim)
    return keys


def count_split(root: Path, split: str) -> Counter:
    c: Counter = Counter()
    d = root / split / "images"
    if not d.is_dir():
        return c
    for img in list_imgs(d):
        boxes = read_yolo_labels(label_path_for(img))
        c["images"] += 1
        c.update(image_keys(clean_stem(img.name), boxes))
    return c


def print_counts(root: Path, targets: dict[str, int] | None = None) -> dict:
    out = {}
    for split in ("train", "test"):
        c = count_split(root, split)
        if not c:
            continue
        out[split] = dict(c)
        row = " | ".join(f"{k} {c.get(k, 0)}" for k in GROUPS + ["hardneg", "neg"])
        print(f"  [{split}] {c['images']} anh | {row}")
        if split == "test" and targets:
            short = {k: targets[k] - c.get(k, 0) for k in targets if targets[k] > c.get(k, 0)}
            print("  [test] con thieu so voi muc tieu: " + (", ".join(f"{k} {v}" for k, v in short.items()) or "khong"))
    return out


# ---------------------------------------------------------------------------
# 1-2. filter
# ---------------------------------------------------------------------------
def filter_dataset(src: Path, out: Path, class_map: dict, drop_test: bool = False) -> dict:
    root = find_root(src)
    if root is None:
        raise SystemExit(f"!! {src}: khong tim thay thu muc co train/images hoac test/images")
    canon = canon_names(root, class_map)
    if out.exists():
        shutil.rmtree(out)
    nums = max_numbers(root)
    renames: dict[str, str | None] = {}  # stem cu -> stem moi (None = xoa)
    rep = {"root": str(root), "deleted": Counter(), "renamed": Counter(), "toNegative": Counter(),
           "droppedBoxes": Counter(), "unknownBoxes": 0, "images": Counter()}

    for split in ("train", "test"):
        d = root / split / "images"
        if not d.is_dir():
            continue
        for img in list_imgs(d):
            stem = clean_stem(img.name)
            src_tag, prim, _ = parse_name(stem)
            kept, dropped = [], Counter()
            for c, x, y, w, h in read_yolo_labels(label_path_for(img)):
                name = canon[c] if 0 <= c < len(canon) else None
                if name in KEEP:
                    kept.append((KEEP.index(name), x, y, w, h))
                elif name is None:
                    rep["unknownBoxes"] += 1
                else:
                    dropped[name] += 1
            rep["droppedBoxes"].update(dropped)

            new_stem = stem
            if prim in DROPPED:
                if kept:
                    top = Counter(KEEP[b[0]] for b in kept).most_common(1)[0][0]
                    key = (src_tag, token_of(top))
                    rep["renamed"][split] += 1
                elif split == "test" and not drop_test:
                    key = (src_tag, "neg")
                    rep["toNegative"][split] += 1
                else:
                    renames[stem] = None
                    rep["deleted"][split] += 1
                    continue
                nums[key] += 1
                new_stem = f"{key[0]}_{key[1]}_{nums[key]:04d}"
            renames[stem] = new_stem
            ext = img.suffix.lower()
            dst = out / split / "images" / f"{new_stem}{ext}"
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(img, dst)
            write_yolo_labels(out / split / "labels" / f"{new_stem}.txt", kept)
            rep["images"][split] += 1

    # metadata.csv
    fields, rows = read_meta(root / "metadata.csv")
    if fields:
        new_rows = []
        for row in rows:
            old = clean_stem(row.get("filename", "").strip())
            if old in renames and renames[old] is None:
                continue
            if old in renames and renames[old] != old:
                row = {**row, "filename": renames[old] + Path(row["filename"].strip()).suffix.lower()}
            new_rows.append(row)
        write_meta(out / "metadata.csv", fields, new_rows)
    # file khac o goc (README.md, stats.json, data_sources.md...) giu nguyen
    for p in root.iterdir():
        if p.is_file() and p.name not in NAMES_FILES and p.name != "metadata.csv":
            shutil.copy2(p, out / p.name)
    readme = out / "README.md"
    if readme.exists():
        with open(readme, "a", encoding="utf-8") as f:
            f.write(f"\n\n> Đã lọc còn {len(KEEP)} lớp ({', '.join(KEEP)}); bỏ {', '.join(DROPPED)} "
                    "(scripts/18_prepare_vn_data.py). Chỉ số lớp theo `data.yaml`.\n")
    write_data_yaml(out)
    rep = {k: (dict(v) if isinstance(v, Counter) else v) for k, v in rep.items()}
    (out.parent / f"{out.name}_filter_report.json").write_text(json.dumps(rep, ensure_ascii=False, indent=1), encoding="utf-8")
    return rep


# ---------------------------------------------------------------------------
# 3. autolabel
# ---------------------------------------------------------------------------
def folder_class(parts: tuple[str, ...], class_map: dict) -> tuple[str | None, str | None]:
    """Lop chinh + split ep buoc tu cac tang thu muc (vd bosung/test/umbrella/x.jpg)."""
    split = next((p.lower() for p in parts if p.lower() in ("train", "test")), None)
    for p in reversed(parts):
        n = norm_name(p)
        if n in ("neg", "negative", "negatives"):
            return "neg", split
        if n in ("hardneg", "hard neg", "hard negative", "hard negatives"):
            return "hardneg", split
        c = class_map.get(n)
        if c:
            return c, split
    return None, split


def exif_info(im: Image.Image) -> tuple[str, str]:
    try:
        ex = im.getexif()
        phone = " ".join(str(ex.get(t, "")).strip() for t in (271, 272)).strip()  # Make Model
        when = str(ex.get_ifd(0x8769).get(36867, "") or ex.get(306, "") or "")  # DateTimeOriginal
        return phone.replace("\x00", ""), when.replace("\x00", "")
    except Exception:  # noqa: BLE001 — EXIF hong thi bo qua
        return "", ""


def auto_lighting(im: Image.Image) -> str:
    """Uoc luong tho (ghi cot note = auto): toi / den vang / ban ngay."""
    s = im.convert("RGB").resize((64, 64))
    px = list(s.getdata())
    lum = sum(0.299 * r + 0.587 * g + 0.114 * b for r, g, b in px) / len(px)
    if lum < 70:
        return "dark"
    r = sum(p[0] for p in px) / len(px)
    b = sum(p[2] for p in px) / len(px)
    return "warm" if r > 1.3 * max(b, 1) else "day"


def iou(a, b) -> float:
    ix = max(0.0, min(a[2], b[2]) - max(a[0], b[0]))
    iy = max(0.0, min(a[3], b[3]) - max(a[1], b[1]))
    inter = ix * iy
    u = (a[2] - a[0]) * (a[3] - a[1]) + (b[2] - b[0]) * (b[3] - b[1]) - inter
    return inter / u if u > 0 else 0.0


def merge_dets(dets: list[dict], merge_iou: float = 0.55) -> list[dict]:
    """dets: {cls, box xyxy chuan hoa, score, model}. Gop box CUNG LOP chong nhau (trung binh co trong so)."""
    out = []
    for c in sorted({d["cls"] for d in dets}):
        pool = sorted((d for d in dets if d["cls"] == c), key=lambda d: -d["score"])
        used = [False] * len(pool)
        for i, d in enumerate(pool):
            if used[i]:
                continue
            group = [d]
            used[i] = True
            for j in range(i + 1, len(pool)):
                if not used[j] and iou(d["box"], pool[j]["box"]) >= merge_iou:
                    group.append(pool[j])
                    used[j] = True
            wsum = sum(g["score"] for g in group)
            box = [sum(g["box"][k] * g["score"] for g in group) / wsum for k in range(4)]
            out.append({"cls": c, "box": box, "score": d["score"], "models": sorted({g["model"] for g in group})})
    return out


def decide(clusters: list[dict], main: str, keep_thr: float, main_thr: float, low: float = 0.6,
           min_area: float = 0.005) -> tuple[list[dict], list[str]]:
    """clusters co score = conf (chua chia nguong). Tra (box giu lai, ly do can soat)."""
    flags: list[str] = []
    if main in NEG_TOKENS:
        if any(c["score"] >= keep_thr * 1.5 for c in clusters):
            flags.append("anh_am_co_vat_the")
        return [], flags
    kept, unsure = [], []
    for c in clusters:
        thr = main_thr if c["cls"] == main else keep_thr
        r = c["score"] / thr
        area = (c["box"][2] - c["box"][0]) * (c["box"][3] - c["box"][1])
        if r >= 1:
            kept.append(c)
        elif r >= low and area >= min_area:
            unsure.append(c)
    if not any(c["cls"] == main for c in kept):
        flags.append("khong_thay_lop_chinh")
    if unsure:
        flags.append(f"box_nghi_van:{len(unsure)}")
    return kept, flags


def assign_splits(items: list[dict], existing: Counter, targets: dict[str, int]) -> dict[str, int]:
    """Lap phan con thieu cua test theo tung khoa (nhom quest / hardneg / neg). Anh co split ep buoc giu nguyen.
    Moi khoa: sap theo thoi diem chup (roi ten file) va lay mot KHOI LIEN TUC vao test -> anh chup lien nhau
    (thuong cung 1 vat the) khong bi chia sang 2 tap."""
    need = {k: max(0, targets.get(k, 0) - existing.get(k, 0)) for k in targets}
    for it in items:
        if it["force"]:
            it["split"] = it["force"]
            if it["split"] == "test" and it["key"] in need:
                need[it["key"]] = max(0, need[it["key"]] - 1)
    by_key: dict[str, list[dict]] = defaultdict(list)
    for it in items:
        if not it["force"]:
            by_key[it["key"]].append(it)
    for k, its in by_key.items():
        its.sort(key=lambda it: (it["when"] or "~", it["orig"]))
        n = min(need.get(k, 0), len(its))
        for i, it in enumerate(its):
            it["split"] = "test" if i < n else "train"
        need[k] = need.get(k, 0) - n
    return need


def draw_preview(img_path: Path, boxes: list[dict], title: str, out: Path) -> None:
    im = Image.open(img_path).convert("RGB")
    im.thumbnail((640, 640))
    W, H = im.size
    dr = ImageDraw.Draw(im)
    for b in boxes:
        x1, y1, x2, y2 = b["box"]
        dr.rectangle([x1 * W, y1 * H, x2 * W, y2 * H], outline=(255, 40, 40), width=3)
        dr.text((x1 * W + 3, y1 * H + 2), f"{b['cls']} {b['score']:.2f}", fill=(255, 255, 0))
    dr.rectangle([0, 0, W, 16], fill=(0, 0, 0))
    dr.text((3, 2), title, fill=(255, 255, 255))
    out.parent.mkdir(parents=True, exist_ok=True)
    im.save(out, quality=85)


def run_detectors(paths: list[Path], models: list[tuple[str, str]], imgsz: int, conf: float, device) -> list[list[dict]]:
    from ultralytics import YOLO

    dets: list[list[dict]] = [[] for _ in paths]
    for tag, weights in models:
        m = YOLO(weights)
        print(f"  detector {tag}: {weights}")
        for i, r in enumerate(m.predict([str(p) for p in paths], imgsz=imgsz, conf=conf, device=device,
                                        stream=True, verbose=False)):
            names = r.names
            for xyxy, cf, c in zip(r.boxes.xyxyn.tolist(), r.boxes.conf.tolist(), r.boxes.cls.tolist()):
                n = names[int(c)]
                if n in KEEP:
                    dets[i].append({"cls": n, "box": xyxy, "score": float(cf), "model": tag})
    return dets


def autolabel(args, class_map: dict) -> dict:
    src, vn, out = Path(args.src), Path(args.vn), Path(args.out)
    try:
        from pillow_heif import register_heif_opener  # anh iPhone .heic

        register_heif_opener()
    except ImportError:
        pass
    vn_root = find_root(vn) or vn
    if out.exists():
        shutil.rmtree(out)
    stage = out.parent / f"{out.name}_staging"
    if stage.exists():
        shutil.rmtree(stage)
    stage.mkdir(parents=True)

    items, skipped = [], []
    for img in list_imgs(src):
        rel = img.relative_to(src)
        main, force = folder_class(rel.parts[:-1], class_map)
        if main is None or main in DROPPED:
            skipped.append(f"{rel} ({'lop ' + main + ' da bo' if main else 'khong ro lop tu ten thu muc'})")
            continue
        try:
            raw = Image.open(img)
            phone, when = exif_info(raw)
            im = ImageOps.exif_transpose(raw).convert("RGB")
        except Exception as e:  # noqa: BLE001
            skipped.append(f"{rel} (khong mo duoc: {e})")
            continue
        sp = stage / f"{len(items):05d}.jpg"
        im.save(sp, quality=95)  # da xoay dung chieu (EXIF) -> toa do box khop moi cong cu
        key = main if main in NEG_TOKENS else GROUP_OF[main]
        items.append({"orig": str(rel), "stage": sp, "main": main, "key": key, "force": force,
                      "phone": phone, "when": when, "lighting": auto_lighting(im), "size": im.size})
    for s in skipped:
        print(f"  bo qua: {s}")
    if not items:
        raise SystemExit("!! khong co anh hop le trong bosung (xem cau truc thu muc trong notebook)")
    print(f"  {len(items)} anh: " + ", ".join(f"{k} {v}" for k, v in Counter(it["main"] for it in items).items()))

    models = []
    if args.y1 and Path(args.y1).exists():
        models.append(("y1", args.y1))
    elif args.y1:
        print(f"  !! khong thay {args.y1} — chi dung detector COCO")
    models.append(("coco", args.coco))
    dets = run_detectors([it["stage"] for it in items], models, args.imgsz, min(args.main_thr, args.keep_thr) * 0.6,
                         args.device)

    for it, d in zip(items, dets):
        kept, flags = decide(merge_dets(d), it["main"], args.keep_thr, args.main_thr)
        it["kept"], it["flags"] = kept, flags

    targets = {g: args.test_target for g in GROUPS}
    targets.update({"hardneg": args.hardneg_target, "neg": args.neg_target})
    existing = count_split(vn_root, "test")
    left = assign_splits(items, existing, targets)

    nums = max_numbers(vn_root)
    preview = Path(args.preview) if args.preview else out.parent / f"{out.name}_preview"
    if preview.exists():
        shutil.rmtree(preview)
    meta_rows, log_rows = [], []
    for it in items:
        tok = it["main"] if it["main"] in NEG_TOKENS else token_of(it["main"])
        nums[("self", tok)] += 1
        stem = f"self_{tok}_{nums[('self', tok)]:04d}"
        it["stem"] = stem
        dst = out / it["split"] / "images" / f"{stem}.jpg"
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(str(it["stage"]), dst)
        boxes = []
        for c in it["kept"]:
            x1, y1, x2, y2 = c["box"]
            boxes.append((KEEP.index(c["cls"]), (x1 + x2) / 2, (y1 + y2) / 2, x2 - x1, y2 - y1))
        write_yolo_labels(out / it["split"] / "labels" / f"{stem}.txt", boxes)
        main_boxes = [c for c in it["kept"] if c["cls"] == it["main"]]
        area = max(((c["box"][2] - c["box"][0]) * (c["box"][3] - c["box"][1]) for c in main_boxes), default=0)
        distance = "" if not main_boxes else ("near" if area >= 0.15 else "mid" if area >= 0.03 else "far")
        meta_rows.append({"filename": dst.name, "split": it["split"], "source": "self", "url": "", "scene": "other",
                          "lighting": it["lighting"], "distance": distance, "phone": it["phone"],
                          "note": "auto-label" + ("; can soat: " + ",".join(it["flags"]) if it["flags"] else "")})
        log_rows.append({"file": dst.name, "original": it["orig"], "split": it["split"], "class": it["main"],
                         "boxes": len(boxes), "flags": ";".join(it["flags"]),
                         "detail": " ".join(f"{c['cls']}:{c['score']:.2f}/{'+'.join(c['models'])}" for c in it["kept"])})
        if it["flags"]:
            draw_preview(dst, it["kept"], f"{dst.name} [{it['split']}] {','.join(it['flags'])}", preview / dst.name)

    write_meta(out / "metadata.csv", ["filename", "split", "source", "url", "scene", "lighting", "distance", "phone", "note"],
               meta_rows)
    write_meta(out.parent / f"{out.name}_log.csv", list(log_rows[0].keys()), log_rows)
    write_data_yaml(out)
    shutil.rmtree(stage, ignore_errors=True)

    flagged = sum(bool(it["flags"]) for it in items)
    summary = {"images": len(items), "skipped": len(skipped), "flagged": flagged,
               "split": dict(Counter(it["split"] for it in items)),
               "testAdded": dict(Counter(it["key"] for it in items if it["split"] == "test")),
               "testStillShort": {k: v for k, v in left.items() if v > 0}, "detectors": [m[0] for m in models]}
    (out.parent / f"{out.name}_summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=1), encoding="utf-8")
    return summary


# ---------------------------------------------------------------------------
# 4. merge
# ---------------------------------------------------------------------------
def load_reviewed(rdir: Path, class_map: dict) -> dict[str, list]:
    """Ban export YOLO tu Roboflow sau khi soat: stem goc -> box (chi so 9 lop). Anh bi xoa khi soat -> khong co."""
    canon = canon_names(rdir, class_map)
    out: dict[str, list] = {}
    for img in list_imgs(rdir):
        try:
            lab = label_path_for(img)
        except ValueError:
            continue
        boxes = [(KEEP.index(canon[c]), x, y, w, h) for c, x, y, w, h in read_yolo_labels(lab)
                 if 0 <= c < len(canon) and canon[c] in KEEP]
        out[clean_stem(img.name)] = boxes
    return out


def merge(args, class_map: dict) -> dict:
    vn_root = find_root(Path(args.vn)) or Path(args.vn)
    bos = find_root(Path(args.bosung)) or Path(args.bosung)
    out = Path(args.out)
    reviewed = load_reviewed(Path(args.reviewed), class_map) if args.reviewed else None
    if reviewed is not None:
        print(f"  dung nhan da soat: {len(reviewed)} anh")

    _, bmeta = read_meta(bos / "metadata.csv")
    notes = {r.get("filename", ""): r.get("note", "") for r in bmeta}
    plan, removed, unsure = [], [], []
    for split in ("train", "test"):
        d = bos / split / "images"
        if not d.is_dir():
            continue
        for img in list_imgs(d):
            stem = clean_stem(img.name)
            if reviewed is not None:
                if stem not in reviewed:
                    removed.append(img.name)  # bi xoa khi soat
                    continue
                boxes = reviewed[stem]
            else:
                # chua soat: anh khong thay lop chinh (nhan rong se day model sai) / anh am co vat the -> bo
                if any(f in notes.get(img.name, "") for f in ("khong_thay_lop_chinh", "anh_am_co_vat_the")):
                    unsure.append(img.name)
                    continue
                boxes = read_yolo_labels(label_path_for(img))
            plan.append((split, img, boxes))
    if unsure:
        print(f"  bo {len(unsure)} anh chua soat ma nhan tu dong khong chac (khong thay lop chinh / anh am co vat the)")
    if reviewed is None and any(s == "test" for s, _, _ in plan) and not args.allow_unreviewed_test:
        n = sum(s == "test" for s, _, _ in plan)
        raise SystemExit(
            f"!! {n} anh bo sung vao TEST chua duoc soat nhan. Nhan tu dong co the sot/sai box, dua vao test se lam "
            "so lieu danh gia sai lech (DATASET_SPEC muc D.6). Soat tren Roboflow roi dat bosung_reviewed.zip, "
            "hoac chay lai voi --allow-unreviewed-test neu chap nhan.")

    if out.exists():
        shutil.rmtree(out)
    shutil.copytree(vn_root, out)
    for split, img, boxes in plan:
        dst = out / split / "images" / img.name
        if dst.exists():
            raise SystemExit(f"!! trung ten {dst.name} giua vn va bosung — chay lai buoc 3 sau buoc 2")
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(img, dst)
        write_yolo_labels(out / split / "labels" / f"{img.stem}.txt", boxes)

    f1, r1 = read_meta(vn_root / "metadata.csv")
    f2, r2 = read_meta(bos / "metadata.csv")
    keep_names = {img.name for _, img, _ in plan}
    tag = "reviewed" if reviewed is not None else "auto-label (chua soat)"
    r2 = [{**r, "note": "auto-label, reviewed" if reviewed is not None else r.get("note", "").replace("auto-label", tag, 1)}
          for r in r2 if r.get("filename") in keep_names]
    fields = list(dict.fromkeys((f1 or ["filename", "split", "source", "url", "scene", "lighting", "distance", "phone"])
                                + f2))
    write_meta(out / "metadata.csv", fields, r1 + r2)
    write_data_yaml(out)
    for p in removed[:10]:
        print(f"  bo (da xoa khi soat): {p}")
    return {"added": dict(Counter(s for s, _, _ in plan)), "removedInReview": len(removed),
            "droppedUnreviewedUnsure": len(unsure), "labels": tag}


# ---------------------------------------------------------------------------
def main() -> None:
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    ap.add_argument("--class-map", default=str(ROOT / "yolo_class_map.yaml"))

    f = sub.add_parser("filter")
    f.add_argument("--src", required=True)
    f.add_argument("--out", required=True)
    f.add_argument("--drop-test", action="store_true", help="xoa luon anh test cua lop bi bo (mac dinh: thanh anh am)")

    a = sub.add_parser("autolabel")
    a.add_argument("--src", required=True, help="bosung da giai nen: <lop>/*.jpg, neg/, hardneg/ (tuy chon train/ test/)")
    a.add_argument("--vn", required=True, help="tap vn da loc (buoc 2) — de tinh phan thieu + so thu tu ten file")
    a.add_argument("--out", required=True)
    a.add_argument("--y1", default=None, help="best.pt cua y1-s (da hoc anh VN)")
    a.add_argument("--coco", default="yolo11x.pt")
    a.add_argument("--imgsz", type=int, default=960)
    a.add_argument("--keep-thr", type=float, default=0.35, help="nguong giu box cua lop phu")
    a.add_argument("--main-thr", type=float, default=0.15, help="nguong giu box cua lop chinh (ten thu muc)")
    a.add_argument("--test-target", type=int, default=85, help="so anh VN-test moi nhom quest (~50 anh real_test)")
    a.add_argument("--hardneg-target", type=int, default=60)
    a.add_argument("--neg-target", type=int, default=0)
    a.add_argument("--preview", default=None)
    a.add_argument("--device", default=None)

    m = sub.add_parser("merge")
    m.add_argument("--vn", required=True)
    m.add_argument("--bosung", required=True)
    m.add_argument("--reviewed", default=None, help="ban export YOLO tu Roboflow sau khi soat (da giai nen)")
    m.add_argument("--allow-unreviewed-test", action="store_true")
    m.add_argument("--out", required=True)
    m.add_argument("--test-target", type=int, default=85)
    args = ap.parse_args()

    class_map = load_class_map(args.class_map)
    print(f"Giu {len(KEEP)} lop: {KEEP} | bo: {DROPPED}")
    if args.cmd == "filter":
        rep = filter_dataset(Path(args.src), Path(args.out), class_map, args.drop_test)
        print(json.dumps({k: rep[k] for k in ("images", "deleted", "renamed", "toNegative", "droppedBoxes")}, ensure_ascii=False))
        print_counts(Path(args.out), {g: 85 for g in GROUPS})
    elif args.cmd == "autolabel":
        s = autolabel(args, class_map)
        print(json.dumps(s, ensure_ascii=False, indent=1))
    else:
        s = merge(args, class_map)
        print(json.dumps(s, ensure_ascii=False))
        print_counts(Path(args.out), {g: args.test_target for g in GROUPS})
    print("OK")


if __name__ == "__main__":
    main()

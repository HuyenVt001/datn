"""
11 — Gop du lieu ngoai (Kaggle / anh Google da gan nhan / Open Images) + tap VIET NAM vao dataset
YOLO chung, KIEM TRA tap VN theo ml/DATASET_SPEC.md (muc F), chong trung lap + ro ri test,
roi sinh danh sach anh + data.yaml cho Ultralytics.

    python scripts/11_merge_external.py --root /content/yolo --coco /content/yolo/coco \
        --yolo /content/ext/kaggle_cup --yolo /content/ext/google_labeled \
        --coco-json /content/ext/ds2/_annotations.coco.json /content/ext/ds2 \
        --voc /content/ext/ds3 \
        --vn /content/ext/vn --class-map yolo_class_map.yaml

Nguon ngoai (lap lai duoc nhieu lan):
  --yolo DIR             cau truc .../images/x.jpg + .../labels/x.txt (Roboflow, Label Studio, script 12).
                         BAT BUOC co file ten lop trong DIR: data.yaml | classes.txt | obj.names | _darknet.labels
  --coco-json JSON IMGDIR  annotation COCO (bbox pixel) + thu muc anh
  --voc DIR              Pascal VOC (Annotations/*.xml + anh)
  --vn DIR               tap VN theo DATASET_SPEC (train/ + test/ + metadata.csv) — DUY NHAT nguon vao TEST that.
                         Lap lai duoc: --vn <anh tu chup> --vn <anh cao tu dong cua 17_crawl_autolabel.py (chi train/)>
Ten lop -> 12 lop chuan qua --class-map (yolo_class_map.yaml). Box lop la bi BO + in canh bao.
Anh ngoai ma MOI box deu bi bo -> LOAI anh (co the chua vat the 12 lop ma khong duoc gan = nhan sai).

Ra trong --root:
  external/{images,labels}/{train,val}/   anh ngoai (symlink) + nhan da doi chi so
  vn/{images,labels}/{train,test}/        tap VN
  lists/train.txt  val.txt  coco_test.txt  real_calib.txt  real_test.txt  real_all.txt
  data.yaml (train), real_calib.yaml, real_test.yaml, coco_test.yaml (danh gia)
  merge_report.json                        thong ke + canh bao (dua vao bao cao)
"""
from __future__ import annotations

import argparse
import csv
import json
import os
import random
import re
import shutil
import sys
import xml.etree.ElementTree as ET
from collections import Counter, defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np  # noqa: E402
from PIL import Image, ImageOps  # noqa: E402

from snapget12 import CLASSES  # noqa: E402
from snapget12.yolo import (  # noqa: E402
    IMAGE_EXTS,
    clean_stem,
    dhash,
    find_names,
    label_path_for,
    list_images,
    load_class_map,
    map_class,
    near_duplicates,
    read_yolo_labels,
    validate_boxes,
    write_yolo_labels,
)

VN_NAME = re.compile(r"^(self|gg|kg)_([a-z_]+?)_(\d{4})$")
VN_SCENES = {"home", "classroom", "cafe", "street", "parking", "office", "other"}
VN_LIGHTING = {"day", "warm", "fluorescent", "dark"}
VN_DISTANCE = {"near", "mid", "far"}


class Report:
    def __init__(self) -> None:
        self.data: dict = {"sources": {}, "warnings": [], "errors": []}

    def warn(self, msg: str) -> None:
        print("  ⚠ " + msg)
        self.data["warnings"].append(msg)

    def error(self, msg: str) -> None:
        print("  ✖ " + msg)
        self.data["errors"].append(msg)


def link_or_copy(src: Path, dst: Path) -> None:
    dst.parent.mkdir(parents=True, exist_ok=True)
    if dst.exists() or dst.is_symlink():
        dst.unlink()
    try:
        os.symlink(src.resolve(), dst)
    except OSError:
        shutil.copyfile(src, dst)


def image_size(path: Path) -> tuple[int, int] | None:
    try:
        with Image.open(path) as im:
            im = ImageOps.exif_transpose(im)
            return im.size
    except Exception:
        return None


def hash_file(path: Path) -> int | None:
    try:
        with Image.open(path) as im:
            return dhash(ImageOps.exif_transpose(im))
    except Exception:
        return None


# ---------------------------------------------------------------------------
# Doc cac dinh dang nguon -> list (anh, [(ten_lop_goc, cx, cy, w, h)])
# ---------------------------------------------------------------------------
def read_yolo_dir(root: Path, rep: Report) -> list[tuple[Path, list | None]]:
    names = find_names(root)
    if names is None:
        raise SystemExit(f"!! {root}: khong tim thay data.yaml/classes.txt/obj.names — khong biet chi so lop la gi. "
                         "Them file ten lop (moi dong 1 ten, dung thu tu chi so) roi chay lai.")
    out = []
    for img in list_images(root):
        try:
            lab = label_path_for(img)
        except ValueError:
            lab = img.with_suffix(".txt")
        if not lab.exists():
            out.append((img, None))  # chua gan nhan
            continue
        boxes = []
        for c, x, y, w, h in read_yolo_labels(lab):
            if not 0 <= c < len(names):
                rep.warn(f"{lab}: class id {c} vuot so ten lop ({len(names)}) — bo box")
                continue
            boxes.append((names[c], x, y, w, h))
        out.append((img, boxes))
    return out


def read_coco_json(js: str, imgdir: str) -> list[tuple[Path, list]]:
    data = json.loads(Path(js).read_text(encoding="utf-8"))
    imgdir = Path(imgdir)
    cats = {c["id"]: c["name"] for c in data["categories"]}
    imgs = {i["id"]: i for i in data["images"]}
    per: dict[int, list] = defaultdict(list)
    for a in data.get("annotations", []):
        if a.get("iscrowd"):
            continue
        im = imgs[a["image_id"]]
        x, y, w, h = a["bbox"]
        W, H = im["width"], im["height"]
        if w <= 0 or h <= 0:
            continue
        per[a["image_id"]].append((cats[a["category_id"]], (x + w / 2) / W, (y + h / 2) / H, w / W, h / H))
    out = []
    for iid, im in imgs.items():
        p = imgdir / im["file_name"]
        if not p.exists():
            hits = list(imgdir.rglob(Path(im["file_name"]).name))
            if not hits:
                continue
            p = hits[0]
        out.append((p, per.get(iid, [])))
    return out


def read_voc_dir(root: Path) -> list[tuple[Path, list]]:
    out = []
    by_name = {p.name: p for p in list_images(root)}
    for xmlp in sorted(root.rglob("*.xml")):
        t = ET.parse(xmlp).getroot()
        fn = (t.findtext("filename") or "").strip()
        img = by_name.get(fn) or by_name.get(xmlp.with_suffix(".jpg").name)
        size = t.find("size")
        if img is None or size is None:
            continue
        W, H = float(size.findtext("width")), float(size.findtext("height"))
        if W <= 0 or H <= 0:
            continue
        boxes = []
        for o in t.findall("object"):
            bb = o.find("bndbox")
            x1, y1, x2, y2 = (float(bb.findtext(k)) for k in ("xmin", "ymin", "xmax", "ymax"))
            if x2 > x1 and y2 > y1:
                boxes.append((o.findtext("name"), (x1 + x2) / 2 / W, (y1 + y2) / 2 / H, (x2 - x1) / W, (y2 - y1) / H))
        out.append((img, boxes))
    return out


def remap(items, class_map, unknown: Counter):
    """(anh, boxes ten goc) -> (anh, boxes chi so 12 lop, so box bi bo)."""
    out = []
    for img, boxes in items:
        if boxes is None:
            out.append((img, None, 0))
            continue
        kept, dropped = [], 0
        for name, x, y, w, h in boxes:
            idx = map_class(name, class_map)
            if idx is None:
                dropped += 1
                key = str(name).strip().lower()
                if key not in class_map:
                    unknown[key] += 1
                continue
            kept.append((idx, min(max(x, 0), 1), min(max(y, 0), 1), min(w, 1), min(h, 1)))
        out.append((img, kept, dropped))
    return out


# ---------------------------------------------------------------------------
# Tap VN — kiem tra theo DATASET_SPEC muc F
# ---------------------------------------------------------------------------
def vn_primary(stem: str) -> tuple[str | None, str | None]:
    m = VN_NAME.match(stem)
    if not m:
        return None, None
    src, cls = m.group(1), m.group(2)
    if cls in ("neg", "hardneg"):
        return src, cls
    cls = cls.replace("_", " ")
    return src, cls if cls in CLASSES else None


def load_vn(vn_dirs: list[Path], class_map, rep: Report, args) -> dict:
    """Nhieu thu muc VN (vd anh tu chup + anh cao tu dong 17_crawl_autolabel.py) -> gop roi kiem tra SO LUONG
    tren tong. Metadata kiem tra rieng tung thu muc (moi thu muc co metadata.csv cua no)."""
    res: dict = {"train": [], "test": []}
    for vn in vn_dirs:
        part = load_vn_dir(vn, class_map, rep)
        check_metadata(vn, part, rep)
        for k in res:
            res[k] += part[k]
    validate_vn_counts(res, rep, args)
    return res


def load_vn_dir(vn: Path, class_map, rep: Report) -> dict:
    if not (vn / "train" / "images").exists() and not (vn / "test" / "images").exists():
        rep.error(f"VN: {vn} khong co train/images lan test/images — sai duong dan hoac sai cau truc (DATASET_SPEC muc E)")
        return {"train": [], "test": []}
    names = find_names(vn)
    if names is None:
        rep.warn(f"{vn}: khong co classes.txt/data.yaml — gia dinh chi so lop THEO DUNG THU TU 12 lop chuan {CLASSES}")
        names = list(CLASSES)
    idx_of = []
    for n in names:
        i = map_class(n, class_map)
        if i is None:
            rep.error(f"VN: ten lop '{n}' trong file ten lop khong map duoc ve 12 lop chuan")
        idx_of.append(i)

    res: dict = {"train": [], "test": []}
    for split in ("train", "test"):
        d = vn / split / "images"
        if not d.exists():
            print(f"  (VN {vn.name}: khong co {split}/ — bo qua)")  # vd thu muc anh cao chi co train/
            continue
        for img in list_images(d):
            stem = clean_stem(img.name)
            src, prim = vn_primary(stem)
            if src is None:
                rep.warn(f"VN {split}: ten file '{img.name}' sai quy uoc <self|gg|kg>_<lop|neg|hardneg>_<4 so>")
            elif prim is None:
                rep.warn(f"VN {split}: ten file '{img.name}' co ten lop khong thuoc 12 lop (lop co dau cach dung '_', vd potted_plant)")
            lab = label_path_for(img)
            is_neg = prim in ("neg", "hardneg")
            if not lab.exists():
                if is_neg:
                    boxes = []
                else:
                    rep.error(f"VN {split}: thieu file nhan {lab.name} (anh khong phai neg)")
                    continue
            else:
                raw = read_yolo_labels(lab)
                bad = [c for c, *_ in raw if not 0 <= c < len(idx_of) or idx_of[c] is None]
                if bad:
                    rep.error(f"VN {split}: {lab.name} co class id khong hop le {sorted(set(bad))}")
                    continue
                boxes = [(idx_of[c], x, y, w, h) for c, x, y, w, h in raw]
                errs = validate_boxes(boxes)
                if errs:
                    rep.error(f"VN {split}: {lab.name}: " + "; ".join(errs[:3]))
                    continue
            if is_neg and boxes:
                rep.warn(f"VN {split}: {img.name} dat ten negative nhung co {len(boxes)} box — kiem tra lai")
            if not is_neg and not boxes:
                rep.warn(f"VN {split}: {img.name} khong co box nao nhung ten khong phai neg/hardneg — kiem tra lai")
            if split == "test" and src in ("gg", "kg"):
                rep.error(f"VN test: {img.name} — tap TEST phai 100% tu chup (self_), khong dung anh Google/Kaggle")
            size = image_size(img)
            if size is None:
                rep.error(f"VN {split}: khong mo duoc anh {img.name}")
                continue
            if max(size) < 1024:
                rep.warn(f"VN {split}: {img.name} canh dai {max(size)}px < 1024px")
            if prim is not None and not is_neg and boxes and CLASSES.index(prim) not in {b[0] for b in boxes}:
                rep.warn(f"VN {split}: {img.name} ten lop '{prim}' nhung khong co box lop do")
            res[split].append({"img": img, "boxes": boxes, "src": src, "primary": prim or (CLASSES[boxes[0][0]] if boxes else "neg"),
                               "neg": is_neg or not boxes, "hardneg": prim == "hardneg", "size": size})
    return res


def validate_vn_counts(res: dict, rep: Report, args) -> None:
    """So luong (DATASET_SPEC muc A) + ti le anh doc + cap de nham — tren TONG cac thu muc VN."""
    if not res["test"]:
        rep.warn("VN: chua co anh test nao (test/images) — real_calib/real_test se rong")
    for split, (hard_min, soft_min, neg_min) in {"train": (args.vn_min_train, 150, 300), "test": (args.vn_min_test, 50, 100)}.items():
        rows = res[split]
        per = {c: sum(i in {b[0] for b in r["boxes"]} for r in rows) for i, c in enumerate(CLASSES)}
        negs = sum(r["neg"] for r in rows)
        hard = sum(r["hardneg"] for r in rows)
        rep.data.setdefault("vn", {})[split] = {"images": len(rows), "imagesPerClass": per, "negatives": negs, "hardNegatives": hard}
        print(f"[VN {split}] {len(rows)} anh | anh/lop {per} | negative {negs} (hard {hard})")
        for c, n in per.items():
            if n < hard_min:
                rep.error(f"VN {split}: lop '{c}' chi {n} anh (toi thieu {hard_min})")
            elif n < soft_min:
                rep.warn(f"VN {split}: lop '{c}' {n} anh (dac ta khuyen nghi >= {soft_min})")
        if negs < neg_min:
            rep.warn(f"VN {split}: {negs} anh negative (dac ta >= {neg_min}, trong do >= {neg_min // 2} hard negative)")
        if hard < neg_min // 2:
            rep.warn(f"VN {split}: {hard} hard negative (dac ta >= {neg_min // 2})")
        self_rows = [r for r in rows if r["src"] == "self"]
        if self_rows:
            portrait = sum(r["size"][1] > r["size"][0] for r in self_rows) / len(self_rows)
            if portrait < 0.7:
                rep.warn(f"VN {split}: chi {portrait:.0%} anh tu chup la anh doc (dac ta >= 70%)")
        pairs = {("laptop", "keyboard"), ("bicycle", "motorcycle"), ("cup", "bottle")}
        for a, b in pairs:
            ia, ib = CLASSES.index(a), CLASSES.index(b)
            n = sum({ia, ib} <= {x[0] for x in r["boxes"]} for r in rows)
            if n == 0:
                rep.warn(f"VN {split}: khong co anh nao chua CA {a} + {b} cung khung (dac ta muc C bat buoc co)")


def check_metadata(vn: Path, res: dict, rep: Report) -> None:
    mp = vn / "metadata.csv"
    if not mp.exists():
        rep.warn("VN: thieu metadata.csv — khong kiem tra duoc do da dang (anh sang/khoang cach/boi canh) va khong bao cao theo dieu kien")
        return
    with open(mp, newline="", encoding="utf-8-sig") as f:
        meta = {clean_stem(row["filename"].strip()): row for row in csv.DictReader(f)}
    for split in ("train", "test"):
        rows = res[split]
        missing = [r["img"].name for r in rows if clean_stem(r["img"].name) not in meta]
        if missing:
            rep.warn(f"VN {split}: {len(missing)} anh khong co dong trong metadata.csv (vd {missing[:3]})")
        by_cls: dict[str, list[dict]] = defaultdict(list)
        for r in rows:
            m = meta.get(clean_stem(r["img"].name))
            if m and not r["neg"]:
                by_cls[r["primary"]].append(m)
        for c, ms in by_cls.items():
            bad = [m["filename"] for m in ms if m.get("lighting", "").strip() not in VN_LIGHTING
                   or m.get("distance", "").strip() not in VN_DISTANCE or m.get("scene", "").strip() not in VN_SCENES]
            if bad:
                rep.warn(f"VN {split}/{c}: {len(bad)} dong metadata co gia tri ngoai danh muc (vd {bad[:2]})")
            n = len(ms)
            dark = sum(m.get("lighting", "").strip() == "dark" for m in ms) / n
            dist = Counter(m.get("distance", "").strip() for m in ms)
            scenes = {m.get("scene", "").strip() for m in ms}
            phones = {m.get("phone", "").strip().lower() for m in ms if m.get("phone", "").strip()}
            if dark < 0.15:
                rep.warn(f"VN {split}/{c}: anh toi/ban dem {dark:.0%} (dac ta >= 15%)")
            if dist["far"] / n < 0.1 or dist["near"] / n < 0.25:
                rep.warn(f"VN {split}/{c}: khoang cach near {dist['near'] / n:.0%} / mid {dist['mid'] / n:.0%} / far {dist['far'] / n:.0%} (dac ta ~40/40/20)")
            if len(scenes) < 4:
                rep.warn(f"VN {split}/{c}: chi {len(scenes)} boi canh (dac ta >= 4)")
            if len(phones) < 3 and split == "test":
                rep.warn(f"VN test/{c}: chi {len(phones)} mau dien thoai (dac ta >= 3)")


# ---------------------------------------------------------------------------
def stratified_split(rows: list[dict], ratio: float, seed: int) -> tuple[list[dict], list[dict]]:
    rng = random.Random(seed)
    groups: dict[str, list[dict]] = defaultdict(list)
    for r in rows:
        groups[r["primary"]].append(r)
    a, b = [], []
    for k in sorted(groups):
        g = groups[k][:]
        rng.shuffle(g)
        n = int(round(len(g) * ratio))
        a += g[:n]
        b += g[n:]
    return a, b


def write_list(path: Path, paths: list) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("".join(f"{Path(p).as_posix()}\n" for p in paths), encoding="utf-8")


def write_yaml(path: Path, root: Path, train: str, val: str, test: str) -> None:
    names = "\n".join(f"  {i}: {c}" for i, c in enumerate(CLASSES))
    path.write_text(f"# sinh boi 11_merge_external.py — 12 lop THEO DUNG THU TU ml/snapget12/classes.py\n"
                    f"path: {root.as_posix()}\ntrain: {train}\nval: {val}\ntest: {test}\nnames:\n{names}\n", encoding="utf-8")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default="/content/yolo")
    ap.add_argument("--coco", default="/content/yolo/coco", help="output cua 10_export_coco_yolo.py")
    ap.add_argument("--yolo", action="append", default=[])
    ap.add_argument("--coco-json", action="append", default=[], nargs=2, metavar=("JSON", "IMGDIR"))
    ap.add_argument("--voc", action="append", default=[])
    ap.add_argument("--vn", action="append", default=[], help="thu muc tap VN theo DATASET_SPEC (lap lai duoc: anh tu chup + anh cao 17)")
    ap.add_argument("--class-map", default=str(Path(__file__).resolve().parents[1] / "yolo_class_map.yaml"))
    ap.add_argument("--vn-repeat", type=int, default=3, help="lap anh VN-train N lan trong train.txt (can bang voi COCO)")
    ap.add_argument("--vn-val-ratio", type=float, default=0.15, help="ti le VN-train dua vao val (chon best.pt theo anh VN)")
    ap.add_argument("--ext-val-ratio", type=float, default=0.1)
    ap.add_argument("--calib-ratio", type=float, default=0.4, help="VN-test -> real-calib (chon nguong) / real-test (bao cao)")
    ap.add_argument("--vn-min-train", type=int, default=100)
    ap.add_argument("--vn-min-test", type=int, default=20)
    ap.add_argument("--dup-dist", type=int, default=5, help="Hamming dHash <= muc nay = anh gan trung")
    ap.add_argument("--no-strict", action="store_true", help="chi canh bao, khong dung khi tap VN co loi")
    ap.add_argument("--seed", type=int, default=42)
    args = ap.parse_args()

    root, coco = Path(args.root), Path(args.coco)
    rep = Report()
    class_map = load_class_map(args.class_map)
    rng = random.Random(args.seed)

    # 1) Nguon ngoai
    unknown: Counter = Counter()
    ext: list[dict] = []
    sources = [("yolo", s) for s in args.yolo] + [("coco", s) for s in args.coco_json] + [("voc", s) for s in args.voc]
    for k, (kind, spec) in enumerate(sources):
        tag = f"x{k}"
        print(f"[ext {tag}] {kind}: {spec}")
        items = (read_yolo_dir(Path(spec), rep) if kind == "yolo" else read_coco_json(*spec) if kind == "coco" else read_voc_dir(Path(spec)))
        mapped = remap(items, class_map, unknown)
        kept = unlabeled = all_dropped = 0
        for img, boxes, dropped in mapped:
            if boxes is None:
                unlabeled += 1
                continue
            if not boxes:
                if dropped:
                    all_dropped += 1
                    continue
                # anh goc KHONG co box nao = negative that cua dataset do -> giu
            if image_size(img) is None or min(image_size(img)) < 32:
                continue
            ext.append({"img": img, "boxes": boxes, "tag": tag, "primary": CLASSES[boxes[0][0]] if boxes else "neg"})
            kept += 1
        rep.data["sources"][tag] = {"kind": kind, "spec": spec, "kept": kept, "unlabeled": unlabeled, "droppedAllBoxes": all_dropped}
        print(f"   giu {kept} anh | chua gan nhan {unlabeled} (bo qua — chay 12_prelabel.py) | bo {all_dropped} anh vi moi box ngoai 12 lop")
    if unknown:
        rep.warn("Ten lop KHONG co trong yolo_class_map.yaml (da bo): " + ", ".join(f"{n}×{c}" for n, c in unknown.most_common(30))
                 + " — them vao file map neu la 1 trong 12 lop")

    # 2) Tap VN
    vn = load_vn([Path(v) for v in args.vn], class_map, rep, args) if args.vn else {"train": [], "test": []}
    if not args.vn:
        rep.warn("Chua co --vn: real_calib/real_test se rong — 14_eval_yolo.py se chon nguong tren COCO val (tam thoi)")
    if rep.data["errors"] and not args.no_strict:
        (root / "merge_report.json").parent.mkdir(parents=True, exist_ok=True)
        (root / "merge_report.json").write_text(json.dumps(rep.data, indent=2, ensure_ascii=False, default=str), encoding="utf-8")
        raise SystemExit(f"!! {len(rep.data['errors'])} loi o tap VN (xem tren / merge_report.json). Sua roi chay lai, "
                         "hoac --no-strict de bo qua (KHONG khuyen nghi).")

    # 3) Khu trung trong train ngoai + chong ro ri sang test (VN-test, COCO test)
    train_pool = ext + vn["train"]
    print(f"[dedup] hash {len(train_pool)} anh train ngoai/VN ...")
    h_train = np.array([hash_file(r["img"]) or 0 for r in train_pool], dtype=np.uint64)
    keep = np.ones(len(train_pool), dtype=bool)
    for i in range(1, len(train_pool)):  # trung noi bo: giu anh dau tien
        if h_train[i] == 0:
            keep[i] = False
            continue
        prev = np.where(keep[:i])[0]
        if len(prev) and len(near_duplicates(h_train[i : i + 1], h_train[prev], args.dup_dist)):
            keep[i] = False
    n_dup = int((~keep).sum())
    test_imgs = [r["img"] for r in vn["test"]] + list_images(coco / "images" / "test")
    print(f"[dedup] hash {len(test_imgs)} anh test ...")
    h_test = np.array([hash_file(p) or 0 for p in test_imgs], dtype=np.uint64)
    leak = near_duplicates(h_train, h_test[h_test != 0], args.dup_dist)
    keep[leak] = False
    rep.data["dedup"] = {"internalDuplicatesRemoved": n_dup, "leakageRemoved": int(len(leak))}
    print(f"[dedup] bo {n_dup} anh trung noi bo, {len(leak)} anh gan trung voi TEST")
    n_ext = len(ext)
    ext = [r for r, k in zip(train_pool[:n_ext], keep[:n_ext]) if k]
    vn_train = [r for r, k in zip(train_pool[n_ext:], keep[n_ext:]) if k]

    # 4) Ghi anh (symlink) + nhan — xoa ket qua lan chay truoc de khong con file cu
    for sub in ("external", "vn"):
        shutil.rmtree(root / sub, ignore_errors=True)
    used: set[Path] = set()

    def place(rows, sub: str, split: str) -> list[Path]:
        out = []
        for r in rows:
            # VN: ten goc (bo duoi Roboflow) de khop metadata.csv; nguon ngoai: giu nguyen stem
            stem = clean_stem(r["img"].name) if "tag" not in r else r["img"].stem
            base = f"{r.get('tag', 'vn')}_{stem}"[:120]
            dst = root / sub / "images" / split / (base + r["img"].suffix.lower())
            k = 1
            while dst in used:  # trung ten (vd 2 ban Roboflow cua cung 1 anh) -> them hau to
                dst = root / sub / "images" / split / f"{base}__{k}{r['img'].suffix.lower()}"
                k += 1
            used.add(dst)
            name = dst.name
            link_or_copy(r["img"], dst)
            write_yolo_labels(root / sub / "labels" / split / Path(name).with_suffix(".txt"), r["boxes"])
            out.append(dst)
        return out

    rng.shuffle(ext)
    n_ev = int(len(ext) * args.ext_val_ratio)
    ext_val, ext_train = place(ext[:n_ev], "external", "val"), place(ext[n_ev:], "external", "train")
    vn_val_rows, vn_train_rows = stratified_split(vn_train, args.vn_val_ratio, args.seed)
    vn_val, vn_tr = place(vn_val_rows, "vn", "val"), place(vn_train_rows, "vn", "train")
    calib_rows, test_rows = stratified_split(vn["test"], args.calib_ratio, args.seed)
    calib, rtest = place(calib_rows, "vn", "test"), place(test_rows, "vn", "test")

    coco_train = list_images(coco / "images" / "train")
    coco_val = list_images(coco / "images" / "val")
    coco_test = list_images(coco / "images" / "test")
    if not coco_train:
        rep.warn(f"Khong thay anh COCO o {coco}/images/train — chay 10_export_coco_yolo.py truoc")

    lists = root / "lists"
    write_list(lists / "train.txt", coco_train + ext_train + vn_tr * max(args.vn_repeat, 1))
    write_list(lists / "val.txt", coco_val + ext_val + vn_val)
    write_list(lists / "coco_test.txt", coco_test)
    write_list(lists / "real_calib.txt", calib)
    write_list(lists / "real_test.txt", rtest)
    write_list(lists / "real_all.txt", calib + rtest)
    write_yaml(root / "data.yaml", root, "lists/train.txt", "lists/val.txt", "lists/coco_test.txt")
    for name in ("real_calib", "real_test", "coco_test"):
        lst = f"lists/{name}.txt"
        write_yaml(root / f"{name}.yaml", root, lst, lst, lst)

    rep.data["lists"] = {
        "train": {"cocoTrain": len(coco_train), "external": len(ext_train), "vn": len(vn_tr), "vnRepeat": args.vn_repeat,
                  "total": len(coco_train) + len(ext_train) + len(vn_tr) * max(args.vn_repeat, 1)},
        "val": {"coco": len(coco_val), "external": len(ext_val), "vn": len(vn_val)},
        "cocoTest": len(coco_test), "realCalib": len(calib), "realTest": len(rtest),
    }
    (root / "merge_report.json").write_text(json.dumps(rep.data, indent=2, ensure_ascii=False, default=str), encoding="utf-8")
    print(json.dumps(rep.data["lists"], indent=2))
    print(f"OK -> {root}/data.yaml ({len(rep.data['warnings'])} canh bao, xem merge_report.json). Tiep: 13_train_yolo.py")


if __name__ == "__main__":
    main()

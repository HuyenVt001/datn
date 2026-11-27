"""
10 — COCO 2017 -> dataset YOLO cac lop CLASSES (box; 9 lop tu y2) (QUEST_AI_PLAN.md muc 3–4).

Chay tren Colab (KHONG tai ve may ca nhan):
    python scripts/10_export_coco_yolo.py --out /content/yolo/coco --max-per-class 2000 \
        --negatives 1500 --test-max 3000 --test-negatives 500 --portrait-frac 0.2

Ra (cau truc Ultralytics: .../images/<split>/x.jpg <-> .../labels/<split>/x.txt):
    <out>/images/{train,val,test}/   symlink toi anh FiftyOne (khong nhan doi disk)
    <out>/labels/{train,val,test}/   "cls cx cy w h" chuan hoa, cls theo THU TU CLASSES
    <out>/images/train/pc_*.jpg      ban cat DOC 3:4 cua mot phan anh ngang (anh that la anh doc)
    <out>/stats.json                 so anh/box tung lop tung split

- train/val = COCO train2017 chia 90/10; test = COCO val2017 (+ negative) — KHONG dung luc train.
- Moi lop tai toi da --max-per-class anh (FiftyOne, ten dataset RIENG tung lop — bai hoc bug
  2026-08-18 o 01_prepare_coco.py) + --negatives anh khong chua lop nao (background).
- Box `iscrowd` bi bo (giong converter COCO cua Ultralytics).
- Giu so anh vua phai de 1 epoch YOLO11s tren T4 Colab ~10 phut (2 vCPU nap anh la nut that).
"""
from __future__ import annotations

import argparse
import json
import os
import random
import shutil
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from snapget12 import CLASSES  # noqa: E402
from snapget12.yolo import portrait_crop_boxes, write_yolo_labels  # noqa: E402


def load(split: str, classes, max_samples: int | None, seed: int):
    import fiftyone.zoo as foz

    tag = "-".join(c.replace(" ", "_") for c in classes) if classes else "any"
    if len(tag) > 40:
        tag = f"all{len(classes)}"
    return foz.load_zoo_dataset(
        "coco-2017", split=split, label_types=["detections"], classes=classes,
        only_matching=False, max_samples=max_samples, shuffle=True, seed=seed,
        dataset_name=f"y1-coco-{split}-{tag}-{seed}-{max_samples}",  # ten RIENG — tranh FiftyOne tra dataset cu
    )


def sample_boxes(sample) -> list[tuple[int, float, float, float, float]]:
    out = []
    dets = sample.ground_truth.detections if sample.ground_truth else []
    for d in dets:
        if d.label not in CLASSES or (getattr(d, "iscrowd", 0) or 0):
            continue
        x, y, w, h = d.bounding_box  # FiftyOne: [x_tren_trai, y_tren_trai, w, h] chuan hoa
        if w <= 0 or h <= 0:
            continue
        out.append((CLASSES.index(d.label), x + w / 2, y + h / 2, w, h))
    return out


def link_or_copy(src: str, dst: Path) -> None:
    dst.parent.mkdir(parents=True, exist_ok=True)
    if dst.exists() or dst.is_symlink():
        return
    try:
        os.symlink(src, dst)
    except OSError:
        shutil.copyfile(src, dst)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="/content/yolo/coco")
    ap.add_argument("--max-per-class", type=int, default=2000)
    ap.add_argument("--negatives", type=int, default=1500)
    ap.add_argument("--test-max", type=int, default=3000)
    ap.add_argument("--test-negatives", type=int, default=500)
    ap.add_argument("--val-ratio", type=float, default=0.1)
    ap.add_argument("--portrait-frac", type=float, default=0.2, help="ti le anh train NGANG duoc them ban cat doc 3:4")
    ap.add_argument("--seed", type=int, default=42)
    args = ap.parse_args()
    rng = random.Random(args.seed)
    out = Path(args.out)

    # 1) train2017: positive theo tung lop + negative
    train: dict[str, dict] = {}
    for c in CLASSES:
        ds = load("train", [c], args.max_per_class, args.seed)
        n_pos = 0
        for s in ds.select_fields(["filepath", "ground_truth"]):
            boxes = sample_boxes(s)
            n_pos += any(CLASSES[b[0]] == c for b in boxes)
            train[s.filepath] = {"filepath": s.filepath, "boxes": boxes}
        if n_pos < 0.8 * len(ds) or n_pos < 300:
            raise SystemExit(f"!! Lop '{c}': chi {n_pos}/{len(ds)} anh chua '{c}' — FiftyOne tra dataset sai. "
                             "Xoa dataset cu: python -c \"import fiftyone as fo; [fo.delete_dataset(n) for n in "
                             "fo.list_datasets() if n.startswith('y1-coco-')]\" roi chay lai.")
        print(f"[train] {c}: {len(ds)} anh ({n_pos} co '{c}') -> tong {len(train)}")

    neg_ds = load("train", None, args.negatives * 3, args.seed + 1)
    n_neg = 0
    for s in neg_ds.select_fields(["filepath", "ground_truth"]):
        if n_neg >= args.negatives or s.filepath in train:
            continue
        if not sample_boxes(s) and not any(d.label in CLASSES for d in (s.ground_truth.detections if s.ground_truth else [])):
            train[s.filepath] = {"filepath": s.filepath, "boxes": []}
            n_neg += 1
    print(f"[train] negative: +{n_neg} -> tong {len(train)}")

    items = list(train.values())
    rng.shuffle(items)
    n_val = int(len(items) * args.val_ratio)
    splits = {"val": items[:n_val], "train": items[n_val:]}

    # 2) val2017 -> test (positive + negative)
    test_ds = load("validation", CLASSES, args.test_max, args.seed)
    test = {s.filepath: {"filepath": s.filepath, "boxes": sample_boxes(s)} for s in test_ds.select_fields(["filepath", "ground_truth"])}
    tneg = load("validation", None, args.test_negatives * 3, args.seed + 2)
    k = 0
    for s in tneg.select_fields(["filepath", "ground_truth"]):
        if k >= args.test_negatives or s.filepath in test:
            continue
        if not any(d.label in CLASSES for d in (s.ground_truth.detections if s.ground_truth else [])):
            test[s.filepath] = {"filepath": s.filepath, "boxes": []}
            k += 1
    splits["test"] = list(test.values())
    print(f"[test] {len(splits['test'])} anh tu val2017 ({k} negative)")

    # 3) Ghi images (symlink) + labels; them ban cat doc cho mot phan anh train ngang
    from PIL import Image

    stats = {}
    for split, rows in splits.items():
        n_pc = 0
        for r in rows:
            src = r["filepath"]
            name = Path(src).name
            link_or_copy(src, out / "images" / split / name)
            write_yolo_labels(out / "labels" / split / Path(name).with_suffix(".txt"), r["boxes"])
            if split == "train" and r["boxes"] and rng.random() < args.portrait_frac:
                with Image.open(src) as im:
                    w, h = im.size
                    if int(h * 3 / 4) < w:
                        left = rng.randint(0, w - int(h * 3 / 4))
                        boxes, cw = portrait_crop_boxes(r["boxes"], w, h, left)
                        if boxes:
                            pc_name = f"pc_{Path(name).stem}.jpg"
                            im.convert("RGB").crop((left, 0, left + cw, h)).save(out / "images" / split / pc_name, quality=92)
                            write_yolo_labels(out / "labels" / split / f"pc_{Path(name).stem}.txt", boxes)
                            n_pc += 1
        img_per_class = {c: sum(any(b[0] == i for b in r["boxes"]) for r in rows) for i, c in enumerate(CLASSES)}
        box_per_class = {c: sum(sum(b[0] == i for b in r["boxes"]) for r in rows) for i, c in enumerate(CLASSES)}
        stats[split] = {"images": len(rows) + n_pc, "portraitCrops": n_pc,
                        "negatives": sum(not r["boxes"] for r in rows),
                        "imagesPerClass": img_per_class, "boxesPerClass": box_per_class}
        print(f"[{split}] {len(rows)} anh + {n_pc} ban cat doc | anh/lop: {img_per_class}")

    (out / "stats.json").write_text(json.dumps(stats, indent=2, ensure_ascii=False), encoding="utf-8")
    weak = [c for c, n in stats["train"]["imagesPerClass"].items() if n < 500]
    if weak:
        raise SystemExit(f"!! Lop it hon 500 anh train: {weak} — kiem tra lai buoc tai.")
    print(f"OK -> {out}. Tiep: 11_merge_external.py (gop Kaggle/Google/VN + tao data.yaml)")


if __name__ == "__main__":
    main()

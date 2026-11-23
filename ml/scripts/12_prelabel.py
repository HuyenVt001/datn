"""
12 — PRE-LABEL anh chua co box (anh tai tu Google, dataset Kaggle chi co nhan cap anh, anh tu chup
VN) bang YOLO11x pretrained COCO (da biet du 12 lop) -> file nhan YOLO de NGUOI SUA LAI
(Roboflow / Label Studio / CVAT). Tiet kiem ~70-80% cong gan box (ml/DATASET_SPEC.md muc D).

    python scripts/12_prelabel.py --images /content/drive/MyDrive/snapget-ml/raw/google \
        --out /content/drive/MyDrive/snapget-ml/prelabel/google --model yolo11x.pt --conf 0.25

Ra (dung dinh dang Roboflow/Label Studio nhan truc tiep):
    <out>/images/<giu nguyen cay thu muc con>/x.jpg     (ban sao anh)
    <out>/labels/<...>/x.txt                            box 12 lop theo THU TU CLASSES (co the rong)
    <out>/classes.txt                                   12 ten lop (dong i = class id i)
    <out>/prelabel_report.csv                           anh, so box, lop phat hien, diem cao nhat
    <out>/needs_review.txt                              anh NGHI VAN can xem ky (xem duoi)

⚠️ Day CHI la ban nhap. Bat buoc duyet 100% anh (DATASET_SPEC muc D.5). Model COCO hay sai dung
cac ca dac trung VN: xe dap dien bi gan motorcycle, non la/ao mua bi gan umbrella, dong ho deo tay
bi gan clock, ly tra sua bi bo sot (diem thap). Anh dua vao needs_review.txt khi:
    - ten thu muc/file goi y lop X nhung model KHONG thay X (sot box)
    - co box diem trong vung 0.25–0.5 (khong chac chan)
    - co cap lop de nham cung luc (bicycle+motorcycle, laptop+keyboard, cup+bottle)
"""
from __future__ import annotations

import argparse
import csv
import shutil
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from snapget12 import CLASSES  # noqa: E402
from snapget12.yolo import COCO80_INDEX, list_images, write_yolo_labels  # noqa: E402

CONFUSABLE = [("bicycle", "motorcycle"), ("laptop", "keyboard"), ("cup", "bottle")]


def hinted_class(path: Path, root: Path) -> str | None:
    """Lop goi y tu ten thu muc con hoac ten file (vd google/cup/..., self_potted_plant_0001.jpg)."""
    parts = [p.lower() for p in path.relative_to(root).parts]
    for token in parts[:-1] + [path.stem.lower()]:
        t = token.replace("-", " ").replace("_", " ")
        for c in sorted(CLASSES, key=len, reverse=True):
            if c in t:
                return c
    return None


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--images", required=True, help="thu muc anh (quet de quy)")
    ap.add_argument("--out", required=True)
    ap.add_argument("--model", default="yolo11x.pt", help="model COCO 80 lop (to nhat = chinh xac nhat)")
    ap.add_argument("--conf", type=float, default=0.25)
    ap.add_argument("--imgsz", type=int, default=1280, help="anh dien thoai lon -> 1280 bat vat nho tot hon")
    ap.add_argument("--uncertain", type=float, default=0.5, help="box diem < muc nay -> needs_review")
    args = ap.parse_args()

    from ultralytics import YOLO

    root, out = Path(args.images), Path(args.out)
    imgs = list_images(root)
    if not imgs:
        raise SystemExit(f"!! khong co anh trong {root}")
    model = YOLO(args.model)
    names = model.model.names
    coco_to_ours = {COCO80_INDEX[c]: i for i, c in enumerate(CLASSES)}
    if any(names.get(k) != CLASSES[v] for k, v in coco_to_ours.items()):
        raise SystemExit(f"!! {args.model} khong phai model 80 lop COCO chuan (ten lop khong khop)")

    out.mkdir(parents=True, exist_ok=True)
    (out / "classes.txt").write_text("\n".join(CLASSES) + "\n", encoding="utf-8")
    (out / "data.yaml").write_text(f"nc: {len(CLASSES)}\nnames: [{', '.join(repr(c) for c in CLASSES)}]\n", encoding="utf-8")
    review, rows = [], []
    for i in range(0, len(imgs), 16):
        batch = imgs[i : i + 16]
        results = model.predict([str(p) for p in batch], conf=args.conf, imgsz=args.imgsz, verbose=False)
        for p, r in zip(batch, results):
            rel = p.relative_to(root)
            dst = out / "images" / rel
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(p, dst)
            boxes, confs = [], []
            for cls, conf, xywhn in zip(r.boxes.cls.tolist(), r.boxes.conf.tolist(), r.boxes.xywhn.tolist()):
                if int(cls) in coco_to_ours:
                    boxes.append((coco_to_ours[int(cls)], *xywhn))
                    confs.append(conf)
            write_yolo_labels(out / "labels" / rel.with_suffix(".txt"), boxes)
            found = {CLASSES[b[0]] for b in boxes}
            hint = hinted_class(p, root)
            reasons = []
            if hint and hint not in found:
                reasons.append(f"goi y '{hint}' nhung khong co box")
            if any(c < args.uncertain for c in confs):
                reasons.append("box diem thap")
            reasons += [f"cap de nham {a}+{b}" for a, b in CONFUSABLE if a in found and b in found]
            if reasons:
                review.append(f"{rel.as_posix()}\t{'; '.join(reasons)}")
            rows.append({"image": rel.as_posix(), "boxes": len(boxes), "classes": "|".join(sorted(found)),
                         "maxConf": round(max(confs), 3) if confs else "", "hint": hint or ""})
        print(f"{min(i + 16, len(imgs))}/{len(imgs)}")

    with open(out / "prelabel_report.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=["image", "boxes", "classes", "maxConf", "hint"])
        w.writeheader()
        w.writerows(rows)
    (out / "needs_review.txt").write_text("\n".join(review) + ("\n" if review else ""), encoding="utf-8")
    print(f"OK: {len(imgs)} anh -> {out} | {len(review)} anh can xem ky (needs_review.txt)")
    print("Tiep: upload thu muc nay len Roboflow (Upload -> chon ca images/ + labels/ + classes.txt) hoac Label Studio, "
          "SUA TAY 100% anh, export lai dinh dang YOLO.")


if __name__ == "__main__":
    main()

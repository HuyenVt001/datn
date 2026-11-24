"""
Dong goi toan bo anh CAO + GAN NHAN (thu muc dau ra cua 17_crawl_autolabel.py) thanh 1 dataset YOLO gon
-> "tap train bo sung du lieu ban dia" (VN-supplement). Chi lay anh DA DUNG DUOC (train/), bo review/, anh loai,
file trang thai cao. Nhan duoc chuan hoa ve thu tu 12 lop chuan (snapget12.CLASSES).

    python scripts/19_package_dataset.py --src /content/drive/MyDrive/snapget-ml/data/vn_web \
        --out /content/drive/MyDrive/snapget-ml/data/snapget_vn_supplement.zip

Ket qua (ben trong zip, thu muc goc snapget_vn_supplement/):
    README.md            the dataset: so luong, cach thu thap/gan nhan, nguon, giay phep
    data.yaml            12 lop (dung truc tiep: 11_merge_external.py --vn <thu muc>, hoac Ultralytics)
    classes.txt
    train/images/*.jpg   train/labels/*.txt     (negative = file nhan rong)
    metadata.csv         filename, scene, lighting, distance, provider, license, autolabel...
    web_attributions.csv trich nguon tung anh (url, trang goc, giay phep, tac gia)
    stats.json
"""
from __future__ import annotations

import argparse
import csv
import json
import sys
import zipfile
from collections import Counter
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from snapget12.classes import CLASSES  # noqa: E402
from snapget12.yolo import (  # noqa: E402
    clean_stem,
    find_names,
    list_images,
    load_class_map,
    map_class,
    read_yolo_labels,
    validate_boxes,
)

ROOT = "snapget_vn_supplement"
METHOD = {
    "accept": "tự động — 2 model đồng thuận (YOLO11x COCO + YOLO-World prompt bối cảnh VN)",
    "auto-review": "tự động tầng 2 — box chưa chắc được cắt vùng, phóng to, 2 model kiểm lại",
    "reviewed": "người sửa (Roboflow)",
}


def read_csv(p: Path) -> list[dict]:
    if not p.exists():
        return []
    with open(p, newline="", encoding="utf-8-sig") as f:
        return list(csv.DictReader(f))


def group_of(stem: str) -> str:
    """gg_potted_plant_0012 -> potted_plant"""
    parts = stem.split("_")
    return "_".join(parts[1:-1]) if len(parts) >= 3 else "?"


def collect(src: Path) -> tuple[list[dict], dict]:
    names = find_names(src / "train") or find_names(src) or list(CLASSES)
    cmap = load_class_map(Path(__file__).resolve().parents[1] / "yolo_class_map.yaml")
    to_std = [map_class(n, cmap) for n in names]
    if any(i is None for i in to_std):
        raise SystemExit(f"!! ten lop khong map duoc ve 12 lop chuan: {names}")
    items, skipped = [], Counter()
    for img in list_images(src / "train" / "images"):
        stem = clean_stem(img.name)
        lab = src / "train" / "labels" / (img.stem + ".txt")
        g = group_of(stem)
        if not lab.exists() and g not in ("neg", "hardneg"):
            skipped["thieu file nhan"] += 1
            continue
        raw = read_yolo_labels(lab)
        if validate_boxes(raw, len(names)):
            skipped["box khong hop le"] += 1
            continue
        boxes = [(to_std[c], x, y, w, h) for c, x, y, w, h in raw]
        if g not in ("neg", "hardneg") and not boxes:
            skipped["anh lop nhung khong co box"] += 1
            continue
        items.append({"img": img, "name": img.name, "group": g, "boxes": boxes})
    return items, dict(skipped)


def build_stats(items: list[dict], meta: dict[str, dict]) -> dict:
    per_img = Counter()
    per_box = Counter()
    for it in items:
        for c in {b[0] for b in it["boxes"]}:
            per_img[CLASSES[c]] += 1
        for b in it["boxes"]:
            per_box[CLASSES[b[0]]] += 1
    rows = [meta.get(it["name"], {}) for it in items]
    cnt = lambda key: dict(Counter((r.get(key) or "?") for r in rows).most_common())  # noqa: E731
    return {
        "images": len(items),
        "imagesPerClass": {c: per_img.get(c, 0) for c in CLASSES},
        "boxesPerClass": {c: per_box.get(c, 0) for c in CLASSES},
        "negatives": sum(it["group"] == "neg" for it in items),
        "hardNegatives": sum(it["group"] == "hardneg" for it in items),
        "multiClassImages": sum(len({b[0] for b in it["boxes"]}) >= 2 for it in items),
        "labelMethod": cnt("autolabel"),
        "provider": cnt("provider"),
        "license": cnt("license"),
        "scene": cnt("scene"),
        "lighting": cnt("lighting"),
        "distance": cnt("distance"),
    }


def readme(st: dict) -> str:
    n = max(st["images"], 1)
    pct = lambda d: ", ".join(f"{k} {v / n:.0%}" for k, v in d.items())  # noqa: E731
    lines = [
        "# Snapget VN-supplement — tập train bổ sung dữ liệu bản địa",
        "",
        f"Đóng gói ngày {date.today().isoformat()} bởi `ml/scripts/19_package_dataset.py`.",
        "",
        "**Mục đích:** bổ sung cho COCO các vật thể/bối cảnh đặc trưng Việt Nam (ghế nhựa vỉa hè, ly trà sữa, xe số, "
        "dù che quán...). **Chỉ dùng để train** — không dùng để đánh giá.",
        "",
        "**Thu thập:** ảnh Internet (Wikimedia Commons, Openverse, DuckDuckGo...) theo bộ từ khoá bối cảnh VN "
        "(`ml/crawl_queries.yaml`); lọc ảnh hỏng/nhỏ < 1024px/mờ/nền studio/tên miền có watermark; khử trùng bằng dHash, "
        "loại ảnh gần trùng với tập test.",
        "",
        "**Gán nhãn:** bán tự động, định dạng YOLO, 12 lớp:",
        "",
    ]
    for k, v in st["labelMethod"].items():
        lines.append(f"- `{k}` ({METHOD.get(k, k)}): {v} ảnh")
    lines += [
        "",
        "Luật chung: loại box trùng hard negative (đồng hồ đeo tay ≠ clock, nón lá ≠ umbrella...) và vật thể nằm trong "
        "màn hình/ảnh in; ảnh còn nghi vấn bị bỏ, không đưa vào gói.",
        "",
        f"## Số lượng — {st['images']} ảnh",
        "",
        "| Lớp | Ảnh chứa lớp | Số box |",
        "|---|---|---|",
    ]
    lines += [f"| {c} | {st['imagesPerClass'][c]} | {st['boxesPerClass'][c]} |" for c in CLASSES]
    lines += [
        f"| *negative* (không có lớp nào) | {st['negatives']} | – |",
        f"| *hard negative* (vật dễ nhầm) | {st['hardNegatives']} | – |",
        "",
        f"Ảnh có ≥ 2 lớp: {st['multiClassImages']}.",
        "",
        "## Phân bố",
        "",
        f"- Bối cảnh: {pct(st['scene'])}",
        f"- Ánh sáng (ước lượng từ độ sáng ảnh): {pct(st['lighting'])}",
        f"- Khoảng cách (ước lượng từ kích thước box): {pct(st['distance'])}",
        f"- Nguồn: {pct(st['provider'])}",
        "",
        "## Giấy phép",
        "",
        "Ảnh giữ giấy phép gốc (trích nguồn từng ảnh: `web_attributions.csv`); chỉ dùng cho mục đích học thuật, "
        "không phát tán lại.",
        "",
        "| Giấy phép | Số ảnh |",
        "|---|---|",
    ]
    lines += [f"| {k} | {v} |" for k, v in list(st["license"].items())[:15]]
    return "\n".join(lines) + "\n"


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", required=True, help="thu muc dau ra cua 17_crawl_autolabel.py (vd .../data/vn_web)")
    ap.add_argument("--out", required=True, help="file .zip dau ra")
    args = ap.parse_args()
    src, out = Path(args.src), Path(args.out)

    items, skipped = collect(src)
    if not items:
        raise SystemExit(f"!! {src}/train/images khong co anh dung duoc")
    keep = {it["name"] for it in items}
    meta_rows = [r for r in read_csv(src / "metadata.csv") if r.get("filename") in keep]
    meta = {r["filename"]: r for r in meta_rows}
    attr_rows = [r for r in read_csv(src / "web_attributions.csv") if r.get("filename") in keep]
    st = build_stats(items, meta)
    st["skipped"] = skipped

    def csv_text(rows: list[dict]) -> str:
        if not rows:
            return ""
        import io

        buf = io.StringIO()
        w = csv.DictWriter(buf, fieldnames=list(rows[0].keys()), extrasaction="ignore")
        w.writeheader()
        w.writerows(rows)
        return buf.getvalue()

    out.parent.mkdir(parents=True, exist_ok=True)
    tmp = out.with_suffix(".zip.part")
    with zipfile.ZipFile(tmp, "w") as z:  # JPEG da nen -> ZIP_STORED (nhanh); file chu -> DEFLATED
        txt = dict(compress_type=zipfile.ZIP_DEFLATED)
        z.writestr(f"{ROOT}/README.md", readme(st), **txt)
        z.writestr(f"{ROOT}/data.yaml", "path: .\ntrain: train/images\nval: train/images\n"
                   f"nc: {len(CLASSES)}\nnames: [{', '.join(repr(c) for c in CLASSES)}]\n", **txt)
        z.writestr(f"{ROOT}/classes.txt", "\n".join(CLASSES) + "\n", **txt)
        z.writestr(f"{ROOT}/metadata.csv", csv_text(meta_rows), **txt)
        z.writestr(f"{ROOT}/web_attributions.csv", csv_text(attr_rows), **txt)
        z.writestr(f"{ROOT}/stats.json", json.dumps(st, indent=2, ensure_ascii=False), **txt)
        for i, it in enumerate(items):
            z.write(it["img"], f"{ROOT}/train/images/{it['name']}", compress_type=zipfile.ZIP_STORED)
            lines = "".join(f"{c} {x:.6f} {y:.6f} {w:.6f} {h:.6f}\n" for c, x, y, w, h in it["boxes"])
            z.writestr(f"{ROOT}/train/labels/{Path(it['name']).stem}.txt", lines, **txt)
            if (i + 1) % 500 == 0:
                print(f"   {i + 1}/{len(items)}")
    tmp.replace(out)

    print(f"OK -> {out} ({out.stat().st_size / 1e6:.0f} MB): {st['images']} anh, "
          f"negative {st['negatives']}, hard negative {st['hardNegatives']}; bo qua {skipped or 'khong'}")
    print("Anh/lop:", st["imagesPerClass"])
    print("Cach gan nhan:", st["labelMethod"])


if __name__ == "__main__":
    main()

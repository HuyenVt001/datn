"""
18 — LAM SACH tap VN (anh tu tai / tu chup) TRUOC moi buoc khac (gan nhan, merge, train) — ml/DATASET_SPEC.md.

    # 1) chi BAO CAO (mac dinh, khong dong vao file nao)
    python scripts/18_clean_dataset.py --vn /content/ext/vn/vn
    # 2) xem clean_report.csv roi moi THUC HIEN: file loi -> <vn>/_quarantine/<ly do>/ (KHONG xoa), sua metadata.csv
    python scripts/18_clean_dataset.py --vn /content/ext/vn/vn --apply

Kiem tra tung anh trong <vn>/{train,test}/images:
  hong       khong mo duoc / file loi
  nho        canh dai < --min-side (1024, DATASET_SPEC muc E)
  khung      ti le > 3:1 (banner, anh ghep)
  studio     nen trang anh san pham
  phang      gan nhu mot mau
  mo         rat mo (phuong sai Laplacian < --blur-thr)
  trung      trung Y HET (md5) hoac GAN TRUNG (dHash <= --dup-dist) trong cung tap -> giu 1 anh
  ro_ri      anh TRAIN gan trung anh TEST -> bo khoi train (giu test) — chong so lieu dep gia
  test_web   anh TEST khong co EXIF may anh (Make/Model) hoac co url nguon -> NGHI la anh Internet,
             trai DATASET_SPEC (test = 100% tu chup). Mac dinh CHI BAO; --quarantine-test-web de chuyen ra.
             (Anh tu chup gui qua Zalo/Messenger cung mat EXIF -> xem tay truoc khi chuyen.)
Kiem tra metadata.csv: dong khong co anh, anh khong co dong, gia tri ngoai danh muc.
--recompute-metadata: ghi de lighting (uoc luong tu pixel), distance (tu box nhan neu co), phone=web cho anh gg_
  — dung khi metadata duoc dien NGAU NHIEN (vd code cao co random.choice) thay vi do that.

Anh trong _quarantine/test_web co the dung lai lam DU LIEU TRAIN: 17_crawl_autolabel.py --import-dir <thu muc do>
(tu doi ten self_ -> gg_ va gan nhan). Ra: <vn>/clean_report.json + clean_report.csv.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import shutil
import sys
from collections import Counter, defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np  # noqa: E402
from PIL import Image, ImageOps  # noqa: E402

from snapget12 import CLASSES  # noqa: E402
from snapget12.autolabel import check_quality, estimate_lighting  # noqa: E402
from snapget12.yolo import (  # noqa: E402
    clean_stem,
    dhash,
    find_names,
    label_path_for,
    list_images,
    load_class_map,
    map_class,
    near_duplicates,
    read_yolo_labels,
)

PRIORITY = ["hong", "trung", "ro_ri", "test_web", "nho", "khung", "studio", "phang", "chat_luong", "mo"]
QUALITY_REASON = {"anh nho": "nho", "ti le": "khung", "nen trang": "studio", "anh gan nhu": "phang"}
EXIF_MAKE, EXIF_MODEL = 271, 272


def blur_score(img: Image.Image) -> float:
    """Phuong sai Laplacian tren anh xam 512px — cang thap cang mo."""
    g = np.asarray(img.convert("L").resize((512, 512)), dtype=np.float32)
    lap = -4 * g[1:-1, 1:-1] + g[:-2, 1:-1] + g[2:, 1:-1] + g[1:-1, :-2] + g[1:-1, 2:]
    return float(lap.var())


def camera_exif(im: Image.Image) -> str:
    try:
        ex = im.getexif()
        return f"{ex.get(EXIF_MAKE, '')} {ex.get(EXIF_MODEL, '')}".strip()
    except Exception:
        return ""


def analyze(path: Path, args) -> dict:
    info = {"path": path, "issues": [], "md5": None, "dhash": 0, "camera": "", "size": None, "blur": None}
    try:
        raw = path.read_bytes()
        info["md5"] = hashlib.md5(raw).hexdigest()
        with Image.open(path) as im:
            info["camera"] = camera_exif(im)
            img = ImageOps.exif_transpose(im).convert("RGB")
    except Exception as e:
        info["issues"].append(("hong", f"khong mo duoc: {type(e).__name__}"))
        return info
    info["size"] = img.size
    info["dhash"] = dhash(img)
    why = check_quality(img, args.min_side)
    if why:
        code = next((v for k, v in QUALITY_REASON.items() if why.startswith(k)), "chat_luong")
        info["issues"].append((code, why))
    info["blur"] = blur_score(img)
    if info["blur"] < args.blur_thr:
        info["issues"].append(("mo", f"anh mo (Laplacian {info['blur']:.0f} < {args.blur_thr})"))
    info["lighting"] = estimate_lighting(img)
    return info


def distance_from_labels(img_path: Path, names_idx: list[int | None]) -> str:
    try:
        boxes = read_yolo_labels(label_path_for(img_path))
    except ValueError:
        return ""
    areas = [w * h for c, _, _, w, h in boxes if 0 <= c < len(names_idx) and names_idx[c] is not None]
    if not areas:
        return ""
    a = max(areas)
    return "near" if a >= 0.20 else "mid" if a >= 0.03 else "far"


def move_to_quarantine(vn: Path, img: Path, reason: str) -> Path:
    split = img.parent.parent.name  # train | test
    dst_dir = vn / "_quarantine" / reason / split
    (dst_dir / "images").mkdir(parents=True, exist_ok=True)
    (dst_dir / "labels").mkdir(parents=True, exist_ok=True)
    dst = dst_dir / "images" / img.name
    k = 1
    while dst.exists():  # khong de file da cach ly truoc do
        dst = dst_dir / "images" / f"{img.stem}__{k}{img.suffix}"
        k += 1
    shutil.move(str(img), dst)
    try:
        lab = label_path_for(img)
        if lab.exists():
            shutil.move(str(lab), dst_dir / "labels" / (dst.stem + ".txt"))
    except ValueError:
        pass
    return dst


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--vn", required=True, help="thu muc VN (co train/ va/hoac test/)")
    ap.add_argument("--apply", action="store_true", help="THUC HIEN chuyen file loi vao _quarantine + sua metadata.csv")
    ap.add_argument("--min-side", type=int, default=1024)
    ap.add_argument("--blur-thr", type=float, default=40.0, help="phuong sai Laplacian duoi muc nay = anh mo")
    ap.add_argument("--dup-dist", type=int, default=5, help="Hamming dHash <= muc nay = gan trung")
    ap.add_argument("--quarantine-test-web", action="store_true",
                    help="chuyen anh TEST nghi la anh Internet (khong EXIF may anh / co url) ra _quarantine/test_web")
    ap.add_argument("--keep-small", action="store_true", help="khong cach ly anh nho hon --min-side (chi bao)")
    ap.add_argument("--recompute-metadata", action="store_true",
                    help="ghi de lighting/distance bang uoc luong tu anh + nhan, phone=web cho anh gg_ (metadata ngau nhien)")
    args = ap.parse_args()

    vn = Path(args.vn)
    splits = {s: list_images(vn / s / "images") for s in ("train", "test") if (vn / s / "images").exists()}
    if not splits:
        raise SystemExit(f"!! {vn} khong co train/images hay test/images")
    meta_path = vn / "metadata.csv"
    meta_rows = []
    if meta_path.exists():
        with open(meta_path, newline="", encoding="utf-8-sig") as f:
            meta_rows = list(csv.DictReader(f))
    meta_by = {clean_stem(r.get("filename", "")): r for r in meta_rows}

    # 1) Phan tich tung anh
    infos: dict[str, list[dict]] = {}
    for split, imgs in splits.items():
        print(f"[{split}] phan tich {len(imgs)} anh ...")
        infos[split] = []
        for i, p in enumerate(imgs, 1):
            infos[split].append(analyze(p, args))
            if i % 200 == 0:
                print(f"   {i}/{len(imgs)}")

    # 2) Trung lap trong cung tap: md5 y het, roi dHash gan trung (giu anh ten nho nhat)
    for split, lst in infos.items():
        ok = [x for x in lst if x["md5"] and not any(c == "hong" for c, _ in x["issues"])]
        ok.sort(key=lambda x: x["path"].name)
        seen_md5: dict[str, Path] = {}
        kept_hashes: list[int] = []
        kept_names: list[str] = []
        for x in ok:
            if x["md5"] in seen_md5:
                x["issues"].append(("trung", f"trung y het {seen_md5[x['md5']].name}"))
                continue
            seen_md5[x["md5"]] = x["path"]
            if kept_hashes:
                d = near_duplicates(np.array([x["dhash"]], dtype=np.uint64), np.array(kept_hashes, dtype=np.uint64), args.dup_dist)
                if len(d):
                    # tim anh giong nhat de ghi vao bao cao
                    hm = np.array(kept_hashes, dtype=np.uint64) ^ np.uint64(x["dhash"])
                    j = int(np.argmin([bin(int(v)).count("1") for v in hm]))
                    x["issues"].append(("trung", f"gan trung {kept_names[j]}"))
                    continue
            kept_hashes.append(x["dhash"])
            kept_names.append(x["path"].name)

    # 3) Ro ri train <-> test: bo ban o TRAIN
    if "train" in infos and "test" in infos:
        th = np.array([x["dhash"] for x in infos["test"] if x["dhash"]], dtype=np.uint64)
        # moi anh train con dung duoc (ke ca anh chi bi "mo"/"nho" — chi bao cao) deu phai soat ro ri
        tr = [x for x in infos["train"] if x["dhash"] and not any(c in ("hong", "trung") for c, _ in x["issues"])]
        hits = near_duplicates(np.array([x["dhash"] for x in tr], dtype=np.uint64), th, args.dup_dist) if len(th) else []
        for k in hits:
            tr[int(k)]["issues"].append(("ro_ri", "gan trung 1 anh TEST — bo khoi train"))

    # 4) Anh TEST nghi la anh Internet
    for x in infos.get("test", []):
        if any(c == "hong" for c, _ in x["issues"]):
            continue
        m = meta_by.get(clean_stem(x["path"].name), {})
        reasons = []
        if not x["camera"]:
            reasons.append("khong co EXIF may anh")
        if (m.get("url") or "").strip():
            reasons.append("co url nguon trong metadata")
        if clean_stem(x["path"].name).startswith(("gg_", "kg_")):
            reasons.append("ten gg_/kg_")
        if reasons:
            x["issues"].append(("test_web", "nghi anh Internet: " + ", ".join(reasons)))

    # 5) Tong hop + hanh dong
    report_rows, counts = [], defaultdict(Counter)
    to_move: list[tuple[dict, str]] = []
    for split, lst in infos.items():
        for x in lst:
            if not x["issues"]:
                continue
            for code, msg in x["issues"]:
                counts[split][code] += 1
            x["issues"].sort(key=lambda t: PRIORITY.index(t[0]) if t[0] in PRIORITY else len(PRIORITY))
            primary = x["issues"][0][0]  # ly do NGHIEM TRONG nhat -> ten thu muc cach ly
            act = "cach_ly"
            if primary == "nho" and args.keep_small:
                act = "chi_bao"
            if primary == "test_web" and not args.quarantine_test_web:
                act = "chi_bao"
            if primary == "mo" and len(x["issues"]) == 1:
                act = "chi_bao"  # do mo uoc luong — chi bao, xem tay
            report_rows.append({"split": split, "file": x["path"].name, "issues": "; ".join(m for _, m in x["issues"]),
                                "action": act if args.apply else f"(dry-run) {act}",
                                "camera": x["camera"], "size": "x".join(map(str, x["size"])) if x["size"] else ""})
            if act == "cach_ly":
                to_move.append((x, primary))

    # metadata: anh <-> dong
    img_stems = {clean_stem(x["path"].name) for lst in infos.values() for x in lst}
    orphan_rows = [r for r in meta_rows if clean_stem(r.get("filename", "")) not in img_stems]
    no_meta = sorted(s for s in img_stems if s not in meta_by)

    print("\n===== KET QUA =====")
    for split, lst in infos.items():
        bad = sum(1 for x in lst if x["issues"])
        print(f"[{split}] {len(lst)} anh | co van de {bad} | {dict(counts[split])}")
    nocam = sum(1 for x in infos.get("test", []) if not x["camera"])
    if "test" in infos:
        print(f"[test] {nocam}/{len(infos['test'])} anh KHONG co EXIF may anh")
        if nocam > 0.3 * max(len(infos["test"]), 1):
            print("   !! Phan lon anh test khong co dau vet chup bang dien thoai. DATASET_SPEC: VN-test phai 100% TU CHUP —\n"
                  "      anh tai tu Internet trong test lam so lieu bao cao KHONG con do duoc model tren anh that.\n"
                  "      Dung --quarantine-test-web de dua ra, chup bu anh that; anh Internet dung lai lam train qua 17 --import-dir.")
    print(f"metadata: {len(orphan_rows)} dong khong con anh, {len(no_meta)} anh khong co dong")

    with open(vn / "clean_report.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=["split", "file", "issues", "action", "camera", "size"])
        w.writeheader()
        w.writerows(report_rows)
    summary = {"applied": args.apply, "counts": {k: dict(v) for k, v in counts.items()},
               "toQuarantine": len(to_move), "testWithoutCameraExif": nocam,
               "metadataOrphanRows": len(orphan_rows), "imagesWithoutMetadata": len(no_meta)}

    if not args.apply:
        print(f"\n(dry-run) se cach ly {len(to_move)} anh. Xem {vn}/clean_report.csv roi chay lai voi --apply.")
        (vn / "clean_report.json").write_text(json.dumps(summary, indent=2, ensure_ascii=False), encoding="utf-8")
        return

    # --apply
    if meta_path.exists():
        shutil.copyfile(meta_path, meta_path.with_suffix(".csv.bak"))
    moved = set()
    for x, reason in to_move:
        move_to_quarantine(vn, x["path"], reason)
        moved.add(clean_stem(x["path"].name))
    kept_rows = [r for r in meta_rows if clean_stem(r.get("filename", "")) not in moved and r not in orphan_rows]
    if args.recompute_metadata and kept_rows:
        names = find_names(vn) or list(CLASSES)
        cmap = load_class_map(Path(__file__).resolve().parents[1] / "yolo_class_map.yaml")
        names_idx = [map_class(n, cmap) for n in names]
        by_stem = {clean_stem(x["path"].name): x for lst in infos.values() for x in lst}
        for r in kept_rows:
            x = by_stem.get(clean_stem(r.get("filename", "")))
            if not x or not x.get("lighting"):
                continue
            r["lighting"] = x["lighting"]
            r["distance"] = distance_from_labels(x["path"], names_idx) or r.get("distance", "")
            if clean_stem(x["path"].name).startswith("gg_"):
                r["phone"] = "web"
                r["scene"] = r.get("scene") if r.get("scene") in {"home", "classroom", "cafe", "street", "parking", "office", "other"} else "other"
        summary["metadataRecomputed"] = True
    if meta_rows:
        with open(meta_path, "w", newline="", encoding="utf-8") as f:
            w = csv.DictWriter(f, fieldnames=list(meta_rows[0].keys()), extrasaction="ignore")
            w.writeheader()
            w.writerows(kept_rows)
    (vn / "clean_report.json").write_text(json.dumps(summary, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"\nDA cach ly {len(moved)} anh vao {vn}/_quarantine/<ly do>/ (khong xoa). metadata.csv da sua "
          f"(ban cu: metadata.csv.bak), bo {len(meta_rows) - len(kept_rows)} dong.")


if __name__ == "__main__":
    main()

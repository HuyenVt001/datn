"""
17 — TU DONG CAO anh Internet + TU DONG GAN NHAN cho tap VN-train (ml/DATASET_SPEC.md) — chay tren Google Colab.

    python scripts/17_crawl_autolabel.py --out /content/drive/MyDrive/snapget-ml/data/vn_web \
        --per-class 100 --negatives 150 --hard-negatives 150 --sources openverse,wikimedia,ddg \
        --test-dir /content/ext/vn/vn/test/images

Luong cho moi lop (tu khoa o ml/crawl_queries.yaml, xoay vong cac truy van x cac nguon):
  tim anh -> bo URL da xu ly -> tai song song -> LOC CHAT LUONG (canh dai >= 1024, khong nen trang studio, khong
  anh ghep) -> KHU TRUNG dHash (voi anh da co + tap TEST that) -> GAN NHAN BANG 2 MODEL (YOLO11x COCO + YOLO-World
  prompt boi canh VN & hard negative, snapget12/autolabel.py) ->
     ACCEPT -> <out>/train/images|labels  (gg_<lop>_NNNN.jpg) + 1 dong metadata.csv (scene theo tu khoa,
               lighting/distance tu uoc luong)
     REVIEW -> <out>/review/... + review_queue.csv (ly do) -> NGUOI SUA tren Roboflow -> --promote
     REJECT -> rejected.csv (khong luu anh)
  dung khi du --per-class anh ACCEPT (tinh ca anh da co tu lan chay truoc — chay lai = chay TIEP).

Ra (<out> = 1 thu muc VN theo DATASET_SPEC muc E, CHI co train/ — tap TEST luon 100% tu chup):
  train/images, train/labels, classes.txt, metadata.csv, data_sources.md, web_attributions.csv
  review/images, review/labels, review/classes.txt, review/review_queue.csv, review/metadata_pending.csv
  rejected.csv, crawl_state.json (URL da xu ly + hash — de resume khi Colab ngat), crawl_report.json
Dung trong 11_merge_external.py: --vn <anh tu chup> --vn <out>

Sau khi sua anh REVIEW tren Roboflow (export YOLOv8, KHONG resize):
    python scripts/17_crawl_autolabel.py --out <out> --promote /content/roboflow_export
-> anh da sua chuyen vao train/ (dung anh goc do phan giai cao trong review/, nhan tu ban export).

⚠️ Chinh sach (DATASET_SPEC muc G): anh ACCEPT vao thang VN-train KHONG qua nguoi duyet tung anh -> nen kiem tra
ngau nhien ~10% (o "xem mau" trong notebook). Anh Internet KHONG BAO GIO vao tap test.
"""
from __future__ import annotations

import argparse
import csv
import json
import re
import shutil
import sys
import time
from collections import Counter, defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np  # noqa: E402
import yaml  # noqa: E402
from PIL import Image  # noqa: E402

from snapget12 import CLASSES  # noqa: E402
from snapget12.autolabel import (  # noqa: E402
    HARDNEG_PROMPTS,
    Det,
    Thresholds,
    check_quality,
    decide,
    estimate_distance,
    estimate_lighting,
    to_yolo_lines,
    world_vocabulary,
)
from snapget12.crawl import blocked, download_many, make_sources, set_contact  # noqa: E402
from snapget12.yolo import (  # noqa: E402
    COCO80_INDEX,
    clean_stem,
    dhash,
    find_names,
    label_path_for,
    list_images,
    load_class_map,
    map_class,
    near_duplicates,
    read_yolo_labels,
    write_yolo_labels,
)

META_FIELDS = ["filename", "split", "source", "url", "scene", "lighting", "distance", "phone",
               "provider", "license", "creator", "query", "autolabel", "target_conf"]
ATTR_FIELDS = ["filename", "provider", "url", "page", "license", "creator", "title"]
NAME_RE = re.compile(r"^gg_([a-z_]+?)_(\d{4})$")


# ---------------------------------------------------------------------------
class Store:
    """Thu muc dau ra + trang thai resume."""

    def __init__(self, out: Path):
        self.out = out
        for d in ("train/images", "train/labels", "review/images", "review/labels"):
            (out / d).mkdir(parents=True, exist_ok=True)
        names = find_names(out / "train") or (find_names(out) if (out / "classes.txt").exists() or (out / "data.yaml").exists() else None)
        if names:  # thu muc da co file ten lop (vd thu muc VN cua nhom, export tu Roboflow) -> ghi nhan THEO THU TU DO
            cmap = load_class_map(Path(__file__).resolve().parents[1] / "yolo_class_map.yaml")
            pos = {map_class(n, cmap): i for i, n in enumerate(names)}
            missing = [c for i, c in enumerate(CLASSES) if i not in pos]
            if missing:
                raise SystemExit(f"!! file ten lop trong {out} thieu lop {missing} — khong ghi nhan duoc")
            self.index_of = [pos[i] for i in range(len(CLASSES))]
        else:
            (out / "classes.txt").write_text("\n".join(CLASSES) + "\n", encoding="utf-8")
            self.index_of = list(range(len(CLASSES)))
        (out / "review" / "classes.txt").write_text("\n".join(CLASSES) + "\n", encoding="utf-8")
        # Roboflow nhan ten lop chac nhat qua data.yaml (upload thu muc YOLO)
        (out / "review" / "data.yaml").write_text(
            f"nc: {len(CLASSES)}\nnames: [{', '.join(repr(c) for c in CLASSES)}]\n", encoding="utf-8")
        self.state_path = out / "crawl_state.json"
        st = json.loads(self.state_path.read_text(encoding="utf-8")) if self.state_path.exists() else {}
        self.seen: set[str] = set(st.get("seen", []))
        self.hash_cache: dict[str, int] = {k: int(v) for k, v in st.get("hashes", {}).items()}

    def save_state(self) -> None:
        tmp = self.state_path.with_suffix(".tmp")
        tmp.write_text(json.dumps({"seen": sorted(self.seen), "hashes": {k: str(v) for k, v in self.hash_cache.items()}}),
                       encoding="utf-8")
        tmp.replace(self.state_path)

    def file_hash(self, p: Path) -> int:
        key = str(p)
        if key not in self.hash_cache:
            try:
                with Image.open(p) as im:
                    self.hash_cache[key] = dhash(im)
            except Exception:
                self.hash_cache[key] = 0
        return self.hash_cache[key]

    def counts(self) -> tuple[Counter, Counter, dict]:
        """(so anh ACCEPT theo nhom, so anh REVIEW theo nhom, chi so lon nhat da dung theo nhom)."""
        acc, rev, mx = Counter(), Counter(), defaultdict(int)
        for split, ctr in (("train", acc), ("review", rev)):
            for p in list_images(self.out / split / "images"):
                m = NAME_RE.match(clean_stem(p.name))
                if m:
                    ctr[m.group(1)] += 1
                    mx[m.group(1)] = max(mx[m.group(1)], int(m.group(2)))
        return acc, rev, mx

    @staticmethod
    def append_csv(path: Path, fields: list[str], rows: list[dict]) -> None:
        if not rows:
            return
        new = not path.exists() or path.stat().st_size == 0
        if not new:  # file co san (vd metadata.csv 8 cot cua nhom) -> giu dung header cua no, cot thua bo qua
            with open(path, newline="", encoding="utf-8-sig") as f:
                fields = next(csv.reader(f), None) or fields
        with open(path, "a", newline="", encoding="utf-8") as f:
            w = csv.DictWriter(f, fieldnames=fields, extrasaction="ignore")
            if new:
                w.writeheader()
            w.writerows(rows)


# ---------------------------------------------------------------------------
class AutoLabeler:
    """YOLO11x (COCO) + YOLO-World (prompt VN + hard negative) -> list[Det] moi anh."""

    def __init__(self, coco_model: str, world_model: str | None, imgsz: int, device, th: Thresholds):
        from ultralytics import YOLO

        self.coco = YOLO(coco_model)
        names = self.coco.model.names
        self.coco_map = {COCO80_INDEX[c]: i for i, c in enumerate(CLASSES)}
        if any(names.get(k) != CLASSES[v] for k, v in self.coco_map.items()):
            raise SystemExit(f"!! {coco_model} khong phai model 80 lop COCO")
        self.world = None
        if world_model:
            self.world = YOLO(world_model)
            vocab, self.world_meaning = world_vocabulary()
            self.world.set_classes(vocab)
        self.imgsz, self.device, self.th = imgsz, device, th

    def __call__(self, imgs: list[Image.Image], chunk: int = 8) -> list[list[Det]]:
        # Ultralytics chay ca list anh PIL thanh 1 batch -> chia nho de khong tran VRAM T4 (YOLO11x @1024)
        out: list[list[Det]] = []
        for i in range(0, len(imgs), chunk):
            out += self._run(imgs[i : i + chunk])
        return out

    def _run(self, imgs: list[Image.Image]) -> list[list[Det]]:
        out: list[list[Det]] = [[] for _ in imgs]
        if not imgs:
            return out
        kw = dict(imgsz=self.imgsz, verbose=False, device=self.device)
        for k, r in enumerate(self.coco.predict(imgs, conf=self.th.keep["coco"] * self.th.low, **kw)):
            for c, conf, box in zip(r.boxes.cls.tolist(), r.boxes.conf.tolist(), r.boxes.xyxyn.tolist()):
                if int(c) in self.coco_map:
                    out[k].append(Det(np.array(box), conf, "coco", cls=self.coco_map[int(c)]))
        if self.world is not None:
            for k, r in enumerate(self.world.predict(imgs, conf=self.th.keep["world"] * self.th.low, **kw)):
                for c, conf, box in zip(r.boxes.cls.tolist(), r.boxes.conf.tolist(), r.boxes.xyxyn.tolist()):
                    kind, val = self.world_meaning[int(c)]
                    if kind == "class":
                        out[k].append(Det(np.array(box), conf, "world", cls=val))
                    else:
                        out[k].append(Det(np.array(box), conf, "world", hardneg=val))
        return out


# ---------------------------------------------------------------------------
def save_jpeg(img: Image.Image, path: Path, max_side: int = 4032) -> None:
    if max(img.size) > max_side:
        s = max_side / max(img.size)
        img = img.resize((round(img.width * s), round(img.height * s)), Image.LANCZOS)
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, "JPEG", quality=92)


def crawl_group(group: str, target: str | None, need: int, queries: list[dict], sources, store: Store,
                labeler: AutoLabeler, ref_hashes: np.ndarray, next_idx: int, args, report: dict) -> int:
    """Cao 1 nhom (1 lop | neg | hardneg) toi khi du `need` anh ACCEPT. Tra so anh ACCEPT moi."""
    accepted = 0
    stats = report.setdefault(group, {"accepted": 0, "review": 0, "reject": Counter(), "filtered": Counter()})
    hashes = ref_hashes
    for q in queries:
        for src in sources:
            if accepted >= need:
                return accepted
            text = q.get("vi") if src.name == "ddg" else q.get("en")
            if not text:
                continue
            try:
                cands = src.search(text, args.per_query)
            except Exception as e:
                print(f"   [{src.name}] '{text}': loi tim kiem {type(e).__name__}: {e}")
                continue
            cands = [c for c in cands if c.url and c.url not in store.seen]
            if not args.no_domain_block:
                n0 = len(cands)
                cands = [c for c in cands if not blocked(c)]
                stats["filtered"]["ten mien watermark/TMDT"] += n0 - len(cands)
            cands = [c for c in cands if not (c.width and c.height and max(c.width, c.height) < args.min_side)]
            if not cands:
                continue
            print(f"   [{src.name}] '{text}': {len(cands)} anh moi")
            batch = []
            for cand, res in download_many(cands, args.workers):
                store.seen.add(cand.url)
                if isinstance(res, str):
                    stats["filtered"][res.split(" (")[0]] += 1
                    continue
                why = check_quality(res, args.min_side)
                if why:
                    stats["filtered"][why.split(" (")[0]] += 1
                    continue
                h = np.array([dhash(res)], dtype=np.uint64)
                if len(hashes) and len(near_duplicates(h, hashes, args.dup_dist)):
                    stats["filtered"]["trung anh da co / anh test"] += 1
                    continue
                hashes = np.concatenate([hashes, h])
                batch.append((cand, res, int(h[0])))

            dets_all = labeler([b[1] for b in batch]) if batch else []
            meta_rows, pend_rows, attr_rows, rej_rows, queue_rows = [], [], [], [], []
            for (cand, img, hv), dets in zip(batch, dets_all):
                d = decide(dets, target, labeler.th, concept=q.get("concept"))
                if d.status == "reject":
                    stats["reject"][d.reasons[-1] if d.reasons else "?"] += 1
                    rej_rows.append({"group": group, "url": cand.url, "reason": "; ".join(d.reasons)})
                    continue
                if d.status == "accept" and accepted >= need:
                    continue
                next_idx += 1
                name = f"gg_{group.replace(' ', '_')}_{next_idx:04d}.jpg"
                split = "train" if d.status == "accept" else "review"
                index_of = store.index_of if split == "train" else None  # review/ luon theo thu tu CLASSES
                save_jpeg(img, store.out / split / "images" / name)
                write_yolo_labels(store.out / split / "labels" / name.replace(".jpg", ".txt"), to_yolo_lines(d.clusters, index_of))
                store.hash_cache[str(store.out / split / "images" / name)] = hv
                row = {"filename": name, "split": "train", "source": "gg", "url": cand.url, "scene": q.get("scene", "other"),
                       "lighting": estimate_lighting(img), "distance": estimate_distance(d.clusters, target),
                       "phone": "web", "provider": cand.provider, "license": cand.license, "creator": cand.creator,
                       "query": text, "autolabel": d.status, "target_conf": round(d.target_score * labeler.th.keep["coco"], 3)}
                attr_rows.append({"filename": name, "provider": cand.provider, "url": cand.url, "page": cand.page,
                                  "license": cand.license, "creator": cand.creator, "title": cand.title})
                if d.status == "accept":
                    meta_rows.append(row)
                    accepted += 1
                    stats["accepted"] += 1
                else:
                    pend_rows.append(row)
                    queue_rows.append({"filename": name, "group": group, "reasons": "; ".join(d.reasons), "url": cand.url})
                    stats["review"] += 1
            Store.append_csv(store.out / "metadata.csv", META_FIELDS, meta_rows)
            Store.append_csv(store.out / "review" / "metadata_pending.csv", META_FIELDS, pend_rows)
            Store.append_csv(store.out / "review" / "review_queue.csv", ["filename", "group", "reasons", "url"], queue_rows)
            Store.append_csv(store.out / "web_attributions.csv", ATTR_FIELDS, attr_rows)
            Store.append_csv(store.out / "rejected.csv", ["group", "url", "reason"], rej_rows)
            store.save_state()
            print(f"      -> nhom '{group}': +{len(meta_rows)} accept, +{len(pend_rows)} review, {len(rej_rows)} reject "
                  f"| tong accept moi {accepted}/{need}")
    return accepted


# ---------------------------------------------------------------------------
def promote(store: Store, export_dir: Path) -> None:
    """Anh REVIEW da sua tren Roboflow -> train/. Dung anh goc trong review/ (Roboflow hay resize), nhan tu export."""
    names = find_names(export_dir)
    if not names:
        raise SystemExit(f"!! {export_dir}: khong co data.yaml/classes.txt")
    cmap = load_class_map(Path(__file__).resolve().parents[1] / "yolo_class_map.yaml")
    to_ours = [map_class(n, cmap) for n in names]
    pend_path = store.out / "review" / "metadata_pending.csv"
    pending = {}
    if pend_path.exists():
        with open(pend_path, newline="", encoding="utf-8") as f:
            pending = {r["filename"]: r for r in csv.DictReader(f)}
    review_imgs = {clean_stem(p.name): p for p in list_images(store.out / "review" / "images")}
    moved, meta_rows = [], []
    for img in list_images(export_dir):
        stem = clean_stem(img.name)
        if stem not in review_imgs:
            continue
        try:
            lab = label_path_for(img)
        except ValueError:
            lab = img.with_suffix(".txt")
        boxes = [(store.index_of[to_ours[c]], x, y, w, h) for c, x, y, w, h in read_yolo_labels(lab)
                 if 0 <= c < len(to_ours) and to_ours[c] is not None]
        src = review_imgs[stem]
        dst = store.out / "train" / "images" / src.name
        shutil.move(str(src), dst)
        (store.out / "review" / "labels" / (stem + ".txt")).unlink(missing_ok=True)
        write_yolo_labels(store.out / "train" / "labels" / (stem + ".txt"), boxes)
        row = pending.pop(src.name, {"filename": src.name, "split": "train", "source": "gg", "phone": "web"})
        row["autolabel"] = "reviewed"
        meta_rows.append(row)
        moved.append(stem)
    Store.append_csv(store.out / "metadata.csv", META_FIELDS, meta_rows)
    with open(pend_path, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=META_FIELDS, extrasaction="ignore")
        w.writeheader()
        w.writerows(pending.values())
    left = len(list_images(store.out / "review" / "images"))
    print(f"OK: chuyen {len(moved)} anh da duyet vao train/. Con {left} anh trong review/ "
          "(anh ban da XOA tren Roboflow van nam day — xoa tay neu muon bo).")


def write_report(store: Store, report: dict, queries: dict, wanted: list[str]) -> None:
    acc, rev, _ = store.counts()
    rows = []
    if (store.out / "metadata.csv").exists():
        with open(store.out / "metadata.csv", newline="", encoding="utf-8") as f:
            rows = list(csv.DictReader(f))
    div: dict = {}
    for g in list(queries.get("classes", {})):
        rs = [r for r in rows if NAME_RE.match(clean_stem(r["filename"])) and NAME_RE.match(clean_stem(r["filename"])).group(1) == g.replace(" ", "_")]
        n = max(len(rs), 1)
        div[g] = {"n": len(rs),
                  "lighting": {k: round(v / n, 2) for k, v in Counter(r["lighting"] for r in rs).items()},
                  "distance": {k: round(v / n, 2) for k, v in Counter(r["distance"] for r in rs).items()},
                  "scenes": sorted({r["scene"] for r in rs})}
    out = {"accepted": dict(acc), "review": dict(rev), "runs": {k: {**v, "reject": dict(v["reject"]), "filtered": dict(v["filtered"])}
                                                                 for k, v in report.items()}, "diversity": div}
    (store.out / "crawl_report.json").write_text(json.dumps(out, indent=2, ensure_ascii=False), encoding="utf-8")
    prov = Counter(r.get("provider", "") for r in rows)
    lic = Counter(r.get("license", "") for r in rows)
    (store.out / "data_sources.md").write_text(
        "# Nguồn ảnh Internet (sinh bởi scripts/17_crawl_autolabel.py)\n\n"
        "Ảnh dùng cho mục đích học thuật (đồ án), chỉ trong tập VN-train, không phát tán lại. "
        "Trích nguồn từng ảnh (URL, trang gốc, giấy phép, tác giả): `web_attributions.csv`.\n\n"
        "| Nguồn | Số ảnh |\n|---|---|\n" + "".join(f"| {k} | {v} |\n" for k, v in prov.most_common()) +
        "\n| Giấy phép | Số ảnh |\n|---|---|\n" + "".join(f"| {k} | {v} |\n" for k, v in lic.most_common(15)),
        encoding="utf-8")
    print("\n===== TONG KET =====")
    print(f"{'nhom':14s} {'accept':>6s} {'review':>6s}  toi%  xa%  boi canh")
    for g in wanted + ["neg", "hardneg"]:
        key = g.replace(" ", "_")
        d = div.get(g, {})
        dark = d.get("lighting", {}).get("dark", 0)
        far = d.get("distance", {}).get("far", 0)
        sc = len(d.get("scenes", []))
        warn = []
        if g in div and d["n"]:
            if dark < 0.15:
                warn.append("thieu anh toi")
            if far < 0.1:
                warn.append("thieu anh xa")
            if sc < 4:
                warn.append("it boi canh")
        print(f"{g:14s} {acc.get(key, 0):6d} {rev.get(key, 0):6d}  {dark:4.0%} {far:4.0%}  {sc:2d}   {', '.join(warn)}")
    print("-> Thieu do da dang: them truy van tuong ung trong crawl_queries.yaml ('ban dem', 'bai xe'...) hoac bu bang anh tu chup.")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True, help="thu muc VN dau ra (nen nam tren Drive de resume)")
    ap.add_argument("--queries", default=str(Path(__file__).resolve().parents[1] / "crawl_queries.yaml"))
    ap.add_argument("--classes", default="all", help="'all' hoac danh sach cach nhau dau phay, vd 'cup,chair'")
    ap.add_argument("--per-class", type=int, default=100, help="so anh ACCEPT can co moi lop (tinh ca lan chay truoc)")
    ap.add_argument("--negatives", type=int, default=150)
    ap.add_argument("--hard-negatives", type=int, default=150)
    ap.add_argument("--sources", default="openverse,wikimedia,ddg")
    ap.add_argument("--ddg-license", default=None, help="None = moi license | Share | ShareCommercially | Modify | ModifyCommercially")
    ap.add_argument("--per-query", type=int, default=40, help="so ket qua lay moi (truy van x nguon)")
    ap.add_argument("--min-side", type=int, default=1024, help="canh dai toi thieu (DATASET_SPEC muc E)")
    ap.add_argument("--test-dir", action="append", default=[], help="anh TEST that — anh cao trung voi day bi bo (chong ro ri)")
    ap.add_argument("--dup-dist", type=int, default=5)
    ap.add_argument("--coco-model", default="yolo11x.pt")
    ap.add_argument("--world-model", default="yolov8x-worldv2.pt", help="'' de tat YOLO-World (chi dung COCO)")
    ap.add_argument("--imgsz", type=int, default=1024)
    ap.add_argument("--device", default=None, help="0 (GPU) | cpu | bo trong = tu chon")
    ap.add_argument("--workers", type=int, default=6, help="so luong tai anh song song")
    ap.add_argument("--accept", type=float, default=1.6, help="nguong ACCEPT (boi so cua nguong giu box)")
    ap.add_argument("--strong", type=float, default=2.1)
    ap.add_argument("--no-domain-block", action="store_true", help="tat chan ten mien stock/TMDT (anh watermark)")
    ap.add_argument("--contact", default=None, help="(tuy chon) thong tin lien he them vao User-Agent — Wikimedia it chan hon")
    ap.add_argument("--promote", help="thu muc export YOLO tu Roboflow chua anh REVIEW da sua")
    args = ap.parse_args()

    store = Store(Path(args.out))
    if args.promote:
        promote(store, Path(args.promote))
        return

    queries = yaml.safe_load(Path(args.queries).read_text(encoding="utf-8"))
    wanted = list(queries["classes"]) if args.classes == "all" else [c.strip() for c in args.classes.split(",") if c.strip()]
    bad = [c for c in wanted if c not in CLASSES or c not in queries["classes"]]
    if bad:
        raise SystemExit(f"!! lop khong hop le / chua co tu khoa: {bad}")
    bad_concepts = [q["concept"] for q in queries.get("hard_negatives", []) if q.get("concept") and q["concept"] not in HARDNEG_PROMPTS]
    if bad_concepts:
        raise SystemExit(f"!! concept khong co trong HARDNEG_PROMPTS (snapget12/autolabel.py): {bad_concepts}")

    set_contact(args.contact)
    th = Thresholds(accept=args.accept, strong=args.strong)
    print("Nap model gan nhan ...")
    labeler = AutoLabeler(args.coco_model, args.world_model or None, args.imgsz, args.device, th)
    sources = make_sources([s.strip() for s in args.sources.split(",") if s.strip()], args.ddg_license)

    # hash anh da co (train + review) + anh test -> khu trung / chong ro ri
    ref = [store.file_hash(p) for p in list_images(store.out / "train" / "images") + list_images(store.out / "review" / "images")]
    for td in args.test_dir:
        ref += [store.file_hash(p) for p in list_images(td)]
    ref_hashes = np.array([h for h in ref if h], dtype=np.uint64)
    store.save_state()
    print(f"{len(ref_hashes)} anh tham chieu (da co + test) de khu trung")

    report: dict = {}
    groups = [(c, c, args.per_class, queries["classes"][c]) for c in wanted]
    groups += [("neg", None, args.negatives, queries.get("negatives", [])),
               ("hardneg", None, args.hard_negatives, queries.get("hard_negatives", []))]
    t0 = time.time()
    for group, target, total, qs in groups:
        acc, _, mx = store.counts()
        key = group.replace(" ", "_")
        need = total - acc.get(key, 0)
        if need <= 0 or not qs:
            print(f"[{group}] da du {acc.get(key, 0)}/{total} — bo qua")
            continue
        print(f"\n[{group}] can them {need} anh (dang co {acc.get(key, 0)})")
        got = crawl_group(group, target, need, qs, sources, store, labeler, ref_hashes, mx.get(key, 0), args, report)
        ref_hashes = np.array([h for h in store.hash_cache.values() if h], dtype=np.uint64)
        if got < need:
            print(f"   !! [{group}] chi duoc {got}/{need} — het tu khoa: them truy van trong crawl_queries.yaml, "
                  "tang --per-query, hoac duyet anh trong review/ roi --promote")
    write_report(store, report, queries, wanted)
    print(f"\nXong sau {(time.time() - t0) / 60:.0f} phut -> {store.out}. "
          f"Tiep: duyet review/ (review_queue.csv) tren Roboflow -> --promote; kiem tra ngau nhien ~10% anh accept.")


if __name__ == "__main__":
    main()

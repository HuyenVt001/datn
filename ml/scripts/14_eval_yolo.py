"""
14 — Danh gia + chon nguong cho MOI model tren CUNG tap (QUEST_AI_PLAN.md muc 5–6, ablation):

    python scripts/14_eval_yolo.py --root /content/yolo --out artifacts/eval \
        --model yolo11s-coco=yolo11s.pt \
        --model y1-s=artifacts/y1-s/best.pt \
        --model y2-s=artifacts/y2-s/best.pt \
        --vn-meta /content/ext/vn/metadata.csv --max-coco 2000

  --model NAME=PATH       YOLO .pt (GPU neu co) hoac .onnx (CPU). Model 80 lop COCO (yolo11s.pt) tu dong
                          cat head con cac lop CLASSES = baseline "KHONG fine-tune". Model co NHIEU lop hon
                          CLASSES (vd y1 12 lop) chi lay diem cac lop CLASSES -> so sanh cap anh (khong chay detection).
  --classifier NAME=ONNX  model v0 (MobileNetV3) — chi co so lieu cap anh (khong co box).

2 cap so lieu:
  1. DETECTION (Ultralytics val, chi YOLO .pt): mAP50, mAP50-95, P/R/F1 tung lop, CONFUSION MATRIX
     (so lop + background), PR/F1 curve  -> det_<split>.json + thu muc plots
  2. VERIFY CAP ANH (dung quyet dinh san pham): diem lop = max theo anchor (snapget12/yolo.py),
     AP/P/R/F1/false-reject tung lop + confusion cap anh  -> metrics_<split>.json, *.png
Nguong tung lop chon tren REAL-CALIB (anh that) voi precision >= --min-precision; chua co anh that
thi tam chon tren val.txt (co canh bao). Cung 1 quy trinh cho moi model -> so sanh cong bang.
Ra: <out>/<NAME>/{thresholds.json, metrics_*.json, det_*.json, ...} + <out>/ablation.json (+ bang in ra).
"""
from __future__ import annotations

import argparse
import csv
import json
import sys
import time
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np  # noqa: E402

from snapget12 import CLASSES  # noqa: E402
from snapget12.metrics import (  # noqa: E402
    MIN_PRECISION_REAL,
    choose_thresholds,
    evaluate,
    image_confusion,
    plot_pr_curves,
    pr_curves,
    save_json,
    sigmoid,
)
from snapget12.yolo import (  # noqa: E402
    classifier_preprocess,
    clean_stem,
    image_scores_from_raw,
    label_path_for,
    letterbox,
    load_rgb,
    multi_hot,
    read_yolo_labels,
    slice_coco_head,
)


# ---------------------------------------------------------------------------
# Scorer: anh -> (N, len(CLASSES)) diem cap anh
# ---------------------------------------------------------------------------
class YoloTorchScorer:
    def __init__(self, path: str, tmp_dir: Path):
        import torch
        from ultralytics import YOLO

        y = YOLO(path)
        if len(y.model.names) == 80:  # model COCO goc -> cat head con CLASSES (baseline khong fine-tune)
            slice_coco_head(y)
            tmp_dir.mkdir(parents=True, exist_ok=True)
            path = str(tmp_dir / f"{Path(path).stem}-coco{len(CLASSES)}.pt")
            y.save(path)
        names = [y.model.names[i] for i in range(len(y.model.names))]
        self.nc = len(names)
        self.cols = None  # None = output dung thu tu CLASSES
        self.det_ok = names == CLASSES
        if names != CLASSES:
            if not set(CLASSES) <= set(names):
                raise SystemExit(f"!! {path}: lop {names} khong chua du CLASSES {CLASSES}")
            # vd y1 (12 lop): lay diem cac lop CLASSES de so sanh cap anh; detection bo qua vi chi so lop khac data.yaml
            self.cols = [names.index(c) for c in CLASSES]
            print(f"   {path}: {len(names)} lop -> chi lay {len(CLASSES)} lop CLASSES (khong chay detection)")
        self.weights = path
        self.device = "cuda" if torch.cuda.is_available() else "cpu"
        self.model = y.model.to(self.device).eval()
        self.torch = torch

    def __call__(self, paths: list[Path], batch: int = 32) -> np.ndarray:
        out = []
        for i in range(0, len(paths), batch):
            x = np.concatenate([letterbox(load_rgb(p))[0] for p in paths[i : i + batch]])
            with self.torch.no_grad():
                raw = self.model(self.torch.from_numpy(x).to(self.device))
            raw = raw[0] if isinstance(raw, (list, tuple)) else raw
            sc = image_scores_from_raw(raw.float().cpu().numpy(), num_classes=self.nc)
            out.append(sc if self.cols is None else sc[:, self.cols])
        return np.concatenate(out) if out else np.zeros((0, len(CLASSES)))


class OnnxScorer:
    def __init__(self, path: str, kind: str):
        import onnxruntime as ort

        self.sess = ort.InferenceSession(path, providers=["CPUExecutionProvider"])
        self.name = self.sess.get_inputs()[0].name
        self.kind = kind  # yolo | classifier

    def one(self, p) -> np.ndarray:
        img = load_rgb(p)
        if self.kind == "yolo":
            return image_scores_from_raw(self.sess.run(None, {self.name: letterbox(img)[0]})[0])[0]
        return sigmoid(self.sess.run(None, {self.name: classifier_preprocess(img)})[0][0].astype(np.float64))

    def __call__(self, paths: list[Path], batch: int = 0) -> np.ndarray:
        return np.stack([self.one(p) for p in paths]) if paths else np.zeros((0, len(CLASSES)))

    def latency(self, p, n: int = 30) -> dict:
        img = load_rgb(p)
        x = letterbox(img)[0] if self.kind == "yolo" else classifier_preprocess(img)
        for _ in range(3):
            self.sess.run(None, {self.name: x})
        t = []
        for _ in range(n):
            t0 = time.perf_counter()
            self.sess.run(None, {self.name: x})
            t.append((time.perf_counter() - t0) * 1000)
        return {"p50": round(float(np.percentile(t, 50)), 1), "p95": round(float(np.percentile(t, 95)), 1)}


# ---------------------------------------------------------------------------
def read_list(path: Path, limit: int | None = None, seed: int = 0) -> list[Path]:
    if not path.exists():
        return []
    items = [Path(ln.strip()) for ln in path.read_text(encoding="utf-8").splitlines() if ln.strip()]
    items = list(dict.fromkeys(items))  # bo lap (train.txt lap anh VN) giu thu tu
    if limit and len(items) > limit:
        rng = np.random.default_rng(seed)
        items = [items[i] for i in sorted(rng.choice(len(items), limit, replace=False))]
    return items


def labels_for(paths: list[Path]) -> np.ndarray:
    return np.stack([multi_hot(read_yolo_labels(label_path_for(p))) for p in paths]) if paths else np.zeros((0, len(CLASSES)), int)


def plot_confusion(conf: dict, out_png: Path, title: str) -> None:
    import matplotlib

    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    rows, cols = conf["rows"], conf["cols"]
    m = np.array([[np.nan if conf["matrix"][r][c] is None else conf["matrix"][r][c] for c in cols] for r in rows])
    fig, ax = plt.subplots(figsize=(11, 9))
    im = ax.imshow(m, cmap="Blues", vmin=0, vmax=1)
    ax.set_xticks(range(len(cols)), cols, rotation=45, ha="right")
    ax.set_yticks(range(len(rows)), [f"{r} (n={conf['counts'][r]})" for r in rows])
    for i in range(len(rows)):
        for j in range(len(cols)):
            if not np.isnan(m[i, j]):
                ax.text(j, i, f"{m[i, j]:.2f}", ha="center", va="center", fontsize=7,
                        color="white" if m[i, j] > 0.5 else "black")
    ax.set_xlabel("model noi anh co lop nay (score >= nguong)")
    ax.set_ylabel("anh that su co lop (hang 'negative' = khong co lop nao)")
    ax.set_title(title)
    fig.colorbar(im)
    fig.tight_layout()
    fig.savefig(out_png, dpi=130)
    plt.close(fig)


def detection_eval(weights: str, yaml_path: Path, out_dir: Path, name: str) -> dict | None:
    """Ultralytics val -> so lieu box + confusion matrix 13x13."""
    from ultralytics import YOLO

    if not yaml_path.exists():
        return None
    m = YOLO(weights).val(data=str(yaml_path), split="test", imgsz=640, batch=16, conf=0.001, iou=0.6,
                          plots=True, project=str(out_dir), name=f"det_{name}", exist_ok=True, verbose=False)
    box = m.box
    per = {}
    for k, ci in enumerate(box.ap_class_index):
        c = CLASSES[int(ci)]
        per[c] = {"ap50": round(float(box.ap50[k]), 4), "ap50_95": round(float(box.ap[k]), 4),
                  "precision": round(float(box.p[k]), 4), "recall": round(float(box.r[k]), 4), "f1": round(float(box.f1[k]), 4)}
    cm = m.confusion_matrix.matrix
    return {
        "mAP50": round(float(box.map50), 4), "mAP50_95": round(float(box.map), 4), "perClass": per,
        # Ultralytics: hang = lop DU DOAN, cot = lop THAT, phan tu cuoi = background
        "confusionMatrix": {"labels": CLASSES + ["background"], "rows": "predicted", "cols": "true", "matrix": cm.astype(int).tolist()},
        "plotsDir": str(Path(m.save_dir)),
    }


def by_condition(paths: list[Path], y: np.ndarray, s: np.ndarray, thr: dict, meta_path: str | None) -> dict:
    """Metric theo dieu kien chup (metadata.csv cua tap VN): anh sang/toi, gan/xa, boi canh."""
    if not meta_path or not Path(meta_path).exists():
        return {}
    with open(meta_path, newline="", encoding="utf-8-sig") as f:
        meta = {clean_stem(r["filename"].strip()): r for r in csv.DictReader(f)}
    out: dict = {}
    for field in ("lighting", "distance", "scene"):
        groups: dict[str, list[int]] = defaultdict(list)
        for i, p in enumerate(paths):
            row = meta.get(clean_stem(p.name[3:] if p.name.startswith("vn_") else p.name))
            if row and row.get(field, "").strip():
                groups[row[field].strip()].append(i)
        out[field] = {}
        for k, idx in sorted(groups.items()):
            rep = evaluate(y[idx], s[idx], thr)
            frr = [v["falseRejectRate"] for v in rep["perClass"].values() if v["falseRejectRate"] is not None]
            out[field][k] = {"n": len(idx), "mAP": rep["mAP"], "macroF1": rep["macroF1"],
                             "meanFalseReject": round(float(np.mean(frr)), 4) if frr else None}
    return out


def parse_named(items: list[str]) -> list[tuple[str, str]]:
    out = []
    for it in items:
        if "=" not in it:
            raise SystemExit(f"!! dinh dang NAME=PATH: {it}")
        n, p = it.split("=", 1)
        out.append((n.strip(), p.strip()))
    return out


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default="/content/yolo", help="output cua 11_merge_external.py")
    ap.add_argument("--out", default="artifacts/eval")
    ap.add_argument("--model", action="append", default=[])
    ap.add_argument("--classifier", action="append", default=[])
    ap.add_argument("--min-precision", type=float, default=MIN_PRECISION_REAL)
    ap.add_argument("--max-coco", type=int, default=2000, help="lay mau COCO test cho nhanh (0 = het)")
    ap.add_argument("--vn-meta", help="metadata.csv cua tap VN -> metric theo dieu kien chup")
    ap.add_argument("--no-det", action="store_true", help="bo qua Ultralytics val (chi cap anh)")
    args = ap.parse_args()

    root, out = Path(args.root), Path(args.out)
    lists = root / "lists"
    calib = read_list(lists / "real_calib.txt")
    calib_name = "real_calib"
    if not calib:
        calib = read_list(lists / "val.txt", limit=3000)
        calib_name = "val"
        print("!! real_calib rong (chua co tap VN) -> TAM chon nguong tren val.txt. Chay lai khi co anh that.")
    splits = {"real_test": read_list(lists / "real_test.txt"),
              "coco_test": read_list(lists / "coco_test.txt", limit=args.max_coco or None)}
    labels = {k: labels_for(v) for k, v in splits.items()}
    y_cal = labels_for(calib)
    print(f"calib ({calib_name}) {len(calib)} anh | real_test {len(splits['real_test'])} | coco_test {len(splits['coco_test'])}")

    runs = [(n, p, "yolo") for n, p in parse_named(args.model)] + [(n, p, "classifier") for n, p in parse_named(args.classifier)]
    if not runs:
        raise SystemExit("!! can it nhat 1 --model hoac --classifier")
    ablation = []
    for name, path, kind in runs:
        print(f"\n===== {name} ({kind}: {path}) =====")
        d = out / name
        d.mkdir(parents=True, exist_ok=True)
        if kind == "yolo" and path.endswith(".pt"):
            scorer = YoloTorchScorer(path, d / "_tmp")
        else:
            scorer = OnnxScorer(path, kind)

        s_cal = scorer(calib)
        thr = choose_thresholds(y_cal, s_cal, args.min_precision)
        save_json(thr, d / "thresholds.json")
        save_json({"calibratedOn": calib_name, "n": len(calib), "minPrecision": args.min_precision}, d / "thresholds_info.json")
        print("nguong:", thr)

        lat = None
        if isinstance(scorer, OnnxScorer) and (splits["real_test"] or calib):
            lat = scorer.latency((splits["real_test"] or calib)[0])
            save_json(lat, d / "latency_cpu.json")
            print(f"latency CPU (onnxruntime 1 anh): {lat}")

        for split, paths in splits.items():
            if not paths:
                continue
            s = scorer(paths)
            y = labels[split]
            rep = evaluate(y, s, thr)
            conf = image_confusion(y, s, thr)
            rep["imageConfusion"] = conf
            rep["calibratedOn"] = calib_name
            if split == "real_test":
                rep["byCondition"] = by_condition(paths, y, s, thr, args.vn_meta)
            np.savez_compressed(d / f"scores_{split}.npz", scores=s, labels=y, paths=np.array([str(p) for p in paths]))
            save_json(rep, d / f"metrics_{split}.json")
            plot_pr_curves(pr_curves(y, s), thr, rep["perClass"], d / f"pr_curves_{split}.png",
                           f"{name} — {split}: mAP {rep['mAP']}  macro-F1 {rep['macroF1']}", min_precision=args.min_precision)
            plot_confusion(conf, d / f"image_confusion_{split}.png", f"{name} — {split}: confusion cap anh tai nguong")
            det = None
            if kind == "yolo" and path.endswith(".pt") and not args.no_det and scorer.det_ok:
                det = detection_eval(scorer.weights, root / f"{split}.yaml", d, split)
                if det:
                    save_json(det, d / f"det_{split}.json")
            print(f"[{split}] n={rep['n']} mAP(anh)={rep['mAP']} macroF1={rep['macroF1']}"
                  + (f" | det mAP50={det['mAP50']} mAP50-95={det['mAP50_95']}" if det else ""))
            for c in CLASSES:
                pc = rep["perClass"][c]
                dd = (det or {}).get("perClass", {}).get(c, {})
                print(f"   {c:13s} AP={pc['ap']} F1={pc['f1']:.3f} P={pc['precision']:.3f} R={pc['recall']:.3f} "
                      f"FRR={pc['falseRejectRate']} n={pc['support']}" + (f" | AP50={dd.get('ap50')}" if dd else ""))
            ablation.append({"model": name, "kind": kind, "split": split, "n": rep["n"], "imageMAP": rep["mAP"],
                             "macroF1": rep["macroF1"], "detMAP50": det["mAP50"] if det else None,
                             "detMAP50_95": det["mAP50_95"] if det else None,
                             "latencyP50ms": lat["p50"] if lat else None, "latencyP95ms": lat["p95"] if lat else None})

    save_json(ablation, out / "ablation.json")
    print(f"\n{'model':16s} {'split':10s} {'n':>5s} {'mAP(anh)':>9s} {'macroF1':>8s} {'detAP50':>8s} {'det50-95':>9s} {'p50ms':>6s}")
    for r in ablation:
        f = lambda v: "-" if v is None else f"{v:.3f}" if isinstance(v, float) else str(v)  # noqa: E731
        print(f"{r['model']:16s} {r['split']:10s} {r['n']:5d} {f(r['imageMAP']):>9s} {f(r['macroF1']):>8s} "
              f"{f(r['detMAP50']):>8s} {f(r['detMAP50_95']):>9s} {f(r['latencyP50ms']):>6s}")
    print(f"\nOK -> {out}. Tiep: 15_eval_report.py --eval {out}/<model>")


if __name__ == "__main__":
    main()

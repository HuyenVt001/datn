"""
07 — Ablation: OWL-ViT base-patch32 ZERO-SHOT (khong train) tren CUNG 2 tap test
     (COCO test + Snapget-12) — QUEST_AI_PLAN muc 5.3.

    python scripts/07_ablation_owlvit.py --manifest data/manifest.csv --snapget12 data/snapget12 \
        --out artifacts/owlvit --max-test 1500

Cau hoi tra loi truoc hoi dong: model nho tu train chuyen biet 12 lop co thang model lon
(~150M params) zero-shot tren dung mien cua no khong? — kem chenh lech latency.
Score moi lop = max score cua cac box detect duoc voi text query "a photo of a <class>".
Nguong chon giong cach cua model chinh (precision >= 0.8 tren val, max recall) de cong bang.
Chay GPU cho nhanh (CPU vai giay/anh -> gioi han --max-test).
"""
from __future__ import annotations

import argparse
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np  # noqa: E402
import torch  # noqa: E402
from PIL import Image, ImageOps  # noqa: E402
from tqdm import tqdm  # noqa: E402

from snapget12 import CLASSES  # noqa: E402
from snapget12.data import labels_of, read_manifest, scan_snapget12  # noqa: E402
from snapget12.metrics import MIN_PRECISION, choose_thresholds, evaluate, plot_pr_curves, pr_curves, save_json  # noqa: E402


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--manifest", required=True)
    ap.add_argument("--snapget12")
    ap.add_argument("--out", default="artifacts/owlvit")
    ap.add_argument("--max-val", type=int, default=1500)
    ap.add_argument("--max-test", type=int, default=1500)
    ap.add_argument("--model", default="google/owlvit-base-patch32")
    ap.add_argument("--seed", type=int, default=42)
    args = ap.parse_args()

    from transformers import OwlViTForObjectDetection, OwlViTProcessor

    device = "cuda" if torch.cuda.is_available() else "cpu"
    processor = OwlViTProcessor.from_pretrained(args.model)
    model = OwlViTForObjectDetection.from_pretrained(args.model).to(device).eval()
    queries = [f"a photo of a {c}" for c in CLASSES]

    def score_image(path: str) -> tuple[np.ndarray, float]:
        im = ImageOps.exif_transpose(Image.open(path)).convert("RGB")
        inputs = processor(text=[queries], images=im, return_tensors="pt").to(device)
        t0 = time.perf_counter()
        with torch.no_grad():
            out = model(**inputs)
        dt = (time.perf_counter() - t0) * 1000
        logits = out.logits[0]  # (num_boxes, 12)
        probs = torch.sigmoid(logits).max(dim=0).values  # max qua cac box -> score/lop
        return probs.float().cpu().numpy(), dt

    def run(rows, name):
        scores, labels, lat = [], [], []
        for r in tqdm(rows, desc=name):
            s, dt = score_image(r["filepath"])
            scores.append(s)
            labels.append(labels_of(r))
            lat.append(dt)
        return np.stack(scores), np.stack(labels).astype(int), lat

    rng = np.random.default_rng(args.seed)
    rows = read_manifest(args.manifest)
    val = [r for r in rows if r["split"] == "val"]
    test = [r for r in rows if r["split"] == "test"]
    if len(val) > args.max_val:
        val = list(rng.choice(val, args.max_val, replace=False))
    if len(test) > args.max_test:
        test = list(rng.choice(test, args.max_test, replace=False))

    sv, lv, _ = run(val, "owlvit-val")
    thresholds = choose_thresholds(lv, sv, MIN_PRECISION)
    save_json(thresholds, f"{args.out}/thresholds.json")

    results = {}
    for name, rws in (("test", test), ("snapget12", scan_snapget12(args.snapget12) if args.snapget12 else [])):
        if not rws:
            continue
        s, l, lat = run(rws, f"owlvit-{name}")
        rep = evaluate(l, s, thresholds)
        rep["latencyMs"] = {"p50": float(np.percentile(lat, 50)), "p95": float(np.percentile(lat, 95)), "device": device}
        save_json(rep, f"{args.out}/metrics_{name}.json")
        plot_pr_curves(pr_curves(l, s), thresholds, rep["perClass"], f"{args.out}/pr_curves_{name}.png",
                       f"OWL-ViT zero-shot ({name}) — mAP {rep['mAP']}")
        results[name] = rep
        print(f"[owlvit {name}] n={rep['n']} mAP={rep['mAP']} macroF1={rep['macroF1']} latency p50 {rep['latencyMs']['p50']:.0f}ms")
    save_json(results, f"{args.out}/summary.json")


if __name__ == "__main__":
    main()

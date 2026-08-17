"""
04 — Chon nguong per-class tren VAL (precision >= 0.8, max recall) + danh gia tren TEST
     (COCO val2017) + ve PR curve — QUEST_AI_PLAN muc 5.

    python scripts/04_eval_thresholds.py --emb data/embeddings.npz --head artifacts/v0/head.pt \
        --out artifacts/v0
    -> artifacts/v0/thresholds.json   (di kem model len Space)
       artifacts/v0/metrics_val.json / metrics_test.json
       artifacts/v0/pr_curves_val.png / pr_curves_test.png

Dung cache embedding (v0). Voi model v1 (fine-tune backbone) dung 08_eval_images.py.
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np  # noqa: E402
import torch  # noqa: E402

from snapget12 import CLASSES, Head  # noqa: E402
from snapget12.metrics import (  # noqa: E402
    MIN_PRECISION,
    choose_thresholds,
    evaluate,
    plot_pr_curves,
    pr_curves,
    save_json,
    sigmoid,
)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--emb", default="data/embeddings.npz")
    ap.add_argument("--head", default="artifacts/v0/head.pt")
    ap.add_argument("--out", default="artifacts/v0")
    ap.add_argument("--min-precision", type=float, default=MIN_PRECISION)
    args = ap.parse_args()

    z = np.load(args.emb, allow_pickle=True)
    emb, labels, splits = z["embeddings"].astype(np.float32), z["labels"].astype(int), z["splits"]
    ckpt = torch.load(args.head, map_location="cpu")
    head = Head(hidden=ckpt.get("hidden", 256), dropout=ckpt.get("dropout", 0.2))
    head.load_state_dict(ckpt["state_dict"])
    head.eval()
    with torch.no_grad():
        scores = sigmoid(head(torch.from_numpy(emb)).numpy())

    va, te = splits == "val", splits == "test"
    thresholds = choose_thresholds(labels[va], scores[va], args.min_precision)
    save_json(thresholds, f"{args.out}/thresholds.json")
    print("thresholds:", thresholds)

    for name, mask in (("val", va), ("test", te)):
        rep = evaluate(labels[mask], scores[mask], thresholds)
        save_json(rep, f"{args.out}/metrics_{name}.json")
        curves = pr_curves(labels[mask], scores[mask])
        plot_pr_curves(curves, thresholds, rep["perClass"], f"{args.out}/pr_curves_{name}.png",
                       f"PR curves ({name}) — mAP {rep['mAP']}  macro-F1 {rep['macroF1']}")
        print(f"[{name}] n={rep['n']} mAP={rep['mAP']} macroF1={rep['macroF1']}")
        for c in CLASSES:
            pc = rep["perClass"][c]
            print(f"   {c:14s} thr={pc['threshold']:.3f} P={pc['precision']:.3f} R={pc['recall']:.3f} F1={pc['f1']:.3f} AP={pc['ap']} n={pc['support']}")


if __name__ == "__main__":
    main()

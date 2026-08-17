"""
08 — Danh gia BAT KY model (v0/v1, ONNX hoac .pt) tren tap anh goc:
     COCO test (manifest split=test) va/hoac Snapget-12 (thu muc tu chup) — muc 3.4, 5, 13.1.

    # v1 tren val -> chon nguong, roi test + snapget12
    python scripts/08_eval_images.py --full artifacts/v1/model.pt --manifest data/manifest.csv \
        --choose-thresholds-on val --out artifacts/v1 --snapget12 data/snapget12
    # v0 ONNX int8 (dung model dang chay tren Space) tren Snapget-12
    python scripts/08_eval_images.py --onnx artifacts/v0/model.onnx --thresholds artifacts/v0/thresholds.json \
        --snapget12 data/snapget12 --out artifacts/v0

Ra: metrics_<split>.json + pr_curves_<split>.png (+ thresholds.json neu --choose-thresholds-on).
Chenh lech COCO-test vs Snapget-12 = DOMAIN SHIFT — muc phan tich chinh cua bao cao.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np  # noqa: E402
import torch  # noqa: E402
from torch.utils.data import DataLoader  # noqa: E402
from tqdm import tqdm  # noqa: E402

from snapget12 import CLASSES, Head, build_model  # noqa: E402
from snapget12.data import ManifestDataset, eval_transform, read_manifest, scan_snapget12  # noqa: E402
from snapget12.metrics import MIN_PRECISION, choose_thresholds, evaluate, plot_pr_curves, pr_curves, save_json, sigmoid  # noqa: E402


class OnnxRunner:
    def __init__(self, path: str):
        import onnxruntime as ort

        self.sess = ort.InferenceSession(path, providers=["CPUExecutionProvider"])
        self.name = self.sess.get_inputs()[0].name

    def __call__(self, x: torch.Tensor) -> np.ndarray:
        return np.concatenate([self.sess.run(None, {self.name: x[i : i + 1].numpy()})[0] for i in range(len(x))])


def load_runner(args, device):
    if args.onnx:
        return OnnxRunner(args.onnx), "cpu"
    if args.full:
        ckpt = torch.load(args.full, map_location="cpu")
        model = build_model(pretrained=False)
        model.head = Head(hidden=ckpt.get("hidden", 256), dropout=ckpt.get("dropout", 0.2))
        model.load_state_dict(ckpt["state_dict"])
    else:
        ckpt = torch.load(args.head, map_location="cpu")
        model = build_model(pretrained=True)
        model.head = Head(hidden=ckpt.get("hidden", 256), dropout=ckpt.get("dropout", 0.2))
        model.head.load_state_dict(ckpt["state_dict"])
    model.to(device).eval()

    def run(x: torch.Tensor) -> np.ndarray:
        with torch.no_grad():
            return model(x.to(device)).float().cpu().numpy()

    return run, device


def score_rows(rows, runner, workers) -> tuple[np.ndarray, np.ndarray]:
    dl = DataLoader(ManifestDataset(rows, eval_transform()), batch_size=64, num_workers=workers)
    scores, labels = [], []
    for x, y in tqdm(dl, desc="eval"):
        scores.append(runner(x))
        labels.append(y.numpy())
    return sigmoid(np.concatenate(scores)), np.concatenate(labels).astype(int)


def report(name, labels, scores, thresholds, out):
    rep = evaluate(labels, scores, thresholds)
    save_json(rep, f"{out}/metrics_{name}.json")
    plot_pr_curves(pr_curves(labels, scores), thresholds, rep["perClass"], f"{out}/pr_curves_{name}.png",
                   f"PR curves ({name}) — mAP {rep['mAP']}  macro-F1 {rep['macroF1']}")
    print(f"[{name}] n={rep['n']} mAP={rep['mAP']} macroF1={rep['macroF1']}")
    for c in CLASSES:
        pc = rep["perClass"][c]
        print(f"   {c:14s} P={pc['precision']:.3f} R={pc['recall']:.3f} F1={pc['f1']:.3f} AP={pc['ap']} n={pc['support']}")


def main() -> None:
    ap = argparse.ArgumentParser()
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--onnx")
    g.add_argument("--full")
    g.add_argument("--head")
    ap.add_argument("--manifest", help="manifest COCO (de lay split val/test)")
    ap.add_argument("--snapget12", help="thu muc Snapget-12 tu chup")
    ap.add_argument("--thresholds", help="thresholds.json co san (khong chon lai)")
    ap.add_argument("--choose-thresholds-on", choices=["val", "test"], help="chon nguong moi tren split nay")
    ap.add_argument("--min-precision", type=float, default=MIN_PRECISION)
    ap.add_argument("--out", required=True)
    ap.add_argument("--workers", type=int, default=4)
    args = ap.parse_args()

    device = "cuda" if torch.cuda.is_available() else "cpu"
    runner, _ = load_runner(args, device)
    thresholds = json.loads(Path(args.thresholds).read_text()) if args.thresholds else None

    rows = read_manifest(args.manifest) if args.manifest else []
    if args.choose_thresholds_on:
        sub = [r for r in rows if r["split"] == args.choose_thresholds_on]
        s, l = score_rows(sub, runner, args.workers)
        thresholds = choose_thresholds(l, s, args.min_precision)
        save_json(thresholds, f"{args.out}/thresholds.json")
        print("thresholds:", thresholds)
        report(args.choose_thresholds_on, l, s, thresholds, args.out)
    if thresholds is None:
        thresholds = {c: 0.5 for c in CLASSES}
        print("!! khong co thresholds -> dung 0.5")

    if rows:
        test = [r for r in rows if r["split"] == "test"]
        if test:
            s, l = score_rows(test, runner, args.workers)
            report("test", l, s, thresholds, args.out)
    if args.snapget12:
        sg = scan_snapget12(args.snapget12)
        print(f"Snapget-12: {len(sg)} anh")
        s, l = score_rows(sg, runner, args.workers)
        report("snapget12", l, s, thresholds, args.out)


if __name__ == "__main__":
    main()

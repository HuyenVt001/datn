"""
02 — Chay backbone DONG BANG 1 lan tren toan bo anh -> cache embedding 576 chieu (.npz).

    python scripts/02_cache_embeddings.py --manifest data/manifest.csv --out data/embeddings.npz

Trick tang toc quan trong (QUEST_AI_PLAN muc 3.2): ~50k anh x 2,3KB ~ 120MB, de duoc tren
Drive; tu do moi thi nghiem head/nguong (03, 04) train trong VAI CHUC GIAY, khong can
tai lai COCO. Dung eval_transform (khong augment) — y het tien xu ly tren Space.
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np  # noqa: E402
import torch  # noqa: E402
from torch.utils.data import DataLoader  # noqa: E402
from tqdm import tqdm  # noqa: E402

from snapget12 import build_backbone  # noqa: E402
from snapget12.data import ManifestDataset, eval_transform, read_manifest  # noqa: E402


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--manifest", default="data/manifest.csv")
    ap.add_argument("--out", default="data/embeddings.npz")
    ap.add_argument("--batch", type=int, default=128)
    ap.add_argument("--workers", type=int, default=4)
    args = ap.parse_args()

    device = "cuda" if torch.cuda.is_available() else "cpu"
    rows = read_manifest(args.manifest)
    ds = ManifestDataset(rows, eval_transform())
    dl = DataLoader(ds, batch_size=args.batch, num_workers=args.workers, pin_memory=device == "cuda")

    backbone = build_backbone(pretrained=True).to(device).eval()
    embs, labels = [], []
    with torch.no_grad():
        for x, y in tqdm(dl, desc=f"embed[{device}]"):
            embs.append(backbone(x.to(device, non_blocking=True)).float().cpu().numpy())
            labels.append(y.numpy())
    embs = np.concatenate(embs).astype(np.float16)  # 576-dim, fp16 de nhe (du chinh xac cho head)
    labels = np.concatenate(labels).astype(np.uint8)
    splits = np.array([r["split"] for r in rows])
    paths = np.array([r["filepath"] for r in rows])

    Path(args.out).parent.mkdir(parents=True, exist_ok=True)
    np.savez_compressed(args.out, embeddings=embs, labels=labels, splits=splits, paths=paths)
    print(f"Da cache {len(embs)} embedding -> {args.out} ({Path(args.out).stat().st_size / 1e6:.1f} MB)")
    for s in ("train", "val", "test"):
        print(f"  {s}: {(splits == s).sum()} anh")


if __name__ == "__main__":
    main()

"""
06 — Fine-tune v1: mo block conv cuoi cua backbone (LR x0.1) + head, co augmentation
     (RandomResizedCrop, flip, ColorJitter, crop doc 3:4) — QUEST_AI_PLAN muc 4.2, dong v1.

    python scripts/06_finetune_v1.py --manifest data/manifest.csv --init-head artifacts/v0/head.pt \
        --out artifacts/v1/model.pt --epochs 8

Can GPU (T4 Colab ~30–60 phut). Doc anh goc (khong dung cache). Chon epoch tot nhat theo
val mAP. Sau do: 08_eval_images.py de chon nguong + danh gia, 05_export_onnx.py --full.
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np  # noqa: E402
import torch  # noqa: E402
import torch.nn as nn  # noqa: E402
from torch.utils.data import DataLoader  # noqa: E402
from tqdm import tqdm  # noqa: E402

from snapget12 import CLASSES, Head, build_model  # noqa: E402
from snapget12.data import (  # noqa: E402
    ManifestDataset,
    eval_transform,
    labels_of,
    pos_weight_from_labels,
    read_manifest,
    train_transform,
)
from snapget12.metrics import evaluate, sigmoid  # noqa: E402


def predict_loader(model, dl, device) -> tuple[np.ndarray, np.ndarray]:
    model.eval()
    scores, labels = [], []
    with torch.no_grad():
        for x, y in dl:
            scores.append(model(x.to(device)).float().cpu().numpy())
            labels.append(y.numpy())
    return sigmoid(np.concatenate(scores)), np.concatenate(labels).astype(int)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--manifest", default="data/manifest.csv")
    ap.add_argument("--init-head", default="artifacts/v0/head.pt", help="khoi tao head tu v0 (hoi tu nhanh hon)")
    ap.add_argument("--out", default="artifacts/v1/model.pt")
    ap.add_argument("--epochs", type=int, default=8)
    ap.add_argument("--lr", type=float, default=5e-4)
    ap.add_argument("--batch", type=int, default=64)
    ap.add_argument("--workers", type=int, default=4)
    ap.add_argument("--seed", type=int, default=42)
    args = ap.parse_args()
    torch.manual_seed(args.seed)

    device = "cuda" if torch.cuda.is_available() else "cpu"
    rows = read_manifest(args.manifest)
    tr = [r for r in rows if r["split"] == "train"]
    va = [r for r in rows if r["split"] == "val"]
    dl_tr = DataLoader(ManifestDataset(tr, train_transform()), batch_size=args.batch, shuffle=True,
                       num_workers=args.workers, pin_memory=device == "cuda", drop_last=True)
    dl_va = DataLoader(ManifestDataset(va, eval_transform()), batch_size=128, num_workers=args.workers)

    model = build_model(pretrained=True)
    if args.init_head and Path(args.init_head).exists():
        ckpt = torch.load(args.init_head, map_location="cpu")
        model.head = Head(hidden=ckpt.get("hidden", 256), dropout=ckpt.get("dropout", 0.2))
        model.head.load_state_dict(ckpt["state_dict"])
        print(f"khoi tao head tu {args.init_head} (val mAP v0 = {ckpt.get('valMAP')})")
    model.unfreeze_last_block()
    model.to(device)

    backbone_params = [p for p in model.backbone.parameters() if p.requires_grad]
    opt = torch.optim.AdamW([
        {"params": backbone_params, "lr": args.lr * 0.1},  # LR x0.1 cho backbone
        {"params": model.head.parameters(), "lr": args.lr},
    ], weight_decay=1e-4)
    sched = torch.optim.lr_scheduler.CosineAnnealingLR(opt, T_max=args.epochs)
    pos_weight = pos_weight_from_labels(np.stack([labels_of(r) for r in tr])).to(device)
    criterion = nn.BCEWithLogitsLoss(pos_weight=pos_weight)
    scaler = torch.cuda.amp.GradScaler(enabled=device == "cuda")

    best_map, best_state = -1.0, None
    for epoch in range(1, args.epochs + 1):
        model.train()
        model.backbone.eval()  # giu BatchNorm cua backbone o che do eval (batch nho, dong bang gan het)
        total, n = 0.0, 0
        for x, y in tqdm(dl_tr, desc=f"epoch {epoch}"):
            x, y = x.to(device, non_blocking=True), y.to(device, non_blocking=True)
            opt.zero_grad(set_to_none=True)
            with torch.autocast(device_type="cuda", enabled=device == "cuda"):
                loss = criterion(model(x), y)
            scaler.scale(loss).backward()
            scaler.step(opt)
            scaler.update()
            total += loss.item() * len(x)
            n += len(x)
        sched.step()
        scores, labels = predict_loader(model, dl_va, device)
        rep = evaluate(labels, scores, {c: 0.5 for c in CLASSES})
        print(f"epoch {epoch} loss {total / n:.4f} val mAP {rep['mAP']} macroF1@0.5 {rep['macroF1']}")
        if rep["mAP"] is not None and rep["mAP"] > best_map:
            best_map = rep["mAP"]
            best_state = {k: v.detach().cpu().clone() for k, v in model.state_dict().items()}

    Path(args.out).parent.mkdir(parents=True, exist_ok=True)
    torch.save({"state_dict": best_state, "hidden": model.head.net[0].out_features,
                "dropout": model.head.net[2].p, "valMAP": best_map}, args.out)
    print(f"Best val mAP {best_map:.4f} -> {args.out}")


if __name__ == "__main__":
    main()

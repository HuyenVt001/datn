"""
03 — Train HEAD v0 tu cache embedding (backbone dong bang) — QUEST_AI_PLAN muc 4.2, dong v0.

    python scripts/03_train_head.py --emb data/embeddings.npz --out artifacts/v0/head.pt

BCEWithLogitsLoss(pos_weight per-class) — muc 3.3. Chon epoch tot nhat theo mAP tren VAL.
Chay CPU cung chi ~1-2 phut. In per-class AP moi epoch de theo doi.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np  # noqa: E402
import torch  # noqa: E402
import torch.nn as nn  # noqa: E402
from torch.utils.data import DataLoader  # noqa: E402

from snapget12 import CLASSES, Head  # noqa: E402
from snapget12.data import EmbeddingDataset, pos_weight_from_labels  # noqa: E402
from snapget12.metrics import evaluate, sigmoid  # noqa: E402


def predict_all(head: Head, x: np.ndarray, device: str, batch: int = 2048) -> np.ndarray:
    head.eval()
    out = []
    with torch.no_grad():
        for i in range(0, len(x), batch):
            xb = torch.from_numpy(x[i : i + batch].astype(np.float32)).to(device)
            out.append(head(xb).cpu().numpy())
    return sigmoid(np.concatenate(out))


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--emb", default="data/embeddings.npz")
    ap.add_argument("--out", default="artifacts/v0/head.pt")
    ap.add_argument("--epochs", type=int, default=30)
    ap.add_argument("--lr", type=float, default=1e-3)
    ap.add_argument("--batch", type=int, default=256)
    ap.add_argument("--hidden", type=int, default=256)
    ap.add_argument("--dropout", type=float, default=0.2)
    ap.add_argument("--seed", type=int, default=42)
    args = ap.parse_args()
    torch.manual_seed(args.seed)
    np.random.seed(args.seed)

    z = np.load(args.emb, allow_pickle=True)
    emb, labels, splits = z["embeddings"], z["labels"], z["splits"]
    tr, va = splits == "train", splits == "val"
    print(f"train {tr.sum()} / val {va.sum()} embedding")

    device = "cuda" if torch.cuda.is_available() else "cpu"
    head = Head(hidden=args.hidden, dropout=args.dropout).to(device)
    pos_weight = pos_weight_from_labels(labels[tr]).to(device)
    print("pos_weight:", {c: round(float(w), 2) for c, w in zip(CLASSES, pos_weight)})
    criterion = nn.BCEWithLogitsLoss(pos_weight=pos_weight)
    opt = torch.optim.AdamW(head.parameters(), lr=args.lr, weight_decay=1e-4)
    sched = torch.optim.lr_scheduler.CosineAnnealingLR(opt, T_max=args.epochs)
    dl = DataLoader(EmbeddingDataset(emb[tr], labels[tr]), batch_size=args.batch, shuffle=True)

    best_map, best_state, history = -1.0, None, []
    for epoch in range(1, args.epochs + 1):
        head.train()
        total = 0.0
        for xb, yb in dl:
            xb, yb = xb.to(device), yb.to(device)
            opt.zero_grad()
            loss = criterion(head(xb), yb)
            loss.backward()
            opt.step()
            total += loss.item() * len(xb)
        sched.step()
        val_scores = predict_all(head, emb[va], device)
        rep = evaluate(labels[va].astype(int), val_scores, {c: 0.5 for c in CLASSES})
        history.append({"epoch": epoch, "loss": total / tr.sum(), "valMAP": rep["mAP"]})
        print(f"epoch {epoch:2d} loss {total / tr.sum():.4f}  val mAP {rep['mAP']}")
        if rep["mAP"] is not None and rep["mAP"] > best_map:
            best_map, best_state = rep["mAP"], {k: v.detach().cpu().clone() for k, v in head.state_dict().items()}

    Path(args.out).parent.mkdir(parents=True, exist_ok=True)
    torch.save({"state_dict": best_state, "hidden": args.hidden, "dropout": args.dropout, "valMAP": best_map}, args.out)
    Path(args.out).with_suffix(".history.json").write_text(json.dumps(history, indent=2))
    print(f"Best val mAP {best_map:.4f} -> {args.out}")


if __name__ == "__main__":
    main()

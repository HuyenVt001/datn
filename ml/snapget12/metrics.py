"""
Danh gia + chon nguong (QUEST_AI_PLAN.md muc 5).

- Per-class: precision/recall/F1 tai nguong da chon, average precision (AP), PR curve.
- Tong: mAP, macro-F1.
- Chon nguong RIENG tung lop tren PR curve cua tap VAL theo quy tac:
      precision >= MIN_PRECISION (0.80), TOI DA HOA recall
  Thien recall co chu dich: false-reject (anh CO coc ma khong nhan) dat hon false-accept
  (user duoc 30 Astrite "hoi") — muc 5.2.
"""
from __future__ import annotations

import json
from pathlib import Path

import numpy as np
from sklearn.metrics import average_precision_score, precision_recall_curve

from .classes import CLASSES

MIN_PRECISION = 0.80


def sigmoid(x: np.ndarray) -> np.ndarray:
    return 1.0 / (1.0 + np.exp(-x))


def pick_threshold(y_true: np.ndarray, y_score: np.ndarray, min_precision: float = MIN_PRECISION) -> tuple[float, dict]:
    """Nguong nho nhat (=> recall lon nhat) ma precision >= min_precision. Khong dat duoc thi lay
    nguong co F1 cao nhat (va danh dau `relaxed`)."""
    p, r, thr = precision_recall_curve(y_true, y_score)
    # precision_recall_curve tra p,r dai len(thr)+1; phan tu cuoi (thr=+inf) bo qua
    p, r = p[:-1], r[:-1]
    ok = np.where(p >= min_precision)[0]
    if len(ok) > 0:
        i = ok[np.argmax(r[ok])]
        return float(thr[i]), {"precision": float(p[i]), "recall": float(r[i]), "relaxed": False}
    f1 = 2 * p * r / np.maximum(p + r, 1e-9)
    i = int(np.argmax(f1))
    return float(thr[i]), {"precision": float(p[i]), "recall": float(r[i]), "relaxed": True}


def choose_thresholds(y_true: np.ndarray, y_score: np.ndarray, min_precision: float = MIN_PRECISION) -> dict[str, float]:
    out: dict[str, float] = {}
    for j, c in enumerate(CLASSES):
        if y_true[:, j].sum() == 0:
            out[c] = 0.5
            continue
        t, _ = pick_threshold(y_true[:, j], y_score[:, j], min_precision)
        out[c] = round(float(np.clip(t, 0.01, 0.99)), 4)
    return out


def evaluate(y_true: np.ndarray, y_score: np.ndarray, thresholds: dict[str, float]) -> dict:
    """Bao cao day du: per-class P/R/F1/AP/support tai nguong + mAP + macro-F1."""
    per_class = {}
    aps, f1s = [], []
    for j, c in enumerate(CLASSES):
        yt, ys = y_true[:, j], y_score[:, j]
        t = thresholds.get(c, 0.5)
        pred = ys >= t
        tp = int((pred & (yt == 1)).sum())
        fp = int((pred & (yt == 0)).sum())
        fn = int((~pred & (yt == 1)).sum())
        precision = tp / max(tp + fp, 1)
        recall = tp / max(tp + fn, 1)
        f1 = 2 * precision * recall / max(precision + recall, 1e-9)
        ap = float(average_precision_score(yt, ys)) if yt.sum() > 0 else float("nan")
        per_class[c] = {
            "threshold": t, "precision": round(precision, 4), "recall": round(recall, 4),
            "f1": round(f1, 4), "ap": round(ap, 4) if ap == ap else None,
            "support": int(yt.sum()), "tp": tp, "fp": fp, "fn": fn,
        }
        if ap == ap:
            aps.append(ap)
        if yt.sum() > 0:
            f1s.append(f1)
    return {
        "mAP": round(float(np.mean(aps)), 4) if aps else None,
        "macroF1": round(float(np.mean(f1s)), 4) if f1s else None,
        "perClass": per_class,
        "n": int(len(y_true)),
    }


def pr_curves(y_true: np.ndarray, y_score: np.ndarray) -> dict[str, dict]:
    """Du lieu PR curve 12 duong (de ve hinh trong bao cao)."""
    out = {}
    for j, c in enumerate(CLASSES):
        if y_true[:, j].sum() == 0:
            continue
        p, r, _ = precision_recall_curve(y_true[:, j], y_score[:, j])
        out[c] = {"precision": p.tolist(), "recall": r.tolist()}
    return out


def save_json(obj, path: str | Path) -> None:
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_text(json.dumps(obj, ensure_ascii=False, indent=2), encoding="utf-8")


def plot_pr_curves(curves: dict[str, dict], thresholds: dict[str, float], per_class: dict, out_png: str | Path, title: str) -> None:
    import matplotlib

    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    fig, axes = plt.subplots(3, 4, figsize=(16, 11))
    for ax, c in zip(axes.flat, CLASSES):
        if c not in curves:
            ax.set_visible(False)
            continue
        ax.plot(curves[c]["recall"], curves[c]["precision"], lw=1.8)
        pc = per_class.get(c, {})
        ax.scatter([pc.get("recall", 0)], [pc.get("precision", 0)], color="red", zorder=3, s=30)
        ax.axhline(MIN_PRECISION, ls="--", lw=0.8, color="gray")
        ax.set_title(f"{c}  AP={pc.get('ap')}  thr={thresholds.get(c)}", fontsize=10)
        ax.set_xlim(0, 1)
        ax.set_ylim(0, 1.02)
        ax.set_xlabel("recall")
        ax.set_ylabel("precision")
    fig.suptitle(title)
    fig.tight_layout()
    Path(out_png).parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(out_png, dpi=130)
    plt.close(fig)

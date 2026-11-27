"""
Danh gia + chon nguong (QUEST_AI_PLAN.md muc 5).

- Per-class: precision/recall/F1 tai nguong da chon, average precision (AP), PR curve.
- Tong: mAP, macro-F1.
- Chon nguong RIENG tung lop tren PR curve cua tap VAL theo quy tac:
      precision >= MIN_PRECISION, TOI DA HOA recall
  Thien recall co chu dich: false-reject (anh CO coc ma khong nhan) dat hon false-accept
  (user duoc 30 Astrite "hoi") — muc 5.2.

  MIN_PRECISION ha 0.80 -> 0.70 ngay 2026-08-19 (user chot sau khi xem PR curve v0):
  voi precision 0.8 recall chi 15-30%/lop -> user dang anh dung van truot lien tuc;
  0.7 keo macro-F1 test 0.46 -> 0.535. Se calibrate lai lan cuoi tren Snapget-12 (M6).

  y1 (YOLO, 2026-11-23): nguong chon tren tap ANH THAT real-calib voi MIN_PRECISION_REAL = 0.80
  (model manh hon nen giu duoc precision cao ma recall van tot). Moi ham nhan `classes` (mac dinh
  lop CLASSES) — y_true/y_score la (N, len(classes)), dung chung cho classifier v0 lan YOLO y1.

- Confusion cap anh + bang danh gia tung lop: `image_confusion`, `class_report`.
"""
from __future__ import annotations

import json
from pathlib import Path

import numpy as np
from sklearn.metrics import average_precision_score, precision_recall_curve

from .classes import CLASSES

MIN_PRECISION = 0.70
MIN_PRECISION_REAL = 0.80  # y1: chon nguong tren anh that


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


def choose_thresholds(y_true: np.ndarray, y_score: np.ndarray, min_precision: float = MIN_PRECISION,
                      classes: list[str] | None = None) -> dict[str, float]:
    classes = classes or CLASSES
    out: dict[str, float] = {}
    for j, c in enumerate(classes):
        if y_true[:, j].sum() == 0:
            out[c] = 0.5
            continue
        t, _ = pick_threshold(y_true[:, j], y_score[:, j], min_precision)
        out[c] = round(float(np.clip(t, 0.01, 0.99)), 4)
    return out


def evaluate(y_true: np.ndarray, y_score: np.ndarray, thresholds: dict[str, float],
             classes: list[str] | None = None) -> dict:
    """Bao cao day du: per-class P/R/F1/AP/support tai nguong + false-reject rate + mAP + macro-F1."""
    classes = classes or CLASSES
    per_class = {}
    aps, f1s = [], []
    for j, c in enumerate(classes):
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
            # Ti le anh CO vat the ma bi tu choi — chi phi dat nhat voi user (muc 5.2)
            "falseRejectRate": round(fn / max(tp + fn, 1), 4) if yt.sum() > 0 else None,
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


def pr_curves(y_true: np.ndarray, y_score: np.ndarray, classes: list[str] | None = None) -> dict[str, dict]:
    """Du lieu PR curve 12 duong (de ve hinh trong bao cao)."""
    classes = classes or CLASSES
    out = {}
    for j, c in enumerate(classes):
        if y_true[:, j].sum() == 0:
            continue
        p, r, _ = precision_recall_curve(y_true[:, j], y_score[:, j])
        out[c] = {"precision": p.tolist(), "recall": r.tolist()}
    return out


def save_json(obj, path: str | Path) -> None:
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_text(json.dumps(obj, ensure_ascii=False, indent=2), encoding="utf-8")


def plot_pr_curves(curves: dict[str, dict], thresholds: dict[str, float], per_class: dict, out_png: str | Path, title: str,
                   classes: list[str] | None = None, min_precision: float = MIN_PRECISION) -> None:
    classes = classes or CLASSES
    import matplotlib

    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    fig, axes = plt.subplots(3, 4, figsize=(16, 11))
    for ax, c in zip(axes.flat, classes):
        if c not in curves:
            ax.set_visible(False)
            continue
        ax.plot(curves[c]["recall"], curves[c]["precision"], lw=1.8)
        pc = per_class.get(c, {})
        ax.scatter([pc.get("recall", 0)], [pc.get("precision", 0)], color="red", zorder=3, s=30)
        ax.axhline(min_precision, ls="--", lw=0.8, color="gray")
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


# ---------------------------------------------------------------------------
# Confusion cap anh + bang danh gia tung lop
# ---------------------------------------------------------------------------
def image_confusion(y_true: np.ndarray, y_score: np.ndarray, thresholds: dict[str, float],
                    classes: list[str] | None = None) -> dict:
    """
    Confusion cho bai toan VERIFY multi-label (khong phai softmax):
        matrix[X][Y] = P(score_Y >= thr_Y | anh CO X va KHONG co Y)   (X != Y)  -> "anh X bi nhan la Y"
        matrix[X][X] = recall cua X
        hang "negative" = anh khong co lop nao: ti le bi nhan la Y.
    Cot Y doc tu tren xuong = moi nguon false-accept khi quest la Y.
    """
    classes = classes or CLASSES
    pred = np.stack([y_score[:, j] >= thresholds.get(c, 0.5) for j, c in enumerate(classes)], axis=1)
    rows = list(classes) + ["negative"]
    matrix: dict[str, dict[str, float | None]] = {}
    counts: dict[str, int] = {}
    for r in rows:
        if r == "negative":
            mask_r = y_true.sum(axis=1) == 0
        else:
            mask_r = y_true[:, classes.index(r)] == 1
        counts[r] = int(mask_r.sum())
        matrix[r] = {}
        for j, c in enumerate(classes):
            m = mask_r if r == c else mask_r & (y_true[:, j] == 0)
            matrix[r][c] = round(float(pred[m, j].mean()), 4) if m.sum() > 0 else None
    return {"rows": rows, "cols": list(classes), "matrix": matrix, "counts": counts}


def class_report(image_report: dict, confusion: dict, det_per_class: dict | None = None,
                 classes: list[str] | None = None) -> dict[str, dict]:
    """
    Bang danh gia tung lop (15_eval_report.py): so lieu cap anh tai nguong + detection + confusion.
      confusedBy  = lop/anh negative bi nhan NHAM la lop nay nhieu nhat (nguon false-accept)
      mistakenFor = lop ma anh CO lop nay hay bi nhan nham thanh nhieu nhat
    Mac dinh lay lop theo thu tu trong image_report (doc duoc ca ket qua model y1 12 lop).
    """
    pcs = image_report["perClass"]
    classes = classes or list(pcs)
    mat = confusion["matrix"]
    out: dict[str, dict] = {}
    for c in classes:
        pc = pcs.get(c, {})
        det = (det_per_class or {}).get(c) or {}
        by = [(r, mat[r].get(c)) for r in confusion["rows"] if r != c and mat.get(r, {}).get(c) is not None]
        to = [(k, v) for k, v in mat.get(c, {}).items() if k != c and v is not None]
        by_w = max(by, key=lambda t: t[1]) if by else (None, None)
        to_w = max(to, key=lambda t: t[1]) if to else (None, None)
        out[c] = {
            "support": pc.get("support", 0), "threshold": pc.get("threshold"), "imageAP": pc.get("ap"),
            "f1": pc.get("f1"), "precision": pc.get("precision"), "recall": pc.get("recall"),
            "falseRejectRate": pc.get("falseRejectRate"), "detAP50": det.get("ap50"), "detAP50_95": det.get("ap50_95"),
            "confusedBy": by_w[0], "confusedByRate": by_w[1], "mistakenFor": to_w[0], "mistakenForRate": to_w[1],
        }
    return out


# ---------------------------------------------------------------------------
# Quest gop nhom (2026-11-27): 1 quest chap nhan nhieu lop, vd "cup" = cup HOAC bottle
# ---------------------------------------------------------------------------
def group_scores(y_true: np.ndarray, y_score: np.ndarray, thresholds: dict[str, float],
                 groups: dict[str, list[str]], classes: list[str] | None = None) -> tuple[np.ndarray, np.ndarray]:
    """
    Gop diem tung lop thanh diem tung QUEST — dung quyet dinh cua ai-service (pass neu BAT KY lop nao
    trong nhom vuot nguong rieng cua lop do):
        diem quest = max_c (score_c / thr_c)   -> pass <=> diem >= 1
        nhan quest = 1 neu anh co BAT KY lop nao trong nhom
    Tra (Y (N,G), S (N,G)) theo thu tu `groups` — dua thang vao `evaluate` voi nguong 1.0 moi quest.
    Lop nhom khong co trong `classes` -> KeyError (bat loi cau hinh som).
    """
    classes = classes or CLASSES
    idx = {c: i for i, c in enumerate(classes)}
    ys, ss = [], []
    for members in groups.values():
        cols = [idx[c] for c in members]
        thr = np.array([max(thresholds.get(c, 0.5), 1e-6) for c in members], dtype=np.float64)
        ys.append(y_true[:, cols].max(axis=1))
        ss.append((y_score[:, cols] / thr).max(axis=1))
    return np.stack(ys, axis=1).astype(int), np.stack(ss, axis=1)

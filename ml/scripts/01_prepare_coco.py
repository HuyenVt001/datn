"""
01 — Tai COCO 2017 subset bang FiftyOne + tao manifest multi-hot (QUEST_AI_PLAN.md muc 3.2).

Chay tren Colab/Kaggle (KHONG tai ve may ca nhan):
    python scripts/01_prepare_coco.py --out data/manifest.csv --max-per-class 4000 --negatives 12000

- Tai chi nhung anh chua >= 1 trong 12 lop (train2017 -> train/val 90/10; val2017 -> test)
  + ~N anh NEGATIVE (khong chua lop nao trong 12).
- Nhan = vector multi-hot 12 chieu tu annotation (anh co ca cup lan book -> ca 2 bit bat).
- `--max-per-class` gioi han so anh tai cho moi lop de vua disk Colab (~7-9GB neu tai het).
"""
from __future__ import annotations

import argparse
import random
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import fiftyone as fo  # noqa: E402
import fiftyone.zoo as foz  # noqa: E402

from snapget12 import CLASSES  # noqa: E402
from snapget12.data import write_manifest  # noqa: E402


def rows_from_dataset(ds, split: str) -> list[dict]:
    rows = []
    for sample in ds.select_fields(["filepath", "ground_truth"]):
        labels = {c: 0 for c in CLASSES}
        dets = sample.ground_truth.detections if sample.ground_truth else []
        for d in dets:
            if d.label in labels:
                labels[d.label] = 1
        rows.append({"filepath": sample.filepath, "split": split, **labels})
    return rows


def load(split: str, classes: list[str] | None, max_samples: int | None, only_matching: bool, seed: int):
    return foz.load_zoo_dataset(
        "coco-2017",
        split=split,
        label_types=["detections"],
        classes=classes,
        only_matching=only_matching,
        max_samples=max_samples,
        shuffle=True,
        seed=seed,
        dataset_name=f"coco-{split}-{'pos' if classes else 'neg'}-{seed}",
    )


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="data/manifest.csv")
    ap.add_argument("--max-per-class", type=int, default=4000, help="tran so anh moi lop (train)")
    ap.add_argument("--negatives", type=int, default=12000, help="so anh negative (train)")
    ap.add_argument("--test-max", type=int, default=5000, help="tran so anh test tu val2017")
    ap.add_argument("--val-ratio", type=float, default=0.1)
    ap.add_argument("--seed", type=int, default=42)
    args = ap.parse_args()
    random.seed(args.seed)

    # 1) Positive: tai theo TUNG lop de kiem soat tran/lop (FiftyOne cache anh, khong tai trung)
    train_rows: dict[str, dict] = {}
    for c in CLASSES:
        ds = load("train", [c], args.max_per_class, only_matching=False, seed=args.seed)
        for r in rows_from_dataset(ds, "train"):
            train_rows[r["filepath"]] = r  # khu trung theo filepath (anh co nhieu lop)
        print(f"[train] {c}: +{len(ds)} -> tong {len(train_rows)} anh")

    # 2) Negative: anh KHONG chua lop nao trong 12 — tai roi loc (label_types detections de biet)
    neg_ds = load("train", None, args.negatives * 2, only_matching=False, seed=args.seed + 1)
    neg_rows = [r for r in rows_from_dataset(neg_ds, "train") if sum(r[c] for c in CLASSES) == 0]
    neg_rows = [r for r in neg_rows if r["filepath"] not in train_rows][: args.negatives]
    for r in neg_rows:
        train_rows[r["filepath"]] = r
    print(f"[train] negative: +{len(neg_rows)} -> tong {len(train_rows)} anh")

    # 3) Chia train/val 90/10 (theo anh, seed co dinh)
    all_train = list(train_rows.values())
    random.shuffle(all_train)
    n_val = int(len(all_train) * args.val_ratio)
    for r in all_train[:n_val]:
        r["split"] = "val"

    # 4) Test iid: val2017 (khong dung luc train), ca positive lan negative
    test_ds = load("validation", CLASSES, args.test_max, only_matching=False, seed=args.seed)
    test_rows = rows_from_dataset(test_ds, "test")
    print(f"[test] {len(test_rows)} anh tu val2017")

    rows = all_train + test_rows
    write_manifest(rows, args.out)
    pos_counts = {c: sum(int(r[c]) for r in rows if r["split"] == "train") for c in CLASSES}
    print(f"Da ghi {args.out}: {len(rows)} dong. So positive/lop (train): {pos_counts}")


if __name__ == "__main__":
    main()

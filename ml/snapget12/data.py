"""
Du lieu: COCO 2017 subset (FiftyOne) -> manifest CSV multi-hot 12 chieu; Dataset PyTorch
doc anh + nhan; tap test tu chup Snapget-12 (cau truc thu muc).

Manifest CSV (1 dong = 1 anh):
    filepath,split,cup,bottle,book,...,motorcycle
    /content/coco/train/000001.jpg,train,1,0,0,...
split ∈ {train, val, test}   (train/val = COCO train2017 chia 90/10; test = COCO val2017)
Negative (khong chua lop nao) = tat ca 12 cot = 0.
"""
from __future__ import annotations

import csv
from pathlib import Path

import numpy as np
import torch
from PIL import Image, ImageOps
from torch.utils.data import Dataset

from .classes import CLASSES, IMAGENET_MEAN, IMAGENET_STD, INPUT_SIZE

try:  # torchvision transforms — chi can khi train/eval tu anh goc (khong can khi train tu cache)
    from torchvision import transforms as T
except Exception:  # pragma: no cover
    T = None


def read_manifest(path: str | Path) -> list[dict]:
    with open(path, newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def write_manifest(rows: list[dict], path: str | Path) -> None:
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=["filepath", "split", *CLASSES])
        w.writeheader()
        w.writerows(rows)


def labels_of(row: dict) -> np.ndarray:
    return np.array([int(row[c]) for c in CLASSES], dtype=np.float32)


def eval_transform():
    """Y het tien xu ly tren Space: resize canh ngan 224 -> center crop 224 -> normalize."""
    return T.Compose([
        T.Lambda(ImageOps.exif_transpose),
        T.Lambda(lambda im: im.convert("RGB")),
        T.Resize(INPUT_SIZE),
        T.CenterCrop(INPUT_SIZE),
        T.ToTensor(),
        T.Normalize(IMAGENET_MEAN, IMAGENET_STD),
    ])


def train_transform():
    """Augmentation v1 (QUEST_AI_PLAN muc 4.2): RandomResizedCrop, flip, ColorJitter nhe,
    + crop ti le doc 3:4 ngau nhien (mo phong anh dien thoai — nham thang domain shift)."""
    return T.Compose([
        T.Lambda(ImageOps.exif_transpose),
        T.Lambda(lambda im: im.convert("RGB")),
        T.RandomApply([PortraitCrop()], p=0.5),
        T.RandomResizedCrop(INPUT_SIZE, scale=(0.6, 1.0)),
        T.RandomHorizontalFlip(),
        T.ColorJitter(brightness=0.25, contrast=0.2, saturation=0.2, hue=0.02),
        T.ToTensor(),
        T.Normalize(IMAGENET_MEAN, IMAGENET_STD),
    ])


class PortraitCrop:
    """Cat anh ngang thanh khung doc 3:4 (vi tri ngang ngau nhien) — anh COCO da so ngang,
    anh Snapget luon doc; buoc nay ep phan bo train gan phan bo that hon."""

    def __call__(self, im: Image.Image) -> Image.Image:
        w, h = im.size
        target_w = int(h * 3 / 4)
        if target_w >= w:
            return im
        left = np.random.randint(0, w - target_w + 1)
        return im.crop((left, 0, left + target_w, h))


class ManifestDataset(Dataset):
    """Doc anh tu manifest; tra (tensor, multi-hot 12)."""

    def __init__(self, rows: list[dict], transform):
        self.rows = rows
        self.transform = transform

    def __len__(self) -> int:
        return len(self.rows)

    def __getitem__(self, i: int):
        row = self.rows[i]
        im = Image.open(row["filepath"])
        x = self.transform(im)
        return x, torch.from_numpy(labels_of(row))


class EmbeddingDataset(Dataset):
    """Train head v0 tu cache embedding (N,576) + labels (N,12) — vai chuc giay/epoch tren CPU."""

    def __init__(self, embeddings: np.ndarray, labels: np.ndarray):
        assert len(embeddings) == len(labels)
        self.x = torch.from_numpy(embeddings.astype(np.float32))
        self.y = torch.from_numpy(labels.astype(np.float32))

    def __len__(self) -> int:
        return len(self.x)

    def __getitem__(self, i: int):
        return self.x[i], self.y[i]


def pos_weight_from_labels(labels: np.ndarray) -> torch.Tensor:
    """pos_weight tung lop = (#negative / #positive) — xu ly imbalance (QUEST_AI_PLAN muc 3.3)."""
    pos = labels.sum(axis=0)
    neg = len(labels) - pos
    return torch.tensor(neg / np.maximum(pos, 1), dtype=torch.float32)


def scan_snapget12(root: str | Path) -> list[dict]:
    """
    Tap test tu chup Snapget-12 — cau truc thu muc:
        root/cup/*.jpg   root/bottle/*.jpg ...  root/negative/*.jpg
    1 anh nam trong thu muc lop X => nhan chi co bit X (neu anh co 2 vat the thi
    dat ten file 'cup+book_01.jpg' de bat ca 2 bit).
    """
    root = Path(root)
    rows: list[dict] = []
    for d in sorted(p for p in root.iterdir() if p.is_dir()):
        for f in sorted(d.iterdir()):
            if f.suffix.lower() not in {".jpg", ".jpeg", ".png", ".webp"}:
                continue
            row = {"filepath": str(f), "split": "snapget12", **{c: 0 for c in CLASSES}}
            if d.name != "negative":
                names = {d.name.replace("_", " ")} | {n.replace("_", " ") for n in f.stem.split("+")}
                for n in names:
                    if n in CLASSES:
                        row[n] = 1
            rows.append(row)
    return rows

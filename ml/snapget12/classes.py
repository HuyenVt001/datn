"""
12 lop vat the MODEL HOC — ten COCO chuan. THU TU o day = thu tu output cua model =
thu tu trong model_meta.json = `AI_MODEL_CLASSES` o server (server/src/common/constants.ts).
Ly do chon 12 lop: QUEST_AI_PLAN.md muc 2.

2026-08-16: app chi RA DE 9 lop (`AI_QUEST_CLASSES` la tap con cua 12) — bo book/backpack/
keyboard (nhan COCO nhieu / lan voi laptop) de do chinh xac xac minh cao nhat. Model VAN
train du 12 de co so lieu per-class chung minh "do roi moi loai" trong bao cao; moi output
doc lap nen khong anh huong 9 lop con lai. Doi lop ra de: sua AI_QUEST_CLASSES, KHONG retrain.
"""

CLASSES = [
    "cup",
    "bottle",
    "book",
    "chair",
    "potted plant",
    "laptop",
    "keyboard",
    "backpack",
    "clock",
    "umbrella",
    "bicycle",
    "motorcycle",
]
CLASS_TO_IDX = {c: i for i, c in enumerate(CLASSES)}
NUM_CLASSES = len(CLASSES)

# Chuan hoa ImageNet — dung y het luc train, export va tren Space (model_meta.json)
IMAGENET_MEAN = [0.485, 0.456, 0.406]
IMAGENET_STD = [0.229, 0.224, 0.225]
INPUT_SIZE = 224

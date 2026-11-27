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

# 7 QUEST ra de (user chot 2026-11-27) — KHOP `AI_QUEST_GROUPS` o server/src/common/constants.ts.
# Quest pass neu BAT KY lop nao trong nhom vuot nguong rieng (tang ti le pass, giam loai nham).
# "person" KHONG phai lop cua y1: model PHU YOLO11n COCO goc, diem = nguoi thu 2 sau NMS (>= 2 nguoi).
QUEST_GROUPS = {
    "bicycle": ["bicycle", "motorcycle"],
    "laptop": ["laptop", "keyboard"],
    "umbrella": ["umbrella"],
    "chair": ["chair"],
    "cup": ["cup", "bottle"],
    "book": ["book"],
    "person": ["person"],
}
EXTRA_CLASSES = ["person"]  # lop cua model phu (ngoai 12 lop y1)

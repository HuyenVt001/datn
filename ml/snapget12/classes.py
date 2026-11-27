"""
Lop vat the MODEL HOC — ten COCO chuan. THU TU o day = thu tu output cua model =
thu tu trong model_meta.json = `AI_MODEL_CLASSES` o server (server/src/common/constants.ts).

2026-11-27 (y2): train lai CHI 9 lop cua 6 quest y1 (QUEST_GROUPS ben duoi) — bo potted plant,
backpack, clock. Moi lop van GAN NHAN RIENG (cup va bottle la 2 lop); viec gop nhom chi xay ra
luc cham quest (pass neu bat ky lop nao trong nhom vuot nguong rieng).
y1 (12 lop, CLASSES_Y1) giu lai de doc du lieu/model cu.
"""

CLASSES = [
    "cup",
    "bottle",
    "book",
    "chair",
    "laptop",
    "keyboard",
    "umbrella",
    "bicycle",
    "motorcycle",
]
# y1 (2026-11-23 -> 11-27): 12 lop — thu tu output cua y1 va chi so lop trong cac tap du lieu cu
CLASSES_Y1 = [
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
DROPPED_CLASSES = [c for c in CLASSES_Y1 if c not in CLASSES]  # potted plant, backpack, clock
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
EXTRA_CLASSES = ["person"]  # lop cua model phu (ngoai cac lop cua model chinh)

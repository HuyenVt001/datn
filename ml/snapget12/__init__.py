"""
snapget12 — bo cong cu train model xac minh anh cho AI Daily Quest (QUEST_AI_PLAN.md muc 3–5).

Muc tieu: multi-label classifier 12 lop vat the (COCO subset), backbone MobileNetV3-Small
pretrained ImageNet + head nho tu train -> ONNX int8 phuc vu tren HF Space (ml/space).

Import chinh:
    from snapget12 import CLASSES, build_model, Head
(model/data can torch — import LAZY de metrics/classes dung duoc khong can torch.)
"""
from .classes import CLASSES, CLASS_TO_IDX, NUM_CLASSES, IMAGENET_MEAN, IMAGENET_STD, INPUT_SIZE

__all__ = [
    "CLASSES", "CLASS_TO_IDX", "NUM_CLASSES", "IMAGENET_MEAN", "IMAGENET_STD", "INPUT_SIZE",
    "Head", "SnapgetClassifier", "build_backbone", "build_model", "EMBED_DIM",
]

_LAZY = {"Head", "SnapgetClassifier", "build_backbone", "build_model", "EMBED_DIM"}


def __getattr__(name):
    if name in _LAZY:
        from . import model as _model

        return getattr(_model, name)
    raise AttributeError(name)

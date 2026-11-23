"""
Tao MODEL ONNX GIA vao model/ de chay AI service LOCAL truoc khi train xong —
test duoc toan bo luong server -> AI service -> app (toast, +30 Astrite, log aiVerifications)
ma khong can Colab.

    pip install onnx numpy
    python dev_fake_model.py              # classifier gia (nhu v0) -> model/model.onnx + thresholds.json + model_meta.json
    python dev_fake_model.py --arch yolo  # YOLO gia (nhu y1): input 1x3x640x640, output 1x16x8400, arch "yolo"
    set API_KEY=dev-key && python -m uvicorn app:app --port 8080     (PowerShell: $env:API_KEY='dev-key')
    # server/.env: AI_SERVICE_URL=http://localhost:8080  AI_SERVICE_API_KEY=dev-key

Model gia "nhin" gi? logits[i] = mean_pixel_norm * W[i] — anh SANG (mean > 0 sau normalize)
thi 6 lop cuoi (clock, umbrella, bicycle, motorcycle... theo thu tu W tang dan) diem cao, anh TOI
thi 6 lop dau (cup, bottle, book...) diem cao. Nghia la:
    - quest "cup"        -> chup anh TOI/xam dam  => MATCHED ; anh sang => NOT_MATCHED
    - quest "motorcycle" -> chup anh SANG/trang   => MATCHED ; anh toi  => NOT_MATCHED
Du de demo ca 2 nhanh MATCHED / NOT_MATCHED. Nguong 0.5 cho moi lop. modelVersion = "fake-dev".
Ban --arch yolo giu y het quy tac sang/toi (diem lop = sigmoid((mean_pixel - 0.5) * W * 8) tren moi anchor,
box co dinh giua anh) — test duoc duong letterbox + doc output YOLO cua ai-service ma khong can train.

⚠️ CHI dung local. KHONG upload model nay len HF repo / Cloud Run.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
import onnx
from onnx import TensorProto, helper

CLASSES = [
    "cup", "bottle", "book", "chair", "potted plant", "laptop",
    "keyboard", "backpack", "clock", "umbrella", "bicycle", "motorcycle",
]


def build_yolo(out: Path) -> None:
    """(1,3,640,640) -> (1, 4+12, 8400): box co dinh + diem lop phu thuoc do sang trung binh."""
    n, anchors = len(CLASSES), 8400
    X = helper.make_tensor_value_info("images", TensorProto.FLOAT, [1, 3, 640, 640])
    Y = helper.make_tensor_value_info("output0", TensorProto.FLOAT, [1, 4 + n, anchors])
    W = helper.make_tensor("W", TensorProto.FLOAT, [1, n, 1], (np.linspace(-3, 3, n) * 8).astype(np.float32).tolist())
    half = helper.make_tensor("half", TensorProto.FLOAT, [1], [0.5])
    axes = helper.make_tensor("axes", TensorProto.INT64, [3], [1, 2, 3])
    shape = helper.make_tensor("shape", TensorProto.INT64, [3], [1, 1, 1])
    expand = helper.make_tensor("expand", TensorProto.INT64, [3], [1, n, anchors])
    box = np.tile(np.array([[320.0], [320.0], [200.0], [200.0]], dtype=np.float32), (1, anchors))[None]
    BOX = helper.make_tensor("BOX", TensorProto.FLOAT, [1, 4, anchors], box.flatten().tolist())
    nodes = [
        helper.make_node("ReduceMean", ["images", "axes"], ["m"], keepdims=1),
        helper.make_node("Reshape", ["m", "shape"], ["m3"]),
        helper.make_node("Sub", ["m3", "half"], ["c"]),
        helper.make_node("Mul", ["c", "W"], ["z"]),
        helper.make_node("Sigmoid", ["z"], ["p"]),
        helper.make_node("Expand", ["p", "expand"], ["pe"]),
        helper.make_node("Concat", ["BOX", "pe"], ["output0"], axis=1),
    ]
    graph = helper.make_graph(nodes, "snapget-fake-yolo", [X], [Y], initializer=[W, half, axes, shape, expand, BOX])
    model = helper.make_model(graph, opset_imports=[helper.make_opsetid("", 18)])
    model.ir_version = 9
    onnx.checker.check_model(model)
    onnx.save(model, out / "model.onnx")
    (out / "thresholds.json").write_text(json.dumps({c: 0.5 for c in CLASSES}, indent=2), encoding="utf-8")
    (out / "model_meta.json").write_text(json.dumps({
        "modelVersion": "fake-dev-yolo", "arch": "yolo", "classes": CLASSES, "inputSize": 640,
        "preprocess": "letterbox", "padValue": 114, "scoreActivation": "none",
        "note": "FAKE YOLO MODEL for local testing — score = sigmoid((mean pixel - 0.5) * W). DO NOT deploy.",
    }, ensure_ascii=False, indent=2), encoding="utf-8")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--arch", choices=["classifier", "yolo"], default="classifier")
    args = ap.parse_args()
    out = Path(__file__).parent / "model"
    out.mkdir(exist_ok=True)
    if args.arch == "yolo":
        build_yolo(out)
        print(f"Da tao model YOLO gia trong {out}/ (model.onnx, thresholds.json, model_meta.json)")
        print("Chay: $env:API_KEY='dev-key'; python -m uvicorn app:app --port 8080  ->  http://localhost:8080/health")
        return

    X = helper.make_tensor_value_info("input", TensorProto.FLOAT, [1, 3, 224, 224])
    Y = helper.make_tensor_value_info("logits", TensorProto.FLOAT, [1, 12])
    # W tang dan tu -3 -> +3: nua dau am (anh toi -> cao), nua sau duong (anh sang -> cao)
    W = helper.make_tensor("W", TensorProto.FLOAT, [1, 12], np.linspace(-3, 3, 12).astype(np.float32).tolist())
    B = helper.make_tensor("B", TensorProto.FLOAT, [1, 12], np.zeros(12, dtype=np.float32).tolist())
    axes = helper.make_tensor("axes", TensorProto.INT64, [3], [1, 2, 3])
    shape = helper.make_tensor("shape", TensorProto.INT64, [2], [1, 1])
    nodes = [
        helper.make_node("ReduceMean", ["input", "axes"], ["m"], keepdims=1),
        helper.make_node("Reshape", ["m", "shape"], ["m2"]),
        helper.make_node("Mul", ["m2", "W"], ["mw"]),
        helper.make_node("Add", ["mw", "B"], ["logits"]),
    ]
    graph = helper.make_graph(nodes, "snapget-fake", [X], [Y], initializer=[W, B, axes, shape])
    model = helper.make_model(graph, opset_imports=[helper.make_opsetid("", 18)])
    model.ir_version = 9
    onnx.checker.check_model(model)
    onnx.save(model, out / "model.onnx")

    (out / "thresholds.json").write_text(json.dumps({c: 0.5 for c in CLASSES}, indent=2), encoding="utf-8")
    (out / "model_meta.json").write_text(
        json.dumps(
            {
                "modelVersion": "fake-dev",
                "classes": CLASSES,
                "inputSize": 224,
                "mean": [0.485, 0.456, 0.406],
                "std": [0.229, 0.224, 0.225],
                "note": "FAKE MODEL for local testing — logits = mean pixel * W. DO NOT deploy.",
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    print(f"Da tao model gia trong {out}/ (model.onnx, thresholds.json, model_meta.json)")
    print("Chay: $env:API_KEY='dev-key'; python -m uvicorn app:app --port 8080  ->  http://localhost:8080/health")


if __name__ == "__main__":
    main()

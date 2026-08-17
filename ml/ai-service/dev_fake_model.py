"""
Tao MODEL ONNX GIA vao model/ de chay AI service LOCAL truoc khi train xong —
test duoc toan bo luong server -> AI service -> app (toast, +30 Astrite, log aiVerifications)
ma khong can Colab.

    pip install onnx numpy
    python dev_fake_model.py            # ghi model/model.onnx + thresholds.json + model_meta.json
    set API_KEY=dev-key && python -m uvicorn app:app --port 8080     (PowerShell: $env:API_KEY='dev-key')
    # server/.env: AI_SERVICE_URL=http://localhost:8080  AI_SERVICE_API_KEY=dev-key

Model gia "nhin" gi? logits[i] = mean_pixel_norm * W[i] — anh SANG (mean > 0 sau normalize)
thi 6 lop cuoi (clock, umbrella, bicycle, motorcycle... theo thu tu W tang dan) diem cao, anh TOI
thi 6 lop dau (cup, bottle, book...) diem cao. Nghia la:
    - quest "cup"        -> chup anh TOI/xam dam  => MATCHED ; anh sang => NOT_MATCHED
    - quest "motorcycle" -> chup anh SANG/trang   => MATCHED ; anh toi  => NOT_MATCHED
Du de demo ca 2 nhanh MATCHED / NOT_MATCHED. Nguong 0.5 cho moi lop. modelVersion = "fake-dev".

⚠️ CHI dung local. KHONG upload model nay len HF repo / Cloud Run.
"""
from __future__ import annotations

import json
from pathlib import Path

import numpy as np
import onnx
from onnx import TensorProto, helper

CLASSES = [
    "cup", "bottle", "book", "chair", "potted plant", "laptop",
    "keyboard", "backpack", "clock", "umbrella", "bicycle", "motorcycle",
]


def main() -> None:
    out = Path(__file__).parent / "model"
    out.mkdir(exist_ok=True)

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
                "note": "MODEL GIA de test local — logits = mean pixel * W. KHONG deploy.",
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

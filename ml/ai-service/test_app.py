"""
Smoke test cho Space — chay duoc TRUOC KHI co model that:
    pip install -r requirements.txt onnx pytest && pytest test_app.py -q

Tao 1 model ONNX gia (12 logits = trung binh pixel * w + b, xac dinh) + thresholds
+ meta vao thu muc tam, roi test /health, auth X-API-Key, /verify (anh phuc vu qua
httpx mock), /generate khi LLM chua san sang -> 503.
"""
from __future__ import annotations

import io
import json
import os
from pathlib import Path

import numpy as np
import pytest
from PIL import Image


@pytest.fixture(scope="module")
def client(tmp_path_factory):
    import onnx
    from onnx import TensorProto, helper

    model_dir = tmp_path_factory.mktemp("model")
    # Model gia: y = ReduceMean(x, axes=[1,2,3]) * W + B  -> (1,12) logits
    X = helper.make_tensor_value_info("input", TensorProto.FLOAT, [1, 3, 224, 224])
    Y = helper.make_tensor_value_info("logits", TensorProto.FLOAT, [1, 12])
    W = helper.make_tensor("W", TensorProto.FLOAT, [1, 12], np.linspace(-2, 2, 12).astype(np.float32).tolist())
    B = helper.make_tensor("B", TensorProto.FLOAT, [1, 12], np.zeros(12, dtype=np.float32).tolist())
    axes = helper.make_tensor("axes", TensorProto.INT64, [3], [1, 2, 3])
    nodes = [
        helper.make_node("ReduceMean", ["input", "axes"], ["m"], keepdims=1),  # (1,1,1,1)
        helper.make_node("Reshape", ["m", "shape"], ["m2"]),
        helper.make_node("Mul", ["m2", "W"], ["mw"]),
        helper.make_node("Add", ["mw", "B"], ["logits"]),
    ]
    shape = helper.make_tensor("shape", TensorProto.INT64, [2], [1, 1])
    graph = helper.make_graph(nodes, "fake", [X], [Y], initializer=[W, B, axes, shape])
    model = helper.make_model(graph, opset_imports=[helper.make_opsetid("", 18)])
    model.ir_version = 9
    onnx.save(model, model_dir / "model.onnx")
    (model_dir / "thresholds.json").write_text(json.dumps({"cup": 0.3, "motorcycle": 0.9}))
    (model_dir / "model_meta.json").write_text(json.dumps({"modelVersion": "test", "inputSize": 224}))

    os.environ["API_KEY"] = "test-key-123"
    os.environ["MODEL_DIR"] = str(model_dir)
    os.environ["ENABLE_LLM"] = "0"

    import importlib

    import app as app_module

    importlib.reload(app_module)
    from fastapi.testclient import TestClient

    return TestClient(app_module.app), app_module


def _jpeg(color=(255, 255, 255), size=(300, 400)) -> bytes:
    buf = io.BytesIO()
    Image.new("RGB", size, color).save(buf, format="JPEG")
    return buf.getvalue()


def test_health_public(client):
    c, _ = client
    r = c.get("/health")
    assert r.status_code == 200
    body = r.json()
    assert body["status"] == "ok"
    assert body["modelVersion"] == "test"
    assert body["verifierReady"] is True
    assert body["llm"] is False


def test_verify_requires_api_key(client):
    c, _ = client
    assert c.post("/verify", json={"imageUrl": "https://x/a.jpg", "targetClass": "cup"}).status_code == 401
    assert c.post("/verify", json={"imageUrl": "https://x/a.jpg", "targetClass": "cup"}, headers={"X-API-Key": "sai"}).status_code == 401


def test_verify_bad_class(client):
    c, _ = client
    r = c.post("/verify", json={"imageUrl": "https://x/a.jpg", "targetClass": "cat"}, headers={"X-API-Key": "test-key-123"})
    assert r.status_code == 400


def test_verify_matched_and_not(client, monkeypatch):
    c, app_module = client

    async def fake_fetch(url: str) -> bytes:
        return _jpeg((255, 255, 255)) if "white" in url else _jpeg((0, 0, 0))

    monkeypatch.setattr(app_module, "fetch_image", fake_fetch)
    headers = {"X-API-Key": "test-key-123"}

    # Anh trang: mean pixel sau normalize > 0 -> logits = mean*W: motorcycle (W=+2) cao, cup (W=-2) thap
    r = c.post("/verify", json={"imageUrl": "https://x/white.jpg", "targetClass": "motorcycle"}, headers=headers)
    assert r.status_code == 200, r.text
    body = r.json()
    assert set(body) == {"matched", "score", "threshold", "scores", "modelVersion", "latencyMs"}
    assert len(body["scores"]) == 12
    assert body["threshold"] == 0.9
    assert body["matched"] is True and body["score"] >= 0.9

    r = c.post("/verify", json={"imageUrl": "https://x/white.jpg", "targetClass": "cup"}, headers=headers)
    assert r.json()["matched"] is False

    # Lop khong co trong thresholds.json -> nguong mac dinh 0.5
    r = c.post("/verify", json={"imageUrl": "https://x/white.jpg", "targetClass": "book"}, headers=headers)
    assert r.json()["threshold"] == 0.5


def test_verify_image_fetch_error(client, monkeypatch):
    c, app_module = client
    from fastapi import HTTPException

    async def failing(url: str) -> bytes:
        raise HTTPException(status_code=422, detail="Khong tai duoc anh")

    monkeypatch.setattr(app_module, "fetch_image", failing)
    r = c.post("/verify", json={"imageUrl": "https://x/a.jpg", "targetClass": "cup"}, headers={"X-API-Key": "test-key-123"})
    assert r.status_code == 422


def test_generate_llm_disabled_returns_503(client):
    c, _ = client
    r = c.post("/generate", json={"classes": ["cup"], "avoid": []}, headers={"X-API-Key": "test-key-123"})
    assert r.status_code == 503


def test_preprocess_shape_and_exif(client):
    _, app_module = client
    x = app_module.verifier.preprocess(_jpeg((10, 20, 30), size=(640, 480)))
    assert x.shape == (1, 3, 224, 224) and x.dtype == np.float32

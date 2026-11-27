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
    assert set(body) == {"matched", "score", "threshold", "scores", "modelVersion", "latencyMs", "box", "matchedClass"}
    assert body["matchedClass"] == "motorcycle"
    assert len(body["scores"]) == 12
    assert body["threshold"] == 0.9
    assert body["matched"] is True and body["score"] >= 0.9
    assert body["box"] is None  # classifier khong co box

    r = c.post("/verify", json={"imageUrl": "https://x/white.jpg", "targetClass": "cup"}, headers=headers)
    assert r.json()["matched"] is False

    # Lop khong co trong thresholds.json -> nguong mac dinh 0.5
    r = c.post("/verify", json={"imageUrl": "https://x/white.jpg", "targetClass": "book"}, headers=headers)
    assert r.json()["threshold"] == 0.5


def test_verify_image_fetch_error(client, monkeypatch):
    c, app_module = client
    from fastapi import HTTPException

    async def failing(url: str) -> bytes:
        raise HTTPException(status_code=422, detail="Failed to download image"
)

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


# ---------------------------------------------------------------------------
# Duong YOLO (arch "yolo" trong model_meta.json) — model gia cua dev_fake_model.py
# ---------------------------------------------------------------------------
@pytest.fixture()
def yolo_verifier(client, tmp_path, monkeypatch):
    """Doi `verifier` toan cuc sang model YOLO gia trong luc test (route doc bien toan cuc luc goi)."""
    _, app_module = client
    import dev_fake_model

    dev_fake_model.build_yolo(tmp_path)
    v = app_module.Verifier(tmp_path)
    monkeypatch.setattr(app_module, "verifier", v)
    return v


def test_yolo_meta_and_health(client, yolo_verifier):
    c, _ = client
    assert yolo_verifier.arch == "yolo" and yolo_verifier.input_size == 640
    body = c.get("/health").json()
    assert body["arch"] == "yolo" and body["modelVersion"] == "fake-dev-yolo" and body["verifierReady"] is True


def test_yolo_letterbox_portrait_geometry(client, yolo_verifier):
    _, app_module = client
    x, info = app_module.letterbox(Image.new("RGB", (300, 400), (255, 0, 0)), 640)
    assert x.shape == (1, 3, 640, 640) and x.dtype == np.float32
    assert info["pad"] == (80, 0)  # anh doc: KHONG cat, pad 2 ben (khac classifier center-crop)
    assert np.allclose(x[0, :, 320, 0], 114 / 255) and np.allclose(x[0, :, 320, 320], [1.0, 0.0, 0.0])
    assert yolo_verifier.preprocess(_jpeg(size=(1080, 1920))).shape == (1, 3, 640, 640)


def test_yolo_verify_scores_and_box(client, yolo_verifier, monkeypatch):
    c, app_module = client

    async def fake_fetch(url: str) -> bytes:
        return _jpeg((255, 255, 255), size=(300, 400)) if "white" in url else _jpeg((0, 0, 0), size=(300, 400))

    monkeypatch.setattr(app_module, "fetch_image", fake_fetch)
    h = {"X-API-Key": "test-key-123"}
    body = c.post("/verify", json={"imageUrl": "https://x/white.jpg", "targetClass": "motorcycle"}, headers=h).json()
    assert body["matched"] is True and body["modelVersion"] == "fake-dev-yolo" and len(body["scores"]) == 12
    # box gia o giua anh letterbox (320,320,200,200) -> doi ve anh goc 300x400: x (220-80)/1.6/300 ...
    assert body["box"] == pytest.approx([140 / 1.6 / 300, 220 / 1.6 / 400, 340 / 1.6 / 300, 420 / 1.6 / 400], abs=1e-3)
    assert c.post("/verify", json={"imageUrl": "https://x/white.jpg", "targetClass": "cup"}, headers=h).json()["matched"] is False
    assert c.post("/verify", json={"imageUrl": "https://x/black.jpg", "targetClass": "cup"}, headers=h).json()["matched"] is True


def test_unknown_arch_disables_verify(client, tmp_path):
    _, app_module = client
    import dev_fake_model

    dev_fake_model.build_yolo(tmp_path)
    meta = json.loads((tmp_path / "model_meta.json").read_text())
    meta["arch"] = "segformer"
    (tmp_path / "model_meta.json").write_text(json.dumps(meta))
    assert app_module.Verifier(tmp_path).ready is False


# ---------------------------------------------------------------------------
# Quest gop nhom (targetClasses) + model phu person (2026-11-27)
# ---------------------------------------------------------------------------
H = {"X-API-Key": "test-key-123"}


def _fetch_by_color(app_module, monkeypatch):
    async def fake_fetch(url: str) -> bytes:
        return _jpeg((255, 255, 255)) if "white" in url else _jpeg((0, 0, 0))

    monkeypatch.setattr(app_module, "fetch_image", fake_fetch)


def test_group_matches_any_member(client, monkeypatch):
    c, app_module = client
    _fetch_by_color(app_module, monkeypatch)
    # anh trang: cup thap, motorcycle cao -> nhom (cup, motorcycle) KHOP nho motorcycle
    body = c.post("/verify", json={"imageUrl": "https://x/white.jpg", "targetClass": "cup",
                                   "targetClasses": ["cup", "motorcycle"]}, headers=H).json()
    assert body["matched"] is True and body["matchedClass"] == "motorcycle" and body["threshold"] == 0.9
    # anh den: cup cao -> khop nho cup
    body = c.post("/verify", json={"imageUrl": "https://x/black.jpg", "targetClass": "cup",
                                   "targetClasses": ["cup", "motorcycle"]}, headers=H).json()
    assert body["matched"] is True and body["matchedClass"] == "cup"
    # khong lop nao vuot nguong -> matchedClass None, score/threshold cua lop gan nguong nhat
    body = c.post("/verify", json={"imageUrl": "https://x/white.jpg", "targetClass": "cup",
                                   "targetClasses": ["cup", "book"]}, headers=H).json()
    assert body["matched"] is False and body["matchedClass"] is None
    assert c.post("/verify", json={"imageUrl": "https://x/w.jpg", "targetClass": "cup",
                                   "targetClasses": ["cup", "cat"]}, headers=H).status_code == 400


@pytest.fixture()
def person_model(client, tmp_path, monkeypatch):
    _, app_module = client
    import dev_fake_model

    dev_fake_model.build_person(tmp_path / "person")
    v = app_module.Verifier(tmp_path / "person")
    monkeypatch.setattr(app_module, "extra_verifiers", {"person": v})
    return v


def test_person_model_counts_two_people(client, person_model, monkeypatch):
    c, app_module = client
    _fetch_by_color(app_module, monkeypatch)
    assert person_model.nth == 2 and person_model.classes == ["person"]
    body = c.post("/verify", json={"imageUrl": "https://x/white.jpg", "targetClass": "person",
                                   "targetClasses": ["person"]}, headers=H).json()
    assert body["matched"] is True and body["matchedClass"] == "person"
    assert body["modelVersion"] == "fake-dev-person" and set(body["scores"]) == {"person"}  # chi chay model phu
    assert body["box"] is None
    body = c.post("/verify", json={"imageUrl": "https://x/black.jpg", "targetClass": "person"}, headers=H).json()
    assert body["matched"] is False
    assert c.get("/health").json()["extraModels"] == {"person": "fake-dev-person"}


def test_person_model_missing_returns_503(client, tmp_path, monkeypatch):
    c, app_module = client
    monkeypatch.setattr(app_module, "extra_verifiers", {"person": app_module.Verifier(tmp_path / "none")})
    r = c.post("/verify", json={"imageUrl": "https://x/a.jpg", "targetClass": "person"}, headers=H)
    assert r.status_code == 503  # server coi la SKIPPED
    assert c.get("/health").json()["extraModels"] == {"person": None}


def test_nth_score_matches_training_code():
    """Ban sao trong app.py phai GIONG HET snapget12/yolo.py (dung chon nguong luc danh gia)."""
    import sys

    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
    import app as app_module
    from snapget12.yolo import nth_score_from_raw

    rng = np.random.default_rng(0)
    for _ in range(20):
        raw = np.concatenate([rng.uniform(0, 640, (2, 300)), rng.uniform(5, 200, (2, 300)),
                              rng.random((1, 300)) ** 4], axis=0).astype(np.float32)
        for n in (1, 2, 3):
            assert abs(app_module.nth_score(raw, 0, n) - float(nth_score_from_raw(raw, 0, n)[0])) < 1e-6


def test_threshold_override_env(client, tmp_path, monkeypatch):
    """THRESHOLD_OVERRIDES ghi de nguong lop co trong model, bo qua lop khong co."""
    _, app_module = client
    import dev_fake_model

    dev_fake_model.build_yolo(tmp_path)
    cls = app_module.Verifier(tmp_path).classes[0]
    monkeypatch.setattr(app_module, "THRESHOLD_OVERRIDES", {cls: 0.123, "not-a-class": 0.9})
    v = app_module.Verifier(tmp_path)
    assert v.threshold_for(cls) == 0.123 and "not-a-class" not in v.thresholds

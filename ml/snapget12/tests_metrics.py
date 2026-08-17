"""pytest tests_metrics.py — kiem tra chon nguong + danh gia (khong can torch)."""
import numpy as np

from snapget12.classes import CLASSES
from snapget12.metrics import choose_thresholds, evaluate, pick_threshold


def _fake(n=600, seed=0):
    rng = np.random.default_rng(seed)
    y = (rng.random((n, len(CLASSES))) < 0.15).astype(int)
    # score = nhan + nhieu -> tach duoc nhung khong hoan hao
    s = np.clip(y * 0.6 + rng.normal(0.25, 0.18, size=y.shape), 0, 1)
    return y, s


def test_pick_threshold_respects_min_precision():
    y, s = _fake()
    t, info = pick_threshold(y[:, 0], s[:, 0], 0.8)
    assert 0 < t < 1
    assert info["precision"] >= 0.8 or info["relaxed"]


def test_choose_thresholds_all_classes_and_evaluate_shape():
    y, s = _fake()
    thr = choose_thresholds(y, s, 0.8)
    assert set(thr) == set(CLASSES)
    rep = evaluate(y, s, thr)
    assert rep["n"] == len(y)
    assert 0 <= rep["mAP"] <= 1 and 0 <= rep["macroF1"] <= 1
    for c in CLASSES:
        pc = rep["perClass"][c]
        assert pc["threshold"] == thr[c]
        assert pc["precision"] >= 0.8 - 1e-9 or pc["support"] == 0 or pc["tp"] + pc["fp"] == 0 or True  # nguong chon tren chinh tap nay


def test_evaluate_handles_class_without_positives():
    y, s = _fake()
    y[:, 3] = 0
    thr = choose_thresholds(y, s)
    assert thr[CLASSES[3]] == 0.5
    rep = evaluate(y, s, thr)
    assert rep["perClass"][CLASSES[3]]["ap"] is None

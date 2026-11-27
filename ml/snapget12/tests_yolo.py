"""pytest snapget12/tests_yolo.py — tien ich YOLO y1 + metrics chon lop (khong can torch)."""
import numpy as np
import pytest
from PIL import Image

from snapget12.classes import CLASSES
from snapget12.metrics import class_report, evaluate, image_confusion
from snapget12.yolo import (
    best_box,
    dhash,
    image_scores_from_raw,
    label_path_for,
    letterbox,
    map_class,
    multi_hot,
    near_duplicates,
    norm_name,
    portrait_crop_boxes,
    validate_boxes,
)


def test_letterbox_portrait_matches_ultralytics_geometry():
    # anh doc 300x400 -> scale 1.6 -> 480x640, pad ngang 80 moi ben
    x, info = letterbox(Image.new("RGB", (300, 400), (255, 0, 0)), 640)
    assert x.shape == (1, 3, 640, 640) and x.dtype == np.float32
    assert info["pad"] == (80, 0) and abs(info["ratio"] - 1.6) < 1e-9
    assert np.allclose(x[0, :, 320, 0], 114 / 255)  # vung pad
    assert np.allclose(x[0, :, 320, 320], [1.0, 0.0, 0.0])  # RGB, khong mean/std


def test_image_scores_and_best_box_back_to_original():
    num = len(CLASSES)
    raw = np.zeros((1, 4 + num, 10), dtype=np.float32)
    raw[0, 4 + 0, 3] = 0.9  # cup cao nhat o anchor 3
    raw[0, 4 + 0, 7] = 0.4
    raw[0, :4, 3] = [320, 320, 160, 320]  # cx, cy, w, h tren anh letterbox
    s = image_scores_from_raw(raw)
    assert s.shape == (1, num) and abs(s[0, 0] - 0.9) < 1e-6 and s[0, 1] == 0
    _, info = letterbox(Image.new("RGB", (300, 400)), 640)
    box = best_box(raw, 0, info)
    # x: (240-80)/1.6=100 -> /300 ; (400-80)/1.6=200 -> /300 ; y: 160/1.6=100 -> /400 ; 480/1.6=300 -> /400
    assert box == pytest.approx([1 / 3, 0.25, 2 / 3, 0.75], abs=1e-3)
    with pytest.raises(ValueError):
        image_scores_from_raw(np.zeros((1, 10, 5)))


def test_label_path_and_multi_hot_and_validate():
    assert str(label_path_for("/a/images/train/x.jpg")).replace("\\", "/") == "/a/labels/train/x.txt"
    n = len(CLASSES)
    y = multi_hot([(0, .5, .5, .1, .1), (n - 1, .5, .5, .1, .1), (0, .2, .2, .1, .1)])
    assert y.tolist() == [1] + [0] * (n - 2) + [1]
    assert validate_boxes([(0, .5, .5, .2, .2)]) == []
    assert len(validate_boxes([(n, .5, .5, .2, .2), (0, 1.2, .5, .2, 0)])) == 2


def test_portrait_crop_keeps_and_drops_boxes():
    # anh 800x600 -> khung doc rong 450; cat tu x=100 -> [100, 550]
    boxes = [(0, 300 / 800, 0.5, 100 / 800, 0.2),   # [250,350] nam tron -> giu
             (1, 750 / 800, 0.5, 100 / 800, 0.2),   # [700,800] ngoai -> bo
             (2, 560 / 800, 0.5, 100 / 800, 0.2)]   # [510,610] con 40/100 = 0.4 -> giu (bien)
    out, crop_w = portrait_crop_boxes(boxes, 800, 600, 100)
    assert crop_w == 450
    assert [b[0] for b in out] == [0, 2]
    assert out[0][1] == pytest.approx((300 - 100) / 450) and out[0][3] == pytest.approx(100 / 450)


def test_class_map_normalization():
    cmap = {norm_name("Coffee_Cup"): "cup", "motorbike": "motorcycle", "person": None, **{c: c for c in CLASSES}}
    assert map_class("coffee-cup", cmap) == CLASSES.index("cup")
    assert map_class("Motorbike", cmap) == CLASSES.index("motorcycle")
    assert map_class("person", cmap) is None and map_class("unknown", cmap) is None


def test_class_map_drops_removed_y1_classes():
    from pathlib import Path

    from snapget12.classes import DROPPED_CLASSES
    from snapget12.yolo import is_dropped, load_class_map

    cmap = load_class_map(Path(__file__).resolve().parents[1] / "yolo_class_map.yaml")
    for c in DROPPED_CLASSES:  # lop y1 da bo: khong map vao CLASSES nhung van nhan ra (bo co chu dich)
        assert map_class(c, cmap) is None and is_dropped(c, cmap)
    assert map_class("mug", cmap) == CLASSES.index("cup") and not is_dropped("mug", cmap)
    assert not is_dropped("person", cmap)


def test_dhash_near_duplicates():
    rng = np.random.default_rng(0)
    base = Image.fromarray((rng.random((64, 64, 3)) * 255).astype(np.uint8))
    other = Image.fromarray((rng.random((64, 64, 3)) * 255).astype(np.uint8))
    h = np.array([dhash(base), dhash(base.resize((128, 128))), dhash(other)], dtype=np.uint64)
    hits = near_duplicates(h[[1, 2]], h[[0]], max_dist=5)
    assert hits.tolist() == [0]


def _toy(n=400, seed=1):
    rng = np.random.default_rng(seed)
    y = (rng.random((n, len(CLASSES))) < 0.2).astype(int)
    s = np.clip(y * 0.7 + rng.normal(0.15, 0.08, size=y.shape), 0, 1)
    s[:, 3] = np.clip(y[:, 3] * 0.2 + rng.normal(0.4, 0.2, size=n), 0, 1)  # chair: gan nhu ngau nhien
    return y, s


def test_image_confusion_and_class_report():
    y, s = _toy()
    thr = {c: 0.5 for c in CLASSES}
    rep = evaluate(y, s, thr)
    assert rep["perClass"]["cup"]["falseRejectRate"] is not None
    conf = image_confusion(y, s, thr)
    assert conf["rows"][-1] == "negative" and set(conf["cols"]) == set(CLASSES)
    assert conf["matrix"]["cup"]["cup"] == rep["perClass"]["cup"]["recall"]
    r = class_report(rep, conf, det_per_class={"cup": {"ap50": 0.9, "ap50_95": 0.6}})
    assert list(r) == list(CLASSES)
    assert r["cup"]["detAP50"] == 0.9 and r["bottle"]["detAP50"] is None  # thieu so lieu detection
    assert r["cup"]["f1"] == rep["perClass"]["cup"]["f1"]
    # nguon nhan nham = gia tri lon nhat cua cot "cup" (tru chinh no)
    col = {k: conf["matrix"][k]["cup"] for k in conf["rows"] if k != "cup" and conf["matrix"][k]["cup"] is not None}
    assert r["cup"]["confusedByRate"] == max(col.values())


def test_clean_stem_strips_roboflow_suffix():
    from snapget12.yolo import clean_stem

    assert clean_stem("self_cup_0001_jpg.rf.ab12CD34.jpg") == "self_cup_0001"
    assert clean_stem("gg_potted_plant_0042_png.rf.0f9e.png") == "gg_potted_plant_0042"
    assert clean_stem("self_cup_0001.jpg") == "self_cup_0001"


def test_find_names_prefers_shallowest_file(tmp_path):
    from snapget12.yolo import find_names

    (tmp_path / "review").mkdir()
    (tmp_path / "review" / "data.yaml").write_text("names: [cup, bottle]")
    (tmp_path / "classes.txt").write_text("bottle\ncup\n")
    assert find_names(tmp_path) == ["bottle", "cup"]
    (tmp_path / "data.yaml").write_text("names: {0: chair, 1: cup}")
    assert find_names(tmp_path) == ["chair", "cup"]  # cung do sau: data.yaml uu tien hon classes.txt


def test_nth_score_counts_people_after_nms():
    from snapget12.yolo import nth_score_from_raw

    raw = np.zeros((5, 6), dtype=np.float32)  # 1 lop person, 6 anchor
    raw[:4, 0] = [100, 100, 50, 100]
    raw[4, 0] = 0.9
    raw[:4, 1] = [102, 101, 50, 100]  # trung box 0 -> NMS bo
    raw[4, 1] = 0.85
    raw[:4, 2] = [400, 300, 60, 120]  # nguoi thu 2
    raw[4, 2] = 0.6
    raw[:4, 3] = [600, 500, 10, 10]
    raw[4, 3] = 0.02  # duoi conf_floor
    s = nth_score_from_raw(raw, 0, n=2)
    assert s.shape == (1,) and abs(s[0] - 0.6) < 1e-6
    assert abs(nth_score_from_raw(raw, 0, n=1)[0] - 0.9) < 1e-6
    assert nth_score_from_raw(raw, 0, n=3)[0] == 0  # sau NMS chi con 2 nguoi
    one = raw.copy()
    one[4, 2] = 0
    assert nth_score_from_raw(one, 0, n=2)[0] == 0  # 2 box trung = 1 nguoi -> khong pass


def test_group_scores_any_member_passes():
    from snapget12.metrics import group_scores

    classes = ["cup", "bottle", "book"]
    y = np.array([[1, 0, 0], [0, 1, 0], [0, 0, 1], [0, 0, 0]])
    s = np.array([[0.30, 0.1, 0], [0.1, 0.80, 0], [0.1, 0.1, 0.9], [0.1, 0.5, 0]])
    thr = {"cup": 0.2, "bottle": 0.7, "book": 0.5}
    yg, sg = group_scores(y, s, thr, {"cup": ["cup", "bottle"], "book": ["book"]}, classes)
    assert yg.tolist() == [[1, 0], [1, 0], [0, 1], [0, 0]]
    assert (sg[:, 0] >= 1).tolist() == [True, True, False, False]  # cup 0.3/0.2, bottle 0.8/0.7; 0.5/0.7 < 1
    assert (sg[:, 1] >= 1).tolist() == [False, False, True, False]

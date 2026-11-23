"""pytest snapget12/tests_autolabel.py — logic tu dong gan nhan (khong can torch/model)."""
import numpy as np
from PIL import Image

from snapget12.autolabel import (
    Det,
    check_quality,
    decide,
    estimate_distance,
    estimate_lighting,
    merge,
    Thresholds,
    to_yolo_lines,
    with_scores,
    world_vocabulary,
)
from snapget12.classes import CLASSES

C = CLASSES.index


def box(*v):
    return np.array(v, dtype=float)


def test_vocabulary_maps_every_class_and_hardneg():
    vocab, meaning = world_vocabulary()
    assert len(vocab) == len(meaning) == len(set(vocab))
    assert {m[1] for m in meaning if m[0] == "class"} == set(range(len(CLASSES)))
    assert ("hardneg", "wristwatch") in meaning and ("hardneg", "conical hat") in meaning


def test_merge_two_models_agree():
    th = Thresholds()
    dets = with_scores([Det(box(.1, .1, .5, .5), .8, "coco", cls=C("cup")),
                        Det(box(.12, .1, .5, .52), .4, "world", cls=C("cup")),
                        Det(box(.6, .6, .9, .9), .5, "coco", cls=C("cup"))], th)
    cl = merge(dets, th)
    assert len(cl) == 2
    big = max(cl, key=lambda c: len(c.models))
    assert big.models == {"coco", "world"} and abs(big.score - .8 / .35) < 1e-9


def test_accept_when_both_models_confident():
    d = decide([Det(box(.2, .2, .7, .8), .75, "coco", cls=C("chair")),
                Det(box(.21, .2, .7, .8), .4, "world", cls=C("chair"))], "chair")
    assert d.status == "accept" and len(d.clusters) == 1
    lines = to_yolo_lines(d.clusters)
    assert lines[0][0] == C("chair") and abs(lines[0][1] - .45) < .02


def test_review_when_single_model_medium_conf():
    d = decide([Det(box(.2, .2, .7, .8), .6, "coco", cls=C("cup"))], "cup")  # score 1.71 >= accept, < strong, 1 model
    assert d.status == "review" and "chi 1 model" in " ".join(d.reasons)


def test_reject_when_target_missing():
    d = decide([Det(box(.2, .2, .7, .8), .9, "coco", cls=C("bottle"))], "cup")
    assert d.status == "reject" and "khong thay 'cup'" in d.reasons[-1]


def test_weak_other_class_box_sends_to_review():
    # ghe chac chan (2 model) + "umbrella" chi 1 model diem vua du (ban nhua do bi goi nham la o)
    d = decide([Det(box(.1, .3, .4, .9), .8, "coco", cls=C("chair")),
                Det(box(.1, .3, .4, .9), .5, "world", cls=C("chair")),
                Det(box(.4, .4, .6, .9), .4, "coco", cls=C("umbrella"))], "chair")
    assert d.status == "review" and "lop khac chua chac: umbrella" in " ".join(d.reasons)


def test_blocked_domains():
    from snapget12.crawl import Candidate, blocked

    assert blocked(Candidate(url="https://down-vn.img.susercontent.com/file/x", provider="ddg"))
    assert blocked(Candidate(url="https://x.com/a.jpg", provider="ddg", page="https://www.shutterstock.com/image/1"))
    assert not blocked(Candidate(url="https://live.staticflickr.com/1/a_b.jpg", provider="openverse"))


def test_wristwatch_suppresses_clock():
    d = decide([Det(box(.4, .4, .5, .5), .5, "coco", cls=C("clock")),
                Det(box(.4, .4, .5, .51), .5, "world", hardneg="wristwatch")], "clock")
    assert d.status == "reject" and "hard negative" in d.reasons[-1]


def test_conical_hat_suppresses_umbrella_but_keeps_other_umbrella():
    d = decide([Det(box(.1, .1, .3, .3), .9, "coco", cls=C("umbrella")),
                Det(box(.1, .1, .3, .3), .9, "world", hardneg="conical hat"),
                Det(box(.5, .2, .9, .6), .9, "coco", cls=C("umbrella")),
                Det(box(.5, .2, .9, .6), .5, "world", cls=C("umbrella"))], "umbrella")
    assert d.status == "review" and len(d.clusters) == 1  # 1 o that giu lai, nhung co box bi loai -> nguoi xem


def test_object_on_screen_is_not_labeled():
    d = decide([Det(box(.4, .4, .5, .5), .9, "coco", cls=C("cup")),
                Det(box(.3, .3, .7, .7), .6, "world", hardneg="television")], "cup")
    assert d.status == "reject"


def test_laptop_without_keyboard_goes_to_review():
    d = decide([Det(box(.2, .2, .8, .8), .9, "coco", cls=C("laptop")),
                Det(box(.2, .2, .8, .8), .5, "world", cls=C("laptop"))], "laptop")
    assert d.status == "review" and "keyboard" in " ".join(d.reasons)
    d2 = decide([Det(box(.2, .2, .8, .8), .9, "coco", cls=C("laptop")),
                 Det(box(.2, .2, .8, .8), .5, "world", cls=C("laptop")),
                 Det(box(.3, .55, .7, .78), .8, "coco", cls=C("keyboard")),
                 Det(box(.3, .55, .7, .78), .4, "world", cls=C("keyboard"))], "laptop")
    assert d2.status == "accept" and len(d2.clusters) == 2


def test_uncertain_box_sends_to_review():
    d = decide([Det(box(.2, .2, .7, .8), .9, "coco", cls=C("cup")),
                Det(box(.2, .2, .7, .8), .5, "world", cls=C("cup")),
                Det(box(.75, .1, .95, .4), .3, "coco", cls=C("bottle"))], "cup")  # bottle score .86 -> nghi van
    assert d.status == "review" and "box diem thap" in " ".join(d.reasons)
    # box nghi van li ti (vat o rat xa, < 0.5% anh) -> bo qua (DATASET_SPEC D.3)
    d2 = decide([Det(box(.2, .2, .7, .8), .9, "coco", cls=C("cup")),
                 Det(box(.2, .2, .7, .8), .5, "world", cls=C("cup")),
                 Det(box(.90, .1, .95, .15), .3, "coco", cls=C("bottle"))], "cup")
    assert d2.status == "accept"


def test_hard_negative_concept_ignores_confused_class():
    from snapget12.autolabel import confused_classes

    assert confused_classes("wristwatch") == {C("clock")} and confused_classes("poster") == set()
    watch_as_clock = [Det(box(.3, .3, .6, .6), .9, "coco", cls=C("clock")),
                      Det(box(.3, .3, .6, .6), .5, "world", cls=C("clock"))]
    # khong co concept (anh negative thuong) -> clock la vat the -> review
    assert decide(watch_as_clock, None).status == "review"
    # hard negative 'wristwatch', YOLO-World co thay wristwatch -> clock la loi can hoc -> accept, nhan rong
    seen = watch_as_clock + [Det(box(.3, .3, .6, .6), .3, "world", hardneg="wristwatch")]
    d = decide(seen, None, concept="wristwatch")
    assert d.status == "accept" and d.clusters == []
    # khong thay wristwatch o dau ma clock rat ro (2 model) -> co the la dong ho that -> review
    assert decide(watch_as_clock, None, concept="wristwatch").status == "review"
    # lop KHAC that su co trong anh van chan accept
    d3 = decide(seen + [Det(box(.7, .5, .9, .9), .9, "coco", cls=C("cup"))], None, concept="wristwatch")
    assert d3.status == "review" and [CLASSES[c.cls] for c in d3.clusters] == ["cup"]


def test_negative_images():
    assert decide([], None).status == "accept"
    d = decide([Det(box(.2, .2, .4, .4), .8, "coco", cls=C("chair"))], None)
    assert d.status == "review" and "negative" in d.reasons[0]


def test_to_yolo_lines_respects_target_index_order():
    d = decide([Det(box(.2, .2, .7, .8), .9, "coco", cls=C("cup")), Det(box(.2, .2, .7, .8), .5, "world", cls=C("cup"))], "cup")
    index_of = list(reversed(range(len(CLASSES))))  # file ten lop dich theo thu tu khac
    assert to_yolo_lines(d.clusters, index_of)[0][0] == len(CLASSES) - 1


def test_metadata_estimates_and_quality():
    assert estimate_lighting(Image.new("RGB", (64, 64), (20, 20, 25))) == "dark"
    assert estimate_lighting(Image.new("RGB", (64, 64), (200, 140, 70))) == "warm"
    assert estimate_lighting(Image.new("RGB", (64, 64), (150, 160, 170))) == "day"
    d = decide([Det(box(0, 0, .6, .6), .9, "coco", cls=C("cup")), Det(box(0, 0, .6, .6), .5, "world", cls=C("cup"))], "cup")
    assert estimate_distance(d.clusters, "cup") == "near" and estimate_distance(d.clusters, None) == ""
    rng = np.random.default_rng(0)
    photo = Image.fromarray((rng.random((90, 120, 3)) * 255).astype(np.uint8)).resize((1600, 1200))
    assert check_quality(photo) is None
    assert "anh nho" in check_quality(photo.resize((800, 600)))
    studio = Image.new("RGB", (1600, 1200), (255, 255, 255))
    studio.paste(Image.new("RGB", (400, 400), (200, 30, 30)), (600, 400))
    assert "studio" in check_quality(studio)
    assert "ti le" in check_quality(Image.new("RGB", (4000, 1000), (90, 90, 90)))

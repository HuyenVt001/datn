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


def _load_script17():
    import importlib.util
    from pathlib import Path

    p = Path(__file__).resolve().parents[1] / "scripts" / "17_crawl_autolabel.py"
    spec = importlib.util.spec_from_file_location("crawl17", p)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def test_crawler_never_reuses_or_overwrites_names(tmp_path):
    m = _load_script17()
    out = tmp_path / "vn_web"
    st = m.Store(out)
    # lan chay truoc: con gg_cup_0001..0003 tren dia; gg_cup_0005 da bi XOA nhung con dong trong metadata.csv
    for i in (1, 2, 3):
        Image.new("RGB", (8, 8)).save(out / "train" / "images" / f"gg_cup_{i:04d}.jpg")
    (out / "metadata.csv").write_text("filename,split\ngg_cup_0005.jpg,train\n", encoding="utf-8")
    st = m.Store(out)  # mo lai nhu lan chay moi
    _, _, mx = st.counts()
    assert st.reserve_name("cup", mx["cup"]) == "gg_cup_0006.jpg"  # khong dung lai 0004/0005
    st.save_state()
    # file nhan mo coi o review (anh bi xoa, nhan con) cung chan so do
    (out / "review" / "labels" / "gg_cup_0007.txt").write_text("")
    st2 = m.Store(out)
    assert st2.reserve_name("cup", 3) == "gg_cup_0008.jpg"
    assert st2.reserve_name("potted plant") == "gg_potted_plant_0001.jpg"
    # chot chan cuoi: save_jpeg tu choi ghi de
    import pytest

    with pytest.raises(FileExistsError):
        m.save_jpeg(Image.new("RGB", (8, 8)), out / "train" / "images" / "gg_cup_0001.jpg")


def test_new_source_parsers_and_key_handling(monkeypatch):
    import pytest

    from snapget12 import crawl

    serp = {"images_results": [{"original": "https://a.vn/x.jpg", "original_width": 1600, "original_height": 1200,
                                "link": "https://a.vn/p", "title": "ghe nhua", "source": "a.vn"}, {"title": "no url"}]}
    c = crawl.SerpApiGoogleSource.parse(serp)
    assert len(c) == 1 and c[0].provider == "serpapi" and c[0].width == 1600 and c[0].page == "https://a.vn/p"
    with pytest.raises(crawl.KeyRejected):
        crawl.SerpApiGoogleSource.parse({"error": "Your account has run out of searches."})
    assert crawl.SerpApiGoogleSource.parse({"error": "Google hasn't returned any results for this query."}) == []

    fl = {"stat": "ok", "photos": {"photo": [
        {"id": "1", "owner": "u", "title": "t", "license": "4", "ownername": "A", "url_o": "o.jpg", "width_o": 9000, "height_o": 6000,
         "url_l": "l.jpg", "width_l": "1024", "height_l": "683"},
        {"id": "2", "owner": "u", "license": "9", "url_o": "o2.jpg", "width_o": 3000, "height_o": 2000}]}}
    f = crawl.FlickrSource.parse(fl)
    assert [x.url for x in f] == ["l.jpg", "o2.jpg"]  # anh goc 9000px qua lon -> dung url_l
    assert f[0].license == "CC BY 2.0" and f[1].license == "CC0 1.0" and f[0].width == 1024
    with pytest.raises(crawl.KeyRejected):
        crawl.FlickrSource.parse({"stat": "fail", "code": 100, "message": "Invalid API Key"})

    px = crawl.PexelsSource.parse({"photos": [{"width": 4000, "height": 3000, "url": "p", "photographer": "B",
                                               "src": {"large2x": "l2.jpg", "original": "o.jpg"}}]})
    assert px[0].url == "l2.jpg" and px[0].license == "Pexels License"
    pb = crawl.PixabaySource.parse({"hits": [{"largeImageURL": "big.jpg", "imageWidth": 4000, "imageHeight": 3000, "pageURL": "pg"}]})
    assert pb[0].width == 1280 and pb[0].height == 960  # kich thuoc THAT cua largeImageURL

    for env in crawl.KEY_ENV.values():
        monkeypatch.delenv(env, raising=False)
    srcs = crawl.make_sources(["serpapi", "flickr", "wikimedia"])  # thieu key -> bo qua, khong loi
    assert [s.name for s in srcs] == ["wikimedia"]
    monkeypatch.setenv("PEXELS_API_KEY", "k")
    assert "pexels" in [s.name for s in crawl.make_sources(["auto"])]


def test_bad_key_is_detected_and_never_printed(monkeypatch):
    import pytest

    from snapget12 import crawl

    good = "12345678-" + "a1" * 16
    monkeypatch.setenv("PIXABAY_API_KEY", good + good)  # dan 2 lan
    assert "pixabay" not in [s.name for s in crawl.make_sources(["pixabay", "wikimedia"])]
    monkeypatch.setenv("PIXABAY_API_KEY", good)
    assert crawl.PixabaySource().key == good

    msg = f"400 Client Error for url: https://pixabay.com/api/?key={good}&q=cup and api_key=abc123"
    assert good not in crawl.redact(msg) and "abc123" not in crawl.redact(msg) and "q=cup" in crawl.redact(msg)

    class Resp:
        def __init__(self, code, text):
            self.status_code, self.text, self.headers = code, text, {}

    class Sess:
        def __init__(self, resp):
            self.resp = resp

        def get(self, *a, **k):
            return self.resp

    with pytest.raises(crawl.KeyRejected):
        crawl._get_json(Sess(Resp(400, "[ERROR 400] Invalid or missing API key")), "https://pixabay.com/api/", {"key": good})
    with pytest.raises(RuntimeError) as e:
        crawl._get_json(Sess(Resp(404, "not found")), "https://x.org/api/", {"key": good})
    assert good not in str(e.value)


def test_import_group_from_filename_or_folder(tmp_path):
    from pathlib import Path

    m = _load_script17()
    assert m.import_group(Path("x/gg_potted_plant_0012.jpg")) == "potted plant"
    assert m.import_group(Path("x/self_hardneg_0003.jpg")) == "hardneg"
    assert m.import_group(Path("google/motorcycle/abc123.jpg")) == "motorcycle"
    assert m.import_group(Path("google/random/abc.jpg")) is None


def test_download_many_never_waits_on_stuck_thread(monkeypatch):
    import time

    from snapget12 import crawl

    def fake(session, cand):
        if cand.url.endswith("slow"):
            time.sleep(3)
        return cand, "ok"

    monkeypatch.setattr(crawl, "download_one", fake)
    cands = [crawl.Candidate(url=f"https://x/{n}", provider="t") for n in ("a", "slow", "b")]
    t0 = time.time()
    res = crawl.download_many(cands, workers=3, batch_seconds=0.5)
    assert time.time() - t0 < 2
    assert [r for _, r in res] == ["ok", "tai qua lau", "ok"] and [c.url for c, _ in res] == [c.url for c in cands]


def test_crop_window_and_uncrop_roundtrip():
    from snapget12.autolabel import crop_window, uncrop

    w = crop_window(box(.4, .4, .5, .5))  # box nho -> cua so toi thieu 0.2 canh anh
    assert np.allclose(w, [.35, .35, .55, .55])
    back = uncrop([Det(box(.25, .25, .75, .75), .9, "coco", cls=C("cup"))], w)[0]
    assert np.allclose(back.box, [.4, .4, .5, .5])
    assert np.all(crop_window(box(0, 0, .1, 1)) >= 0) and np.all(crop_window(box(0, 0, .1, 1)) <= 1)


def test_auto_resolve_confirms_or_removes_uncertain_boxes():
    from snapget12.autolabel import auto_resolve

    th = Thresholds()
    chair = [Det(box(.1, .3, .4, .9), .8, "coco", cls=C("chair")), Det(box(.1, .3, .4, .9), .5, "world", cls=C("chair"))]
    umb = Det(box(.5, .4, .7, .9), .4, "coco", cls=C("umbrella"))  # box lop khac chua chac -> review
    assert decide(chair + [umb], "chair", th).status == "review"

    # phong to khong thay umbrella -> xoa box do -> accept, nhan chi con ghe
    d, note = auto_resolve(chair + [umb], "chair", th, lambda wins: [[] for _ in wins])
    assert d.status == "accept" and [c.cls for c in d.clusters] == [C("chair")] and "-umbrella" in note

    # phong to thay umbrella ro (2 model) -> giu, 1 box duy nhat (khong trung)
    def confirm(wins):
        return [[Det(box(.5, .4, .7, .9), .9, "coco", cls=C("umbrella")), Det(box(.51, .4, .7, .9), .6, "world", cls=C("umbrella"))]
                for _ in wins]
    d, _ = auto_resolve(chair + [umb], "chair", th, confirm)
    assert d.status == "accept" and sorted(c.cls for c in d.clusters) == sorted([C("chair"), C("umbrella")])

    # muc tieu chi 1 model, diem vua -> phong to khong xac nhan -> bi xoa -> reject (khong dua vao train)
    d, _ = auto_resolve([Det(box(.2, .2, .7, .8), .6, "coco", cls=C("cup"))], "cup", th, lambda wins: [[] for _ in wins])
    assert d.status == "reject"


def test_auto_resolve_laptop_keyboard_and_hardneg_untouched():
    from snapget12.autolabel import auto_resolve

    th = Thresholds()
    lap = [Det(box(.2, .2, .8, .8), .9, "coco", cls=C("laptop")), Det(box(.2, .2, .8, .8), .6, "world", cls=C("laptop"))]
    assert decide(lap, "laptop", th).status == "review"  # thieu keyboard
    kb = lambda wins: [[Det(box(.3, .5, .7, .75), .7, "coco", cls=C("keyboard"))] for _ in wins]  # noqa: E731
    d, note = auto_resolve(lap, "laptop", th, kb)
    assert d.status == "accept" and C("keyboard") in [c.cls for c in d.clusters] and "keyboard" in note
    d, _ = auto_resolve(lap, "laptop", th, lambda wins: [[] for _ in wins])  # khong thay phim -> laptop gap, van accept
    assert d.status == "accept" and [c.cls for c in d.clusters] == [C("laptop")]

    # o that (2 model) + 1 "umbrella" thuc ra la non la -> review vi hard negative: KHONG tu xu ly, de nguoi xem
    calls = []
    imgdets = [Det(box(.05, .05, .45, .6), .9, "coco", cls=C("umbrella")), Det(box(.05, .05, .45, .6), .6, "world", cls=C("umbrella")),
               Det(box(.55, .1, .95, .5), .6, "coco", cls=C("umbrella")), Det(box(.55, .1, .95, .5), .5, "world", hardneg="conical hat")]
    assert decide(imgdets, "umbrella", th).status == "review"
    d, note = auto_resolve(imgdets, "umbrella", th, lambda wins: calls.append(wins) or [[] for _ in wins])
    assert d.status == "review" and not calls and "hard negative" in note

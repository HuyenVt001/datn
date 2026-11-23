"""pytest snapget12/tests_clean.py — scripts/18_clean_dataset.py tren bo du lieu gia co du loai loi."""
import csv
import importlib.util
import json
import subprocess
import sys
from pathlib import Path

import numpy as np
from PIL import Image

SCRIPT = Path(__file__).resolve().parents[1] / "scripts" / "18_clean_dataset.py"


def _photo(seed, size=(1600, 1200)):
    rng = np.random.default_rng(seed)
    return Image.fromarray((rng.random((size[1] // 4, size[0] // 4, 3)) * 255).astype(np.uint8)).resize(size, Image.BILINEAR)


def _save(img, path, camera=False):
    path.parent.mkdir(parents=True, exist_ok=True)
    if camera:
        ex = Image.Exif()
        ex[271], ex[272] = "samsung", "SM-A546E"
        img.save(path, quality=92, exif=ex)
    else:
        img.save(path, quality=92)
    lab = Path(str(path).replace("/images/", "/labels/").replace("\\images\\", "\\labels\\")).with_suffix(".txt")
    lab.parent.mkdir(parents=True, exist_ok=True)
    lab.write_text("0 0.5 0.5 0.3 0.3\n")


def _build(vn: Path):
    tr, te = vn / "train" / "images", vn / "test" / "images"
    _save(_photo(1), tr / "gg_cup_0001.jpg")
    _save(_photo(1), tr / "gg_cup_0002.jpg")                        # trung y het 0001
    _save(_photo(1).resize((1400, 1050)), tr / "gg_cup_0003.jpg")   # gan trung (resize)
    _save(_photo(2), tr / "gg_cup_0004.jpg")                        # sach
    _save(_photo(3, (800, 600)), tr / "gg_cup_0005.jpg")            # nho
    studio = Image.new("RGB", (1600, 1200), (255, 255, 255))
    studio.paste(_photo(4, (400, 400)), (600, 400))
    _save(studio, tr / "gg_cup_0006.jpg")                           # nen studio
    _save(_photo(5), tr / "gg_cup_0007.jpg")                        # ro ri voi test
    _save(_photo(5), te / "self_cup_0001.jpg", camera=True)         # test that (co EXIF) — trung 0007
    _save(_photo(6), te / "self_cup_0002.jpg", camera=False)        # test KHONG EXIF -> nghi anh Internet
    rows = [{"filename": f"gg_cup_000{i}.jpg", "split": "train", "source": "gg", "url": "u", "scene": "street",
             "lighting": "dark", "distance": "far", "phone": ""} for i in range(1, 8)]
    rows += [{"filename": "self_cup_0001.jpg", "split": "test", "source": "self", "url": "", "scene": "home",
              "lighting": "day", "distance": "near", "phone": "Galaxy A54"},
             {"filename": "self_cup_0002.jpg", "split": "test", "source": "self", "url": "", "scene": "cafe",
              "lighting": "warm", "distance": "mid", "phone": "iPhone 13"},
             {"filename": "gg_cup_0999.jpg", "split": "train", "source": "gg", "url": "u", "scene": "street",
              "lighting": "day", "distance": "near", "phone": ""}]  # dong khong co anh
    with open(vn / "metadata.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0]))
        w.writeheader()
        w.writerows(rows)


def _run(vn, *extra):
    r = subprocess.run([sys.executable, str(SCRIPT), "--vn", str(vn), *extra], capture_output=True, text=True,
                       encoding="utf-8", env={**__import__("os").environ, "PYTHONIOENCODING": "utf-8"})
    assert r.returncode == 0, r.stderr
    return r.stdout


def test_dry_run_reports_without_touching_files(tmp_path):
    vn = tmp_path / "vn"
    _build(vn)
    before = sorted(p.name for p in vn.rglob("*.jpg"))
    _run(vn)
    assert sorted(p.name for p in vn.rglob("*.jpg")) == before  # dry-run khong dong vao file
    with open(vn / "clean_report.csv", encoding="utf-8") as f:
        rep = {r["file"]: r["issues"] for r in csv.DictReader(f)}
    assert "trung y het gg_cup_0001.jpg" in rep["gg_cup_0002.jpg"]
    assert "gan trung" in rep["gg_cup_0003.jpg"]
    assert "anh nho" in rep["gg_cup_0005.jpg"] and "studio" in rep["gg_cup_0006.jpg"]
    assert "TEST" in rep["gg_cup_0007.jpg"]
    assert "khong co EXIF" in rep["self_cup_0002.jpg"] and "self_cup_0001.jpg" not in rep
    assert "gg_cup_0001.jpg" not in rep and "gg_cup_0004.jpg" not in rep


def test_apply_quarantines_and_fixes_metadata(tmp_path):
    vn = tmp_path / "vn"
    _build(vn)
    _run(vn, "--apply", "--quarantine-test-web", "--recompute-metadata")
    left_train = sorted(p.name for p in (vn / "train" / "images").glob("*.jpg"))
    assert left_train == ["gg_cup_0001.jpg", "gg_cup_0004.jpg"]
    assert [p.name for p in (vn / "test" / "images").glob("*.jpg")] == ["self_cup_0001.jpg"]
    q = vn / "_quarantine"
    assert (q / "trung" / "train" / "images" / "gg_cup_0002.jpg").exists()
    assert (q / "trung" / "train" / "labels" / "gg_cup_0002.txt").exists()  # nhan di cung anh
    assert (q / "ro_ri" / "train" / "images" / "gg_cup_0007.jpg").exists()
    assert (q / "test_web" / "test" / "images" / "self_cup_0002.jpg").exists()
    with open(vn / "metadata.csv", encoding="utf-8") as f:
        rows = {r["filename"]: r for r in csv.DictReader(f)}
    assert set(rows) == {"gg_cup_0001.jpg", "gg_cup_0004.jpg", "self_cup_0001.jpg"}  # bo dong cach ly + dong mo coi
    assert rows["gg_cup_0001.jpg"]["phone"] == "web" and rows["gg_cup_0001.jpg"]["lighting"] in {"day", "warm", "dark"}
    assert rows["gg_cup_0001.jpg"]["distance"] == "mid"  # box 0.3x0.3 = 9% anh
    assert (vn / "metadata.csv.bak").exists()
    assert json.loads((vn / "clean_report.json").read_text(encoding="utf-8"))["applied"] is True

"""pytest snapget12/tests_package.py — dong goi tap train bo sung (scripts/19_package_dataset.py)."""
import csv
import json
import subprocess
import sys
import zipfile
from pathlib import Path

from PIL import Image

from snapget12.classes import CLASSES

ML = Path(__file__).resolve().parents[1]


def test_package_keeps_only_usable_images_and_remaps_classes(tmp_path):
    src = tmp_path / "vn_web"
    for d in ("train/images", "train/labels", "review/images", "review/labels"):
        (src / d).mkdir(parents=True)
    # file ten lop theo thu tu KHAC chuan (vd thu muc goc cua nhom) -> phai doi ve thu tu CLASSES
    names = list(reversed(CLASSES))
    (src / "classes.txt").write_text("\n".join(names) + "\n", encoding="utf-8")
    cup_src = names.index("cup")
    files = {
        "gg_cup_0001.jpg": f"{cup_src} 0.5 0.5 0.2 0.3\n",
        "gg_neg_0001.jpg": "",                        # negative: nhan rong
        "gg_chair_0001.jpg": None,                    # thieu nhan -> bo
        "gg_book_0001.jpg": "",                       # anh lop nhung khong box -> bo
        "gg_bottle_0001.jpg": "99 0.5 0.5 0.2 0.2\n",  # class id sai -> bo
    }
    for n, lab in files.items():
        Image.new("RGB", (64, 48), (200, 100, 50)).save(src / "train" / "images" / n)
        if lab is not None:
            (src / "train" / "labels" / n.replace(".jpg", ".txt")).write_text(lab, encoding="utf-8")
    Image.new("RGB", (8, 8)).save(src / "review" / "images" / "gg_cup_0002.jpg")  # review -> khong vao goi
    with open(src / "metadata.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=["filename", "scene", "lighting", "distance", "provider", "license", "autolabel"])
        w.writeheader()
        w.writerow({"filename": "gg_cup_0001.jpg", "scene": "cafe", "lighting": "day", "distance": "near",
                    "provider": "wikimedia", "license": "CC BY 2.0", "autolabel": "auto-review"})
        w.writerow({"filename": "gg_cup_0002.jpg", "scene": "cafe", "autolabel": "review"})
    out = tmp_path / "pkg.zip"
    r = subprocess.run([sys.executable, str(ML / "scripts" / "19_package_dataset.py"), "--src", str(src), "--out", str(out)],
                       capture_output=True, text=True, encoding="utf-8")
    assert r.returncode == 0, r.stderr
    z = zipfile.ZipFile(out)
    imgs = sorted(n.split("/")[-1] for n in z.namelist() if "/train/images/" in n)
    assert imgs == ["gg_cup_0001.jpg", "gg_neg_0001.jpg"]
    lab = z.read("snapget_vn_supplement/train/labels/gg_cup_0001.txt").decode()
    assert lab.split()[0] == str(CLASSES.index("cup"))
    assert z.read("snapget_vn_supplement/train/labels/gg_neg_0001.txt") == b""
    st = json.loads(z.read("snapget_vn_supplement/stats.json"))
    assert st["imagesPerClass"]["cup"] == 1 and st["negatives"] == 1 and sum(st["skipped"].values()) == 3
    meta = z.read("snapget_vn_supplement/metadata.csv").decode()
    assert "gg_cup_0001.jpg" in meta and "gg_cup_0002.jpg" not in meta
    assert "auto-review" in z.read("snapget_vn_supplement/README.md").decode()
    assert "names: ['cup'" in z.read("snapget_vn_supplement/data.yaml").decode()

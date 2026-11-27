import json
import sys

# Sinh notebook snapget_vn_data.ipynb:  python build/build_vn_data_nb.py snapget_vn_data.ipynb
# Neu da sua chu truc tiep trong notebook -> cap nhat file nay cho khop TRUOC khi sinh lai.
cells = []
BS = "\\"  # dau gach nguoc noi dong lenh shell trong notebook


def md(s):
    cells.append({"cell_type": "markdown", "metadata": {}, "source": s.strip("\n").splitlines(keepends=True)})


def code(s):
    s = s.replace("<BS>", BS)
    cells.append({"cell_type": "code", "metadata": {}, "execution_count": None, "outputs": [],
                  "source": s.strip("\n").splitlines(keepends=True)})


md("""
# Snapget — chuẩn bị tập ảnh Việt Nam cho lần train 9 lớp

Lần train mới chỉ giữ 9 lớp của 6 quest: cup, bottle, book, chair, laptop, keyboard, umbrella, bicycle, motorcycle. Bỏ potted plant, backpack, clock.

| Nhiệm vụ | Đầu vào (Drive `snapget-ml/data/`) | Kết quả |
|---|---|---|
| 1 | `snapget_vn_supplement.zip` | Lọc còn 9 lớp, **ghi đè** `snapget_vn_supplement.zip` |
| 2 | `vn.zip` | Lọc còn 9 lớp (giữ trên Colab, gộp ở nhiệm vụ 4) |
| 3 | thư mục `bosung/` (đã giải nén) | Tự vẽ box, gán nhãn, chia vào phần train/test còn thiếu |
| 4 | kết quả 2 + 3 | `vnreal.zip` **ghi đè** `vn.zip` |

- File cũ được sao lưu vào `snapget-ml/data/backup/` trước khi ghi đè.
- Nhiệm vụ 3 cần GPU (Runtime → Change runtime type → T4); các nhiệm vụ còn lại chạy được trên CPU.
""")

code("""
# 0a. Drive
from google.colab import drive
drive.mount('/content/drive')
import os, shutil, time
WORK = '/content/drive/MyDrive/snapget-ml'
DATA = f'{WORK}/data'
PREP = '/content/vnprep'        # thư mục làm việc trên đĩa Colab
BACKUP = True                   # sao lưu file cũ trước khi ghi đè
os.makedirs(PREP, exist_ok=True)

def put_on_drive(local, name):
    dst = f'{DATA}/{name}'
    if BACKUP and os.path.exists(dst):
        bak = f'{DATA}/backup/{name[:-4]}_{time.strftime("%Y%m%d-%H%M")}.zip'
        os.makedirs(os.path.dirname(bak), exist_ok=True)
        shutil.copy2(dst, bak)
        print('sao lưu:', bak)
    shutil.copy2(local, dst)
    print('->', dst, f'({os.path.getsize(dst) / 1e6:.0f} MB)')
""")

code("""
# 0b. Code + thư viện
%cd /content
!rm -rf datn && git clone --depth 1 https://github.com/HuyenVt001/datn.git datn
%cd /content/datn/ml
!pip install -q "ultralytics>=8.3,<9" pyyaml pillow-heif
""")

md("""
## 1. Lọc `snapget_vn_supplement` còn 9 lớp
- Box của lớp bị bỏ được xoá khỏi file nhãn; chỉ số lớp đánh lại theo `data.yaml` 9 lớp.
- Ảnh mang tên lớp bị bỏ (vd `gg_clock_0012`): nếu còn box lớp khác (vd có ghế) thì giữ và đổi tên theo lớp đó, không thì xoá.
- `metadata.csv` và `README.md` được cập nhật theo.
""")

code("""
!rm -rf {PREP}/raw/sup {PREP}/sup9 && mkdir -p {PREP}/raw/sup
!unzip -q -o "{DATA}/snapget_vn_supplement.zip" -d {PREP}/raw/sup
!python scripts/18_prepare_vn_data.py filter --src {PREP}/raw/sup --out {PREP}/sup9/snapget_vn_supplement
if os.path.exists(f'{PREP}/sup9/snapget_vn_supplement/data.yaml'):
    !cd {PREP}/sup9 && rm -f ../snapget_vn_supplement.zip && zip -q -r ../snapget_vn_supplement.zip snapget_vn_supplement
    put_on_drive(f'{PREP}/snapget_vn_supplement.zip', 'snapget_vn_supplement.zip')
""")

md("""
## 2. Lọc `vn.zip` còn 9 lớp
- Train: giống nhiệm vụ 1.
- Test: ảnh mang tên lớp bị bỏ mà không còn box lớp nào khác được giữ làm **ảnh âm** (`self_neg_xxxx`), vì đây vẫn là ảnh thật tốt để kiểm tra nhận nhầm.
- Cuối ô in số ảnh test của từng nhóm quest và phần còn thiếu so với mục tiêu 85 ảnh/nhóm (khoảng 50 ảnh vào real_test). Đây là số ảnh nên chụp thêm cho thư mục `bosung/`.
""")

code("""
!rm -rf {PREP}/raw/vn {PREP}/vn9 && mkdir -p {PREP}/raw/vn
!unzip -q -o "{DATA}/vn.zip" -d {PREP}/raw/vn
!python scripts/18_prepare_vn_data.py filter --src {PREP}/raw/vn --out {PREP}/vn9
""")

md("""
## 3. Gán nhãn tự động ảnh bổ sung
Ảnh tự chụp đặt sẵn (đã giải nén) trong `snapget-ml/data/bosung/`, xếp theo thư mục, **tên thư mục = lớp chính** (tên tiếng Anh như trên). Script chỉ đọc, không sửa thư mục này.
```
snapget-ml/data/
└─ bosung/
   ├─ umbrella/   *.jpg
   ├─ chair/      *.jpg
   ├─ book/ ...
   ├─ hardneg/    ảnh âm khó: nón lá, áo mưa, tablet, menu quán, ảnh vật trên màn hình...
   ├─ neg/        ảnh không có vật nào
   └─ test/umbrella/ ...   (tuỳ chọn: ép vào test; tương tự train/)
```
- Hai detector cùng chạy: y1-s (đã học ảnh Việt Nam) và YOLO11x COCO. Box cùng lớp được gộp. Lớp chính lấy ngưỡng thấp hơn để ít bỏ sót.
- Box được vẽ cho **mọi** lớp trong 9 lớp xuất hiện trong ảnh, không chỉ lớp chính.
- Chia tập: lấp phần test còn thiếu của từng nhóm trước, còn lại vào train. Ảnh chụp liền nhau được giữ chung một tập để hạn chế rò rỉ train ↔ test.
- Ảnh được đổi tên tiếp số thứ tự của `vn.zip` (vd `self_umbrella_0046.jpg`), xoay đúng chiều theo EXIF; ảnh `.heic` được chuyển sang `.jpg`.
- Ảnh nhãn chưa chắc chắn (không thấy lớp chính, box nghi vấn, ảnh âm có vật thể) được đánh dấu trong `bosung_log.csv` và hiện ở cuối ô.
""")

code("""
BOSUNG = f'{DATA}/bosung'      # ảnh bổ sung đã giải nén trên Drive
assert os.path.isdir(BOSUNG), f'Không thấy thư mục {BOSUNG}'
!rm -rf {PREP}/bosung_auto
Y1 = f'{WORK}/artifacts/y1-s/best.pt'
!python scripts/18_prepare_vn_data.py autolabel --src "{BOSUNG}" --vn {PREP}/vn9 <BS>
    --out {PREP}/bosung_auto/bosung --y1 {Y1} --coco yolo11x.pt --test-target 85
if os.path.exists(f'{PREP}/bosung_auto/bosung/data.yaml'):
    !cd {PREP}/bosung_auto && rm -f bosung_review.zip && zip -q -r bosung_review.zip bosung
    shutil.copy2(f'{PREP}/bosung_auto/bosung_review.zip', f'{DATA}/bosung_review.zip')
    shutil.copy2(f'{PREP}/bosung_auto/bosung_log.csv', f'{DATA}/bosung_log.csv')
    print('-> Drive: bosung_review.zip, bosung_log.csv')
""")

code("""
# Ảnh cần soát (tối đa 12): khung đỏ = box tự động
import glob
from IPython.display import Image, display
prev = sorted(glob.glob(f'{PREP}/bosung_auto/bosung_preview/*.jpg'))
print(f'{len(prev)} ảnh cần soát')
for p in prev[:12]:
    display(Image(p, width=320))
""")

md("""
### Soát nhãn (bắt buộc với ảnh vào test)
Nhãn tự động có thể sót hoặc sai box. Nếu đưa thẳng vào test, số liệu đánh giá sẽ sai lệch, nên nhiệm vụ 4 **không nhận ảnh test chưa soát**.
1. Tải `bosung_review.zip` từ Drive về, giải nén. Roboflow → *Create Project* (Object Detection) → *Upload* cả thư mục `bosung` (Roboflow tự đọc nhãn YOLO và `data.yaml`).
2. Soát từng ảnh, ưu tiên các ảnh có cột `flags` trong `bosung_log.csv`: vẽ box còn sót, xoá box sai, xoá ảnh hỏng.
3. *Generate version*: bỏ hết Preprocessing và Augmentation → *Export* định dạng YOLOv8 → đổi tên file thành `bosung_reviewed.zip` → đặt vào `snapget-ml/data/`.

Tên file có thêm đuôi `_jpg.rf.xxx` của Roboflow không sao, script tự nhận lại.
""")

md("""
## 4. Gộp thành `vnreal.zip` và ghi đè `vn.zip`
- Gộp `vn` đã lọc (nhiệm vụ 2) với ảnh bổ sung (nhiệm vụ 3). Có `bosung_reviewed.zip` thì dùng nhãn đã soát; ảnh bị xoá khi soát sẽ bị bỏ.
- Chưa soát: ảnh nhãn không chắc chắn bị bỏ; ảnh test chỉ được nhận khi đặt `ALLOW_UNREVIEWED_TEST = True`.
- In số ảnh cuối cùng từng nhóm và phần test còn thiếu.
""")

code("""
ALLOW_UNREVIEWED_TEST = False
REV = f'{DATA}/bosung_reviewed.zip'
rev = ''
if os.path.exists(REV):
    !rm -rf {PREP}/raw/reviewed && mkdir -p {PREP}/raw/reviewed && unzip -q -o "{REV}" -d {PREP}/raw/reviewed
    rev = f'--reviewed {PREP}/raw/reviewed'
else:
    print('Chưa có bosung_reviewed.zip -> dùng nhãn tự động')
flag = '--allow-unreviewed-test' if ALLOW_UNREVIEWED_TEST else ''
!rm -rf {PREP}/vnreal {PREP}/vnreal.zip
!python scripts/18_prepare_vn_data.py merge --vn {PREP}/vn9 --bosung {PREP}/bosung_auto/bosung {rev} {flag} <BS>
    --out {PREP}/vnreal/vn
if os.path.exists(f'{PREP}/vnreal/vn/data.yaml'):
    !cd {PREP}/vnreal && zip -q -r ../vnreal.zip vn
    put_on_drive(f'{PREP}/vnreal.zip', 'vn.zip')
""")

md("""
Notebook train đọc `vn.zip` và `snapget_vn_supplement.zip` mới. Hai file giờ chỉ còn 9 lớp, nên pipeline train cũng phải chuyển sang 9 lớp trước khi train lại.
""")

nb = {"cells": cells,
      "metadata": {"colab": {"provenance": []}, "accelerator": "GPU",
                   "kernelspec": {"display_name": "Python 3", "name": "python3"}, "language_info": {"name": "python"}},
      "nbformat": 4, "nbformat_minor": 0}
with open(sys.argv[1], "w", encoding="utf-8") as fh:
    json.dump(nb, fh, ensure_ascii=False, indent=1)
print("cells:", len(cells))

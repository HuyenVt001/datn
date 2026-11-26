import json
import sys

cells = []
BS = "\\"  # dau gach nguoc noi dong lenh shell trong notebook


def md(s):
    cells.append({"cell_type": "markdown", "metadata": {}, "source": s.strip("\n").splitlines(keepends=True)})


def code(s):
    s = s.replace("<BS>", BS)
    cells.append({"cell_type": "code", "metadata": {}, "execution_count": None, "outputs": [],
                  "source": s.strip("\n").splitlines(keepends=True)})


md("""
# Snapget y1 — fine-tune YOLO11 cho xác minh ảnh Daily Quest

Model nhận diện 12 lớp vật thể (cup, bottle, book, chair, potted plant, laptop, keyboard, backpack, clock, umbrella, bicycle, motorcycle). Ảnh người dùng đăng được coi là "có vật thể X" khi điểm cao nhất của lớp X trên toàn ảnh vượt ngưỡng riêng của lớp đó.

Code xử lý nằm trong `ml/snapget12/` và `ml/scripts/10–16`; notebook chỉ gọi lần lượt các script. Chạy trên Colab, GPU T4.

| Ô | Nội dung | Script |
|---|---|---|
| 0 | Mount Drive, lấy code, cài thư viện | |
| 1 | COCO 2017 → dataset YOLO 12 lớp | `10` |
| 2 | Giải nén dữ liệu bổ sung | |
| 3 | Gộp dữ liệu, kiểm tra, chia tập | `11` |
| 4 | Fine-tune YOLO11s (và YOLO11n để so sánh) | `13` |
| 5 | Đánh giá, so sánh các model | `14` |
| 6 | Chọn lớp ra đề quest | `15` |
| 7 | Export ONNX, đưa model lên Hugging Face | `16` |
| 8 | Bảng tổng hợp kết quả | |

Dữ liệu gốc và checkpoint để trên Drive (`WORK`). Dataset làm việc giải nén ra `/content` vì đọc ảnh trực tiếp từ Drive rất chậm; Colab ngắt thì phần này mất, chạy lại ô 0–3 là có lại.
""")
code("""
# 0a. Drive
from google.colab import drive
drive.mount('/content/drive')
WORK = '/content/drive/MyDrive/snapget-ml'      # dữ liệu gốc + checkpoint (Drive)
YOLO = '/content/yolo'                          # dataset làm việc (đĩa Colab)
EXT = '/content/ext'                            # dữ liệu bổ sung đã giải nén
import os
for d in [WORK, f'{WORK}/data', f'{WORK}/artifacts', YOLO, EXT]:
    os.makedirs(d, exist_ok=True)
""")
code("""
# 0b. Code + thư viện
%cd /content
!rm -rf datn && git clone --depth 1 https://github.com/HuyenVt001/datn.git datn
%cd /content/datn/ml
!pip install -q -r requirements.txt
!nvidia-smi --query-gpu=name,memory.total --format=csv
""")
md("""
## 1. COCO 2017 → YOLO 12 lớp
Lấy ảnh COCO có chứa 12 lớp (tối đa 2.000 ảnh/lớp) cùng 1.500 ảnh nền không có lớp nào, giữ nguyên box gốc. Thêm khoảng 20% bản cắt dọc 3:4 để giống ảnh chụp điện thoại. Tập test lấy từ COCO val2017.

Lần đầu tải mất 20–40 phút, xong thì nén lại thành `coco_yolo.zip` trên Drive; các lần sau chỉ giải nén.
""")
code("""
import os
ZIP = f'{WORK}/data/coco_yolo.zip'
if os.path.exists(ZIP):
    !unzip -q -o {ZIP} -d {YOLO}
else:
    !python scripts/10_export_coco_yolo.py --out {YOLO}/coco --max-per-class 2000 --negatives 1500 <BS>
        --test-max 3000 --test-negatives 500 --portrait-frac 0.2
    !cd {YOLO} && zip -q -r {ZIP} coco      # ảnh là symlink tới cache FiftyOne, zip -r chép ảnh thật
!head -40 {YOLO}/coco/stats.json
""")
md("""
## 2. Dữ liệu bổ sung
COCO chủ yếu là ảnh nước ngoài, thiếu nhiều vật thể quen thuộc ở Việt Nam (ghế nhựa vỉa hè, ly trà sữa, xe số, dù che quán...). Phần này bù vào:

- `snapget_vn_supplement.zip` — tập train bổ sung dữ liệu bản địa: ảnh bối cảnh Việt Nam, nhãn YOLO 12 lớp, chỉ dùng để train. Số lượng, nguồn và giấy phép ghi trong `README.md` của gói.
- `vn.zip` — ảnh tự chụp bằng điện thoại, gồm `train/` và `test/` (cấu trúc theo `ml/DATASET_SPEC.md`). Tập test chỉ gồm ảnh tự chụp; đây là tập dùng để chọn ngưỡng và chọn lớp quest. Khi chưa có, ngưỡng tạm chọn trên tập val.
- Tuỳ chọn: dataset Kaggle có box (cần `kaggle.json` trên Drive) hoặc ảnh tự gán nhãn trên Roboflow (`google_labeled.zip`).
""")
code("""
KAGGLE_DATASETS = [
    # 'owner/dataset-name',
]
import os, shutil
if KAGGLE_DATASETS:
    os.makedirs('/root/.kaggle', exist_ok=True)
    shutil.copy(f'{WORK}/kaggle.json', '/root/.kaggle/kaggle.json'); os.chmod('/root/.kaggle/kaggle.json', 0o600)
    for i, ds in enumerate(KAGGLE_DATASETS):
        !kaggle datasets download -d {ds} -p {EXT}/kaggle{i} --unzip -q
z = f'{WORK}/data/google_labeled.zip'
if os.path.exists(z):
    !unzip -q -o {z} -d {EXT}/google_labeled
# ảnh tự chụp (train + test)
!rm -rf {EXT}/vn
if os.path.exists(f'{WORK}/data/vn.zip'):
    print('vn.zip: có')
    !mkdir -p {EXT}/vn && unzip -q -o "{WORK}/data/vn.zip" -d {EXT}/vn
else:
    print('vn.zip: chưa có -> không có tập test ảnh thật, ngưỡng chọn tạm trên val')
# tập train bổ sung dữ liệu bản địa
!rm -rf {EXT}/snapget_vn_supplement
if os.path.exists(f'{WORK}/data/snapget_vn_supplement.zip'):
    print('snapget_vn_supplement.zip: có')
    !unzip -q -o {WORK}/data/snapget_vn_supplement.zip -d {EXT}
else:
    print('snapget_vn_supplement.zip: KHÔNG TÌM THẤY trong WORK/data/')
!ls {EXT}
""")
md("""
## 3. Gộp dữ liệu, kiểm tra, chia tập
Script `11` gộp COCO với dữ liệu bổ sung thành `data.yaml` và danh sách ảnh cho từng tập:
- ảnh Việt Nam được lặp ×3 trong tập train để không bị COCO lấn át;
- ảnh test tự chụp chia 40% `real_calib` (chọn ngưỡng) / 60% `real_test` (báo cáo, chọn lớp);
- ảnh train gần trùng ảnh test (dHash) bị loại để tránh rò rỉ.

Nếu dữ liệu sai đặc tả (thiếu ảnh, sai class id, box lỗi) thì script dừng; các thiếu hụt nhẹ hơn chỉ ghi cảnh báo vào `merge_report.json`.
""")
code("""
import glob, os

def find_vn(base):
    # file nén có thể bọc thêm 1–2 tầng thư mục
    cands = [p.rstrip('/') for p in glob.glob(f'{base}/**/', recursive=True)
             if os.path.isdir(os.path.join(p, 'train', 'images')) or os.path.isdir(os.path.join(p, 'test', 'images'))]
    return sorted(cands, key=len)[0] if cands else None

sources = ''
for d in sorted(glob.glob(f'{EXT}/kaggle*')) + [f'{EXT}/google_labeled']:
    if os.path.isdir(d):
        sources += f' --yolo {d}'     # dataset dạng COCO-JSON / VOC thì đổi thành --coco-json / --voc
VN_DIR = find_vn(f'{EXT}/vn')
vn = f'--vn {VN_DIR}' if VN_DIR else ''
if os.path.isdir(f'{EXT}/snapget_vn_supplement/train'):
    vn += f' --vn {EXT}/snapget_vn_supplement'
VN_MIN_TRAIN = 100   # số ảnh Việt Nam tối thiểu mỗi lớp trong train
!python scripts/11_merge_external.py --root {YOLO} --coco {YOLO}/coco {sources} {vn} --class-map yolo_class_map.yaml <BS>
    --vn-min-train {VN_MIN_TRAIN}
!cp {YOLO}/merge_report.json {WORK}/artifacts/merge_report.json
""")
md("""
## 4. Fine-tune
- Khởi tạo từ YOLO11s pretrained COCO; head 80 lớp được cắt còn đúng 12 lớp, giữ nguyên trọng số đã học.
- `imgsz 640`, 40 epoch, batch tự chọn theo VRAM; dừng sớm nếu 12 epoch liền không cải thiện; 8 epoch cuối tắt mosaic.
- Trên T4 mỗi epoch mất 8–12 phút, nên thường phải chạy qua 2–3 phiên Colab. Checkpoint lưu lên Drive sau mỗi epoch; bị ngắt thì chạy lại ô 0 → 1 → 2 → 3 rồi chạy 4b.
- 4c train thêm YOLO11n (nhẹ hơn khoảng 3 lần) để so sánh độ chính xác với tốc độ.
""")
code("""
# 4a. Train YOLO11s
!python scripts/13_train_yolo.py --data {YOLO}/data.yaml --model yolo11s.pt <BS>
    --project {WORK}/artifacts/yolo --name y1-s --epochs 40 --export-best {WORK}/artifacts/y1-s/best.pt
""")
code("""
# 4b. Train tiếp từ checkpoint sau khi Colab ngắt
!python scripts/13_train_yolo.py --data {YOLO}/data.yaml --project {WORK}/artifacts/yolo --name y1-s --resume <BS>
    --export-best {WORK}/artifacts/y1-s/best.pt
""")
code("""
# 4c. YOLO11n (so sánh)
!python scripts/13_train_yolo.py --data {YOLO}/data.yaml --model yolo11n.pt <BS>
    --project {WORK}/artifacts/yolo --name y1-n --epochs 40 --export-best {WORK}/artifacts/y1-n/best.pt
""")
md("""
## 5. Đánh giá
Các model được đánh giá trên cùng tập ảnh và cùng cách chọn ngưỡng:
- `yolo11s-coco` — YOLO11s gốc chưa fine-tune (mốc so sánh);
- `y1-s`, `y1-n` — hai model vừa fine-tune;
- `v0` — model MobileNetV3 cũ (nếu còn file `artifacts/v0/model.onnx`).

Hai cấp chỉ số:
- **Detection:** mAP50, mAP50-95 từng lớp, confusion matrix 13×13 (12 lớp + nền).
- **Xác minh ảnh** (đúng với quyết định của app): AP, precision, recall, F1, tỉ lệ từ chối sai, confusion cấp ảnh.

Ngưỡng từng lớp được chọn trên `real_calib` theo nguyên tắc recall cao nhất với precision ≥ 0.80. Đo thêm độ trễ suy luận trên CPU.
""")
code("""
import os
models = f'--model yolo11s-coco=yolo11s.pt --model y1-s={WORK}/artifacts/y1-s/best.pt'
if os.path.exists(f'{WORK}/artifacts/y1-n/best.pt'):
    models += f' --model y1-n={WORK}/artifacts/y1-n/best.pt'
if os.path.exists(f'{WORK}/artifacts/v0/model.onnx'):
    models += f' --classifier v0={WORK}/artifacts/v0/model.onnx'
meta = f'--vn-meta {VN_DIR}/metadata.csv' if VN_DIR and os.path.exists(f'{VN_DIR}/metadata.csv') else ''
!python scripts/14_eval_yolo.py --root {YOLO} --out {WORK}/artifacts/eval {models} {meta} --max-coco 2000
""")
code("""
# Biểu đồ của y1-s: confusion matrix (detection), confusion cấp ảnh, PR curve, F1 curve
# (real_test nếu đã có ảnh tự chụp, không thì coco_test)
from IPython.display import Image, display
import os
EVAL = f'{WORK}/artifacts/eval/y1-s'
SPLIT = 'real_test' if os.path.exists(f'{EVAL}/metrics_real_test.json') else 'coco_test'
print('Tập:', SPLIT)
for p in [f'{EVAL}/det_{SPLIT}/confusion_matrix_normalized.png',
          f'{EVAL}/image_confusion_{SPLIT}.png',
          f'{EVAL}/pr_curves_{SPLIT}.png',
          f'{EVAL}/det_{SPLIT}/BoxF1_curve.png']:
    if os.path.exists(p):
        print(p); display(Image(p, width=900))
""")
md("""
## 6. Chọn lớp ra đề quest
Một lớp chỉ được dùng làm quest khi đạt đủ các tiêu chí trên `real_test`:

| Tiêu chí | Ngưỡng |
|---|---|
| AP cấp ảnh | ≥ 0.85 |
| F1 tại ngưỡng | ≥ 0.85 |
| AP50 detection | ≥ 0.75 |
| Tỉ lệ từ chối sai | ≤ 15% |
| Bị lớp khác nhận nhầm | ≤ 5% |
| Số ảnh test | ≥ 20 |

Nếu ít hơn 6 lớp đạt thì lấy 6 lớp có F1 cao nhất. Kết quả lưu ở `class_selection.json` / `.md`, kèm danh sách `AI_QUEST_CLASSES` để cập nhật cho server.
""")
code("""
import os
EVAL = f'{WORK}/artifacts/eval/y1-s'
SPLIT = 'real_test' if os.path.exists(f'{EVAL}/metrics_real_test.json') else 'coco_test'
if SPLIT != 'real_test':
    print('Chưa có real_test -> kết quả chọn lớp dưới đây là tạm thời (trên coco_test)')
!python scripts/15_select_classes.py --eval {EVAL} --split {SPLIT}
!cat {EVAL}/class_selection.md
""")
md("""
## 7. Export ONNX và triển khai
Export ONNX fp32 (không lượng tử hoá int8 vì làm giảm độ chính xác), so khớp đầu ra với PyTorch, rồi tải `model.onnx`, `thresholds.json`, `model_meta.json` lên Hugging Face. AI service trên Cloud Run tải model từ đó khi khởi động; sau khi restart, `/health` trả về `modelVersion: "y1"`, `arch: "yolo"`.

Chỉ thực hiện khi y1 tốt hơn model v0 trên ảnh thật. Cần token Hugging Face có quyền write.
""")
code("""
from huggingface_hub import login
login()
MODEL_REPO = 'Flowerng/snapget-ai-model'
!python scripts/16_export_yolo_onnx.py --weights {WORK}/artifacts/y1-s/best.pt <BS>
    --thresholds {WORK}/artifacts/eval/y1-s/thresholds.json --version y1 --out {WORK}/artifacts/y1-s/export <BS>
    --upload {MODEL_REPO}
""")
md("""
## 8. Tổng hợp
So sánh các model trên từng tập: mAP cấp ảnh, macro-F1, mAP50 / mAP50-95 detection và độ trễ trung vị trên CPU.
""")
code("""
import json
rows = json.load(open(f'{WORK}/artifacts/eval/ablation.json'))
f = lambda v: '-' if v is None else (f'{v:.3f}' if isinstance(v, float) else str(v))
print(f"{'model':14s} {'split':10s} {'n':>5s} {'mAP ảnh':>8s} {'macroF1':>8s} {'AP50 box':>9s} {'AP50-95':>8s} {'p50 ms':>7s}")
for r in rows:
    print(f"{r['model']:14s} {r['split']:10s} {r['n']:5d} {f(r['imageMAP']):>8s} {f(r['macroF1']):>8s} "
          f"{f(r['detMAP50']):>9s} {f(r['detMAP50_95']):>8s} {f(r['latencyP50ms']):>7s}")
""")

nb = {"cells": cells,
      "metadata": {"accelerator": "GPU", "colab": {"provenance": [], "gpuType": "T4"},
                   "kernelspec": {"display_name": "Python 3", "name": "python3"}, "language_info": {"name": "python"}},
      "nbformat": 4, "nbformat_minor": 0}
with open(sys.argv[1], "w", encoding="utf-8") as fh:
    json.dump(nb, fh, ensure_ascii=False, indent=1)
print("cells:", len(cells))

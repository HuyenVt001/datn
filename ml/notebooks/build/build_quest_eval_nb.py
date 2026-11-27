import json
import sys

# Sinh notebook snapget_quest_eval.ipynb:  python build/build_quest_eval_nb.py snapget_quest_eval.ipynb
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
# Snapget — đánh giá 7 quest AI và model đếm người

Model chính y2 (notebook `snapget_yolo_train.ipynb`) học 9 lớp, mỗi lớp gán nhãn riêng. Các lớp dễ lẫn được gộp lại khi chấm quest: ảnh qua nếu có **bất kỳ** lớp nào trong nhóm vượt ngưỡng riêng của lớp đó. Cách này tăng tỉ lệ pass và giảm từ chối sai.

| Quest | Lớp chấp nhận | Model |
|---|---|---|
| bicycle | bicycle / motorcycle | y2 |
| laptop | laptop / keyboard | y2 |
| umbrella | umbrella | y2 |
| chair | chair | y2 |
| cup | cup / bottle | y2 |
| book | book | y2 |
| person | ≥ 2 người trong khung | YOLO11n COCO gốc (không fine-tune) |

- **Quest person** dùng model phụ YOLO11n pretrained COCO, chỉ giữ lớp `person`. Điểm của ảnh là độ tin cậy của **người thứ 2** sau NMS, nên "có ≥ 2 người" thành một điểm cấp ảnh và đánh giá được bằng AP/F1 như các lớp khác. Lớp person của COCO đã rất tốt nên không cần train lại.
- **6 quest còn lại** dùng lại điểm số của y2 từ bước đánh giá (mục 5 notebook train), không chạy lại model.

Chạy được trên CPU.
""")

code("""
# 0a. Drive
from google.colab import drive
drive.mount('/content/drive')
WORK = '/content/drive/MyDrive/snapget-ml'
import os
os.makedirs(f'{WORK}/artifacts', exist_ok=True)
""")

code("""
# 0b. Code + thư viện
%cd /content
!rm -rf datn && git clone --depth 1 https://github.com/HuyenVt001/datn.git datn
%cd /content/datn/ml
!pip install -q -r requirements.txt
""")

md("""
## 1. Ảnh test quest person
Ảnh tự chụp, gán nhãn theo thư mục, nén thành `snapget-ml/data/quest_test.zip`:
- `person/`: có ≥ 2 người (selfie nhóm, chụp xa/gần, ảnh co-op ghép đôi);
- `other/`: dưới 2 người (selfie một mình, ảnh một người, ảnh không có người).

Tập này được chia 40% để chọn ngưỡng, 60% để đánh giá.
""")

code("""
import glob, os
QT = None
if os.path.exists(f'{WORK}/data/quest_test.zip'):
    !rm -rf /content/quest_test && unzip -q -o {WORK}/data/quest_test.zip -d /content/quest_test
    # file nén có thể bọc thêm 1–2 tầng thư mục
    hits = sorted(glob.glob('/content/quest_test/**/person', recursive=True), key=len)
    QT = os.path.dirname(hits[0]) if hits else None
for sub in ('person', 'other'):
    n = len(glob.glob(f'{QT}/{sub}/*')) if QT else 0
    print(f'{sub}: {n} ảnh')
if not QT:
    print('Chưa có quest_test.zip -> chỉ đánh giá 6 quest của y2')
""")

md("""
## 2. Đánh giá 7 quest
- 6 quest của y2 đánh giá trên `real_test` (ngưỡng từng lớp đã chọn trên `real_calib`).
- Quest person: chọn ngưỡng trên phần calib (precision ≥ 0.80), đánh giá trên phần test của `quest_test`.
- Cột "Từ chối sai trước gộp" là tỉ lệ từ chối sai của từng lớp khi chưa gộp, để so sánh.
""")

code("""
QT_ARGS = f'--quest-test {QT}' if QT else ''
!python scripts/17_eval_quests.py --eval {WORK}/artifacts/eval/y2-s {QT_ARGS} <BS>
    --person-weights yolo11n.pt --out {WORK}/artifacts/quest_eval
""")

md("""
## 3. Export model person và triển khai
Export YOLO11n (chỉ lớp person) sang ONNX fp32 kèm ngưỡng vừa chọn, rồi tải lên thư mục `person/` của Hugging Face repo. AI service tự nạp model phụ khi khởi động; sau khi restart, `/health` trả về `extraModels.person`.

Model chính y2 export bằng mục 7 của notebook train.
""")

code("""
from huggingface_hub import login
login()
MODEL_REPO = 'Flowerng/snapget-ai-model'
THR = f'{WORK}/artifacts/quest_eval/person/thresholds.json'
THR_ARGS = f'--thresholds {THR}' if os.path.exists(THR) else ''
!python scripts/16_export_yolo_onnx.py --task person {THR_ARGS} --out {WORK}/artifacts/person <BS>
    --upload {MODEL_REPO}
""")

nb = {"cells": cells,
      "metadata": {"colab": {"provenance": []},
                   "kernelspec": {"display_name": "Python 3", "name": "python3"}, "language_info": {"name": "python"}},
      "nbformat": 4, "nbformat_minor": 0}
with open(sys.argv[1], "w", encoding="utf-8") as fh:
    json.dump(nb, fh, ensure_ascii=False, indent=1)
print("cells:", len(cells))

Thư mục artifact model — RỖNG trong repo (không commit binary).

Sau khi train xong (ml/notebooks/snapget12_train.ipynb), copy/upload 3 file này lên Space:
- model.onnx        (backbone + head, đã quantize int8, ~2.5MB)
- thresholds.json   ({"cup":0.35, ...} — ngưỡng riêng từng lớp)
- model_meta.json   ({"modelVersion":"v0","classes":[...],"inputSize":224,"mean":[..],"std":[..]})

Cách upload: bước cuối notebook dùng `HfApi().upload_folder(folder_path="artifacts/v0", path_in_repo="model", repo_id="<user>/snapget-ai", repo_type="space")`.

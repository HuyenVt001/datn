Thư mục artifact model — RỖNG trong repo (không commit binary).

Service tự tải 3 file từ HF Model repo lúc khởi động (env `MODEL_REPO=<user>/snapget-ai-model`), hoặc copy tay vào đây:
- `model.onnx`        y1: YOLO11 fp32, input 1×3×640×640, output 1×16×8400 (`ml/scripts/16_export_yolo_onnx.py`)
                      v0: MobileNetV3 + head, input 1×3×224×224, logits 1×12 (`ml/scripts/05_export_onnx.py`)
- `thresholds.json`   `{"cup": 0.42, ...}` — ngưỡng riêng từng lớp (y1 chọn trên ảnh thật real-calib)
- `model_meta.json`   y1: `{"modelVersion":"y1","arch":"yolo","classes":[...12],"inputSize":640,"preprocess":"letterbox",...}`
                      v0: `{"modelVersion":"v0","classes":[...12],"inputSize":224,"mean":[..],"std":[..]}` (không có `arch`)

Chạy local không cần train: `python dev_fake_model.py` (classifier giả) hoặc `python dev_fake_model.py --arch yolo` (YOLO giả).

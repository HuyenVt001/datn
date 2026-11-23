"""
16 — Export YOLO y1 -> ONNX fp32 + model_meta.json (+ upload HF Model repo) cho ai-service.

    python scripts/16_export_yolo_onnx.py --weights artifacts/yolo/y1-s/weights/best.pt \
        --thresholds artifacts/eval/y1-s/thresholds.json --version y1 --out artifacts/y1-s \
        --upload <hf-user>/snapget-ai-model

- ONNX opset 12, input (1,3,640,640) float32 RGB/255 letterbox, output (1, 4+12, 8400) — KHONG NMS
  (ai-service lay max theo anchor, snapget12/yolo.py). fp32: KHONG quantize (bai hoc v0: int8 dong
  pha MobileNetV3 — QUEST_AI_PLAN muc 2; YOLO11s fp32 ~36MB van vua Cloud Run 512Mi).
- Tu kiem tra: diem cap anh PyTorch vs ONNX tren anh that (--sample) phai lech < 0.01; do latency CPU.
- model_meta.json co "arch": "yolo" -> ai-service tu chon duong tien xu ly letterbox. Rollback ve v0
  = upload lai 3 file cua v0 (arch mac dinh "classifier").
"""
from __future__ import annotations

import argparse
import json
import shutil
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np  # noqa: E402

from snapget12 import CLASSES  # noqa: E402
from snapget12.yolo import PAD_VALUE, YOLO_INPUT_SIZE, image_scores_from_raw, letterbox, load_rgb  # noqa: E402


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--weights", required=True, help="best.pt (12 lop, THU TU CLASSES)")
    ap.add_argument("--thresholds", required=True, help="thresholds.json tu 14_eval_yolo.py (chon tren real-calib)")
    ap.add_argument("--version", default="y1")
    ap.add_argument("--out", required=True)
    ap.add_argument("--sample", help="1 anh that de so khop PyTorch vs ONNX (mac dinh: anh mau Ultralytics)")
    ap.add_argument("--upload", help="HF Model repo, vd user/snapget-ai-model (can token write)")
    args = ap.parse_args()

    import torch
    from ultralytics import YOLO

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    y = YOLO(args.weights)
    names = [y.model.names[i] for i in range(len(y.model.names))]
    if names != CLASSES:
        raise SystemExit(f"!! thu tu lop {names} khac CLASSES — khong export")
    thr = json.loads(Path(args.thresholds).read_text(encoding="utf-8"))
    if set(thr) != set(CLASSES):
        raise SystemExit(f"!! thresholds.json thieu/thua lop: {set(CLASSES) ^ set(thr)}")

    exported = Path(y.export(format="onnx", imgsz=YOLO_INPUT_SIZE, opset=12, simplify=True, dynamic=False,
                             nms=False, half=False, device="cpu"))
    serve = out / "model.onnx"
    shutil.copyfile(exported, serve)
    shutil.copyfile(args.thresholds, out / "thresholds.json")

    # So khop PyTorch vs ONNX tren anh that
    import onnxruntime as ort

    if args.sample:
        sample = Path(args.sample)
    else:
        from ultralytics.utils import ASSETS

        sample = ASSETS / "bus.jpg"
    x, _ = letterbox(load_rgb(sample))
    model = YOLO(args.weights).model.float().eval()
    with torch.no_grad():
        ref = model(torch.from_numpy(x))
    ref = (ref[0] if isinstance(ref, (list, tuple)) else ref).numpy()
    sess = ort.InferenceSession(str(serve), providers=["CPUExecutionProvider"])
    inp = sess.get_inputs()[0]
    got = sess.run(None, {inp.name: x})[0]
    if got.shape != (1, 4 + len(CLASSES), got.shape[2]):
        raise SystemExit(f"!! output ONNX {got.shape} khong dung dang (1, {4 + len(CLASSES)}, A) — co dang export kem NMS?")
    diff = float(np.abs(image_scores_from_raw(ref) - image_scores_from_raw(got)).max())
    print(f"max |diem cap anh PyTorch - ONNX| = {diff:.5f}  (anh {sample.name})")
    if diff > 0.01:
        raise SystemExit("!! lech > 0.01 — ONNX khong khop PyTorch, KHONG upload")

    so = ort.SessionOptions()
    so.intra_op_num_threads = 2  # giong ai-service tren Cloud Run 1 vCPU
    sess2 = ort.InferenceSession(str(serve), so, providers=["CPUExecutionProvider"])
    for _ in range(3):
        sess2.run(None, {inp.name: x})
    t = []
    for _ in range(30):
        t0 = time.perf_counter()
        sess2.run(None, {inp.name: x})
        t.append((time.perf_counter() - t0) * 1000)
    lat = {"p50": round(float(np.percentile(t, 50)), 1), "p95": round(float(np.percentile(t, 95)), 1)}
    print(f"latency onnxruntime CPU (2 thread): p50 {lat['p50']}ms  p95 {lat['p95']}ms")
    if lat["p95"] > 1500:
        print("!! p95 > 1.5s tren may nay — Cloud Run 1 vCPU co the cham hon; can nhac yolo11n (timeout verify 3s)")

    meta = {
        "modelVersion": args.version,
        "arch": "yolo",
        "base": Path(str(y.model.yaml.get("yaml_file", "yolo"))).stem,  # vd yolo11s

        "classes": CLASSES,
        "inputSize": YOLO_INPUT_SIZE,
        "inputName": inp.name,
        "preprocess": "letterbox",
        "padValue": PAD_VALUE,
        "scoreActivation": "none",  # diem lop trong output YOLO DA qua sigmoid
        "imageScore": "max over anchors",
        "quantization": "none (fp32)",
        "maxAbsDiffVsPytorch": round(diff, 5),
        "latencyCpuMs": lat,
        "exportedAt": datetime.now(timezone.utc).isoformat(),
        "sizeBytes": serve.stat().st_size,
    }
    (out / "model_meta.json").write_text(json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"-> {serve} ({serve.stat().st_size / 1e6:.1f} MB), thresholds.json, model_meta.json")

    if args.upload:
        from huggingface_hub import HfApi

        api = HfApi()
        api.create_repo(repo_id=args.upload, repo_type="model", exist_ok=True)
        for f in ("model.onnx", "thresholds.json", "model_meta.json"):
            api.upload_file(path_or_fileobj=str(out / f), path_in_repo=f, repo_id=args.upload, repo_type="model")
            print(f"uploaded {f} -> {args.upload}")
        print("\n>> Xong. Restart AI service tren Cloud Run de nap model moi:")
        print("   gcloud run services update snapget-ai --region asia-southeast1 --update-env-vars MODEL_RELOAD=$(date +%s)")
        print("   kiem tra: GET <Service URL>/health -> modelVersion:\"%s\", verifierReady:true" % args.version)


if __name__ == "__main__":
    main()

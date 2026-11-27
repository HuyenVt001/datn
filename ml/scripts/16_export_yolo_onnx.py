"""
16 — Export YOLO (y1/y2) -> ONNX fp32 + model_meta.json (+ upload HF Model repo) cho ai-service.

    python scripts/16_export_yolo_onnx.py --weights artifacts/y2-s/best.pt \
        --thresholds artifacts/eval/y2-s/thresholds.json --version y2 --out artifacts/y2-s/export \
        --upload <hf-user>/snapget-ai-model

- ONNX opset 12, input (1,3,640,640) float32 RGB/255 letterbox, output (1, 4+so lop, 8400) — KHONG NMS
  (ai-service lay max theo anchor, snapget12/yolo.py). fp32: KHONG quantize (bai hoc v0: int8 dong
  pha MobileNetV3 — QUEST_AI_PLAN muc 2; YOLO11s fp32 ~36MB van vua Cloud Run 512Mi).
- Tu kiem tra: diem cap anh PyTorch vs ONNX tren anh that (--sample) phai lech < 0.01; do latency CPU.
- model_meta.json co "arch": "yolo" -> ai-service tu chon duong tien xu ly letterbox. Rollback ve v0
  = upload lai 3 file cua v0 (arch mac dinh "classifier").

--task person (2026-11-27, quest "Snap a photo with your friend" = >= 2 nguoi): model PHU, KHONG train —
YOLO11n COCO goc cat head con lop person -> output (1, 5, 8400); diem cap anh = do tin cay cua nguoi THU 2
sau NMS ("imageScore": "nth:2"). Upload vao THU MUC CON person/ cua cung HF repo; ai-service tu nap.

    python scripts/16_export_yolo_onnx.py --task person --thresholds artifacts/quest_eval/person/thresholds.json         --out artifacts/person --upload <hf-user>/snapget-ai-model
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
from snapget12.yolo import (  # noqa: E402
    COCO_PERSON_ID,
    PAD_VALUE,
    YOLO_INPUT_SIZE,
    image_scores_from_raw,
    letterbox,
    load_rgb,
    nth_score_from_raw,
    slice_coco_head,
)

PERSON_MIN_COUNT = 2  # quest person: >= 2 nguoi trong khung
PERSON_NMS_IOU = 0.5
PERSON_DEFAULT_THRESHOLD = 0.4  # chi dung khi chua co thresholds.json tu 17_eval_quests.py


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--task", choices=["main", "person"], default="main",
                    help="main = model chinh (lop CLASSES) | person = model phu dem nguoi (YOLO11n COCO goc, khong train)")
    ap.add_argument("--weights", help="main: best.pt (THU TU CLASSES) | person: mac dinh yolo11n.pt")
    ap.add_argument("--thresholds", help="main: thresholds.json tu 14 (BAT BUOC) | person: tu 17_eval_quests.py")
    ap.add_argument("--version", help="mac dinh y2 (main, 9 lop) / person-y11n (person)")
    ap.add_argument("--out", required=True)
    ap.add_argument("--sample", help="1 anh that de so khop PyTorch vs ONNX (mac dinh: anh mau Ultralytics)")
    ap.add_argument("--upload", help="HF Model repo, vd user/snapget-ai-model (can token write)")
    args = ap.parse_args()

    import torch
    from ultralytics import YOLO

    person = args.task == "person"
    version = args.version or ("person-y11n" if person else "y2")
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    if person:
        expected = ["person"]
        weights = args.weights or "yolo11n.pt"
        y = YOLO(weights)
        if len(y.model.names) != 80:
            raise SystemExit(f"!! {weights} khong phai model 80 lop COCO")
        slice_coco_head(y, ids=[COCO_PERSON_ID], names=expected)
        weights = str(out / f"{Path(weights).stem}-person.pt")
        y.save(weights)
        y = YOLO(weights)
    else:
        expected = CLASSES
        if not args.weights or not args.thresholds:
            raise SystemExit("!! --task main can --weights va --thresholds")
        weights = args.weights
        y = YOLO(weights)
    names = [y.model.names[i] for i in range(len(y.model.names))]
    if names != expected:
        raise SystemExit(f"!! thu tu lop {names} khac {expected} — khong export")
    if args.thresholds:
        thr = json.loads(Path(args.thresholds).read_text(encoding="utf-8"))
    else:
        thr = {"person": PERSON_DEFAULT_THRESHOLD}
        print(f"!! chua co --thresholds -> tam dung nguong person = {PERSON_DEFAULT_THRESHOLD} (chay 17_eval_quests.py de chon)")
    if set(thr) != set(expected):
        raise SystemExit(f"!! thresholds.json thieu/thua lop: {set(expected) ^ set(thr)}")

    exported = Path(y.export(format="onnx", imgsz=YOLO_INPUT_SIZE, opset=12, simplify=True, dynamic=False,
                             nms=False, half=False, device="cpu"))
    serve = out / "model.onnx"
    shutil.copyfile(exported, serve)
    (out / "thresholds.json").write_text(json.dumps(thr, ensure_ascii=False, indent=2), encoding="utf-8")

    # So khop PyTorch vs ONNX tren anh that
    import onnxruntime as ort

    if args.sample:
        sample = Path(args.sample)
    else:
        from ultralytics.utils import ASSETS

        sample = ASSETS / "bus.jpg"
    x, _ = letterbox(load_rgb(sample))
    model = YOLO(weights).model.float().eval()
    with torch.no_grad():
        ref = model(torch.from_numpy(x))
    ref = (ref[0] if isinstance(ref, (list, tuple)) else ref).numpy()
    sess = ort.InferenceSession(str(serve), providers=["CPUExecutionProvider"])
    inp = sess.get_inputs()[0]
    got = sess.run(None, {inp.name: x})[0]
    nc = len(expected)
    if got.shape != (1, 4 + nc, got.shape[2]):
        raise SystemExit(f"!! output ONNX {got.shape} khong dung dang (1, {4 + nc}, A) — co dang export kem NMS?")
    diff = float(np.abs(image_scores_from_raw(ref, nc) - image_scores_from_raw(got, nc)).max())
    if person:
        nth = [nth_score_from_raw(r, 0, PERSON_MIN_COUNT, PERSON_NMS_IOU)[0] for r in (ref, got)]
        diff = max(diff, abs(float(nth[0] - nth[1])))
        print(f"diem '>= {PERSON_MIN_COUNT} nguoi' PyTorch {nth[0]:.4f} / ONNX {nth[1]:.4f}")
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
        "modelVersion": version,
        "arch": "yolo",
        "base": Path(str(y.model.yaml.get("yaml_file", "yolo"))).stem,  # vd yolo11s
        "classes": expected,
        "inputSize": YOLO_INPUT_SIZE,
        "inputName": inp.name,
        "preprocess": "letterbox",
        "padValue": PAD_VALUE,
        "scoreActivation": "none",  # diem lop trong output YOLO DA qua sigmoid
        # main: max theo anchor | person: do tin cay nguoi thu 2 sau NMS (ai-service doc truong nay)
        "imageScore": f"nth:{PERSON_MIN_COUNT}" if person else "max over anchors",
        **({"nmsIou": PERSON_NMS_IOU} if person else {}),
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
        prefix = "person/" if person else ""  # model phu nam o thu muc con, ai-service tu nap
        for f in ("model.onnx", "thresholds.json", "model_meta.json"):
            api.upload_file(path_or_fileobj=str(out / f), path_in_repo=prefix + f, repo_id=args.upload,
                            repo_type="model")
            print(f"uploaded {prefix}{f} -> {args.upload}")
        print("\n>> Xong. Restart AI service tren Cloud Run de nap model moi:")
        print("   gcloud run services update snapget-ai --region asia-southeast1 --update-env-vars MODEL_RELOAD=$(date +%s)")
        print("   kiem tra: GET <Service URL>/health -> %s" % (
            f'extraModels.person = "{version}"' if person else f'modelVersion:"{version}", verifierReady:true'))


if __name__ == "__main__":
    main()

"""
05 — Export backbone+head -> ONNX -> quantize int8 + model_meta.json (QUEST_AI_PLAN muc 4.2).

    # v0 (head tu 03, backbone pretrained nguyen ban)
    python scripts/05_export_onnx.py --head artifacts/v0/head.pt --version v0 --out artifacts/v0
    # v1 (ca model tu 06)
    python scripts/05_export_onnx.py --full artifacts/v1/model.pt --version v1 --out artifacts/v1

Ra: <out>/model.onnx (int8, ~2.5MB), <out>/model_fp32.onnx, <out>/model_meta.json.
Kiem tra: so sanh output PyTorch vs ONNX int8 tren input ngau nhien (in ra max |diff|).
Kem `--upload <user>/snapget-ai-model` de day 3 file (model.onnx, thresholds.json,
model_meta.json) len **HF MODEL repo** — repo model VAN MIEN PHI (chi Space moi thu phi);
AI service tren Cloud Run doc env MODEL_REPO va tu tai 3 file nay luc khoi dong.
Can dang nhap HF truoc (`login()` trong notebook / `huggingface-cli login`), token quyen write.
"""
from __future__ import annotations

import argparse
import json
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np  # noqa: E402
import torch  # noqa: E402

from snapget12 import CLASSES, IMAGENET_MEAN, IMAGENET_STD, INPUT_SIZE, Head, build_model  # noqa: E402


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--head", help="checkpoint head (v0)")
    ap.add_argument("--full", help="checkpoint ca model (v1)")
    ap.add_argument("--version", required=True, help="v0 | v1 ... (ghi vao model_meta.json)")
    ap.add_argument("--out", required=True)
    ap.add_argument("--upload", help="HF Model repo, vd user/snapget-ai-model (can token write)")
    args = ap.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    if args.full:
        ckpt = torch.load(args.full, map_location="cpu")
        model = build_model(pretrained=False)
        model.head = Head(hidden=ckpt.get("hidden", 256), dropout=ckpt.get("dropout", 0.2))
        model.load_state_dict(ckpt["state_dict"])
    elif args.head:
        ckpt = torch.load(args.head, map_location="cpu")
        model = build_model(pretrained=True)
        model.head = Head(hidden=ckpt.get("hidden", 256), dropout=ckpt.get("dropout", 0.2))
        model.head.load_state_dict(ckpt["state_dict"])
    else:
        raise SystemExit("can --head hoac --full")
    model.eval()

    dummy = torch.randn(1, 3, INPUT_SIZE, INPUT_SIZE)
    fp32_path = out / "model_fp32.onnx"
    export_kwargs = dict(
        input_names=["input"], output_names=["logits"],
        opset_version=17, dynamic_axes={"input": {0: "batch"}, "logits": {0: "batch"}},
    )
    # torch >= 2.9 mac dinh dung exporter "dynamo" (can them goi onnxscript). Dung exporter
    # TorchScript cu (dynamo=False) — on dinh voi MobileNet + quantize_dynamic. torch cu hon
    # khong co tham so `dynamo` -> TypeError -> goi lai khong kem.
    try:
        torch.onnx.export(model, dummy, str(fp32_path), dynamo=False, **export_kwargs)
    except TypeError:
        torch.onnx.export(model, dummy, str(fp32_path), **export_kwargs)

    from onnxruntime.quantization import QuantType, quantize_dynamic

    int8_path = out / "model.onnx"
    quantize_dynamic(str(fp32_path), str(int8_path), weight_type=QuantType.QUInt8)

    # Kiem tra sai lech + do latency CPU
    import onnxruntime as ort

    sess = ort.InferenceSession(str(int8_path), providers=["CPUExecutionProvider"])
    with torch.no_grad():
        ref = model(dummy).numpy()
    got = sess.run(None, {"input": dummy.numpy()})[0]
    print(f"max |pytorch - onnx_int8| = {np.abs(ref - got).max():.4f}")
    times = []
    for _ in range(20):
        t0 = time.perf_counter()
        sess.run(None, {"input": dummy.numpy()})
        times.append((time.perf_counter() - t0) * 1000)
    print(f"latency int8 CPU: p50 {np.percentile(times, 50):.1f}ms  p95 {np.percentile(times, 95):.1f}ms")

    meta = {
        "modelVersion": args.version,
        "classes": CLASSES,
        "inputSize": INPUT_SIZE,
        "mean": IMAGENET_MEAN,
        "std": IMAGENET_STD,
        "backbone": "mobilenet_v3_small (ImageNet1K_V1)",
        "head": "Linear(576->256)-ReLU-Dropout(0.2)-Linear(256->12)",
        "quantization": "dynamic int8 (onnxruntime)",
        "exportedAt": datetime.now(timezone.utc).isoformat(),
        "sizeBytes": int8_path.stat().st_size,
    }
    (out / "model_meta.json").write_text(json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"-> {int8_path} ({int8_path.stat().st_size / 1e6:.2f} MB), model_meta.json")

    if args.upload:
        from huggingface_hub import HfApi

        api = HfApi()
        # Tao repo neu chua co (mien phi, public de service tai khong can token)
        api.create_repo(repo_id=args.upload, repo_type="model", exist_ok=True)
        for f in ("model.onnx", "thresholds.json", "model_meta.json"):
            p = out / f
            if not p.exists():
                print(f"!! thieu {p} — chay 04_eval_thresholds.py truoc")
                continue
            api.upload_file(path_or_fileobj=str(p), path_in_repo=f, repo_id=args.upload, repo_type="model")
            print(f"uploaded {f} -> {args.upload}")
        print("\n>> Xong. Restart AI service tren Cloud Run de nap model moi:")
        print("   gcloud run services update snapget-ai --region asia-southeast1 --update-env-vars MODEL_RELOAD=$(date +%s)")


if __name__ == "__main__":
    main()

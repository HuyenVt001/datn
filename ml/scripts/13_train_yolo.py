"""
13 — Fine-tune YOLO11 (y1) tren dataset 12 lop (data.yaml tu 11_merge_external.py) — Google Colab T4.

    # phien dau
    python scripts/13_train_yolo.py --data /content/yolo/data.yaml --model yolo11s.pt \
        --project /content/drive/MyDrive/snapget-ml/artifacts/yolo --name y1-s --epochs 40
    # Colab ngat giua chung -> mo lai notebook, giai nen dataset, chay LAI DUNG lenh tren + --resume
    python scripts/13_train_yolo.py ... --name y1-s --resume

KHOI TAO (--init, mac dinh coco12): cat head 80 lop COCO -> 12 lop (snapget12/yolo.py
`slice_coco_head`) THAY VI de Ultralytics khoi tao ngau nhien lop phan loai khi nc doi 80 -> 12.
Model bat dau tu dung diem cua YOLO11 COCO tren 12 lop (da kha tot) -> hoi tu nhanh hon nhieu,
quan trong khi Colab free chi ~3–5 tieng/phien. `--init scratch-head` de lam ablation.

Colab:
  - project tro vao DRIVE -> weights/last.pt luu moi epoch, mat ket noi khong mat tien do.
  - Dataset PHAI nam tren /content (dia local) — doc anh tu Drive cham gap ~10 lan.
  - workers=2 (Colab co 2 vCPU). batch=-1 = Ultralytics tu chon theo VRAM (T4 16GB: ~32 voi 11s).
Ra: <project>/<name>/weights/{best,last}.pt + results.csv + bieu do; copy best.pt -> --export-best.
"""
from __future__ import annotations

import argparse
import shutil
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from snapget12 import CLASSES  # noqa: E402
from snapget12.yolo import slice_coco_head  # noqa: E402


def keep_head_trainer():
    """
    Trainer dung NGUYEN model khoi tao thay vi dung lai tu yaml. Ly do (do tren yolo11n, 2026-11-23):
    trong Detect cua YOLO11 do rong nhanh phan loai c3 = max(ch0, min(nc, 100)) PHU THUOC nc —
    yolo11n (ch0=64) nc 80 -> 12 thi c3 80 -> 64 => Ultralytics dung lai head moi va BO 48 tensor cv3
    ("Transferred 451/499"), mat het kien thuc COCO cua nhanh phan loai. yolo11s/m (ch0 >= 128) khong
    bi nhung van dung chung trainer nay cho thong nhat. Dung ca khi --resume (get_model duoc goi lai).
    """
    import copy

    from ultralytics.models.yolo.detect import DetectionTrainer
    from ultralytics.nn.tasks import DetectionModel

    class KeepHeadTrainer(DetectionTrainer):
        def get_model(self, cfg=None, weights=None, verbose=True):
            if isinstance(weights, DetectionModel) and len(weights.names) == self.data["nc"]:
                m = copy.deepcopy(weights).float()
                for p in m.parameters():
                    p.requires_grad = True
                return m
            return super().get_model(cfg=cfg, weights=weights, verbose=verbose)

    return KeepHeadTrainer


def build_init(model: str, init: str, cache_dir: Path) -> str:
    """Tra duong dan weights dung de bat dau train."""
    if init == "scratch-head" or not Path(model).stem.startswith("yolo11"):
        return model
    from ultralytics import YOLO

    out = cache_dir / f"{Path(model).stem}-coco12.pt"
    if out.exists():
        return str(out)
    y = YOLO(model)
    if len(y.model.names) != 80:
        print(f"!! {model} khong phai model 80 lop COCO -> bo qua cat head, train thang")
        return model
    slice_coco_head(y)
    out.parent.mkdir(parents=True, exist_ok=True)
    y.save(str(out))
    print(f"Khoi tao tu head COCO da cat 12 lop -> {out}")
    return str(out)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", default="/content/yolo/data.yaml")
    ap.add_argument("--model", default="yolo11s.pt", help="yolo11n.pt | yolo11s.pt (chinh) | yolo11m.pt (nang)")
    ap.add_argument("--init", choices=["coco12", "scratch-head"], default="coco12")
    ap.add_argument("--project", required=True, help="thu muc tren DRIVE")
    ap.add_argument("--name", default="y1-s")
    ap.add_argument("--epochs", type=int, default=40)
    ap.add_argument("--imgsz", type=int, default=640)
    ap.add_argument("--batch", type=int, default=-1)
    ap.add_argument("--workers", type=int, default=2)
    ap.add_argument("--patience", type=int, default=12)
    ap.add_argument("--fraction", type=float, default=1.0, help="dung 1 phan train set (thu nhanh)")
    ap.add_argument("--close-mosaic", type=int, default=8)
    ap.add_argument("--resume", action="store_true")
    ap.add_argument("--export-best", default=None, help="copy best.pt toi day sau khi train (vd artifacts/y1-s/best.pt)")
    ap.add_argument("--seed", type=int, default=42)
    args = ap.parse_args()

    from ultralytics import YOLO

    run_dir = Path(args.project) / args.name
    last = run_dir / "weights" / "last.pt"
    if args.resume:
        if not last.exists():
            raise SystemExit(f"!! khong co {last} de resume — bo --resume de train moi")
        print(f"Resume tu {last}")
        YOLO(str(last)).train(resume=True, trainer=keep_head_trainer())
    else:
        if last.exists():
            raise SystemExit(f"!! {run_dir} da co lan train truoc. Dung --resume de chay tiep, hoac doi --name.")
        weights = build_init(args.model, args.init, Path(args.project) / "_init")
        YOLO(weights).train(
            trainer=keep_head_trainer() if args.init == "coco12" else None,
            data=args.data, epochs=args.epochs, imgsz=args.imgsz, batch=args.batch, workers=args.workers,
            patience=args.patience, project=args.project, name=args.name, exist_ok=False, seed=args.seed,
            fraction=args.fraction, close_mosaic=args.close_mosaic,
            # Augment: mac dinh Ultralytics (mosaic, HSV, scale 0.5, flip ngang) + nhe tay hon ve mau
            # vi anh that la anh dien thoai (den vang/toi) — da co ban cat doc 3:4 tu buoc 10.
            hsv_h=0.015, hsv_s=0.6, hsv_v=0.45, fliplr=0.5, degrees=0.0, mixup=0.0,
            cos_lr=True, plots=True, save_period=-1, cache=False, amp=True,
        )

    best = run_dir / "weights" / "best.pt"
    if not best.exists():
        raise SystemExit(f"!! khong thay {best}")
    y = YOLO(str(best))
    names = [y.model.names[i] for i in range(len(y.model.names))]
    if names != CLASSES:
        raise SystemExit(f"!! thu tu lop cua model {names} KHAC CLASSES {CLASSES} — kiem tra data.yaml")
    if args.export_best:
        Path(args.export_best).parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(best, args.export_best)
        print(f"best.pt -> {args.export_best}")
    print(f"Xong: {best}. Tiep: 14_eval_yolo.py")


if __name__ == "__main__":
    main()

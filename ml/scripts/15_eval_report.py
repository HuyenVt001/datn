"""
15 — BANG DANH GIA TUNG LOP cua 1 model tu ket qua 14_eval_yolo.py (dua vao bao cao):

    python scripts/15_eval_report.py --eval artifacts/eval/y2-s --split real_test

Doc metrics_<split>.json (+ det_<split>.json neu co). Moi lop: so anh test, nguong (chon tren real_calib),
AP cap anh, F1 / precision / recall tai nguong, ti le tu choi sai, AP50 va AP50-95 detection,
lop bi nhan nham nhieu nhat (confusion cap anh, 2 chieu). Cuoi bang: chi so tong cua model.
Danh gia THEO QUEST (gop nhom) la viec cua 17_eval_quests.py.

Ra: <eval>/eval_report.json + eval_report.md
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from snapget12.metrics import class_report, save_json  # noqa: E402


def fmt(v, pct=False):
    if v is None:
        return "–"
    return f"{v:.0%}" if pct else f"{v:.3f}"


def who(name, rate):
    return f"{name} ({fmt(rate, True)})" if rate else "–"


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--eval", required=True, help="thu muc ket qua 1 model cua 14_eval_yolo.py")
    ap.add_argument("--split", default="real_test")
    args = ap.parse_args()

    d = Path(args.eval)
    mp = d / f"metrics_{args.split}.json"
    if not mp.exists():
        raise SystemExit(f"!! thieu {mp} — chay 14_eval_yolo.py truoc")
    rep = json.loads(mp.read_text(encoding="utf-8"))
    if rep.get("calibratedOn") != "real_calib":
        print(f"!! Nguong duoc chon tren '{rep.get('calibratedOn')}', khong phai real_calib — so lieu chi mang tinh tam thoi.")
    det_path = d / f"det_{args.split}.json"
    det = json.loads(det_path.read_text(encoding="utf-8")) if det_path.exists() else {}
    rows = class_report(rep, rep["imageConfusion"], det.get("perClass"))
    summary = {"model": d.name, "split": args.split, "calibratedOn": rep.get("calibratedOn"), "n": rep.get("n"),
               "imageMAP": rep.get("mAP"), "macroF1": rep.get("macroF1"),
               "detMAP50": det.get("mAP50"), "detMAP50_95": det.get("mAP50_95")}
    save_json({**summary, "perClass": rows}, d / "eval_report.json")

    lines = [
        f"# Đánh giá từng lớp — model `{d.name}`, tập `{args.split}` (ngưỡng chọn trên `{rep.get('calibratedOn')}`)",
        "",
        "| Lớp | Ảnh test | Ngưỡng | AP ảnh | F1 | P | R | Từ chối sai | AP50 box | AP50-95 box | Bị nhận nhầm từ | Hay bị nhận thành |",
        "|---|---|---|---|---|---|---|---|---|---|---|---|",
    ]
    for c, r in rows.items():
        lines.append(
            f"| {c} | {r['support']} | {fmt(r['threshold'])} | {fmt(r['imageAP'])} | {fmt(r['f1'])} | {fmt(r['precision'])} | "
            f"{fmt(r['recall'])} | {fmt(r['falseRejectRate'], True)} | {fmt(r['detAP50'])} | {fmt(r['detAP50_95'])} | "
            f"{who(r['confusedBy'], r['confusedByRate'])} | {who(r['mistakenFor'], r['mistakenForRate'])} |")
    lines += [
        "",
        f"**Tổng ({summary['n']} ảnh):** mAP ảnh {fmt(summary['imageMAP'])} · macro-F1 {fmt(summary['macroF1'])} · "
        f"mAP50 box {fmt(summary['detMAP50'])} · mAP50-95 box {fmt(summary['detMAP50_95'])}",
        "",
        "- *Bị nhận nhầm từ*: lớp khác (hoặc ảnh không có vật nào — `negative`) bị nhận là lớp này nhiều nhất → nguồn nhận nhầm khi quest là lớp này.",
        "- *Hay bị nhận thành*: ảnh có lớp này hay bị nhận thành lớp nào nhất.",
    ]
    (d / "eval_report.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("\n".join(lines))
    print(f"\nOK -> {d}/eval_report.json, eval_report.md")


if __name__ == "__main__":
    main()

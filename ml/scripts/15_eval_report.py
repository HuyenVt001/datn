"""
15 — CHON LOP RA DE QUEST tu so lieu anh that (QUEST_AI_PLAN.md muc 5.3):

    python scripts/15_select_classes.py --eval artifacts/eval/y1-s --split real_test

Doc metrics_<split>.json (+ det_<split>.json neu co) cua 1 model do 14_eval_yolo.py sinh, ap tieu chi
(snapget12/metrics.py DEFAULT_SELECTION_CRITERIA, sua bang --criteria '{"minF1":0.8}'):
    AP cap anh >= 0.85 · F1 tai nguong >= 0.85 · detection AP50 >= 0.75 · false-reject <= 15%
    · khong lop nao khac / anh negative bi nhan nham la lop nay > 5% · >= 20 anh test
< 6 lop dat -> lay top 6 theo F1 (danh dau fallbackUsed — PHAI ghi ro trong bao cao).

Ra: <eval>/class_selection.json + class_selection.md (bang cho bao cao) + doan code TS cho server
(`AI_QUEST_CLASSES` trong server/src/common/constants.ts). Lop moi vao vong quay ma chua co cau mau
(book/backpack/keyboard) -> phai viet them 8 cau trong server/src/quests/entities/ai-quest-templates.ts.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from snapget12 import CLASSES  # noqa: E402
from snapget12.metrics import save_json, select_quest_classes  # noqa: E402

CURRENT_QUEST_CLASSES = ["cup", "bottle", "chair", "potted plant", "laptop", "clock", "umbrella", "bicycle", "motorcycle"]
CHECK_LABELS = {"imageAP": "AP ảnh", "f1": "F1", "detAP50": "AP50 box", "falseReject": "Từ chối sai",
                "confusedBy": "Bị nhầm", "support": "Đủ ảnh"}


def fmt(v, pct=False):
    if v is None:
        return "–"
    return f"{v:.0%}" if pct else f"{v:.3f}"


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--eval", required=True, help="thu muc ket qua 1 model cua 14_eval_yolo.py")
    ap.add_argument("--split", default="real_test")
    ap.add_argument("--criteria", default="{}", help='JSON ghi de tieu chi, vd \'{"minF1": 0.8}\'')
    args = ap.parse_args()

    d = Path(args.eval)
    mp = d / f"metrics_{args.split}.json"
    if not mp.exists():
        raise SystemExit(f"!! thieu {mp} — chay 14_eval_yolo.py truoc")
    rep = json.loads(mp.read_text(encoding="utf-8"))
    if args.split != "real_test":
        print(f"!! Dang chon lop tren '{args.split}' — tieu chi duoc thiet ke cho ANH THAT (real_test).")
    if rep.get("calibratedOn") != "real_calib":
        print(f"!! Nguong duoc chon tren '{rep.get('calibratedOn')}', khong phai real_calib — ket qua chi mang tinh tam thoi.")
    det_path = d / f"det_{args.split}.json"
    det = json.loads(det_path.read_text(encoding="utf-8"))["perClass"] if det_path.exists() else None
    sel = select_quest_classes(rep, rep["imageConfusion"], det, json.loads(args.criteria))
    sel.update({"model": d.name, "split": args.split, "calibratedOn": rep.get("calibratedOn"),
                "added": [c for c in sel["selected"] if c not in CURRENT_QUEST_CLASSES],
                "removed": [c for c in CURRENT_QUEST_CLASSES if c not in sel["selected"]]})
    save_json(sel, d / "class_selection.json")

    crit = sel["criteria"]
    lines = [
        f"# Chọn lớp ra đề quest — model `{d.name}`, tập `{args.split}` (ngưỡng chọn trên `{rep.get('calibratedOn')}`)",
        "",
        f"Tiêu chí: AP ảnh ≥ {crit['minImageAP']} · F1 ≥ {crit['minF1']} · AP50 box ≥ {crit['minDetAP50']} · "
        f"từ chối sai ≤ {crit['maxFalseReject']:.0%} · bị nhầm ≤ {crit['maxConfusedBy']:.0%} · ≥ {crit['minSupport']} ảnh test.",
        "",
        "| Lớp | Ảnh test | Ngưỡng | AP ảnh | F1 | P | R | Từ chối sai | AP50 box | Bị nhầm nhiều nhất bởi | Kết quả |",
        "|---|---|---|---|---|---|---|---|---|---|---|",
    ]
    for c in CLASSES:
        r = sel["perClass"][c]
        failed = [CHECK_LABELS[k] for k, v in r["checks"].items() if v is False]
        worst = f"{r['worstConfusedBy']} ({fmt(r['worstConfusedRate'], True)})" if r["worstConfusedRate"] else "–"
        verdict = "✅ RA ĐỀ" if c in sel["selected"] else ("❌ " + ", ".join(failed) if failed else "❌ (ngoài top fallback)")
        lines.append(f"| {c} | {r['support']} | {fmt(r['threshold'])} | {fmt(r['imageAP'])} | {fmt(r['f1'])} | {fmt(r['precision'])} | "
                     f"{fmt(r['recall'])} | {fmt(r['falseRejectRate'], True)} | {fmt(r['detAP50'])} | {worst} | {verdict} |")
    lines += ["", f"**Ra đề ({len(sel['selected'])} lớp):** {', '.join(sel['selected'])}"]
    if sel["fallbackUsed"]:
        lines.append(f"\n> ⚠️ Chỉ {sum(r['pass'] for r in sel['perClass'].values())} lớp đạt đủ tiêu chí → lấy top {crit['minClasses']} theo F1 (ghi rõ trong báo cáo).")
    if sel["added"] or sel["removed"]:
        lines.append(f"\nSo với vòng quay hiện tại: thêm {sel['added'] or '–'} · bỏ {sel['removed'] or '–'}")
    (d / "class_selection.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("\n".join(lines))

    ts = ",\n".join(f"  '{c}'" for c in sel["selected"])
    print(f"\n// server/src/common/constants.ts\nexport const AI_QUEST_CLASSES = [\n{ts},\n] as const;")
    if sel["added"]:
        print(f"\n!! Lop moi vao vong quay {sel['added']}: viet them 8 cau/lop trong server/src/quests/entities/ai-quest-templates.ts")
    print(f"\nOK -> {d}/class_selection.json, class_selection.md")


if __name__ == "__main__":
    main()

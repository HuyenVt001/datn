"""
17 — Danh gia THEO QUEST (7 quest gop nhom, user chot 2026-11-27) + chon nguong cho model phu person.

    python scripts/17_eval_quests.py --eval artifacts/eval/y1-s --quest-test /content/quest_test \
        --person-weights yolo11n.pt --out artifacts/quest_eval

1. 6 quest cua y1 (bicycle|motorcycle, laptop|keyboard, umbrella, chair, cup|bottle, book):
   dung LAI scores_<split>.npz + thresholds.json ma 14_eval_yolo.py da luu — KHONG chay lai model.
   Diem quest = max_c(score_c / thr_c) tren cac lop trong nhom -> pass khi >= 1 (snapget12.metrics.group_scores)
   = dung quyet dinh cua ai-service. Anh duong = anh co BAT KY lop nao trong nhom.
2. Quest person (>= 2 nguoi): YOLO11n COCO goc (khong train), diem = do tin cay nguoi THU 2 sau NMS
   (snapget12.yolo.nth_score_from_raw). Tap anh tu chup, nhan theo THU MUC:
       quest_test/person/  >= 2 nguoi (selfie nhom, chup xa/gan, anh co-op)
       quest_test/other/   < 2 nguoi (selfie 1 minh, anh 1 nguoi, anh khong nguoi)
   Chia calib 40% / test 60% (seed co dinh, stratify theo thu muc) -> nguong chon tren calib
   (precision >= --min-precision, toi da recall — cung quy tac 14) -> person/thresholds.json cho 16 --task person.
Ra: <out>/quest_metrics.json, quest_metrics.md (bang 7 quest cho bao cao), person/thresholds.json.
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np  # noqa: E402

from snapget12.classes import CLASSES, EXTRA_CLASSES, QUEST_GROUPS  # noqa: E402
from snapget12.metrics import MIN_PRECISION_REAL, choose_thresholds, evaluate, group_scores, save_json  # noqa: E402
from snapget12.yolo import COCO_PERSON_ID, letterbox, list_images, load_rgb, nth_score_from_raw  # noqa: E402

PERSON_MIN_COUNT = 2
PERSON_NMS_IOU = 0.5  # KHOP 16_export_yolo_onnx.py (model_meta.json "nmsIou") va ai-service


def load_scores(eval_dir: Path, split: str) -> tuple[np.ndarray, np.ndarray] | None:
    f = eval_dir / f"scores_{split}.npz"
    if not f.exists():
        return None
    z = np.load(f, allow_pickle=False)
    return z["labels"].astype(int), z["scores"].astype(np.float64)


def rates(rep_pc: dict, n: int) -> dict:
    """Them ti le 'nhan nham' = anh AM bi cho qua / tong anh am."""
    neg = n - rep_pc["support"]
    return {**rep_pc, "falseAcceptRate": round(rep_pc["fp"] / neg, 4) if neg > 0 else None}


def eval_y1_groups(eval_dir: Path, split: str) -> dict | None:
    import json

    loaded = load_scores(eval_dir, split)
    thr_path = eval_dir / "thresholds.json"
    if loaded is None or not thr_path.exists():
        return None
    y, s = loaded
    thr = json.loads(thr_path.read_text(encoding="utf-8"))
    groups = {k: v for k, v in QUEST_GROUPS.items() if all(c in CLASSES for c in v)}
    yg, sg = group_scores(y, s, thr, groups, CLASSES)
    rep = evaluate(yg, sg, {k: 1.0 for k in groups}, list(groups))
    single = evaluate(y, s, thr, CLASSES)  # so lieu TUNG LOP (truoc khi gop) de so sanh trong bao cao
    out = {}
    for k, members in groups.items():
        pc = rates(rep["perClass"][k], rep["n"])
        pc.pop("threshold", None)
        pc["classes"] = members
        pc["thresholds"] = {c: thr[c] for c in members}
        pc["perClassFalseReject"] = {c: single["perClass"][c]["falseRejectRate"] for c in members}
        out[k] = pc
    return {"split": split, "n": rep["n"], "quests": out}


class PersonScorer:
    """Diem '>= 2 nguoi' cho 1 anh. .pt (torch, model 80 lop hoac da cat) hoac .onnx (tu 16 --task person)."""

    def __init__(self, path: str):
        self.onnx = path.endswith(".onnx")
        if self.onnx:
            import onnxruntime as ort

            self.sess = ort.InferenceSession(path, providers=["CPUExecutionProvider"])
            self.name = self.sess.get_inputs()[0].name
        else:
            import torch
            from ultralytics import YOLO

            y = YOLO(path)
            names = [y.model.names[i] for i in range(len(y.model.names))]
            if names[COCO_PERSON_ID] != "person":
                raise SystemExit(f"!! {path}: lop {COCO_PERSON_ID} la {names[COCO_PERSON_ID]}, khong phai person")
            self.torch = torch
            self.device = "cuda" if torch.cuda.is_available() else "cpu"
            self.model = y.model.to(self.device).float().eval()

    def __call__(self, paths: list[Path]) -> np.ndarray:
        out = []
        for p in paths:
            x = letterbox(load_rgb(p))[0]
            if self.onnx:
                raw = self.sess.run(None, {self.name: x})[0]
            else:
                with self.torch.no_grad():
                    raw = self.model(self.torch.from_numpy(x).to(self.device))
                raw = (raw[0] if isinstance(raw, (list, tuple)) else raw).float().cpu().numpy()
            # lop person = kenh 4 + 0 ca o model 80 lop COCO lan model da cat con 1 lop
            out.append(nth_score_from_raw(raw, COCO_PERSON_ID, PERSON_MIN_COUNT, PERSON_NMS_IOU)[0])
        return np.array(out, dtype=np.float64)


def split_calib_test(paths: list[Path], frac: float, seed: int) -> tuple[list[Path], list[Path]]:
    rng = np.random.default_rng(seed)
    idx = rng.permutation(len(paths))
    k = int(round(len(paths) * frac))
    return [paths[i] for i in sorted(idx[:k])], [paths[i] for i in sorted(idx[k:])]


def eval_person(quest_test: Path, weights: str, min_precision: float, calib_frac: float, seed: int) -> dict | None:
    pos, neg = list_images(quest_test / "person"), list_images(quest_test / "other")
    if not pos or not neg:
        print(f"!! {quest_test} thieu person/ hoac other/ -> bo qua quest person")
        return None
    pc, pt = split_calib_test(pos, calib_frac, seed)
    nc, nt = split_calib_test(neg, calib_frac, seed)
    scorer = PersonScorer(weights)
    y_cal = np.array([1] * len(pc) + [0] * len(nc))[:, None]
    s_cal = scorer(pc + nc)[:, None]
    thr = choose_thresholds(y_cal, s_cal, min_precision, ["person"])
    y_te = np.array([1] * len(pt) + [0] * len(nt))[:, None]
    s_te = scorer(pt + nt)[:, None]
    rep = evaluate(y_te, s_te, thr, ["person"])
    q = rates(rep["perClass"]["person"], rep["n"])
    q.pop("threshold", None)
    q.update({"classes": ["person"], "thresholds": thr, "minCount": PERSON_MIN_COUNT, "nmsIou": PERSON_NMS_IOU,
              "calib": {"pos": len(pc), "neg": len(nc)}, "test": {"pos": len(pt), "neg": len(nt)},
              "weights": Path(weights).name})
    return {"split": "quest_test", "n": rep["n"], "thresholds": thr, "quest": q}


def pct(v) -> str:
    return "-" if v is None else f"{v * 100:.0f}%"


def num(v) -> str:
    return "-" if v is None else f"{v:.3f}"


def to_markdown(y1: dict | None, person: dict | None) -> str:
    lines = [
        "# Đánh giá theo quest (7 quest gộp nhóm)",
        "",
        "Quest pass khi **bất kỳ** lớp nào trong nhóm vượt ngưỡng riêng của lớp đó (ngưỡng chọn trên `real_calib`). "
        "Quest `person` = có ≥ 2 người (model phụ YOLO11n COCO gốc, ngưỡng chọn trên phần calib của `quest_test`).",
        "",
        "| Quest | Lớp chấp nhận | Tập | Ảnh dương / tổng | AP | F1 | P | R | Từ chối sai | Từ chối sai trước gộp | Nhận nhầm |",
        "|---|---|---|---|---|---|---|---|---|---|---|",
    ]
    rows = []
    if y1:
        for k, q in y1["quests"].items():
            before = " · ".join(f"{c} {pct(v)}" for c, v in q["perClassFalseReject"].items())
            rows.append((k, q, y1["split"], y1["n"], before))
    if person:
        rows.append(("person", person["quest"], person["split"], person["n"], "-"))
    for k, q, split, n, before in rows:
        accepted = " / ".join(q["classes"]) + (f" (≥ {q['minCount']})" if "minCount" in q else "")
        lines.append(f"| {k} | {accepted} | {split} | {q['support']} / {n} | {num(q['ap'])} | {num(q['f1'])} | "
                     f"{num(q['precision'])} | {num(q['recall'])} | {pct(q['falseRejectRate'])} | {before} | "
                     f"{pct(q['falseAcceptRate'])} |")
    if not person:
        lines += ["", "> Chưa có `quest_test/` → chưa đánh giá quest person (16 --task person dùng ngưỡng tạm)."]
    lines += ["", "- **Từ chối sai** = ảnh có vật thể nhưng bị báo không khớp (chi phí lớn nhất với người dùng).",
              "- **Nhận nhầm** = ảnh không có vật thể nhưng vẫn được cho qua."]
    return "\n".join(lines) + "\n"


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--eval", required=True, help="thu muc 14_eval_yolo.py cua model y1 (vd artifacts/eval/y1-s)")
    ap.add_argument("--split", default="", help="real_test (mac dinh neu co) | coco_test")
    ap.add_argument("--quest-test", help="thu muc anh tu chup co person/ va other/")
    ap.add_argument("--person-weights", default="yolo11n.pt", help=".pt (COCO 80 lop) hoac .onnx tu 16 --task person")
    ap.add_argument("--min-precision", type=float, default=MIN_PRECISION_REAL)
    ap.add_argument("--calib-frac", type=float, default=0.4)
    ap.add_argument("--seed", type=int, default=42)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    assert set(EXTRA_CLASSES) == {c for g in QUEST_GROUPS.values() for c in g if c not in CLASSES}
    eval_dir, out = Path(args.eval), Path(args.out)
    split = args.split or ("real_test" if (eval_dir / "scores_real_test.npz").exists() else "coco_test")
    if split != "real_test":
        print(f"!! chua co scores_real_test.npz -> danh gia tam tren {split}")
    y1 = eval_y1_groups(eval_dir, split)
    if y1 is None:
        print(f"!! thieu {eval_dir}/scores_{split}.npz hoac thresholds.json -> chay 14_eval_yolo.py truoc")

    person = None
    if args.quest_test:
        person = eval_person(Path(args.quest_test), args.person_weights, args.min_precision, args.calib_frac, args.seed)
        if person:
            save_json(person["thresholds"], out / "person" / "thresholds.json")

    save_json({"y1Groups": y1, "person": person, "minPrecision": args.min_precision}, out / "quest_metrics.json")
    md = to_markdown(y1, person)
    (out / "quest_metrics.md").write_text(md, encoding="utf-8")
    print(md)
    print(f"OK -> {out}/quest_metrics.json, quest_metrics.md" + (", person/thresholds.json" if person else ""))
    if person:
        print(f"Tiep: 16_export_yolo_onnx.py --task person --thresholds {out}/person/thresholds.json --out <dir> --upload <repo>")


if __name__ == "__main__":
    main()

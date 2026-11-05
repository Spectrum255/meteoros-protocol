"""Six-phase Voltix split check (v4, first pass).

The v3 field gives one total Voltix share.  v4 keeps that total and splits it
into Voltix+ / Voltix- with a polarity function driven by the same climate
bases.  This script verifies that:

  - the six shares still sum to 100;
  - the total Voltix share is unchanged;
  - V+ / V- targets and predictions use the same polarity rule.

It is a *consistency/calibration* pass, not an independent fit of V+/V-.
Independent polarity data can replace the derived targets later.

Run: python tools/check_v4.py
"""

from __future__ import annotations

from pathlib import Path

from check_v3 import predict as predict_v3
from particle_calibration import clamp, parse_rows, smoothstep


def polarity(T: float, H: float, C: float, E: float, PV: float, D: float, Y: int = 64) -> float:
    """Fraction of total Voltix that is positive.

    Warm, dry, high, peaked, fair-weather sites lean V+.
    Cold, wet, cave/deep sites lean V-.
    """
    hot = smoothstep(0.20, 1.00, T)
    cold = 1.0 - smoothstep(-1.00, -0.15, T)
    wet = smoothstep(-0.10, 0.90, H)
    peak = smoothstep(0.20, 0.70, PV)
    cave = smoothstep(0.20, 0.90, D)
    deep = smoothstep(1.00, 1.10, D)
    alt = clamp(max(0, Y - 64) / 192.0)
    return clamp(
        0.50
        + 0.18 * hot
        - 0.18 * cold
        - 0.14 * wet
        + 0.10 * alt
        + 0.05 * peak
        - 0.08 * cave
        - 0.10 * deep,
        0.20,
        0.80,
    )


def split_six(T, H, C, E, PV, D, total_v):
    f = polarity(T, H, C, E, PV, D)
    return f, total_v * f, total_v * (1.0 - f)


def main():
    rows = parse_rows()
    names = ["热", "冷", "V+", "V-", "水", "以太"]
    errors = {n: [] for n in names}
    total_v_errors = []
    lines = [
        "# 六相粒子场检查（v4 首轮）\n",
        "> 总 Voltix 沿用 v3；V+/V- 按极性函数拆分。目标值由同一极性规则派生，"
        "因此本轮不是独立极性拟合。\n",
        f"解析群系行数：{len(rows)}\n",
        "| 群系 | 目标 热/冷/V+/V-/水/以太 | 预测 热/冷/V+/V-/水/以太 | 最大误差 |",
        "| --- | --- | --- | --- |",
    ]

    for group, T, H, C, E, PV, D, target5 in rows:
        pred5 = predict_v3(T, H, C, E, PV, D)
        f_t, vp_t, vn_t = split_six(T, H, C, E, PV, D, target5[2])
        f_p, vp_p, vn_p = split_six(T, H, C, E, PV, D, pred5[2])
        target6 = [target5[0], target5[1], vp_t, vn_t, target5[3], target5[4]]
        pred6 = [pred5[0], pred5[1], vp_p, vn_p, pred5[3], pred5[4]]
        diffs = [p - t for p, t in zip(pred6, target6)]
        for n, d in zip(names, diffs):
            errors[n].append(abs(d))
        total_v_errors.append(abs((vp_p + vn_p) - (vp_t + vn_t)))
        tgt = "/".join(f"{v:.1f}" for v in target6)
        prd = "/".join(f"{v:.1f}" for v in pred6)
        lines.append(f"| {group} | {tgt} | {prd} | {max(abs(x) for x in diffs):.1f} |")

    lines.append("\n## MAE by phase\n")
    for n in names:
        lines.append(f"- {n}: {sum(errors[n]) / len(errors[n]):.2f} pp")
    all_err = [x for n in names for x in errors[n]]
    lines.append(f"- 六相总体: {sum(all_err) / len(all_err):.2f} pp")
    lines.append(f"- 总 Voltix 偏差: {sum(total_v_errors) / len(total_v_errors):.2f} pp")

    out = Path(__file__).resolve().parent / "calibration-output-v4.md"
    out.write_text("\n".join(lines), encoding="utf-8")
    print(f"wrote {out}")
    print(f"rows={len(rows)} overall_six={sum(all_err)/len(all_err):.2f}pp "
          f"total_v={sum(total_v_errors)/len(total_v_errors):.2f}pp")


if __name__ == "__main__":
    main()

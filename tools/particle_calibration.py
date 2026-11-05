"""Particle-field calibration prototype.

Parses the biome calibration table from docs/biome-particle-mapping.md,
evaluates the current weight functions at each biome's representative
parameter point, and reports predicted vs target shares.

Pure stdlib; run with the bundled Python.
"""

from __future__ import annotations

import math
import re
from pathlib import Path

DOC = Path(__file__).resolve().parents[1] / "docs" / "biome-particle-mapping.md"


def clamp(x: float, lo: float = 0.0, hi: float = 1.0) -> float:
    return max(lo, min(hi, x))


def smoothstep(edge0: float, edge1: float, x: float) -> float:
    if edge0 == edge1:
        return 0.0 if x < edge0 else 1.0
    t = clamp((x - edge0) / (edge1 - edge0))
    return t * t * (3.0 - 2.0 * t)


def midpoint(text: str) -> float | None:
    nums = [float(v) for v in re.findall(r"-?\d+(?:\.\d+)?", text)]
    if not nums:
        return None
    return sum(nums) / len(nums)


def c_value(text: str) -> float:
    if "蘑菇" in text:
        return -1.10
    if "深海" in text:
        return -0.75
    if "海洋" in text:
        return -0.32
    if "海岸" in text:
        return -0.15
    if "远" in text:
        return 0.65
    if "近" in text:
        return -0.04
    if "中" in text:
        return 0.165
    return 0.165


PV_LEVEL = {"山谷": 0, "低": 1, "中": 2, "高": 3, "峰顶": 4}


def pv_value(text: str) -> float:
    for key, level in PV_LEVEL.items():
        if key in text:
            return level
    return 2.0


def t_raw(level: float) -> float:
    # midpoint of the five vanilla temperature intervals
    bounds = [-1.0, -0.45, -0.15, 0.20, 0.55, 1.0]
    i = int(clamp(level, 0, 4.999))
    return (bounds[i] + bounds[i + 1]) / 2.0


def h_raw(level: float) -> float:
    bounds = [-1.0, -0.35, -0.10, 0.10, 0.30, 1.0]
    i = int(clamp(level, 0, 4.999))
    return (bounds[i] + bounds[i + 1]) / 2.0


def e_raw(level: float) -> float:
    bounds = [-1.0, -0.78, -0.375, -0.2225, 0.05, 0.45, 0.55, 1.0]
    i = int(clamp(level, 0, 6.999))
    return (bounds[i] + bounds[i + 1]) / 2.0


def pv_raw(level: float) -> float:
    # normalized PV level 0..4 -> PV value in [-1, 1]
    bounds = [-1.0, -0.85, -0.60, 0.20, 0.70, 1.0]
    i = int(clamp(level, 0, 4.999))
    return (bounds[i] + bounds[i + 1]) / 2.0


COEFF = dict(
    a0=1, aT=70, aH=0.6, aC=2, aE=4, aP=6,
    b0=1, bT=70, bH=0.3, bE=6, bP=5, bO=3,
    c0=2, cH=55, cT0=0.4, cT1=0.6, cP=4, cT=3,
    dT0=0.2, dT1=0.8, cO=45, dO0=0.5, dO1=0.5,
    v0=0.1, vP=0.6, vD=1.5, vE=0.3, vTH=0.3,
    eMin=0.5, eD=60, eM=25,
)


def predict(T: float, H: float, C: float, E: float, PV: float, D: float, Y: int = 64):
    t = clamp((T + 1.0) / 2.0)
    h = clamp((H + 1.0) / 2.0)
    e = clamp((E + 1.0) / 2.0)
    pv = clamp((PV + 1.0) / 2.0)
    d = clamp(D / 1.1)

    ocean = 1.0 - smoothstep(-0.19, -0.11, C)
    inland = smoothstep(-0.11, 0.30, C)
    mushroom = math.exp(-(((C + 1.1) / 0.08) ** 2))
    t_eff = clamp(t - 0.000625 * max(0, Y - 80))

    k = COEFF
    wT = k["a0"] + k["aT"] * t_eff ** 2 * (1 - k["aH"] * h) + k["aC"] * inland * (1 - h) + k["aE"] * e - k["aP"] * pv
    wC = k["b0"] + k["bT"] * (1 - t_eff) ** 2 * (1 - k["bH"] * h) + k["bE"] * (1 - e) + k["bP"] * pv * (1 - t_eff) + k["bO"] * ocean * (1 - t_eff)
    wA = (k["c0"] + k["cH"] * h ** 1.3 * (k["cT0"] + k["cT1"] * t_eff) + k["cP"] * (1 - pv) - k["cT"] * t_eff) * (k["dT0"] + k["dT1"] * t_eff) + k["cO"] * ocean * (k["dO0"] + k["dO1"] * t_eff)
    wV = k["v0"] + k["vP"] * pv + k["vD"] * d + k["vE"] * e + k["vTH"] * t_eff * h
    wE = max(k["eMin"], 100.0 - (wT + wC + wA + wV)) + k["eD"] * d * d + k["eM"] * mushroom

    ws = [max(0.0, v) for v in (wT, wC, wV, wA, wE)]
    s = sum(ws)
    return [100.0 * v / s for v in ws]  # order: T, C, V, A, E


def parse_rows():
    rows = []
    for line in DOC.read_text(encoding="utf-8").splitlines():
        if not line.startswith("|"):
            continue
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if len(cells) != 11:
            continue
        try:
            target = [float(v) for v in cells[6:11]]
        except ValueError:
            continue
        group = cells[0]
        t_level = midpoint(cells[1])
        h_level = midpoint(cells[2])
        e_level = midpoint(cells[4])
        if t_level is None or h_level is None or e_level is None:
            continue
        c = c_value(cells[3])
        pv = pv_raw(pv_value(cells[5]))
        d = 1.1 if "深暗" in group else (0.55 if "洞穴" in group else 0.0)
        rows.append((group, t_raw(t_level), h_raw(h_level), c, e_raw(e_level), pv, d, target))
    return rows


def main():
    rows = parse_rows()
    names = ["热", "冷", "电", "水", "以太"]
    errors = {n: [] for n in names}
    lines = []
    lines.append("# 粒子场公式校准输出（v1）\n")
    lines.append(f"解析群系行数：{len(rows)}\n")
    lines.append("| 群系 | 目标 热/冷/电/水/以太 | 预测 热/冷/电/水/以太 | 最大误差 |")
    lines.append("| --- | --- | --- | --- |")
    for group, T, H, C, E, PV, D, target in rows:
        pred = predict(T, H, C, E, PV, D)
        diffs = [p - t for p, t in zip(pred, target)]
        for n, d in zip(names, diffs):
            errors[n].append(abs(d))
        tgt = "/".join(f"{v:.1f}" for v in target)
        prd = "/".join(f"{v:.1f}" for v in pred)
        lines.append(f"| {group} | {tgt} | {prd} | {max(abs(x) for x in diffs):.1f} |")

    lines.append("\n## MAE by phase\n")
    for n in names:
        lines.append(f"- {n}: {sum(errors[n]) / len(errors[n]):.2f} pp")
    all_err = [x for n in names for x in errors[n]]
    lines.append(f"- overall: {sum(all_err) / len(all_err):.2f} pp")

    out = Path(__file__).resolve().parent / "calibration-output.md"
    out.write_text("\n".join(lines), encoding="utf-8")
    print(f"wrote {out}")
    print(f"rows={len(rows)} overall_mae={sum(all_err)/len(all_err):.2f}pp")


if __name__ == "__main__":
    main()

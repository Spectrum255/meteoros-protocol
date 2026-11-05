"""Offline fit of the particle-field scores (softmax form).

Design-time only: the fitted numbers are compiled into the mod. Runtime
evaluates five linear scores and a softmax; no calibration at runtime.

Run: python tools/fit_particle_field.py
"""

from __future__ import annotations

import math
from pathlib import Path

from particle_calibration import clamp, parse_rows, smoothstep

NAMES = [
    # sT
    "a0", "aHotDry", "aHotWet", "aFlatDry", "aInlandDry",
    # sC
    "b0", "bCold", "bColdWet", "bMountain", "bPeak",
    # sA
    "c0", "cWet", "cWetLand", "cOcean", "cValley",
    # sV
    "d0", "dPeak", "dCave", "dDeep",
    # sE
    "e0", "eDry", "eInland", "eAlt", "eCave", "eDeep", "eMushroom", "ePeak",
]

START = dict(
    a0=0.0, aHotDry=2.5, aHotWet=0.0, aFlatDry=0.2, aInlandDry=0.3,
    b0=0.0, bCold=2.5, bColdWet=0.0, bMountain=0.3, bPeak=0.2,
    c0=0.0, cWet=2.0, cWetLand=0.5, cOcean=1.0, cValley=0.3,
    d0=-3.0, dPeak=0.5, dCave=0.5, dDeep=0.5,
    e0=0.0, eDry=0.3, eInland=0.3, eAlt=0.2, eCave=0.5, eDeep=1.0, eMushroom=1.0, ePeak=0.2,
)


def basis(T: float, H: float, C: float, E: float, PV: float, D: float, Y: int = 64):
    hot = smoothstep(0.20, 1.00, T)
    cold = 1.0 - smoothstep(-1.00, -0.15, T)
    wet = smoothstep(-0.10, 0.90, H)
    dry = 1.0 - wet
    inland = smoothstep(-0.11, 0.30, C)
    ocean = 1.0 - smoothstep(-0.19, -0.11, C)
    flat = smoothstep(0.05, 0.55, E)
    peak = smoothstep(0.20, 0.70, PV)
    valley = 1.0 - peak
    cave = smoothstep(0.20, 0.90, D)
    deep = smoothstep(1.00, 1.10, D)
    alt = clamp(max(0, Y - 64) / 192.0)
    mushroom = math.exp(-(((C + 1.1) / 0.08) ** 2))
    land = 1.0 - ocean
    return dict(
        hot=hot, cold=cold, wet=wet, dry=dry, inland=inland, ocean=ocean,
        flat=flat, peak=peak, valley=valley, cave=cave, deep=deep,
        alt=alt, mushroom=mushroom, land=land,
        hotDry=hot * dry, hotWet=hot * wet, coldWet=cold * wet,
        wetLand=wet * land, flatDry=flat * dry, inlandDry=inland * dry,
    )


def scores(c, b):
    sT = c["a0"] + c["aHotDry"] * b["hotDry"] + c["aHotWet"] * b["hotWet"] + c["aFlatDry"] * b["flatDry"] + c["aInlandDry"] * b["inlandDry"]
    sC = c["b0"] + c["bCold"] * b["cold"] + c["bColdWet"] * b["coldWet"] + c["bMountain"] * (1 - b["flat"]) + c["bPeak"] * b["peak"] * (1 - b["hot"])
    sA = c["c0"] + c["cWet"] * b["wet"] + c["cWetLand"] * b["wetLand"] + c["cOcean"] * b["ocean"] + c["cValley"] * b["valley"] * b["land"]
    sV = c["d0"] + c["dPeak"] * b["peak"] + c["dCave"] * b["cave"] + c["dDeep"] * b["deep"]
    sE = c["e0"] + c["eDry"] * b["dry"] + c["eInland"] * b["inland"] + c["eAlt"] * b["alt"] + c["eCave"] * b["cave"] + c["eDeep"] * b["deep"] + c["eMushroom"] * b["mushroom"] + c["ePeak"] * b["peak"]
    return sT, sC, sV, sA, sE


def predict(c, b):
    ss = scores(c, b)
    m = max(ss)
    exps = [math.exp(v - m) for v in ss]
    s = sum(exps)
    return [100.0 * v / s for v in exps]


def mae(c, rows):
    total = 0.0
    n = 0
    for _group, T, H, C, E, PV, D, target in rows:
        pred = predict(c, basis(T, H, C, E, PV, D))
        total += sum(abs(p - t) for p, t in zip(pred, target))
        n += 5
    return total / n


def coordinate_descent(rows, start, passes=25):
    c = dict(start)
    best = mae(c, rows)
    steps = [0.02, 0.05, 0.1, 0.2, 0.4, 0.8]
    for _p in range(passes):
        improved = False
        for name in NAMES:
            base = c[name]
            best_local = base
            for step in steps:
                for sign in (-1.0, 1.0):
                    c[name] = base + sign * step
                    v = mae(c, rows)
                    if v + 1e-6 < best:
                        best = v
                        best_local = c[name]
                        improved = True
                        break
                if best_local != base:
                    break
            c[name] = best_local
        if not improved:
            break
    return c, best


def main():
    rows = parse_rows()
    best_c, best_v = None, 1e9
    for scale in (0.6, 1.0, 1.6):
        start = {k: v * scale for k, v in START.items()}
        c, v = coordinate_descent(rows, start)
        print(f"restart scale={scale}: mae={v:.2f}")
        if v < best_v:
            best_c, best_v = c, v

    print(f"\nbest overall MAE: {best_v:.2f} pp over {len(rows)} rows")
    print("\nfinal scores:")
    for name in NAMES:
        print(f"  {name:10s} = {best_c[name]:.4f}")

    lines = [f"# fitted softmax scores (MAE {best_v:.2f} pp, {len(rows)} rows)\n", "```java"]
    for name in NAMES:
        lines.append(f"private static final double {name.upper()} = {best_c[name]:.4f};")
    lines.append("```")
    out = Path(__file__).resolve().parent / "fitted-coefficients.md"
    out.write_text("\n".join(lines), encoding="utf-8")
    print(f"\nwrote {out}")


if __name__ == "__main__":
    main()

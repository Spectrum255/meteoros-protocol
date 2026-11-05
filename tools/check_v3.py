"""Verify the fitted v3 linear-interaction formula."""

import math

from particle_calibration import clamp, parse_rows, smoothstep

C = dict(
    a0=2.1829, aHotDry=42.2878, aHotWet=0.0007, aFlatDry=0.0008, aInlandDry=4.1582,
    b0=2.1829, bCold=26.8973, bColdWet=48.1324, bMountain=1.7475, bPeak=3.5657,
    c0=0.6918, cWet=5.7026, cWetLand=3.3184, cOcean=11.0987, cValley=4.5969,
    d0=0.0855, dPeak=0.2882, dCave=2.0, dDeep=3.0,
    e0=6.8466, eDry=2.2818, eInland=6.2685, eAlt=4.0, eCave=10.0, eDeep=20.0,
    eMushroom=20.0, ePeak=1.9845,
)


def predict(T, H, Cc, E, PV, D, Y=64):
    hot = smoothstep(0.20, 1.00, T)
    cold = 1.0 - smoothstep(-1.00, -0.15, T)
    wet = smoothstep(-0.10, 0.90, H)
    dry = 1.0 - wet
    inland = smoothstep(-0.11, 0.30, Cc)
    ocean = 1.0 - smoothstep(-0.19, -0.11, Cc)
    flat = smoothstep(0.05, 0.55, E)
    peak = smoothstep(0.20, 0.70, PV)
    valley = 1.0 - peak
    cave = smoothstep(0.20, 0.90, D)
    deep = smoothstep(1.00, 1.10, D)
    alt = clamp(max(0, Y - 64) / 192.0)
    mushroom = math.exp(-(((Cc + 1.1) / 0.08) ** 2))
    land = 1.0 - ocean

    wT_land = C["a0"] + C["aHotDry"] * hot * dry + C["aHotWet"] * hot * wet + C["aFlatDry"] * flat * dry + C["aInlandDry"] * inland * dry
    wC_land = C["b0"] + C["bCold"] * cold + C["bColdWet"] * cold * wet + C["bMountain"] * (1 - flat) + C["bPeak"] * peak * (1 - hot)
    wA_land = C["c0"] + C["cWet"] * wet + C["cWetLand"] * wet * land + C["cValley"] * valley * land
    wV_land = C["d0"] + C["dPeak"] * peak + C["dCave"] * cave + C["dDeep"] * deep
    wE_land = C["e0"] + C["eDry"] * dry + C["eInland"] * inland + C["eAlt"] * alt + C["eCave"] * cave + C["eDeep"] * deep + C["eMushroom"] * mushroom + C["ePeak"] * peak

    # separate ocean branch, blended by the ocean factor
    wT_ocean = 5 + 40 * hot
    wC_ocean = 5 + 50 * cold
    wA_ocean = 38 - 12 * cold
    wV_ocean = 0.1 + peak
    wE_ocean = 15 + 10 * cold

    wT = land * wT_land + ocean * wT_ocean
    wC = land * wC_land + ocean * wC_ocean
    wA = land * wA_land + ocean * wA_ocean
    wV = land * wV_land + ocean * wV_ocean
    wE = land * wE_land + ocean * wE_ocean
    ws = [max(0.0, v) for v in (wT, wC, wV, wA, wE)]
    s = sum(ws)
    return [100 * v / s for v in ws]


def main():
    rows = parse_rows()
    names = ["热", "冷", "电", "水", "以太"]
    err = {n: [] for n in names}
    print("key biomes:")
    for group, T, H, Cc, E, PV, D, target in rows:
        pred = predict(T, H, Cc, E, PV, D)
        for n, p, t in zip(names, pred, target):
            err[n].append(abs(p - t))
        if group in ("沙漠", "雪原", "丛林", "河流", "暖水海洋", "平原"):
            print(f"  {group}: target {target} pred {[round(p,1) for p in pred]}")
    for n in names:
        print(f"{n}: {sum(err[n]) / len(err[n]):.2f}")
    allv = [x for n in names for x in err[n]]
    print(f"overall: {sum(allv) / len(allv):.2f} pp")


if __name__ == "__main__":
    main()

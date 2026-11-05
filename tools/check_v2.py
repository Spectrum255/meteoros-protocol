"""Quick check of the hand-tuned v2 particle-field formula."""

from particle_calibration import clamp, parse_rows, smoothstep


def basis(T, H, C, E, PV, D, Y=64):
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
    mushroom = __import__("math").exp(-(((C + 1.1) / 0.08) ** 2))
    return hot, cold, wet, dry, inland, ocean, flat, peak, valley, cave, deep, alt, mushroom


def predict(T, H, C, E, PV, D, Y=64):
    hot, cold, wet, dry, inland, ocean, flat, peak, valley, cave, deep, alt, mushroom = basis(T, H, C, E, PV, D, Y)
    wT = 1 + 75 * hot * dry + 5 * inland * dry + 6 * flat * dry
    wC = 1 + 75 * cold + 10 * (1 - flat) + 6 * peak * (1 - hot)
    wA = 2 + 60 * wet + 18 * ocean + 8 * valley * (1 - ocean)
    wV = 0.1 + 1.5 * peak + 2 * cave + 3 * deep
    wE = 10 + 10 * dry + 8 * inland + 6 * alt + 10 * cave + 20 * deep + 20 * mushroom + 5 * peak
    ws = [wT, wC, wV, wA, wE]
    s = sum(ws)
    return [100 * v / s for v in ws]


def main():
    rows = parse_rows()
    names = ["热", "冷", "电", "水", "以太"]
    err = {n: [] for n in names}
    for group, T, H, C, E, PV, D, target in rows:
        pred = predict(T, H, C, E, PV, D)
        for n, p, t in zip(names, pred, target):
            err[n].append(abs(p - t))
    for n in names:
        print(f"{n}: {sum(err[n])/len(err[n]):.2f}")
    allv = [x for n in names for x in err[n]]
    print(f"overall: {sum(allv)/len(allv):.2f} pp")


if __name__ == "__main__":
    main()

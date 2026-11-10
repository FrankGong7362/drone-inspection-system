"""论文第 5 章算法对比实验：5 种算法 × 4 种规模 × 2 种场景 × 多个随机种子。

用法（在 algorithm 目录下执行）：
    python experiments/run_experiment.py
    python experiments/run_experiment.py --sizes 10,20 --seeds 3
    python experiments/run_experiment.py --algorithms greedy,astar

输出（experiments/results/）：
    raw.csv        每次运行的原始记录（含收敛迭代数）
    summary.csv    按 场景/规模/算法 聚合的均值与标准差
    summary.md     论文可直接引用的中文表格
    convergence.json  GA/ACO 每代最优航程序列（画收敛曲线用）

说明：
- 固定随机种子生成航点，保证不同算法面对同一组输入；
- 航点与禁飞区保持"安全距离 + 额外余量"，确保禁飞区场景下各算法均可达；
- 耗时为单次 solve 的墙钟时间，包含避障距离矩阵构建（与线上接口口径一致）。
"""
import argparse
import csv
import json
import math
import os
import random
import statistics
import sys
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)

from app import algorithms  # noqa: E402
from app.algorithms.avoid import _haversine  # noqa: E402
from app.schemas import NoFlyZone, PlanRequest  # noqa: E402

CENTER = (121.50, 31.23)
ZONES = [
    NoFlyZone(center_lng=121.515, center_lat=31.240, radius_km=0.5),
    NoFlyZone(center_lng=121.488, center_lat=31.222, radius_km=0.3),
]
BUFFER_KM = 0.5
ALGORITHMS = {
    "greedy": algorithms.greedy,
    "nearest": algorithms.nearest,
    "astar": algorithms.astar_algo,
    "ga": algorithms.genetic,
    "aco": algorithms.ant_colony,
}
ALGO_CN = {"greedy": "贪心", "nearest": "最近邻", "astar": "改进A*", "ga": "遗传算法", "aco": "蚁群算法"}
SCENARIOS = {"no_zone": "无禁飞区", "zone": "含禁飞区"}


def generate_waypoints(n: int, seed: int, zones):
    """生成 n 个航点：均匀分布 + 固定种子；含禁飞区时保证安全距离。"""
    rng = random.Random(seed)
    pts = [{"id": 0, "lng": CENTER[0], "lat": CENTER[1]}]
    while len(pts) <= n:
        lng = CENTER[0] + rng.uniform(-0.020, 0.020)
        lat = CENTER[1] + rng.uniform(-0.015, 0.015)
        if zones and any(_haversine(lat, lng, z.center_lat, z.center_lng)
                         < z.radius_km + BUFFER_KM + 0.25 for z in zones):
            continue
        pts.append({"id": len(pts), "lng": lng, "lat": lat})
    return pts


def make_request(algorithm: str, n: int, seed: int, scenario: str) -> PlanRequest:
    zones = [] if scenario == "no_zone" else ZONES
    pts = generate_waypoints(n, seed, zones)
    return PlanRequest.model_validate({
        "algorithm": algorithm,
        "start_point": pts[0],
        "waypoints": pts[1:],
        "constraints": {
            "max_flight_range_km": None,
            "no_fly_buffer_km": BUFFER_KM,
            "no_fly_zones": [
                {"center_lng": z.center_lng, "center_lat": z.center_lat, "radius_km": z.radius_km}
                for z in zones
            ],
        },
    })


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--sizes", default="10,20,50,100")
    parser.add_argument("--seeds", type=int, default=5, help="随机种子个数（1..N）")
    parser.add_argument("--algorithms", default=",".join(ALGORITHMS))
    parser.add_argument("--out", default=os.path.join(ROOT, "experiments", "results"))
    args = parser.parse_args()

    sizes = [int(x) for x in args.sizes.split(",") if x.strip()]
    algos = [x.strip() for x in args.algorithms.split(",") if x.strip() in ALGORITHMS]
    scenarios = list(SCENARIOS)
    os.makedirs(args.out, exist_ok=True)

    raw = []
    convergence = {}
    total = len(sizes) * len(algos) * args.seeds * len(scenarios)
    done = 0
    for scenario in scenarios:
        convergence[scenario] = {}
        for n in sizes:
            convergence[scenario][n] = {}
            for seed in range(1, args.seeds + 1):
                req = make_request("greedy", n, seed, scenario)  # 同一组输入复用
                convergence[scenario][n][seed] = {}
                for algo in algos:
                    req = req.model_copy(update={"algorithm": algo})
                    t0 = time.perf_counter()
                    result = ALGORITHMS[algo](req)
                    runtime_ms = (time.perf_counter() - t0) * 1000.0
                    route, distance, feasible = result[:3]
                    conv = result[3] if len(result) > 3 else None
                    message = result[4] if len(result) > 4 else ""
                    if conv:
                        convergence[scenario][n][seed][algo] = [round(v, 3) for v in conv]
                    raw.append({
                        "scenario": scenario,
                        "n": n,
                        "seed": seed,
                        "algorithm": algo,
                        "distance_km": round(float(distance), 3),
                        "feasible": bool(feasible),
                        "runtime_ms": round(runtime_ms, 1),
                        "iterations": len(conv) if conv else "",
                        "route_nodes": len(route),
                        "message": message or "",
                    })
                    done += 1
                    print(f"[{done}/{total}] {scenario} n={n} seed={seed} {algo:8s} "
                          f"{distance:8.2f} km {runtime_ms:7.1f} ms feasible={feasible}", flush=True)

    raw_path = os.path.join(args.out, "raw.csv")
    with open(raw_path, "w", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=list(raw[0].keys()))
        writer.writeheader()
        writer.writerows(raw)

    with open(os.path.join(args.out, "convergence.json"), "w", encoding="utf-8") as f:
        json.dump(convergence, f, ensure_ascii=False, indent=1)

    # 聚合：场景 × 规模 × 算法
    summary = []
    for scenario in scenarios:
        for n in sizes:
            for algo in algos:
                rows = [r for r in raw if r["scenario"] == scenario
                        and r["n"] == n and r["algorithm"] == algo]
                if not rows:
                    continue
                iters = [r["iterations"] for r in rows if r["iterations"] != ""]
                summary.append({
                    "scenario": SCENARIOS[scenario],
                    "n": n,
                    "algorithm": ALGO_CN[algo],
                    "distance_mean": round(statistics.mean(r["distance_km"] for r in rows), 3),
                    "distance_std": round(statistics.pstdev([r["distance_km"] for r in rows]), 3),
                    "runtime_mean_ms": round(statistics.mean(r["runtime_ms"] for r in rows), 1),
                    "runtime_std_ms": round(statistics.pstdev([r["runtime_ms"] for r in rows]), 1),
                    "feasible_rate": round(sum(1 for r in rows if r["feasible"]) / len(rows), 3),
                    "iterations_mean": round(statistics.mean(iters), 1) if iters else "",
                })

    with open(os.path.join(args.out, "summary.csv"), "w", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=list(summary[0].keys()))
        writer.writeheader()
        writer.writerows(summary)

    lines = ["# 算法对比实验结果\n",
             "> 每格为 mean ± population std（N 个随机种子）；耗时含避障距离矩阵构建。\n"]
    for scenario in scenarios:
        lines.append(f"\n## {SCENARIOS[scenario]}\n")
        for metric, unit in (("distance", "km"), ("runtime", "ms")):
            lines.append(f"\n### 航程（{unit}，mean±std）\n" if metric == "distance"
                         else f"\n### 单次规划耗时（{unit}，mean±std）\n")
            header = "| 算法 | " + " | ".join(f"n={n}" for n in sizes) + " |"
            lines.append(header)
            lines.append("|---|" + "|".join(["---"] * len(sizes)) + "|")
            for algo in algos:
                cells = []
                for n in sizes:
                    row = next((s for s in summary if s["scenario"] == SCENARIOS[scenario]
                                and s["n"] == n and s["algorithm"] == ALGO_CN[algo]), None)
                    if row is None:
                        cells.append("--")
                    elif metric == "distance":
                        cells.append(f"{row['distance_mean']:.2f}±{row['distance_std']:.2f}")
                    else:
                        cells.append(f"{row['runtime_mean_ms']:.0f}±{row['runtime_std_ms']:.0f}")
                lines.append(f"| {ALGO_CN[algo]} | " + " | ".join(cells) + " |")
    with open(os.path.join(args.out, "summary.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")

    print(f"\n完成：{raw_path}")
    print(f"聚合：{os.path.join(args.out, 'summary.csv')}")


if __name__ == "__main__":
    main()

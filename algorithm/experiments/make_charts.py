"""根据实验结果生成论文用图表（PNG，200dpi）。

依赖 matplotlib（仅实验环境需要，算法服务本体不依赖）。
用法（在 algorithm 目录下执行，先跑 run_experiment.py / run_astar_compare.py）：
    python experiments/make_charts.py

输出 experiments/results/figures/：
    distance_compare.png   5 算法航程对比（无/含禁飞区）
    runtime_compare.png    5 算法耗时对比（对数坐标）
    convergence.png        GA/ACO 收敛曲线（多种子平均）
    astar_improvement.png  标准 A* vs 改进 A* 四项指标（归一化）
"""
import csv
import json
import os
import statistics
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RESULTS = os.path.join(ROOT, "experiments", "results")

try:
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
except ImportError:
    print("未安装 matplotlib，跳过图表生成：pip install matplotlib")
    sys.exit(0)

plt.rcParams["font.sans-serif"] = ["Microsoft YaHei", "SimHei", "DejaVu Sans"]
plt.rcParams["axes.unicode_minus"] = False

ALGO_ORDER = ["贪心", "最近邻", "改进A*", "遗传算法", "蚁群算法"]
COLORS = ["#9aa5b1", "#6f9bd1", "#e6501a", "#2f9e44", "#7048b6"]
SCENARIOS = ["无禁飞区", "含禁飞区"]


def load_summary():
    with open(os.path.join(RESULTS, "summary.csv"), encoding="utf-8-sig") as f:
        return list(csv.DictReader(f))


def series(rows, scenario, algo, field):
    out = {}
    for r in rows:
        if r["scenario"] == scenario and r["algorithm"] == algo:
            out[int(r["n"])] = float(r[field])
    return out


def grouped_bars(rows, field, ylabel, title, path, log=False):
    sizes = sorted({int(r["n"]) for r in rows})
    fig, axes = plt.subplots(1, len(SCENARIOS), figsize=(11, 4), sharey=True)
    width = 0.16
    for ax, scenario in zip(axes, SCENARIOS):
        for i, algo in enumerate(ALGO_ORDER):
            values = [series(rows, scenario, algo, field).get(n, 0) for n in sizes]
            xs = [x + (i - 2) * width for x in range(len(sizes))]
            bars = ax.bar(xs, values, width=width, label=algo, color=COLORS[i])
            if log:
                ax.set_yscale("log")
            for x, v in zip(xs, values):
                label = f"{v:.0f}" if field.endswith("ms") else f"{v:.1f}"
                ax.text(x, v, label, ha="center", va="bottom", fontsize=6, rotation=90)
        ax.set_xticks(range(len(sizes)))
        ax.set_xticklabels([f"n={n}" for n in sizes])
        ax.set_title(scenario)
        ax.grid(axis="y", linestyle=":", alpha=0.5)
        ax.set_ylabel(ylabel)
    handles, labels = axes[0].get_legend_handles_labels()
    fig.legend(handles, labels, loc="upper center", ncol=5, frameon=False,
               bbox_to_anchor=(0.5, 1.02))
    fig.suptitle(title, y=1.10)
    fig.tight_layout()
    fig.savefig(path, dpi=200, bbox_inches="tight")
    plt.close(fig)


def convergence_chart():
    path = os.path.join(RESULTS, "convergence.json")
    if not os.path.exists(path):
        return
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    sizes = sorted({int(n) for n in data.get("no_zone", {})})
    if not sizes:
        return
    fig, axes = plt.subplots(1, len(sizes), figsize=(13, 3.2), sharey=False)
    if len(sizes) == 1:
        axes = [axes]
    for ax, n in zip(axes, sizes):
        seeds = data["no_zone"][str(n)]
        for algo, color, name in (("ga", "#2f9e44", "遗传算法"), ("aco", "#7048b6", "蚁群算法")):
            runs = [seeds[s][algo] for s in seeds if algo in seeds[s]]
            if not runs:
                continue
            length = max(len(r) for r in runs)
            avg = [statistics.mean(r[i] if i < len(r) else r[-1] for r in runs)
                   for i in range(length)]
            ax.plot(range(len(avg)), avg, color=color, label=name, linewidth=1.4)
        ax.set_title(f"n={n}")
        ax.set_xlabel("迭代代数")
        ax.set_ylabel("当前最优总航程（km）")
        ax.grid(linestyle=":", alpha=0.5)
        ax.legend(frameon=False, fontsize=8)
    fig.suptitle("GA/ACO 收敛曲线（无禁飞区，5 个随机种子平均）", y=1.04)
    fig.tight_layout()
    fig.savefig(os.path.join(RESULTS, "figures", "convergence.png"), dpi=200,
                bbox_inches="tight")
    plt.close(fig)


def astar_chart():
    path = os.path.join(RESULTS, "astar_compare_raw.csv")
    if not os.path.exists(path):
        return
    with open(path, encoding="utf-8-sig") as f:
        rows = list(csv.DictReader(f))
    metrics = [("expanded", "扩展节点数"), ("runtime_ms", "单段耗时"),
               ("path_km", "路径长度"), ("turns", "转折点数")]
    std_means, imp_means = [], []
    for key, _ in metrics:
        std = [float(r[key]) for r in rows if r["variant"] == "standard"]
        imp = [float(r[key]) for r in rows if r["variant"] == "improved"]
        std_means.append(statistics.mean(std))
        imp_means.append(statistics.mean(imp))
    fig, ax = plt.subplots(figsize=(7.2, 3.8))
    x = range(len(metrics))
    width = 0.34
    std_rel = [100.0] * len(metrics)
    imp_rel = [imp / std * 100 for imp, std in zip(imp_means, std_means)]
    ax.bar([i - width / 2 for i in x], std_rel, width, label="标准 A*", color="#9aa5b1")
    bars = ax.bar([i + width / 2 for i in x], imp_rel, width, label="改进 A*", color="#e6501a")
    for i, v in enumerate(imp_rel):
        ax.text(i + width / 2, v, f"{v:.0f}%", ha="center", va="bottom", fontsize=8)
    for i, (_, label) in enumerate(metrics):
        ax.text(i, -8, label, ha="center", va="top", fontsize=8)
    ax.set_xticks([])
    ax.set_ylabel("相对标准 A*（标准=100）")
    ax.set_ylim(0, 110)
    ax.grid(axis="y", linestyle=":", alpha=0.5)
    ax.legend(frameon=False, ncol=2, loc="upper right")
    ax.set_title("改进 A* 相对标准 A* 的指标对比（30 条穿越禁飞区航段）")
    fig.tight_layout()
    fig.savefig(os.path.join(RESULTS, "figures", "astar_improvement.png"), dpi=200,
                bbox_inches="tight")
    plt.close(fig)


def main() -> None:
    os.makedirs(os.path.join(RESULTS, "figures"), exist_ok=True)
    rows = load_summary()
    grouped_bars(rows, "distance_mean", "总航程（km）", "5 种算法闭环航程对比",
                 os.path.join(RESULTS, "figures", "distance_compare.png"))
    grouped_bars(rows, "runtime_mean_ms", "单次规划耗时（ms）", "5 种算法规划耗时对比（对数）",
                 os.path.join(RESULTS, "figures", "runtime_compare.png"), log=True)
    convergence_chart()
    astar_chart()
    print("图表已生成：", os.path.join(RESULTS, "figures"))


if __name__ == "__main__":
    main()

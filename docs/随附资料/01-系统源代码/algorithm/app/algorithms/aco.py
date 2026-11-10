"""蚁群算法（ACO）求解带起点往返的巡检 TSP（任务管理与路径规划核心算法之一）。

设计要点（对应开题报告"基于蚁群算法的巡检路径优化"）：
- 模型：精英蚂蚁系统（Elitist Ant System）。
  转移概率 p ∝ τ^α · η^β，η = 1/d 为启发式信息（距离倒数），
  β 偏大使距离主导搜索、α 保留信息素正反馈。
- 信息素更新：蒸发(1-ρ)τ + Σ(蚂蚁按 Q/L 沉积) + 精英权重(全局最优额外沉积 e·Q/L_best)。
- 混合策略：与 GA 一致，每代对全局最优做 2-opt 邻域改进，克服纯 ACO 收敛慢。
- 性能：numpy 向量化转移概率与信息素更新；设 20s 迭代上限保证契约 30s 超时内返回；
  蚂蚁数/迭代数随规模自适应缩减。

返回：(route, total_distance_km, feasible, convergence)
    convergence 为每代全局最优总航程（km）列表，供论文收敛曲线绘图与算法对比。
"""
import time
from typing import List, Optional, Tuple

import numpy as np

from app.schemas import PlanRequest
from app.algorithms.ga import _build_distance_matrix, _path_length, _two_opt
from app.algorithms.avoid import obstacle_distance_matrix

# 超参数（供第 7 步实验调优）
ANTS_CAP = 40        # 蚂蚁数上限
ANTS_FLOOR = 16      # 蚂蚁数下限
ITERATIONS = 400     # 迭代代数
ALPHA = 1.0          # 信息素重要度
BETA = 3.0           # 启发式信息重要度（距离倒数）
RHO = 0.3            # 信息素蒸发率
Q = 100.0            # 信息素强度常数
ELITE_W = 4.0        # 精英蚂蚁沉积权重
TIME_BUDGET_S = 20.0  # 迭代时间预算（契约 30s 超时内留余量）


def solve(req: PlanRequest) -> Tuple[List[int], float, bool, Optional[List[float]]]:
    start = req.start_point
    waypoints = list(req.waypoints)
    n = len(waypoints)
    if n == 0:
        return [start.id], 0.0, True, []

    zones = list(req.constraints.no_fly_zones or []) if req.constraints else []
    buffer_km = (req.constraints.no_fly_buffer_km
                 if req.constraints and req.constraints.no_fly_buffer_km is not None else 0.5)
    dm = _build_distance_matrix(start, waypoints, zones, buffer_km=buffer_km)
    if dm is None:
        return [start.id], 0.0, False, [], "禁飞区阻隔，无法构建可行距离矩阵"
    dmv = np.asarray(dm, dtype=float)          # (n+1, n+1)，节点 0 为起点
    n_nodes = n + 1

    # 启发式信息矩阵 η = 1/d（对角线与 0 距离边置 0，避免除零警告）
    eta = np.divide(1.0, dmv, out=np.zeros_like(dmv), where=dmv > 0)

    # 信息素初值：τ0 = 1/(n·L_nn)，L_nn 以非零最小边距离近似
    # （起点可能与某航点同坐标 → dmv[0] 含 0，必须取全矩阵非零最小距离）
    tau0 = 1.0 / (n * max(dmv[dmv > 0].min(), 1e-9))
    tau = np.full((n_nodes, n_nodes), tau0)

    n_ants = max(ANTS_FLOOR, min(ANTS_CAP, n * 2))

    def construct(ant_rng: np.random.Generator) -> List[int]:
        """单只蚂蚁：从起点出发按轮盘赌构造完整排列（航点索引 1..n）。"""
        unvisited = np.ones(n_nodes, dtype=bool)
        unvisited[0] = False
        perm: List[int] = []
        cur = 0
        for _ in range(n):
            weight = (tau[cur] ** ALPHA) * (eta[cur] ** BETA) * unvisited
            idx = np.flatnonzero(weight)
            if idx.size == 0 or weight[idx].sum() <= 0:
                # 防御：候选全零（如重复坐标点）时均匀随机挑一个未访问点
                cur = int(ant_rng.choice(np.flatnonzero(unvisited)))
            else:
                cur = int(ant_rng.choice(idx, p=weight[idx] / weight[idx].sum()))
            unvisited[cur] = False
            perm.append(cur)
        return perm

    # 全局最优初始化：贪心解（2-opt 改进后）
    seq = list(range(1, n + 1))
    unv = seq[:]
    greedy_perm: List[int] = []
    cur = 0
    while unv:
        nxt = min(unv, key=lambda i: dm[cur][i])
        greedy_perm.append(nxt)
        cur = nxt
        unv.remove(nxt)
    best_perm, best_len = _two_opt(dm, greedy_perm, _path_length(dm, greedy_perm))

    convergence: List[float] = []
    deadline = time.time() + TIME_BUDGET_S
    rng = np.random.default_rng()

    # ---- 迭代 ----
    for _gen in range(ITERATIONS):
        if time.time() > deadline:
            break

        # 所有蚂蚁构造解
        ant_perms = [construct(rng) for _ in range(n_ants)]
        ant_lens = [_path_length(dm, p) for p in ant_perms]
        gen_best_i = int(np.argmin(ant_lens))

        gen_best_perm, gen_best_len = ant_perms[gen_best_i], ant_lens[gen_best_i]
        # 混合策略：对当代最优做 2-opt 改进
        gen_best_perm, gen_best_len = _two_opt(dm, gen_best_perm[:], gen_best_len)
        if gen_best_len < best_len:
            best_perm, best_len = gen_best_perm[:], gen_best_len

        # 信息素更新：蒸发 + 蚂蚁沉积 + 精英沉积
        tau *= (1.0 - RHO)
        for p, l in zip(ant_perms, ant_lens):
            deposit = Q / l
            path = [0] + p + [0]
            for a, b in zip(path, path[1:]):
                tau[a][b] += deposit
                tau[b][a] += deposit
        elite_deposit = ELITE_W * Q / best_len
        elite_path = [0] + best_perm + [0]
        for a, b in zip(elite_path, elite_path[1:]):
            tau[a][b] += elite_deposit
            tau[b][a] += elite_deposit

        convergence.append(round(best_len, 3))

    # ---- 可行性：续航约束 ----
    feasible = True
    constraint = req.constraints
    if constraint and constraint.max_flight_range_km is not None:
        if best_len > constraint.max_flight_range_km:
            feasible = False

    route = [start.id] + [waypoints[i - 1].id for i in best_perm] + [start.id]
    return route, best_len, feasible, convergence

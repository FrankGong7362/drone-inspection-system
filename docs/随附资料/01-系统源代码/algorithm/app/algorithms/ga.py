"""遗传算法（GA）求解带起点往返的巡检 TSP（任务管理与路径规划核心算法之一）。

设计要点（对应开题报告"基于遗传算法的巡检路径优化"）：
- 编码：染色体为待巡检航点（不含起点）的排列，完整路径 = 起点 → 排列 → 起点。
- 适应度：路径总航程的倒数（航程越短适应度越高）。
- 遗传算子：锦标赛选择(k=3) + OX 顺序交叉 + 交换/逆转变异（各 50%）。
- 混合策略（GA + 2-opt 局部搜索，即模因算法）：每代对全局最优个体做
  2-opt 邻域改进并回插种群，克服纯 GA 过早收敛问题；初始种群注入贪心解。
- 性能：预计算 Haversine 距离矩阵，适应度评估 O(n) 查表；设 20s 迭代上限
  保证在契约 30s 读超时内返回。

返回：(route, total_distance_km, feasible, convergence)
    convergence 为每代全局最优总航程（km）列表，供论文收敛曲线绘图与算法对比。
"""
import random
import time
from typing import List, Optional, Tuple

from app.schemas import PlanRequest
from app.algorithms.baselines import _dist
from app.algorithms.avoid import obstacle_distance_matrix

# 超参数（供第 7 步实验调优）
POP_SIZE_CAP = 120        # 种群规模上限
POP_SIZE_FLOOR = 40       # 种群规模下限
GENERATIONS = 600         # 迭代代数
TOURNAMENT_K = 3          # 锦标赛规模
CX_RATE = 0.9             # 交叉率
MUT_RATE = 0.25           # 变异率
ELITE_COUNT = 2           # 精英保留数量
TIME_BUDGET_S = 20.0      # 迭代时间预算（契约 30s 超时内留余量）


def _build_distance_matrix(start, waypoints, zones=None, buffer_km=0.0) -> Optional[List[List[float]]]:
    """节点 0 = 起点，1..n = 航点，返回对称距离矩阵（km）。
    zones 非空时用避障距离（绕"禁飞区+buffer"），否则用直线距离。
    """
    if zones:
        return obstacle_distance_matrix(start, waypoints, zones, buffer_km=buffer_km)

    nodes = [start] + list(waypoints)
    m = len(nodes)
    dm = [[0.0] * m for _ in range(m)]
    for i in range(m):
        for j in range(i + 1, m):
            d = _dist(nodes[i], nodes[j])
            dm[i][j] = dm[j][i] = d
    return dm


def _path_length(dm, perm: List[int]) -> float:
    """排列 perm（航点索引 1..n）的闭环路径长度：0 -> perm -> 0。"""
    total = dm[0][perm[0]]
    for a, b in zip(perm, perm[1:]):
        total += dm[a][b]
    return total + dm[perm[-1]][0]


def _ox_crossover(p1: List[int], p2: List[int]) -> List[int]:
    """OX 顺序交叉：保留父代 1 的一段，其余按父代 2 的相对顺序填充。"""
    n = len(p1)
    a, b = sorted(random.sample(range(n), 2))
    child = [None] * n
    child[a:b + 1] = p1[a:b + 1]
    fill = (g for g in p2 if g not in child[a:b + 1])
    for i in list(range(b + 1, n)) + list(range(0, a + 1)):
        if child[i] is None:
            child[i] = next(fill)
    return child


def _mutate(perm: List[int]) -> List[int]:
    """变异：50% 交换（两点互换），50% 逆转（片段反转，兼具 2-opt 局部搜索）。"""
    n = len(perm)
    a, b = sorted(random.sample(range(n), 2))
    if random.random() < 0.5:
        perm[a], perm[b] = perm[b], perm[a]
    else:
        perm[a:b + 1] = reversed(perm[a:b + 1])
    return perm


def _two_opt(dm, perm: List[int], length: float) -> Tuple[List[int], float]:
    """2-opt 局部搜索：逆转改进直到局部最优（闭环路径，节点 0 为起点）。

    返回 (改进后排列, 改进后长度)。delta = 断开两条边换成两条新边的净变化。
    """
    n = len(perm)
    improved = True
    while improved:
        improved = False
        for i in range(n - 1):
            a = perm[i - 1] if i > 0 else 0          # 前驱（i=0 时为起点）
            b = perm[i]
            for j in range(i + 1, n):
                c = perm[j]
                d = perm[j + 1] if j + 1 < n else 0  # 后继（j 为末位时回到起点）
                delta = dm[a][c] + dm[b][d] - dm[a][b] - dm[c][d]
                if delta < -1e-9:
                    perm[i:j + 1] = reversed(perm[i:j + 1])
                    length += delta
                    improved = True
                    b = perm[i]  # 反转后 perm[i] 已变，重取避免后续 delta 用旧值累积漂移
    return perm, length


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

    def length(perm: List[int]) -> float:
        return _path_length(dm, perm)

    # ---- 初始种群：1 条贪心解（2-opt 改进后）+ 随机排列 ----
    seq = list(range(1, n + 1))
    # 真贪心注入：按基线贪心逻辑构造初始排列
    unvisited = seq[:]
    greedy_perm: List[int] = []
    cur = 0
    while unvisited:
        nxt = min(unvisited, key=lambda i: dm[cur][i])
        greedy_perm.append(nxt)
        cur = nxt
        unvisited.remove(nxt)
    greedy_perm, greedy_len = _two_opt(dm, greedy_perm, _path_length(dm, greedy_perm))

    population = [greedy_perm]
    base = seq[:]
    pop_size = max(POP_SIZE_FLOOR, min(POP_SIZE_CAP, n * 4))
    for _ in range(pop_size - 1):
        p = base[:]
        random.shuffle(p)
        population.append(p)
    scores = [length(p) for p in population]
    best_idx = min(range(pop_size), key=lambda i: scores[i])
    best_perm, best_len = population[best_idx][:], scores[best_idx]
    if greedy_len < best_len:
        best_perm, best_len = greedy_perm[:], greedy_len

    convergence: List[float] = []
    deadline = time.time() + TIME_BUDGET_S

    # ---- 迭代进化 ----
    for _gen in range(GENERATIONS):
        if time.time() > deadline:
            break
        # 精英保留
        order = sorted(range(pop_size), key=lambda i: scores[i])
        elites = [population[i][:] for i in order[:ELITE_COUNT]]

        # 锦标赛选择生成子代
        def select() -> List[int]:
            contenders = random.sample(range(pop_size), TOURNAMENT_K)
            winner = min(contenders, key=lambda i: scores[i])
            return population[winner]

        children = elites
        while len(children) < pop_size:
            c1, c2 = select(), select()
            if random.random() < CX_RATE and n > 2:
                child = _ox_crossover(c1, c2)
            else:
                child = c1[:]
            if random.random() < MUT_RATE:
                child = _mutate(child)
            children.append(child)

        population = children
        scores = [length(p) for p in population]
        gen_best = min(range(pop_size), key=lambda i: scores[i])

        # 混合策略：对当代最优做 2-opt 改进，并回插种群替换最差个体
        cand_perm, cand_len = _two_opt(dm, population[gen_best][:], scores[gen_best])
        if cand_len < scores[gen_best]:
            worst = max(range(pop_size), key=lambda i: scores[i])
            population[worst] = cand_perm[:]
            scores[worst] = cand_len
        if cand_len < best_len:   # 全局最优只减不增，避免被更差的当代结果覆盖
            best_perm, best_len = cand_perm[:], cand_len
        convergence.append(round(best_len, 3))

    # ---- 可行性：续航约束 ----
    feasible = True
    message = ""
    constraint = req.constraints
    if constraint and constraint.max_flight_range_km is not None:
        if best_len > constraint.max_flight_range_km:
            feasible = False
            message = (f"总航程 {best_len:.2f} km 超过续航 "
                       f"{constraint.max_flight_range_km} km")

    route = [start.id] + [waypoints[i - 1].id for i in best_perm] + [start.id]
    return route, best_len, feasible, convergence

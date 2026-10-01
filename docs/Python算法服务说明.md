# Python 算法服务说明（FastAPI，端口 8000）

> 更新日期：2026-10-01。本文档面向后续维护，说明算法服务结构、各算法实现状态、超参数与扩展方法。
> Java↔Python 字段契约（snake_case）见 [api-contract.md](api-contract.md)。

## 1. 目录结构与职责

```
algorithm/
├── app/
│   ├── main.py                 # FastAPI 入口：CORS 放开、注册路由、/health
│   ├── schemas.py              # pydantic 模型（与契约一致）
│   ├── routers/
│   │   └── plan.py             # POST /api/plan：按 algorithm 分派到各算法
│   └── algorithms/
│       ├── __init__.py         # 算法注册与别名：genetic→ga.solve / ant_colony→aco.solve / astar_algo→astar.solve
│       ├── avoid.py            # 公共避障层：AvoidGrid 栅格化 + 8邻域A* + obstacle_distance_matrix（五算法共用）
│       ├── baselines.py        # 基线：greedy 贪心、nearest 最近邻（避障距离矩阵上选点）
│       ├── ga.py               # 遗传算法（已实现，混合策略；含共用工具 _two_opt/_path_length/_build_distance_matrix）
│       ├── aco.py              # 蚁群算法（已实现，精英蚂蚁系统；复用 ga._build_distance_matrix）
│       └── astar.py            # 避障距离最近邻（obstacle_distance_matrix 上最近邻排序，不做自身栅格化）
├── data/
│   └── gen_data.py             # 模拟数据生成（固定随机种子，可复现）
└── requirements.txt
```

## 2. 启动与自测

```powershell
cd algorithm
uvicorn app.main:app --port 8000
# 健康检查：GET http://127.0.0.1:8000/health → {"status": "ok"}
```

注意：uvicorn 默认**只监听 IPv4 127.0.0.1**，Java 侧已固定用 `http://127.0.0.1:8000` 连接。

## 3. 接口与调用链

`POST /api/plan`（plan.py）：
1. 校验 `algorithm` ∈ {ga, aco, astar, greedy, nearest}，否则 400
2. 分派到 `app/algorithms/` 对应函数
3. 统一包装为 `PlanResponse`：status / algorithm / route / total_distance_km / feasible / run_time_ms / message / convergence(可选)

**返回值约定**：
- 正常返回 4 元组 `(route, distance, feasible, convergence)`（greedy/nearest/astar 的 convergence 恒为 `[]`；GA/ACO 为每代全局最优总航程 km 列表，供论文收敛曲线绘图）
- 不可行时（禁飞区阻隔）返回 5 元组，第 5 位为中文原因 message
- plan.py 兼容解包：`result[:3]` + `len(result) > N` 取可选位
- plan.py 在算法返回后统一调 `avoid.build_full_route_polyline` 构建避障折线 `detail_paths`（Leaflet 顺序 `[[lat,lng],...]`）；算法本身不可行时优先保留其 message 并提前返回

route 格式：航点 id 的访问顺序，**含起点往返**，如 `[100, 3, 1, 2, 100]`。

## 4. 各算法实现状态

### 避障距离统一度量（avoid.py，✅ 2026-10-01）
**核心设计**：避障距离 = 两点间绕开禁飞区圆的最短路径长度（栅格 A* 求得）。五种算法在"构建距离矩阵 / 选点"时统一使用避障距离（而非直线距离），保证访问顺序在避障航程下最优——度量内化在算法内部，不是算法外部分层绕行。

- `class AvoidGrid`：外包矩形（所有点+禁飞区圆心 ±0.02°）、cell 初始 0.001°（≈0.111km/格）、行列 >400 自动放大 cell
- **blocked 标记（2026-10-01 修复穿透 bug）**：① 经纬度方格不是正方形——东西向 km 数必须乘 `cos(lat)`（上海 ≈0.857），用真实 km 椭圆判定格点是否在圆内，否则 blocked 圆东西向缩水 ~14%，路径会切入真实圆边缘（实测最深 0.59km，视觉上"笔直穿过禁飞区"）；② blocked 半径额外膨胀一个 8 邻域对角步长（√2·cell·111.32km），保证相邻可行节点的连线不切入真实圆；③ 起终点可行性按**真实圆**单独判定（`in_real_zone`），A* 邻居扩展时膨胀环仅放行终点格——紧贴圆边（圆外 <0.31km）的航点仍可规划，否则会被膨胀环围死误判 infeasible
- `astar(src, dst)`：8 邻域 A*，g/h 均 Haversine 实际折算（启发式可采纳，保证最优）；起点/终点在真实圆内或不可达 → `(None, None)`
- `obstacle_distance_matrix(start, waypoints, zones)` → `(n+1)x(n+1)` 对称矩阵（0=起点，1..n=航点）：
  - zones 为空 → 直线 haversine 矩阵（性能与改动前一致）
  - zones 非空 → 一次构建 AvoidGrid，逐对点 A* 填矩阵；任一对不可达 → 整个矩阵返回 `None`（禁飞区阻隔）
  - 各算法收到 `None` → 返回 5 元组 `[start.id], 0.0, False, [], "禁飞区阻隔，无法构建可行距离矩阵"`
- `build_full_route_polyline(...)`：plan.py 统一调用，把 route 逐段 A* 拼成完整避障折线（`detail_paths`），供前端画绕行航线

### greedy / nearest（baselines.py，可用）
- 用 `obstacle_distance_matrix` 构建距离矩阵（无禁飞区自动退化为直线 haversine），在矩阵上最近邻选点并累加航程
- nearest 等价于 greedy，独立保留接口作为论文第二条基线
- 续航超 `max_flight_range_km` → feasible=False；禁飞区阻隔 → 5 元组 + message

### ga（ga.py，已实现 ✅ 2026-10-01）
混合遗传算法（模因算法），核心设计：
- 编码：染色体 = 待巡检航点（不含起点）的排列，路径 = 起点 → 排列 → 起点
- 适应度 = 1/总航程；预计算距离矩阵（`_build_distance_matrix(start, waypoints, zones)`：zones 非空 → `obstacle_distance_matrix` 避障距离，为空 → 直线距离），评估 O(n) 查表；矩阵返回 None → 5 元组不可行
- 算子：锦标赛选择(k=3) + OX 顺序交叉 + 交换/逆转变异（各 50%）+ 精英保留(2)
- **混合策略**：初始种群注入真贪心解；每代对当代最优做 `_two_opt()` 2-opt 局部搜索并回插种群（解决纯 GA 过早收敛——初版 60 点时反而差于基线，加 2-opt 后全面反超）
- 续航约束：总航程超过 `max_flight_range_km` → feasible=False

超参数（ga.py 顶部常量，第 7 步实验时可调）：

| 常量 | 默认值 | 说明 |
|---|---|---|
| POP_SIZE_CAP / FLOOR | 120 / 40 | 种群规模（随航点数自适应 min(120, n*4)） |
| GENERATIONS | 600 | 迭代代数 |
| TOURNAMENT_K | 3 | 锦标赛规模 |
| CX_RATE / MUT_RATE | 0.9 / 0.25 | 交叉率 / 变异率 |
| ELITE_COUNT | 2 | 精英保留数 |
| TIME_BUDGET_S | 20.0 | 迭代时间预算（契约 30s 超时内留余量，超时提前返回当前最优） |

实测性能（vs greedy 基线）：n=30 提升 15.3%、n=60 提升 4.2%、n=100 提升 14.0%，耗时 0.9~2.5s。

### aco（aco.py，已实现 ✅ 2026-10-01）
精英蚂蚁系统（Elitist Ant System），核心设计：
- 距离矩阵复用 `ga._build_distance_matrix(start, waypoints, zones)`（避障距离内化，与 GA 同一度量）；矩阵 None → 5 元组不可行
- 模型：每代 n_ants 只蚂蚁从起点出发，按轮盘赌 `p ∝ τ^α · η^β` 逐步构造排列（η=1/d 距离倒数启发式，β 偏大使距离主导搜索）
- 信息素更新：蒸发 `(1-ρ)τ` + 每蚂蚁按 `Q/L` 沉积（含对称边）+ 全局最优蚂蚁额外按精英权重 `e·Q/L_best` 沉积
- **混合策略**：与 GA 一致——初始注入贪心解（2-opt 改进后），每代对当代最优做 `_two_opt()` 改进，克服纯 ACO 收敛慢
- 性能：numpy 向量化（η 矩阵、转移概率乘掩码）；`τ0 = 1/(n·d_min)`，**d_min 取全矩阵非零最小边距**（起点可能与某航点同坐标导致 dmv[0] 含 0，取错会 τ0 爆炸到 5e7）
- 防御：候选权重全零（重复坐标点）时均匀随机挑未访问点，避免轮盘赌除零崩溃
- 续航约束：同 GA，超 `max_flight_range_km` → feasible=False

超参数（aco.py 顶部常量，第 7 步实验时可调）：

| 常量 | 默认值 | 说明 |
|---|---|---|
| ANTS_CAP / FLOOR | 40 / 16 | 蚂蚁数（随航点数自适应 min(40, n*2)） |
| ITERATIONS | 400 | 迭代代数 |
| ALPHA / BETA | 1.0 / 3.0 | 信息素 / 启发式重要度 |
| RHO | 0.3 | 信息素蒸发率 |
| Q | 100.0 | 信息素强度常数 |
| ELITE_W | 4.0 | 精英蚂蚁沉积权重 |
| TIME_BUDGET_S | 20.0 | 迭代时间预算 |

实测性能（vs greedy 基线）：n=20 时 12.35km 与 GA 持平（提升 23.4%），耗时约 4.8s；taskId=2 全链路（Java→Python→planning_result 落库）验证通过。

### astar（✅ 2026-10-01 重写，避障距离最近邻）
astar.py 已整体重写为"避障距离最近邻"：不再自身栅格化，直接复用 avoid 层。

- 用 `obstacle_distance_matrix` 建矩阵（无禁飞区自动退化直线），矩阵 None → 5 元组不可行
- 在避障距离矩阵上做确定性最近邻排序（可复现），total 沿矩阵累加闭环航程，超续航 → feasible=False
- 返回 4 元组（convergence 恒为 []，单次确定性搜索无收敛曲线）
- 原文件的 `_haversine/_build_grid/_astar_segment/_nearest_neighbor_order` 已删除（逻辑统一在 avoid.py，避免重复）
- 前端已随规划请求把禁飞区（camelCase noFlyZones，后端 @JsonAlias 兼容）传给算法服务

> **注意（2026-10-01）**：plan.py 的 factory 曾把 astar 映射到 `algorithms.astar`（模块本身）导致 `'module' object is not callable`——占位时期遗留 bug，已改为 `algorithms.astar_algo`。后端启动**不要依赖 --reload 换代码**（曾出现 reload 子进程 spawn 失败 + 孤儿进程占 8000 端口，旧代码一直返回 500），改代码后手动重启 uvicorn 最稳。

> **关键教训（2026-10-01，`_two_opt` 漂移 bug）**：`_two_opt` 内层 j 循环中执行段反转后，`perm[i]` 已改变，若不重取 `b = perm[i]`，后续 delta 会用**过期的 b** 计算，累积后 claimed 长度假性变小甚至为负（实测漂移 100km+，ACO 曾因此返回 1.27km 的假距离）。该函数被 GA/ACO 共用，修复后两算法的收敛曲线才是真实数据。另：GA 主循环中全局最优不得被当代结果无条件覆盖，必须 `if cand_len < best_len` 才更新。

## 5. 如何新增/修改算法

1. 在 `app/algorithms/` 写实现，函数签名 `(req: PlanRequest) -> (route, distance, feasible, convergence[, message])`
2. 若新增算法名：`plan.py` 的 factory 字典加映射 + `schemas.py` 无需改 + Java 侧 `PlanService.SUPPORTED` 集合同步加 + 前端下拉加 option
3. 距离矩阵统一用 `obstacle_distance_matrix`（或 `ga._build_distance_matrix` 带 zones），保持五算法同一避障度量，实验才有可比性
4. 想调试：直接 `python -c` 构造 PlanRequest 调 solve（参考 ga 的验证方式），不必起服务

## 6. 后续待办（对应工作清单）

- [x] aco.py 蚁群算法（✅ 2026-10-01，精英蚂蚁系统，返回 4 元组含 convergence）
- [x] astar.py 改进 A*（✅ 2026-10-01 重写为避障距离最近邻；"改进点"答辩方向：动态加权启发 / JPS / θ* 可作为后续增强）
- [x] 禁飞区处理（✅ 2026-10-01，避障距离统一度量：五算法共用 obstacle_distance_matrix，顺序在避障航程下最优；plan.py 统一输出 detail_paths 避障折线）
- [ ] 第 7 步实验脚本：不同规模 × 5 算法对比，固定种子，输出 CSV + 收敛曲线图

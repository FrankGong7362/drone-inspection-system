"""公共避障层：栅格化 + 逐段 A*，供所有算法统一使用。

核心设计：避障距离 = 两点间绕开"禁飞区圆 + buffer"的最短路径长度（栅格 A* 求得）。
所有算法在构建距离矩阵/选点时统一用避障距离，保证顺序在避障航程下最优。

API：
- AvoidGrid：栅格化禁飞区（支持 buffer_km 膨胀与自定义分辨率），
  提供 astar(src, dst) 求两点间避障距离与路径
- obstacle_distance_matrix：构建 (n+1)x(n+1) 对称避障距离矩阵（0=起点，1..n=航点）
- segment_crosses_zone：判断直线段是否与"禁飞区圆+buffer"相交（局部平面近似）
- build_full_route_polyline：构建前端画线用的完整航线折线
  （仅直线穿过"圆+buffer"的段走 A* 绕行折线且用细栅格贴合，其余段直线）
"""
import heapq
import math
from typing import List, Optional, Tuple

EARTH_R_KM = 6371.0
MARGIN_DEG = 0.02       # 外包矩形外扩（°）
CELL_INIT = 0.001       # 默认栅格分辨率（°），约 0.111 km/格
GRID_CAP = 400          # 默认行/列上限，超出则放大 cell
CELL_FINE = 0.0005      # 折线重建用细分辨率（°），约 0.056 km/格
GRID_CAP_FINE = 2400    # 细栅格行列上限


def _haversine(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    """Haversine 球面距离（km），输入十进制度。"""
    p1, l1, p2, l2 = map(math.radians, [lat1, lng1, lat2, lng2])
    a = math.sin((p2 - p1) / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin((l2 - l1) / 2) ** 2
    return 2 * EARTH_R_KM * math.asin(math.sqrt(a))


class AvoidGrid:
    """禁飞区栅格化：外包矩形 + cell 缩放 + 障碍标记（支持 buffer 膨胀）。"""

    def __init__(self, zones: List, points: List[Tuple[float, float]],
                 buffer_km: float = 0.0, cell_init: float = CELL_INIT,
                 grid_cap: int = GRID_CAP):
        """
        zones: NoFlyZone 列表（center_lng/center_lat/radius_km）
        points: [(lng, lat), ...] 起点+航点坐标
        buffer_km: 安全距离——blocked 半径 = radius_km + buffer_km，
          保证路径与真实禁飞区圆保持 ≥ buffer_km
        cell_init/grid_cap: 栅格分辨率与行列上限（折线重建用细栅格）
        """
        all_lng = [p[0] for p in points] + [z.center_lng for z in zones]
        all_lat = [p[1] for p in points] + [z.center_lat for z in zones]
        self.min_lng = min(all_lng) - MARGIN_DEG
        self.min_lat = min(all_lat) - MARGIN_DEG
        max_lng = max(all_lng) + MARGIN_DEG
        max_lat = max(all_lat) + MARGIN_DEG

        self.cell = cell_init
        cols = int((max_lng - self.min_lng) / self.cell) + 1
        rows = int((max_lat - self.min_lat) / self.cell) + 1
        scale = max(1.0, math.ceil(max(cols, rows) / grid_cap))
        if scale > 1.0:
            self.cell = cell_init * scale
            cols = int((max_lng - self.min_lng) / self.cell) + 1
            rows = int((max_lat - self.min_lat) / self.cell) + 1
        self.cols = cols
        self.rows = rows

        # 保存 zones 供起终点真实圆判定（膨胀 blocked 仅约束路径节点）
        self.zones = list(zones)
        self.buffer_km = buffer_km
        self.blocked = set()
        km_per_cell_lat = self.cell * 111.32
        # 安全膨胀余量 = 一个 8 邻域对角步长（南北尺度）。
        # 只要相邻可行节点距圆心 >= 半径+buffer+对角步长，其连线任意点
        # （距端点不超过一个步长）就不会切入"圆+buffer"区域。
        margin_km = math.sqrt(2.0) * km_per_cell_lat
        for z in zones:
            c0 = int((z.center_lng - self.min_lng) / self.cell)
            r0 = int((z.center_lat - self.min_lat) / self.cell)
            # 经度方向每格 km 数随纬度 cos 收敛（上海 ≈0.857），必须分别折算
            coslat = max(0.5, math.cos(math.radians(z.center_lat)))
            kmx = self.cell * 111.32 * coslat
            kmy = km_per_cell_lat
            eff_r = z.radius_km + buffer_km + margin_km
            rg_x = eff_r / kmx
            rg_y = eff_r / kmy
            r_lo = max(0, r0 - math.ceil(rg_y))
            r_hi = min(self.rows - 1, r0 + math.ceil(rg_y))
            c_lo = max(0, c0 - math.ceil(rg_x))
            c_hi = min(self.cols - 1, c0 + math.ceil(rg_x))
            for r in range(r_lo, r_hi + 1):
                for c in range(c_lo, c_hi + 1):
                    # 格点相对圆心的真实 km 距离（椭圆判定，非整数格数）
                    dx = (c - c0) * kmx
                    dy = (r - r0) * kmy
                    if dx * dx + dy * dy <= eff_r * eff_r:
                        self.blocked.add((c, r))

    def in_real_zone_ll(self, lat: float, lng: float) -> bool:
        """真实经纬度是否落在任一禁飞区真实圆内（供 astar 端点精确判定）。

        格点化有半格漂移（粗格 E-W 0.19km/N-S 0.22km），贴圆航点（圆外
        余量 < 漂移）经格点判定会被误判圆内 → 必须用真实坐标判端点。
        """
        for z in self.zones:
            if _haversine(lat, lng, z.center_lat, z.center_lng) < z.radius_km:
                return True
        return False

    def in_real_zone(self, cell_pt) -> bool:
        """格点是否落在任一禁飞区的真实圆内（不含 buffer），用于路径中间节点判定。"""
        c, r = cell_pt
        lat = self.min_lat + r * self.cell
        lng = self.min_lng + c * self.cell
        return self.in_real_zone_ll(lat, lng)

    def in_buffer_zone_ll(self, lat: float, lng: float) -> bool:
        """真实经纬度是否落在任一"真实圆+buffer"内（判定端点自身是否已违反安全距离）。"""
        for z in self.zones:
            if _haversine(lat, lng, z.center_lat, z.center_lng) < z.radius_km + self.buffer_km:
                return True
        return False

    def in_buffer_zone(self, cell_pt) -> bool:
        """格点是否落在任一"真实圆+buffer"内：esc 严格模式的内界——环内逃逸只许
        走在安全距离带 [buffer, buffer+margin]，不得切入 buffer 圆贴真实圆绕弧。"""
        c, r = cell_pt
        return self.in_buffer_zone_ll(self.min_lat + r * self.cell,
                                      self.min_lng + c * self.cell)

    def to_grid(self, lng: float, lat: float) -> Tuple[int, int]:
        """经纬度 → 格子坐标 (col, row)。"""
        return (int((lng - self.min_lng) / self.cell),
                int((lat - self.min_lat) / self.cell))

    def astar(self, src: Tuple[float, float], dst: Tuple[float, float]) -> Tuple[Optional[float], Optional[List[Tuple[int, int]]]]:
        """单段 A*：src/dst 为 (lng, lat)。返回 (避障距离km, 格子路径)。
        起点或终点落在真实圆内、或不可达 → 返回 (None, None)。"""
        sg = self.to_grid(src[0], src[1])
        dg = self.to_grid(dst[0], dst[1])
        if sg == dg:
            return 0.0, []
        # 起终点按"真实坐标"判真实圆：圆外但落在膨胀环带内的航点仍可规划
        if self.in_real_zone_ll(src[1], src[0]) or self.in_real_zone_ll(dst[1], dst[0]):
            return None, None

        def latlng(c, r):
            return self.min_lat + r * self.cell, self.min_lng + c * self.cell

        def h(c, r):
            la, lo = latlng(c, r)
            return _haversine(la, lo, self.min_lat + dg[1] * self.cell, self.min_lng + dg[0] * self.cell)

        # 步长按本段中纬度的局部平面度量（E-W 每格 km 随纬度 cos 收缩），
        # 否则赤道度量会系统性高估 E-W 步长约 1/cos(lat)（上海 ≈16%）
        lat0 = math.radians((src[1] + dst[1]) / 2.0)
        kmx = self.cell * 111.32 * max(0.5, math.cos(lat0))
        kmy = self.cell * 111.32

        def step_len(dc, dr):
            return math.hypot(dc * kmx, dr * kmy)

        neighbors = [(dc, dr, step_len(dc, dr))
                     for dc in (-1, 0, 1) for dr in (-1, 0, 1) if (dc, dr) != (0, 0)]

        def esc_bfs(g0, strict):
            """端点格落在膨胀环内时，在环内 Dijkstra 到环边自由格。
            strict=True 内界为"真实圆+buffer"（逃逸只走安全距离带，折线全程 ≥ buffer）；
            strict=False 内界为真实圆（端点自身已违反 buffer 时的旧行为）。
            返回 (exits, came_esc)：exits=[(自由格, 端点→该格 km)]，
            came_esc 支持回溯 端点→出口 格路径。端点自由时退化为 [(g0, 0)]。"""
            if g0 not in self.blocked:
                return [(g0, 0.0)], {g0: None}
            seen = {g0: 0.0}
            came_esc = {g0: None}
            heap = [(0.0, g0)]
            exits = []
            while heap:
                d0, cur = heapq.heappop(heap)
                if d0 > seen.get(cur, math.inf):
                    continue
                c, r = cur
                for dc, dr, w in neighbors:
                    n = (c + dc, r + dr)
                    if not (0 <= n[0] < self.cols and 0 <= n[1] < self.rows):
                        continue
                    if n in self.blocked:
                        inner_hit = self.in_buffer_zone(n) if strict else self.in_real_zone(n)
                        if n not in seen and not inner_hit:
                            seen[n] = d0 + w
                            came_esc[n] = cur
                            heapq.heappush(heap, (d0 + w, n))
                    else:
                        if n not in came_esc:      # 出口格只记一次（最近环内来路）
                            came_esc[n] = cur
                            exits.append((n, d0 + w))
            return exits, came_esc

        # 端点真实坐标已在安全距离外（gap ≥ buffer）→ 严格模式：环内逃逸只走
        # [buffer, buffer+margin] 带、不切入 buffer 圆，折线全程保持 ≥ buffer；
        # 端点自身已在环内（gap < buffer）→ 违反几何必然，退回旧内界（真实圆）。
        # 严格带因格点离散不连通时也退回旧行为，保证不因修复而判死
        src_strict = not self.in_buffer_zone_ll(src[1], src[0])
        dst_strict = not self.in_buffer_zone_ll(dst[1], dst[0])
        src_exits, src_came = esc_bfs(sg, src_strict)
        if src_strict and not src_exits:
            src_exits, src_came = esc_bfs(sg, False)
        dst_exits, dst_came = esc_bfs(dg, dst_strict)
        if dst_strict and not dst_exits:
            dst_exits, dst_came = esc_bfs(dg, False)
        if not src_exits or not dst_exits:
            return None, None
        dst_esc = dict(dst_exits)

        def rem(c, r):
            """到目标的剩余代价估计：dst 侧出口用逃逸距离（更紧且一致），其余用直线。"""
            d = dst_esc.get((c, r))
            return d if d is not None else h(c, r)

        # 多源（src 出口，带逃逸代价）→ 多目标（到达任一 dst 出口即达）。
        # 堆里只存 (f, g, 格)，路径用 came_from 回溯——堆内拷贝整条 path
        # 在大图（细栅格数十万格）上是 O(n·L) 内存/时间爆炸
        open_heap = []
        g_best = {}
        came = {}
        for e, d_e in src_exits:
            g_best[e] = d_e
            heapq.heappush(open_heap, (d_e + rem(e[0], e[1]), d_e, e))
        while open_heap:
            _, g, cur = heapq.heappop(open_heap)
            if cur in dst_esc:
                total = g + dst_esc[cur]
                seg = [cur]
                while seg[-1] in came:
                    seg.append(came[seg[-1]])
                seg.reverse()                       # src出口 → cur
                head = [seg[0]]
                while head[-1] in src_came and src_came[head[-1]] is not None:
                    head.append(src_came[head[-1]])
                head.reverse()                      # sg → src出口
                tail = []
                x = cur
                while dst_came.get(x) is not None:
                    x = dst_came[x]
                    tail.append(x)                  # cur 环内回溯 → dg
                return total, head + seg[1:] + tail
            if g > g_best.get(cur, math.inf):
                continue
            c, r = cur
            for dc, dr, w in neighbors:
                nc, nr = c + dc, r + dr
                if nc < 0 or nr < 0 or nc >= self.cols or nr >= self.rows:
                    continue
                nxt = (nc, nr)
                if nxt in self.blocked:
                    # 自由格一律禁入膨胀环；端点环内通道由 esc_bfs 专门负责
                    continue
                ng = g + w
                if ng < g_best.get(nxt, math.inf):
                    g_best[nxt] = ng
                    came[nxt] = cur
                    heapq.heappush(open_heap, (ng + rem(nc, nr), ng, nxt))
        return None, None


def obstacle_distance_matrix(start, waypoints: List, zones: List,
                             buffer_km: float = 0.0) -> Optional[List[List[float]]]:
    """构建 (n+1)x(n+1) 对称避障距离矩阵（节点0=起点，1..n=航点）。

    zones 为空 → 返回直线 haversine 矩阵（与原 ga 逻辑一致）。
    zones 非空 → 一次构建 AvoidGrid（圆膨胀 buffer_km），逐对点调 astar；
      若任一对不可达 → 返回 None（禁飞区阻隔）。
    """
    nodes = [start] + list(waypoints)
    n = len(nodes)

    # 无禁飞区：直线矩阵
    if not zones:
        dm = [[0.0] * n for _ in range(n)]
        for i in range(n):
            for j in range(i + 1, n):
                d = _haversine(nodes[i].lat, nodes[i].lng, nodes[j].lat, nodes[j].lng)
                dm[i][j] = dm[j][i] = d
        return dm

    # 有禁飞区：避障矩阵
    points = [(node.lng, node.lat) for node in nodes]
    grid = AvoidGrid(zones, points, buffer_km=buffer_km)

    dm = [[0.0] * n for _ in range(n)]
    for i in range(n):
        for j in range(i + 1, n):
            dist, _ = grid.astar(points[i], points[j])
            if dist is None:
                return None  # 禁飞区阻隔
            dm[i][j] = dm[j][i] = dist
    return dm


def segment_crosses_zone(p: Tuple[float, float], q: Tuple[float, float], zones: List,
                         buffer_km: float = 0.0) -> bool:
    """判断直线段 p→q（(lat, lng)）是否与任一"禁飞区圆+buffer"相交。

    以各圆心为原点的局部平面近似（城市尺度误差 << 1m），
    用"圆心到线段的最短距离 < radius_km + buffer_km"判定。
    """
    for z in zones:
        limit = z.radius_km + buffer_km
        coslat = math.cos(math.radians(z.center_lat))
        px = (p[1] - z.center_lng) * 111.32 * coslat
        py = (p[0] - z.center_lat) * 111.32
        qx = (q[1] - z.center_lng) * 111.32 * coslat
        qy = (q[0] - z.center_lat) * 111.32
        dx, dy = qx - px, qy - py
        seg2 = dx * dx + dy * dy
        if seg2 == 0.0:
            dist_sq = px * px + py * py
        else:
            t = -(px * dx + py * dy) / seg2
            t = max(0.0, min(1.0, t))
            cx, cy = px + t * dx, py + t * dy
            dist_sq = cx * cx + cy * cy
        if dist_sq < limit * limit:
            return True
    return False


def build_full_route_polyline(start, waypoints: List, route_ids: List[int], zones: List,
                              buffer_km: float = 0.0) -> Tuple[List[List[float]], float, bool, str]:
    """统一避障折线构建（供 plan.py 调用，前端画线用）。

    仅"直线穿过 圆+buffer"的段用 A* 绕行折线（细栅格 CELL_FINE，紧贴膨胀圆），
    其余段保持直线（消除不必要锯齿）。

    返回：
      full_polyline: [[lat, lng], ...] Leaflet 顺序的完整航线（含所有弯折）
      total_distance_km: 避障总航程
      feasible: 是否可行（禁飞区判断）
      message: 空字符串或不可行原因
    """
    id_to_point = {start.id: start}
    id_to_point.update({w.id: w for w in waypoints})

    # 无禁飞区：直线折线
    if not zones:
        polyline = []
        total = 0.0
        for i in range(len(route_ids) - 1):
            a = id_to_point[route_ids[i]]
            b = id_to_point[route_ids[i + 1]]
            polyline.append([a.lat, a.lng])
            total += _haversine(a.lat, a.lng, b.lat, b.lng)
        last = id_to_point[route_ids[-1]]
        polyline.append([last.lat, last.lng])
        return polyline, round(total, 2), True, ""

    # 有禁飞区：逐段判断——直线不与"圆+buffer"相交的段走直线，相交段才 A* 绕行
    all_points = [(start.lng, start.lat)] + [(w.lng, w.lat) for w in waypoints]
    grid = None  # 惰性构建：只有存在绕行段时才建细栅格

    full_polyline: List[List[float]] = []
    total = 0.0

    def _append(pt: List[float]) -> None:
        # 相邻段共享航点时自动去重
        if not full_polyline or pt[0] != full_polyline[-1][0] or pt[1] != full_polyline[-1][1]:
            full_polyline.append(pt)

    for i in range(len(route_ids) - 1):
        a = id_to_point[route_ids[i]]
        b = id_to_point[route_ids[i + 1]]
        # 航点落在真实禁飞区内 → 不可行
        if any(_haversine(pt.lat, pt.lng, z.center_lat, z.center_lng) < z.radius_km
               for pt in (a, b) for z in zones):
            return [], 0.0, False, "起飞点或巡检点位于禁飞区内，无法规划安全航线"

        if not segment_crosses_zone((a.lat, a.lng), (b.lat, b.lng), zones, buffer_km):
            # 直线段：只保留首尾两点
            if i == 0:
                _append([a.lat, a.lng])
            _append([b.lat, b.lng])
            total += _haversine(a.lat, a.lng, b.lat, b.lng)
            continue

        # 相交段：细栅格（惰性）+ A* 绕行，折线紧贴"圆+buffer"
        if grid is None:
            grid = AvoidGrid(zones, all_points, buffer_km=buffer_km,
                             cell_init=CELL_FINE, grid_cap=GRID_CAP_FINE)
        seg_len, seg_path = grid.astar((a.lng, a.lat), (b.lng, b.lat))
        if seg_len is None:
            return [], 0.0, False, "禁飞区完全阻隔航线，无可达路径"
        # 首/尾格角用精确航点坐标替换（航线直接落在航点上），中间格点保留
        _append([a.lat, a.lng])
        for g in (seg_path or [])[1:-1]:
            _append([grid.min_lat + g[1] * grid.cell,
                     grid.min_lng + g[0] * grid.cell])
        _append([b.lat, b.lng])
        total += seg_len if seg_path else _haversine(a.lat, a.lng, b.lat, b.lng)

    return full_polyline, round(total, 2), True, ""

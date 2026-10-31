# Java ↔ Python 算法服务接口契约

算法服务独立部署（FastAPI，默认端口 8000），后端（Spring Boot :8080）通过 HTTP 调用。
字段统一 **snake_case**、UTF-8、`Content-Type: application/json`；每次调用独立无状态（幂等）；超时建议 30s。

## POST /api/plan

### 请求体
```json
{
  "algorithm": "ga",
  "waypoints": [{"id": 1, "lng": 121.47, "lat": 31.23}],
  "start_point": {"id": 0, "lng": 121.45, "lat": 31.20},
  "constraints": {
    "max_flight_range_km": 20.0,
    "no_fly_buffer_km": 0.5,
    "no_fly_zones": [
      {"center_lng": 121.5, "center_lat": 31.2, "radius_km": 0.5}
    ]
  }
}
```

`constraints` 字段说明：
- `max_flight_range_km`：续航上限（km），可空
- `no_fly_buffer_km`：禁飞区安全距离 buffer（km），默认 0.5；绕行路径与禁飞区圆保持的最小余量，同时作用于避障距离矩阵与绕行折线
- `no_fly_zones`：禁飞区列表，可空

`algorithm` 取值：
- `ga` 遗传算法
- `aco` 蚁群算法
- `astar` 改进 A*
- `greedy` / `nearest` 基线（用于论文对比）

### 响应体
```json
{
  "status": "ok",
  "algorithm": "ga",
  "route": [0, 3, 1, 2, 4, 0],
  "total_distance_km": 12.4,
  "feasible": true,
  "run_time_ms": 230,
  "message": ""
}
```

### 错误
- `status: "error"`、`code`、`message`
- 常见：`UNFEASIBLE` 无可行路径、`BAD_REQUEST` 参数非法、`ALGO_NOT_IMPLEMENTED` 算法未实现

## 健康检查

`GET /health` → `{"status": "ok"}`

## 后端封装约定

- `com.tongji.uav.client.PlanClient` 负责调用。
- 建议加超时与降级：算法服务不可用时返回可读错误，而非让主流程崩溃。
- 响应中的 `convergence`（每代全局最优总航程 km 列表）为 GA/ACO 增量返回的可选字段，供收敛曲线绘制与算法对比实验。

---

# 前端 ↔ Spring Boot 接口约定

前端（Vue3，开发端口 5173）经 Vite 代理访问后端 `http://localhost:8080`，路径前缀 `/api`。
字段统一 **camelCase**（与 Java 实体一致）。

## 登录认证（JWT）

除 `/api/auth/login`、`/api/auth/register` 外，所有 `/api/**` 接口均需请求头
`Authorization: Bearer <token>`；缺失/无效/过期返回 `401`（message 为中文原因）。
前端将 token 存 localStorage，由共享 axios 实例（`src/api/http.js`）统一附带；收到 401 清登录态跳登录页。

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/register` | 注册（自动登录）：`{username, password, nickname?}`；用户名 3-32 位、密码 6-64 位，重复用户名 400 |
| POST | `/api/auth/login` | 登录：`{username, password}`；密码错误 401 |

响应体（登录/注册一致，密码 BCrypt 加密存储，响应不返回密码）：

```json
{
  "token": "eyJhbGciOi...",
  "userId": 1,
  "username": "admin",
  "nickname": "管理员",
  "role": "admin"
}
```

`role` 取值 `admin` / `user`。数据库表 `sys_user`；预置账号 `admin / 123456`；注册固定为 `user`。

### 个人中心（自助）

所有登录用户可用（`AuthInterceptor` 放行 `/api/auth/me` 路径）。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/auth/me` | 获取当前用户资料（返回 `userId`/`username`/`nickname`/`role`/`createTime`，**不含 password**） |
| PUT | `/api/auth/me` | 自助修改昵称：请求体 `{nickname}`（必填，最长 30 字符），返回更新后的 UserVO |
| POST | `/api/auth/me/password` | 自助修改密码：请求体 `{oldPassword, newPassword}`；新密码 8-30 位且同时含字母与数字；新旧密码不能相同；旧密码错误返回 400；**成功后前端应强制退出重新登录** |

### 角色权限（2026-10-17 起，含任务归属）

后端在 JWT 认证后统一拦截（`AuthInterceptor` + `TaskAccessGuard`）：

| 操作 | admin | 任务 owner | 其他用户 |
|---|---|---|---|
| GET 任务列表 | 全部任务 | 自己的 + 公开的 | 自己的 + 公开的 |
| GET 任务详情 / 航点（私有任务） | ✅ | ✅ | ❌ 403 |
| GET 任务详情 / 航点（公开任务） | ✅ | ✅ | ✅ |
| POST `/api/tasks` 创建任务 | ✅ | ✅ | ✅（创建者即 owner） |
| PUT/DELETE 任务、航点增删改/生成 | ✅ 任意任务 | ✅ 自己的 | ❌ 403 |
| POST `/api/plan` 发起规划 | ✅ 任意任务 | ✅ 自己的（含私有） | ✅ 仅公开任务，私有 403 |
| GET `/api/drones` 无人机列表 | ✅ | ✅ | ✅ |
| POST/PUT/DELETE `/api/drones` | ✅ | ❌ 403 | ❌ 403 |
| 任务执行 `/dispatch`、`/finish`、`/cancel`、POST `/logs` | ✅ 任意任务 | ✅ 自己的 | ❌ 403 |
| GET `/latest-plan`、`/logs` | ✅ 任意任务 | ✅ 自己的 | ✅ 仅公开任务 |
| `/api/users` 用户管理 | ✅ | ❌ 403 | ❌ 403 |

权限以 token 内 role claim 与任务 `createdBy` 为准；**管理员修改某用户角色后，该用户需重新登录才获得新角色**。

### 任务归属与可见性

`inspection_task` 新增字段：`createdBy`（创建者 sys_user.id，决定可编辑性）、`visible`（true 公开 / false 私有，默认公开）。

- 创建时通过 POST /api/tasks 请求体 `visible` 指定；创建后 owner 用 `PUT /api/tasks/{id}` body `{"visible": false}` 随时切换。
- 公开任务：所有人可查看航点并发起规划（规划会更新任务的总航程/结果）。
- 私有任务：不出现在他人任务列表中，他人直连访问返回 403。

## 用户管理（仅 admin）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/users` | 用户列表（按 id 升序），响应不含密码 |
| PUT | `/api/users/{id}/role` | 修改角色，body: `{role: "admin" \| "user"}` |
| DELETE | `/api/users/{id}` | 删除用户 |

约束：不能修改自己的角色、不能删除自己的账号（400）；系统至少保留一名管理员（对最后一个 admin 降级/删除返回 400）；目标用户不存在 400；非 admin 访问 403。

用户列表响应体：

```json
[
  {
    "id": 1,
    "username": "admin",
    "nickname": "管理员",
    "role": "admin",
    "createTime": "2026-10-11T21:00:00"
  }
]
```

## 任务与航点

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/tasks` | 任务列表 |
| POST | `/api/tasks` | 创建任务 `{taskName, taskType, visible?}`（visible 缺省 true 公开；true 公开/false 私有），编号/状态/创建者后端自动生成 |
| GET | `/api/tasks/{id}` | 任务详情 |
| PUT | `/api/tasks/{id}` | 更新任务 |
| DELETE | `/api/tasks/{id}` | 删除任务 |
| GET | `/api/tasks/{id}/waypoints` | 任务航点列表（按 seq 升序） |
| POST | `/api/tasks/{id}/waypoints` | **新增单个真实巡检点**（地图选点） |
| DELETE | `/api/tasks/{id}/waypoints/{waypointId}` | **删除单个航点**（地图右键菜单）；删除起点时自动移交起点给剩余 seq 最小者 |
| PUT | `/api/tasks/{id}/waypoints/{waypointId}` | **重命名单个航点**（航点列表改名），body: `{name}`，返回更新后的航点 |
| POST | `/api/tasks/{id}/waypoints/generate?count=8&centerLng=121.5&centerLat=31.2` | **生成 count 个模拟航点**（2~30，默认 8）：以 centerLng/centerLat 为起飞点中心，第一个航点精确落在中心、名称"起飞点"并设为任务起点，其余围绕中心 ±0.02° 随机分布；centerLng/centerLat 必传且需合法（经度 [-180,180]、纬度 [-90,90]），否则 400 |

### POST /api/tasks/{id}/waypoints

请求体（经纬度必须来自地图点击或浏览器定位的真实坐标）：

```json
{
  "name": "教学楼A",
  "longitude": 121.505476,
  "latitude": 31.285107
}
```

响应：完整航点对象。`task_id` 取路径参数、`seq` 自动取当前最大值+1、`is_forbidden=0`；
若为该任务首个航点，自动设为任务起点（`start_point_id`）。

```json
{
  "id": 41,
  "taskId": 2,
  "name": "教学楼A",
  "longitude": 121.505476,
  "latitude": 31.285107,
  "altitude": null,
  "seq": 9,
  "isForbidden": 0
}
```

错误：`400` 任务不存在 / 经纬度数值非法（message 字段为中文原因）。

### PUT /api/tasks/{id}/waypoints/{waypointId}

重命名航点（前端航点列表「改名」）。请求体：

```json
{ "name": "陆家嘴环路巡检点" }
```

响应：更新后的完整航点对象（同上）。校验失败返回 `400`：航点不存在或不属于该任务 / 名称不能为空。

## 路径规划

`POST /api/plan`，请求/响应见前端 `dto/PlanCommand` / `PlanResultVO`（camelCase）。
响应含 `route`（航点 id 顺序，起点往返）、`totalDistanceKm`、`runTimeMs`、`feasible`、
`resultId`（planning_result 落库主键）、`convergence`（GA/ACO 收敛曲线，可选）。
结果同时写入 `planning_result` 表并回填 `inspection_task.total_distance`。

`planning_result` 新增 `detail_paths JSON` 列（2026-10-30）：算法服务返回的避障折线顶点
`[[lat, lng], ...]`（Leaflet 经纬度顺序），用于执行页沿真实折线回放飞行；无该字段时前端回退为航点直连。

## 禁飞区

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/no-fly-zones` | 禁飞区列表（按 id 升序，所有登录用户可读）。返回 `[{id, name, centerLng, centerLat, radiusKm}]`（半径 km）；前端地图以红色半透明圆形展示 |
| POST | `/api/no-fly-zones` | **仅 admin**。新增：`{name?, centerLng, centerLat, radiusKm}`；经度 -180~180、纬度 -90~90、半径 (0, 50] km，name 缺省为「禁飞区」 |
| PUT | `/api/no-fly-zones/{id}` | **仅 admin**。修改同上请求体；id 不存在返回 404 |
| DELETE | `/api/no-fly-zones/{id}` | **仅 admin**。删除；id 不存在返回 404 |

管理页面 `/no-fly-zones`（仅 admin 可见）：列表 + 地图可视化，支持地图点选圆心、点圆直接编辑、虚线预览半径效果。禁飞区变更后，新发起的规划即时生效（规划时按当前禁飞区实时传给算法服务）。

## 无人机档案（2026-10-30）

数据库表 `drone`，实体 `com.tongji.uav.entity.Drone`，状态取值 **可用 / 作业 / 维护 / 离线**。

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/drones` | 所有登录用户 | 列表（按 id 升序） |
| POST | `/api/drones` | 仅 admin | 新增；机号唯一，缺省状态「可用」 |
| PUT | `/api/drones/{id}` | 仅 admin | 修改；id 不存在 404；绑定任务中不允许改为非「作业」状态（400） |
| DELETE | `/api/drones/{id}` | 仅 admin | 删除；绑定任务中不允许删除（400）；id 不存在 404 |

请求体（`DroneRequest`，camelCase）：

```json
{
  "droneNo": "DJI-M300-01",
  "model": "DJI Matrice 300 RTK",
  "batteryCapacity": 5000,
  "maxFlightRange": 60,
  "maxSpeed": 55,
  "status": "可用"
}
```

校验：`droneNo` 必填 ≤32 且唯一；`model` ≤64；`batteryCapacity` 0~100000（mAh）；
`maxFlightRange` 0~1000（km）；`maxSpeed` 0~200（km/h）；`status` 必须为上述四值之一。

响应体：

```json
{
  "id": 1,
  "droneNo": "DJI-M300-01",
  "model": "DJI Matrice 300 RTK",
  "batteryCapacity": 5000.00,
  "maxFlightRange": 60.00,
  "maxSpeed": 55.00,
  "status": "可用",
  "bindTaskId": null
}
```

`bindTaskId` 为当前绑定的任务 id（执行中才有值），由任务下发/完成/取消自动维护。
前端管理页 `/drones`（仅 admin 可见）提供表格 + 新增/编辑弹窗 + 状态标签。

## 任务执行模拟（2026-10-30）

任务状态机：**待执行 → 执行中 → 已完成 / 已取消**；无人机状态随执行联动（可用 ↔ 作业）。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/tasks/{id}/latest-plan` | 最近一次规划结果（含 `detailPaths` 折线）；无规划返回 **200 空体** |
| GET | `/api/tasks/{id}/logs` | 执行日志（按 id 正序），所有可见者均可读 |
| POST | `/api/tasks/{id}/dispatch` | 下发执行，body `{droneId}`；需已有规划且无人机「可用」，否则 400/404 |
| POST | `/api/tasks/{id}/logs` | 追加过程日志 `{action, waypointId?, detail?}`；仅执行中任务 |
| POST | `/api/tasks/{id}/finish` | 完成执行（仅执行中）：任务→已完成，写「降落」日志并释放无人机 |
| POST | `/api/tasks/{id}/cancel` | 取消（仅待执行/执行中）：任务→已取消，写「返航」（执行中）/「异常」（待执行）日志并释放无人机 |

`latest-plan` 响应：

```json
{
  "resultId": 46,
  "algorithm": "ga",
  "route": [0, 3, 1, 2, 4, 0],
  "totalDistanceKm": 58.53,
  "feasible": true,
  "detailPaths": [[31.3397, 121.4702], [31.3412, 121.4751]]
}
```

`logs` 响应元素：

```json
{
  "id": 1,
  "taskId": 2,
  "droneId": 3,
  "action": "起飞",
  "waypointId": null,
  "logTime": "2026-10-30T17:20:00",
  "detail": "任务下发，无人机 DJI-M300-01 起飞"
}
```

动作取值：`起飞` / `到点` / `返航` / `降落` / `异常`（「异常」也用于取消尚未起飞的任务）。
其中 `到点`、`异常` 可由前端追加，其余由后端在执行状态流转时自动写入。

错误约定：重复下发 / 无规划下发 / 无人机不可用 / 对非执行中任务追加日志或完成 / 取消已结束任务 → `400`（message 中文）；无人机不存在 → `404`；非 owner 且非 admin → `403`。

前端执行页 `/execute?taskId=`：按 `detailPaths` 折线做 `requestAnimationFrame` 回放，无人机图标沿线飞行，
到点异步写入「到点」日志，进度条按累计距离推进；动画结束自动完成执行并刷新无人机状态。

## 规划历史与航线导出（2026-10-31）

`planning_result` 为追加式历史（每次规划新增一行），`latest-plan` 仍取最新一条；以下接口用于回看历次结果与导出航线。

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/tasks/{id}/plans` | owner / admin / 公开任务 | 历次规划列表，按 id 倒序；不含 `route`/`detailPaths` 大字段 |
| GET | `/api/tasks/{id}/plans/{resultId}` | 同上 | 单次规划详情（含 `route` 与 `detailPaths`），用于前端重绘 |
| GET | `/api/tasks/{id}/plans/{resultId}/export?format=kml\|csv` | 同上 | 导出航线文件；`format` 缺省 `kml`，其余值 400 |

列表元素：

```json
{
  "resultId": 214,
  "taskId": 2,
  "algorithm": "ga",
  "totalDistanceKm": 58.53,
  "runTimeMs": 914,
  "feasible": true,
  "waypointCount": 13,
  "createTime": "2026-10-30T17:08:02"
}
```

详情响应：

```json
{
  "resultId": 214,
  "taskId": 2,
  "algorithm": "ga",
  "route": [254, 261, 256, "..."],
  "totalDistanceKm": 58.53,
  "runTimeMs": 914,
  "feasible": true,
  "detailPaths": [[31.3397, 121.4722], "..."],
  "createTime": "2026-10-30T17:08:02"
}
```

导出约定：
- `kml`：UTF-8 KML 2.2，`LineString` 使用 `detailPaths`（缺失时回退航点顺序连线），并为每个巡检点生成 Placemark；文件名 `plan-{taskId}-{resultId}-{algorithm}.kml`
- `csv`：UTF-8（带 BOM，Excel 直接打开不乱码），列为 `序号,类型,航点ID,名称,经度,纬度,高度(m),距上一点(km),累计航程(km)`
- 响应头 `Content-Disposition: attachment; filename="..."`

前端规划页新增「规划历史（N）」抽屉：按时间倒序列出历次结果，支持「查看」（重绘到地图）与 KML/CSV 导出。

## 操作审计日志（2026-10-31，仅 admin）

基于 `mission_log` 汇总任务执行留痕，供管理员跨任务查看。

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/audit/logs` | 仅 admin | 分页查询执行留痕，按 id 倒序 |

查询参数：`page`（默认 1）、`size`（默认 20，上限 100）、`taskId`、`action`（起飞/到点/返航/降落/异常）、`keyword`（对 `detail` 模糊匹配）。

响应：

```json
{
  "total": 32,
  "page": 1,
  "size": 5,
  "records": [
    {
      "id": 45,
      "taskId": 2,
      "taskNo": "T1790776886600",
      "taskName": "浏览器联调任务",
      "droneId": 1,
      "droneNo": "DJI-M300-01",
      "action": "降落",
      "waypointId": null,
      "waypointName": null,
      "logTime": "2026-10-30T17:29:54",
      "detail": "无人机 DJI-M300-01 完成航线并降落，任务结束"
    }
  ]
}
```

角色权限补充：`/api/audit` 与 `/api/users` 一致，**全部方法仅 admin**，普通用户访问返回 `403`。
前端审计页 `/audit-logs`（仅 admin）：任务下拉 + 动作筛选 + 关键字搜索 + 服务端分页。


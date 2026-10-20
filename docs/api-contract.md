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

## 禁飞区

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/no-fly-zones` | **禁飞区只读列表**（按 id 升序）。返回 `[{id, name, centerLng, centerLat, radiusKm}]`（半径 km）；前端地图以红色半透明圆形展示，点击弹名称与半径。本次无增删改接口，算法避障待 A* 实现时接入 |

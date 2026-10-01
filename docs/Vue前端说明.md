# Vue 前端说明（Vue3 + Element Plus + Leaflet，端口 5173）

> 更新日期：2026-10-01。本文档面向后续维护，说明页面结构、API 封装、地图实现要点与扩展方法。

## 1. 目录结构与职责

```
frontend/
├── index.html              # 根页面：高德 JS API 加载（key + 安全密钥 + PlaceSearch 插件）
├── vite.config.js          # 端口 5173、/api 代理到 :8080
└── src/
    ├── main.js             # 应用入口：挂载 Element Plus、路由
    ├── App.vue             # 根布局（顶部标题栏）
    ├── router/index.js     # 路由：/ → 重定向 /tasks；/tasks → TaskList；/map → PlanView
    ├── api/plan.js         # 唯一的 HTTP 封装（axios，baseURL '/api'，全部经 Vite 代理转发）
    └── views/
        ├── TaskList.vue    # 任务列表页：列表/新建/删除/跳转规划
        └── PlanView.vue    # 地图规划页（核心页）：高德底图 + 搜索定位 + 选点 + 规划 + 收敛曲线
```

> 旧的 `MapView.vue` 已删除合并进 PlanView（功能重叠），路由路径仍是 `/map`。

## 2. 代理与跨域

`vite.config.js` 中开发代理：`/api → http://localhost:8080`。
前端所有请求只写相对路径 `/api/...`，**无跨域问题**；改后端端口只需改这一处。

## 3. API 封装（api/plan.js）

| 函数 | 请求 | 用途 |
|---|---|---|
| `fetchTasks()` | GET /tasks | 任务列表 |
| `createTask(task)` | POST /tasks | 创建任务（后端自动生成编号/状态） |
| `fetchTask(id)` | GET /tasks/{id} | 任务详情（含 startPointId） |
| `deleteTask(id)` | DELETE /tasks/{id} | 删除任务 |
| `fetchWaypoints(taskId)` | GET /tasks/{id}/waypoints | 航点列表（按 seq 升序） |
| `addWaypoint(taskId, {name, longitude, latitude})` | POST /tasks/{id}/waypoints | **地图选点新增真实巡检点** |
| `generateWaypoints(taskId, count)` | POST /tasks/{id}/waypoints/generate | 生成模拟航点 |
| `requestPlan(payload)` | POST /plan | 触发路径规划 |

**字段约定**：后端返回 camelCase（`totalDistanceKm`、`startPointId`），读响应时用 camelCase；不要按 snake_case 读（读不到）。

## 4. TaskList.vue（任务列表页）

- 表格列：任务编号/名称/类型/状态/总航程；操作：规划（跳 `/map?taskId=x`）、删除
- 新建弹窗：任务名称（必填）+ 类型 + 航点数量（2~30，默认 8）；提交后**先 createTask 再 generateWaypoints**，最后刷新列表
- 扩展提示：后续"任务状态流转"（待执行→执行中→已完成）在此页加状态筛选/操作按钮

## 5. PlanView.vue（地图规划页，核心）

### 工具栏功能
| 控件 | 说明 |
|---|---|
| 算法下拉 | ga/greedy/nearest 可用；aco/astar `disabled`（算法未实现，后端会 400） |
| 续航(km) | 传入 constraints.maxFlightRangeKm |
| 开始规划 | 调 requestPlan；**payload 必须含 taskId/algorithm/startPoint/waypoints/constraints 全量字段**（后端校验必填，缺了 400） |
| 定位到我 | 浏览器 geolocation（真实坐标），失败有提示 |
| 搜索地名定位 | 高德 AMap.PlaceSearch（amapReady 才显示），结果弹窗点行定位，详见下文"地名搜索定位" |
| 新增巡检点 | 进入选点模式（按钮变橙色）→ 点地图取真实点击坐标 → ElMessageBox.prompt 填名称 → addWaypoint 入库 → 图层即时刷新并退出选点模式 |
| 航点右键菜单 | 右键航点圆点 → teleport 菜单（设为起飞点 `updateTask(id,{startPointId})` / 删除该巡检点 `deleteWaypoint`）→ 操作后重绘图层；点地图空白处关闭菜单 |
| 生成模拟航点 | 兜底造数据（会清空该任务旧航点） |

**按钮布局**：面板内竖排按钮统一放 `.btn-col`（flex column + gap:8px），避免 Element 默认 `margin-left:12px` 导致的贴合/错位。

**定位与安全上下文**：浏览器 geolocation 仅在安全上下文可用，`http://localhost` 是例外，因此开发期用 `http://localhost:5173` 直接定位即可。（2026-10-01 曾为高德域名白名单配置过 uav.local.com + HTTPS 自签证书方案，白名单撤销后已一并恢复为 localhost + http。）定位失败时前端会识别 secure origins 错误并给出指引。

**续航约束语义**：`max_flight_range_km` 为硬约束——总航程超限返回 `feasible=false`（仍给出路径供参考），页面有**四重不可行提示**：顶部任务条深红色横幅（含数值对比）、结果卡红色 Alert（"超出续航上限，规划不可行"+建议）、总航程大数字变红（#dc2626）、ElMessage.warning toast；恢复可行后自动回到绿色/橙色正常态。greedy/nearest/ga 均已实现该判定（baselines.py `_check_range`，ga.py 收敛后判定）。

### 地图实现要点
- 底图：**高德瓦片**（`webrd0{1-4}.is.autonavi.com`，国内可加载，中文标注），Leaflet `subdomains: ['1','2','3','4']`
- 标记：全部用 `L.circleMarker`（**不要用默认 L.marker**，其图标图片路径在打包环境会 404）
- 起点高亮：id === task.startPointId → 橙色大圆 + popup 加【起点】前缀
- 图层管理：`waypointLayer`（航点）/ `pathLayer`（规划路径）/ `pickedMarker`（选点），重绘前先 `removeLayer` 防叠加
- 规划路径绘制：`route` 数组（航点 id 顺序）→ `posMap` 映射为坐标 → `L.polyline`（红色虚线，起点往返闭合）
- 收敛曲线：GA/ACO 响应含 `convergence`（每代最优航程），用内联 SVG `<polyline>` 画迷你图（`convPoints` computed 做归一化），无第三方图表库

### 规划响应字段（camelCase）
`resultId`（planning_result 落库主键）、`algorithm`、`route`、`totalDistanceKm`、`runTimeMs`、`feasible`、`convergence`(可选)。
错误提示统一读 `e.response?.data?.message`（后端 ResponseStatusException 的中文原因，需 application.yml 开 `include-message: always`）。

## 6. 启动与扩展

```powershell
cd frontend
npm install        # 首次（已装过可跳过；国内可用 --registry=https://registry.npmmirror.com）
npm run dev
```

### 地名搜索定位（已接入高德 JS API，2026-10-01）
- `index.html` 加载 AMap JS API 2.0（key + `window._AMapSecurityConfig.securityJsCode`），插件 `AMap.PlaceSearch`
- PlanView 工具栏搜索框 → `searchPlaces()` → 结果 el-dialog 列表（名称/地址/坐标）→ 点行 `locateTo()` 定位并显示绿色标记
- 坐标系说明：高德 API 与瓦片均为 **GCJ-02**，搜索结果/地图点击/入库坐标全链路统一 GCJ-02，地图显示无偏移；两点间相对距离受 GCJ-02 扰动影响可忽略，算法 Haversine 计算不受影响
- **访问方式**：`http://localhost:5173` 直接访问（2026-10-01 起高德 key 已撤销域名白名单，任何域名可用；此前 uav.local.com + hosts + allowedHosts 方案已全部恢复移除）
- 若搜索报 `INVALID_USER_DOMAIN`，说明高德控制台为该 key 配置了域名白名单——去高德控制台去掉白名单或把当前域名加入
- `amapReady` 检测：AMap 加载失败时搜索框不显示，其余功能不受影响

### 扩展指南
1. **新增页面**：views/ 加 .vue → router/index.js 注册 → 需要的接口在 api/plan.js 加函数（保持唯一出口，组件内不要直接写 axios）
2. **算法对比演示页**（第 7 步）：新页面复用 requestPlan，把多种算法结果汇总成表格 + 收敛曲线对比
3. **执行动画**（第 3 步任务执行）：按 route 顺序定时平移一个 circleMarker 模拟无人机飞行，配 mission_log 列表
4. Leaflet 组件卸载务必 `map.remove()`（onBeforeUnmount 已处理），否则热更新会残留定时器/瓦片请求

# Java 后端说明（Spring Boot 3.x + MyBatis-Plus，端口 8080）

> 更新日期：2026-10-01。本文档面向后续维护，说明每个类的职责、接口清单与调用链。
> 接口字段契约见 [api-contract.md](api-contract.md)。

## 1. 整体架构与调用链

```
前端 Vue3 (:5173)  --Vite代理 /api-->  Spring Boot (:8080)  --RestTemplate-->  FastAPI 算法服务 (:8000)
                                         │
                                         └--> MySQL (uav_inspection)
```

核心调用链（路径规划）：
`PlanController` → `PlanService`（校验/落库/回填）→ `PlanClient` → `POST http://127.0.0.1:8000/api/plan` → 返回 `PlanResponse` → 写 `planning_result` 表 → 更新 `inspection_task.total_distance` → 返回 `PlanResultVO` 给前端。

## 2. 包结构（com.tongji.uav）

### 入口与配置
| 文件 | 职责 |
|---|---|
| `UavApplication.java` | 启动类 |
| `config/AppConfig.java` | `RestTemplate` Bean：连接 5s / 读取 30s 超时（契约约定），**显式 `Proxy.NO_PROXY`** 防止本机代理软件劫持对 127.0.0.1 的调用 |
| `config/CorsConfig.java` | 全局 CORS 放开（开发期） |

### controller 层（HTTP 入口）
| 文件 | 接口 |
|---|---|
| `TaskController.java` | `GET/POST /api/tasks`、`GET/PUT/DELETE /api/tasks/{id}`（PUT 可部分字段，如设为起飞点传 `{startPointId}`）、`GET /api/tasks/{id}/waypoints`、`POST /api/tasks/{id}/waypoints`（地图选点新增真实航点）、`DELETE /api/tasks/{id}/waypoints/{waypointId}`（删除航点；删起点自动移交）、`POST /api/tasks/{id}/waypoints/generate?count=8&centerLng=&centerLat=`（生成模拟航点：count∈[2,30] 默认 8，centerLng/centerLat 必传为起飞点中心，第一个航点精确落中心、名称"起飞点"并设为任务起点） |
| `NoFlyZoneController.java` | `GET /api/no-fly-zones`：禁飞区只读列表（按 id 升序），供前端地图红色半透明圆形展示；本次仅查询，无增删改 |
| `PlanController.java` | `POST /api/plan`：接收 `PlanCommand`，异常映射为 400（参数非法）/ 502（算法服务拒绝）/ 503（算法服务不可达） |

### service 层（业务逻辑）
| 文件 | 职责 |
|---|---|
| `TaskService.java` | 任务 CRUD；`createTask()` 自动生成任务编号（`T+时间戳`）、默认状态"待执行" |
| `WaypointService.java` | `listByTask()` 按 seq 排序查航点；`addWaypoint()` 地图选点新增（校验经纬度范围、seq=最大+1、首个航点自动设为任务起点）；`generateForTask(taskId, count, centerLng, centerLat)` 生成模拟航点（count∈[2,30]、中心坐标必传校验，清空旧点，第一个航点精确落中心名称"起飞点"并设起点，其余 ±0.02° 随机分布）。**禁飞区规避（2026-10-01）**：注入 `NoFlyZoneMapper`，`inNoFlyZone(lng, lat)` 用 haversine（R=6371）判点是否落在任一禁飞区圆内；起飞点在禁飞区 → 抛 400"起飞点位于禁飞区，请更换起飞点"；候选航点落禁飞区 → 重试最多 50 次，仍失败抛"无法在起飞点附近生成足够航点，请更换起飞点或调整禁飞区" |
| `PlanService.java` | 规划主流程：校验（taskId/起点/航点/算法名 ∈ {ga,aco,astar,greedy,nearest}，默认 greedy）→ 组装 `PlanRequest` 调 `PlanClient` → 结果写 `planning_result`（route 用 JSON 存储）→ 回填任务 `total_distance` → 返回 `PlanResultVO` |

### client 层
| 文件 | 职责 |
|---|---|
| `PlanClient.java` | 通过 `RestTemplate` 调算法服务；`base-url` 读 `application.yml` 的 `app.algorithm-service.base-url`（**用 127.0.0.1 而非 localhost**，避免 Java 将 localhost 解析为 IPv6 ::1 而 uvicorn 只监听 IPv4） |

### dto 层（数据传输对象）
| 文件 | 方向 | 说明 |
|---|---|---|
| `PlanCommand.java` | 前端 → 后端 | camelCase；字段：taskId、algorithm、waypoints[]、startPoint、constraints（constraints 复用 PlanRequest.Constraint） |
| `PlanRequest.java` | 后端 → 算法服务 | **类及内部 Constraint/NoFlyZone 类都标了 `@JsonNaming(SnakeCaseStrategy)`**，序列化为契约 snake_case（start_point / max_flight_range_km）；内部类字段**同时标了 `@JsonAlias`**（camelCase 别名），保证前端 camelCase 输入也能正确反序列化 |
| `PlanResponse.java` | 算法服务 → 后端 | 同样 snake_case 反序列化；含 `convergence`（GA/ACO 收敛曲线，可选）、`detail_paths`（避障折线 `List<List<Double>>`，Leaflet 顺序 [lat,lng]，可选） |
| `PlanResultVO.java` | 后端 → 前端 | camelCase；含 `resultId`（planning_result 落库主键）、`route`、`totalDistanceKm`、`feasible`、`runTimeMs`、`convergence`、`detailPaths`（前端优先用它画绕行折线，无则回退 route 直线） |

> **注意**：三层 DTO 转换是刻意的——对外（前端）camelCase、对算法服务 snake_case，改字段时三处要同步。
> **关键教训（2026-10-01）**：`@JsonNaming` **同时影响输入与输出**——只有 @JsonNaming 而无 @JsonAlias 时，camelCase 输入字段会被 Jackson **静默忽略**（续航约束曾因此失效且无报错）。新增跨端 DTO 字段必须同步加 @JsonAlias，避免"静默丢字段"。

### entity / mapper 层
| 文件 | 对应表 | 要点 |
|---|---|---|
| `entity/Task.java` + `mapper/TaskMapper.java` | `inspection_task` | 字段：taskNo/taskName/taskType/status/startPointId/totalDistance 等 |
| `entity/Waypoint.java` + `mapper/WaypointMapper.java` | `waypoint` | taskId/name/longitude/latitude/altitude/seq/isForbidden |
| `entity/NoFlyZone.java` + `mapper/NoFlyZoneMapper.java` | `no_fly_zone` | name/centerLng/centerLat/radiusKm（半径 km）；只读列表接口 + 前端展示；2026-10-01 已贯穿：① 算法服务五算法避障距离排序 ② WaypointService 生成航点避开禁飞区 |
| `entity/PlanningResult.java` + `mapper/PlanningResultMapper.java` | `planning_result` | **`route` 字段用 `JacksonTypeHandler` 存 MySQL JSON 列，`@TableName(autoResultMap = true)` 必须保留**，否则读写 route 会失败 |

## 3. 配置（application.yml）

```yaml
server:
  port: 8080
  error:
    include-message: always   # 让 ResponseStatusException 的 message 返回给前端展示
spring:
  datasource:                  # 本机 MySQL 密码已填好
app:
  algorithm-service:
    base-url: http://127.0.0.1:8000
```

## 4. 数据库

- 库：`uav_inspection`，建表脚本 `db/schema.sql`（6 张表：inspection_task / waypoint / drone / mission_log / planning_result / no_fly_zone）
- **已接入**：inspection_task、waypoint、planning_result、no_fly_zone（只读展示，2026-10-01）
- **未接入**（仅建表）：drone、mission_log —— 对应后续"无人机管理、执行日志"功能

## 5. 如何扩展（示例：新增一张表 + 接口）

1. `db/schema.sql` 加表 → 在 MySQL 执行
2. `entity/Xxx.java`（`@TableName` + `@TableId(type = IdType.AUTO)`）
3. `mapper/XxxMapper.java`（`extends BaseMapper<Xxx>` + `@Mapper`）
4. 需要 Service 则 `extends ServiceImpl<XxxMapper, Xxx>`（**不要给 ServiceImpl 写带参构造器**，用 `@Autowired` 字段注入其他 Service）
5. `controller/XxxController.java`（`@RestController + @RequestMapping`）
6. 有算法交互的新字段：前端 DTO（camelCase）与 `PlanRequest/PlanResponse`（snake_case + `@JsonNaming`）同步加

## 6. 启动与已知坑

```powershell
# 启动（二选一）
# a) IDEA / Trae 中直接运行 UavApplication
# b) 命令行打包后运行：
mvn package -DskipTests
java -jar target\uav-backend-0.0.1-SNAPSHOT.jar
```

已知坑（都已规避，改动时勿回退）：
1. **中文路径**：`mvn spring-boot:run` 在含中文的项目路径下 fork JVM 会 `ClassNotFoundException` → 用上面的打包方式或 IDE 运行
2. **打包时 jar 被锁**：先停掉运行中的 8080 进程再 `mvn package`
3. **localhost 解析为 IPv6** 导致连不上算法服务 → base-url 固定 `127.0.0.1`
4. **系统代理劫持** → RestTemplate 已设 `Proxy.NO_PROXY`
5. JDK 用 17（本机 `C:\Program Files\Java\jdk-17`），默认 PATH 里的 Java 8 不行

import http from './http'

// 统一实例已内置 JWT 请求头与 401 跳转（见 http.js）

export function fetchTasks() {
  return http.get('/tasks')
}

export function createTask(task) {
  return http.post('/tasks', task)
}

export function deleteTask(id) {
  return http.delete(`/tasks/${id}`)
}

export function fetchTask(id) {
  return http.get(`/tasks/${id}`)
}

export function generateWaypoints(taskId, count = 8, centerLng, centerLat) {
  return http.post(`/tasks/${taskId}/waypoints/generate`, null, {
    params: { count, centerLng, centerLat }
  })
}

// 新增单个真实巡检点（地图选点），data: { name, longitude, latitude }
export function addWaypoint(taskId, data) {
  return http.post(`/tasks/${taskId}/waypoints`, data)
}

export function fetchWaypoints(taskId) {
  return http.get(`/tasks/${taskId}/waypoints`)
}

// 触发路径规划：后端 /api/plan（后端再调 Python 算法服务）
export function requestPlan(payload) {
  return http.post('/plan', payload)
}

// 更新任务（部分字段），如设为起飞点：updateTask(id, { startPointId })
export function updateTask(id, data) {
  return http.put(`/tasks/${id}`, data)
}

// 删除单个航点（地图右键菜单），删除起点时后端自动移交起点
export function deleteWaypoint(taskId, waypointId) {
  return http.delete(`/tasks/${taskId}/waypoints/${waypointId}`)
}

// 重命名单个航点（航点列表改名）
export function renameWaypoint(taskId, waypointId, name) {
  return http.put(`/tasks/${taskId}/waypoints/${waypointId}`, { name })
}

// 禁飞区列表（只读，地图红色半透明圆形展示）
export function fetchNoFlyZones() {
  return http.get('/no-fly-zones')
}

// 规划历史：历次 planning_result（最新在前）
export function fetchPlanHistory(taskId) {
  return http.get(`/tasks/${taskId}/plans`)
}

// 单次规划详情（含 route / detailPaths），用于历史回看重绘
export function fetchPlanDetail(taskId, resultId) {
  return http.get(`/tasks/${taskId}/plans/${resultId}`)
}

// 导出航线：format = kml | csv（返回二进制，前端触发下载）
export function exportPlan(taskId, resultId, format) {
  return http.get(`/tasks/${taskId}/plans/${resultId}/export`, {
    params: { format },
    responseType: 'blob'
  })
}

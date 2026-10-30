import http from './http'

// 任务执行模拟：下发 / 到点日志 / 完成 / 取消 / 日志 / 最近规划
// 写操作仅任务所有者或 admin（后端 TaskAccessGuard 校验）

export function fetchLatestPlan(taskId) {
  return http.get(`/tasks/${taskId}/latest-plan`)
}

export function fetchMissionLogs(taskId) {
  return http.get(`/tasks/${taskId}/logs`)
}

export function dispatchTask(taskId, droneId) {
  return http.post(`/tasks/${taskId}/dispatch`, { droneId })
}

export function appendMissionLog(taskId, data) {
  return http.post(`/tasks/${taskId}/logs`, data)
}

export function finishTask(taskId) {
  return http.post(`/tasks/${taskId}/finish`)
}

export function cancelTask(taskId) {
  return http.post(`/tasks/${taskId}/cancel`)
}

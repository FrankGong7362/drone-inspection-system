import http from './http'

// 操作审计日志（仅 admin）：分页 + 任务 / 动作 / 关键字筛选
export function fetchAuditLogs(params) {
  return http.get('/audit/logs', { params })
}

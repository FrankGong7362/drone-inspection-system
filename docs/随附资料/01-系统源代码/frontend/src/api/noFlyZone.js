import http from './http'

// 禁飞区（圆形：中心经纬度 + 半径 km）
// 列表所有登录用户可读；新增/修改/删除仅 admin（后端 AuthInterceptor 校验）

export function fetchNoFlyZones() {
  return http.get('/no-fly-zones')
}

// data: { name, centerLng, centerLat, radiusKm }
export function createNoFlyZone(data) {
  return http.post('/no-fly-zones', data)
}

export function updateNoFlyZone(id, data) {
  return http.put(`/no-fly-zones/${id}`, data)
}

export function deleteNoFlyZone(id) {
  return http.delete(`/no-fly-zones/${id}`)
}

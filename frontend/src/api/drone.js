import http from './http'

// 无人机档案：列表所有登录用户可读；新增/修改/删除仅 admin

export function fetchDrones() {
  return http.get('/drones')
}

// data: { droneNo, model, batteryCapacity, maxFlightRange, maxSpeed, status }
export function createDrone(data) {
  return http.post('/drones', data)
}

export function updateDrone(id, data) {
  return http.put(`/drones/${id}`, data)
}

export function deleteDrone(id) {
  return http.delete(`/drones/${id}`)
}

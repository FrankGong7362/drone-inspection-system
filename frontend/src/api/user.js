import http from './http'

// 用户管理（仅 admin）：列表返回 [{ id, username, nickname, role, createTime }]
export function fetchUsers() {
  return http.get('/users')
}

// 修改角色：data { role: 'admin' | 'user' }
export function updateUserRole(id, role) {
  return http.put(`/users/${id}/role`, { role })
}

export function deleteUser(id) {
  return http.delete(`/users/${id}`)
}

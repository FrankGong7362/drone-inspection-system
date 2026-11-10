import http from './http'

// 登录：data { username, password }，返回 { token, userId, username, nickname, role }
export function login(data) {
  return http.post('/auth/login', data)
}

// 注册：data { username, password, nickname? }，返回同上（自动登录）
export function register(data) {
  return http.post('/auth/register', data)
}

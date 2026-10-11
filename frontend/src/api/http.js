import axios from 'axios'

// 统一 axios 实例：Vite 代理 /api → 后端 :8080
const http = axios.create({ baseURL: '/api' })

// 请求拦截：自动附带 JWT 令牌
http.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// 响应拦截：401 清登录态并跳登录页（登录页本身除外，避免错误密码时刷新表单）
http.interceptors.response.use(
  (resp) => resp,
  (err) => {
    if (err.response && err.response.status === 401 && location.pathname !== '/login') {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      location.href = '/login'
    }
    return Promise.reject(err)
  }
)

export default http

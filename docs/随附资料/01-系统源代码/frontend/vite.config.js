import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // 前端请求 /api/* 转发到 Spring Boot 后端
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})

<template>
  <el-container class="layout">
    <el-header class="header">
      <span>无人机巡检任务管理与路径规划系统</span>
      <div v-if="username" class="user-area">
        <span class="nick">{{ nickname }}</span>
        <el-button size="small" text class="logout" @click="logout">退出</el-button>
      </div>
    </el-header>
    <el-main>
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const router = useRouter()
const route = useRoute()
const username = ref('')
const nickname = ref('')

// 每次路由变化都从 localStorage 刷新头部用户信息（登录/注册/退出后立即生效）
function refreshUser() {
  const u = JSON.parse(localStorage.getItem('user') || 'null')
  username.value = u?.username || ''
  nickname.value = u?.nickname || u?.username || ''
}
refreshUser()
watch(() => route.path, refreshUser)

function logout() {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  refreshUser()
  router.push('/login')
}
</script>

<style>
.layout {
  height: 100vh;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #0f3d91;
  color: #fff;
  font-size: 18px;
  font-weight: 600;
}
.user-area {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 400;
}
.logout {
  color: #fff;
}
</style>

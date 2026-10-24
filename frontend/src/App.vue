<template>
  <el-container class="layout">
    <el-header class="header">
      <span>无人机巡检任务管理与路径规划系统</span>
      <div v-if="username" class="user-area">
        <el-button v-if="role === 'admin'" size="small" text class="nav-link" @click="router.push('/users')">用户管理</el-button>
        <el-button v-if="role === 'admin'" size="small" text class="nav-link" @click="router.push('/no-fly-zones')">禁飞区管理</el-button>
        <el-button size="small" text class="nav-link" @click="router.push('/profile')">个人中心</el-button>
        <span class="nick">{{ nickname }}</span>
        <el-tag :type="role === 'admin' ? 'danger' : 'info'" size="small">{{ role === 'admin' ? '管理员' : '普通用户' }}</el-tag>
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
const role = ref('')

// 每次路由变化都从 localStorage 刷新头部用户信息（登录/注册/退出后立即生效）
function refreshUser() {
  const u = JSON.parse(localStorage.getItem('user') || 'null')
  username.value = u?.username || ''
  nickname.value = u?.nickname || u?.username || ''
  role.value = u?.role || ''
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
.nav-link {
  color: #fff;
}
</style>

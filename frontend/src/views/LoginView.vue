<template>
  <div class="login-wrap">
    <el-card class="login-card" shadow="always">
      <h2 class="title">无人机巡检任务管理与路径规划系统</h2>
      <el-tabs v-model="tab" stretch>
        <!-- 登录 -->
        <el-tab-pane label="登录" name="login">
          <el-form ref="loginFormRef" :model="loginForm" :rules="rules" size="large">
            <el-form-item prop="username">
              <el-input v-model="loginForm.username" placeholder="用户名" :prefix-icon="UserIcon" />
            </el-form-item>
            <el-form-item prop="password">
              <el-input v-model="loginForm.password" type="password" placeholder="密码"
                        :prefix-icon="LockIcon" show-password @keyup.enter="doLogin" />
            </el-form-item>
            <el-button type="primary" size="large" class="submit" :loading="loading" @click="doLogin">
              登 录
            </el-button>
          </el-form>
        </el-tab-pane>
        <!-- 注册 -->
        <el-tab-pane label="注册" name="register">
          <el-form ref="regFormRef" :model="regForm" :rules="rules" size="large">
            <el-form-item prop="username">
              <el-input v-model="regForm.username" placeholder="用户名（3-32 位）" :prefix-icon="UserIcon" />
            </el-form-item>
            <el-form-item prop="password">
              <el-input v-model="regForm.password" type="password" placeholder="密码（6 位以上）"
                        :prefix-icon="LockIcon" show-password />
            </el-form-item>
            <el-form-item prop="confirm">
              <el-input v-model="regForm.confirm" type="password" placeholder="确认密码"
                        :prefix-icon="LockIcon" show-password @keyup.enter="doRegister" />
            </el-form-item>
            <el-form-item prop="nickname">
              <el-input v-model="regForm.nickname" placeholder="昵称（可选）" />
            </el-form-item>
            <el-button type="primary" size="large" class="submit" :loading="loading" @click="doRegister">
              注 册
            </el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>
      <el-alert v-if="errorMsg" :title="errorMsg" type="error" :closable="false" class="err" />
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User as UserIcon, Lock as LockIcon } from '@element-plus/icons-vue'
import { login, register } from '../api/auth'

const router = useRouter()
const tab = ref('login')
const loading = ref(false)
const errorMsg = ref('')
const loginFormRef = ref()
const regFormRef = ref()

const loginForm = reactive({ username: '', password: '' })
const regForm = reactive({ username: '', password: '', confirm: '', nickname: '' })

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

function saveSession(data) {
  localStorage.setItem('token', data.token)
  localStorage.setItem('user', JSON.stringify({
    userId: data.userId, username: data.username, nickname: data.nickname, role: data.role
  }))
}

function errText(e) {
  return e.response?.data?.message || '网络错误，请稍后重试'
}

async function doLogin() {
  await loginFormRef.value.validate()
  loading.value = true
  errorMsg.value = ''
  try {
    const { data } = await login({ username: loginForm.username, password: loginForm.password })
    saveSession(data)
    ElMessage.success(`欢迎回来，${data.nickname || data.username}`)
    router.push('/tasks')
  } catch (e) {
    errorMsg.value = errText(e)
  } finally {
    loading.value = false
  }
}

async function doRegister() {
  await regFormRef.value.validate()
  if (regForm.password !== regForm.confirm) {
    errorMsg.value = '两次输入的密码不一致'
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    const { data } = await register({
      username: regForm.username,
      password: regForm.password,
      nickname: regForm.nickname || undefined
    })
    saveSession(data)
    ElMessage.success(`注册成功，${data.nickname || data.username}`)
    router.push('/tasks')
  } catch (e) {
    errorMsg.value = errText(e)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrap {
  display: flex;
  justify-content: center;
  align-items: center;
  height: calc(100vh - 60px);
  background: linear-gradient(135deg, #e8f0fe 0%, #f5f7fa 100%);
}
.login-card {
  width: 400px;
  padding: 8px 12px;
}
.title {
  text-align: center;
  font-size: 18px;
  color: #0f3d91;
  margin: 4px 0 16px;
}
.submit {
  width: 100%;
}
.err {
  margin-top: 12px;
}
</style>

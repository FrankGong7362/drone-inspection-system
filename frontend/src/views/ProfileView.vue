<template>
  <div class="profile-page">
    <div class="section">
      <h3>基本信息</h3>
      <el-form label-width="100px" class="form">
        <el-form-item label="用户名">
          <el-input :model-value="profile.username" disabled />
        </el-form-item>
        <el-form-item label="角色">
          <el-tag :type="profile.role === 'admin' ? 'danger' : 'info'">
            {{ profile.role === 'admin' ? '管理员' : '普通用户' }}
          </el-tag>
        </el-form-item>
        <el-form-item label="注册时间">
          <span>{{ profile.createTime }}</span>
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="nicknameForm.nickname" maxlength="30" show-word-limit />
          <el-button type="primary" :loading="nicknameSaving" @click="saveNickname" style="margin-left: 12px">保存</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="section">
      <h3>修改密码</h3>
      <el-form label-width="100px" class="form">
        <el-form-item label="旧密码">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="pwdForm.newPassword" type="password" show-password autocomplete="new-password" placeholder="8-30 位，需同时包含字母和数字" />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item>
          <el-button type="warning" :loading="pwdSaving" @click="changePassword">确认修改</el-button>
          <span v-if="pwdMsg" class="pwd-msg" :class="pwdMsgOk ? 'ok' : 'err'">{{ pwdMsg }}</span>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '../api/http'
import { ElMessage } from 'element-plus'

const router = useRouter()
const profile = ref({ username: '', nickname: '', role: '', createTime: '' })
const nicknameForm = ref({ nickname: '' })
const nicknameSaving = ref(false)

const pwdForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })
const pwdSaving = ref(false)
const pwdMsg = ref('')
const pwdMsgOk = ref(true)

onMounted(async () => {
  try {
    const { data } = await request.get('/auth/me')
    profile.value = data
    nicknameForm.value.nickname = data.nickname || ''
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '加载用户信息失败')
  }
})

async function saveNickname() {
  nicknameSaving.value = true
  try {
    const { data } = await request.put('/auth/me', { nickname: nicknameForm.value.nickname })
    profile.value.nickname = data.nickname
    // 同步刷新 localStorage，让头部昵称立即更新
    const u = JSON.parse(localStorage.getItem('user') || '{}')
    u.nickname = data.nickname
    localStorage.setItem('user', JSON.stringify(u))
    ElMessage.success('昵称已更新')
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '保存失败')
  } finally {
    nicknameSaving.value = false
  }
}

async function changePassword() {
  pwdMsg.value = ''
  if (!pwdForm.value.oldPassword || !pwdForm.value.newPassword || !pwdForm.value.confirmPassword) {
    pwdMsgOk.value = false; pwdMsg.value = '请填写所有密码字段'; return
  }
  if (pwdForm.value.newPassword !== pwdForm.value.confirmPassword) {
    pwdMsgOk.value = false; pwdMsg.value = '两次输入的新密码不一致'; return
  }
  if (pwdForm.value.newPassword === pwdForm.value.oldPassword) {
    pwdMsgOk.value = false; pwdMsg.value = '新密码不能与旧密码相同'; return
  }
  pwdSaving.value = true
  try {
    await request.post('/auth/me/password', {
      oldPassword: pwdForm.value.oldPassword,
      newPassword: pwdForm.value.newPassword
    })
    ElMessage.success('密码已修改，请重新登录')
    // 强制退出：清 token/user，跳登录页
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    router.push('/login')
  } catch (e) {
    pwdMsgOk.value = false
    pwdMsg.value = e.response?.data?.message || '修改失败'
  } finally {
    pwdSaving.value = false
  }
}
</script>

<style scoped>
.profile-page {
  max-width: 560px;
  padding: 16px 0;
}
.section {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 20px 28px;
  margin-bottom: 20px;
}
.section h3 {
  margin: 0 0 16px;
  font-size: 15px;
  color: #0f3d91;
  border-bottom: 1px solid #e5e7eb;
  padding-bottom: 8px;
}
.form {
  max-width: 420px;
}
.pwd-msg {
  margin-left: 12px;
  font-size: 13px;
}
.pwd-msg.ok { color: #22c55e; }
.pwd-msg.err { color: #ef4444; }
</style>

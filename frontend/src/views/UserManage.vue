<template>
  <div>
    <div style="margin-bottom: 12px">
      <el-button type="primary" @click="load">刷新</el-button>
      <span class="tip">仅管理员可见。修改角色后对方需重新登录才生效。</span>
    </div>
    <el-table :data="users" border stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="username" label="用户名" width="180">
        <template #default="{ row }">
          {{ row.username }}
          <el-tag v-if="row.id === myId" size="small" type="info">我</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="nickname" label="昵称" />
      <el-table-column label="角色" width="160">
        <template #default="{ row }">
          <el-select
            :model-value="row.role"
            :disabled="row.id === myId"
            size="small"
            @change="(role) => changeRole(row, role)"
          >
            <el-option label="管理员" value="admin" />
            <el-option label="普通用户" value="user" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="180">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button
            size="small"
            type="danger"
            :disabled="row.id === myId"
            @click="remove(row)"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchUsers, updateUserRole, deleteUser } from '../api/user'

const users = ref([])
const myId = JSON.parse(localStorage.getItem('user') || 'null')?.userId

async function load() {
  const { data } = await fetchUsers()
  users.value = data
}

async function changeRole(row, role) {
  if (role === row.role) return
  try {
    await updateUserRole(row.id, role)
    row.role = role
    ElMessage.success('已修改角色，对方需重新登录后生效')
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '修改失败')
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确定删除用户「${row.username}」？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteUser(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '删除失败')
  }
}

function formatTime(t) {
  return t ? String(t).replace('T', ' ').slice(0, 19) : '-'
}

onMounted(load)
</script>

<style scoped>
.tip {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}
</style>

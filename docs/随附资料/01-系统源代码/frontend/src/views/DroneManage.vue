<template>
  <div>
    <div style="margin-bottom: 12px">
      <el-button plain @click="router.push('/tasks')">返回列表</el-button>
      <el-button type="primary" @click="load">刷新</el-button>
      <el-button type="success" @click="openCreate">新增无人机</el-button>
      <span class="tip">仅管理员可维护档案。执行中的无人机处于「作业」状态，任务结束后自动恢复「可用」。</span>
    </div>

    <el-table :data="drones" border stripe>
      <el-table-column prop="droneNo" label="机号" width="160" />
      <el-table-column prop="model" label="型号" />
      <el-table-column label="电量(mAh)" width="110">
        <template #default="{ row }">{{ fmt(row.batteryCapacity, 0) }}</template>
      </el-table-column>
      <el-table-column label="最大航程(km)" width="120">
        <template #default="{ row }">{{ fmt(row.maxFlightRange, 2) }}</template>
      </el-table-column>
      <el-table-column label="巡航速度(km/h)" width="130">
        <template #default="{ row }">{{ fmt(row.maxSpeed, 2) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="当前任务" width="110">
        <template #default="{ row }">
          <span v-if="row.bindTaskId">#{{ row.bindTaskId }}</span>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="170">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button
            size="small"
            type="danger"
            :disabled="!!row.bindTaskId"
            @click="remove(row)"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑无人机' : '新增无人机'"
      width="440px"
    >
      <el-form :model="form" label-width="110px">
        <el-form-item label="机号" required>
          <el-input v-model="form.droneNo" maxlength="32" placeholder="如：DJI-M300-01" />
        </el-form-item>
        <el-form-item label="型号">
          <el-input v-model="form.model" maxlength="64" placeholder="如：DJI Matrice 300 RTK" />
        </el-form-item>
        <el-form-item label="电量(mAh)">
          <el-input-number v-model="form.batteryCapacity" :min="0" :max="100000" :precision="0" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="最大航程(km)">
          <el-input-number v-model="form.maxFlightRange" :min="0" :max="1000" :precision="2" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="巡航速度(km/h)">
          <el-input-number v-model="form.maxSpeed" :min="0" :max="200" :precision="2" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%" :disabled="!!form.bindTaskId">
            <el-option v-for="s in STATUS" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.bindTaskId" label="">
          <span class="tip">该无人机正在执行任务 #{{ form.bindTaskId }}，状态固定为「作业」</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchDrones, createDrone, updateDrone, deleteDrone } from '../api/drone'

const router = useRouter()
const STATUS = ['可用', '作业', '维护', '离线']

const drones = ref([])
const dialogVisible = ref(false)
const saving = ref(false)
const form = ref({})

function fmt(v, digits) {
  if (v === null || v === undefined || v === '') return '—'
  return Number(v).toFixed(digits)
}

function statusType(s) {
  return { 可用: 'success', 作业: 'warning', 维护: 'info', 离线: 'danger' }[s] || 'info'
}

async function load() {
  try {
    const { data } = await fetchDrones()
    drones.value = data
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '加载无人机列表失败')
  }
}

function openCreate() {
  form.value = {
    id: null, droneNo: '', model: '', batteryCapacity: 5000,
    maxFlightRange: 12, maxSpeed: 12, status: '可用', bindTaskId: null
  }
  dialogVisible.value = true
}

function openEdit(row) {
  form.value = { ...row }
  dialogVisible.value = true
}

async function save() {
  if (!form.value.droneNo || !form.value.droneNo.trim()) {
    ElMessage.warning('请填写机号')
    return
  }
  const payload = {
    droneNo: form.value.droneNo.trim(),
    model: form.value.model,
    batteryCapacity: form.value.batteryCapacity,
    maxFlightRange: form.value.maxFlightRange,
    maxSpeed: form.value.maxSpeed,
    status: form.value.status
  }
  saving.value = true
  try {
    if (form.value.id) {
      await updateDrone(form.value.id, payload)
      ElMessage.success('已更新无人机档案')
    } else {
      await createDrone(payload)
      ElMessage.success('已新增无人机')
    }
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确定删除无人机「${row.droneNo}」？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteDrone(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '删除失败')
  }
}

onMounted(load)
</script>

<style scoped>
.tip {
  font-size: 12px;
  color: #909399;
  margin-left: 8px;
}
.muted {
  color: #c0c4cc;
}
</style>

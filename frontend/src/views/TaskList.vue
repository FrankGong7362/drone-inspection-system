<template>
  <div>
    <div style="margin-bottom: 12px">
      <el-button type="primary" @click="load">刷新</el-button>
      <el-button type="success" @click="openCreate">新建任务</el-button>
    </div>
    <el-table :data="tasks" border stripe>
      <el-table-column prop="taskNo" label="任务编号" width="160" />
      <el-table-column prop="taskName" label="任务名称" />
      <el-table-column prop="taskType" label="类型" width="100" />
      <el-table-column prop="status" label="状态" width="120" />
      <el-table-column prop="totalDistance" label="总航程(km)" width="120" />
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button size="small" type="primary" @click="goPlan(row)">规划</el-button>
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新建任务 -->
    <el-dialog v-model="dialogVisible" title="新建巡检任务" width="420px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="任务名称" required>
          <el-input v-model="form.taskName" placeholder="如：园区东区巡检" maxlength="64" />
        </el-form-item>
        <el-form-item label="任务类型">
          <el-select v-model="form.taskType" style="width: 100%">
            <el-option label="电力" value="电力" />
            <el-option label="园区" value="园区" />
            <el-option label="交通" value="交通" />
          </el-select>
        </el-form-item>
        <el-form-item label="航点数量">
          <el-input-number v-model="form.waypointCount" :min="2" :max="30" />
          <span class="tip">自动生成模拟巡检点</span>
        </el-form-item>
        <el-form-item label="起飞点经度">
          <el-input-number v-model="form.centerLng" :min="-180" :max="180" :precision="6" :controls="false" style="width: 180px" />
        </el-form-item>
        <el-form-item label="起飞点纬度">
          <el-input-number v-model="form.centerLat" :min="-90" :max="90" :precision="6" :controls="false" style="width: 180px" />
          <span class="tip">模拟巡检点在该点 ±2km 内生成，不可位于禁飞区</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchTasks, deleteTask, createTask, generateWaypoints } from '../api/plan'

const router = useRouter()
const tasks = ref([])

const dialogVisible = ref(false)
const creating = ref(false)
const form = ref({
  taskName: '',
  taskType: '园区',
  waypointCount: 8,
  centerLng: 121.472225,
  centerLat: 31.339709
})

async function load() {
  const { data } = await fetchTasks()
  tasks.value = data
}

function openCreate() {
  form.value = {
    taskName: '',
    taskType: '园区',
    waypointCount: 8,
    centerLng: 121.472225,
    centerLat: 31.339709
  }
  dialogVisible.value = true
}

async function submitCreate() {
  if (!form.value.taskName.trim()) {
    ElMessage.warning('请填写任务名称')
    return
  }
  if (form.value.centerLng == null || form.value.centerLat == null) {
    ElMessage.warning('请填写起飞点经纬度')
    return
  }
  creating.value = true
  try {
    const { data: task } = await createTask({
      taskName: form.value.taskName.trim(),
      taskType: form.value.taskType
    })
    await generateWaypoints(task.id, form.value.waypointCount,
      form.value.centerLng, form.value.centerLat)
    ElMessage.success('任务创建成功，已生成模拟航点')
    dialogVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(`创建失败：${e.response?.data?.message || e.message}`)
  } finally {
    creating.value = false
  }
}

async function remove(row) {
  await deleteTask(row.id)
  ElMessage.success('已删除')
  load()
}

function goPlan(row) {
  router.push({ path: '/map', query: { taskId: row.id } })
}

onMounted(load)
</script>

<style scoped>
.tip {
  font-size: 12px;
  color: #909399;
  margin-left: 8px;
}
</style>

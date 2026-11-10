<template>
  <div class="audit-page">
    <div class="toolbar">
      <el-button plain @click="router.push('/tasks')">返回列表</el-button>
      <el-button type="primary" :loading="loading" @click="load(1)">刷新</el-button>
      <el-select
        v-model="filters.taskId"
        clearable
        filterable
        placeholder="全部任务"
        style="width: 230px"
        @change="load(1)"
      >
        <el-option
          v-for="t in tasks"
          :key="t.id"
          :label="`${t.taskNo} ${t.taskName}`"
          :value="t.id"
        />
      </el-select>
      <el-select v-model="filters.action" clearable placeholder="全部动作" style="width: 130px" @change="load(1)">
        <el-option v-for="a in actions" :key="a" :label="a" :value="a" />
      </el-select>
      <el-input
        v-model="filters.keyword"
        clearable
        placeholder="按说明关键字搜索"
        style="width: 220px"
        @keyup.enter="load(1)"
        @clear="load(1)"
      />
      <el-button @click="load(1)">查询</el-button>
      <span class="tip">仅管理员可见；数据来源 mission_log（下发 / 到点 / 返航 / 降落 / 异常）</span>
    </div>

    <el-table v-loading="loading" :data="records" border stripe size="small">
      <el-table-column label="时间" width="160">
        <template #default="{ row }">{{ formatTime(row.logTime) }}</template>
      </el-table-column>
      <el-table-column label="任务" min-width="210">
        <template #default="{ row }">
          <span class="task-no">{{ row.taskNo || `#${row.taskId}` }}</span>
          <span class="task-name">{{ row.taskName || '' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="动作" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="actionType(row.action)">{{ row.action }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="无人机" width="130">
        <template #default="{ row }">
          {{ row.droneNo || (row.droneId ? `#${row.droneId}` : '-') }}
        </template>
      </el-table-column>
      <el-table-column label="航点" width="130">
        <template #default="{ row }">
          {{ row.waypointName || (row.waypointId ? `#${row.waypointId}` : '-') }}
        </template>
      </el-table-column>
      <el-table-column prop="detail" label="说明" min-width="240" show-overflow-tooltip />
    </el-table>

    <el-pagination
      class="pager"
      background
      layout="total, sizes, prev, pager, next"
      :total="total"
      :current-page="page"
      :page-size="pageSize"
      :page-sizes="[10, 20, 50, 100]"
      @current-change="load"
      @size-change="onSizeChange"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchAuditLogs } from '../api/audit'
import { fetchTasks } from '../api/plan'

const router = useRouter()

const actions = ['起飞', '到点', '返航', '降落', '异常']
const filters = reactive({ taskId: null, action: '', keyword: '' })
const tasks = ref([])
const records = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const loading = ref(false)

async function load(targetPage = page.value) {
  loading.value = true
  try {
    const params = { page: targetPage, size: pageSize.value }
    if (filters.taskId != null) params.taskId = filters.taskId
    if (filters.action) params.action = filters.action
    if (filters.keyword && filters.keyword.trim()) params.keyword = filters.keyword.trim()
    const { data } = await fetchAuditLogs(params)
    records.value = data.records || []
    total.value = Number(data.total || 0)
    page.value = Number(data.page || targetPage)
    pageSize.value = Number(data.size || pageSize.value)
  } catch (e) {
    ElMessage.error('审计日志加载失败：' + (e.response?.data?.message || e.message))
  } finally {
    loading.value = false
  }
}

function onSizeChange(size) {
  pageSize.value = size
  load(1)
}

async function loadTasks() {
  try {
    const { data } = await fetchTasks()
    tasks.value = data || []
  } catch {
    tasks.value = []
  }
}

function actionType(action) {
  return {
    起飞: 'primary',
    到点: 'success',
    返航: 'warning',
    降落: 'info',
    异常: 'danger'
  }[action] || 'info'
}

function formatTime(t) {
  return t ? String(t).replace('T', ' ').slice(0, 19) : '-'
}

onMounted(() => {
  loadTasks()
  load(1)
})
</script>

<style scoped>
.audit-page {
  height: calc(100vh - 62px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 16px;
  box-sizing: border-box;
  background: #fff;
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  flex-shrink: 0;
}
.toolbar .el-button + .el-button {
  margin-left: 0;
}
.tip {
  font-size: 12px;
  color: #909399;
}
.task-no {
  font-weight: 700;
  color: #0f3d91;
  margin-right: 8px;
}
.task-name {
  color: #374151;
}
.pager {
  margin-top: 12px;
  flex-shrink: 0;
  justify-content: flex-end;
}
</style>

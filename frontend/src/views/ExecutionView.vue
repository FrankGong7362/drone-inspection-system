<template>
  <div class="exec-page">
    <div class="top-bar">
      <el-button plain @click="router.push('/tasks')">返回列表</el-button>
      <el-button type="primary" @click="load">刷新</el-button>
      <span class="task-name">{{ task.taskName || '任务' }}</span>
      <el-tag :type="statusType(task.status)" size="small">{{ task.status || '—' }}</el-tag>
      <span v-if="plan" class="meta">航程 {{ Number(plan.totalDistanceKm || 0).toFixed(2) }} km · {{ plan.algorithm }}</span>

      <div class="controls">
        <el-select
          v-if="task.status !== '执行中'"
          v-model="demoSeconds"
          size="small"
          style="width: 118px"
          title="演示时长：把真实飞行时间压缩到该目标时长（航线很短时按真实速度播放）"
        >
          <el-option v-for="s in DEMO_DURATIONS" :key="s" :label="`演示 ${s} 秒`" :value="s" />
        </el-select>
        <el-select
          v-if="task.status !== '执行中'"
          v-model="selectedDroneId"
          placeholder="选择无人机"
          size="small"
          style="width: 190px"
        >
          <el-option
            v-for="d in availableDrones"
            :key="d.id"
            :label="`${d.droneNo}（${d.model || d.status}）`"
            :value="d.id"
          />
        </el-select>
        <el-button
          v-if="task.status !== '执行中'"
          type="success"
          size="small"
          :disabled="!selectedDroneId || !plan"
          :loading="dispatching"
          @click="onDispatch"
        >下发执行</el-button>
        <el-button
          v-else
          type="warning"
          size="small"
          :loading="operating"
          @click="onFinish"
        >完成执行</el-button>
        <el-button
          v-if="task.status === '待执行' || task.status === '执行中'"
          type="danger"
          size="small"
          plain
          :loading="operating"
          @click="onCancel"
        >取消任务</el-button>
      </div>
    </div>

    <div v-if="!plan" class="no-plan">
      <el-alert
        type="warning"
        :closable="false"
        title="该任务还没有规划结果"
        description="请先回到任务列表点击「规划」，完成路径规划后再下发执行。"
      />
    </div>

    <div class="main">
      <div ref="mapEl" class="map"></div>
      <div class="side">
        <div class="progress-box">
          <div class="progress-label">
            <span>飞行进度</span>
            <span>{{ Math.round(progress) }}%</span>
          </div>
          <el-progress :percentage="progress" :stroke-width="10" :show-text="false" />
          <div class="speed-line">
            <span v-if="currentDrone">无人机 {{ currentDrone.droneNo }} · 巡航 {{ fmt(currentDrone.maxSpeed) }} km/h</span>
            <span v-else>未绑定无人机</span>
            <span>{{ arrivedCount }}/{{ waypoints.length }} 航点到达</span>
          </div>
          <div class="speed-line">
            <span>全程 {{ fmt(totalDist, 2) }} km · 已飞 {{ fmt(flyingKm, 1) }} km</span>
            <span v-if="animationSeconds">预计 {{ animationSeconds }} 秒</span>
          </div>
          <div v-if="realSeconds" class="speed-note">
            真实飞行 {{ humanDuration(realSeconds) }}，演示压缩 {{ compressionFactor }}×
          </div>
        </div>

        <div class="log-box">
          <div class="log-title">
            <span>执行日志（{{ logs.length }} 条<span v-if="logRounds.length"> · {{ logRounds.length }} 轮</span>）</span>
            <span v-if="archived" class="log-hint">已归档，可回看；重新下发会追加新一轮</span>
          </div>
          <div class="log-list">
            <div v-for="r in logRounds" :key="r.index" class="log-round">
              <div class="round-title">
                <span class="round-name">第 {{ r.index }} 轮</span>
                <el-tag size="small" :type="roundState(r).type">{{ roundState(r).text }}</el-tag>
                <span v-if="roundDroneNo(r)" class="round-meta">无人机 {{ roundDroneNo(r) }}</span>
                <span class="round-meta">{{ formatTime(r.start.logTime) }}</span>
                <span v-if="roundDuration(r)" class="round-meta">历时 {{ roundDuration(r) }}</span>
              </div>
              <div v-for="log in r.logs.slice().reverse()" :key="log.id" class="log-item">
                <el-tag size="small" :type="actionType(log.action)">{{ log.action }}</el-tag>
                <span class="log-time">{{ formatTime(log.logTime) }}</span>
                <span class="log-detail">{{ log.detail || wpName(log.waypointId) }}</span>
              </div>
            </div>
            <div v-if="!logs.length" class="log-empty">暂无日志，下发任务后开始记录</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchTask, fetchWaypoints } from '../api/plan'
import { fetchLatestPlan, fetchMissionLogs, dispatchTask, appendMissionLog, finishTask, cancelTask } from '../api/execution'
import { fetchDrones } from '../api/drone'

const route = useRoute()
const router = useRouter()
const taskId = Number(route.query.taskId)

const task = ref({})
const plan = ref(null)
const waypoints = ref([])
const logs = ref([])
const drones = ref([])
const selectedDroneId = ref(null)
const progress = ref(0)
const dispatching = ref(false)
const operating = ref(false)
// 演示时长：把真实飞行时间压缩到目标秒数；航线很短时按真实速度播放（不小于 3 秒）
const DEMO_DURATIONS = [30, 60, 90, 120]
const demoSeconds = ref(60)

const mapEl = ref(null)
let map = null
let routeLayer = null
let wpLayer = null
let droneMarker = null

let flightPts = []      // [[lat, lng], ...]
let cumDist = []        // 累计距离（km）
const totalDist = ref(0)
const arrivals = ref([]) // [{id, name, t, done}]，需响应式才能让“x/13 航点到达”实时更新
let rafId = null
let animStart = 0
let lastLoggedIndex = 0

const statusType = s => ({ 待执行: 'info', 执行中: 'warning', 已完成: 'success', 已取消: 'danger' }[s] || 'info')
const actionType = a => ({ 起飞: 'success', 到点: 'primary', 返航: 'warning', 降落: 'success', 异常: 'danger' }[a] || 'info')

const availableDrones = computed(() => drones.value.filter(d => d.status === '可用'))
const currentDrone = computed(() => drones.value.find(d => d.bindTaskId === taskId) || null)
const selectedDrone = computed(() => drones.value.find(d => d.id === selectedDroneId.value) || null)
// 巡航速度：执行中用已绑定无人机，待执行用下拉选中的无人机，缺省 15 km/h
const cruiseSpeed = computed(() => Number((currentDrone.value || selectedDrone.value)?.maxSpeed) || 15)
// 按巡航速度计算真实飞行耗时（秒）
const realSeconds = computed(() => totalDist.value ? totalDist.value / cruiseSpeed.value * 3600 : 0)
// 演示时长（秒）：不超过选择的演示目标，真实耗时更短时按真实速度播放
const animationSeconds = computed(() => {
  if (!realSeconds.value) return 0
  return Math.max(3, Math.min(demoSeconds.value, Math.round(realSeconds.value)))
})
// 演示压缩倍数：真实耗时 / 演示时长
const compressionFactor = computed(() => {
  if (!realSeconds.value || !animationSeconds.value) return 0
  return Math.max(1, Math.round(realSeconds.value / animationSeconds.value))
})
// 已飞距离（km）
const flyingKm = computed(() => totalDist.value * progress.value / 100)
const arrivedCount = computed(() => arrivals.value.filter(a => a.done).length)
const archived = computed(() => task.value.status === '已完成' || task.value.status === '已取消')
// 执行日志按「起飞」切分轮次：每次重新下发追加一轮，历史不覆盖
const logRounds = computed(() => {
  const sorted = [...logs.value].sort((a, b) => (a.id || 0) - (b.id || 0))
  const rounds = []
  sorted.forEach(log => {
    if (!rounds.length || log.action === '起飞') {
      rounds.push({ index: rounds.length + 1, start: log, logs: [] })
    }
    rounds[rounds.length - 1].logs.push(log)
  })
  return rounds.reverse() // 最新一轮排在最前
})

/** 一轮执行的结束状态（由日志动作推断） */
function roundState(r) {
  const actions = r.logs.map(l => l.action)
  if (actions.includes('降落')) return { text: '已完成', type: 'success' }
  if (actions.includes('返航')) return { text: '已返航', type: 'warning' }
  if (actions.includes('异常')) return { text: '异常结束', type: 'danger' }
  return { text: '执行中', type: 'warning' }
}

function roundDroneNo(r) {
  const log = r.logs.find(l => l.droneId)
  if (!log) return ''
  const d = drones.value.find(x => x.id === log.droneId)
  return d ? d.droneNo : `#${log.droneId}`
}

function roundDuration(r) {
  const first = r.logs[0]?.logTime
  const last = r.logs[r.logs.length - 1]?.logTime
  if (!first || !last) return ''
  const sec = (new Date(last) - new Date(first)) / 1000
  return sec >= 1 ? humanDuration(sec) : ''
}

function humanDuration(sec) {
  if (!sec) return '—'
  if (sec < 60) return `${Math.round(sec)} 秒`
  if (sec < 3600) return `${Math.floor(sec / 60)} 分 ${Math.round(sec % 60)} 秒`
  const h = Math.floor(sec / 3600)
  const m = Math.round(sec % 3600 / 60)
  return `${h} 小时${m ? ` ${m} 分` : ''}`
}

function fmt(v) {
  if (v === null || v === undefined || v === '') return '—'
  return Number(v).toFixed(2)
}

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 19)
}

function wpName(id) {
  const w = waypoints.value.find(x => x.id === id)
  return w ? w.name : ''
}

function haversine(a, b) {
  const R = 6371
  const dLat = (b[0] - a[0]) * Math.PI / 180
  const dLng = (b[1] - a[1]) * Math.PI / 180
  const s = Math.sin(dLat / 2) ** 2 +
    Math.cos(a[0] * Math.PI / 180) * Math.cos(b[0] * Math.PI / 180) * Math.sin(dLng / 2) ** 2
  return 2 * R * Math.asin(Math.sqrt(s))
}

function initMap() {
  map = L.map(mapEl.value).setView([31.30, 121.49], 12)
  L.tileLayer('https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}', {
    subdomains: ['1', '2', '3', '4'],
    maxZoom: 18,
    attribution: '© 高德地图'
  }).addTo(map)
  routeLayer = L.layerGroup().addTo(map)
  wpLayer = L.layerGroup().addTo(map)
}

function buildFlightPath() {
  const pts = []
  if (plan.value && Array.isArray(plan.value.detailPaths) && plan.value.detailPaths.length > 1) {
    plan.value.detailPaths.forEach(p => pts.push([Number(p[0]), Number(p[1])]))
  } else if (waypoints.value.length) {
    // 无避障折线：按规划访问顺序用航点连线
    const byId = new Map(waypoints.value.map(w => [w.id, w]))
    const order = (plan.value?.route || []).map(id => byId.get(id)).filter(Boolean)
    (order.length ? order : waypoints.value).forEach(w =>
      pts.push([Number(w.latitude), Number(w.longitude)]))
  }
  flightPts = pts
  cumDist = []
  totalDist.value = 0
  for (let i = 0; i < pts.length; i++) {
    if (i > 0) totalDist.value += haversine(pts[i - 1], pts[i])
    cumDist.push(totalDist.value)
  }
  computeArrivals()
}

function computeArrivals() {
  arrivals.value = waypoints.value.map(w => {
    let best = 0, bestD = Infinity
    flightPts.forEach((p, i) => {
      const d = (p[0] - Number(w.latitude)) ** 2 + (p[1] - Number(w.longitude)) ** 2
      if (d < bestD) { bestD = d; best = i }
    })
    const t = totalDist.value > 0 ? cumDist[best] / totalDist.value : 0
    return { id: w.id, name: w.name, t, done: false }
  }).sort((a, b) => a.t - b.t)
  lastLoggedIndex = 0
}

function renderRoute() {
  routeLayer.clearLayers()
  wpLayer.clearLayers()
  if (!flightPts.length) return
  L.polyline(flightPts, { color: '#2563eb', weight: 3, opacity: 0.85 }).addTo(routeLayer)
  waypoints.value.forEach(w => {
    const dep = w.id === task.value.startPointId
    L.circleMarker([Number(w.latitude), Number(w.longitude)], {
      radius: dep ? 7 : 5,
      color: dep ? '#f59e0b' : '#2563eb',
      fillColor: dep ? '#f59e0b' : '#ffffff',
      fillOpacity: 0.9
    }).bindTooltip(`${w.name || '巡检点'}${dep ? '（起点）' : ''}`, { direction: 'top' })
      .addTo(wpLayer)
  })
  map.fitBounds(L.latLngBounds(flightPts), { padding: [30, 30] })
}

function ensureDroneMarker() {
  if (!droneMarker && flightPts.length) {
    droneMarker = L.circleMarker(flightPts[0], {
      radius: 9, color: '#ef4444', weight: 2, fillColor: '#ef4444', fillOpacity: 0.85
    }).bindTooltip('无人机', { direction: 'top' }).addTo(map)
    droneMarker.setLatLng(flightPts[0])
  }
}

function pointAt(p) {
  if (!flightPts.length) return null
  if (p <= 0) return flightPts[0]
  if (p >= 1) return flightPts[flightPts.length - 1]
  const target = totalDist.value * p
  let i = 1
  while (i < cumDist.length && cumDist[i] < target) i++
  const a = flightPts[i - 1]
  const b = flightPts[i]
  const seg = cumDist[i] - cumDist[i - 1] || 1
  const r = (target - cumDist[i - 1]) / seg
  return [a[0] + (b[0] - a[0]) * r, a[1] + (b[1] - a[1]) * r]
}

function startAnimation() {
  if (!flightPts.length) {
    ElMessage.warning('暂无可回放的航线')
    return
  }
  stopAnimation()
  ensureDroneMarker()
  progress.value = 0
  arrivals.value.forEach(a => { a.done = false })
  lastLoggedIndex = 0
  droneMarker.setLatLng(flightPts[0])

  // 演示时长：把真实飞行时间（航程 ÷ 巡航速度）压缩到所选时长
  const duration = animationSeconds.value * 1000
  animStart = 0
  const step = now => {
    if (!animStart) animStart = now
    const p = Math.min(1, (now - animStart) / duration)
    progress.value = p * 100
    const pos = pointAt(p)
    if (pos && droneMarker) droneMarker.setLatLng(pos)
    logArrivals(p)
    if (p >= 1) {
      rafId = null
      onArrivedEnd()
    } else {
      rafId = requestAnimationFrame(step)
    }
  }
  rafId = requestAnimationFrame(step)
}

function stopAnimation() {
  if (rafId) {
    cancelAnimationFrame(rafId)
    rafId = null
  }
}

function logArrivals(p) {
  while (lastLoggedIndex < arrivals.value.length && p >= arrivals.value[lastLoggedIndex].t) {
    const a = arrivals.value[lastLoggedIndex]
    lastLoggedIndex++
    if (a.done) continue
    a.done = true
    const detail = `到达航点「${a.name || ('#' + a.id)}」`
    appendMissionLog(taskId, { action: '到点', waypointId: a.id, detail })
      .then(({ data }) => logs.value.push(data))
      .catch(() => {})
  }
}

async function onArrivedEnd() {
  progress.value = 100
  try {
    operating.value = true
    const { data } = await finishTask(taskId)
    task.value = data
    await refreshAfterChange()
    ElMessage.success('航线飞行完成，任务已归档')
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '结束任务失败')
  } finally {
    operating.value = false
  }
}

async function onDispatch() {
  if (!selectedDroneId.value) {
    ElMessage.warning('请先选择无人机')
    return
  }
  dispatching.value = true
  try {
    const { data } = await dispatchTask(taskId, selectedDroneId.value)
    task.value = data
    await refreshAfterChange()
    ElMessage.success('任务已下发给无人机')
    startAnimation()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '下发失败')
  } finally {
    dispatching.value = false
  }
}

async function onFinish() {
  stopAnimation()
  try {
    await ElMessageBox.confirm('提前结束本次执行？任务将归档为「已完成」。', '完成执行', { type: 'warning' })
  } catch {
    return
  }
  operating.value = true
  try {
    const { data } = await finishTask(taskId)
    task.value = data
    await refreshAfterChange()
    ElMessage.success('任务已完成')
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '操作失败')
  } finally {
    operating.value = false
  }
}

async function onCancel() {
  try {
    await ElMessageBox.confirm('确定取消该任务？执行中的无人机会立即返航。', '取消任务', { type: 'warning' })
  } catch {
    return
  }
  stopAnimation()
  operating.value = true
  try {
    const { data } = await cancelTask(taskId)
    task.value = data
    await refreshAfterChange()
    ElMessage.success('任务已取消')
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '操作失败')
  } finally {
    operating.value = false
  }
}

async function refreshAfterChange() {
  const [d, l] = await Promise.all([fetchDrones(), fetchMissionLogs(taskId)])
  drones.value = d.data
  logs.value = l.data
}

async function load() {
  try {
    const { data: t } = await fetchTask(taskId)
    task.value = t
    const [wp, lp, dr, lg] = await Promise.all([
      fetchWaypoints(taskId),
      fetchLatestPlan(taskId),
      fetchDrones(),
      fetchMissionLogs(taskId)
    ])
    waypoints.value = wp.data
    plan.value = lp.data && lp.data.route ? lp.data : null
    drones.value = dr.data
    logs.value = lg.data
    if (currentDrone.value) selectedDroneId.value = currentDrone.value.id

    buildFlightPath()
    renderRoute()
    // 刷新时若任务已执行过，按历史「到点」日志恢复到达计数
    const arrivedIds = new Set(logs.value.filter(l => l.action === '到点' && l.waypointId).map(l => l.waypointId))
    arrivals.value.forEach(a => { if (arrivedIds.has(a.id)) a.done = true })
    if (task.value.status === '已完成') {
      // 已完成任务回看：进度置满，无人机图标停在航线终点
      progress.value = 100
      ensureDroneMarker()
      if (droneMarker && flightPts.length) droneMarker.setLatLng(flightPts[flightPts.length - 1])
    } else if (task.value.status === '执行中') {
      ensureDroneMarker()
      ElMessage.info('该任务正在执行中，点击「完成执行」归档，或重新下发以回放航线')
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '加载任务失败')
  }
}

onMounted(async () => {
  await nextTick()
  initMap()
  await load()
})

onBeforeUnmount(() => {
  stopAnimation()
  if (map) {
    map.remove()
    map = null
  }
})
</script>

<style scoped>
.exec-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 110px);
}
.top-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.task-name {
  font-weight: 600;
  color: #0f3d91;
}
.meta {
  font-size: 13px;
  color: #6b7280;
}
.controls {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
}
.no-plan {
  margin-bottom: 12px;
}
.main {
  flex: 1;
  display: flex;
  gap: 12px;
  min-height: 0;
}
.map {
  flex: 1;
  min-width: 0;
  border-radius: 8px;
  overflow: hidden;
}
.side {
  width: 360px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
}
.progress-box {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 12px 16px;
}
.progress-label {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: #374151;
  margin-bottom: 6px;
}
.speed-line {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #6b7280;
  margin-top: 8px;
}
.speed-note {
  font-size: 12px;
  color: #9ca3af;
  margin-top: 4px;
}
.log-box {
  flex: 1;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.log-title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: #0f3d91;
  padding-bottom: 8px;
  border-bottom: 1px solid #e5e7eb;
  margin-bottom: 8px;
}
.log-hint {
  font-size: 11px;
  font-weight: 400;
  color: #9ca3af;
}
.log-round {
  margin-bottom: 10px;
}
.round-title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  padding: 6px 0;
  border-bottom: 1px solid #eef2f7;
}
.round-name {
  font-size: 12px;
  font-weight: 600;
  color: #0f3d91;
}
.round-meta {
  font-size: 11px;
  color: #9ca3af;
}
.log-list {
  flex: 1;
  overflow-y: auto;
}
.log-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  padding: 6px 0;
  border-bottom: 1px dashed #f0f0f0;
}
.log-time {
  color: #9ca3af;
  flex-shrink: 0;
}
.log-detail {
  color: #374151;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.log-empty {
  color: #9ca3af;
  font-size: 13px;
  text-align: center;
  padding: 30px 0;
}
</style>

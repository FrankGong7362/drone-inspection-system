<template>
  <div class="plan-page">
    <!-- 顶部任务信息条 -->
    <div class="task-bar">
      <div class="task-meta">
        <el-button class="back-btn" plain @click="router.push('/tasks')">返回列表</el-button>
        <span class="task-no">{{ task?.taskNo || '--' }}</span>
        <span class="task-name">{{ task?.taskName || '加载中' }}</span>
        <el-tag size="small" :type="statusType">{{ statusText }}</el-tag>
        <el-tag size="small" :type="task?.visible ? 'success' : 'info'" effect="plain">
          {{ task?.visible ? '公开' : '私有' }}
        </el-tag>
        <el-tag size="small" type="info" effect="plain">{{ waypoints.length }} 个航点</el-tag>
      </div>
      <div v-if="info" class="result-brief">
        <el-tag v-if="info.feasible" type="success" effect="dark" size="large">
          {{ info.algorithm }} 规划完成 · 总航程 {{ info.totalDistanceKm }} km · 结果已入库 #{{ info.resultId }}
        </el-tag>
        <el-tag v-else type="danger" effect="dark" size="large">
          {{ info.algorithm }} 规划不可行 · 总航程 {{ info.totalDistanceKm }} km 超过续航上限 {{ maxRange }} km
        </el-tag>
      </div>
    </div>

    <div class="main">
      <!-- 左侧操作面板 -->
      <div class="side-panel">
        <div class="panel-card">
          <div class="panel-title">路径规划</div>
          <el-select v-model="algorithm" style="width: 100%">
            <el-option label="遗传算法 ga" value="ga" />
            <el-option label="蚁群算法 aco" value="aco" />
            <el-option label="贪心 greedy" value="greedy" />
            <el-option label="最近邻 nearest" value="nearest" />
            <el-option label="改进A* astar" value="astar" />
          </el-select>
          <div class="field-row">
            <span class="field-label">续航上限</span>
            <el-input-number v-model="maxRange" :min="1" :max="200" size="small" style="width: 120px" />
            <span class="field-unit">km</span>
          </div>
          <div class="field-row">
            <span class="field-label">安全距离</span>
            <el-input-number v-model="bufferKm" :min="0" :max="5" :step="0.1" size="small" style="width: 120px" />
            <span class="field-unit">km</span>
          </div>
          <el-alert
            v-if="ringInWaypoints.length"
            type="error"
            :closable="false"
            show-icon
            :title="`${ringInWaypoints.length} 个巡检点位于安全环内，暂不能开始规划`"
            :description="ringInTip"
            style="margin-top: 8px"
          />
          <div class="btn-col">
            <el-button
              type="primary"
              size="large"
              style="width: 100%"
              :loading="loading"
              :disabled="!waypoints.length || ringInWaypoints.length"
              @click="run"
            >
              开始规划
            </el-button>
          </div>
          <div class="tip" style="margin-top: 6px">支持遗传、蚁群、贪心、最近邻、改进 A*（含禁飞区避障）共 5 种算法</div>
        </div>

        <div class="panel-card">
          <div class="panel-title">地图操作</div>
          <div class="field-row" style="gap: 6px">
            <el-input
              v-model="searchKeyword"
              v-if="amapReady"
              placeholder="搜索地名，如：同济大学"
              clearable
              @keyup.enter="searchPlaces"
            />
            <el-button v-if="amapReady" :loading="searching" @click="searchPlaces">搜索</el-button>
          </div>
          <div class="btn-col">
            <el-button style="width: 100%" :loading="locating" @click="locateMe">定位到我</el-button>
            <el-button v-if="canEdit" style="width: 100%" :type="addMode ? 'warning' : 'success'" @click="toggleAddMode">
              {{ addMode ? '取消选点' : '新增巡检点' }}
            </el-button>
            <el-button v-if="canEdit" style="width: 100%" @click="openCoordDialog">按经纬度添加</el-button>
            <el-button v-if="canEdit" style="width: 100%" :loading="generating" @click="openGenDialog">生成模拟航点</el-button>
          </div>
          <div class="tip" style="margin-top: 8px">地图航点上右键：设为起飞点 / 删除该点</div>
          <el-alert
            v-if="addMode"
            type="warning"
            :closable="false"
            title="选点模式已开启"
            description="在地图上点击目标位置，即可把该点保存为巡检点"
            style="margin-top: 8px"
          />
        </div>

        <div class="panel-card">
          <div class="panel-title">航点列表（{{ waypoints.length }}）</div>
          <el-table
            v-if="waypoints.length"
            :data="waypoints"
            size="small"
            max-height="150"
            style="cursor: pointer;"
            @row-click="locateWaypoint"
          >
            <el-table-column label="#" width="42">
              <template #default="{ row }">{{ row.seq }}</template>
            </el-table-column>
            <el-table-column label="名称" min-width="90">
              <template #default="{ row }">
                {{ row.name || '巡检点' + row.id }}
                <el-tag v-if="Number(row.id) === Number(task?.startPointId)" size="small" type="warning" effect="plain">起飞</el-tag>
                <el-tag v-else-if="isRingIn(row)" size="small" type="danger" effect="plain">环内</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="176">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click.stop="locateWaypoint(row)">定位</el-button>
                <el-button v-if="canEdit" link type="success" size="small" @click.stop="renameWaypoint(row)">改名</el-button>
                <el-button v-if="canEdit" link type="warning" size="small" @click.stop="setStartPoint(row)">起飞</el-button>
                <el-button v-if="canEdit" link type="danger" size="small" @click.stop="removeWaypoint(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-else class="tip">暂无航点：可在地图选点、搜索地名或生成模拟航点</div>
        </div>

        <div class="panel-card">
          <div class="panel-title">当前选中位置</div>
          <template v-if="picked">
            <div class="coord-main">{{ picked.lng.toFixed(6) }}, {{ picked.lat.toFixed(6) }}</div>
            <div class="tip">来自选点 / 定位 / 搜索，可一键添加为巡检点</div>
            <el-button
              v-if="canEdit"
              style="width: 100%; margin-top: 8px"
              type="success"
              plain
              @click="saveWaypoint(picked.lng, picked.lat)"
            >
              添加为巡检点
            </el-button>
          </template>
          <template v-else>
            <div class="tip">搜索地名、「定位到我」，或开启「新增巡检点」后在地图点击</div>
          </template>
        </div>

        <div v-if="info" class="panel-card">
          <div class="panel-title">规划结果</div>
          <el-alert
            v-if="!info.feasible"
            type="error"
            :closable="false"
            show-icon
            title="超出续航上限，规划不可行"
            :description="`总航程 ${info.totalDistanceKm} km ＞ 续航上限 ${maxRange} km，请减少巡检点数量或提高续航上限后重新规划`"
            style="margin-bottom: 10px"
          />
          <div class="big-dist" :class="{ 'infeasible': !info.feasible }">
            {{ info.totalDistanceKm }}<span class="unit">km</span>
          </div>
          <div class="big-dist-label">总航程</div>
          <div class="stat-grid">
            <div class="stat-item">
              <div class="stat-value">{{ info.runTimeMs }}<span class="unit-sm">ms</span></div>
              <div class="stat-label">计算耗时</div>
            </div>
            <div class="stat-item">
              <div class="stat-value">{{ info.convergence ? info.convergence.length + ' 代' : '--' }}</div>
              <div class="stat-label">迭代代数</div>
            </div>
            <div class="stat-item">
              <div class="stat-value">
                <el-tag size="small" :type="info.feasible ? 'success' : 'danger'">
                  {{ info.feasible ? '可行' : '不可行' }}
                </el-tag>
              </div>
              <div class="stat-label">约束校验</div>
            </div>
          </div>
          <div v-if="info.convergence && info.convergence.length > 1" class="conv">
            <div class="tip" style="margin-bottom: 4px">收敛曲线（每代最优总航程 km）</div>
            <svg viewBox="0 0 300 60" preserveAspectRatio="none" class="conv-chart">
              <polyline :points="convPoints" fill="none" stroke="#e6501a" stroke-width="1.5" />
            </svg>
          </div>
        </div>
      </div>

      <!-- 右侧地图 -->
      <div class="map-wrap">
        <div ref="mapEl" class="map-el"></div>
      </div>
    </div>

    <!-- 航点右键操作菜单 -->
    <teleport to="body">
      <div
        v-if="ctxMenu.visible && canEdit"
        class="ctx-menu"
        :style="{ left: ctxMenu.x + 'px', top: ctxMenu.y + 'px' }"
        @click.stop
      >
        <div class="ctx-item" @click="setStartPoint(ctxMenu.waypoint)">设为起飞点</div>
        <div class="ctx-item ctx-danger" @click="removeWaypoint(ctxMenu.waypoint)">删除该巡检点</div>
      </div>
    </teleport>
    <div v-if="ctxMenu.visible" class="ctx-mask" @click="closeCtxMenu" @contextmenu.prevent="closeCtxMenu"></div>

    <!-- 地名搜索结果列表 -->
    <el-dialog v-model="searchDialog" title="搜索结果" width="520px">
      <el-table :data="searchResults" size="small" @row-click="locateTo" style="cursor: pointer;">
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column prop="address" label="地址" min-width="200" show-overflow-tooltip />
        <el-table-column label="坐标" width="170">
          <template #default="{ row }">{{ row.lng.toFixed(5) }}, {{ row.lat.toFixed(5) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button v-if="canEdit" size="small" type="success" plain @click.stop="addFromSearch(row)">添加为巡检点</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <span style="font-size: 12px; color: #909399;">点击行定位到该位置，或直接「添加为巡检点」（GCJ-02 坐标，与高德底图一致）</span>
      </template>
    </el-dialog>

    <!-- 按经纬度添加巡检点对话框 -->
    <el-dialog v-model="coordDialog.visible" title="按经纬度添加巡检点" width="460px">
      <div class="gen-field">
        <div class="gen-label">经度（-180 ~ 180）</div>
        <el-input-number v-model="coordDialog.lng" :min="-180" :max="180" :precision="6" :controls="false" style="width: 100%" />
      </div>
      <div class="gen-field">
        <div class="gen-label">纬度（-90 ~ 90）</div>
        <el-input-number v-model="coordDialog.lat" :min="-90" :max="90" :precision="6" :controls="false" style="width: 100%" />
      </div>
      <div class="tip">使用 GCJ-02 坐标（与高德底图一致）；确定后地图会飞到该位置，再命名保存</div>
      <template #footer>
        <el-button @click="coordDialog.visible = false">取消</el-button>
        <el-button type="primary" :disabled="coordDialog.lng == null || coordDialog.lat == null" @click="confirmAddCoord">
          下一步：命名并保存
        </el-button>
      </template>
    </el-dialog>

    <!-- 生成模拟航点对话框 -->
    <el-dialog v-model="genDialog.visible" title="生成模拟航点" width="460px">
      <div class="gen-field">
        <div class="gen-label">航点数量（含起飞点，2~30）</div>
        <el-input-number v-model="genDialog.count" :min="2" :max="30" />
      </div>
      <div class="gen-field">
        <div class="gen-label">起飞点中心</div>
        <el-radio-group v-model="genDialog.center" class="gen-radios">
          <el-radio value="picked" :disabled="!picked" class="gen-radio">
            使用当前选中位置
            <span class="gen-coord">
              {{ picked ? `${picked.lng.toFixed(6)}, ${picked.lat.toFixed(6)}` : '（暂无：请搜索 / 定位，或用选点模式点击地图）' }}
            </span>
          </el-radio>
          <el-radio value="task" :disabled="!startPointCoord" class="gen-radio">
            使用任务起飞点
            <span class="gen-coord">
              {{ startPointCoord ? `${startPointCoord.lng}, ${startPointCoord.lat}` : '（任务未设起飞点）' }}
            </span>
          </el-radio>
        </el-radio-group>
        <el-alert
          v-if="!picked && !startPointCoord"
          type="warning"
          :closable="false"
          title="请先在地图上定位或点选一个位置作为起飞点"
          style="margin-top: 10px"
        >
          <el-button v-if="canEdit" size="small" type="warning" plain @click="goPickStartPoint">去选起飞点</el-button>
        </el-alert>
      </div>
      <template #footer>
        <el-button @click="genDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="generating" :disabled="!genDialog.center" @click="confirmGenerate">生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { fetchTask, fetchWaypoints, generateWaypoints, addWaypoint, deleteWaypoint, renameWaypoint as renameWaypointApi, updateTask, requestPlan, fetchNoFlyZones } from '../api/plan'

const route = useRoute()
const router = useRouter()
const taskId = Number(route.query.taskId)
const myId = JSON.parse(localStorage.getItem('user') || 'null')?.userId
const isAdmin = JSON.parse(localStorage.getItem('user') || 'null')?.role === 'admin'
// 航点写操作仅 owner/admin（公开任务他人只读 + 可规划），后端 TaskAccessGuard 兜底
const canEdit = computed(() => isAdmin || task.value?.createdBy === myId)
const algorithm = ref('ga')
const maxRange = ref(20)
const loading = ref(false)
const locating = ref(false)
const generating = ref(false)
const addMode = ref(false)
const info = ref(null)
const picked = ref(null)
const mapEl = ref(null)
const task = ref(null)
// 高德地名搜索
const amapReady = ref(false)
const searchKeyword = ref('')
const searching = ref(false)
const searchDialog = ref(false)
const searchResults = ref([])

let map = null
let waypointLayer = null
const wpMarkers = new Map() // 航点 id → Leaflet marker（供列表定位）
let pathLayer = null
let orderLayer = null
let noFlyLayer = null
let bufferLayer = null
let pickedMarker = null
const waypoints = ref([])
// 航点右键菜单
const ctxMenu = ref({ visible: false, x: 0, y: 0, waypoint: null })

const statusMap = {
  pending: ['待执行', 'info'],
  executing: ['执行中', 'primary'],
  completed: ['已完成', 'success'],
  cancelled: ['已取消', 'danger']
}
const statusText = computed(() => statusMap[task.value?.status]?.[0] || task.value?.status || '--')
const statusType = computed(() => statusMap[task.value?.status]?.[1] || 'info')

// 收敛曲线 → SVG polyline 点串
const convPoints = computed(() => {
  const data = info.value?.convergence
  if (!data || data.length < 2) return ''
  const max = Math.max(...data)
  const min = Math.min(...data)
  const span = max - min || 1
  const W = 300
  const H = 60
  return data
    .map((v, i) => {
      const x = (i / (data.length - 1)) * W
      const y = H - ((v - min) / span) * (H - 10) - 5
      return `${x.toFixed(1)},${y.toFixed(1)}`
    })
    .join(' ')
})

function initMap() {
  // 高德底图（国内可访问，带中文标注）
  map = L.map(mapEl.value).setView([31.23, 121.50], 13)
  L.tileLayer('https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}', {
    subdomains: ['1', '2', '3', '4'],
    maxZoom: 18,
    attribution: '© 高德地图'
  }).addTo(map)

  // 点击地图：仅「新增巡检点」选点模式响应——落绿点并保存；普通模式点击不选点
  map.on('click', e => {
    closeCtxMenu()
    if (!addMode.value) return
    const lng = e.latlng.lng
    const lat = e.latlng.lat
    picked.value = { lng, lat }
    if (pickedMarker) map.removeLayer(pickedMarker)
    pickedMarker = L.circleMarker([lat, lng], {
      radius: 7, color: '#16a34a', fillColor: '#16a34a', fillOpacity: 1
    }).bindPopup(`经度: ${lng.toFixed(6)}<br/>纬度: ${lat.toFixed(6)}`).addTo(map)
    pickedMarker.openPopup()
    saveWaypoint(lng, lat)
  })
}

function toggleAddMode() {
  addMode.value = !addMode.value
  if (addMode.value) {
    ElMessage.info('选点模式：在地图上点击目标位置')
  } else if (pickedMarker) {
    // 取消选点：清掉地图上的绿点
    map.removeLayer(pickedMarker)
    pickedMarker = null
  }
}

// 保存真实巡检点入库（defaultName：搜索结果可直接用地名作默认名）
async function saveWaypoint(lng, lat, defaultName) {
  try {
    const { value: name } = await ElMessageBox.prompt(
      `坐标：经度 ${lng.toFixed(6)}，纬度 ${lat.toFixed(6)}`,
      '新增巡检点',
      {
        inputValue: defaultName || `巡检点${waypoints.value.length + 1}`,
        inputPattern: /\S+/,
        inputErrorMessage: '名称不能为空',
        confirmButtonText: '保存',
        cancelButtonText: '取消'
      }
    )
    const { data: wp } = await addWaypoint(taskId, { name, longitude: lng, latitude: lat })
    waypoints.value.push(wp)
    drawWaypoint(wp)
    addMode.value = false
    if (pickedMarker) {
      // 已成为正式航点：清掉临时绿点，避免残留
      map.removeLayer(pickedMarker)
      pickedMarker = null
    }
    ElMessage.success(`巡检点"${wp.name}"已保存 (#${wp.id})`)
  } catch (e) {
    if (e === 'cancel' || e === 'close') return // 用户取消
    const msg = e.response?.data?.message || e.message
    ElMessage.error(`保存失败：${msg}`)
  }
}

// 地图上显示绿色选中点（定位/搜索/点击共用）
function showPicked(lat, lng, popupHtml) {
  picked.value = { lng, lat }
  map.setView([lat, lng], Math.max(map.getZoom(), 15))
  if (pickedMarker) map.removeLayer(pickedMarker)
  pickedMarker = L.circleMarker([lat, lng], {
    radius: 8, color: '#16a34a', fillColor: '#16a34a', fillOpacity: 1
  }).bindPopup(popupHtml).addTo(map)
  pickedMarker.openPopup()
}

// 浏览器定位到当前真实位置
function locateMe() {
  if (!navigator.geolocation) {
    ElMessage.warning('浏览器不支持定位')
    return
  }
  locating.value = true
  navigator.geolocation.getCurrentPosition(
    pos => {
      const lat = pos.coords.latitude
      const lng = pos.coords.longitude
      showPicked(lat, lng, `我的位置<br/>经度: ${lng.toFixed(6)}<br/>纬度: ${lat.toFixed(6)}`)
      ElMessage.success('已定位到当前位置')
      locating.value = false
    },
    err => {
      locating.value = false
      const m = String(err.message || err.code)
      if (m.includes('secure origins') || m.includes('secure context')) {
        ElMessage.warning('浏览器定位仅支持 localhost 或 HTTPS 访问：请用 http://localhost:5173 打开，或改用搜索地名定位')
      } else {
        ElMessage.warning('定位失败：' + m)
      }
    },
    { enableHighAccuracy: true, timeout: 10000 }
  )
}

// 高德地名搜索（AMap.PlaceSearch，结果为 GCJ-02 坐标，与底图一致）
function searchPlaces() {
  const kw = searchKeyword.value.trim()
  if (!kw) return
  searching.value = true
  const ps = new AMap.PlaceSearch({ city: '全国', citylimit: false, pageSize: 8 })
  ps.search(kw, (status, result) => {
    searching.value = false
    if (status === 'complete' && result.poiList && result.poiList.pois.length) {
      searchResults.value = result.poiList.pois.map(p => ({
        name: p.name,
        address: p.address || (p.pname || '') + (p.cityname || '') + (p.adname || ''),
        lng: p.location.lng,
        lat: p.location.lat
      }))
      searchDialog.value = true
    } else if (status === 'no_data') {
      ElMessage.warning('未找到相关地名，换个关键词试试')
    } else {
      // result 为高德错误码字符串，如 INVALID_USER_DOMAIN（key 配置了域名白名单）
      const reason = typeof result === 'string' ? result : result?.info || status
      const hint = reason === 'INVALID_USER_DOMAIN'
        ? '（请检查高德 key 的安全域名设置）'
        : ''
      ElMessage.error(`搜索失败：${reason} ${hint}`)
    }
  })
}

// 点击搜索结果行 → 定位并显示坐标
function locateTo(row) {
  showPicked(row.lat, row.lng, `${row.name}<br/>${row.address || ''}<br/>经度: ${row.lng.toFixed(6)}<br/>纬度: ${row.lat.toFixed(6)}`)
  searchDialog.value = false
  ElMessage.success(`已定位：${row.name}`)
}

// 搜索结果直接添加为巡检点（免二次选点）
function addFromSearch(row) {
  searchDialog.value = false
  showPicked(row.lat, row.lng, `${row.name}<br/>${row.address || ''}<br/>经度: ${row.lng.toFixed(6)}<br/>纬度: ${row.lat.toFixed(6)}`)
  saveWaypoint(row.lng, row.lat, row.name)
}

function closeCtxMenu() {
  ctxMenu.value.visible = false
}

// 右键航点 → 操作菜单（设为起飞点 / 删除）
function onWaypointContextMenu(e, wp) {
  const evt = e.originalEvent
  evt.preventDefault()
  ctxMenu.value = { visible: true, x: evt.clientX, y: evt.clientY, waypoint: wp }
}

// 设为起飞点（右键菜单 / 航点列表共用）
async function setStartPoint(wp) {
  closeCtxMenu()
  if (!wp) return
  try {
    await updateTask(taskId, { startPointId: wp.id })
    task.value.startPointId = wp.id
    await drawWaypoints()
    ElMessage.success(`已将"${wp.name || '巡检点' + wp.id}"设为起飞点`)
  } catch (e) {
    ElMessage.error('设置失败：' + (e.response?.data?.message || e.message))
  }
}

// 删除巡检点（右键菜单 / 航点列表共用）
async function removeWaypoint(wp) {
  closeCtxMenu()
  if (!wp) return
  try {
    await ElMessageBox.confirm(
      `确定删除"${wp.name || '巡检点' + wp.id}"（#${wp.id}）？`,
      '删除巡检点',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await deleteWaypoint(taskId, wp.id)
    if (pathLayer) { map.removeLayer(pathLayer); pathLayer = null }
    info.value = null
    await drawWaypoints()
    ElMessage.success('巡检点已删除')
  } catch (e) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error('删除失败：' + (e.response?.data?.message || e.message))
  }
}

// 重命名巡检点（航点列表）
async function renameWaypoint(wp) {
  try {
    const { value: name } = await ElMessageBox.prompt(
      `将"${wp.name || '巡检点' + wp.id}"重命名为：`,
      '重命名巡检点',
      {
        inputValue: wp.name || '巡检点' + wp.id,
        inputPattern: /\S+/,
        inputErrorMessage: '名称不能为空',
        confirmButtonText: '保存',
        cancelButtonText: '取消'
      }
    )
    await renameWaypointApi(taskId, wp.id, name)
    await drawWaypoints()
    ElMessage.success('已重命名')
  } catch (e) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error('重命名失败：' + (e.response?.data?.message || e.message))
  }
}

// 绘制单个航点（起点橙色高亮）
function drawWaypoint(w, startId) {
  const isStart = Number(w.id) === Number(startId)
  const m = L.circleMarker([w.latitude, w.longitude], {
    radius: isStart ? 9 : 6,
    color: isStart ? '#e6501a' : '#0f3d91',
    fillColor: isStart ? '#e6501a' : '#0f3d91',
    fillOpacity: 1
  }).bindPopup(`${isStart ? '【起飞点】' : ''}${w.name || '巡检点' + w.id} (#${w.id})<br/>经度: ${w.longitude}<br/>纬度: ${w.latitude}<br/><span style="color:#909399">右键 / 列表可操作该点</span>`)
    .on('contextmenu', e => onWaypointContextMenu(e, w))
    .addTo(waypointLayer)
  wpMarkers.set(w.id, m)
}

// 航点列表：定位到航点并打开其气泡
function locateWaypoint(wp) {
  const m = wpMarkers.get(wp.id)
  if (!m) return
  map.setView(m.getLatLng(), Math.max(map.getZoom(), 15))
  m.openPopup()
}

// 禁飞区数据（展示用 + 规划时传给算法服务做避障）
const noFlyZones = ref([])
// 禁飞区安全距离 buffer（km）：路径与禁飞区圆保持的最小余量
const bufferKm = ref(0.5)

// 禁飞区图层：数据库 no_fly_zone → 红色半透明圆形叠加在高德瓦片上（只读展示，不影响航点交互）
async function loadNoFlyZones() {
  try {
    const { data } = await fetchNoFlyZones()
    noFlyZones.value = data
    if (noFlyLayer) map.removeLayer(noFlyLayer)
    noFlyLayer = L.layerGroup()
    data.forEach(z => {
      L.circle([Number(z.centerLat), Number(z.centerLng)], {
        radius: Number(z.radiusKm) * 1000,
        color: '#dc2626',
        fillColor: '#dc2626',
        fillOpacity: 0.15,
        weight: 2
      })
        .bindPopup(`${z.name}<br/>半径 ${z.radiusKm} km`)
        .addTo(noFlyLayer)
    })
    noFlyLayer.addTo(map)
    drawBufferCircles()
  } catch (e) {
    console.warn('禁飞区加载失败', e)
  }
}

// 禁飞区"安全距离"膨胀圈（真实圆 + bufferKm，橙色虚线）：绕行航线贴的就是
// 这条圈，它与红色真实圆之间的空隙即当前设置的安全距离，随输入框实时重画
function drawBufferCircles() {
  if (!map) return
  if (bufferLayer) map.removeLayer(bufferLayer)
  bufferLayer = L.layerGroup()
  noFlyZones.value.forEach(z => {
    L.circle([Number(z.centerLat), Number(z.centerLng)], {
      radius: (Number(z.radiusKm) + (Number(bufferKm.value) || 0)) * 1000,
      color: '#f97316',
      weight: 1.5,
      dashArray: '6 6',
      fill: false,
      interactive: false
    }).addTo(bufferLayer)
  })
  bufferLayer.addTo(map)
}
watch(bufferKm, drawBufferCircles)

// ── 安全环内航点标红 + 阻断规划 ───────────────────────────────────
// 距任一禁飞区真实圆边缘 < bufferKm 的巡检点：访问它航线必然进入安全区（几何必然），
// 地图红圈标记 + 面板报错 + 禁用「开始规划」——用户须调小安全距离或将航点移出安全环后才能规划
function _havKm(lat1, lng1, lat2, lng2) {
  const R = 6371.0088
  const p1 = (lat1 * Math.PI) / 180
  const p2 = (lat2 * Math.PI) / 180
  const dp = p2 - p1
  const dl = ((lng2 - lng1) * Math.PI) / 180
  const a = Math.sin(dp / 2) ** 2 + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) ** 2
  return 2 * R * Math.asin(Math.sqrt(a))
}

const ringInWaypoints = computed(() => {
  const bk = Number(bufferKm.value) || 0
  if (bk <= 0 || !noFlyZones.value.length) return []
  return waypoints.value.filter(w =>
    noFlyZones.value.some(z =>
      _havKm(Number(w.latitude), Number(w.longitude), Number(z.centerLat), Number(z.centerLng)) -
        Number(z.radiusKm) < bk - 1e-6
    )
  )
})

function isRingIn(wp) {
  return ringInWaypoints.value.some(r => r.id === wp.id)
}

const ringInTip = computed(() => {
  const parts = ringInWaypoints.value.map(w => {
    let best = Infinity
    noFlyZones.value.forEach(z => {
      const g = _havKm(Number(w.latitude), Number(w.longitude), Number(z.centerLat), Number(z.centerLng)) -
        Number(z.radiusKm)
      if (g < best) best = g
    })
    return `${w.name || '巡检点#' + w.id} 距边缘仅 ${best.toFixed(2)}km`
  })
  return `${parts.join('、')}，均小于安全距离 ${bufferKm.value}km——访问这些点的航线必然进入安全环。请先调小上方「安全距离」，或将标红巡检点移到橙色虚线圈外，再开始规划`
})

let ringWarnLayer = null
function drawRingWarnHalos() {
  if (!map) return
  if (ringWarnLayer) map.removeLayer(ringWarnLayer)
  ringWarnLayer = L.layerGroup()
  ringInWaypoints.value.forEach(w => {
    L.circleMarker([Number(w.latitude), Number(w.longitude)], {
      radius: 13,
      color: '#dc2626',
      weight: 3,
      fill: false,
      interactive: false
    }).addTo(ringWarnLayer)
  })
  ringWarnLayer.addTo(map)
}
watch(ringInWaypoints, drawRingWarnHalos)

async function drawWaypoints() {
  const [{ data }, { data: t }] = await Promise.all([fetchWaypoints(taskId), fetchTask(taskId)])
  task.value = t
  waypoints.value = data
  wpMarkers.clear()
  if (waypointLayer) map.removeLayer(waypointLayer)
  waypointLayer = L.layerGroup()
  data.forEach(w => drawWaypoint(w, t.startPointId))
  waypointLayer.addTo(map)
  // 航点集变化后旧的访问序号已失效
  if (orderLayer) {
    map.removeLayer(orderLayer)
    orderLayer = null
  }
  if (data.length) map.fitBounds(L.latLngBounds(data.map(w => [w.latitude, w.longitude])), { padding: [40, 40] })
}

// 规划完成后按访问顺序在每个航点上叠加序号牌（起点=起，其后 1..n），便于看清先后
function drawOrderMarkers(route) {
  if (orderLayer) map.removeLayer(orderLayer)
  orderLayer = L.layerGroup()
  const posMap = new Map(waypoints.value.map(w => [w.id, [w.latitude, w.longitude]]))
  route.forEach((id, i) => {
    // 末位是回到起点，不重复标注
    if (i === 0 || (i === route.length - 1 && id === route[0])) return
    const pos = posMap.get(id)
    if (!pos) return
    const label = String(i)
    L.marker(pos, {
      interactive: false,
      zIndexOffset: 500,
      icon: L.divIcon({
        className: '',
        html: `<div style="width:22px;height:22px;border-radius:11px;background:#0f3d91;color:#fff;font-size:12px;font-weight:700;line-height:18px;text-align:center;border:2px solid #fff;box-shadow:0 0 3px rgba(0,0,0,.4)">${label}</div>`,
        iconSize: [22, 22],
        iconAnchor: [11, 11]
      })
    }).addTo(orderLayer)
  })
  orderLayer.addTo(map)
}

// 生成模拟航点对话框
const genDialog = ref({ visible: false, count: 8, center: null })
// 按经纬度添加巡检点
const coordDialog = ref({ visible: false, lng: null, lat: null })

function openCoordDialog() {
  coordDialog.value.lng = picked.value ? Number(picked.value.lng.toFixed(6)) : null
  coordDialog.value.lat = picked.value ? Number(picked.value.lat.toFixed(6)) : null
  coordDialog.value.visible = true
}

function confirmAddCoord() {
  const { lng, lat } = coordDialog.value
  if (lng == null || lat == null) return
  coordDialog.value.visible = false
  picked.value = { lng, lat }
  if (map) map.flyTo([lat, lng], Math.max(map.getZoom(), 14))
  saveWaypoint(lng, lat)
}
// 任务起飞点坐标（来自 start_point_id 对应航点）
const startPointCoord = computed(() => {
  const spid = task.value?.startPointId
  if (!spid) return null
  const w = waypoints.value.find(x => x.id === spid)
  return w ? { lng: Number(w.longitude), lat: Number(w.latitude) } : null
})

function openGenDialog() {
  genDialog.value.count = 8
  // 默认中心：优先当前选中位置，其次任务起飞点
  genDialog.value.center = picked.value ? 'picked' : (startPointCoord.value ? 'task' : null)
  genDialog.value.visible = true
}

function goPickStartPoint() {
  genDialog.value.visible = false
  if (!addMode.value) toggleAddMode()
  ElMessage.info('请在地图上点击目标位置作为起飞点')
}

async function confirmGenerate() {
  const center = genDialog.value.center === 'picked'
    ? { lng: picked.value.lng, lat: picked.value.lat }
    : startPointCoord.value
  const count = genDialog.value.count
  generating.value = true
  try {
    await generateWaypoints(taskId, count, center.lng, center.lat)
    genDialog.value.visible = false
    await drawWaypoints()
    // 航点已重建，旧规划路径与结果失效
    if (pathLayer) {
      map.removeLayer(pathLayer)
      pathLayer = null
    }
    info.value = null
    ElMessage.success(`已生成 ${count} 个模拟航点，起飞点=${center.lng.toFixed(6)}, ${center.lat.toFixed(6)}`)
  } catch (e) {
    const msg = e.response?.data?.message || e.response?.data?.detail || e.message
    ElMessage.error(`生成失败：${msg}`)
  } finally {
    generating.value = false
  }
}

async function run() {
  loading.value = true
  info.value = null
  try {
    // 起点：后端记录的起点航点，否则取 seq 最小的一个
    const sorted = [...waypoints.value].sort((a, b) => a.seq - b.seq)
    const start = sorted[0]
    const rest = sorted.slice(1)
    const { data } = await requestPlan({
      taskId,
      algorithm: algorithm.value,
      startPoint: { id: start.id, lng: start.longitude, lat: start.latitude },
      waypoints: rest.map(w => ({ id: w.id, lng: w.longitude, lat: w.latitude })),
      constraints: {
        maxFlightRangeKm: maxRange.value,
        noFlyBufferKm: bufferKm.value,
        noFlyZones: noFlyZones.value.map(z => ({ centerLng: z.centerLng, centerLat: z.centerLat, radiusKm: z.radiusKm }))
      }
    })
    info.value = data
    const posMap = new Map(waypoints.value.map(w => [w.id, [w.latitude, w.longitude]]))
    const latlngs = data.route.map(id => posMap.get(id)).filter(Boolean)
    // 避障折线：优先用后端透传的 detailPaths（含绕行弯折，元素 [lat,lng]），缺失则回退 route 直线
    const detail = (data.detailPaths || []).map(p => [p[0], p[1]])
    const lineCoords = detail.length >= 2 ? detail : latlngs
    if (pathLayer) map.removeLayer(pathLayer)
    if (lineCoords.length >= 2) {
      pathLayer = L.polyline(lineCoords, { color: '#e11d48', weight: 3, opacity: 0.85, dashArray: '8 6' })
        .bindPopup(`巡检路径：总航程 ${data.totalDistanceKm} km`).addTo(map)
      drawOrderMarkers(data.route)
      map.fitBounds(L.latLngBounds(lineCoords).pad(0.1))
    }
    ElMessage.success(`规划完成：总航程 ${data.totalDistanceKm} km`)
    if (!data.feasible) {
      ElMessage.warning(`总航程 ${data.totalDistanceKm} km 超过续航上限 ${maxRange.value} km，结果不可行`)
    }
  } catch (e) {
    const msg = e.response?.data?.message || e.response?.data?.detail || e.message
    ElMessage.error(`规划失败：${msg}`)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  if (!taskId) {
    ElMessage.error('缺少 taskId，请从任务列表进入')
    return
  }
  // 高德 JS API 在 index.html 同步加载；加载成功才显示搜索框
  amapReady.value = typeof window.AMap !== 'undefined' && !!window.AMap.PlaceSearch
  await nextTick()
  initMap()
  loadNoFlyZones()
  await drawWaypoints()
})

onBeforeUnmount(() => {
  if (map) map.remove()
})
</script>

<style scoped>
.plan-page {
  height: calc(100vh - 62px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.task-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 16px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;
}
.task-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.back-btn {
  flex-shrink: 0;
}
.task-no {
  font-weight: 700;
  color: #0f3d91;
}
.task-name {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.result-brief {
  flex-shrink: 0;
}
.main {
  flex: 1;
  display: flex;
  min-height: 0;
}
.side-panel {
  width: 340px;
  flex-shrink: 0;
  overflow-y: auto;
  padding: 12px;
  background: #f5f7fa;
  border-right: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.panel-card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 12px;
}
.panel-title {
  font-size: 14px;
  font-weight: 700;
  color: #1f2937;
  margin-bottom: 10px;
  padding-left: 8px;
  border-left: 3px solid #0f3d91;
  line-height: 1.2;
}
.field-row {
  display: flex;
  align-items: center;
  margin: 8px 0;
  gap: 8px;
}
.field-label {
  font-size: 13px;
  color: #4b5563;
}
.field-unit {
  font-size: 13px;
  color: #6b7280;
}
.btn-col {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.btn-col .el-button + .el-button {
  margin-left: 0;
}
/* 生成模拟航点对话框 */
.gen-field {
  margin-bottom: 14px;
}
.gen-label {
  font-size: 13px;
  color: #4b5563;
  margin-bottom: 6px;
}
.gen-radios {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-start;
}
.gen-radio {
  height: auto;
  margin-right: 0;
  white-space: normal;
}
.gen-coord {
  font-size: 12px;
  color: #909399;
  margin-left: 4px;
}
.ctx-mask {
  position: fixed;
  inset: 0;
  z-index: 2999;
}
.ctx-menu {
  position: fixed;
  z-index: 3000;
  min-width: 130px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  padding: 4px 0;
}
.ctx-item {
  padding: 8px 16px;
  font-size: 13px;
  color: #374151;
  cursor: pointer;
}
.ctx-item:hover {
  background: #f3f4f6;
  color: #0f3d91;
}
.ctx-danger {
  color: #dc2626;
}
.ctx-danger:hover {
  background: #fef2f2;
  color: #dc2626;
}
.tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}
.coord-main {
  font-family: Consolas, monospace;
  font-size: 14px;
  font-weight: 600;
  color: #0f3d91;
}
.big-dist {
  font-size: 34px;
  font-weight: 800;
  color: #e6501a;
  line-height: 1.1;
}
.big-dist.infeasible {
  color: #dc2626;
}
.big-dist .unit {
  font-size: 15px;
  font-weight: 600;
  margin-left: 4px;
  color: #9a3412;
}
.big-dist-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 10px;
}
.stat-grid {
  display: flex;
  gap: 8px;
}
.stat-item {
  flex: 1;
  background: #f9fafb;
  border-radius: 6px;
  padding: 8px;
  text-align: center;
}
.stat-value {
  font-size: 15px;
  font-weight: 700;
  color: #1f2937;
}
.stat-value .unit-sm {
  font-size: 11px;
  color: #6b7280;
  margin-left: 2px;
}
.stat-label {
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
}
.conv {
  margin-top: 10px;
}
.conv-chart {
  width: 100%;
  height: 60px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  background: #fafafa;
}
.map-wrap {
  flex: 1;
  min-width: 0;
  position: relative;
}
.map-el {
  position: absolute;
  inset: 0;
}
</style>

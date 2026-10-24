<template>
  <div class="nfz-page">
    <div class="top-bar">
      <el-button plain @click="router.push('/tasks')">返回列表</el-button>
      <el-button @click="load">刷新</el-button>
      <el-button type="primary" @click="openCreate">新增禁飞区</el-button>
      <span class="tip">仅管理员可设置。可在地图上点选圆心，点击红色圆形可直接编辑。</span>
    </div>

    <div class="main">
      <div class="panel">
        <div class="panel-title">禁飞区列表（{{ zones.length }}）</div>
        <el-table
          :data="zones"
          size="small"
          border
          height="100%"
          highlight-current-row
          @row-click="focusZone"
        >
          <el-table-column prop="name" label="名称" min-width="110" />
          <el-table-column label="中心经纬度" min-width="150">
            <template #default="{ row }">
              {{ Number(row.centerLng).toFixed(6) }}, {{ Number(row.centerLat).toFixed(6) }}
            </template>
          </el-table-column>
          <el-table-column label="半径" width="80">
            <template #default="{ row }">{{ Number(row.radiusKm).toFixed(2) }} km</template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button size="small" @click.stop="openEdit(row)">编辑</el-button>
              <el-button size="small" type="danger" @click.stop="remove(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="!zones.length" class="empty">暂无禁飞区，点击「新增禁飞区」开始添加</div>
      </div>

      <div ref="mapEl" class="map"></div>
    </div>

    <el-dialog
      v-model="dialog.visible"
      :title="dialog.id ? '编辑禁飞区' : '新增禁飞区'"
      width="440px"
      @closed="endPick"
    >
      <el-form label-width="96px">
        <el-form-item label="名称">
          <el-input v-model="dialog.name" maxlength="64" placeholder="如：陆家嘴禁飞区" />
        </el-form-item>
        <el-form-item label="中心经度">
          <el-input-number v-model="dialog.centerLng" :precision="6" :step="0.001" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="中心纬度">
          <el-input-number v-model="dialog.centerLat" :precision="6" :step="0.001" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="半径 (km)">
          <el-input-number v-model="dialog.radiusKm" :min="0.05" :max="50" :step="0.1" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item>
          <el-button :type="dialog.picking ? 'warning' : 'default'" @click="togglePick">
            {{ dialog.picking ? '取消选点' : '在地图上点选圆心' }}
          </el-button>
          <span v-if="dialog.picking" class="picking-tip">请在地图上点击圆心位置</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchNoFlyZones, createNoFlyZone, updateNoFlyZone, deleteNoFlyZone } from '../api/noFlyZone'

const router = useRouter()

const zones = ref([])
const mapEl = ref(null)
let map = null
let zoneLayer = null
let previewCircle = null

const dialog = reactive({
  visible: false,
  id: null,
  name: '',
  centerLng: 121.50,
  centerLat: 31.23,
  radiusKm: 1,
  picking: false
})
const saving = ref(false)

async function load() {
  try {
    const { data } = await fetchNoFlyZones()
    zones.value = data
    renderZones()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '加载禁飞区失败')
  }
}

function initMap() {
  map = L.map(mapEl.value).setView([31.23, 121.50], 12)
  L.tileLayer('https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}', {
    subdomains: ['1', '2', '3', '4'],
    maxZoom: 18,
    attribution: '© 高德地图'
  }).addTo(map)
  zoneLayer = L.layerGroup().addTo(map)

  // 点击地图：选点模式下写入圆心；否则忽略
  map.on('click', e => {
    if (!dialog.picking) return
    dialog.centerLng = Number(e.latlng.lng.toFixed(6))
    dialog.centerLat = Number(e.latlng.lat.toFixed(6))
    endPick()
    renderPreview()
  })
}

function renderZones() {
  if (!map) return
  zoneLayer.clearLayers()
  zones.value.forEach(z => {
    const circle = L.circle([Number(z.centerLat), Number(z.centerLng)], {
      radius: Number(z.radiusKm) * 1000,
      color: '#ef4444',
      weight: 2,
      fillColor: '#ef4444',
      fillOpacity: 0.18
    })
    circle.bindTooltip(`${z.name} · ${Number(z.radiusKm).toFixed(2)} km`, { direction: 'top' })
    circle.on('click', e => {
      L.DomEvent.stopPropagation(e)
      openEdit(z)
    })
    zoneLayer.addLayer(circle)
  })
}

// 弹窗内的虚线预览圆（跟随输入的圆心与半径）
function renderPreview() {
  if (!map) return
  if (previewCircle) {
    map.removeLayer(previewCircle)
    previewCircle = null
  }
  if (!dialog.visible) return
  const lng = Number(dialog.centerLng)
  const lat = Number(dialog.centerLat)
  const r = Number(dialog.radiusKm)
  if (!isFinite(lng) || !isFinite(lat) || !isFinite(r) || r <= 0) return
  previewCircle = L.circle([lat, lng], {
    radius: r * 1000,
    color: '#2563eb',
    weight: 2,
    dashArray: '6,6',
    fillColor: '#2563eb',
    fillOpacity: 0.08
  }).addTo(map)
  map.setView([lat, lng], Math.max(map.getZoom(), 12))
}

function openCreate() {
  dialog.id = null
  dialog.name = ''
  dialog.centerLng = 121.50
  dialog.centerLat = 31.23
  dialog.radiusKm = 1
  dialog.picking = false
  dialog.visible = true
  nextTick(renderPreview)
}

function openEdit(row) {
  dialog.id = row.id
  dialog.name = row.name || ''
  dialog.centerLng = Number(row.centerLng)
  dialog.centerLat = Number(row.centerLat)
  dialog.radiusKm = Number(row.radiusKm)
  dialog.picking = false
  dialog.visible = true
  nextTick(() => {
    renderPreview()
    map.setView([Number(row.centerLat), Number(row.centerLng)], 13)
  })
}

function focusZone(row) {
  map.setView([Number(row.centerLat), Number(row.centerLng)], 13)
}

function togglePick() {
  dialog.picking = !dialog.picking
}

function endPick() {
  dialog.picking = false
}

async function save() {
  if (dialog.centerLng === null || dialog.centerLat === null || dialog.centerLng === undefined || dialog.centerLat === undefined) {
    ElMessage.error('请填写中心经纬度（或在地图上点选圆心）')
    return
  }
  if (!dialog.radiusKm || Number(dialog.radiusKm) <= 0) {
    ElMessage.error('半径必须大于 0')
    return
  }
  const payload = {
    name: dialog.name || '禁飞区',
    centerLng: Number(dialog.centerLng),
    centerLat: Number(dialog.centerLat),
    radiusKm: Number(dialog.radiusKm)
  }
  saving.value = true
  try {
    if (dialog.id) {
      await updateNoFlyZone(dialog.id, payload)
      ElMessage.success('已更新禁飞区')
    } else {
      await createNoFlyZone(payload)
      ElMessage.success('已新增禁飞区')
    }
    dialog.visible = false
    await load()
  } catch (e) {
    const msg = e.response?.data?.message || e.response?.data?.errors?.join('；') || '保存失败'
    ElMessage.error(msg)
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除禁飞区「${row.name}」？删除后规划将不再避让该区域。`,
      '删除确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await deleteNoFlyZone(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '删除失败')
  }
}

onMounted(async () => {
  await nextTick()
  initMap()
  await load()
})

onBeforeUnmount(() => {
  if (map) {
    map.remove()
    map = null
  }
})
</script>

<style scoped>
.nfz-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 110px);
}
.top-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.tip {
  color: #6b7280;
  font-size: 13px;
  margin-left: 6px;
}
.main {
  flex: 1;
  display: flex;
  gap: 12px;
  min-height: 0;
}
.panel {
  width: 480px;
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 12px;
  min-height: 0;
}
.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: #0f3d91;
  padding-bottom: 8px;
  border-bottom: 1px solid #e5e7eb;
  margin-bottom: 8px;
}
.panel :deep(.el-table) {
  flex: 1;
}
.empty {
  color: #9ca3af;
  font-size: 13px;
  text-align: center;
  padding: 40px 0;
}
.map {
  flex: 1;
  min-width: 0;
  border-radius: 8px;
  overflow: hidden;
}
.picking-tip {
  margin-left: 10px;
  font-size: 13px;
  color: #f59e0b;
}
</style>

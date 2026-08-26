<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api/client'

interface HostMetrics {
  systemCpuPercent: number | null
  processCpuPercent: number | null
  memTotalBytes: number
  memUsedBytes: number
  heapMaxBytes: number
  heapUsedBytes: number
  diskTotalBytes: number
  diskUsableBytes: number
  availableProcessors: number
  systemLoadAverage: number
}

interface MiddlewareItem {
  name: string
  status: 'UP' | 'DOWN' | 'DISABLED'
  latencyMs?: number
  detail?: string
}

interface ContainerItem {
  name: string
  state: string
  status: string
  image: string
}

interface MiddlewareItemPlus extends MiddlewareItem {
  container?: string
}

const loading = ref(false)
const host = ref<HostMetrics | null>(null)
const middleware = ref<MiddlewareItemPlus[]>([])
const containers = ref<ContainerItem[]>([])
const dockerAvailable = ref(true)
/** 仅显示本项目（zhixu-*）与运维工具容器，过滤其他项目遗留容器 */
const projectOnly = ref(true)
const allContainers = ref<ContainerItem[]>([])
let pollTimer: ReturnType<typeof setInterval> | null = null

// ---------- 容器日志下钻 ----------
const logDialogVisible = ref(false)
const logContainerName = ref('')
const logContent = ref('')
const logLoading = ref(false)
const logTail = ref(300)
const logAutoRefresh = ref(false)
const logContentEl = ref<HTMLElement>()
let logPollTimer: ReturnType<typeof setInterval> | null = null

const openContainerLogs = async (name: string) => {
  logContainerName.value = name
  logContent.value = ''
  logDialogVisible.value = true
  await fetchContainerLogs()
}

const fetchContainerLogs = async () => {
  if (!logContainerName.value) return
  logLoading.value = true
  try {
    const { data: body } = await http.get(`/api/v1/admin/system/containers/${logContainerName.value}/logs`, {
      params: { tail: logTail.value }
    })
    logContent.value = body.data.logs || '（暂无日志）'
    nextTick(() => {
      const el = logContentEl.value
      if (el) el.scrollTop = el.scrollHeight
    })
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '拉取容器日志失败')
  } finally {
    logLoading.value = false
  }
}

const stopLogPolling = () => {
  if (logPollTimer) {
    clearInterval(logPollTimer)
    logPollTimer = null
  }
}

const handleLogDialogChange = (visible: boolean) => {
  stopLogPolling()
  if (visible && logAutoRefresh.value) {
    logPollTimer = setInterval(() => {
      if (logAutoRefresh.value) fetchContainerLogs()
    }, 5000)
  }
}

const GB = 1024 * 1024 * 1024
const fmtGB = (bytes?: number) => (bytes == null ? '-' : (bytes / GB).toFixed(1) + ' GB')

const percent = (used?: number | null, total?: number | null) =>
  used == null || total == null || total <= 0 ? 0 : Math.min(100, Math.round((used / total) * 100))

const statusType = (s: string) => (s === 'UP' ? 'success' : s === 'DOWN' ? 'danger' : 'info')
const statusText = (s: string) => (s === 'UP' ? '正常' : s === 'DOWN' ? '异常' : '未启用')
const containerStateType = (s: string) =>
  s === 'running' ? 'success' : s === 'restarting' ? 'warning' : 'danger'
const healthOf = (statusText: string) => {
  if (statusText?.includes('(healthy')) return { text: '健康', type: 'success' as const }
  if (statusText?.includes('(unhealthy')) return { text: '不健康', type: 'danger' as const }
  if (statusText?.includes('(health: starting')) return { text: '启动中', type: 'warning' as const }
  return null
}

const load = async () => {
  loading.value = true
  try {
    const { data: body } = await http.get('/api/v1/admin/system/runtime')
    host.value = body.data.host
    middleware.value = body.data.middleware || []
    allContainers.value = body.data.containers?.containers || []
    applyContainerFilter()
    dockerAvailable.value = !!body.data.containers?.available
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载运行状态失败')
  } finally {
    loading.value = false
  }
}

const applyContainerFilter = () => {
  containers.value = projectOnly.value
    ? allContainers.value.filter((c) => c.name?.startsWith('zhixu-') || c.name === 'portainer')
    : allContainers.value
}

onMounted(async () => {
  await load()
  pollTimer = setInterval(load, 10000)
})

onBeforeUnmount(() => {
  if (pollTimer) clearInterval(pollTimer)
  stopLogPolling()
})
</script>

<template>
  <div class="admin-runtime">
    <div class="runtime-header">
      <div>
        <h2>运行状态</h2>
        <p class="runtime-sub">服务器 / 中间件 / 容器实时状态（10s 自动刷新）
          <a href="http://localhost:9000" target="_blank" rel="noopener">Portainer 容器面板 →</a>
        </p>
      </div>
      <el-button :loading="loading" @click="load">立即刷新</el-button>
    </div>

    <!-- 服务器指标 -->
    <el-alert
      type="info"
      :closable="false"
      class="source-alert"
    >
      <template #title>
        指标来源：Docker Desktop 的 WSL2 虚拟机（Ubuntu）与后端容器 cgroup 视角，非 Windows 宿主机。
        CPU 核数为后端容器的配额核数（compose 限制 cpus=1）；磁盘为 WSL2 虚拟盘（动态扩展）。
      </template>
    </el-alert>
    <div class="stat-grid">
      <el-card shadow="never" class="stat-card">
        <div class="stat-title">CPU（{{ host?.availableProcessors ?? '-' }} 核）</div>
        <el-progress
          type="dashboard"
          :percentage="host?.systemCpuPercent ?? 0"
          :color="percent(host?.systemCpuPercent, 100) > 85 ? '#f56c6c' : '#67c23a'"
        />
        <div class="stat-detail">
          系统 {{ host?.systemCpuPercent ?? '-' }}% · 后端进程 {{ host?.processCpuPercent ?? '-' }}%
        </div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-title">内存</div>
        <el-progress
          type="dashboard"
          :percentage="percent(host?.memUsedBytes, host?.memTotalBytes)"
          :color="percent(host?.memUsedBytes, host?.memTotalBytes) > 85 ? '#f56c6c' : '#409eff'"
        />
        <div class="stat-detail">{{ fmtGB(host?.memUsedBytes) }} / {{ fmtGB(host?.memTotalBytes) }}</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-title">磁盘</div>
        <el-progress
          type="dashboard"
          :percentage="percent(host ? (host.diskTotalBytes - host.diskUsableBytes) : 0, host?.diskTotalBytes)"
          :color="percent(host ? (host.diskTotalBytes - host.diskUsableBytes) : 0, host?.diskTotalBytes) > 85 ? '#f56c6c' : '#409eff'"
        />
        <div class="stat-detail">
          已用 {{ fmtGB(host ? host.diskTotalBytes - host.diskUsableBytes : 0) }} / {{ fmtGB(host?.diskTotalBytes) }}
        </div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-title">后端 JVM 堆</div>
        <el-progress
          type="dashboard"
          :percentage="percent(host?.heapUsedBytes, host?.heapMaxBytes)"
          :color="percent(host?.heapUsedBytes, host?.heapMaxBytes) > 85 ? '#f56c6c' : '#e6a23c'"
        />
        <div class="stat-detail">{{ fmtGB(host?.heapUsedBytes) }} / {{ fmtGB(host?.heapMaxBytes) }}</div>
      </el-card>
    </div>

    <!-- 中间件 -->
    <el-card shadow="never" class="section-card">
      <template #header><span class="section-title">中间件健康（点击行查看服务日志）</span></template>
      <el-table
        :data="middleware"
        size="default"
        :show-header="false"
        class="clickable-table"
        @row-click="(row: MiddlewareItemPlus) => row.container && openContainerLogs(row.container)"
      >
        <el-table-column prop="name" label="名称" width="180" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small" disable-transitions>
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="延迟" width="90">
          <template #default="{ row }">
            <span v-if="row.latencyMs != null" class="latency">{{ row.latencyMs }}ms</span>
            <span v-else class="latency">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="detail" label="详情">
          <template #default="{ row }">
            <span class="detail" :title="row.detail">{{ row.detail || '-' }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 容器 -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="container-header">
          <span class="section-title">Docker 容器（{{ containers.length }}）</span>
          <el-switch
            v-model="projectOnly"
            active-text="仅看本项目"
            @change="applyContainerFilter"
          />
        </div>
      </template>
      <el-alert
        v-if="!dockerAvailable"
        type="info"
        :closable="false"
        title="Docker Socket 不可用（本地直跑后端时无法采集容器状态）"
      />
      <el-table v-else :data="containers" size="default">
        <el-table-column prop="name" label="容器" width="200" />
        <el-table-column label="运行态" width="110">
          <template #default="{ row }">
            <el-tag :type="containerStateType(row.state)" size="small" disable-transitions>
              {{ row.state }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态明细" min-width="180" />
        <el-table-column prop="image" label="镜像" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openContainerLogs(row.name)">日志</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 容器日志弹窗 -->
    <el-dialog
      v-model="logDialogVisible"
      :title="`容器日志 — ${logContainerName}`"
      width="860px"
      top="6vh"
      @opened="handleLogDialogChange(true)"
      @closed="handleLogDialogChange(false)"
    >
      <div class="log-dialog-toolbar">
        <el-select v-model="logTail" style="width: 130px" @change="fetchContainerLogs">
          <el-option :value="200" label="最近 200 行" />
          <el-option :value="500" label="最近 500 行" />
          <el-option :value="1000" label="最近 1000 行" />
        </el-select>
        <el-switch v-model="logAutoRefresh" active-text="自动刷新" />
        <el-button :loading="logLoading" @click="fetchContainerLogs">刷新</el-button>
      </div>
      <div ref="logContentEl" v-loading="logLoading" class="log-viewer">{{ logContent }}</div>
    </el-dialog>
  </div>
</template>

<style scoped>
.admin-runtime {
  padding: 24px 28px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.runtime-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  flex-wrap: wrap;
}

.runtime-header h2 {
  margin: 0;
  font-size: 20px;
  color: #111827;
}

.runtime-sub {
  margin: 6px 0 0;
  font-size: 12px;
  color: #909399;
}

.runtime-sub a {
  color: #409eff;
  text-decoration: none;
}

.source-alert {
  border-radius: 10px;
}

.clickable-table :deep(.el-table__row) {
  cursor: pointer;
}

.log-dialog-toolbar {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 10px;
}

.log-viewer {
  height: 55vh;
  overflow-y: auto;
  background: #141a22;
  border: 1px solid #2a3340;
  border-radius: 8px;
  padding: 10px 12px;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12px;
  line-height: 18px;
  color: #cfd8e3;
  white-space: pre-wrap;
  word-break: break-all;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.stat-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.stat-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.stat-detail {
  font-size: 12px;
  color: #909399;
}

.section-card {
  border-radius: 12px;
}

.container-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.section-title {
  font-weight: 600;
  color: #303133;
}

.latency {
  color: #606266;
  font-size: 12px;
}

.detail {
  color: #909399;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
}

@media (max-width: 1100px) {
  .stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>

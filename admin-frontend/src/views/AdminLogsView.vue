<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api/client'

interface LogEntry {
  id: number
  timestamp: number
  level: string
  logger: string
  thread: string
  traceId: string
  message: string
}

type LevelFilter = 'ALL' | 'INFO' | 'WARN' | 'ERROR'

const entries = ref<LogEntry[]>([])
const level = ref<LevelFilter>('ALL')
const keyword = ref('')
const appliedKeyword = ref('')
const autoRefresh = ref(true)
const loading = ref(false)
const matchedTotal = ref(0)

let maxId = 0
let pollTimer: ReturnType<typeof setInterval> | null = null
const listEl = ref<HTMLElement>()

const levelTagType = (lv: string) =>
  lv === 'ERROR' ? 'danger' : lv === 'WARN' ? 'warning' : lv === 'DEBUG' ? 'info' : 'primary'

const formatTime = (ts: number) => {
  const d = new Date(ts)
  const p = (n: number, w = 2) => String(n).padStart(w, '0')
  return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}.${p(d.getMilliseconds(), 3)}`
}

const isNearBottom = () => {
  const el = listEl.value
  if (!el) return true
  return el.scrollHeight - el.scrollTop - el.clientHeight < 80
}

const scrollToBottom = () => {
  nextTick(() => {
    const el = listEl.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

const fetchFull = async () => {
  loading.value = true
  try {
    const { data: body } = await http.get('/api/v1/admin/system/logs', {
      params: {
        level: level.value,
        keyword: appliedKeyword.value || undefined,
        sinceId: 0,
        limit: 500
      }
    })
    entries.value = body.data.entries || []
    maxId = body.data.maxId || 0
    matchedTotal.value = body.data.matchedTotal || 0
    scrollToBottom()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载日志失败')
  } finally {
    loading.value = false
  }
}

const fetchIncremental = async () => {
  try {
    const { data: body } = await http.get('/api/v1/admin/system/logs', {
      params: {
        level: level.value,
        keyword: appliedKeyword.value || undefined,
        sinceId: maxId,
        limit: 500
      }
    })
    const fresh: LogEntry[] = body.data.entries || []
    matchedTotal.value = body.data.matchedTotal || 0
    if (!fresh.length) return
    const stick = isNearBottom()
    entries.value = [...entries.value, ...fresh].slice(-2000)
    maxId = body.data.maxId || maxId
    if (stick) scrollToBottom()
  } catch {
    // 轮询失败静默跳过，下个周期重试
  }
}

const handleSearch = () => {
  appliedKeyword.value = keyword.value.trim()
  fetchFull()
}

const handleDownload = () => {
  window.open('/api/v1/admin/system/logs/download', '_blank', 'noopener')
}

onMounted(async () => {
  await fetchFull()
  pollTimer = setInterval(() => {
    if (autoRefresh.value) fetchIncremental()
  }, 4000)
})

onBeforeUnmount(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<template>
  <div class="admin-logs">
    <div class="logs-header">
      <div>
        <h2>系统日志</h2>
        <p class="logs-sub">实时查看后端日志（保留最近约 3000 条）；深度分析可
          <a href="http://localhost:5601/app/discover" target="_blank" rel="noopener">在 Kibana 中打开 →</a>
        </p>
      </div>
      <div class="logs-actions">
        <el-switch v-model="autoRefresh" active-text="自动刷新" />
        <el-button :loading="loading" @click="fetchFull">刷新</el-button>
        <el-button @click="handleDownload">导出</el-button>
      </div>
    </div>

    <div class="logs-toolbar">
      <el-radio-group v-model="level" @change="fetchFull">
        <el-radio-button label="ALL">全部</el-radio-button>
        <el-radio-button label="INFO">INFO+</el-radio-button>
        <el-radio-button label="WARN">WARN+</el-radio-button>
        <el-radio-button label="ERROR">仅 ERROR</el-radio-button>
      </el-radio-group>
      <el-input
        v-model="keyword"
        placeholder="搜索关键字 / logger / traceId"
        clearable
        class="keyword-input"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      >
        <template #append>
          <el-button @click="handleSearch">搜索</el-button>
        </template>
      </el-input>
      <span class="matched-hint" v-if="appliedKeyword">命中 {{ matchedTotal }} 条</span>
    </div>

    <div ref="listEl" class="logs-list" v-loading="loading">
      <div v-if="!entries.length && !loading" class="logs-empty">
        暂无匹配日志（环形缓冲仅保留启动后的日志，重启后清空；历史日志见 Kibana）
      </div>
      <div v-for="entry in entries" :key="entry.id" class="log-row" :class="'lv-' + entry.level">
        <span class="log-time">{{ formatTime(entry.timestamp) }}</span>
        <el-tag :type="levelTagType(entry.level)" size="small" class="log-level" disable-transitions>
          {{ entry.level }}
        </el-tag>
        <span class="log-logger" :title="entry.logger">{{ entry.logger }}</span>
        <pre class="log-message">{{ entry.message }}</pre>
      </div>
    </div>
  </div>
</template>

<style scoped>
.admin-logs {
  display: flex;
  flex-direction: column;
  gap: 14px;
  height: 100vh;
  padding: 24px 28px;
  box-sizing: border-box;
}

.logs-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  flex-wrap: wrap;
}

.logs-header h2 {
  margin: 0;
  font-size: 20px;
  color: #e8edf4;
}

.logs-sub {
  margin: 6px 0 0;
  font-size: 12px;
  color: #8b96a5;
}

.logs-sub a {
  color: #6ea8fe;
  text-decoration: none;
}

.logs-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logs-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.keyword-input {
  width: 320px;
}

.matched-hint {
  color: #8b96a5;
  font-size: 12px;
}

.logs-list {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  background: #141a22;
  border: 1px solid #2a3340;
  border-radius: 10px;
  padding: 10px 0;
  font-family: 'JetBrains Mono', Consolas, monospace;
}

.logs-empty {
  padding: 40px;
  text-align: center;
  color: #8b96a5;
  font-size: 13px;
}

.log-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 3px 14px;
}

.log-row:hover {
  background: rgba(110, 168, 254, 0.06);
}

.log-row.lv-ERROR {
  background: rgba(245, 108, 108, 0.1);
}

.log-row.lv-WARN {
  background: rgba(230, 162, 60, 0.07);
}

.log-time {
  flex-shrink: 0;
  color: #5d6b7d;
  font-size: 12px;
  line-height: 20px;
}

.log-level {
  flex-shrink: 0;
  width: 58px;
  justify-content: center;
}

.log-logger {
  flex-shrink: 0;
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #7d8b9d;
  font-size: 11px;
  line-height: 20px;
}

.log-message {
  margin: 0;
  flex: 1 1 auto;
  min-width: 0;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 12px;
  line-height: 20px;
  color: #cfd8e3;
}

.lv-ERROR .log-message {
  color: #f5a0a0;
}

.lv-WARN .log-message {
  color: #e6c07b;
}
</style>

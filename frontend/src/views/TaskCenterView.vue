<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Document, MagicStick } from '@element-plus/icons-vue'
import {
  getActiveTasks,
  getRecentTasks,
  retryTask,
  deleteTaskRecord,
  clearAllTaskRecords,
  type ActiveTask,
  type RecentTask
} from '../api/file'
import { getAiConfig, type AiUserConfig } from '../api/ai'

const props = defineProps<{
  embedded?: boolean
}>()

const router = useRouter()

interface UnifiedTask {
  key: string
  type: 'doc' | 'ai'
  subType: string
  taskId?: string
  noteId?: string
  name: string
  status: string
  statusLabel: string
  progress: number
  elapsedSeconds?: number
  stage?: string
  error?: string
  failReason?: string
  cleanChunks?: { total: number; success: number; failed: number; processing: number }
  embedChunks?: { total: number; success: number; failed: number; processing: number }
}

const loading = ref(false)
const initialLoading = ref(true)
const polling = ref(false)
const docActive = ref<ActiveTask[]>([])
const docRecent = ref<RecentTask[]>([])
const retryingId = ref<string | null>(null)
const deletingId = ref<string | null>(null)
const clearing = ref(false)
const activeTab = ref<'all' | 'running' | 'completed' | 'failed'>('all')
const currentPage = ref(1)
const pageSize = ref(10)
let pollTimer: ReturnType<typeof setInterval> | null = null
let tickTimer: ReturnType<typeof setInterval> | null = null
const localElapsed = ref<Map<string, number>>(new Map())
const aiConfig = ref<AiUserConfig | null>(null)
const aiConfigLoading = ref(false)

const showAiConfigHint = computed(() => {
  if (aiConfigLoading.value) return false
  if (aiConfig.value?.configured) return false
  // 有进行中的任务时才提示，避免空状态打扰
  return allTasks.value.some((t) => isRunningStatus(t.status))
})

const STAGE_LABELS: Record<string, string> = {
  PENDING: '等待处理',
  PARSING: '解析文本',
  CLEANING: 'AI 清洗中',
  EMBEDDING: '向量化中',
  COMPLETED: '已完成',
  FAILED: '失败',
  SKIPPED: '已跳过'
}

const STAGE_ORDER = ['PENDING', 'PARSING', 'CLEANING', 'EMBEDDING', 'COMPLETED']

const isRunningStatus = (status: string) => status !== 'COMPLETED' && status !== 'FAILED' && status !== 'SKIPPED'

const statusType = (status: string) => {
  if (status === 'COMPLETED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'SKIPPED') return 'warning'
  return 'primary'
}

const formatElapsed = (seconds?: number) => {
  if (!seconds || seconds <= 0) return '-'
  if (seconds < 60) return `${seconds} 秒`
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return s > 0 ? `${m} 分 ${s} 秒` : `${m} 分钟`
}

const elapsedText = (task: UnifiedTask) => {
  if (isRunningStatus(task.status)) {
    const local = localElapsed.value.get(task.key)
    if (local != null) return formatElapsed(local)
  }
  return formatElapsed(task.elapsedSeconds)
}

const syncLocalElapsed = () => {
  const next = new Map(localElapsed.value)
  allTasks.value.forEach((task) => {
    if (isRunningStatus(task.status) && task.elapsedSeconds != null) {
      if (!next.has(task.key)) {
        next.set(task.key, task.elapsedSeconds)
      }
    } else {
      next.delete(task.key)
    }
  })
  localElapsed.value = next
}

const chunkText = (stats?: { total: number; success: number; failed: number; processing: number }) => {
  if (!stats || stats.total <= 0) return ''
  const parts: string[] = []
  if (stats.success > 0) parts.push(`成功 ${stats.success}`)
  if (stats.processing > 0) parts.push(`处理中 ${stats.processing}`)
  if (stats.failed > 0) parts.push(`失败 ${stats.failed}`)
  return `${parts.join(' / ') || '待处理'} · 共 ${stats.total} 块`
}

const allTasks = computed<UnifiedTask[]>(() => {
  const list: UnifiedTask[] = []
  const seen = new Set<string>()
  const addUnique = (item: UnifiedTask) => {
    if (seen.has(item.key)) return
    seen.add(item.key)
    list.push(item)
  }

  docActive.value.forEach((task) => {
    addUnique({
      key: `d-${task.taskId}`,
      type: 'doc',
      subType: task.subType || '文档处理',
      taskId: task.taskId,
      noteId: task.noteId,
      name: task.fileName || `任务 #${task.taskId}`,
      status: task.status || 'PENDING',
      statusLabel: STAGE_LABELS[task.status] || task.status || '处理中',
      progress: task.progress || 0,
      elapsedSeconds: task.elapsedSeconds,
      stage: task.currentStage,
      cleanChunks: task.cleanChunks,
      embedChunks: task.embedChunks
    })
  })
  docRecent.value.forEach((task) => {
    addUnique({
      key: `d-${task.taskId}`,
      type: 'doc',
      subType: task.subType || '文档处理',
      taskId: task.taskId,
      noteId: task.noteId,
      name: task.fileName || `任务 #${task.taskId}`,
      status: task.status || 'COMPLETED',
      statusLabel: STAGE_LABELS[task.status] || task.status || '已完成',
      progress: task.progress || (task.status === 'COMPLETED' ? 100 : 0),
      elapsedSeconds: task.elapsedSeconds,
      failReason: task.failReason,
      cleanChunks: task.cleanChunks,
      embedChunks: task.embedChunks
    })
  })
  return list
})

const filteredTasks = computed(() => {
  if (activeTab.value === 'all') return allTasks.value
  if (activeTab.value === 'running') return allTasks.value.filter((t) => isRunningStatus(t.status))
  if (activeTab.value === 'completed') return allTasks.value.filter((t) => t.status === 'COMPLETED' || t.status === 'SKIPPED')
  if (activeTab.value === 'failed') return allTasks.value.filter((t) => t.status === 'FAILED' || Boolean(t.error))
  return allTasks.value
})

const totalCount = computed(() => filteredTasks.value.length)

const pagedTasks = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredTasks.value.slice(start, start + pageSize.value)
})

const runningCount = computed(() => allTasks.value.filter((t) => isRunningStatus(t.status)).length)
const hasAny = computed(() => allTasks.value.length > 0)

const loadTasks = async (silent = false) => {
  if (loading.value) return
  if (silent) {
    if (polling.value) return
    polling.value = true
  } else {
    loading.value = true
  }
  try {
    const [active, recent] = await Promise.allSettled([getActiveTasks(), getRecentTasks()])
    if (active.status === 'fulfilled') {
      docActive.value = active.value
    }
    if (recent.status === 'fulfilled') {
      docRecent.value = recent.value
    }
  } catch {
    // 文档任务轮询失败保留上次数据
  }
  syncLocalElapsed()
  if (silent) {
    polling.value = false
  } else {
    loading.value = false
    initialLoading.value = false
  }
}

const onTabChange = () => {
  currentPage.value = 1
}

const onRetry = async (row: UnifiedTask) => {
  if (row.type !== 'doc' || !row.taskId) return
  retryingId.value = row.taskId
  try {
    await retryTask(row.taskId)
    ElMessage.success('任务已重新提交')
    await loadTasks(true)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '重试失败')
  } finally {
    retryingId.value = null
  }
}

const onDelete = async (row: UnifiedTask) => {
  if (row.type !== 'doc' || !row.taskId) return
  try {
    await ElMessageBox.confirm(`确定删除任务「${row.name}」的记录吗？（不影响已写回的笔记正文）`, '删除任务', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  deletingId.value = row.taskId
  try {
    await deleteTaskRecord(row.taskId)
    ElMessage.success('任务记录已删除')
    await loadTasks(true)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '删除失败')
  } finally {
    deletingId.value = null
  }
}

const onClearAll = async () => {
  try {
    await ElMessageBox.confirm('确定清空全部任务记录吗？（仅清除任务日志，不影响笔记正文）', '清空任务', {
      type: 'warning',
      confirmButtonText: '清空',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  clearing.value = true
  try {
    await clearAllTaskRecords()
    ElMessage.success('任务记录已清空')
    await loadTasks(true)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '清空失败')
  } finally {
    clearing.value = false
  }
}

const openNote = (noteId?: string) => {
  if (noteId) router.push(`/notes/edit/${noteId}`)
}

const loadAiConfig = async () => {
  aiConfigLoading.value = true
  try {
    aiConfig.value = await getAiConfig()
  } catch {
    aiConfig.value = null
  } finally {
    aiConfigLoading.value = false
  }
}

const goToAiSettings = () => {
  router.push('/settings/ai')
}

onMounted(() => {
  loadTasks(false)
  loadAiConfig()
  pollTimer = setInterval(() => loadTasks(true), 6000)
  tickTimer = setInterval(() => {
    const next = new Map(localElapsed.value)
    let changed = false
    allTasks.value.forEach((task) => {
      if (isRunningStatus(task.status) && next.has(task.key)) {
        next.set(task.key, next.get(task.key)! + 1)
        changed = true
      }
    })
    if (changed) {
      localElapsed.value = next
    }
  }, 1000)
})

onBeforeUnmount(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
  if (tickTimer) {
    clearInterval(tickTimer)
    tickTimer = null
  }
})
</script>

<template>
  <div class="task-center" :class="{ embedded: embedded }">
    <div v-if="!embedded" class="page-header">
      <div>
        <h2>任务中心</h2>
        <p>文档解析 / AI 清洗 / 向量化入库的细粒度进度监控（每 3 秒自动刷新）</p>
      </div>
    </div>

    <div class="task-toolbar">
      <el-radio-group v-model="activeTab" size="small" class="task-tabs" @change="onTabChange">
        <el-radio-button label="all">全部</el-radio-button>
        <el-radio-button label="running">进行中</el-radio-button>
        <el-radio-button label="completed">已完成</el-radio-button>
        <el-radio-button label="failed">失败</el-radio-button>
      </el-radio-group>
      <div class="toolbar-right">
        <el-button size="small" text type="danger" :loading="clearing" :disabled="!hasAny" @click="onClearAll">
          清空全部任务记录
        </el-button>
      </div>
    </div>

    <el-alert
      v-if="showAiConfigHint"
      type="info"
      :closable="false"
      show-icon
      class="ai-config-hint"
    >
      <template #title>
        任务处理较慢？
        <el-button link type="primary" size="small" @click="goToAiSettings">配置自己的 AI API Key</el-button>
        可避免平台共享额度拥堵，通常响应更快更稳定。
      </template>
    </el-alert>

    <el-empty
      v-if="!initialLoading && !hasAny"
      description="暂无任务记录。上传文档后，系统会自动完成清洗、摘要/关键词生成并向量化入库"
      :image-size="embedded ? 60 : 90"
    />

    <template v-else>
      <div class="table-summary">共 {{ totalCount }} 条</div>

      <div class="table-wrapper" v-loading="initialLoading" element-loading-text="加载任务中…">
        <el-table :data="pagedTasks" row-key="key" size="small" stripe>
          <el-table-column label="任务名称" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="name-cell">
                <span>{{ row.name }}</span>
                <el-tag
                  v-if="row.noteId"
                  size="small"
                  type="info"
                  effect="plain"
                  class="note-link"
                  @click="openNote(row.noteId)"
                >
                  笔记 #{{ row.noteId }}
                </el-tag>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="类型" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="row.type === 'ai' ? 'warning' : 'primary'" effect="plain">
                {{ row.subType }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="statusType(row.status)">
                {{ row.statusLabel }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column label="进度" width="140">
            <template #default="{ row }">
              <el-progress :percentage="row.progress" :stroke-width="6" />
            </template>
          </el-table-column>

          <el-table-column label="耗时" width="100">
            <template #default="{ row }">
              {{ elapsedText(row) }}
            </template>
          </el-table-column>

          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 'FAILED' || row.error || (row.type === 'ai' && isRunningStatus(row.status))"
                size="small"
                type="primary"
                text
                :loading="row.type === 'doc' && retryingId === row.taskId"
                @click="onRetry(row)"
              >
                重试
              </el-button>
              <el-button
                size="small"
                text
                type="danger"
                :loading="row.type === 'doc' && deletingId === row.taskId"
                @click="onDelete(row)"
              >
                删除
              </el-button>
              <el-button
                v-if="row.noteId"
                size="small"
                text
                @click="openNote(row.noteId)"
              >
                打开笔记
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="totalCount"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @size-change="currentPage = 1"
          @current-change="() => {}"
        />
      </div>
    </template>
  </div>
</template>

<style scoped>
.task-center {
  max-width: 1100px;
  margin: 0 auto;
}

.task-center.embedded {
  max-width: none;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 18px;
}

.page-header h2 {
  font-size: 20px;
  color: #1f2937;
  margin: 0 0 6px;
}

.page-header p {
  font-size: 13px;
  color: #909399;
  margin: 0;
}

.task-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.ai-config-hint {
  margin-bottom: 12px;
}

.ai-config-hint :deep(.el-alert__title) {
  font-size: 13px;
}

.task-tabs :deep(.el-radio-button__inner) {
  padding: 6px 14px;
}

.table-summary {
  font-size: 13px;
  color: #606266;
  margin-bottom: 8px;
}

.table-wrapper {
  border-radius: 8px;
  overflow: hidden;
}

.name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.note-link {
  cursor: pointer;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>

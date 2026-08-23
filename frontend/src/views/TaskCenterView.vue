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
import { listAIAnalysisTasks, submitAIAnalysis, deleteAIAnalysisTask, type AiAnalysisTaskItem } from '../api/note'

const props = defineProps<{
  embedded?: boolean
}>()

const router = useRouter()

interface UnifiedTask {
  key: string
  type: 'doc' | 'ai'
  subType: string
  taskId?: number
  noteId?: number
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
const docActive = ref<ActiveTask[]>([])
const docRecent = ref<RecentTask[]>([])
const aiActive = ref<AiAnalysisTaskItem[]>([])
const aiRecent = ref<AiAnalysisTaskItem[]>([])
const retryingId = ref<number | null>(null)
const retryingAiId = ref<number | null>(null)
const deletingId = ref<number | null>(null)
const deletingAiId = ref<number | null>(null)
const clearing = ref(false)
const activeTab = ref<'all' | 'running' | 'completed' | 'failed'>('all')
const currentPage = ref(1)
const pageSize = ref(10)
let pollTimer: ReturnType<typeof setInterval> | null = null

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
  aiActive.value.forEach((task) => {
    addUnique({
      key: `a-${task.noteId}`,
      type: 'ai',
      subType: 'AI 整理',
      noteId: task.noteId,
      name: task.noteTitle || `笔记 #${task.noteId}`,
      status: isRunningStatus(task.stage || '') ? 'RUNNING' : (task.stage || 'RUNNING'),
      statusLabel: task.stage ? STAGE_LABELS[task.stage] || task.stage : 'AI 整理中',
      progress: 50,
      elapsedSeconds: task.elapsedSeconds,
      stage: task.stage,
      error: task.error || undefined
    })
  })
  aiRecent.value.forEach((task) => {
    addUnique({
      key: `a-${task.noteId}`,
      type: 'ai',
      subType: 'AI 整理',
      noteId: task.noteId,
      name: task.noteTitle || `笔记 #${task.noteId}`,
      status: task.error ? 'FAILED' : 'COMPLETED',
      statusLabel: task.error ? '失败' : '已完成',
      progress: task.error ? 0 : 100,
      elapsedSeconds: task.elapsedSeconds,
      error: task.error || undefined
    })
  })
  return list.sort((a, b) => (b.taskId || b.noteId || 0) - (a.taskId || a.noteId || 0))
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

const loadTasks = async () => {
  if (loading.value) return
  loading.value = true
  try {
    docActive.value = await getActiveTasks()
    docRecent.value = await getRecentTasks()
  } catch {
    // 轮询失败保留上次数据
  }
  try {
    const ai = await listAIAnalysisTasks()
    aiActive.value = ai.active || []
    aiRecent.value = ai.recent || []
  } catch {
    // AI 整理任务列表轮询失败不影响文档任务
  }
  loading.value = false
}

const onTabChange = () => {
  currentPage.value = 1
}

const onRetry = async (row: UnifiedTask) => {
  if (row.type === 'doc' && row.taskId) {
    retryingId.value = row.taskId
    try {
      await retryTask(row.taskId)
      ElMessage.success('已重新提交处理')
      await loadTasks()
    } catch (e: any) {
      ElMessage.error(e?.response?.data?.message || '重试失败')
    } finally {
      retryingId.value = null
    }
  } else if (row.type === 'ai' && row.noteId) {
    retryingAiId.value = row.noteId
    try {
      await submitAIAnalysis(row.noteId)
      ElMessage.success('AI 整理已重新提交')
      await loadTasks()
    } catch (e: any) {
      ElMessage.error(e?.response?.data?.message || '重试失败')
    } finally {
      retryingAiId.value = null
    }
  }
}

const onDelete = async (row: UnifiedTask) => {
  const name = row.type === 'doc' ? row.name : `笔记 ${row.name}`
  try {
    await ElMessageBox.confirm(`确定删除任务「${name}」的记录吗？（不影响已写回的笔记正文）`, '删除任务', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  if (row.type === 'doc' && row.taskId) {
    deletingId.value = row.taskId
    try {
      await deleteTaskRecord(row.taskId)
      ElMessage.success('任务记录已删除')
      await loadTasks()
    } catch (e: any) {
      ElMessage.error(e?.response?.data?.message || '删除失败')
    } finally {
      deletingId.value = null
    }
  } else if (row.type === 'ai' && row.noteId) {
    deletingAiId.value = row.noteId
    try {
      await deleteAIAnalysisTask(row.noteId)
      ElMessage.success('任务记录已删除')
      await loadTasks()
    } catch (e: any) {
      ElMessage.error(e?.response?.data?.message || '删除失败')
    } finally {
      deletingAiId.value = null
    }
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
    aiRecent.value.forEach((t) => deleteAIAnalysisTask(t.noteId).catch(() => undefined))
    ElMessage.success('任务记录已清空')
    await loadTasks()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '清空失败')
  } finally {
    clearing.value = false
  }
}

const openNote = (noteId?: number) => {
  if (noteId) router.push(`/notes/${noteId}`)
}

onMounted(() => {
  loadTasks()
  pollTimer = setInterval(loadTasks, 3000)
})

onBeforeUnmount(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
})
</script>

<template>
  <div class="task-center" :class="{ embedded: embedded }">
    <div v-if="!embedded" class="page-header">
      <div>
        <h2>任务中心</h2>
        <p>文档解析 / AI 清洗 / 向量化 / AI 整理的细粒度进度监控（每 3 秒自动刷新）</p>
      </div>
    </div>

    <div class="task-toolbar">
      <el-radio-group v-model="activeTab" size="small" class="task-tabs" @change="onTabChange">
        <el-radio-button label="all">全部</el-radio-button>
        <el-radio-button label="running">进行中</el-radio-button>
        <el-radio-button label="completed">已完成</el-radio-button>
        <el-radio-button label="failed">失败</el-radio-button>
      </el-radio-group>
      <el-button size="small" text type="danger" :loading="clearing" :disabled="!hasAny" @click="onClearAll">
        清空全部任务记录
      </el-button>
    </div>

    <el-empty
      v-if="!hasAny"
      description="暂无任务记录。上传文档或点击「AI 整理」后，任务会出现在这里"
      :image-size="embedded ? 60 : 90"
    />

    <template v-else>
      <div class="table-summary">共 {{ totalCount }} 条</div>

      <div class="table-wrapper" v-loading="loading">
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
              {{ formatElapsed(row.elapsedSeconds) }}
            </template>
          </el-table-column>

          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 'FAILED' || row.error || (row.type === 'ai' && isRunningStatus(row.status))"
                size="small"
                type="primary"
                text
                :loading="(row.type === 'doc' && retryingId === row.taskId) || (row.type === 'ai' && retryingAiId === row.noteId)"
                @click="onRetry(row)"
              >
                重试
              </el-button>
              <el-button
                size="small"
                text
                type="danger"
                :loading="(row.type === 'doc' && deletingId === row.taskId) || (row.type === 'ai' && deletingAiId === row.noteId)"
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

<script setup lang="ts">
import { onActivated, onBeforeUnmount, onDeactivated, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadFile, getTaskStatus, getTaskByNote, type UploadResponse } from '../api/file'
import { UploadFilled, Document, Picture } from '@element-plus/icons-vue'

const props = defineProps<{
  noteId?: number
}>()
const emit = defineEmits<{
  (e: 'uploaded', result: UploadResponse): void
  (e: 'taskCompleted'): void
}>()

const uploading = ref(false)
const progress = ref(0)
const stageText = ref('')
const taskFinished = ref(false)
let taskTimer: ReturnType<typeof setInterval> | null = null
let currentTaskId: number | null = null
let pollErrorCount = 0
let lastEmittedTaskId: number | null = null
const MAX_POLL_ERRORS = 10

const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/jpg']
const DOC_EXTS = ['txt', 'md', 'markdown', 'pdf', 'docx']

const isSupported = (file: File) => {
  if (IMAGE_TYPES.includes(file.type)) return true
  const name = (file.name || '').toLowerCase()
  return DOC_EXTS.some((ext) => name.endsWith('.' + ext))
}

const beforeUpload = (file: File) => {
  if (!isSupported(file)) {
    ElMessage.error('支持图片（JPG/PNG）与文档（txt/md/pdf/docx）')
    return false
  }
  if (file.size / 1024 / 1024 > 20) {
    ElMessage.error('文件需小于20MB')
    return false
  }
  return true
}

const STAGE_LABELS: Record<string, string> = {
  PENDING: '等待处理',
  CLEANING: 'AI 清洗中',
  COMPLETED: '清洗完成',
  FAILED: '处理失败'
}

/** 文档处理任务进度轮询（3s 一次，直到完成/失败；连续失败超过阈值自动停止） */
const startTaskPolling = (taskId: number) => {
  if (taskTimer) clearInterval(taskTimer)
  currentTaskId = taskId
  pollErrorCount = 0
  progress.value = 5
  stageText.value = '等待处理'
  taskFinished.value = false
  taskTimer = setInterval(async () => {
    try {
      const st = await getTaskStatus(taskId)
      pollErrorCount = 0
      progress.value = Math.max(progress.value, st.progress || 0)
      stageText.value = STAGE_LABELS[st.currentStage] || st.currentStage
      if (st.status === 'COMPLETED') {
        stopTaskPolling()
        progress.value = 100
        stageText.value = '清洗完成，正文已更新'
        taskFinished.value = true
        lastEmittedTaskId = taskId
        ElMessage.success('文档已清洗并保存为可读正文；点击"AI 整理"可生成摘要并向量化入库')
        emit('taskCompleted')
      } else if (st.status === 'FAILED') {
        stopTaskPolling()
        stageText.value = '处理失败'
        taskFinished.value = true
        ElMessage.error(`文档处理失败：${st.failReason || '未知原因'}，可重新上传`)
      }
    } catch {
      // 连续失败超阈值（后端重启/任务被清理等）停止轮询，避免永久空转
      pollErrorCount++
      if (pollErrorCount >= MAX_POLL_ERRORS) {
        stopTaskPolling()
        stageText.value = '任务状态获取失败，已停止轮询'
        taskFinished.value = true
      }
    }
  }, 3000)
}

const stopTaskPolling = () => {
  if (taskTimer) {
    clearInterval(taskTimer)
    taskTimer = null
  }
  currentTaskId = null
}

const handleUpload = async (file: File) => {
  if (!file) return
  uploading.value = true
  progress.value = 0
  stageText.value = ''
  taskFinished.value = false
  try {
    const res = await uploadFile(file, props.noteId, (percent) => {
      progress.value = percent
    })
    emit('uploaded', res)
    const isDoc = !IMAGE_TYPES.includes(file.type)
    ElMessage.success(isDoc ? '文档上传成功，正在后台解析清洗' : '上传成功')
    // 文档上传后开始后台处理（解析 + AI 清洗，完成写回正文），轮询进度
    if (res.taskId) {
      startTaskPolling(res.taskId)
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

onMounted(async () => {
  // 进入页面时检查该笔记是否有进行中的文档处理任务，有则恢复进度展示
  await recoverActiveTask()
})

// KeepAlive 缓存切换（路由离开编辑页）时停止轮询，返回时恢复，
// 避免在用户看不到的页面上持续空转并弹出无关提示
onDeactivated(() => {
  stopTaskPolling()
})

onActivated(async () => {
  await recoverActiveTask()
})

const recoverActiveTask = async () => {
  if (!props.noteId) return
  if (currentTaskId) return
  try {
    const task = await getTaskByNote(props.noteId)
    if (!task) return
    if (task.status && task.status !== 'COMPLETED' && task.status !== 'FAILED') {
      startTaskPolling(task.taskId)
      return
    }
    // 离开页面期间任务已结束：恢复终态展示；
    // 完成的任务补发一次 taskCompleted，确保返回编辑页时正文刷新
    if (task.status === 'COMPLETED') {
      progress.value = 100
      stageText.value = '清洗完成，正文已更新'
      taskFinished.value = true
      if (lastEmittedTaskId !== task.taskId) {
        lastEmittedTaskId = task.taskId
        emit('taskCompleted')
      }
    } else if (task.status === 'FAILED') {
      stageText.value = '处理失败'
      taskFinished.value = true
    }
  } catch {
    // 查询失败忽略
  }
}

onBeforeUnmount(() => {
  stopTaskPolling()
})

const onChange = (uploadFile: any) => {
  if (uploading.value) return
  if (uploadFile?.status && uploadFile.status !== 'ready') return
  const raw = uploadFile?.raw as File | undefined
  if (!raw) return
  handleUpload(raw)
}
</script>

<template>
  <el-upload
    class="upload-box"
    drag
    :auto-upload="false"
    :show-file-list="false"
    :before-upload="beforeUpload"
    :on-change="onChange"
    :accept="'.jpg,.jpeg,.png,.txt,.md,.markdown,.pdf,.docx'"
  >
    <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
    <div class="el-upload__text">拖拽或点击上传笔记素材</div>
    <div class="el-upload__tip">
      <span class="tip-item"><el-icon><Document /></el-icon> 文档：txt / md / pdf / docx（自动提取文本）</span>
      <span class="tip-item"><el-icon><Picture /></el-icon> 图片：JPG / PNG（OCR 识别）</span>
      <span class="tip-item">≤20MB</span>
    </div>
    <el-button type="primary" :loading="uploading" size="small" style="margin-top: 10px">
        {{ uploading ? `上传中 ${progress}%` : '开始上传' }}
      </el-button>
      <el-progress
        v-if="progress > 0 && progress < 100"
        :percentage="progress"
        :stroke-width="4"
        style="margin-top: 8px"
      />
      <div v-if="stageText" class="upload-stage" :class="{ done: taskFinished }">
        <span v-if="!taskFinished" class="stage-spinner" />
        {{ stageText }} {{ progress }}%
      </div>
  </el-upload>
</template>

<style scoped>
.upload-box {
  width: 100%;
}

.upload-stage {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #606266;
}

.upload-stage.done {
  color: #67c23a;
}

.stage-spinner {
  width: 12px;
  height: 12px;
  border: 2px solid #c0c4cc;
  border-top-color: #409eff;
  border-radius: 50%;
  animation: stage-spin 0.8s linear infinite;
}

@keyframes stage-spin {
  to {
    transform: rotate(360deg);
  }
}

.el-upload__tip {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  color: #909399;
  font-size: 12px;
  line-height: 1.7;
}

.tip-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
</style>

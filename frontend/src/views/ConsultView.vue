<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  User,
  ChatDotRound,
  Plus,
  Download,
  Delete,
  Promotion,
  Loading
} from '@element-plus/icons-vue'
import { submitAskStream, listAskRecords, deleteAsk, exportAsk, type AskRecord } from '../api/ask'

interface MessageItem {
  role: 'user' | 'assistant'
  content: string
  loading?: boolean
}

const escapeHtml = (text: string): string =>
  text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')

const messages = ref<MessageItem[]>([])
const input = ref('')
const sending = ref(false)
const scrollRef = ref<HTMLDivElement>()
const records = ref<AskRecord[]>([])
const recordsLoading = ref(false)
const currentRecordId = ref<string | null>(null)
let currentAbortController: AbortController | null = null
let currentTimeoutId: ReturnType<typeof setTimeout> | null = null

const isEmptyChat = computed(() => messages.value.length === 0)

const groupedRecords = computed(() => {
  const groups: { label: string; items: AskRecord[] }[] = []
  const today = new Date().setHours(0, 0, 0, 0)
  const yesterday = today - 86400000
  const weekAgo = today - 86400000 * 7
  const monthAgo = today - 86400000 * 30

  const buckets: Record<string, AskRecord[]> = {
    今天: [],
    昨天: [],
    最近7天: [],
    最近30天: [],
    更早: []
  }

  records.value.forEach((record) => {
    const t = record.createdAt ? new Date(record.createdAt).setHours(0, 0, 0, 0) : 0
    if (t === today) buckets['今天'].push(record)
    else if (t === yesterday) buckets['昨天'].push(record)
    else if (t > weekAgo) buckets['最近7天'].push(record)
    else if (t > monthAgo) buckets['最近30天'].push(record)
    else buckets['更早'].push(record)
  })

  Object.entries(buckets).forEach(([label, items]) => {
    if (items.length) groups.push({ label, items })
  })
  return groups
})

const scrollToBottom = async () => {
  await nextTick()
  if (scrollRef.value) {
    scrollRef.value.scrollTop = scrollRef.value.scrollHeight
  }
}

const cancelCurrentRequest = () => {
  if (currentAbortController) {
    currentAbortController.abort()
    currentAbortController = null
  }
  if (currentTimeoutId) {
    clearTimeout(currentTimeoutId)
    currentTimeoutId = null
  }
}

const clearChat = () => {
  currentRecordId.value = null
  messages.value = []
}

const loadRecords = async () => {
  recordsLoading.value = true
  try {
    records.value = await listAskRecords({ page: 1, size: 100 })
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载历史记录失败')
  } finally {
    recordsLoading.value = false
  }
}

const loadRecord = (record: AskRecord) => {
  currentRecordId.value = record.id
  messages.value = [
    { role: 'user', content: record.question },
    { role: 'assistant', content: record.answer }
  ]
  scrollToBottom()
}

const exportRecord = async (record: AskRecord) => {
  try {
    const payload = await exportAsk(record.id)
    const blob = new Blob([payload.content], { type: 'text/markdown;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = payload.fileName
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('问答记录已导出')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '导出失败')
  }
}

const deleteRecord = async (record: AskRecord) => {
  try {
    await ElMessageBox.confirm('确定删除这条问答记录吗？', '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteAsk(record.id)
    ElMessage.success('已删除')
    if (currentRecordId.value === record.id) {
      clearChat()
    }
    await loadRecords()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '删除失败')
  }
}

const send = async () => {
  const question = input.value.trim()
  if (!question || sending.value) return
  currentRecordId.value = null
  input.value = ''
  messages.value.push({ role: 'user', content: question })
  const assistant: MessageItem = { role: 'assistant', content: '', loading: true }
  messages.value.push(assistant)
  sending.value = true
  currentAbortController = new AbortController()
  currentTimeoutId = setTimeout(() => {
    currentAbortController?.abort()
  }, 180000)
  try {
    await submitAskStream(
      question,
      (chunk) => {
        assistant.content += chunk
        assistant.loading = false
        scrollToBottom()
      },
      currentAbortController.signal
    )
    if (assistant.content.trim().length === 0) {
      assistant.content = '（未获取到回答，请检查 AI 引擎配置后重试）'
    }
    await loadRecords()
  } catch (e: any) {
    if (e?.name === 'AbortError') {
      assistant.content += '\n（请求已取消或超时）'
    } else {
      assistant.content = `请求失败：${e?.message || '未知错误'}`
      ElMessage.error('问答请求失败，请确认 AI 引擎可用')
    }
  } finally {
    assistant.loading = false
    sending.value = false
    cancelCurrentRequest()
    scrollToBottom()
  }
}

onMounted(() => {
  loadRecords()
})

onBeforeUnmount(() => {
  cancelCurrentRequest()
})
</script>

<template>
  <div class="ask-layout">
    <aside class="chat-sidebar">
      <button type="button" class="new-chat-btn" @click="clearChat">
        <el-icon><Plus /></el-icon>
        <span>新对话</span>
      </button>

      <div v-loading="recordsLoading" class="history-list">
        <div v-if="records.length === 0 && !recordsLoading" class="history-empty">
          暂无问答记录
        </div>
        <div
          v-for="group in groupedRecords"
          :key="group.label"
          class="history-group"
        >
          <div class="history-group-title">{{ group.label }}</div>
          <div
            v-for="record in group.items"
            :key="record.id"
            class="history-item"
            :class="{ active: currentRecordId === record.id }"
            @click="loadRecord(record)"
          >
            <span class="history-item-text">{{ record.question }}</span>
            <div class="history-item-actions" @click.stop>
              <el-icon title="导出" @click="exportRecord(record)"><Download /></el-icon>
              <el-icon title="删除" @click="deleteRecord(record)"><Delete /></el-icon>
            </div>
          </div>
        </div>
      </div>
    </aside>

    <main class="chat-main">
      <header class="chat-header">
        <h2>知识问答</h2>
        <div class="header-actions">
          <el-button size="small" @click="clearChat">清空对话</el-button>
          <el-button size="small" type="primary" @click="$router.push('/notes')">去整理笔记</el-button>
        </div>
      </header>

      <div ref="scrollRef" class="chat-body">
        <template v-if="isEmptyChat">
          <div class="welcome-screen">
            <div class="welcome-icon">
              <el-icon><ChatDotRound /></el-icon>
            </div>
            <h1 class="welcome-title">准备好，随时开始</h1>
            <p class="welcome-subtitle">向我提问，我会先检索你的笔记，再生成回答</p>
          </div>
        </template>

        <template v-else>
          <div
            v-for="(msg, index) in messages"
            :key="index"
            class="chat-row"
            :class="msg.role"
          >
            <div class="avatar" :class="msg.role">
              <el-icon><User v-if="msg.role === 'user'" /><ChatDotRound v-else /></el-icon>
            </div>
            <div class="bubble">
              <div v-if="msg.loading" class="typing">正在检索你的知识库并生成回答…</div>
              <div v-else class="bubble-text" v-html="escapeHtml(msg.content).replace(/\n/g, '<br/>')"></div>
            </div>
          </div>
        </template>
      </div>

      <div class="chat-input-area">
        <div class="input-box">
          <el-input
            v-model="input"
            type="textarea"
            :rows="2"
            placeholder="输入你的问题，例如：我在笔记里记过 Spring Boot 的启动流程，帮我总结一下"
            resize="none"
            @keydown.ctrl.enter.prevent="send"
          />
          <button
            type="button"
            class="send-btn"
            :disabled="!input.trim() || sending"
            @click="send"
          >
            <el-icon v-if="!sending"><Promotion /></el-icon>
            <el-icon v-else class="is-loading"><Loading /></el-icon>
          </button>
        </div>
        <p class="input-tip">AI 生成内容仅供参考，请核实重要信息</p>
      </div>
    </main>
  </div>
</template>

<style scoped>
.ask-layout {
  display: flex;
  height: calc(100vh - 108px);
  min-height: 480px;
  margin: -20px -24px;
}

.chat-sidebar {
  width: 260px;
  flex-shrink: 0;
  background: #f9fafb;
  border-right: 1px solid #ebeef5;
  display: flex;
  flex-direction: column;
  padding: 14px;
}

.new-chat-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 10px 0;
  border: 1px solid #d1d5db;
  border-radius: 12px;
  background: #fff;
  color: #374151;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.18s;
}

.new-chat-btn:hover {
  background: #f3f4f6;
}

.history-list {
  flex: 1;
  overflow-y: auto;
  margin-top: 14px;
}

.history-empty {
  font-size: 13px;
  color: #9ca3af;
  text-align: center;
  padding: 24px 0;
}

.history-group {
  margin-bottom: 16px;
}

.history-group-title {
  font-size: 12px;
  color: #9ca3af;
  padding: 0 8px 6px;
}

.history-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
  color: #4b5563;
  font-size: 13px;
}

.history-item:hover,
.history-item.active {
  background: #e5e7eb;
  color: #111827;
}

.history-item-text {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-item-actions {
  display: flex;
  gap: 6px;
  opacity: 0;
  transition: opacity 0.15s;
}

.history-item:hover .history-item-actions {
  opacity: 1;
}

.history-item-actions .el-icon {
  font-size: 14px;
  color: #6b7280;
  padding: 2px;
  border-radius: 4px;
}

.history-item-actions .el-icon:hover {
  color: #111827;
  background: #d1d5db;
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
  min-width: 0;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  border-bottom: 1px solid #f0f2f6;
}

.chat-header h2 {
  font-size: 18px;
  font-weight: 700;
  color: #1a1d21;
}

.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

.welcome-screen {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  color: #1a1d21;
}

.welcome-icon {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  background: #eff6ff;
  color: #2563eb;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  margin-bottom: 20px;
}

.welcome-title {
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 8px;
}

.welcome-subtitle {
  font-size: 14px;
  color: #6b7280;
}

.chat-row {
  display: flex;
  gap: 12px;
  max-width: 800px;
  margin: 0 auto 20px;
}

.chat-row.user {
  flex-direction: row-reverse;
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 16px;
}

.avatar.user {
  background: #2563eb;
  color: #fff;
}

.avatar.assistant {
  background: #f0f4fa;
  color: #409eff;
}

.bubble {
  background: #f4f7fb;
  border-radius: 14px;
  padding: 12px 16px;
  line-height: 1.7;
  font-size: 14px;
  color: #303133;
  word-break: break-word;
}

.chat-row.user .bubble {
  background: #2563eb;
  color: #fff;
}

.typing {
  color: #909399;
}

.chat-input-area {
  padding: 16px 24px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.input-box {
  width: 100%;
  max-width: 760px;
  display: flex;
  align-items: flex-end;
  gap: 10px;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 20px;
  padding: 10px 12px 10px 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.input-box :deep(.el-textarea__inner) {
  background: transparent;
  border: none;
  box-shadow: none;
  resize: none;
  font-size: 14px;
  color: #1a1d21;
}

.input-box :deep(.el-textarea__inner:focus) {
  outline: none;
}

.send-btn {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: none;
  background: #2563eb;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.18s;
  flex-shrink: 0;
}

.send-btn:hover:not(:disabled) {
  background: #1d4ed8;
}

.send-btn:disabled {
  background: #d1d5db;
  cursor: not-allowed;
}

.input-tip {
  font-size: 12px;
  color: #9ca3af;
}
</style>

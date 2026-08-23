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

/**
 * 轻量 Markdown 渲染：先整体转义防 XSS，再转换常见标记
 * （加粗 / 行内代码 / 标题 / 无序列表），其余保持原文。
 * 代码块（``` ... ```）会保留原样，避免块内 ** 等被误解析为加粗。
 */
const renderMarkdown = (text: string): string => {
  const raw = text || ''
  // 1) 提取并占位代码块，避免块内标记被后续正则误处理
  const codeBlocks: string[] = []
  let body = raw.replace(/```[\s\S]*?```/g, (match) => {
    codeBlocks.push(match)
    return `\u0000CODE_BLOCK_${codeBlocks.length - 1}\u0000`
  })
  // 2) 对非代码部分内容做 HTML 转义和 Markdown 轻量转换
  let html = escapeHtml(body)
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
  html = html
    .split('\n')
    .map((line) => {
      let l = line
      l = l.replace(/^#{1,6}\s+(.+)$/, '<span class="md-heading">$1</span>')
      l = l.replace(/^\s*[-*+]\s+(.+)$/, '<span class="md-list-item">• $1</span>')
      l = l.replace(/^(\d+)[.、．]\s+(.+)$/, '<span class="md-list-item">$1. $2</span>')
      return l
    })
    .join('\n')
  // 3) 恢复代码块：去除外围 ``` 并整体转义为 <pre><code>
  html = html.replace(/\u0000CODE_BLOCK_(\d+)\u0000/g, (_, idx) => {
    const block = codeBlocks[Number(idx)]
    const content = block
      .replace(/^```\s*\w*\n?/, '')
      .replace(/\n?```$/, '')
    return `<pre class="md-code-block"><code>${escapeHtml(content)}</code></pre>`
  })
  return html.replace(/\n/g, '<br/>')
}

const messages = ref<MessageItem[]>([])
const input = ref('')
const sending = ref(false)
const scrollRef = ref<HTMLDivElement>()
const records = ref<AskRecord[]>([])
const recordsLoading = ref(false)
const currentRecordId = ref<string | null>(null)
const currentConversationId = ref<string | null>(null)
let currentAbortController: AbortController | null = null
let currentTimeoutId: ReturnType<typeof setTimeout> | null = null
/** 请求代际：新对话/新请求会自增，旧请求的收尾逻辑据此判断自己是否已被取代 */
let requestSeq = 0

const isEmptyChat = computed(() => messages.value.length === 0)

/** 回答中出现 AI 引擎不可用提示时，展示"去配置 API Key"引导条 */
const showAiConfigHint = computed(() =>
  messages.value.some(
    (m) =>
      m.role === 'assistant' &&
      !m.loading &&
      /暂时无法调用外部模型|余额不足|额度已用尽|API Key/.test(m.content)
  )
)

interface ConversationGroup {
  conversationId: string
  label: string
  time: string
  items: AskRecord[]
}

/** 历史记录按会话分组（同会话多轮合并）；无会话 ID 的旧记录按 30 分钟时间窗口聚类成对话 */
const conversationGroups = computed<ConversationGroup[]>(() => {
  const groups: ConversationGroup[] = []
  const indexByConv = new Map<string, ConversationGroup>()
  const today = new Date().setHours(0, 0, 0, 0)
  const sorted = [...records.value].sort(
    (a, b) => new Date(a.createdAt || 0).getTime() - new Date(b.createdAt || 0).getTime()
  )
  let lastTime = 0
  for (const record of sorted) {
    const time = new Date(record.createdAt || 0).getTime()
    let convId = record.conversationId
    if (convId) {
      // 同一会话的已有分组：并入该组（多轮对话）
      const existing = indexByConv.get(convId)
      if (existing) {
        existing.items.push(record)
        lastTime = time
        continue
      }
    } else {
      const last = groups[groups.length - 1]
      // 相邻（≤30 分钟）的旧记录并入同一对话组
      if (last && last.conversationId.startsWith('merged-') && time - lastTime <= 30 * 60 * 1000 && time >= lastTime) {
        last.items.push(record)
        lastTime = time
        continue
      }
      convId = `merged-${groups.length}`
    }
    const group: ConversationGroup = { conversationId: convId, label: '', time: '', items: [record] }
    groups.push(group)
    indexByConv.set(convId, group)
    lastTime = time
  }
  groups.forEach((group) => {
    group.items.sort(
      (a, b) => new Date(a.createdAt || 0).getTime() - new Date(b.createdAt || 0).getTime()
    )
    const first = group.items[0]
    group.label = first?.question?.slice(0, 24) || '新对话'
    group.time = formatTime(first?.createdAt, today)
  })
  groups.sort(
    (a, b) =>
      new Date(b.items[b.items.length - 1]?.createdAt || 0).getTime() -
      new Date(a.items[a.items.length - 1]?.createdAt || 0).getTime()
  )
  return groups
})

const formatTime = (createdAt: string | undefined, today: number) => {
  if (!createdAt) return ''
  const t = new Date(createdAt)
  const d = t.setHours(0, 0, 0, 0)
  if (d === today) return t.toTimeString().slice(0, 5)
  if (d === today - 86400000) return '昨天'
  return `${t.getMonth() + 1}/${t.getDate()}`
}

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
  // 取消在飞请求并推进代际，避免旧流的收尾逻辑干扰新对话
  cancelCurrentRequest()
  requestSeq++
  currentRecordId.value = null
  currentConversationId.value = null
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

/** 加载某会话的全部记录（多轮） */
const loadConversation = (conversationId: string) => {
  cancelCurrentRequest()
  const group = conversationGroups.value.find((g) => g.conversationId === conversationId)
  if (!group) return
  const list: MessageItem[] = []
  group.items.forEach((record) => {
    list.push({ role: 'user', content: record.question })
    list.push({ role: 'assistant', content: record.answer })
  })
  currentRecordId.value = group.items[group.items.length - 1]?.id || null
  currentConversationId.value = conversationId.startsWith('merged-') ? null : conversationId
  messages.value = list
  scrollToBottom()
}

const loadRecord = (record: AskRecord) => {
  if (record.conversationId) {
    loadConversation(record.conversationId)
  } else {
    currentRecordId.value = record.id
    currentConversationId.value = null
    messages.value = [
      { role: 'user', content: record.question },
      { role: 'assistant', content: record.answer }
    ]
    scrollToBottom()
  }
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
    // 延迟释放对象 URL：Firefox/Safari 下立即 revoke 会取消尚未开始的下载
    setTimeout(() => URL.revokeObjectURL(url), 10000)
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

/** 删除整个会话（该会话全部记录） */
const deleteConversation = async (conversationId: string) => {
  try {
    await ElMessageBox.confirm('确定删除整个对话吗？该会话的所有问答记录都会删除。', '删除对话', {
      type: 'warning'
    })
  } catch {
    return
  }
  const group = conversationGroups.value.find((g) => g.conversationId === conversationId)
  const ids = group ? group.items.map((r) => r.id) : []
  for (const id of ids) {
    try {
      await deleteAsk(id)
    } catch {
      // 单条删除失败继续
    }
  }
  ElMessage.success('对话已删除')
  if (currentConversationId.value === conversationId) {
    clearChat()
  }
  await loadRecords()
}

const send = async () => {
  const question = input.value.trim()
  if (!question || sending.value) return
  // 新请求开始前取消残留的在飞请求（连发/未清空场景）
  cancelCurrentRequest()
  const reqId = ++requestSeq
  currentRecordId.value = null
  input.value = ''
  messages.value.push({ role: 'user', content: question })
  const assistant: MessageItem = { role: 'assistant', content: '', loading: true }
  messages.value.push(assistant)
  sending.value = true
  // 捕获局部引用：超时/收尾只作用于本次请求的控制器，不会误杀后续请求
  const controller = new AbortController()
  currentAbortController = controller
  const timeoutId = setTimeout(() => controller.abort(), 180000)
  currentTimeoutId = timeoutId
  // 打字机效果：chunk 先攒入缓冲，定时增量渲染（避免一次 read 的大量内容被 Vue 批量一次性显示）
  let typingBuffer = ''
  let typingTimer: ReturnType<typeof setInterval> | null = null
  const flushTyping = () => {
    if (typingBuffer) {
      assistant.content += typingBuffer
      typingBuffer = ''
      if (reqId === requestSeq) {
        scrollToBottom()
      }
    }
  }
  try {
    await submitAskStream(
      question,
      (chunk) => {
        typingBuffer += chunk
        assistant.loading = false
        if (!typingTimer) {
          typingTimer = setInterval(() => {
            flushTyping()
          }, 40)
        }
      },
      controller.signal,
      currentConversationId.value || undefined
    )
    if (typingTimer) clearInterval(typingTimer)
    flushTyping()
    if (reqId !== requestSeq) return
    if (assistant.content.trim().length === 0) {
      assistant.content = '（未获取到回答，请检查 AI 引擎配置后重试）'
    }
    // 若后端创建了会话 ID（首次提问时为空），用它记录当前会话
    await loadRecords()
    if (reqId !== requestSeq) return
    if (!currentConversationId.value) {
      const latest = records.value
        .filter((r) => r.question === question)
        .sort((a, b) => new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime())[0]
      if (latest?.conversationId) {
        currentConversationId.value = latest.conversationId
      }
    }
  } catch (e: any) {
    if (reqId !== requestSeq) return
    if (e?.name === 'AbortError') {
      assistant.content += '\n（请求已取消或超时）'
    } else {
      assistant.content = `请求失败：${e?.message || '未知错误'}`
      ElMessage.error('问答请求失败，请确认 AI 引擎可用')
    }
  } finally {
    if (typingTimer) clearInterval(typingTimer)
    typingTimer = null
    flushTyping()
    assistant.loading = false
    if (reqId === requestSeq) {
      sending.value = false
    }
    // 只清理属于本次请求的控制器与定时器，防止误杀后续请求
    if (currentTimeoutId === timeoutId) {
      clearTimeout(timeoutId)
      currentTimeoutId = null
    }
    if (currentAbortController === controller) {
      currentAbortController = null
    }
    scrollToBottom()
  }
}

onMounted(() => {
  loadRecords()
})

onBeforeUnmount(() => {
  cancelCurrentRequest()
  requestSeq++
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
          v-for="group in conversationGroups"
          :key="group.conversationId"
          class="history-group"
        >
          <div class="history-group-title">
            <span class="history-group-label">{{ group.label }}</span>
            <span class="history-group-time">{{ group.time }}</span>
            <span v-if="group.items.length > 1" class="history-group-count">{{ group.items.length }} 轮</span>
          </div>
          <div
            class="history-item"
            :class="{ active: currentRecordId === group.items[group.items.length - 1]?.id && currentConversationId === group.conversationId }"
            @click="loadConversation(group.conversationId)"
          >
            <span class="history-item-text">{{ group.items[group.items.length - 1]?.question }}</span>
            <div class="history-item-actions" @click.stop>
              <el-icon title="导出" @click="exportRecord(group.items[group.items.length - 1])"><Download /></el-icon>
              <el-icon
                title="删除会话"
                @click="deleteConversation(group.conversationId)"
              ><Delete /></el-icon>
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
              <div v-else class="bubble-text" v-html="renderMarkdown(msg.content)"></div>
            </div>
          </div>
        </template>
      </div>

      <div class="chat-input-area">
        <el-alert
          v-if="showAiConfigHint"
          type="warning"
          :closable="false"
          class="ai-config-hint"
        >
          <template #title>
            <span>AI 引擎暂时不可用（平台默认额度可能已用尽）。</span>
            <el-button link type="primary" @click="$router.push('/settings/ai')">去配置自己的 API Key</el-button>
          </template>
        </el-alert>
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

/* Markdown 渲染样式 */
.bubble-text :deep(.md-heading) {
  display: block;
  font-weight: 700;
  font-size: 15px;
  margin: 8px 0 4px;
  color: #1f2937;
}

.bubble-text :deep(.md-list-item) {
  display: block;
  padding-left: 2px;
}

.bubble-text :deep(code) {
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  padding: 1px 5px;
  font-size: 12.5px;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  color: #c2410c;
}

.bubble-text :deep(strong) {
  font-weight: 700;
}

.chat-row.user .bubble :deep(.md-heading) {
  color: #fff;
}

.chat-row.user .bubble :deep(code) {
  background: rgba(255, 255, 255, 0.18);
  border-color: rgba(255, 255, 255, 0.35);
  color: #fff;
}

.chat-input-area {
  padding: 16px 24px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.ai-config-hint {
  width: 100%;
  max-width: 760px;
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

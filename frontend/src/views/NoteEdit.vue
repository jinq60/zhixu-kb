<script setup lang="ts">
import { computed, onActivated, onBeforeUnmount, onDeactivated, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getNote,
  getNoteHistory,
  getNoteStructure,
  restoreNoteHistory,
  triggerOCR,
  updateNote,
  type Note,
  type NoteHistoryItem,
  type NoteSectionItem,
  type NoteStructureResponse,
  type OutlineNode,
  type OCREngine
} from '../api/note'
import { listCategories, type Category } from '../api/category'
import { deleteFile, fetchFileBlob, type UploadResponse } from '../api/file'
import { buildNoteGraph, deleteNoteGraph, getNoteGraph, getGraphTaskStatus, type GraphData } from '../api/graph'
import ImageUpload from '../components/ImageUpload.vue'
import KnowledgeGraph from '../components/KnowledgeGraph.vue'
import MermaidPreview from '../components/MermaidPreview.vue'
import RichEditor from '../components/RichEditor.vue'
import { ArrowLeft, Document, Expand, Fold, Plus, Refresh } from '@element-plus/icons-vue'

// 显式组件名：配合 App.vue 的 KeepAlive include 缓存（避免重复初始化 wangeditor 等重组件）
defineOptions({ name: 'NoteEdit' })

interface NoteFile {
  id?: number
  originalName?: string
  storedName?: string
  fileSize?: number
  mimeType?: string
  uploadTime?: string
}

interface StructureTreeNode {
  key: string
  index: number
  level: number
  title: string
  children?: StructureTreeNode[]
}

const route = useRoute()
const router = useRouter()
// note ID 为雪花 ID（可能超出 Number 安全整数范围），保持字符串形式；新建时为空
const id = computed(() => (route.params.id ? String(route.params.id) : ''))

const loading = ref(false)
const saving = ref(false)
const ocrLoading = ref(false)
const structureLoading = ref(false)
const historyLoading = ref(false)

const categories = ref<Category[]>([])
const files = ref<NoteFile[]>([])
const deletingFileId = ref<number | null>(null)
const ocrQueueFileIds = ref<number[]>([])

const previewVisible = ref(false)
const previewLoading = ref(false)
const previewName = ref('图片预览')
const previewUrl = ref('')

const activeTab = ref('mindmap')
const graphData = ref<GraphData | null>(null)
const graphLoading = ref(false)
const graphBuilding = ref(false)
let graphPollTimer: ReturnType<typeof setInterval> | null = null

const stopGraphPolling = () => {
  if (graphPollTimer) {
    clearInterval(graphPollTimer)
    graphPollTimer = null
  }
}

const pollGraphTask = (taskId: string) => {
  stopGraphPolling()
  graphPollTimer = setInterval(async () => {
    try {
      const status = await getGraphTaskStatus(taskId)
      if (!status.running) {
        stopGraphPolling()
        graphBuilding.value = false
        if (status.error) {
          ElMessage.error(status.error)
        } else {
          ElMessage.success('图谱构建完成')
          await loadGraph()
        }
      }
    } catch (e: any) {
      stopGraphPolling()
      graphBuilding.value = false
      ElMessage.error(e?.response?.data?.message || '查询构建状态失败')
    }
  }, 3000)
}
const structure = ref<NoteStructureResponse>({
  outline: [],
  sections: [],
  mermaid: ''
})
const editableSections = ref<NoteSectionItem[]>([])
const selectedStructureIndex = ref(0)
const historyItems = ref<NoteHistoryItem[]>([])

const ocrEngine = ref<OCREngine>('auto')
const ocrEngineOptions: Array<{ label: string; value: OCREngine }> = [
  { label: '自动', value: 'auto' },
  { label: 'PaddleOCR', value: 'paddle' },
  { label: 'DeepSeek-OCR-2', value: 'deepseek' }
]

const statusOptions = [
  { label: '草稿', value: 0 },
  { label: '已发布', value: 1 }
]

const note = reactive<Note>({
  id: id.value,
  title: '',
  content: '',
  categoryId: undefined,
  summary: '',
  keywords: '',
  status: 0
})

const structureTreeProps = {
  label: 'title',
  children: 'children'
}

const validCategories = computed(() =>
  categories.value.filter((c): c is Category & { id: number } => !!c && c.id != null)
)

const sortFiles = (list: NoteFile[]) =>
  [...list].sort((left, right) => {
    const leftTime = left?.uploadTime ? new Date(left.uploadTime).getTime() : NaN
    const rightTime = right?.uploadTime ? new Date(right.uploadTime).getTime() : NaN
    if (Number.isFinite(leftTime) && Number.isFinite(rightTime) && leftTime !== rightTime) {
      return leftTime - rightTime
    }
    return Number(left?.id || 0) - Number(right?.id || 0)
  })

const extractEmbeddedFileIds = (html?: string) => {
  const ids = new Set<number>()
  if (!html) return ids

  const regex = /\/api\/files\/(\d+)\/content/g
  let match: RegExpExecArray | null
  while ((match = regex.exec(html)) !== null) {
    const fileId = Number(match[1])
    if (Number.isFinite(fileId)) {
      ids.add(fileId)
    }
  }

  return ids
}

const embeddedFileIdSet = computed(() => extractEmbeddedFileIds(note.content))

const isImageFile = (file?: NoteFile): boolean =>
  ['image/jpeg', 'image/png', 'image/jpg'].includes(String(file?.mimeType || '').toLowerCase())

const ocrCandidateFiles = computed(() =>
  files.value.filter((f) => {
    // OCR 仅支持图片；文档（pdf/txt/docx/md）走上传后的自动解析/清洗流程
    if (!isImageFile(f)) return false
    const fileId = Number(f?.id)
    if (!Number.isFinite(fileId)) return true
    return !embeddedFileIdSet.value.has(fileId)
  })
)

const hasImageFiles = computed(() => files.value.some((f) => isImageFile(f)))

const isEmbeddedFile = (file: NoteFile) => {
  const fileId = Number(file?.id)
  return Number.isFinite(fileId) && embeddedFileIdSet.value.has(fileId)
}

const isQueuedOcrFile = (file: NoteFile) => {
  const fileId = Number(file?.id)
  return Number.isFinite(fileId) && ocrQueueFileIds.value.includes(fileId)
}

const getOcrQueuePosition = (file: NoteFile) => {
  const fileId = Number(file?.id)
  return Number.isFinite(fileId) ? ocrQueueFileIds.value.indexOf(fileId) + 1 : 0
}

const normalizeStructure = (payload?: Partial<NoteStructureResponse>) => ({
  outline: payload?.outline || [],
  sections: payload?.sections || [],
  mermaid: payload?.mermaid || '',
  updateTime: payload?.updateTime
})

const cloneSections = (sections?: NoteSectionItem[]) =>
  (sections || []).map((section, index) => ({
    ...section,
    sortOrder: section?.sortOrder ?? index
  }))

const normalizeEditableSections = (sections: NoteSectionItem[]) => {
  const next = cloneSections(sections)
  let previousLevel = 1

  return next.map((section, index) => {
    let level = Math.min(Math.max(section.level || 1, 1), 4)
    if (index === 0) {
      level = 1
    } else {
      level = Math.min(level, previousLevel + 1)
    }
    previousLevel = level

    return {
      ...section,
      title: section.title?.trim() || `未命名章节 ${index + 1}`,
      content: section.content || '',
      level,
      sortOrder: index
    }
  })
}

const buildOutlineFromSections = (sections: NoteSectionItem[]): OutlineNode[] => {
  const normalized = normalizeEditableSections(sections)
  const roots: OutlineNode[] = []
  const stack: Array<{ level: number; node: OutlineNode }> = []

  for (const section of normalized) {
    const level = Math.max(section.level || 1, 1)
    const node: OutlineNode = {
      title: section.title,
      content: section.content,
      children: []
    }

    while (stack.length && stack[stack.length - 1].level >= level) {
      stack.pop()
    }

    if (!stack.length) {
      roots.push(node)
    } else {
      stack[stack.length - 1].node.children = stack[stack.length - 1].node.children || []
      stack[stack.length - 1].node.children!.push(node)
    }

    stack.push({ level, node })
  }

  return roots
}

const buildStructureTree = (sections: NoteSectionItem[]): StructureTreeNode[] => {
  const roots: StructureTreeNode[] = []
  const stack: Array<{ level: number; node: StructureTreeNode }> = []

  sections.forEach((section, index) => {
    const level = Math.max(section.level || 1, 1)
    const node: StructureTreeNode = {
      key: `${section.id}-${index}`,
      index,
      level,
      title: section.title || `未命名章节 ${index + 1}`,
      children: []
    }

    while (stack.length && stack[stack.length - 1].level >= level) {
      stack.pop()
    }

    if (!stack.length) {
      roots.push(node)
    } else {
      stack[stack.length - 1].node.children = stack[stack.length - 1].node.children || []
      stack[stack.length - 1].node.children!.push(node)
    }

    stack.push({ level, node })
  })

  return roots
}

const editableSectionTree = computed(() => buildStructureTree(editableSections.value))
const selectedStructureKey = computed(() => {
  const current = editableSections.value[selectedStructureIndex.value]
  return current ? `${current.id}-${selectedStructureIndex.value}` : undefined
})
/** 目录树全部展开/默认只展开前两层，避免深树视觉噪音 */
const structureExpanded = ref(false)
const defaultExpandedStructureKeys = computed(() => {
  if (structureExpanded.value) {
    return editableSectionTree.value.map((node) => node.key)
  }
  return collectExpandedKeys(editableSectionTree.value, 2)
})
const collectExpandedKeys = (nodes: StructureTreeNode[], maxDepth: number): string[] => {
  const keys: string[] = []
  const walk = (list: StructureTreeNode[], depth: number) => {
    for (const node of list) {
      if (node.children?.length && depth < maxDepth) {
        keys.push(node.key)
        walk(node.children, depth + 1)
      }
    }
  }
  walk(nodes, 1)
  return keys
}
const toggleStructureExpand = () => {
  structureExpanded.value = !structureExpanded.value
}

const createEditableSection = (patch?: Partial<NoteSectionItem>): NoteSectionItem => ({
  id: Date.now() + Math.floor(Math.random() * 1000),
  noteId: id.value,
  parentId: patch?.parentId,
  title: patch?.title || '',
  content: patch?.content || '',
  level: patch?.level ?? 1,
  sortOrder: patch?.sortOrder ?? editableSections.value.length
})

const updateEditableSection = (index: number, patch: Partial<NoteSectionItem>) => {
  if (index < 0 || index >= editableSections.value.length) return
  const next = cloneSections(editableSections.value)
  next[index] = { ...next[index], ...patch }
  editableSections.value = normalizeEditableSections(next)
}

const getEditableSubtreeEnd = (sections: NoteSectionItem[], index: number) => {
  const currentLevel = sections[index]?.level || 1
  let end = index + 1
  while (end < sections.length && (sections[end].level || 1) > currentLevel) {
    end += 1
  }
  return end
}

const addRootStructureSection = () => {
  editableSections.value = normalizeEditableSections([
    ...editableSections.value,
    createEditableSection({ level: 1 })
  ])
  selectedStructureIndex.value = editableSections.value.length - 1
}

const addSiblingStructureSection = (index: number) => {
  const current = editableSections.value[index]
  if (!current) return
  const next = cloneSections(editableSections.value)
  const insertAt = getEditableSubtreeEnd(next, index)
  next.splice(insertAt, 0, createEditableSection({ level: current.level, noteId: current.noteId }))
  editableSections.value = normalizeEditableSections(next)
  selectedStructureIndex.value = insertAt
}

const addChildStructureSection = (index: number) => {
  const current = editableSections.value[index]
  if (!current) return
  const next = cloneSections(editableSections.value)
  const insertAt = index + 1
  next.splice(
    insertAt,
    0,
    createEditableSection({
      level: Math.min((current.level || 1) + 1, 4),
      noteId: current.noteId
    })
  )
  editableSections.value = normalizeEditableSections(next)
  selectedStructureIndex.value = insertAt
}

const removeStructureSection = (index: number) => {
  if (index < 0 || index >= editableSections.value.length) return
  const next = cloneSections(editableSections.value)
  const end = getEditableSubtreeEnd(next, index)
  next.splice(index, end - index)
  editableSections.value = normalizeEditableSections(next)
  selectedStructureIndex.value = Math.max(0, Math.min(index, editableSections.value.length - 1))
}

const onStructureNodeSelect = (node: StructureTreeNode) => {
  selectedStructureIndex.value = node.index
}

watch(
  ocrCandidateFiles,
  (list) => {
    if (!list.length) {
      ocrQueueFileIds.value = []
      return
    }

    const candidateIds = list
      .map((item) => Number(item?.id))
      .filter((fileId) => Number.isFinite(fileId))
    if (!ocrQueueFileIds.value.length) {
      ocrQueueFileIds.value = [...candidateIds]
      return
    }
    ocrQueueFileIds.value = ocrQueueFileIds.value.filter((fileId) => candidateIds.includes(fileId))
  },
  { immediate: true }
)

watch(
  editableSections,
  (list) => {
    if (!list.length) {
      selectedStructureIndex.value = 0
      return
    }
    if (selectedStructureIndex.value >= list.length) {
      selectedStructureIndex.value = list.length - 1
    }
  },
  { deep: true }
)

const load = async () => {
  loading.value = true
  try {
    const detail = await getNote(id.value)
    Object.assign(note, { status: 0, ...detail.note })
    files.value = sortFiles((detail.files || []).filter(Boolean))
  } catch (e: any) {
    const status = e?.response?.status
    const message = e?.response?.data?.message || '加载笔记失败'
    if (status === 401) {
      // 401 由 axios 拦截器统一处理登出并跳转首页，这里不再二次导航避免路由冲突
      ElMessage.warning('登录已过期，请重新登录')
      return
    }
    ElMessage.error(message)
    router.push('/notes')
  } finally {
    loading.value = false
  }
}

const fetchCategories = async () => {
  try {
    const list = await listCategories()
    categories.value = (list || []).filter(Boolean)
  } catch {
    categories.value = []
  }
}

const fetchStructure = async () => {
  structureLoading.value = true
  try {
    structure.value = normalizeStructure(await getNoteStructure(id.value))
    editableSections.value = cloneSections(structure.value.sections)
    selectedStructureIndex.value = 0
  } catch (e: any) {
    structure.value = normalizeStructure()
    editableSections.value = []
    ElMessage.error(e?.response?.data?.message || '加载结构化整理结果失败')
  } finally {
    structureLoading.value = false
  }
}

const fetchHistory = async () => {
  historyLoading.value = true
  try {
    historyItems.value = await getNoteHistory(id.value)
  } catch (e: any) {
    historyItems.value = []
    ElMessage.error(e?.response?.data?.message || '加载历史记录失败')
  } finally {
    historyLoading.value = false
  }
}

// KeepAlive 组件首次挂载时 onMounted 与 onActivated 都会触发（顺序：mounted → activated），
// 用标记跳过首次激活（onMounted 已完成加载），避免目录/图谱/历史被重复请求
let pendingFirstActivate = true

onMounted(() => {
  // 非法 id（如 /notes/abc 或空）直接回列表，避免无效请求
  if (!id.value || !/^\d+$/.test(id.value)) {
    router.replace('/notes')
    return
  }
  fetchCategories()
  load()
  fetchStructure()
  fetchHistory()
  loadGraph()
})

onActivated(() => {
  // 首次挂载后的激活直接跳过；此后 KeepAlive 缓存命中（返回同一笔记）时
  // 刷新轻量数据，保证目录/图谱/历史最新；
  // 不重新拉取正文，避免 wangeditor 大文档 setHtml 卡顿
  if (pendingFirstActivate) {
    pendingFirstActivate = false
    return
  }
  if (!id.value || !/^\d+$/.test(id.value)) {
    return
  }
  fetchStructure()
  fetchHistory()
  loadGraph()
})

// KeepAlive 下离开编辑页只触发 deactivated 不触发 unmount：
// 必须暂停轮询，否则切到其他页面后仍每 3s 轮询并弹出"构建完成"提示
onDeactivated(() => {
  stopGraphPolling()
})

watch(
  id,
  (newId) => {
    // 路由切出编辑页时 params.id 为空（KeepAlive 缓存下 watch 仍触发），不能发起空 id 请求
    if (!newId || !/^\d+$/.test(newId)) {
      return
    }
    note.id = newId
    fetchCategories()
    load()
    fetchStructure()
    fetchHistory()
    loadGraph()
  }
)

const buildNotePayload = (): Note => ({
  ...note,
  // 清除分类时 categoryId 为 undefined，会被 JSON 序列化丢弃导致后端不更新；
  // 显式转为 null，保证"未分类"能真正写回后端
  categoryId: note.categoryId ?? null,
  ...(editableSections.value.length || structure.value.outline.length || structure.value.sections.length
    ? { outline: buildOutlineFromSections(editableSections.value) }
    : {})
})

const onSave = async () => {
  saving.value = true
  try {
    await updateNote(id.value, buildNotePayload())
    await Promise.all([fetchStructure(), fetchHistory()])
    ElMessage.success('保存成功')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

const hasMeaningfulContent = (html?: string) => {
  if (!html) return false
  const text = html
    .replace(/<[^>]*>/g, '')
    .replace(/&nbsp;/g, ' ')
    .trim()
  return text.length > 0
}

const looksLikeHtml = (value?: string) => /<[^>]+>/.test(value || '')

const escapeHtml = (raw: string) =>
  raw
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')

const getReferencedFileId = (src?: string | null): number | null => {
  if (!src) return null

  try {
    const url = new URL(src, window.location.origin)
    const matched = url.pathname.match(/^\/api\/files\/(\d+)\/content$/)
    return matched ? Number(matched[1]) : null
  } catch {
    const matched = src.match(/^\/api\/files\/(\d+)\/content(?:\?.*)?$/)
    return matched ? Number(matched[1]) : null
  }
}

const removeEmbeddedFileFromContent = (html?: string, fileId?: number) => {
  if (!html || fileId == null || typeof document === 'undefined') return html || ''

  const root = document.createElement('div')
  root.innerHTML = html

  root.querySelectorAll('img[src]').forEach((img) => {
    if (getReferencedFileId(img.getAttribute('src')) !== fileId) return

    const parent = img.parentElement
    img.remove()

    if (
      parent &&
      parent.tagName === 'P' &&
      !parent.textContent?.trim() &&
      parent.querySelectorAll('img,video,iframe').length === 0
    ) {
      parent.remove()
    }
  })

  return root.innerHTML
}

const ocrTextToHtml = (raw: string) => {
  const lines = raw
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
  if (!lines.length) return ''
  return lines.map((line) => `<p>${escapeHtml(line)}</p>`).join('')
}

const saveCurrentNoteSilently = async () => {
  await updateNote(id.value, buildNotePayload())
}

const addToOcrQueue = (file: NoteFile) => {
  // OCR 队列仅支持图片；文档自动走解析/清洗流程
  if (!isImageFile(file)) {
    ElMessage.warning('仅支持图片加入 OCR 队列，文档会在上传后自动解析并清洗')
    return
  }
  if (isEmbeddedFile(file)) {
    ElMessage.warning('已插入正文的图片不能加入 OCR 队列')
    return
  }
  const fileId = Number(file?.id)
  if (!Number.isFinite(fileId) || ocrQueueFileIds.value.includes(fileId)) return
  ocrQueueFileIds.value = [...ocrQueueFileIds.value, fileId]
}

const removeFromOcrQueue = (file: NoteFile) => {
  const fileId = Number(file?.id)
  if (!Number.isFinite(fileId)) return
  ocrQueueFileIds.value = ocrQueueFileIds.value.filter((item) => item !== fileId)
}

const moveOcrQueueItem = (file: NoteFile, direction: 'up' | 'down') => {
  const fileId = Number(file?.id)
  if (!Number.isFinite(fileId)) return

  const currentIndex = ocrQueueFileIds.value.indexOf(fileId)
  if (currentIndex < 0) return

  const targetIndex = direction === 'up' ? currentIndex - 1 : currentIndex + 1
  if (targetIndex < 0 || targetIndex >= ocrQueueFileIds.value.length) return

  const next = [...ocrQueueFileIds.value]
  ;[next[currentIndex], next[targetIndex]] = [next[targetIndex], next[currentIndex]]
  ocrQueueFileIds.value = next
}

const onOCR = async () => {
  if (!ocrCandidateFiles.value.length) {
    ElMessage.warning('右侧没有可用于 OCR 的图片')
    return
  }

  const queue = ocrQueueFileIds.value.filter((fileId) =>
    ocrCandidateFiles.value.some((item) => Number(item?.id) === fileId)
  )
  if (!queue.length) {
    ElMessage.warning('请先将图片加入 OCR 队列')
    return
  }

  ocrLoading.value = true
  try {
    // 后端单次 OCR 上限 10 张，队列超过时自动分批串行处理
    const batchSize = 10
    const texts: string[] = []
    for (let i = 0; i < queue.length; i += batchSize) {
      const batch = queue.slice(i, i + batchSize)
      const text = await triggerOCR(id.value, ocrEngine.value, batch)
      if (text && text.trim()) texts.push(text)
    }
    const text = texts.join('\n\n')
    const html = ocrTextToHtml(text || '')

    if (!html) {
      ElMessage.warning('OCR 未识别出文本')
      return
    }

    note.content = !hasMeaningfulContent(note.content) ? html : `${note.content}${html}`

    try {
      await saveCurrentNoteSilently()
      await fetchHistory()
      ElMessage.success(`OCR 识别完成，已按队列顺序处理 ${queue.length} 张图片并自动保存`)
    } catch (saveError: any) {
      ElMessage.warning(saveError?.response?.data?.message || 'OCR 已完成，但自动保存失败，请手动保存')
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || 'OCR 识别失败')
  } finally {
    ocrLoading.value = false
  }
}

const loadGraph = async () => {
  graphLoading.value = true
  try {
    graphData.value = await getNoteGraph(id.value)
  } catch {
    graphData.value = null
  } finally {
    graphLoading.value = false
  }
}

const onBuildGraph = async () => {
  graphBuilding.value = true
  try {
    await saveCurrentNoteSilently()
    const result = await buildNoteGraph(id.value)
    ElMessage.success('图谱构建已提交，可在任务中心查看进度')
    pollGraphTask(result.taskId)
  } catch (e: any) {
    graphBuilding.value = false
    ElMessage.error(e?.response?.data?.message || '图谱构建提交失败')
  }
}

const onDeleteGraph = async () => {
  try {
    await ElMessageBox.confirm('确认删除这篇笔记的知识图谱吗？', '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteNoteGraph(id.value)
    graphData.value = null
    ElMessage.success('图谱已删除')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '图谱删除失败')
  }
}

const onRestoreHistory = async (item: NoteHistoryItem) => {
  try {
    await ElMessageBox.confirm(
      `确认恢复到「${item.operationDesc}」这条历史记录吗？当前未保存内容会被覆盖。`,
      '恢复历史版本',
      {
        type: 'warning',
        confirmButtonText: '恢复',
        cancelButtonText: '取消'
      }
    )
  } catch {
    return
  }

  try {
    await restoreNoteHistory(id.value, item.id)
    await Promise.all([fetchCategories(), load(), fetchStructure(), fetchHistory()])
    ElMessage.success('已恢复历史版本')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '恢复历史版本失败')
  }
}

const onUploaded = async (result: UploadResponse) => {
  const file = result?.file
  if (!file) return
  files.value = sortFiles([...files.value, file])
  const fileId = Number(file?.id)
  const isImage = ['image/jpeg', 'image/png', 'image/jpg'].includes(String(file?.mimeType || ''))
  // 图片进入 OCR 识别队列
  if (isImage && Number.isFinite(fileId) && !ocrQueueFileIds.value.includes(fileId)) {
    ocrQueueFileIds.value = [...ocrQueueFileIds.value, fileId]
  }
  // 文档：解析/清洗后台异步执行，完成后后端写回正文，无需前端处理大文本（避免卡顿）
}

/** 文档处理任务完成（后端已写回清洗后正文）：刷新笔记内容并强制编辑器重载，确保正文展示 */
const editorRefreshKey = ref(0)
const onDocTaskCompleted = async () => {
  try {
    await load()
    await fetchStructure()
    await fetchHistory()
    // 强制 RichEditor 重新挂载：保证后台写回的正文一定显示（含离开页面期间完成的任务）
    editorRefreshKey.value++
  } catch {
    // 刷新失败不阻塞
  }
}

/**
 * 将清洗后的文本转为可读 HTML（确定性，与后端 NoteNormalizeExecutor 同一套规则）：
 * - "# "→h2、"## "→h3、"###+"→h4
 * - 编号标题：1. / 1.1 / 一、 / 第一章 / （一） → h2-h4（按编号层级推导）
 * - 列表行（-、*、• 开头）→ ul/li；其余空行分段，段内连续行用 <br> 连接
 */
const toStructuredHtml = (text: string) => {
  const lines = text.split('\n').map((line) => line.trim())
  let html = ''
  let paragraph: string[] = []
  let list: string[] = []

  const flushParagraph = () => {
    if (!paragraph.length) return
    html += `<p>${paragraph.join('<br>')}</p>`
    paragraph = []
  }
  const flushList = () => {
    if (!list.length) return
    html += `<ul>${list.map((item) => `<li>${escapeHtml(item)}</li>`).join('')}</ul>`
    list = []
  }

  for (const line of lines) {
    if (!line) {
      flushParagraph()
      flushList()
      continue
    }
    if (line.startsWith('#')) {
      flushParagraph()
      flushList()
      const count = line.match(/^#+/)?.[0].length || 1
      const text = line.replace(/^#+\s*/, '')
      const level = Math.min(Math.max(count, 1), 3)
      html += `<h${level + 1}>${escapeHtml(text)}</h${level + 1}>`
      continue
    }
    const numHeading = line.match(/^(\d{1,2}(?:\.\d{1,2}){0,2})\s*[.、．)）]\s*(.+)/)
    if (numHeading) {
      flushParagraph()
      flushList()
      const segments = numHeading[1].split('.').length
      const level = Math.min(Math.max(segments, 1), 3)
      html += `<h${level + 1}>${escapeHtml(numHeading[2])}</h${level + 1}>`
      continue
    }
    if (/^(第[一二三四五六七八九十百0-9]+[章节篇部部分]\s+.+|（[一二三四五六七八九十]+）\s*.+|[一二三四五六七八九十]+[、.．]\s*.+)/.test(line)) {
      flushParagraph()
      flushList()
      const text = line.replace(/^(第[一二三四五六七八九十百0-9]+[章节篇部部分]|（[一二三四五六七八九十]+）|[一二三四五六七八九十]+[、.．])\s*/, '')
      html += `<h2>${escapeHtml(text)}</h2>`
      continue
    }
    if (/^[-*•·]\s+.+/.test(line)) {
      flushParagraph()
      list.push(line.replace(/^[-*•·]\s+/, ''))
      continue
    }
    flushList()
    paragraph.push(escapeHtml(line))
  }
  flushParagraph()
  flushList()
  return html
}

const onPreviewFile = async (file: NoteFile) => {
  if (!file?.id) {
    ElMessage.warning('文件信息无效')
    return
  }
  // 预览仅支持图片：文档（pdf/docx/txt/md）下载查看，避免生成无效的 img 预览
  if (!isImageFile(file)) {
    ElMessage.info('该文件为文档，不支持在线预览，请下载后查看')
    return
  }

  previewLoading.value = true
  previewName.value = file.originalName || '图片预览'

  try {
    const blob = await fetchFileBlob(file.id)
    if (previewUrl.value) {
      URL.revokeObjectURL(previewUrl.value)
    }
    previewUrl.value = URL.createObjectURL(blob)
    previewVisible.value = true
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '预览失败')
  } finally {
    previewLoading.value = false
  }
}

const onDeleteFile = async (file: NoteFile) => {
  if (!file?.id) {
    files.value = files.value.filter((f) => f !== file)
    return
  }

  const embedded = isEmbeddedFile(file)

  try {
    await ElMessageBox.confirm(
      embedded
        ? `图片「${file.originalName || '未命名文件'}」已插入正文，删除后会同时移除正文中的引用，是否继续？`
        : `确认删除图片「${file.originalName || '未命名文件'}」吗？`,
      '删除确认',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消'
      }
    )
  } catch {
    return
  }

  deletingFileId.value = file.id
  try {
    await deleteFile(file.id)
    files.value = files.value.filter((f) => f?.id !== file.id)

    if (embedded) {
      note.content = removeEmbeddedFileFromContent(note.content, file.id)
      try {
        await saveCurrentNoteSilently()
        await fetchHistory()
        ElMessage.success('删除成功，正文引用已同步移除')
      } catch (saveError: any) {
        ElMessage.warning(saveError?.response?.data?.message || '图片已删除，但正文同步失败，请手动保存')
      }
    } else {
      ElMessage.success('删除成功')
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '删除失败')
  } finally {
    deletingFileId.value = null
  }
}

onBeforeUnmount(() => {
  stopGraphPolling()
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = ''
  }
})
</script>

<template>
  <div class="page" v-loading="loading">
    <div class="page-header">
      <div class="header-top">
        <el-button class="back-btn" text @click="router.push('/notes')">
          <el-icon class="btn-icon"><ArrowLeft /></el-icon>
          <span>工作台</span>
        </el-button>
        <el-input
          v-model="note.title"
          class="title-input"
          placeholder="请输入笔记标题"
          size="large"
        >
          <template #prefix>
            <el-icon class="title-icon"><Document /></el-icon>
          </template>
        </el-input>
        <div class="primary-actions">
          <el-button type="primary" :loading="saving" class="save-btn" @click="onSave">保存</el-button>
        </div>
      </div>

      <div class="header-meta">
        <div class="meta-field">
          <span class="meta-label">分类</span>
          <el-select v-model="note.categoryId" placeholder="选择分类" clearable class="meta-select meta-select--category">
            <el-option
              v-for="(c, idx) in validCategories"
              :key="c.id ?? `cat-${idx}`"
              :label="c.name || '未命名分类'"
              :value="c.id"
            />
          </el-select>
        </div>
        <div class="meta-field">
          <span class="meta-label">状态</span>
          <el-select v-model="note.status" class="meta-select meta-select--status">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </div>
        <div class="meta-field meta-field--grow">
          <span class="meta-label">关键词</span>
          <el-input v-model="note.keywords" placeholder="多个关键词用逗号分隔，用于全文检索与知识图谱" />
        </div>
        <div class="ocr-group">
          <span class="meta-label">OCR 引擎</span>
          <el-select v-model="ocrEngine" class="ocr-engine-select">
            <el-option
              v-for="item in ocrEngineOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
          <el-tooltip content="对尚未嵌入正文的图片执行批量文字识别，识别结果写入笔记正文" placement="bottom">
            <el-button :loading="ocrLoading" :disabled="!ocrCandidateFiles.length" @click="onOCR">
              批量 OCR{{ ocrCandidateFiles.length ? `（${ocrCandidateFiles.length}）` : '' }}
            </el-button>
          </el-tooltip>
        </div>
      </div>
    </div>

    <div class="grid">
      <div class="left">
        <div class="editor-layout">
          <section class="panel structure-side">
            <div class="structure-side-header">
              <div>
                <h4>结构目录</h4>
                <p class="panel-tip">上传文档后系统会自动生成目录；这里支持折叠和手动微调，最后点右上角“保存”。</p>
              </div>
            </div>

            <div class="structure-side-toolbar">
              <el-button size="small" plain :loading="structureLoading" @click="fetchStructure">
                <el-icon class="btn-icon"><Refresh /></el-icon>
                <span>刷新</span>
              </el-button>
              <el-button size="small" plain @click="toggleStructureExpand">
                <el-icon class="btn-icon">
                  <component :is="structureExpanded ? Fold : Expand" />
                </el-icon>
                <span>{{ structureExpanded ? '折叠' : '展开' }}</span>
              </el-button>
              <el-button size="small" plain @click="addRootStructureSection">
                <el-icon class="btn-icon"><Plus /></el-icon>
                <span>新章节</span>
              </el-button>
            </div>

            <div class="structure-meta" v-if="structure.updateTime">
              最近生成时间：{{ structure.updateTime }}
            </div>

            <el-empty
              v-if="!editableSections.length"
              description="暂无结构，点击上方按钮生成或手动新增"
              :image-size="88"
            />

            <template v-else>
              <div class="structure-tree-wrap">
                <el-tree
                  :data="editableSectionTree"
                  :props="structureTreeProps"
                  node-key="key"
                  highlight-current
                  :current-node-key="selectedStructureKey"
                  :default-expanded-keys="defaultExpandedStructureKeys"
                  :expand-on-click-node="false"
                  class="structure-tree"
                  @node-click="onStructureNodeSelect"
                >
                  <template #default="{ data }">
                    <div class="structure-tree-node">
                      <span class="structure-directory-level">L{{ data.level }}</span>
                      <span class="structure-directory-title">{{ data.title }}</span>
                    </div>
                  </template>
                </el-tree>
              </div>

              <div v-if="editableSections[selectedStructureIndex]" class="structure-editor">
                <div class="structure-editor-header">
                  <el-tag size="small">当前章节</el-tag>
                  <div class="structure-editor-actions">
                    <el-button link type="primary" @click="addSiblingStructureSection(selectedStructureIndex)">
                      新增同级
                    </el-button>
                    <el-button link type="primary" @click="addChildStructureSection(selectedStructureIndex)">
                      新增子级
                    </el-button>
                    <el-button link type="danger" @click="removeStructureSection(selectedStructureIndex)">
                      删除
                    </el-button>
                  </div>
                </div>

                <el-input
                  :model-value="editableSections[selectedStructureIndex].title"
                  placeholder="输入章节标题"
                  @update:model-value="
                    (value: string | number) => updateEditableSection(selectedStructureIndex, { title: String(value || '') })
                  "
                />

                <div class="structure-editor-level">
                  <span>层级</span>
                  <el-select
                    :model-value="editableSections[selectedStructureIndex].level"
                    style="width: 120px"
                    @update:model-value="
                      (value: string | number) =>
                        updateEditableSection(selectedStructureIndex, { level: Number(value || 1) })
                    "
                  >
                    <el-option v-for="level in 4" :key="level" :label="`L${level}`" :value="level" />
                  </el-select>
                </div>

                <el-input
                  :model-value="editableSections[selectedStructureIndex].content"
                  type="textarea"
                  :rows="6"
                  placeholder="输入该章节的摘要或说明"
                  @update:model-value="
                    (value: string | number) => updateEditableSection(selectedStructureIndex, { content: String(value || '') })
                  "
                />
              </div>
            </template>
          </section>

          <div class="editor-main">
            <RichEditor :key="`${id}-${editorRefreshKey}`" v-model="note.content" :note-id="id" fill-height />
          </div>
        </div>
      </div>

      <div class="right">
        <section class="panel">
          <h4>摘要</h4>
          <el-input
            v-model="note.summary"
            type="textarea"
            :rows="4"
            placeholder="填写摘要或重点"
          />
        </section>

        <section class="panel">
          <h4>上传素材</h4>
          <p class="panel-tip">支持图片（OCR 识别）与文档 txt/md/pdf/docx（自动提取文本并清洗格式，进入知识库）。</p>
          <ImageUpload :key="id" :note-id="id" @uploaded="onUploaded" @task-completed="onDocTaskCompleted" />

          <ul class="file-list" v-if="files.length">
            <li
              v-for="(f, idx) in files"
              :key="f?.id ?? f?.storedName ?? `file-${idx}`"
              :class="{ active: isQueuedOcrFile(f), embedded: isEmbeddedFile(f) }"
            >
              <div class="file-meta">
                <span class="name">{{ f?.originalName || '未命名文件' }}</span>
                <small>{{ (Number(f?.fileSize || 0) / 1024).toFixed(1) }} KB</small>
              </div>
              <div class="file-actions">
                <el-tag v-if="isEmbeddedFile(f)" size="small">已插入正文</el-tag>
                <template v-else-if="isQueuedOcrFile(f)">
                  <el-tag size="small" type="success">OCR #{{ getOcrQueuePosition(f) }}</el-tag>
                  <el-button
                    link
                    type="primary"
                    :disabled="getOcrQueuePosition(f) <= 1"
                    @click.stop="moveOcrQueueItem(f, 'up')"
                  >
                    上移
                  </el-button>
                  <el-button
                    link
                    type="primary"
                    :disabled="getOcrQueuePosition(f) >= ocrQueueFileIds.length"
                    @click.stop="moveOcrQueueItem(f, 'down')"
                  >
                    下移
                  </el-button>
                  <el-button link type="warning" @click.stop="removeFromOcrQueue(f)">移出队列</el-button>
                </template>
                <el-button v-else link type="primary" @click.stop="addToOcrQueue(f)">加入 OCR 队列</el-button>
                <el-button
                  v-if="isImageFile(f)"
                  link
                  type="primary"
                  :loading="previewLoading"
                  @click.stop="onPreviewFile(f)"
                >预览</el-button>
                <el-button link type="danger" :loading="deletingFileId === f?.id" @click.stop="onDeleteFile(f)">删除</el-button>
              </div>
            </li>
          </ul>

          <p v-if="files.length && !ocrCandidateFiles.length && hasImageFiles" class="panel-tip warning-tip">
            当前图片都已插入正文，如需继续 OCR，请再上传新的原图。
          </p>
          <el-empty v-else-if="!files.length" description="暂无上传图片" :image-size="84" />
        </section>
      </div>
    </div>

    <section class="workspace-panel">
      <div class="workspace-header">
        <div>
          <h4>结构化结果</h4>
          <p class="workspace-sub">这里保留思维导图预览和历史版本，章节目录已移动到编辑器左侧。</p>
        </div>
        <div class="workspace-actions">
          <el-button :loading="historyLoading" @click="fetchHistory">刷新历史</el-button>
        </div>
      </div>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="思维导图" name="mindmap" lazy>
          <div v-loading="structureLoading">
            <MermaidPreview :code="structure.mermaid" />
          </div>
        </el-tab-pane>

        <el-tab-pane label="历史版本" name="history" lazy>
          <div v-loading="historyLoading">
            <el-empty v-if="!historyItems.length" description="暂无历史记录" :image-size="96" />
            <el-timeline v-else>
              <el-timeline-item
                v-for="item in historyItems"
                :key="item.id"
                :timestamp="item.createTime"
                placement="top"
              >
                <div class="history-item">
                  <div class="history-main">
                    <div class="history-title">{{ item.operationDesc }}</div>
                    <div class="history-meta">
                      <el-tag size="small">{{ item.operationType }}</el-tag>
                      <el-tag v-if="item.hasOutline" size="small" type="success">含大纲</el-tag>
                      <el-tag v-if="item.hasMindmap" size="small" type="warning">含导图</el-tag>
                      <span v-if="item.snapshotTitle" class="history-note-title">{{ item.snapshotTitle }}</span>
                    </div>
                  </div>
                  <el-button type="primary" link @click="onRestoreHistory(item)">恢复到此版本</el-button>
                </div>
              </el-timeline-item>
            </el-timeline>
          </div>
        </el-tab-pane>

        <el-tab-pane label="知识图谱" name="graph" lazy>
          <div class="graph-actions-bar">
            <span class="graph-tip">从笔记正文中抽取实体与关系（AI 优先，降级规则抽取）</span>
            <div>
              <el-button
                type="primary"
                :loading="graphBuilding"
                @click="onBuildGraph"
              >构建 / 更新图谱</el-button>
              <el-button
                type="danger"
                plain
                :disabled="!graphData || !graphData.nodes.length"
                @click="onDeleteGraph"
              >删除图谱</el-button>
            </div>
          </div>
          <div v-loading="graphLoading">
            <KnowledgeGraph :data="graphData" height="480px" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </section>

    <el-dialog v-model="previewVisible" :title="previewName" width="680px" destroy-on-close>
      <div class="preview-wrap">
        <img v-if="previewUrl" :src="previewUrl" alt="预览图" />
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.page {
  padding: 20px;
}

.page-header {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px 16px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.03);
  margin-bottom: 16px;
}

.header-top {
  display: flex;
  align-items: center;
  gap: 12px;
}

.back-btn {
  flex-shrink: 0;
  color: #606266;
}

.back-btn + .title-input {
  margin-left: 0;
}

.title-input {
  flex: 1;
  min-width: 0;
}

.title-input :deep(.el-input__wrapper) {
  border-radius: 8px;
}

.title-input :deep(.el-input__inner) {
  font-size: 17px;
  font-weight: 600;
}

.title-icon {
  color: #c0c4cc;
  font-size: 16px;
}

.primary-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.primary-actions .el-button + .el-button {
  margin-left: 0;
}

.ai-icon {
  color: #7c3aed;
}

.save-btn {
  min-width: 88px;
}

.btn-icon {
  margin-right: 4px;
  vertical-align: -2px;
}

.header-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  padding-top: 12px;
  border-top: 1px dashed #f0f2f5;
}

.meta-field {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.meta-label {
  font-size: 13px;
  color: #909399;
  white-space: nowrap;
}

.meta-select--category {
  width: 168px;
}

.meta-select--status {
  width: 96px;
}

.meta-field--grow {
  flex: 1;
  min-width: 240px;
}

.meta-field--grow .el-input {
  width: 100%;
}

.ocr-group {
  display: flex;
  align-items: center;
  gap: 6px;
  padding-left: 16px;
  border-left: 1px solid #ebeef5;
}

.ocr-engine-select {
  width: 132px;
}

.grid {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 16px;
  align-items: stretch;
  height: 72vh;
  min-height: 620px;
}

.editor-layout {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 16px;
  align-items: stretch;
  height: 100%;
}

.editor-main {
  min-width: 0;
  min-height: 0;
  height: 100%;
  overflow: hidden;
}

.panel {
  padding: 14px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fff;
}

.panel + .panel {
  margin-top: 16px;
}

.panel h4 {
  margin: 0 0 8px;
}

.panel-tip {
  margin: 0 0 12px;
  font-size: 12px;
  color: #909399;
}

.warning-tip {
  margin-top: 10px;
  color: #e6a23c;
}

.structure-side {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
  height: 100%;
  overflow-y: auto;
}

.structure-side-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.structure-side-header h4 {
  margin: 0;
}

.structure-side-actions,
.structure-side-toolbar,
.structure-editor-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.structure-side-toolbar {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.structure-side-toolbar .el-button {
  width: 100%;
  margin-left: 0;
  padding-left: 0;
  padding-right: 0;
}

.structure-tree-wrap {
  max-height: none;
  overflow-y: auto;
  padding-right: 4px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #f8fafc;
}

.structure-tree {
  background: transparent;
}

.structure-tree-node {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.structure-directory-level {
  font-size: 12px;
  color: #409eff;
  font-weight: 600;
  flex-shrink: 0;
}

.structure-directory-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.structure-editor {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-top: 12px;
  border-top: 1px solid #ebeef5;
}

.structure-editor-header,
.structure-editor-level {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.left,
.right {
  min-height: 0;
  height: 100%;
}

.editor-main :deep(.rich-editor) {
  height: 100%;
}

:deep(.structure-tree .el-tree-node__content) {
  min-height: 38px;
  border-radius: 8px;
  margin: 4px 6px;
  padding-right: 8px;
}

:deep(.structure-tree .el-tree-node__content:hover) {
  background: #eef5ff;
}

:deep(.structure-tree .el-tree-node.is-current > .el-tree-node__content) {
  background: #ecf5ff;
  color: #303133;
}

.file-list {
  list-style: none;
  padding: 0;
  margin: 10px 0 0;
}

.file-list li {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 10px;
  border: 1px solid transparent;
  border-radius: 8px;
  margin-bottom: 8px;
  font-size: 13px;
  color: #606266;
  cursor: pointer;
  transition: all 0.2s ease;
}

.file-list li:hover {
  background: #f8fafc;
}

.file-list li.active {
  border-color: #409eff;
  background: #ecf5ff;
}

.file-list li.embedded {
  cursor: default;
  background: #fafafa;
}

.file-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.file-meta .name {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-actions {
  display: flex;
  gap: 4px;
  align-items: center;
}

.right {
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow: hidden;
}

.right .panel {
  display: flex;
  flex-direction: column;
  margin-top: 0;
}

.right .panel + .panel {
  margin-top: 0;
}

.right .panel:first-child {
  flex: 0 0 150px;
}

.right .panel:last-child {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
}

.workspace-panel {
  margin-top: 18px;
  padding: 16px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
}

.workspace-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  margin-bottom: 12px;
}

.workspace-header h4 {
  margin: 0;
}

.workspace-sub {
  margin: 4px 0 0;
  font-size: 13px;
  color: #909399;
}

.workspace-actions {
  display: flex;
  gap: 10px;
}

.structure-meta {
  margin-bottom: 10px;
  color: #909399;
  font-size: 12px;
}

.outline-layout {
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  gap: 16px;
}

.outline-tree,
.outline-sections {
  min-height: 220px;
}

.outline-node {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 2px 0;
}

.outline-title {
  font-weight: 600;
  color: #303133;
}

.outline-content {
  color: #909399;
}

.section-card {
  padding: 12px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  margin-bottom: 10px;
  background: #fafafa;
}

.section-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-weight: 600;
}

.section-card p {
  margin: 0;
  color: #606266;
  white-space: pre-wrap;
}

.history-item {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.history-main {
  flex: 1;
}

.history-title {
  font-weight: 600;
  color: #303133;
}

.history-meta {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 8px;
  align-items: center;
}

.history-note-title {
  color: #909399;
  font-size: 12px;
}

.preview-wrap {
  text-align: center;
}

.preview-wrap img {
  max-width: 100%;
  max-height: 70vh;
  object-fit: contain;
}

@media (max-width: 1400px) {
  .editor-layout {
    grid-template-columns: 260px minmax(0, 1fr);
  }
}

@media (max-width: 1200px) {
  .grid {
    grid-template-columns: 1fr;
    height: auto;
  }

  .grid,
  .left,
  .right,
  .editor-layout,
  .editor-main,
  .structure-side {
    height: auto;
    min-height: 0;
  }

  .editor-layout {
    grid-template-columns: 1fr;
  }

  .right {
    overflow: visible;
  }

  .right .panel:first-child,
  .right .panel:last-child {
    flex: none;
    overflow: visible;
  }
}

@media (max-width: 900px) {
  .workspace-header,
  .structure-side-header,
  .structure-editor-header,
  .structure-editor-level {
    flex-direction: column;
    align-items: flex-start;
  }

  .header-top {
    flex-wrap: wrap;
  }

  /* 窄屏：标题独占一行，返回/动作按钮收进上一行 */
  .title-input {
    order: 3;
    flex-basis: 100%;
  }

  .header-meta,
  .ocr-group {
    gap: 10px;
    padding-left: 0;
    border-left: none;
  }

  .meta-field--grow {
    min-width: 100%;
  }
}

.graph-actions-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  gap: 12px;
  flex-wrap: wrap;
}

.graph-tip {
  color: #909399;
  font-size: 13px;
}

.normalize-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
  color: #909399;
  font-size: 12px;
}

</style>

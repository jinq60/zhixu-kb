import http from './http'

export interface UploadResult {
  id: number
  originalName: string
  storedName: string
  filePath: string
  fileSize: number
  mimeType: string
  /** note ID 为雪花 ID，后端序列化为字符串 */
  noteId?: string | number
}

/** 上传响应：file 为文件记录；文档上传后返回 taskId，解析/清洗在后台异步执行 */
export interface UploadResponse {
  file: UploadResult
  /** 文档处理任务（解析 + AI 清洗，异步后台执行，完成后正文自动写回） */
  taskId?: number
  taskStatus?: string
}

/** 块级进度统计（清洗/向量化明细） */
export interface ChunkStats {
  total: number
  success: number
  failed: number
  processing: number
}

/** 文档处理任务状态（细粒度：阶段/进度/块级统计/耗时） */
export interface DocumentTaskStatus {
  taskId: string
  status: string
  currentStage: string
  progress: number
  failReason?: string
  noteId?: string
  fileName?: string
  subType?: string
  elapsedSeconds?: number
  cleanChunks?: ChunkStats
  embedChunks?: ChunkStats
}

/** 查询文档处理任务状态 */
export async function getTaskStatus(taskId: string | number): Promise<DocumentTaskStatus> {
  const { data } = await http.get(`/api/files/tasks/${taskId}`)
  return data.data as DocumentTaskStatus
}

/** 按笔记查询最新任务（编辑页恢复进度展示） */
export async function getTaskByNote(noteId: string | number): Promise<DocumentTaskStatus | null> {
  const { data } = await http.get(`/api/files/tasks/note/${noteId}`)
  return data.data as DocumentTaskStatus
}

/** 进行中任务列表（全局任务角标） */
export interface ActiveTask extends DocumentTaskStatus {}

/** 最近任务（含完成/失败，任务面板展示历史） */
export interface RecentTask extends DocumentTaskStatus {}

export async function getActiveTasks(): Promise<ActiveTask[]> {
  const { data } = await http.get('/api/files/tasks/active')
  return data.data as ActiveTask[]
}

export async function getRecentTasks(): Promise<RecentTask[]> {
  const { data } = await http.get('/api/files/tasks/recent')
  return data.data as RecentTask[]
}

/** 重试失败任务 */
export async function retryTask(taskId: string | number): Promise<void> {
  await http.post(`/api/files/tasks/${taskId}/retry`)
}

/** 删除单条任务记录（级联删除明细，不影响笔记正文） */
export async function deleteTaskRecord(taskId: string | number): Promise<void> {
  await http.delete(`/api/files/tasks/${taskId}`)
}

/** 清空当前用户全部任务记录 */
export async function clearAllTaskRecords(): Promise<void> {
  await http.delete('/api/files/tasks')
}

/** 分片大小：8MB */
const CHUNK_SIZE = 8 * 1024 * 1024
/** 超过该大小的文件走分片上传 */
const CHUNK_THRESHOLD = 10 * 1024 * 1024

const createIdentifier = (file: File) => {
  const seed = `${file.name}-${file.size}-${file.lastModified}`
  let hash = 0
  for (let i = 0; i < seed.length; i++) {
    hash = (hash << 5) - hash + seed.charCodeAt(i)
    hash |= 0
  }
  return `u-${Date.now()}-${Math.abs(hash).toString(36)}`
}

/**
 * 上传文件：小文件直接上传；大文件自动分片（8MB/片）+ 进度回调。
 */
export async function uploadFile(
  file: File,
  noteId?: string | number,
  onProgress?: (percent: number) => void
): Promise<UploadResponse> {
  if (file.size <= CHUNK_THRESHOLD) {
    const form = new FormData()
    form.append('file', file)
    if (noteId) form.append('noteId', String(noteId))
    const { data } = await http.post('/api/files/upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120000
    })
    return data.data as UploadResponse
  }

  // 分片上传
  const identifier = createIdentifier(file)
  const totalChunks = Math.ceil(file.size / CHUNK_SIZE)
  for (let index = 0; index < totalChunks; index++) {
    const start = index * CHUNK_SIZE
    const chunk = file.slice(start, Math.min(start + CHUNK_SIZE, file.size))
    const form = new FormData()
    form.append('file', chunk, file.name)
    form.append('identifier', identifier)
    form.append('chunkIndex', String(index))
    form.append('totalChunks', String(totalChunks))
    await http.post('/api/files/upload-chunk', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120000
    })
    if (onProgress) {
      onProgress(Math.round(((index + 1) / totalChunks) * 80))
    }
  }
  // 合并
  const form = new FormData()
  form.append('identifier', identifier)
  form.append('fileName', file.name)
  form.append('totalChunks', String(totalChunks))
  if (noteId) form.append('noteId', String(noteId))
  const { data } = await http.post('/api/files/upload-chunk/merge', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
  if (onProgress) {
    onProgress(100)
  }
  return data.data as UploadResponse
}

export async function fetchFileBlob(id: number): Promise<Blob> {
  const { data } = await http.get(`/api/files/${id}/content`, {
    responseType: 'blob'
  })
  return data as Blob
}

export async function deleteFile(id: number): Promise<void> {
  await http.delete(`/api/files/${id}`)
}

import http from './http'

export interface GraphNode {
  id?: string
  name: string
  type: string
  description?: string
}

export interface GraphEdge {
  source: string
  target: string
  relation: string
}

export interface GraphData {
  noteId?: string
  noteTitle: string
  nodes: GraphNode[]
  edges: GraphEdge[]
  extractionSource?: string
}

export interface GraphBuildSubmitResult {
  submitted: boolean
  taskId: string
  taskType: 'NOTE' | 'CATEGORY' | 'GLOBAL'
  targetId?: string
  targetName?: string
}

export interface GraphTaskItem {
  taskId: string
  taskType: 'NOTE' | 'CATEGORY' | 'GLOBAL'
  targetId?: string
  targetName?: string
  running: boolean
  stage?: string
  error: string | null
  startedAt: number
  finishedAt: number
  elapsedSeconds?: number
}

export interface GraphBuildResult {
  noteId?: string
  entityCount: number
  relationCount: number
  extractionSource: string
  message: string
}

/** 提交单篇笔记图谱构建任务（异步，立即返回 taskId） */
export async function buildNoteGraph(noteId: string | number) {
  const { data } = await http.post(`/api/notes/${noteId}/graph/build`, null, { timeout: 30000 })
  return data.data as GraphBuildSubmitResult
}

export async function getNoteGraph(noteId: string | number) {
  const { data } = await http.get(`/api/notes/${noteId}/graph`)
  return data.data as GraphData
}

export async function deleteNoteGraph(noteId: string | number) {
  await http.delete(`/api/notes/${noteId}/graph`)
}

export async function buildCategoryGraph(categoryId: number) {
  const { data } = await http.post(`/api/graph/category/${categoryId}/build`, null, { timeout: 30000 })
  return data.data as GraphBuildSubmitResult
}

export async function getCategoryGraph(categoryId: number) {
  const { data } = await http.get(`/api/graph/category/${categoryId}`)
  return data.data as GraphData
}

export async function buildGlobalGraph() {
  const { data } = await http.post('/api/graph/global/build', null, { timeout: 30000 })
  return data.data as GraphBuildSubmitResult
}

export async function getGlobalGraph() {
  const { data } = await http.get('/api/graph/global')
  return data.data as GraphData
}

export async function searchGraph(keyword: string) {
  const { data } = await http.get('/api/graph/search', { params: { keyword } })
  return data.data as GraphNode[]
}

/** 当前用户的图谱构建任务列表（进行中 + 最近） */
export async function listGraphTasks() {
  const { data } = await http.get('/api/graph/tasks')
  return data.data as { active: GraphTaskItem[]; recent: GraphTaskItem[] }
}

/** 查询单个图谱构建任务状态 */
export async function getGraphTaskStatus(taskId: string) {
  const { data } = await http.get(`/api/graph/tasks/${encodeURIComponent(taskId)}`)
  return data.data as GraphTaskItem
}

/** 删除图谱构建任务记录 */
export async function deleteGraphTask(taskId: string) {
  await http.delete(`/api/graph/tasks/${encodeURIComponent(taskId)}`)
}

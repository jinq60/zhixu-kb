import http from './http'

export interface Note {
  id?: number
  title: string
  content?: string
  summary?: string
  keywords?: string
  coverImage?: string
  categoryId?: number
  status?: number
  ocrText?: string
  outline?: OutlineNode[]
}

export interface AIAnalysisResult {
  suggestedCategory: string
  tags: string[]
  summary: string
  keywords: string
  outline?: OutlineNode[]
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
}

export interface NoteSectionItem {
  id: number
  noteId: number
  parentId?: number
  title: string
  content?: string
  level: number
  sortOrder: number
}

export interface OutlineNode {
  title: string
  content?: string
  children?: OutlineNode[]
}

export interface NoteStructureResponse {
  outline: OutlineNode[]
  sections: NoteSectionItem[]
  mermaid?: string
  updateTime?: string
}

export interface NoteHistoryItem {
  id: number
  operationType: string
  operationDesc: string
  createTime: string
  snapshotTitle?: string
  hasOutline?: boolean
  hasMindmap?: boolean
}

export interface NoteStats {
  totalCount: number
  draftCount: number
  publishedCount: number
}

export interface PublicNoteDetail {
  id: number
  title: string
  content?: string
  ocrText?: string
  summary?: string
  keywords?: string
  coverImage?: string
  status?: number
  categoryName?: string
  authorName?: string
  authorId?: number
  editable?: boolean
  published?: boolean
  createTime?: string
  updateTime?: string
}

export async function listNotes(params: { page?: number; size?: number; categoryId?: number }) {
  const { data } = await http.get('/api/notes', { params })
  return data.data as PageResult<Note>
}

export async function searchNotes(params: { keyword: string; page?: number; size?: number; categoryId?: number }) {
  const { data } = await http.get('/api/notes/search', { params })
  return data.data as PageResult<Note>
}

export async function getMyNoteStats() {
  const { data } = await http.get('/api/notes/stats')
  return data.data as NoteStats
}

export async function getReadableNote(id: number) {
  const { data } = await http.get(`/api/public/notes/${id}`)
  return data.data as PublicNoteDetail
}

export async function getReadableNoteStructure(id: number) {
  const { data } = await http.get(`/api/public/notes/${id}/structure`)
  return data.data as NoteStructureResponse
}

export async function getNote(id: number) {
  const { data } = await http.get(`/api/notes/${id}`)
  return data.data as { note: Note; files: any[] }
}

export async function createNote(payload: Note) {
  const { data } = await http.post('/api/notes', payload)
  return data.data as Note
}

export async function updateNote(id: number, payload: Note) {
  const { data } = await http.put(`/api/notes/${id}`, payload)
  return data.data as Note
}

export async function deleteNote(id: number) {
  await http.delete(`/api/notes/${id}`)
}

export type OCREngine = 'auto' | 'paddle' | 'deepseek'

export async function triggerOCR(id: number, engine: OCREngine = 'auto', fileIds?: number[]) {
  const payload: { engine: OCREngine; fileIds?: number[] } = { engine }
  if (fileIds?.length) {
    payload.fileIds = fileIds
  }
  const { data } = await http.post(`/api/notes/${id}/ocr`, payload, {
    timeout: 120000
  })
  return data.data as string
}

export async function submitAIAnalysis(id: number) {
  const { data } = await http.post(`/api/notes/${id}/ai-analysis`, null, {
    timeout: 20000
  })
  return data.data as { submitted: boolean }
}

export interface AiAnalysisStatus {
  running: boolean
  error: string | null
  startedAt: number
  finishedAt: number
}

export async function getAIAnalysisStatus(id: number) {
  const { data } = await http.get(`/api/notes/${id}/ai-analysis/status`)
  return data.data as AiAnalysisStatus
}

export async function getNoteStructure(id: number) {
  const { data } = await http.get(`/api/notes/${id}/structure`)
  return data.data as NoteStructureResponse
}

export async function getNoteHistory(id: number) {
  const { data } = await http.get(`/api/notes/${id}/history`)
  return data.data as NoteHistoryItem[]
}

export async function restoreNoteHistory(id: number, historyId: number) {
  await http.post(`/api/notes/${id}/history/${historyId}/restore`)
}

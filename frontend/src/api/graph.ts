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
  noteId?: number
  noteTitle: string
  nodes: GraphNode[]
  edges: GraphEdge[]
  extractionSource?: string
}

export interface GraphBuildResult {
  noteId?: number
  entityCount: number
  relationCount: number
  extractionSource: string
  message: string
}

export async function buildNoteGraph(noteId: number) {
  const { data } = await http.post(`/api/notes/${noteId}/graph/build`, null, { timeout: 180000 })
  return data.data as GraphBuildResult
}

export async function getNoteGraph(noteId: number) {
  const { data } = await http.get(`/api/notes/${noteId}/graph`)
  return data.data as GraphData
}

export async function deleteNoteGraph(noteId: number) {
  await http.delete(`/api/notes/${noteId}/graph`)
}

export async function buildCategoryGraph(categoryId: number) {
  const { data } = await http.post(`/api/graph/category/${categoryId}/build`, null, { timeout: 180000 })
  return data.data as GraphBuildResult
}

export async function getCategoryGraph(categoryId: number) {
  const { data } = await http.get(`/api/graph/category/${categoryId}`)
  return data.data as GraphData
}

export async function buildGlobalGraph() {
  const { data } = await http.post('/api/graph/global/build', null, { timeout: 180000 })
  return data.data as GraphBuildResult
}

export async function getGlobalGraph() {
  const { data } = await http.get('/api/graph/global')
  return data.data as GraphData
}

export async function searchGraph(keyword: string) {
  const { data } = await http.get('/api/graph/search', { params: { keyword } })
  return data.data as GraphNode[]
}

import http from './client'

// ---------- 系统总览 / AI 引擎 ----------
export interface AlertEvent {
  id: string
  type: string
  severity: string
  message: string
  createdAt: string
  metadata: Record<string, any>
}

export interface SystemOverview {
  serverTime: string
  status: string
  aiEngineType: string
  aiEngineConfiguredType: string
  aiEngineOverrideEnabled: boolean
  aiEngineOverrideType: string | null
  aiHealthy: boolean
  platformDefaultApi?: boolean
  monitorEnabled: boolean
  rateLimitPerMinute: number
  recentAlertCount: number
  recentAlerts: AlertEvent[]
}

export interface AiEngineState {
  effectiveEngineType: string
  configuredEngineType: string
  overrideEnabled: boolean
  overrideEngineType: string | null
  healthy: boolean
  availableEngineTypes: string[]
}

export async function getSystemOverview(alertLimit = 20) {
  const { data } = await http.get('/api/v1/admin/system/overview', { params: { alertLimit } })
  return data.data as SystemOverview
}

export async function getAiEngineState() {
  const { data } = await http.get('/api/v1/admin/system/ai-engine')
  return data.data as AiEngineState
}

export async function resetAiEngine() {
  const { data } = await http.post('/api/v1/admin/system/ai-engine/reset')
  return data.data as AiEngineState
}

// ---------- 用户治理 ----------
export interface AdminUser {
  id: number
  username: string
  email?: string
  status: number
  createTime?: string
  roles: string[]
}

export async function adminListUsers() {
  const { data } = await http.get('/api/v1/admin/users')
  return data.data as AdminUser[]
}

export async function adminChangeRole(userId: number, role: string) {
  await http.post(`/api/v1/admin/users/${userId}/role`, { role })
}

// ---------- 图谱总览 ----------
export interface GraphOverview {
  available: boolean
  noteCount: number
  entityCount: number
  relationCount: number
  message?: string
}

export async function getGraphOverview() {
  const { data } = await http.get('/api/v1/admin/graph/overview')
  return data.data as GraphOverview
}

// ---------- AI 端点管理 ----------
export interface AiEndpoint {
  id: number
  baseUrl: string
  apiKeyMasked: string
  model: string
  embeddingModel?: string
  enabled: boolean
  remark?: string
  cooldown: boolean
  status: string
  statusText: string
  lastUsed?: string
}

export interface AiEndpointSavePayload {
  id?: number
  baseUrl: string
  apiKey?: string
  model: string
  embeddingModel?: string
  enabled?: boolean
  remark?: string
}

export async function adminListAiEndpoints() {
  const { data } = await http.get('/api/v1/admin/ai/endpoints')
  return data.data as AiEndpoint[]
}

export async function adminCreateAiEndpoint(payload: AiEndpointSavePayload) {
  const { data } = await http.post('/api/v1/admin/ai/endpoints', payload)
  return data.data as AiEndpoint
}

export async function adminUpdateAiEndpoint(id: number, payload: AiEndpointSavePayload) {
  const { data } = await http.put(`/api/v1/admin/ai/endpoints/${id}`, payload)
  return data.data as AiEndpoint
}

export async function adminDeleteAiEndpoint(id: number) {
  await http.delete(`/api/v1/admin/ai/endpoints/${id}`)
}

export async function adminToggleAiEndpoint(id: number, enabled: boolean) {
  const { data } = await http.post(`/api/v1/admin/ai/endpoints/${id}/toggle`, null, { params: { enabled } })
  return data.data as AiEndpoint
}

export async function adminTestAiEndpoint(id: number) {
  const { data } = await http.post(`/api/v1/admin/ai/endpoints/${id}/test`, null, { timeout: 30000 })
  return data.data as { success: boolean; message: string; model?: string }
}

import http from './http'

export interface AiUserConfig {
  provider: string
  baseUrl: string
  apiKeyMasked: string
  model: string
  embeddingBaseUrl?: string
  embeddingApiKeyMasked?: string
  embeddingModel?: string
  enabled: boolean
  configured: boolean
}

export interface AiConfigSavePayload {
  provider: string
  baseUrl: string
  apiKey?: string
  model: string
  embeddingBaseUrl?: string
  embeddingApiKey?: string
  embeddingModel?: string
  enabled?: boolean
}

export async function getAiConfig() {
  const { data } = await http.get('/api/v1/ai/config')
  return data.data as AiUserConfig
}

export async function saveAiConfig(payload: AiConfigSavePayload) {
  const { data } = await http.put('/api/v1/ai/config', payload)
  return data.data as AiUserConfig
}

export async function clearAiConfig() {
  await http.delete('/api/v1/ai/config')
}

export async function testAiConnection(payload: { baseUrl: string; apiKey: string; model?: string }) {
  const { data } = await http.post('/api/v1/ai/config/test', payload, { timeout: 30000 })
  return data.data as { success: boolean; message: string; model?: string }
}

import http from './http'

export interface RelatedNote {
  noteId: string | number
  noteTitle: string
  similarity?: number
}

export interface AskRecord {
  id: string
  userId: number
  question: string
  answer: string
  relatedNotes: RelatedNote[]
  status: string
  confidenceLevel: string
  riskFlags: string[]
  conversationId?: string
  createdAt?: string
  completedAt?: string
}

export async function submitAsk(question: string, conversationId?: string) {
  const { data } = await http.post('/api/v1/ask', { question, conversationId }, { timeout: 180000 })
  return data.data as AskRecord
}

export async function submitAskStream(
  question: string,
  onChunk: (chunk: string) => void,
  signal?: AbortSignal,
  conversationId?: string
): Promise<void> {
  const authStore = (await import('../stores/auth')).useAuthStore()
  const base = (http.defaults.baseURL || '').replace(/\/$/, '')
  const response = await fetch(`${base}/api/v1/ask/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${authStore.token || ''}`
    },
    body: JSON.stringify({ question, conversationId }),
    signal
  })
  if (!response.ok || !response.body) {
    // fetch 不经过 axios 拦截器：token 过期时手动触发登出（与 http.ts 401 行为一致：
    // 仅在仍有 token 时登出，避免并行请求重复触发），引导用户重新登录
    if (response.status === 401 && authStore.token) {
      authStore.logout().catch(() => undefined)
    }
    throw new Error(`流式请求失败: ${response.status}`)
  }
  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  const emitPayload = (raw: string) => {
    const payload = raw.trim()
    if (!payload || payload === '[DONE]') return
    try {
      const parsed = JSON.parse(payload)
      if (typeof parsed === 'string') {
        if (parsed) onChunk(parsed)
        return
      }
      const delta =
        parsed?.choices?.[0]?.delta?.content || parsed?.choices?.[0]?.message?.content || ''
      if (delta) onChunk(delta)
    } catch {
      // 非 JSON 时按原文输出
      onChunk(payload)
    }
  }
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() || ''
    for (const line of lines) {
      const trimmed = line.trim()
      if (!trimmed.startsWith('data:')) continue
      emitPayload(trimmed.substring(5))
    }
  }
  const tail = decoder.decode()
  if (tail) {
    buffer += tail
    const trimmed = buffer.trim()
    if (trimmed.startsWith('data:')) {
      emitPayload(trimmed.substring(5))
    }
  }
}

export async function getAsk(id: string) {
  const { data } = await http.get(`/api/v1/ask/${id}`)
  return data.data as AskRecord
}

export async function deleteAsk(id: string) {
  await http.delete(`/api/v1/ask/${id}`)
}

export async function exportAsk(id: string) {
  const { data } = await http.get(`/api/v1/ask/${id}/export`)
  return data.data as { fileName: string; content: string }
}

export async function listAskRecords(params: { page?: number; size?: number }) {
  const { data } = await http.get('/api/v1/ask', { params })
  return data.data as AskRecord[]
}

import http from './http'

export interface RelatedNote {
  noteId: number
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
  createdAt?: string
  completedAt?: string
}

export async function submitAsk(question: string) {
  const { data } = await http.post('/api/v1/ask', { question }, { timeout: 180000 })
  return data.data as AskRecord
}

export async function submitAskStream(
  question: string,
  onChunk: (chunk: string) => void,
  signal?: AbortSignal
): Promise<void> {
  const authStore = (await import('../stores/auth')).useAuthStore()
  const base = (http.defaults.baseURL || '').replace(/\/$/, '')
  const response = await fetch(`${base}/api/v1/ask/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${authStore.token || ''}`
    },
    body: JSON.stringify({ question }),
    signal
  })
  if (!response.ok || !response.body) {
    throw new Error(`流式请求失败: ${response.status}`)
  }
  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() || ''
    for (const line of lines) {
      const trimmed = line.trim()
      if (!trimmed.startsWith('data:')) continue
      const payload = trimmed.substring(5).trim()
      if (payload === '[DONE]') continue
      if (payload) {
        try {
          const parsed = JSON.parse(payload)
          const delta = parsed?.choices?.[0]?.delta?.content || parsed?.choices?.[0]?.message?.content || ''
          if (delta) onChunk(delta)
        } catch {
          // 非 JSON 时直接输出原文
          onChunk(payload)
        }
      }
    }
  }
  const tail = decoder.decode()
  if (tail) {
    buffer += tail
    const trimmed = buffer.trim()
    if (trimmed.startsWith('data:')) {
      const payload = trimmed.substring(5).trim()
      if (payload && payload !== '[DONE]') onChunk(payload)
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

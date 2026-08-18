import http from './http'

export interface UploadResult {
  id: number
  originalName: string
  storedName: string
  filePath: string
  fileSize: number
  mimeType: string
  noteId?: number
}

/** 上传响应：file 为文件记录，extractedText 为原始提取文本，normalizedText 为 AI 规范化文本（图片为 null） */
export interface UploadResponse {
  file: UploadResult
  extractedText: string | null
  normalizedText: string | null
}

export async function uploadFile(file: File, noteId?: number): Promise<UploadResponse> {
  const form = new FormData()
  form.append('file', file)
  if (noteId) form.append('noteId', String(noteId))
  const { data } = await http.post('/api/files/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
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

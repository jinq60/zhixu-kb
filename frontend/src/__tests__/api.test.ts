import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

// Mock axios
vi.mock('axios', () => {
  const mockInstance = {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    interceptors: {
      request: { use: vi.fn() },
      response: { use: vi.fn() }
    }
  }
  return {
    default: {
      create: vi.fn(() => mockInstance),
      ...mockInstance
    }
  }
})

vi.mock('../router', () => ({
  default: { push: vi.fn() }
}))

import http from '../api/http'

describe('API - Auth', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('guestLogin should POST to /api/auth/guest and return token', async () => {
    const mockResponse = { data: { data: { token: 'jwt-abc123' } } }
    vi.mocked(http.post).mockResolvedValue(mockResponse)

    const { guestLogin } = await import('../api/auth')
    const token = await guestLogin('dev-test-1234')

    expect(http.post).toHaveBeenCalledWith('/api/auth/guest', { deviceId: 'dev-test-1234' })
    expect(token).toBe('jwt-abc123')
  })

  it('fetchUserInfo should GET /api/auth/info', async () => {
    const userInfo = { id: 1, username: 'admin', email: 'admin@test.com', roles: ['admin'] }
    vi.mocked(http.get).mockResolvedValue({ data: { data: userInfo } })

    const { fetchUserInfo } = await import('../api/auth')
    const result = await fetchUserInfo()

    expect(http.get).toHaveBeenCalledWith('/api/auth/info')
    expect(result).toEqual(userInfo)
  })
})

describe('API - Notes', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('listNotes should GET /api/notes with params', async () => {
    const pageResult = { records: [], total: 0, size: 10, current: 1 }
    vi.mocked(http.get).mockResolvedValue({ data: { data: pageResult } })

    const { listNotes } = await import('../api/note')
    const result = await listNotes({ page: 1, size: 10, categoryId: 2 })

    expect(http.get).toHaveBeenCalledWith('/api/notes', { params: { page: 1, size: 10, categoryId: 2 } })
    expect(result.total).toBe(0)
  })

  it('createNote should POST /api/notes', async () => {
    const note = { title: 'Test Note', content: 'Hello' }
    const created = { ...note, id: 1 }
    vi.mocked(http.post).mockResolvedValue({ data: { data: created } })

    const { createNote } = await import('../api/note')
    const result = await createNote(note)

    expect(http.post).toHaveBeenCalledWith('/api/notes', note)
    expect(result.id).toBe(1)
  })

  it('updateNote should PUT /api/notes/:id', async () => {
    const note = { title: 'Updated Title', content: 'Updated' }
    vi.mocked(http.put).mockResolvedValue({ data: { data: { ...note, id: 5 } } })

    const { updateNote } = await import('../api/note')
    const result = await updateNote(5, note)

    expect(http.put).toHaveBeenCalledWith('/api/notes/5', note)
    expect(result.title).toBe('Updated Title')
  })

  it('deleteNote should DELETE /api/notes/:id', async () => {
    vi.mocked(http.delete).mockResolvedValue({ data: { code: 200 } })

    const { deleteNote } = await import('../api/note')
    await deleteNote(3)

    expect(http.delete).toHaveBeenCalledWith('/api/notes/3')
  })

  it('triggerOCR should POST with engine and fileIds', async () => {
    vi.mocked(http.post).mockResolvedValue({ data: { data: 'Recognized text' } })

    const { triggerOCR } = await import('../api/note')
    const result = await triggerOCR(1, 'paddle', [10, 20])

    expect(http.post).toHaveBeenCalledWith(
      '/api/notes/1/ocr',
      { engine: 'paddle', fileIds: [10, 20] },
      { timeout: 120000 }
    )
    expect(result).toBe('Recognized text')
  })

  it('searchNotes should GET /api/notes/search with keyword', async () => {
    const pageResult = { records: [], total: 0, size: 10, current: 1 }
    vi.mocked(http.get).mockResolvedValue({ data: { data: pageResult } })

    const { searchNotes } = await import('../api/note')
    await searchNotes({ keyword: 'test', page: 1, size: 10 })

    expect(http.get).toHaveBeenCalledWith('/api/notes/search', {
      params: { keyword: 'test', page: 1, size: 10 }
    })
  })

  it('getNoteHistory should GET /api/notes/:id/history', async () => {
    const history = [{ id: 1, operationType: 'UPDATE', operationDesc: 'update', createTime: '2024-01-01' }]
    vi.mocked(http.get).mockResolvedValue({ data: { data: history } })

    const { getNoteHistory } = await import('../api/note')
    const result = await getNoteHistory(5)

    expect(http.get).toHaveBeenCalledWith('/api/notes/5/history')
    expect(result).toHaveLength(1)
  })
})

describe('API - Categories', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('listCategories should GET /api/categories', async () => {
    const categories = [{ id: 1, name: 'Math' }]
    vi.mocked(http.get).mockResolvedValue({ data: { data: categories } })

    const { listCategories } = await import('../api/category')
    const result = await listCategories()

    expect(http.get).toHaveBeenCalledWith('/api/categories')
    expect(result).toHaveLength(1)
  })

  it('createCategory should POST /api/categories', async () => {
    vi.mocked(http.post).mockResolvedValue({ data: { data: { id: 2, name: 'Science' } } })

    const { createCategory } = await import('../api/category')
    const result = await createCategory({ name: 'Science' })

    expect(http.post).toHaveBeenCalledWith('/api/categories', { name: 'Science' })
    expect(result.id).toBe(2)
  })

  it('deleteCategory should DELETE /api/categories/:id', async () => {
    vi.mocked(http.delete).mockResolvedValue({ data: { code: 200 } })

    const { deleteCategory } = await import('../api/category')
    await deleteCategory(1)

    expect(http.delete).toHaveBeenCalledWith('/api/categories/1')
  })
})

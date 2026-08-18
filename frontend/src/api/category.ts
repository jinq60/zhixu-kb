import http from './http'

export interface Category {
  id?: number
  name: string
  description?: string
  sortOrder?: number
}

export async function listCategories() {
  const { data } = await http.get('/api/categories')
  return data.data as Category[]
}

export async function pageCategories(params: { page: number; size: number; keyword?: string }) {
  const { data } = await http.get('/api/categories/page', { params })
  return data.data as { records: Category[]; total: number }
}

export async function createCategory(payload: Category) {
  const { data } = await http.post('/api/categories', payload)
  return data.data as Category
}

export async function updateCategory(id: number, payload: Category) {
  const { data } = await http.put(`/api/categories/${id}`, payload)
  return data.data as Category
}

export async function deleteCategory(id: number) {
  await http.delete(`/api/categories/${id}`)
}

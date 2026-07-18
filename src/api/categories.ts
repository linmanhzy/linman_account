import client from './client'
import type { CategoryNode, RecordType } from '../types'

export async function getCategoryTree(): Promise<CategoryNode[]> {
  const data = await client.get<CategoryNode[]>('/api/categories')
  return data
}

export async function createCategory(payload: {
  name: string
  type?: RecordType
  icon?: string
  parentId?: number | null
}): Promise<CategoryNode> {
  const data = await client.post<CategoryNode>('/api/categories', payload)
  return data
}

export async function updateCategory(
  id: number,
  payload: { name?: string; icon?: string }
): Promise<CategoryNode> {
  const data = await client.put<CategoryNode>(`/api/categories/${id}`, payload)
  return data
}

export async function deleteCategory(id: number): Promise<void> {
  await client.delete(`/api/categories/${id}`)
}

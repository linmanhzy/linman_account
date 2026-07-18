import React, { createContext, useContext, useState, useEffect, useMemo, useCallback } from 'react'
import { getCategoryTree, createCategory, updateCategory, deleteCategory } from '../api/categories'
import type { CategoryL1, CategoryNode, RecordType } from '../types'

interface CategoryContextValue {
  loading: boolean
  categoryTree: CategoryNode[]
  expenseCategories: CategoryL1[]
  incomeCategories: CategoryL1[]
  refresh: () => Promise<void>
  addL1: (type: RecordType, name: string, icon: string) => Promise<void>
  addL2: (parentId: number, name: string) => Promise<void>
  updateCategory: (id: number, updates: { name?: string; icon?: string }) => Promise<void>
  deleteCategory: (id: number) => Promise<void>
}

const CategoryContext = createContext<CategoryContextValue | null>(null)

function toL1(nodes: CategoryNode[]): CategoryL1[] {
  return nodes.map((n) => ({
    name: n.name,
    icon: n.icon || '📦',
    children: n.children.map((c) => c.name),
  }))
}

export function CategoryProvider({ children }: { children: React.ReactNode }) {
  const [categoryTree, setCategoryTree] = useState<CategoryNode[]>([])
  const [loading, setLoading] = useState(true)

  const refresh = useCallback(async () => {
    try {
      const tree = await getCategoryTree()
      setCategoryTree(tree)
    } catch (err) {
      console.error('加载分类失败:', err)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    refresh()
  }, [refresh])

  const expenseCategories = useMemo(
    () => toL1(categoryTree.filter((n) => n.type === 'expense')),
    [categoryTree]
  )
  const incomeCategories = useMemo(
    () => toL1(categoryTree.filter((n) => n.type === 'income')),
    [categoryTree]
  )

  const addL1 = useCallback(
    async (type: RecordType, name: string, icon: string) => {
      await createCategory({ name, type, icon, parentId: null })
      await refresh()
    },
    [refresh]
  )

  const addL2 = useCallback(
    async (parentId: number, name: string) => {
      await createCategory({ name, parentId })
      await refresh()
    },
    [refresh]
  )

  const updateCategoryCb = useCallback(
    async (id: number, updates: { name?: string; icon?: string }) => {
      await updateCategory(id, updates)
      await refresh()
    },
    [refresh]
  )

  const deleteCategoryCb = useCallback(
    async (id: number) => {
      await deleteCategory(id)
      await refresh()
    },
    [refresh]
  )

  return (
    <CategoryContext.Provider
      value={{
        loading,
        categoryTree,
        expenseCategories,
        incomeCategories,
        refresh,
        addL1,
        addL2,
        updateCategory: updateCategoryCb,
        deleteCategory: deleteCategoryCb,
      }}
    >
      {children}
    </CategoryContext.Provider>
  )
}

export function useCategoryContext(): CategoryContextValue {
  const ctx = useContext(CategoryContext)
  if (!ctx) {
    throw new Error('useCategoryContext 必须在 CategoryProvider 内部使用')
  }
  return ctx
}

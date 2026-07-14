import React, { createContext, useContext, useState, useEffect, useMemo, useCallback } from 'react';
import {
  getCategories,
  getCategoriesGrouped,
  addCategory as dbAddCategory,
  updateCategory as dbUpdateCategory,
  deleteCategory as dbDeleteCategory,
} from '../data/categoryDb';
import type { CategoryL1 } from '../data/categories';

interface CategoryContextValue {
  // 平铺数据（供管理页面使用）
  allCategories: Category[];
  // 分组数据（供 CategorySelect 下拉框使用）
  expenseCategories: CategoryL1[];
  incomeCategories: CategoryL1[];
  loading: boolean;
  refreshCategories: () => Promise<void>;
  addCategory: (cat: { type: 'expense' | 'income'; name_l1: string; name_l2: string; icon: string }) => Promise<void>;
  updateCategory: (id: number, updates: { name_l1?: string; name_l2?: string; icon?: string }) => Promise<void>;
  deleteCategory: (id: number) => Promise<void>;
}

const CategoryContext = createContext<CategoryContextValue | null>(null);

export function CategoryProvider({ children }: { children: React.ReactNode }) {
  const [allCategories, setAllCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);

  const refreshCategories = useCallback(async () => {
    try {
      const data = await getCategories();
      setAllCategories(data);
    } catch (err) {
      console.error('加载分类数据失败:', err);
    } finally {
      setLoading(false);
    }
  }, []);

  // 挂载时加载
  useEffect(() => {
    refreshCategories();
  }, [refreshCategories]);

  // 从平铺数据派生分组数据
  const expenseCategories = useMemo<CategoryL1[]>(() => {
    const expenseRows = allCategories.filter((c) => c.type === 'expense');
    const groups = new Map<string, { name: string; icon: string; children: string[] }>();
    for (const row of expenseRows) {
      if (!groups.has(row.name_l1)) {
        groups.set(row.name_l1, { name: row.name_l1, icon: row.icon, children: [] });
      }
      groups.get(row.name_l1)!.children.push(row.name_l2);
    }
    return Array.from(groups.values());
  }, [allCategories]);

  const incomeCategories = useMemo<CategoryL1[]>(() => {
    const incomeRows = allCategories.filter((c) => c.type === 'income');
    const groups = new Map<string, { name: string; icon: string; children: string[] }>();
    for (const row of incomeRows) {
      if (!groups.has(row.name_l1)) {
        groups.set(row.name_l1, { name: row.name_l1, icon: row.icon, children: [] });
      }
      groups.get(row.name_l1)!.children.push(row.name_l2);
    }
    return Array.from(groups.values());
  }, [allCategories]);

  const addCategory = useCallback(
    async (cat: { type: 'expense' | 'income'; name_l1: string; name_l2: string; icon: string }) => {
      await dbAddCategory(cat);
      await refreshCategories();
    },
    [refreshCategories]
  );

  const updateCategory = useCallback(
    async (id: number, updates: { name_l1?: string; name_l2?: string; icon?: string }) => {
      await dbUpdateCategory(id, updates);
      await refreshCategories();
    },
    [refreshCategories]
  );

  const deleteCategory = useCallback(
    async (id: number) => {
      await dbDeleteCategory(id);
      await refreshCategories();
    },
    [refreshCategories]
  );

  return (
    <CategoryContext.Provider
      value={{
        allCategories,
        expenseCategories,
        incomeCategories,
        loading,
        refreshCategories,
        addCategory,
        updateCategory,
        deleteCategory,
      }}
    >
      {children}
    </CategoryContext.Provider>
  );
}

// 获取分类数据的 hook
export function useCategoryContext(): CategoryContextValue {
  const ctx = useContext(CategoryContext);
  if (!ctx) {
    throw new Error('useCategoryContext 必须在 CategoryProvider 内部使用');
  }
  return ctx;
}

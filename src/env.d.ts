/// <reference types="vite/client" />

interface RecordData {
  type: 'expense' | 'income'
  amount: number
  date: string
  categoryL1: string
  categoryL2: string
  note: string
}

interface RecordItem extends RecordData {
  id: number
  created_at: string
}

interface MonthStats {
  income: number
  expense: number
  balance: number
}

// 分类数据（数据库平铺行结构）
interface Category {
  id: number
  type: 'expense' | 'income'
  name_l1: string
  name_l2: string
  icon: string
  sort_order: number
  is_deleted: number
}

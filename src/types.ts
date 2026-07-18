export type RecordType = 'income' | 'expense'

export interface RecordItem {
  id: number
  type: RecordType
  amount: number
  recordDate: string // YYYY-MM-DD
  categoryL1: string
  categoryL2: string
  note: string
  createdAt?: string
}

export interface MonthlyStats {
  income: number
  expense: number
  balance: number
}

export interface CategoryNode {
  id: number
  name: string
  type: RecordType
  icon: string | null
  system: boolean
  children: CategoryNode[]
}

// 分组后的大类结构（供下拉框 / 分类管理使用）
export interface CategoryL1 {
  name: string
  icon: string
  children: string[]
}

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

interface ElectronAPI {
  addRecord: (record: RecordData) => Promise<{ id: number }>
  getRecords: (filter?: { type?: string; month?: string }) => Promise<RecordItem[]>
  deleteRecord: (id: number) => Promise<{ success: boolean }>
  getMonthStats: (month: string) => Promise<MonthStats>
}

declare global {
  interface Window {
    electronAPI: ElectronAPI
  }
}

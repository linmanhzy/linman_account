import { contextBridge, ipcRenderer } from 'electron'

// 通过 contextBridge 安全地暴露 API 给渲染进程
contextBridge.exposeInMainWorld('electronAPI', {
  // 添加记录
  addRecord: (record: {
    type: 'expense' | 'income'
    amount: number
    date: string
    categoryL1: string
    categoryL2: string
    note: string
  }) => ipcRenderer.invoke('record:add', record),

  // 获取所有记录
  getRecords: (filter?: { type?: string; month?: string }) =>
    ipcRenderer.invoke('record:getAll', filter),

  // 删除记录
  deleteRecord: (id: number) => ipcRenderer.invoke('record:delete', id),

  // 获取月度统计
  getMonthStats: (month: string) => ipcRenderer.invoke('record:getMonthStats', month)
})

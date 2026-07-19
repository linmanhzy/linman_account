import client, { API_BASE, tokenStore } from './client'
import type { TrendPoint, CategoryProportion } from '../types'
import axios from 'axios'

// 消费趋势：最近 N 个月每月收入与支出
export async function getTrend(months = 12): Promise<TrendPoint[]> {
  return client.get<TrendPoint[]>('/api/reports/trend', { params: { months } })
}

// 分类占比：按一级分类统计支出占比；month 为空表示全部时间
export async function getCategoryProportion(params?: { month?: string }): Promise<CategoryProportion[]> {
  return client.get<CategoryProportion[]>('/api/reports/category-proportion', { params })
}

// 导出账本（Excel / CSV）。返回的是文件流，走独立的 axios 请求（不经过 JSON 解包拦截器）。
export async function exportRecords(format: 'excel' | 'csv'): Promise<void> {
  const token = tokenStore.get()
  const resp = await axios.post(`${API_BASE}/api/records/export`, null, {
    params: { format },
    responseType: 'blob',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  const blob = resp.data as Blob
  const url = window.URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = format === 'csv' ? '记账大王_records.csv' : '记账大王_records.xlsx'
  document.body.appendChild(a)
  a.click()
  a.remove()
  window.URL.revokeObjectURL(url)
}

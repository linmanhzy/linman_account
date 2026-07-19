import client from './client'
import type { RecordItem, MonthlyStats, RecordType, UserRecordQuota } from '../types'

export interface RecordPayload {
  type: RecordType
  amount: number
  recordDate: string
  categoryL1?: string
  categoryL2?: string
  note?: string
}

export async function getRecords(params?: { type?: string; month?: string }): Promise<RecordItem[]> {
  const data = await client.get<RecordItem[]>('/api/records', { params })
  return data
}

export async function createRecord(payload: RecordPayload): Promise<RecordItem> {
  const data = await client.post<RecordItem>('/api/records', payload)
  return data
}

export async function updateRecord(id: number, payload: RecordPayload): Promise<RecordItem> {
  const data = await client.put<RecordItem>(`/api/records/${id}`, payload)
  return data
}

export async function deleteRecord(id: number): Promise<void> {
  await client.delete(`/api/records/${id}`)
}

export async function getMonthlyStats(month: string): Promise<MonthlyStats> {
  const data = await client.get<MonthlyStats>('/api/records/stats/monthly', { params: { month } })
  return data
}

export async function getRecordQuota(): Promise<UserRecordQuota> {
  return client.get<UserRecordQuota>('/api/records/quota')
}

import client from './client'
import type { NotificationResponse, UnreadCountResponse } from '../types'

export async function getNotifications(): Promise<NotificationResponse[]> {
  return client.get<NotificationResponse[]>('/api/notifications')
}

export async function getUnreadCount(): Promise<UnreadCountResponse> {
  return client.get<UnreadCountResponse>('/api/notifications/unread-count')
}

export async function markRead(id: number): Promise<void> {
  return client.put<void>(`/api/notifications/${id}/read`)
}

export async function markAllRead(): Promise<void> {
  return client.put<void>('/api/notifications/read-all')
}

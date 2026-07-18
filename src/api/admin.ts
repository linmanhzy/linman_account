import client from './client'
import type { FeedbackResponse } from '../types'

export async function getAllFeedbacks(): Promise<FeedbackResponse[]> {
  return client.get<FeedbackResponse[]>('/api/admin/feedback')
}

export async function replyFeedback(id: number, reply: string): Promise<FeedbackResponse> {
  return client.put<FeedbackResponse>(`/api/admin/feedback/${id}/reply`, { reply })
}

export async function sendNotification(params: {
  title: string
  content: string
  targetUserId?: number | null
}): Promise<void> {
  return client.post<void>('/api/admin/notifications', params)
}

export async function getAllUsers(): Promise<{ id: number; username: string }[]> {
  return client.get<{ id: number; username: string }[]>('/api/admin/users/simple')
}

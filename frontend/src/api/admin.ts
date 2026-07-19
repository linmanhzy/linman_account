import client from './client'
import type { FeedbackResponse, UserSummary, CreateUserRequest, UpdateUserRequest, ResetPasswordRequest, ScheduledNotification, ScheduledNotificationRequest } from '../types'

// ===== 用户管理 =====

export async function getFullUserList(): Promise<UserSummary[]> {
  return client.get<UserSummary[]>('/api/admin/users')
}

export async function createUser(data: CreateUserRequest): Promise<UserSummary> {
  return client.post<UserSummary>('/api/admin/users', data)
}

export async function updateUser(id: number, data: UpdateUserRequest): Promise<UserSummary> {
  return client.put<UserSummary>(`/api/admin/users/${id}`, data)
}

export async function deleteUser(id: number): Promise<void> {
  return client.delete<void>(`/api/admin/users/${id}`)
}

export async function resetUserPassword(id: number, data: ResetPasswordRequest): Promise<void> {
  return client.put<void>(`/api/admin/users/${id}/password`, data)
}

export async function changeUserStatus(id: number, status: string): Promise<UserSummary> {
  return client.put<UserSummary>(`/api/admin/users/${id}/status`, { status })
}

// ===== 反馈管理 =====

export async function getAllFeedbacks(): Promise<FeedbackResponse[]> {
  return client.get<FeedbackResponse[]>('/api/admin/feedback')
}

export async function replyFeedback(id: number, reply: string): Promise<FeedbackResponse> {
  return client.put<FeedbackResponse>(`/api/admin/feedback/${id}/reply`, { reply })
}

// ===== 通知管理 =====

export async function sendNotification(params: {
  title: string
  content: string
  targetUserId?: number | null
}): Promise<void> {
  return client.post<void>('/api/admin/notifications', params)
}

// ===== 用户列表（通知选择器） =====

export async function getAllUsers(): Promise<{ id: number; username: string }[]> {
  return client.get<{ id: number; username: string }[]>('/api/admin/users/simple')
}

// ===== 定时通知管理 =====

export async function getScheduledNotifications(): Promise<ScheduledNotification[]> {
  return client.get<ScheduledNotification[]>('/api/admin/scheduled-notifications')
}

export async function createScheduledNotification(data: ScheduledNotificationRequest): Promise<ScheduledNotification> {
  return client.post<ScheduledNotification>('/api/admin/scheduled-notifications', data)
}

export async function updateScheduledNotification(id: number, data: ScheduledNotificationRequest): Promise<ScheduledNotification> {
  return client.put<ScheduledNotification>(`/api/admin/scheduled-notifications/${id}`, data)
}

export async function toggleScheduledNotification(id: number): Promise<ScheduledNotification> {
  return client.put<ScheduledNotification>(`/api/admin/scheduled-notifications/${id}/toggle`)
}

export async function deleteScheduledNotification(id: number): Promise<void> {
  return client.delete<void>(`/api/admin/scheduled-notifications/${id}`)
}

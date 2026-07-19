import client from './client'
import type { FeedbackResponse } from '../types'

export async function submitFeedback(content: string): Promise<FeedbackResponse> {
  return client.post<FeedbackResponse>('/api/feedback', { content })
}

export async function getMyFeedbacks(): Promise<FeedbackResponse[]> {
  return client.get<FeedbackResponse[]>('/api/feedback/my')
}

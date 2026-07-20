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

// 报表：消费趋势的一个月数据点
export interface TrendPoint {
  month: string // YYYY-MM
  income: number
  expense: number
}

// 报表：分类占比
export interface CategoryProportion {
  category: string
  amount: number
  percentage: number
}

// 记录条数与上限
export interface UserRecordQuota {
  count: number
  max: number
}

// 反馈
export interface FeedbackResponse {
  id: number
  content: string
  status: string // PENDING / REPLIED
  reply?: string
  createdAt: string
  userId: number
  username: string
}

// 通知
export interface NotificationResponse {
  id: number
  title: string
  content: string
  isRead: boolean
  type?: string  // WELCOME / DAILY / HOLIDAY / ADMIN
  createdAt: string
}

// 定时通知配置（管理员）
export type NotificationType = 'WELCOME' | 'DAILY' | 'HOLIDAY' | 'ADMIN'
export type Frequency = 'DAILY' | 'SPECIFIC_DATE' | 'ONCE'

export interface ScheduledNotification {
  id: number
  title: string
  content: string
  frequency: Frequency
  sendTime: string    // HH:mm:ss
  sendDate: string | null
  type: NotificationType
  enabled: boolean
  targetUserId: number | null
  lastFireDate: string | null
  createdAt: string
  updatedAt: string | null
}

export interface ScheduledNotificationRequest {
  title: string
  content: string
  frequency: Frequency
  sendTime: string
  sendDate?: string | null
  type?: NotificationType
  targetUserId?: number | null
}

export interface UnreadCountResponse {
  count: number
}

// 贪吃蛇游戏成绩
export interface GameScoreResponse {
  id: number
  score: number
  playedAt: string
}

export interface GameScoreSummary {
  bestScore: number
  recent: GameScoreResponse[]
}

export interface LeaderboardEntry {
  userId: number
  username: string
  bestScore: number
  me?: boolean
}

// 用户管理（管理员）
export interface UserSummary {
  id: number
  username: string
  role: 'ADMIN' | 'USER'
  status: 'ENABLED' | 'DISABLED'
  createdAt: string
}

export interface CreateUserRequest {
  username: string
  password: string
  role: 'USER' | 'ADMIN'
}

export interface UpdateUserRequest {
  username?: string
  role?: string
}

export interface ResetPasswordRequest {
  newPassword: string
}

export interface ChangeStatusRequest {
  status: string
}

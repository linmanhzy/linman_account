import client from './client'
import type { GameScoreResponse, GameScoreSummary, LeaderboardEntry } from '../types'

export async function submitGameScore(score: number): Promise<GameScoreResponse> {
  return client.post<GameScoreResponse>('/api/game/scores', { score })
}

export async function getMyGameSummary(): Promise<GameScoreSummary> {
  return client.get<GameScoreSummary>('/api/game/my')
}

export async function getLeaderboard(limit = 20): Promise<LeaderboardEntry[]> {
  return client.get<LeaderboardEntry[]>(`/api/game/leaderboard?limit=${limit}`)
}

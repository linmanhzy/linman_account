import React, { useRef, useState } from 'react'
import { Card, List, Tag, Spin } from 'antd'
import SnakeCanvas from '../components/SnakeCanvas'
import type { GameState } from '../components/SnakeCanvas'
import { useAuth } from '../auth/AuthContext'
import { submitGameScore, getMyGameSummary, getLeaderboard } from '../api/game'
import type { GameScoreSummary, LeaderboardEntry } from '../types'

const SnakeGame: React.FC = () => {
  const { username } = useAuth()
  const [score, setScore] = useState(0)
  const [gameState, setGameState] = useState<GameState>('idle')

  const [summary, setSummary] = useState<GameScoreSummary | null>(null)
  const [leaderboard, setLeaderboard] = useState<LeaderboardEntry[]>([])
  const [loading, setLoading] = useState(true)
  const [isNewRecord, setIsNewRecord] = useState(false)

  const scoreRef = useRef(0)
  const submittedRef = useRef(false)
  const prevBestRef = useRef(0)

  const refresh = async () => {
    try {
      const [s, lb] = await Promise.all([getMyGameSummary(), getLeaderboard(20)])
      prevBestRef.current = s.bestScore
      setSummary(s)
      setLeaderboard(lb)
    } catch (e) {
      console.error('加载成绩失败', e)
    } finally {
      setLoading(false)
    }
  }

  // 首次进入加载成绩与排行榜
  // eslint-disable-next-line react-hooks/exhaustive-deps
  React.useEffect(() => { refresh() }, [])

  const handleScoreChange = (s: number) => {
    scoreRef.current = s
    setScore(s)
  }

  const handleStateChange = (state: GameState) => {
    setGameState(state)
    if (state === 'running') {
      // 新一局开始，重置提交标记与新纪录提示
      submittedRef.current = false
      setIsNewRecord(false)
    }
    if ((state === 'gameover' || state === 'win') && !submittedRef.current) {
      submittedRef.current = true
      const finalScore = scoreRef.current
      submitGameScore(finalScore)
        .then(() => {
          setIsNewRecord(finalScore > prevBestRef.current)
          refresh()
        })
        .catch((e) => console.error('保存成绩失败', e))
    }
  }

  const getScoreTip = (): string => {
    switch (gameState) {
      case 'idle':
        return '按空格键开始游戏'
      case 'running':
        return `当前得分：${score}`
      case 'paused':
        return '暂停中 — 按空格键继续'
      case 'gameover':
        return `游戏结束！最终得分：${score}`
      case 'win':
        return `🎉 恭喜通关！最终得分：${score}`
    }
  }

  return (
    <div>
      <h2 style={{ marginBottom: 24, fontSize: 22 }}>贪吃蛇</h2>
      <div style={{ display: 'flex', gap: 24, flexWrap: 'wrap', alignItems: 'flex-start' }}>
        {/* 左：游戏区 */}
        <Card
          style={{ borderRadius: 12, maxWidth: 500 }}
          styles={{ body: { textAlign: 'center' } }}
        >
          <div
            style={{
              marginBottom: 12,
              fontSize: 16,
              fontWeight: 600,
              color: gameState === 'gameover' ? '#ff4d4f' : '#333',
            }}
          >
            {getScoreTip()}
            {isNewRecord && (
              <Tag color="gold" style={{ marginLeft: 8 }}>
                🏆 新纪录！
              </Tag>
            )}
          </div>

          <SnakeCanvas
            onScoreChange={handleScoreChange}
            onGameStateChange={handleStateChange}
          />

          <div style={{ marginTop: 16, color: '#999', fontSize: 13, lineHeight: 2 }}>
            <div>↑↓←→ 或 WASD — 控制方向</div>
            <div>空格 — 开始 / 暂停 / 继续 &nbsp;|&nbsp; P — 暂停</div>
          </div>
        </Card>

        {/* 右：成绩面板 */}
        <div style={{ flex: 1, minWidth: 280, maxWidth: 360 }}>
          <Card title="我的成绩" style={{ borderRadius: 12, marginBottom: 24 }}>
            {loading ? (
              <Spin />
            ) : (
              <>
                <div style={{ fontSize: 14, color: '#666' }}>历史最高分</div>
                <div
                  style={{
                    fontSize: 32,
                    fontWeight: 700,
                    color: '#1677ff',
                    margin: '4px 0 16px',
                  }}
                >
                  {summary?.bestScore ?? 0}
                </div>
                <div style={{ fontSize: 14, color: '#666', marginBottom: 8 }}>最近 5 次</div>
                {summary && summary.recent.length > 0 ? (
                  <List
                    size="small"
                    dataSource={summary.recent}
                    renderItem={(item, idx) => (
                      <List.Item key={item.id}>
                        <span style={{ color: '#999', width: 20 }}>{idx + 1}.</span>
                        <span style={{ fontWeight: 600 }}>{item.score} 分</span>
                        <span style={{ marginLeft: 'auto', color: '#bbb', fontSize: 12 }}>
                          {new Date(item.playedAt).toLocaleString('zh-CN', { hour12: false })}
                        </span>
                      </List.Item>
                    )}
                  />
                ) : (
                  <div style={{ color: '#bbb', fontSize: 13 }}>
                    还没有成绩，快去玩一局吧！
                  </div>
                )}
              </>
            )}
          </Card>

          <Card title="🏆 排行榜" style={{ borderRadius: 12 }}>
            {loading ? (
              <Spin />
            ) : leaderboard.length > 0 ? (
              <List
                size="small"
                dataSource={leaderboard}
                renderItem={(item, idx) => {
                  const me = !!username && item.username === username
                  return (
                    <List.Item
                      key={item.userId}
                      style={me ? { background: '#fffbe6', borderRadius: 6 } : undefined}
                    >
                      <span
                        style={{
                          width: 24,
                          color: idx < 3 ? '#fa8c16' : '#999',
                          fontWeight: 600,
                        }}
                      >
                        {idx + 1}
                      </span>
                      <span style={{ fontWeight: me ? 700 : 400 }}>
                        {item.username}
                        {me ? '（我）' : ''}
                      </span>
                      <span style={{ marginLeft: 'auto', fontWeight: 600 }}>
                        {item.bestScore} 分
                      </span>
                    </List.Item>
                  )
                }}
              />
            ) : (
              <div style={{ color: '#bbb', fontSize: 13 }}>
                还没有人上榜，快来抢占榜首！
              </div>
            )}
          </Card>
        </div>
      </div>
    </div>
  )
}

export default SnakeGame

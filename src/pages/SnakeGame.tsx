import React, { useRef, useState } from 'react'
import { ConfigProvider, theme, Spin } from 'antd'
import SnakeCanvas from '../components/SnakeCanvas'
import type { GameState } from '../components/SnakeCanvas'
import { useAuth } from '../auth/AuthContext'
import { submitGameScore, getMyGameSummary, getLeaderboard } from '../api/game'
import type { GameScoreSummary, LeaderboardEntry } from '../types'
import './SnakeGame.css'

const darkTheme = {
  algorithm: theme.darkAlgorithm,
  token: { colorPrimary: '#00D2FF', colorBgElevated: '#1A1A2E' },
}

const rankClass = (idx: number) =>
  idx === 0 ? 'gold' : idx === 1 ? 'silver' : idx === 2 ? 'bronze' : 'normal'

const tipColor: Record<GameState, string> = {
  idle: '#00D2FF',
  running: '#00D2FF',
  paused: '#A0A0B8',
  gameover: '#FF4D4F',
  win: '#F59E0B',
}

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
    <ConfigProvider theme={darkTheme}>
      <div className="snake-stage">
        <div className="snake-header">
          <h1 className="snake-title">
            贪吃蛇 <span className="snake-title-accent">ARCADE</span>
          </h1>
          <p className="snake-sub">
            方向键或 WASD 操控，吃豆成长。每一局成绩都会记入你的战绩与全球排行榜。
          </p>
        </div>

        <div className="snake-grid">
          {/* 左：游戏区（流光霓虹边框） */}
          <div className="neon-frame">
            <div className="neon-frame-inner">
              <div className="score-tip" style={{ color: tipColor[gameState] }}>
                {getScoreTip()}
                {isNewRecord && <span className="record-badge">🏆 新纪录！</span>}
              </div>

              <SnakeCanvas
                onScoreChange={handleScoreChange}
                onGameStateChange={handleStateChange}
              />

              <div className="controls-hint">
                <div>↑ ↓ ← → 或 W A S D — 控制方向</div>
                <div>空格 — 开始 / 暂停 / 继续 &nbsp;|&nbsp; P — 暂停</div>
              </div>
            </div>
          </div>

          {/* 右：战绩 + 排行榜 */}
          <div>
            {/* 我的战绩 */}
            <section className="glass-panel">
              <h3 className="panel-title">我的战绩</h3>
              {loading ? (
                <div style={{ textAlign: 'center', padding: 24 }}>
                  <Spin />
                </div>
              ) : (
                <>
                  <div className="label-muted">历史最高分</div>
                  <div className="score-hero">{summary?.bestScore ?? 0}</div>
                  <div className="label-muted" style={{ marginBottom: 8 }}>
                    最近 5 次
                  </div>
                  {summary && summary.recent.length > 0 ? (
                    <div>
                      {summary.recent.map((item, idx) => (
                        <div className="recent-item" key={item.id}>
                          <span className="recent-idx">{idx + 1}</span>
                          <span className="recent-score">{item.score} 分</span>
                          <span className="recent-time">
                            {new Date(item.playedAt).toLocaleString('zh-CN', { hour12: false })}
                          </span>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <div className="empty-hint">还没有成绩，快去玩一局吧！</div>
                  )}
                </>
              )}
            </section>

            {/* 全球排行榜 */}
            <section className="glass-panel">
              <h3 className="panel-title">🏆 全球排行榜</h3>
              {loading ? (
                <div style={{ textAlign: 'center', padding: 24 }}>
                  <Spin />
                </div>
              ) : leaderboard.length > 0 ? (
                <div>
                  {leaderboard.map((item, idx) => {
                    const me = !!username && item.username === username
                    return (
                      <div className={`lb-row${me ? ' me' : ''}`} key={item.userId}>
                        <span className={`lb-rank ${rankClass(idx)}`}>{idx + 1}</span>
                        <span className={`lb-name${me ? ' me' : ''}`}>
                          {item.username}
                          {me ? '（我）' : ''}
                        </span>
                        <span className="lb-score">{item.bestScore} 分</span>
                      </div>
                    )
                  })}
                </div>
              ) : (
                <div className="empty-hint">还没有人上榜，快来抢占榜首！</div>
              )}
            </section>
          </div>
        </div>
      </div>
    </ConfigProvider>
  )
}

export default SnakeGame

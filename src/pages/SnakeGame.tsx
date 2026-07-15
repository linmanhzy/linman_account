import React, { useState } from 'react'
import { Card } from 'antd'
import SnakeCanvas from '../components/SnakeCanvas'
import type { GameState } from '../components/SnakeCanvas'

const SnakeGame: React.FC = () => {
  const [score, setScore] = useState(0)
  const [gameState, setGameState] = useState<GameState>('idle')

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
      <Card
        style={{ borderRadius: 12, maxWidth: 500 }}
        styles={{ body: { textAlign: 'center' } }}
      >
        {/* 分数提示 */}
        <div
          style={{
            marginBottom: 12,
            fontSize: 16,
            fontWeight: 600,
            color: gameState === 'gameover' ? '#ff4d4f' : '#333',
          }}
        >
          {getScoreTip()}
        </div>

        {/* 游戏画布 */}
        <SnakeCanvas
          onScoreChange={setScore}
          onGameStateChange={setGameState}
        />

        {/* 操作提示 */}
        <div style={{ marginTop: 16, color: '#999', fontSize: 13, lineHeight: 2 }}>
          <div>↑↓←→ 或 WASD — 控制方向</div>
          <div>空格 — 开始 / 暂停 / 继续 &nbsp;|&nbsp; P — 暂停</div>
        </div>
      </Card>
    </div>
  )
}

export default SnakeGame

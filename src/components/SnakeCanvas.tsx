import React, { useEffect, useRef } from 'react'

// ====== 游戏常量 ======
const CELL_SIZE = 20       // 每格像素
const GRID_COUNT = 20      // 网格数（20×20）
const CANVAS_SIZE = CELL_SIZE * GRID_COUNT // 画布总尺寸 400px
const INITIAL_SPEED = 150  // 初始速度（毫秒/格）
const MIN_SPEED = 60       // 最快速度
const SPEED_STEP = 8       // 每次加速减少的毫秒数
const SPEED_EVERY = 5      // 每吃 N 个食物加速一次
const SCORE_PER_FOOD = 10  // 每个食物得分

// ====== 类型定义 ======
type GameState = 'idle' | 'running' | 'paused' | 'gameover' | 'win'

interface Point {
  x: number
  y: number
}

interface Props {
  onScoreChange: (score: number) => void
  onGameStateChange: (state: GameState) => void
}

// ====== 生成食物位置（避开蛇身） ======
function generateFood(snake: Point[]): Point | null {
  for (let attempt = 0; attempt < 100; attempt++) {
    const x = Math.floor(Math.random() * GRID_COUNT)
    const y = Math.floor(Math.random() * GRID_COUNT)
    if (!snake.some(seg => seg.x === x && seg.y === y)) {
      return { x, y }
    }
  }
  return null // 网格已满，蛇赢了
}

const SnakeCanvas: React.FC<Props> = ({ onScoreChange, onGameStateChange }) => {
  const canvasRef = useRef<HTMLCanvasElement>(null)

  // 游戏状态全部用 ref，避免触发 React 重渲染
  const snakeRef = useRef<Point[]>([])
  const foodRef = useRef<Point | null>(null)
  const directionRef = useRef<Point>({ x: 1, y: 0 })
  const nextDirRef = useRef<Point>({ x: 1, y: 0 })
  const gameStateRef = useRef<GameState>('idle')
  const scoreRef = useRef(0)
  const foodEatenRef = useRef(0)
  const speedRef = useRef(INITIAL_SPEED)
  const lastTickRef = useRef(0)
  const rafIdRef = useRef(0)
  const dprRef = useRef(1) // 设备像素比，高分屏缩放用

  // 保持回调 ref 最新，避免闭包过期
  const onScoreRef = useRef(onScoreChange)
  const onStateRef = useRef(onGameStateChange)
  onScoreRef.current = onScoreChange
  onStateRef.current = onGameStateChange

  // ====== 绘制函数 ======
  const draw = () => {
    const canvas = canvasRef.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return

    const snake = snakeRef.current
    const food = foodRef.current
    const state = gameStateRef.current
    const dir = directionRef.current

    // 背景
    ctx.fillStyle = '#1a1a2e'
    ctx.fillRect(0, 0, CANVAS_SIZE, CANVAS_SIZE)

    // 网格线
    ctx.strokeStyle = '#16213e'
    ctx.lineWidth = 0.5
    for (let i = 0; i <= GRID_COUNT; i++) {
      const pos = i * CELL_SIZE
      ctx.beginPath()
      ctx.moveTo(pos, 0)
      ctx.lineTo(pos, CANVAS_SIZE)
      ctx.stroke()
      ctx.beginPath()
      ctx.moveTo(0, pos)
      ctx.lineTo(CANVAS_SIZE, pos)
      ctx.stroke()
    }

    // 食物（红色圆点，带发光效果）
    if (food) {
      const fx = food.x * CELL_SIZE + CELL_SIZE / 2
      const fy = food.y * CELL_SIZE + CELL_SIZE / 2
      ctx.shadowColor = '#ff4d4f'
      ctx.shadowBlur = 8
      ctx.fillStyle = '#ff4d4f'
      ctx.beginPath()
      ctx.arc(fx, fy, CELL_SIZE / 2 - 2, 0, Math.PI * 2)
      ctx.fill()
      ctx.shadowBlur = 0
    }

    // 蛇身
    snake.forEach((seg, i) => {
      const px = seg.x * CELL_SIZE + 1
      const py = seg.y * CELL_SIZE + 1
      const size = CELL_SIZE - 2

      if (i === 0) {
        // 蛇头 — 亮色
        ctx.fillStyle = '#00d2ff'
      } else {
        // 蛇身 — 渐深
        const ratio = i / Math.max(snake.length - 1, 1)
        const r = 0
        const g = Math.floor(210 - ratio * 160)
        const b = Math.floor(255 - ratio * 150)
        ctx.fillStyle = `rgb(${r},${g},${b})`
      }

      // 圆角矩形
      const radius = 4
      ctx.beginPath()
      ctx.moveTo(px + radius, py)
      ctx.lineTo(px + size - radius, py)
      ctx.quadraticCurveTo(px + size, py, px + size, py + radius)
      ctx.lineTo(px + size, py + size - radius)
      ctx.quadraticCurveTo(px + size, py + size, px + size - radius, py + size)
      ctx.lineTo(px + radius, py + size)
      ctx.quadraticCurveTo(px, py + size, px, py + size - radius)
      ctx.lineTo(px, py + radius)
      ctx.quadraticCurveTo(px, py, px + radius, py)
      ctx.closePath()
      ctx.fill()

      // 蛇头眼睛
      if (i === 0) {
        ctx.fillStyle = '#fff'
        const eyeSize = 3
        const eyeOffset = 5
        // 根据方向调整眼睛位置
        let eye1: Point, eye2: Point
        if (dir.x === 1) { // 向右
          eye1 = { x: px + size - eyeOffset, y: py + 5 }
          eye2 = { x: px + size - eyeOffset, y: py + size - 5 }
        } else if (dir.x === -1) { // 向左
          eye1 = { x: px + eyeOffset, y: py + 5 }
          eye2 = { x: px + eyeOffset, y: py + size - 5 }
        } else if (dir.y === -1) { // 向上
          eye1 = { x: px + 5, y: py + eyeOffset }
          eye2 = { x: px + size - 5, y: py + eyeOffset }
        } else { // 向下
          eye1 = { x: px + 5, y: py + size - eyeOffset }
          eye2 = { x: px + size - 5, y: py + size - eyeOffset }
        }
        ctx.beginPath()
        ctx.arc(eye1.x, eye1.y, eyeSize, 0, Math.PI * 2)
        ctx.fill()
        ctx.beginPath()
        ctx.arc(eye2.x, eye2.y, eyeSize, 0, Math.PI * 2)
        ctx.fill()

        // 瞳孔
        ctx.fillStyle = '#111'
        ctx.beginPath()
        ctx.arc(eye1.x + dir.x * 1.5, eye1.y + dir.y * 1.5, 1.5, 0, Math.PI * 2)
        ctx.fill()
        ctx.beginPath()
        ctx.arc(eye2.x + dir.x * 1.5, eye2.y + dir.y * 1.5, 1.5, 0, Math.PI * 2)
        ctx.fill()
      }
    })

    // 非运行状态遮罩
    if (state !== 'running' && state !== 'paused') {
      ctx.fillStyle = 'rgba(0,0,0,0.6)'
      ctx.fillRect(0, 0, CANVAS_SIZE, CANVAS_SIZE)

      ctx.fillStyle = '#fff'
      ctx.font = 'bold 22px "Microsoft YaHei", sans-serif'
      ctx.textAlign = 'center'
      ctx.textBaseline = 'middle'

      let message = ''
      let subMsg = ''
      switch (state) {
        case 'idle':
          message = '🐍 贪吃蛇'
          subMsg = '按空格键开始游戏'
          break
        case 'gameover':
          message = `游戏结束！得分：${scoreRef.current}`
          subMsg = '按空格键重新开始'
          break
        case 'win':
          message = `🎉 你赢了！得分：${scoreRef.current}`
          subMsg = '按空格键重新开始'
          break
      }

      ctx.fillText(message, CANVAS_SIZE / 2, CANVAS_SIZE / 2 - 16)
      ctx.font = '14px "Microsoft YaHei", sans-serif'
      ctx.fillStyle = '#aaa'
      ctx.fillText(subMsg, CANVAS_SIZE / 2, CANVAS_SIZE / 2 + 20)
    }

    // 暂停状态遮罩
    if (state === 'paused') {
      ctx.fillStyle = 'rgba(0,0,0,0.4)'
      ctx.fillRect(0, 0, CANVAS_SIZE, CANVAS_SIZE)
      ctx.fillStyle = '#fff'
      ctx.font = 'bold 22px "Microsoft YaHei", sans-serif'
      ctx.textAlign = 'center'
      ctx.textBaseline = 'middle'
      ctx.fillText('暂停中', CANVAS_SIZE / 2, CANVAS_SIZE / 2 - 10)
      ctx.font = '14px "Microsoft YaHei", sans-serif'
      ctx.fillStyle = '#aaa'
      ctx.fillText('按空格键继续', CANVAS_SIZE / 2, CANVAS_SIZE / 2 + 16)
    }
  }

  // ====== 初始化新游戏 ======
  const initGame = () => {
    // 蛇从中间偏左开始，水平三节，向右移动
    const startX = Math.floor(GRID_COUNT / 2)
    const startY = Math.floor(GRID_COUNT / 2)
    snakeRef.current = [
      { x: startX, y: startY },
      { x: startX - 1, y: startY },
      { x: startX - 2, y: startY },
    ]
    directionRef.current = { x: 1, y: 0 }
    nextDirRef.current = { x: 1, y: 0 }
    scoreRef.current = 0
    foodEatenRef.current = 0
    speedRef.current = INITIAL_SPEED
    onScoreRef.current(0)
    lastTickRef.current = performance.now() // 重置计时，防止新游戏首帧瞬移

    const food = generateFood(snakeRef.current)
    if (food) {
      foodRef.current = food
    }

    gameStateRef.current = 'running'
    onStateRef.current('running')
  }

  // ====== 游戏主循环 ======
  const gameLoop = (timestamp: number) => {
    rafIdRef.current = requestAnimationFrame(gameLoop)

    if (gameStateRef.current !== 'running') {
      draw()
      return
    }

    // 速度控制 — 到时间才走一格
    if (timestamp - lastTickRef.current < speedRef.current) {
      draw()
      return
    }
    lastTickRef.current = timestamp

    // 应用方向（防 180° 掉头）
    const curDir = directionRef.current
    const nextDir = nextDirRef.current
    if (nextDir.x !== -curDir.x || nextDir.y !== -curDir.y) {
      directionRef.current = { ...nextDir }
    }

    const dir = directionRef.current
    const head = snakeRef.current[0]
    const newHead: Point = { x: head.x + dir.x, y: head.y + dir.y }

    // 撞墙检测
    if (newHead.x < 0 || newHead.x >= GRID_COUNT || newHead.y < 0 || newHead.y >= GRID_COUNT) {
      gameStateRef.current = 'gameover'
      onStateRef.current('gameover')
      draw()
      return
    }

    // 撞自己检测（未吃到食物时排除蛇尾，因为蛇尾即将被移除）
    const willEat = foodRef.current !== null
      && newHead.x === foodRef.current.x
      && newHead.y === foodRef.current.y
    const bodyToCheck = willEat ? snakeRef.current : snakeRef.current.slice(0, -1)
    if (bodyToCheck.some(seg => seg.x === newHead.x && seg.y === newHead.y)) {
      gameStateRef.current = 'gameover'
      onStateRef.current('gameover')
      draw()
      return
    }

    // 移动蛇
    snakeRef.current = [newHead, ...snakeRef.current]

    // 吃食物判断
    if (willEat) {
      scoreRef.current += SCORE_PER_FOOD
      foodEatenRef.current += 1
      onScoreRef.current(scoreRef.current)

      // 加速
      if (foodEatenRef.current % SPEED_EVERY === 0) {
        speedRef.current = Math.max(MIN_SPEED, speedRef.current - SPEED_STEP)
      }

      // 生成新食物
      const food = generateFood(snakeRef.current)
      if (food) {
        foodRef.current = food
      } else {
        // 没有空位 — 玩家胜利
        gameStateRef.current = 'win'
        onStateRef.current('win')
        draw()
        return
      }
    } else {
      // 没吃到食物 — 去尾
      snakeRef.current.pop()
    }

    draw()
  }

  // ====== 键盘事件处理 ======
  const handleKeyDown = (e: KeyboardEvent) => {
    const state = gameStateRef.current

    // 空格：开始 / 暂停 / 继续
    if (e.key === ' ') {
      e.preventDefault()
      if (state === 'idle' || state === 'gameover' || state === 'win') {
        initGame()
      } else if (state === 'running') {
        gameStateRef.current = 'paused'
        onStateRef.current('paused')
      } else if (state === 'paused') {
        lastTickRef.current = performance.now() // 重置计时防止瞬移
        gameStateRef.current = 'running'
        onStateRef.current('running')
      }
      return
    }

    // P：暂停/继续
    if (e.key === 'p' || e.key === 'P') {
      if (state === 'running') {
        gameStateRef.current = 'paused'
        onStateRef.current('paused')
      } else if (state === 'paused') {
        lastTickRef.current = performance.now()
        gameStateRef.current = 'running'
        onStateRef.current('running')
      }
      return
    }

    if (state !== 'running') return

    // 方向键 / WASD
    switch (e.key) {
      case 'ArrowUp':
      case 'w':
      case 'W':
        e.preventDefault()
        if (directionRef.current.y !== 1) nextDirRef.current = { x: 0, y: -1 }
        break
      case 'ArrowDown':
      case 's':
      case 'S':
        e.preventDefault()
        if (directionRef.current.y !== -1) nextDirRef.current = { x: 0, y: 1 }
        break
      case 'ArrowLeft':
      case 'a':
      case 'A':
        e.preventDefault()
        if (directionRef.current.x !== 1) nextDirRef.current = { x: -1, y: 0 }
        break
      case 'ArrowRight':
      case 'd':
      case 'D':
        e.preventDefault()
        if (directionRef.current.x !== -1) nextDirRef.current = { x: 1, y: 0 }
        break
    }
  }

  // ====== 生命周期 ======
  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return

    // 高分屏适配：canvas 内部分辨率按 devicePixelRatio 放大，CSS 尺寸不变
    const dpr = window.devicePixelRatio || 1
    dprRef.current = dpr
    canvas.width = CANVAS_SIZE * dpr
    canvas.height = CANVAS_SIZE * dpr
    canvas.style.width = `${CANVAS_SIZE}px`
    canvas.style.height = `${CANVAS_SIZE}px`
    const ctx = canvas.getContext('2d')
    if (ctx) ctx.scale(dpr, dpr)

    window.addEventListener('keydown', handleKeyDown)
    lastTickRef.current = performance.now()
    rafIdRef.current = requestAnimationFrame(gameLoop)

    return () => {
      window.removeEventListener('keydown', handleKeyDown)
      cancelAnimationFrame(rafIdRef.current)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return (
    <canvas
      ref={canvasRef}
      style={{
        display: 'block',
        margin: '0 auto',
        borderRadius: 8,
        cursor: 'pointer',
      }}
      tabIndex={0}
    />
  )
}

export default SnakeCanvas
export type { GameState }

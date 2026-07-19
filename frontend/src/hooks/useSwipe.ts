import { useRef, useCallback, type TouchEvent } from 'react'

export type SwipeDirection = 'up' | 'down' | 'left' | 'right'

interface UseSwipeOptions {
  /** 滑动超过阈值后回调方向 */
  onSwipe?: (dir: SwipeDirection) => void
  /** 短距离轻触（点击）回调，用于开始 / 暂停 */
  onTap?: () => void
  /** 触发方向判定的最小位移（px），防误触 */
  threshold?: number
}

/**
 * 触屏滑动手势 hook：在绑定元素上监听 touchstart/touchend，
 * 根据位移主轴方向回调 onSwipe，短距离轻触回调 onTap。
 * 用阈值与短时长区分「滑动」与「点按」，避免误触。
 */
export function useSwipe({ onSwipe, onTap, threshold = 24 }: UseSwipeOptions) {
  const startRef = useRef<{ x: number; y: number; t: number } | null>(null)

  const onTouchStart = useCallback((e: TouchEvent) => {
    const t = e.touches[0]
    if (!t) return
    startRef.current = { x: t.clientX, y: t.clientY, t: Date.now() }
  }, [])

  const onTouchEnd = useCallback(
    (e: TouchEvent) => {
      const start = startRef.current
      startRef.current = null
      if (!start) return
      const touch = e.changedTouches[0]
      if (!touch) return

      const dx = touch.clientX - start.x
      const dy = touch.clientY - start.y
      const adx = Math.abs(dx)
      const ady = Math.abs(dy)
      const dt = Date.now() - start.t

      // 位移很小 → 视为点按
      if (adx < threshold && ady < threshold) {
        if (dt < 300) onTap?.()
        return
      }
      // 主轴判定方向
      if (adx > ady) {
        onSwipe?.(dx > 0 ? 'right' : 'left')
      } else {
        onSwipe?.(dy > 0 ? 'down' : 'up')
      }
    },
    [onSwipe, onTap, threshold],
  )

  return { onTouchStart, onTouchEnd }
}

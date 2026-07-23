import { resolveApiBase, showFatalErrorOverlay } from './apiBase'

describe('resolveApiBase', () => {
  describe('VITE_API_BASE 已设置（核心路径）', () => {
    it('返回配置的服务器地址', () => {
      const result = resolveApiBase('http://47.104.152.25:8080', false)
      expect(result).toBe('http://47.104.152.25:8080')
    })

    it('返回局域网 IP 地址', () => {
      const result = resolveApiBase('http://192.168.1.100:8080', false)
      expect(result).toBe('http://192.168.1.100:8080')
    })

    it('DEV=true 时优先用配置值而非 localhost', () => {
      const result = resolveApiBase('http://47.104.152.25:8080', true)
      expect(result).toBe('http://47.104.152.25:8080')
    })
  })

  describe('VITE_API_BASE 未设置 — DEV 模式', () => {
    it('返回本地开发默认地址', () => {
      const result = resolveApiBase(undefined, true)
      expect(result).toBe('http://127.0.0.1:8080')
    })

    it('空字符串也回落到本地', () => {
      const result = resolveApiBase('', true)
      expect(result).toBe('http://127.0.0.1:8080')
    })
  })

  describe('VITE_API_BASE 未设置 — PROD 模式（修复 Invalid URL 根因）', () => {
    it('undefined 时抛出明确错误', () => {
      expect(() => resolveApiBase(undefined, false)).toThrow()
    })

    it('空字符串时也抛出错误', () => {
      expect(() => resolveApiBase('', false)).toThrow()
    })

    it('错误信息包含中文 "后端地址"', () => {
      try {
        resolveApiBase(undefined, false)
        expect.fail('应该抛出错误')
      } catch (e: any) {
        expect(e.message).toContain('后端地址')
      }
    })

    it('错误信息包含 "VITE_API_BASE"', () => {
      try {
        resolveApiBase('', false)
        expect.fail('应该抛出错误')
      } catch (e: any) {
        expect(e.message).toContain('VITE_API_BASE')
      }
    })

    it('错误信息包含默认服务器地址', () => {
      try {
        resolveApiBase('', false)
        expect.fail('应该抛出错误')
      } catch (e: any) {
        expect(e.message).toContain('47.104.152.25')
      }
    })
  })

  describe('边界情况', () => {
    it('带尾部斜杠保留原样', () => {
      expect(resolveApiBase('http://47.104.152.25:8080/', false)).toBe('http://47.104.152.25:8080/')
    })

    it('仅空格的字符串 PROD 模式抛错', () => {
      expect(() => resolveApiBase('   ', false)).toThrow()
    })

    it('前后有空格 trim 后正常返回', () => {
      expect(resolveApiBase('  http://47.104.152.25:8080  ', false)).toBe('http://47.104.152.25:8080')
    })
  })
})

describe('showFatalErrorOverlay', () => {
  it('注入红色错误覆盖层到 <body>', () => {
    document.body.innerHTML = ''
    showFatalErrorOverlay('测试错误信息')
    const el = document.getElementById('__linman_fatal_error__')
    expect(el).not.toBeNull()
    expect(el!.textContent).toContain('测试错误信息')
    expect(el!.style.background).toBe('rgb(168, 7, 26)')
  })

  it('覆盖层必须设置 maxHeight，避免长报错占满整个屏幕', () => {
    document.body.innerHTML = ''
    showFatalErrorOverlay('A'.repeat(5000))
    const el = document.getElementById('__linman_fatal_error__')
    expect(el).not.toBeNull()
    const maxHeight = el!.style.maxHeight
    expect(maxHeight).toBeTruthy()
    expect(maxHeight).not.toBe('')
    // max-height 必须含有 vh 或 px 单位，不能无限长
    expect(/vh|px|em|rem/.test(maxHeight)).toBe(true)
  })

  it('覆盖层超出 maxHeight 时内容可滚动（overflowY=auto/scroll）', () => {
    document.body.innerHTML = ''
    showFatalErrorOverlay('A'.repeat(5000))
    const el = document.getElementById('__linman_fatal_error__')
    expect(el).not.toBeNull()
    const overflowY = el!.style.overflowY
    expect(['auto', 'scroll']).toContain(overflowY)
  })

  it('覆盖层必须包含关闭按钮，用户可以关闭', () => {
    document.body.innerHTML = ''
    showFatalErrorOverlay('测试错误信息')
    const el = document.getElementById('__linman_fatal_error__')
    expect(el).not.toBeNull()
    const closeBtn = el!.querySelector('[data-linman-close]') as HTMLElement
    expect(closeBtn).not.toBeNull()
  })

  it('点击关闭按钮后覆盖层应被移除（DOM 中不再存在）', () => {
    document.body.innerHTML = ''
    showFatalErrorOverlay('测试错误信息')
    const el = document.getElementById('__linman_fatal_error__')
    const closeBtn = el!.querySelector('[data-linman-close]') as HTMLButtonElement
    expect(closeBtn).not.toBeNull()
    closeBtn.click()
    expect(document.getElementById('__linman_fatal_error__')).toBeNull()
  })
})

import { app, BrowserWindow, ipcMain } from 'electron'
import { join } from 'path'
import { initDatabase, queryAll, run } from './database'

let mainWindow: BrowserWindow | null = null

function createWindow(): void {
  mainWindow = new BrowserWindow({
    width: 1100,
    height: 720,
    minWidth: 900,
    minHeight: 600,
    title: '林蛮记账',
    webPreferences: {
      preload: join(__dirname, '../preload/index.js'),
      sandbox: false
    }
  })

  // 开发模式加载 Vite 服务，生产模式加载打包后的文件
  if (process.env.ELECTRON_RENDERER_URL) {
    mainWindow.loadURL(process.env.ELECTRON_RENDERER_URL)
  } else {
    mainWindow.loadFile(join(__dirname, '../renderer/index.html'))
  }
}

app.whenReady().then(async () => {
  // 初始化数据库
  await initDatabase()

  // 注册 IPC 通信
  registerIpcHandlers()

  // 创建窗口
  createWindow()

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow()
    }
  })
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit()
  }
})

// ====== IPC 通信处理 ======

function registerIpcHandlers(): void {
  // 添加一条记录
  ipcMain.handle('record:add', (_event, record: {
    type: 'expense' | 'income'
    amount: number
    date: string
    categoryL1: string
    categoryL2: string
    note: string
  }) => {
    const result = run(
      `INSERT INTO records (type, amount, date, category_l1, category_l2, note)
       VALUES (?, ?, ?, ?, ?, ?)`,
      [record.type, record.amount, record.date, record.categoryL1, record.categoryL2, record.note || '']
    )
    return { id: result.lastInsertRowid }
  })

  // 获取所有记录
  ipcMain.handle('record:getAll', (_event, filter?: { type?: string; month?: string }) => {
    let sql = 'SELECT * FROM records WHERE 1=1'
    const params: any[] = []

    if (filter?.type) {
      sql += ' AND type = ?'
      params.push(filter.type)
    }

    if (filter?.month) {
      sql += ' AND date LIKE ?'
      params.push(`${filter.month}%`)
    }

    sql += ' ORDER BY date DESC, id DESC'
    return queryAll(sql, params)
  })

  // 删除一条记录
  ipcMain.handle('record:delete', (_event, id: number) => {
    run('DELETE FROM records WHERE id = ?', [id])
    return { success: true }
  })

  // 获取月度统计
  ipcMain.handle('record:getMonthStats', (_event, month: string) => {
    const results = queryAll(
      `SELECT type, SUM(amount) as total
       FROM records
       WHERE date LIKE ?
       GROUP BY type`,
      [`${month}%`]
    )

    let income = 0
    let expense = 0
    results.forEach((s: any) => {
      if (s.type === 'income') income = s.total
      if (s.type === 'expense') expense = s.total
    })

    return { income, expense, balance: income - expense }
  })
}

import { app } from 'electron'
import { join } from 'path'
import * as fs from 'fs'

// sql.js 的类型（避免直接依赖 Node.js path 等）
let db: any = null
let SQL: any = null
let dbPath = ''

// 保存数据库到文件
function saveToFile(): void {
  if (!db) return
  const data = db.export()
  const buffer = Buffer.from(data)
  fs.writeFileSync(dbPath, buffer)
}

// 初始化数据库
export async function initDatabase(): Promise<void> {
  // 数据库文件路径（必须在 app ready 后获取）
  dbPath = join(app.getPath('userData'), 'linman-account-book.db')

  // 动态加载 sql.js
  const initSqlJs = require('sql.js')
  SQL = await initSqlJs()

  // 如果已有数据库文件，加载它
  if (fs.existsSync(dbPath)) {
    const fileBuffer = fs.readFileSync(dbPath)
    db = new SQL.Database(fileBuffer)
  } else {
    db = new SQL.Database()
  }

  // 创建表
  db.run(`
    CREATE TABLE IF NOT EXISTS records (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      type TEXT NOT NULL CHECK(type IN ('expense', 'income')),
      amount REAL NOT NULL CHECK(amount > 0),
      date TEXT NOT NULL,
      category_l1 TEXT NOT NULL,
      category_l2 TEXT NOT NULL,
      note TEXT DEFAULT '',
      created_at TEXT DEFAULT (datetime('now', 'localtime'))
    )
  `)

  // 创建索引
  db.run(`CREATE INDEX IF NOT EXISTS idx_records_date ON records(date)`)
  db.run(`CREATE INDEX IF NOT EXISTS idx_records_type ON records(type)`)

  // 保存到文件
  saveToFile()
}

// 获取数据库实例
export function getDatabase(): any {
  return db
}

// 执行查询并返回对象数组（模拟 better-sqlite3 的 all()）
export function queryAll(sql: string, params: any[] = []): any[] {
  if (!db) return []

  const stmt = db.prepare(sql)
  if (params.length > 0) {
    stmt.bind(params)
  }

  const results: any[] = []
  while (stmt.step()) {
    results.push(stmt.getAsObject())
  }
  stmt.free()
  return results
}

// 执行写操作
export function run(sql: string, params: any[] = []): { lastInsertRowid: number; changes: number } {
  if (!db) return { lastInsertRowid: 0, changes: 0 }

  db.run(sql, params)
  saveToFile() // 每次写操作后保存

  // 获取 lastInsertRowid
  const lastId = (db.exec('SELECT last_insert_rowid() as id') as any[])[0]?.values[0]?.[0] || 0

  return {
    lastInsertRowid: lastId,
    changes: db.getRowsModified()
  }
}

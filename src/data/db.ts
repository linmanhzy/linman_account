import Database from '@tauri-apps/plugin-sql';

let db: Database | null = null;
let dbPromise: Promise<Database> | null = null;

// 初始化数据库（使用 Promise 缓存避免并发竞态条件）
export function initDB(): Promise<Database> {
  if (db) return Promise.resolve(db);
  if (!dbPromise) {
    dbPromise = (async () => {
      const database = await Database.load('sqlite:linman-account-book.db');

      // 创建表
      await database.execute(`
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
      `);

      // 创建索引
      await database.execute('CREATE INDEX IF NOT EXISTS idx_records_date ON records(date)');
      await database.execute('CREATE INDEX IF NOT EXISTS idx_records_type ON records(type)');

      db = database;
      return database;
    })();
  }
  return dbPromise;
}

// 添加记录
export async function addRecord(record: {
  type: 'expense' | 'income';
  amount: number;
  date: string;
  categoryL1: string;
  categoryL2: string;
  note: string;
}): Promise<{ id: number }> {
  const database = await initDB();
  const result = await database.execute(
    'INSERT INTO records (type, amount, date, category_l1, category_l2, note) VALUES ($1, $2, $3, $4, $5, $6)',
    [record.type, record.amount, record.date, record.categoryL1, record.categoryL2, record.note || '']
  );
  return { id: result.lastInsertId };
}

// 获取所有记录
export async function getRecords(filter?: {
  type?: string;
  month?: string;
}): Promise<RecordItem[]> {
  const database = await initDB();

  // 使用别名将蛇形命名映射为驼峰命名，与 RecordItem 接口一致
  let sql = 'SELECT id, type, amount, date, category_l1 AS categoryL1, category_l2 AS categoryL2, note, created_at FROM records WHERE 1=1';
  const params: (string | number)[] = [];
  let paramIndex = 1;

  if (filter?.type) {
    sql += ` AND type = $${paramIndex++}`;
    params.push(filter.type);
  }

  if (filter?.month) {
    sql += ` AND date LIKE $${paramIndex++}`;
    params.push(`${filter.month}%`);
  }

  sql += ' ORDER BY date DESC, id DESC';

  return await database.select<RecordItem[]>(sql, params);
}

// 删除记录
export async function deleteRecord(id: number): Promise<void> {
  const database = await initDB();
  await database.execute('DELETE FROM records WHERE id = $1', [id]);
}

// 获取月度统计
export async function getMonthStats(month: string): Promise<{
  income: number;
  expense: number;
  balance: number;
}> {
  const database = await initDB();

  // 使用 COALESCE 确保 SUM 在无记录时返回 0 而非 NULL
  const rows = await database.select<{ type: string; total: number }[]>(
    `SELECT type, COALESCE(SUM(amount), 0) as total FROM records WHERE date LIKE $1 GROUP BY type`,
    [`${month}%`]
  );

  let income = 0;
  let expense = 0;
  rows.forEach((r) => {
    if (r.type === 'income') income = r.total ?? 0;
    if (r.type === 'expense') expense = r.total ?? 0;
  });

  return { income, expense, balance: income - expense };
}

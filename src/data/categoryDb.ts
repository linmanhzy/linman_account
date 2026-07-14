import { initDB } from './db';
import type { CategoryL1 } from './categories';

// 查询所有未删除的分类（可按类型筛选）
export async function getCategories(type?: 'expense' | 'income'): Promise<Category[]> {
  const db = await initDB();
  let sql = 'SELECT * FROM categories WHERE is_deleted = 0';
  const params: string[] = [];

  if (type) {
    sql += ' AND type = $1';
    params.push(type);
  }

  sql += ' ORDER BY sort_order ASC, id ASC';
  return await db.select<Category[]>(sql, params);
}

// 将平铺数据分组为 CategoryL1 结构（供 CategorySelect 使用）
export async function getCategoriesGrouped(type: 'expense' | 'income'): Promise<CategoryL1[]> {
  const rows = await getCategories(type);

  // 按大类名分组，保持插入顺序
  const groups = new Map<string, { name: string; icon: string; children: string[] }>();
  for (const row of rows) {
    if (!groups.has(row.name_l1)) {
      groups.set(row.name_l1, {
        name: row.name_l1,
        icon: row.icon,
        children: [],
      });
    }
    groups.get(row.name_l1)!.children.push(row.name_l2);
  }

  return Array.from(groups.values());
}

// 新增分类（如果同名软删除行已存在则直接恢复，避免 UNIQUE 冲突）
export async function addCategory(cat: {
  type: 'expense' | 'income';
  name_l1: string;
  name_l2: string;
  icon: string;
}): Promise<Category> {
  const db = await initDB();

  // 检查是否已存在同名分类（包含软删除的行）
  const existing = await db.select<Category[]>(
    'SELECT * FROM categories WHERE type = $1 AND name_l1 = $2 AND name_l2 = $3',
    [cat.type, cat.name_l1, cat.name_l2]
  );

  if (existing.length > 0) {
    const row = existing[0];
    if (row.is_deleted === 1) {
      // 同名分类之前被软删除，直接恢复
      await db.execute(
        'UPDATE categories SET is_deleted = 0, icon = $1 WHERE id = $2',
        [cat.icon, row.id]
      );
      return { ...row, icon: cat.icon, is_deleted: 0 };
    }
    // 同名分类已存在且未删除，抛出错误
    throw new Error('该分类已存在，请勿重复添加');
  }

  // 获取当前最大 sort_order
  const rows = await db.select<{ max_order: number | null }[]>(
    'SELECT MAX(sort_order) as max_order FROM categories WHERE type = $1 AND is_deleted = 0',
    [cat.type]
  );
  const sortOrder = (rows[0]?.max_order ?? -1) + 1;

  const result = await db.execute(
    'INSERT INTO categories (type, name_l1, name_l2, icon, sort_order) VALUES ($1, $2, $3, $4, $5)',
    [cat.type, cat.name_l1, cat.name_l2, cat.icon, sortOrder]
  );

  return {
    id: result.lastInsertId as number,
    type: cat.type,
    name_l1: cat.name_l1,
    name_l2: cat.name_l2,
    icon: cat.icon,
    sort_order: sortOrder,
    is_deleted: 0,
  };
}

// 更新分类（改名时同步更新 records 表中的旧名称）
export async function updateCategory(
  id: number,
  updates: { name_l1?: string; name_l2?: string; icon?: string }
): Promise<void> {
  const db = await initDB();

  // 先查出旧值（事务外读取）
  const rows = await db.select<Category[]>('SELECT * FROM categories WHERE id = $1', [id]);
  if (rows.length === 0) throw new Error('分类不存在');
  const old = rows[0];

  const newL1 = updates.name_l1 ?? old.name_l1;
  const newL2 = updates.name_l2 ?? old.name_l2;
  const newIcon = updates.icon ?? old.icon;

  // 用事务包裹所有写操作，防止部分失败导致数据不一致
  try {
    await db.execute('BEGIN');

    // 更新分类表
    await db.execute(
      'UPDATE categories SET name_l1 = $1, name_l2 = $2, icon = $3 WHERE id = $4',
      [newL1, newL2, newIcon, id]
    );

    // 如果大类名变了，同步更新 records 表中所有关联记录
    if (updates.name_l1 && updates.name_l1 !== old.name_l1) {
      await db.execute(
        'UPDATE records SET category_l1 = $1 WHERE category_l1 = $2 AND type = $3',
        [updates.name_l1, old.name_l1, old.type]
      );
    }

    // 如果小类名变了，只更新匹配 (旧大类, 旧小类) 的记录
    if (updates.name_l2 && updates.name_l2 !== old.name_l2) {
      await db.execute(
        'UPDATE records SET category_l2 = $1 WHERE category_l1 = $2 AND category_l2 = $3 AND type = $4',
        [updates.name_l2, newL1, old.name_l2, old.type]
      );
    }

    await db.execute('COMMIT');
  } catch (err) {
    await db.execute('ROLLBACK');
    throw err;
  }
}

// 软删除单个小类
export async function deleteCategory(id: number): Promise<void> {
  const db = await initDB();
  await db.execute('UPDATE categories SET is_deleted = 1 WHERE id = $1', [id]);
}

// 软删除某大类下的所有小类
export async function deleteCategoryGroup(type: 'expense' | 'income', nameL1: string): Promise<void> {
  const db = await initDB();
  await db.execute(
    'UPDATE categories SET is_deleted = 1 WHERE type = $1 AND name_l1 = $2',
    [type, nameL1]
  );
}

import React, { useState } from 'react';
import {
  Card,
  Segmented,
  Button,
  Modal,
  Form,
  Input,
  Select,
  Popconfirm,
  message,
  Space,
  Spin,
  Empty,
  Tag,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
} from '@ant-design/icons';
import { useCategoryContext } from '../context/CategoryContext';
import { deleteCategoryGroup } from '../data/categoryDb';
import type { CategoryL1 } from '../data/categories';

type CatType = 'expense' | 'income';

// 编辑弹窗的数据结构
interface FormData {
  name_l1: string;
  name_l2: string;
  icon: string;
}

const CategoryManage: React.FC = () => {
  const {
    allCategories,
    expenseCategories,
    incomeCategories,
    loading,
    refreshCategories,
    addCategory,
    updateCategory,
    deleteCategory,
  } = useCategoryContext();

  const [catType, setCatType] = useState<CatType>('expense');
  const [modalOpen, setModalOpen] = useState(false);
  const [modalMode, setModalMode] = useState<'addL1' | 'editL1' | 'addL2' | 'editL2'>('addL1');
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editingL1Name, setEditingL1Name] = useState<string>('');
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<FormData>();

  const currentGrouped: CategoryL1[] =
    catType === 'expense' ? expenseCategories : incomeCategories;

  // 打开新增大类弹窗
  const openAddL1 = () => {
    setModalMode('addL1');
    setEditingId(null);
    setEditingL1Name('');
    form.resetFields();
    form.setFieldsValue({ icon: '📦' });
    setModalOpen(true);
  };

  // 打开编辑大类弹窗
  const openEditL1 = (nameL1: string, icon: string) => {
    setModalMode('editL1');
    setEditingId(null);
    setEditingL1Name(nameL1);
    form.setFieldsValue({ name_l1: nameL1, icon });
    setModalOpen(true);
  };

  // 打开新增小类弹窗
  const openAddL2 = (nameL1: string) => {
    setModalMode('addL2');
    setEditingId(null);
    setEditingL1Name(nameL1);
    form.resetFields();
    form.setFieldsValue({ name_l1: nameL1 });
    setModalOpen(true);
  };

  // 打开编辑小类弹窗
  const openEditL2 = (nameL1: string, nameL2: string, id: number) => {
    setModalMode('editL2');
    setEditingId(id);
    setEditingL1Name(nameL1);
    form.setFieldsValue({ name_l1: nameL1, name_l2: nameL2 });
    setModalOpen(true);
  };

  // 提交弹窗
  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);

      if (modalMode === 'addL1') {
        await addCategory({
          type: catType,
          name_l1: values.name_l1.trim(),
          name_l2: values.name_l2?.trim() || '其他',
          icon: values.icon.trim(),
        });
        message.success('大类添加成功');
      } else if (modalMode === 'editL1') {
        // 更新大类：需要更新该大类下所有行的大类名和图标
        const rows = allCategories.filter(
          (c) => c.type === catType && c.name_l1 === editingL1Name
        );
        for (const row of rows) {
          await updateCategory(row.id, {
            name_l1: values.name_l1.trim(),
            icon: values.icon.trim(),
          });
        }
        message.success('大类修改成功');
      } else if (modalMode === 'addL2') {
        // 查找该大类的信息以获取图标
        const l1Group = currentGrouped.find((g) => g.name === editingL1Name);
        await addCategory({
          type: catType,
          name_l1: editingL1Name,
          name_l2: values.name_l2.trim(),
          icon: l1Group?.icon || '📦',
        });
        message.success('小类添加成功');
      } else if (modalMode === 'editL2' && editingId !== null) {
        await updateCategory(editingId, {
          name_l2: values.name_l2.trim(),
        });
        message.success('小类修改成功');
      }

      setModalOpen(false);
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'errorFields' in err) {
        // 表单校验错误，不做处理（antd 会自动显示错误信息）
        return;
      }
      // 数据库 UNIQUE 约束冲突或业务层重复分类检测
      const msg = String(err);
      const isUniqueError =
        msg.includes('已存在') ||
        msg.includes('UNIQUE') ||
        msg.includes('unique') ||
        msg.includes('constraint') ||
        (err !== null && typeof err === 'object' && 'code' in err && String((err as Record<string, unknown>).code).includes('CONSTRAINT'));
      if (isUniqueError) {
        message.warning(msg.includes('已存在') ? msg : '该分类已存在，请勿重复添加');
      } else {
        message.error('操作失败：' + msg);
      }
    } finally {
      setSubmitting(false);
    }
  };

  // 删除大类组
  const handleDeleteL1 = async (nameL1: string) => {
    try {
      await deleteCategoryGroup(catType, nameL1);
      await refreshCategories();
      message.success('大类已删除');
    } catch (err) {
      message.error('删除失败：' + String(err));
    }
  };

  // 删除单个小类
  const handleDeleteL2 = async (id: number) => {
    try {
      await deleteCategory(id);
      message.success('小类已删除');
    } catch (err) {
      message.error('删除失败：' + String(err));
    }
  };

  // 弹窗标题
  const modalTitle =
    modalMode === 'addL1'
      ? '添加大类'
      : modalMode === 'editL1'
        ? '编辑大类'
        : modalMode === 'addL2'
          ? '添加小类'
          : '编辑小类';

  if (loading) {
    return (
      <div style={{ textAlign: 'center', paddingTop: 120 }}>
        <Spin size="large" tip="加载中..." />
      </div>
    );
  }

  return (
    <div>
      {/* 顶部切换栏 */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 24,
        }}
      >
        <Segmented
          value={catType}
          onChange={(val) => setCatType(val as CatType)}
          options={[
            { label: '支出分类', value: 'expense' },
            { label: '收入分类', value: 'income' },
          ]}
        />
        <Button type="primary" icon={<PlusOutlined />} onClick={openAddL1}>
          添加大类
        </Button>
      </div>

      {/* 分类卡片列表 */}
      {currentGrouped.length === 0 ? (
        <Empty description="暂无分类，点击右上角添加" style={{ marginTop: 80 }} />
      ) : (
        currentGrouped.map((group) => (
          <Card
            key={group.name}
            size="small"
            style={{ marginBottom: 16 }}
            title={
              <Space>
                <span style={{ fontSize: 18 }}>{group.icon}</span>
                <span>{group.name}</span>
                <Tag style={{ marginLeft: 4 }}>{group.children.length} 个小类</Tag>
              </Space>
            }
            extra={
              <Space>
                <Button
                  size="small"
                  icon={<EditOutlined />}
                  onClick={() => openEditL1(group.name, group.icon)}
                >
                  编辑
                </Button>
                <Popconfirm
                  title="确定删除该大类？"
                  description="将同时删除该大类下所有小类，已有记账记录不受影响。"
                  onConfirm={() => handleDeleteL1(group.name)}
                  okText="确定"
                  cancelText="取消"
                >
                  <Button size="small" danger icon={<DeleteOutlined />}>
                    删除
                  </Button>
                </Popconfirm>
              </Space>
            }
          >
            {/* 小类列表 */}
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginBottom: 12 }}>
              {group.children.map((child) => {
                // 找到该小类对应的数据库行 ID
                const row = allCategories.find(
                  (c) =>
                    c.type === catType &&
                    c.name_l1 === group.name &&
                    c.name_l2 === child
                );
                return (
                  <Tag
                    key={child}
                    closeIcon={
                      <Popconfirm
                        title="确定删除该小类？"
                        description="已有记账记录不受影响。"
                        onConfirm={() => row && handleDeleteL2(row.id)}
                        okText="确定"
                        cancelText="取消"
                      >
                        <DeleteOutlined style={{ fontSize: 10 }} />
                      </Popconfirm>
                    }
                    style={{
                      fontSize: 14,
                      padding: '4px 12px',
                      cursor: 'default',
                    }}
                  >
                    <span
                      style={{ cursor: 'pointer' }}
                      onClick={() => row && openEditL2(group.name, child, row.id)}
                    >
                      {child}
                    </span>
                  </Tag>
                );
              })}
            </div>

            {/* 添加小类按钮 */}
            <Button
              type="dashed"
              size="small"
              icon={<PlusOutlined />}
              onClick={() => openAddL2(group.name)}
            >
              添加小类
            </Button>
          </Card>
        ))
      )}

      {/* 添加/编辑弹窗 */}
      <Modal
        title={modalTitle}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        confirmLoading={submitting}
        destroyOnClose
        okText="确定"
        cancelText="取消"
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          {/* 大类名：addL1 / editL1 / addL2 时显示 */}
          {(modalMode === 'addL1' || modalMode === 'editL1') && (
            <Form.Item
              name="icon"
              label="图标（emoji）"
              rules={[{ required: true, message: '请输入图标' }]}
            >
              <Input placeholder="例如：🍜" maxLength={4} />
            </Form.Item>
          )}

          {/* 大类名 */}
          {(modalMode === 'addL1' ||
            modalMode === 'editL1' ||
            modalMode === 'addL2' ||
            modalMode === 'editL2') && (
            <Form.Item
              name="name_l1"
              label="大类名称"
              rules={[
                { required: true, message: '请输入大类名称' },
                { max: 20, message: '不超过20个字' },
              ]}
            >
              {modalMode === 'addL2' || modalMode === 'editL2' ? (
                <Select
                  disabled={modalMode === 'addL2' || modalMode === 'editL2'}
                  options={currentGrouped.map((g) => ({
                    label: `${g.icon} ${g.name}`,
                    value: g.name,
                  }))}
                />
              ) : (
                <Input placeholder="例如：餐饮饮食" maxLength={20} />
              )}
            </Form.Item>
          )}

          {/* 小类名：新增大类时可选，其他情况必填 */}
          {(modalMode === 'addL1' ||
            modalMode === 'addL2' ||
            modalMode === 'editL2') && (
            <Form.Item
              name="name_l2"
              label={modalMode === 'addL1' ? '默认小类名称（可选）' : '小类名称'}
              rules={
                modalMode === 'addL1'
                  ? [{ max: 20, message: '不超过20个字' }]
                  : [
                      { required: true, message: '请输入小类名称' },
                      { max: 20, message: '不超过20个字' },
                    ]
              }
            >
              <Input placeholder="例如：早餐" maxLength={20} />
            </Form.Item>
          )}
        </Form>
      </Modal>
    </div>
  );
};

export default CategoryManage;

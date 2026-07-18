import React, { useState } from 'react'
import { Card, Segmented, Button, Modal, Form, Input, Popconfirm, message, Space, Spin, Empty, Tag } from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons'
import { useCategoryContext } from '../context/CategoryContext'
import type { CategoryNode, RecordType } from '../types'

type Mode = 'l1' | 'l2' | 'editL1' | 'editL2'

const CategoryManage: React.FC = () => {
  const { categoryTree, loading, addL1, addL2, updateCategory, deleteCategory } = useCategoryContext()
  const [catType, setCatType] = useState<RecordType>('expense')
  const [modalOpen, setModalOpen] = useState(false)
  const [mode, setMode] = useState<Mode>('l1')
  const [editing, setEditing] = useState<{ id?: number; name?: string; icon?: string; parentId?: number } | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [form] = Form.useForm<{ name: string; icon: string }>()

  const roots = categoryTree.filter((n) => n.type === catType)

  const openAddL1 = () => {
    setMode('l1')
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ icon: '📦' })
    setModalOpen(true)
  }

  const openAddL2 = (parent: CategoryNode) => {
    setMode('l2')
    setEditing({ parentId: parent.id })
    form.resetFields()
    setModalOpen(true)
  }

  const openEditL1 = (node: CategoryNode) => {
    setMode('editL1')
    setEditing({ id: node.id, name: node.name, icon: node.icon || '' })
    form.setFieldsValue({ name: node.name, icon: node.icon || '' })
    setModalOpen(true)
  }

  const openEditL2 = (node: CategoryNode, parent: CategoryNode) => {
    setMode('editL2')
    setEditing({ id: node.id, name: node.name, parentId: parent.id })
    form.setFieldsValue({ name: node.name })
    setModalOpen(true)
  }

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()
      setSubmitting(true)
      if (mode === 'l1') {
        await addL1(catType, values.name.trim(), values.icon.trim())
        message.success('大类添加成功')
      } else if (mode === 'l2' && editing?.parentId != null) {
        await addL2(editing.parentId, values.name.trim())
        message.success('小类添加成功')
      } else if (mode === 'editL1' && editing?.id != null) {
        await updateCategory(editing.id, { name: values.name.trim(), icon: values.icon.trim() })
        message.success('大类修改成功')
      } else if (mode === 'editL2' && editing?.id != null) {
        await updateCategory(editing.id, { name: values.name.trim() })
        message.success('小类修改成功')
      }
      setModalOpen(false)
    } catch (err: any) {
      if (err && err.errorFields) return
      message.error(err?.message || '操作失败')
    } finally {
      setSubmitting(false)
    }
  }

  const handleDelete = async (id: number, isL1: boolean) => {
    try {
      await deleteCategory(id)
      message.success(isL1 ? '大类已删除' : '小类已删除')
    } catch (err: any) {
      message.error(err?.message || '删除失败')
    }
  }

  const modalTitle =
    mode === 'l1' ? '添加大类' : mode === 'editL1' ? '编辑大类' : mode === 'l2' ? '添加小类' : '编辑小类'

  if (loading) {
    return (
      <div style={{ textAlign: 'center', paddingTop: 120 }}>
        <Spin size="large" tip="加载中..." />
      </div>
    )
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <Segmented
          value={catType}
          onChange={(val) => setCatType(val as RecordType)}
          options={[
            { label: '支出分类', value: 'expense' },
            { label: '收入分类', value: 'income' },
          ]}
        />
        <Button type="primary" icon={<PlusOutlined />} onClick={openAddL1}>
          添加大类
        </Button>
      </div>

      {roots.length === 0 ? (
        <Empty description="暂无分类，点击右上角添加" style={{ marginTop: 80 }} />
      ) : (
        roots.map((group) => (
          <Card
            key={group.id}
            size="small"
            style={{ marginBottom: 16, borderRadius: 12 }}
            title={
              <Space>
                <span style={{ fontSize: 18 }}>{group.icon || '📦'}</span>
                <span>{group.name}</span>
                {group.system && <Tag color="blue">系统</Tag>}
                <Tag style={{ marginLeft: 4 }}>{group.children.length} 个小类</Tag>
              </Space>
            }
            extra={
              group.system ? null : (
                <Space>
                  <Button size="small" icon={<EditOutlined />} onClick={() => openEditL1(group)}>
                    编辑
                  </Button>
                  <Popconfirm
                    title="确定删除该大类？"
                    description="将同时删除该大类下所有小类，已有记账记录不受影响。"
                    onConfirm={() => handleDelete(group.id, true)}
                    okText="确定"
                    cancelText="取消"
                  >
                    <Button size="small" danger icon={<DeleteOutlined />}>
                      删除
                    </Button>
                  </Popconfirm>
                </Space>
              )
            }
          >
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginBottom: 12 }}>
              {group.children.map((child) => (
                <Tag
                  key={child.id}
                  closable={!group.system}
                  onClose={() => handleDelete(child.id, false)}
                  style={{ fontSize: 14, padding: '4px 12px', cursor: group.system ? 'default' : 'pointer' }}
                  onClick={() => !group.system && openEditL2(child, group)}
                >
                  {child.name}
                </Tag>
              ))}
            </div>

            <Button type="dashed" size="small" icon={<PlusOutlined />} onClick={() => openAddL2(group)}>
              添加小类
            </Button>
          </Card>
        ))
      )}

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
          {(mode === 'l1' || mode === 'editL1') && (
            <Form.Item name="icon" label="图标（emoji）" rules={[{ required: true, message: '请输入图标' }]}>
              <Input placeholder="例如：🍜" maxLength={4} />
            </Form.Item>
          )}
          <Form.Item
            name="name"
            label="名称"
            rules={[{ required: true, message: '请输入名称' }, { max: 20, message: '不超过20个字' }]}
          >
            <Input placeholder="例如：餐饮饮食" maxLength={20} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

export default CategoryManage

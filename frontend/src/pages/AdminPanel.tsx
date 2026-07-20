import { useState, useEffect, useCallback, useRef } from 'react'
import { useSearchParams } from 'react-router-dom'
import {
  Card, Tabs, Table, Button, Modal, Input, Form, Select, message, Tag,
  Spin, Empty, Popconfirm, Space, Typography, Switch, Tooltip, TimePicker
} from 'antd'
import {
  SendOutlined, ReloadOutlined, PlusOutlined, EditOutlined,
  DeleteOutlined, ExclamationCircleOutlined, KeyOutlined, WarningOutlined,
  ClockCircleOutlined
} from '@ant-design/icons'
import dayjs from 'dayjs'
import {
  getAllFeedbacks, replyFeedback, getAllUsers,
  getFullUserList, createUser, updateUser, deleteUser,
  resetUserPassword, changeUserStatus,
  getScheduledNotifications, createScheduledNotification,
  updateScheduledNotification, toggleScheduledNotification, deleteScheduledNotification
} from '../api/admin'
import type { FeedbackResponse, UserSummary, CreateUserRequest, ScheduledNotification, ScheduledNotificationRequest } from '../types'

const { TextArea } = Input
const { Text } = Typography

// ===== 6秒倒计时防误触 Hook =====
function useCountdown() {
  const [countdown, setCountdown] = useState(0)
  const timerRef = useRef<number | null>(null)

  const start = useCallback((seconds: number = 6) => {
    // 先停掉可能残留的旧定时器，避免多次打开弹窗时定时器叠加导致倒计时跳变
    if (timerRef.current) clearInterval(timerRef.current)
    setCountdown(seconds)
    timerRef.current = window.setInterval(() => {
      setCountdown((prev) => {
        if (prev <= 1) {
          if (timerRef.current) clearInterval(timerRef.current)
          return 0
        }
        return prev - 1
      })
    }, 1000)
  }, [])

  const stop = useCallback(() => {
    if (timerRef.current) {
      clearInterval(timerRef.current)
      timerRef.current = null
    }
    setCountdown(0)
  }, [])

  useEffect(() => () => stop(), [stop])

  return { countdown, start, stop }
}

// ===== 管理面板主页 =====
export default function AdminPanel() {
  const [searchParams, setSearchParams] = useSearchParams()
  const activeKey = searchParams.get('tab') || 'users'

  const handleTabChange = (key: string) => {
    setSearchParams({ tab: key })
  }

  return (
    <div style={{ maxWidth: 1000, margin: '0 auto' }}>
      <Card style={{ borderRadius: 12 }}>
        <Tabs activeKey={activeKey} onChange={handleTabChange} size="large">
          <Tabs.TabPane tab="用户管理" key="users">
            <UserManager />
          </Tabs.TabPane>
          <Tabs.TabPane tab="反馈管理" key="feedback">
            <FeedbackManager />
          </Tabs.TabPane>
          <Tabs.TabPane tab="拟写通知" key="notify">
            <MergedNotifier />
          </Tabs.TabPane>
        </Tabs>
      </Card>
    </div>
  )
}

// ===== 用户管理 =====
function UserManager() {
  const [users, setUsers] = useState<UserSummary[]>([])
  const [loading, setLoading] = useState(false)
  const [addModalOpen, setAddModalOpen] = useState(false)
  const [editModalOpen, setEditModalOpen] = useState(false)
  const [editingUser, setEditingUser] = useState<UserSummary | null>(null)
  const [addForm] = Form.useForm()
  const [editForm] = Form.useForm()
  const [submitting, setSubmitting] = useState(false)

  // 防误触：删除
  const deleteCD = useCountdown()
  const [deleteTarget, setDeleteTarget] = useState<UserSummary | null>(null)

  // 防误触：重置密码
  const resetCD = useCountdown()
  const [resetTarget, setResetTarget] = useState<UserSummary | null>(null)
  const [resetForm] = Form.useForm()

  const load = async () => {
    setLoading(true)
    try {
      const data = await getFullUserList()
      setUsers(data)
    } catch {
      message.error('加载用户列表失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  // 新建用户
  const handleAdd = async (values: CreateUserRequest) => {
    setSubmitting(true)
    try {
      await createUser({ username: values.username, password: values.password, role: values.role })
      message.success('用户创建成功')
      setAddModalOpen(false)
      addForm.resetFields()
      load()
    } catch {
      message.error('创建失败，请检查用户名是否已存在')
    } finally {
      setSubmitting(false)
    }
  }

  // 编辑用户
  const openEdit = (user: UserSummary) => {
    setEditingUser(user)
    editForm.setFieldsValue({ username: user.username, role: user.role })
    setEditModalOpen(true)
  }

  const handleEdit = async (values: { username: string; role: string }) => {
    if (!editingUser) return
    setSubmitting(true)
    try {
      await updateUser(editingUser.id, values)
      message.success('用户信息已更新')
      setEditModalOpen(false)
      setEditingUser(null)
      load()
    } catch {
      message.error('更新失败')
    } finally {
      setSubmitting(false)
    }
  }

  // 删除用户（4秒防误触）
  const openDeleteConfirm = (user: UserSummary) => {
    setDeleteTarget(user)
    deleteCD.start(4)
  }

  const handleDelete = async () => {
    if (!deleteTarget) return
    try {
      await deleteUser(deleteTarget.id)
      message.success(`用户「${deleteTarget.username}」已删除`)
      setDeleteTarget(null)
      deleteCD.stop()
      load()
    } catch (e: any) {
      message.error(e.message || '删除失败')
      deleteCD.stop()
      setDeleteTarget(null)
    }
  }

  // 重置密码（6秒防误触）
  const openResetConfirm = (user: UserSummary) => {
    setResetTarget(user)
    resetForm.resetFields()
    resetCD.start()
  }

  const handleResetPassword = async (values: { newPassword: string; confirmPassword: string }) => {
    if (!resetTarget) return
    if (values.newPassword !== values.confirmPassword) {
      message.error('两次输入的密码不一致')
      return
    }
    try {
      await resetUserPassword(resetTarget.id, { newPassword: values.newPassword })
      message.success(`用户「${resetTarget.username}」的密码已重置`)
      setResetTarget(null)
      resetCD.stop()
      resetForm.resetFields()
    } catch (e: any) {
      message.error(e.message || '重置失败')
      resetCD.stop()
      setResetTarget(null)
      resetForm.resetFields()
    }
  }

  // 状态切换（禁用/启用走 6 秒防误触弹窗）
  const statusCD = useCountdown()
  const [statusTarget, setStatusTarget] = useState<{ user: UserSummary; next: 'ENABLED' | 'DISABLED' } | null>(null)

  const openStatusConfirm = (user: UserSummary) => {
    const next = user.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
    setStatusTarget({ user, next })
    statusCD.start()
  }

  const handleStatusToggle = async () => {
    if (!statusTarget) return
    const { user, next } = statusTarget
    try {
      await changeUserStatus(user.id, next)
      message.success(`用户「${user.username}」已${next === 'ENABLED' ? '启用' : '禁用'}`)
      setStatusTarget(null)
      statusCD.stop()
      load()
    } catch (e: any) {
      message.error(e.message || '操作失败')
      setStatusTarget(null)
      statusCD.stop()
    }
  }

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: '用户名', dataIndex: 'username', width: 120 },
    {
      title: '角色',
      dataIndex: 'role',
      width: 100,
      render: (r: string) => (
        <Tag color={r === 'ADMIN' ? 'purple' : 'blue'}>{r === 'ADMIN' ? '管理员' : '普通用户'}</Tag>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (s: string, record: UserSummary) => {
        const isDefaultAdmin = record.username === 'admin'
        return (
          <Popconfirm
            title={s === 'ENABLED' ? '确认禁用该用户？' : '确认启用该用户？'}
            onConfirm={() => openStatusConfirm(record)}
            okText="确认"
            cancelText="取消"
            disabled={isDefaultAdmin && s === 'ENABLED'}
          >
            <Tooltip
              title={isDefaultAdmin && s === 'ENABLED' ? '默认管理员不能被禁用' : undefined}
            >
              <Switch
                checked={s === 'ENABLED'}
                checkedChildren="启用"
                unCheckedChildren="禁用"
                disabled={isDefaultAdmin && s === 'ENABLED'}
              />
            </Tooltip>
          </Popconfirm>
        )
      },
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 140,
      render: (t: string) => t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '-',
    },
    {
      title: '操作',
      width: 220,
      render: (_: any, record: UserSummary) => (
        <Space size="small">
          <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(record)}>
            编辑
          </Button>
          <Button size="small" icon={<KeyOutlined />} onClick={() => openResetConfirm(record)}>
            重置密码
          </Button>
          <Button
            size="small"
            danger
            icon={<DeleteOutlined />}
            onClick={() => openDeleteConfirm(record)}
          >
            删除
          </Button>
        </Space>
      ),
    },
  ]

  return (
    <>
      <div style={{ marginBottom: 12, textAlign: 'right' }}>
        <Space>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              addForm.resetFields()
              setAddModalOpen(true)
            }}
          >
            新建用户
          </Button>
          <Button icon={<ReloadOutlined />} onClick={load}>刷新</Button>
        </Space>
      </div>

      {loading ? (
        <div style={{ textAlign: 'center', padding: 40 }}><Spin /></div>
      ) : users.length === 0 ? (
        <Empty description="暂无用户" />
      ) : (
        <Table
          rowKey="id"
          dataSource={users}
          columns={columns}
          size="middle"
          pagination={{ pageSize: 10 }}
          scroll={{ x: 780 }}
        />
      )}

      {/* 新建用户弹窗 */}
      <Modal
        title="新建用户"
        open={addModalOpen}
        onCancel={() => setAddModalOpen(false)}
        footer={null}
        destroyOnClose
      >
        <Form form={addForm} layout="vertical" onFinish={handleAdd} initialValues={{ role: 'USER' }}>
          <Form.Item name="username" label="用户名" rules={[{ required: true, min: 2, max: 32, message: '2-32个字符' }]}>
            <Input placeholder="请输入用户名" maxLength={32} />
          </Form.Item>
          <Form.Item name="password" label="密码" rules={[{ required: true, min: 6, max: 64, message: '6-64个字符' }]}>
            <Input.Password placeholder="请输入密码" maxLength={64} />
          </Form.Item>
          <Form.Item name="role" label="角色">
            <Select options={[
              { label: '普通用户', value: 'USER' },
              { label: '管理员', value: 'ADMIN' },
            ]} />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
            <Space>
              <Button onClick={() => setAddModalOpen(false)}>取消</Button>
              <Button type="primary" htmlType="submit" loading={submitting}>创建</Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      {/* 编辑用户弹窗 */}
      <Modal
        title="编辑用户"
        open={editModalOpen}
        onCancel={() => { setEditModalOpen(false); setEditingUser(null) }}
        footer={null}
        destroyOnClose
      >
        <Form form={editForm} layout="vertical" onFinish={handleEdit}>
          <Form.Item name="username" label="用户名" rules={[{ required: true, min: 2, max: 32 }]}>
            <Input placeholder="请输入用户名" maxLength={32} />
          </Form.Item>
          <Form.Item name="role" label="角色">
            <Select options={[
              { label: '普通用户', value: 'USER' },
              { label: '管理员', value: 'ADMIN' },
            ]} />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
            <Space>
              <Button onClick={() => { setEditModalOpen(false); setEditingUser(null) }}>取消</Button>
              <Button type="primary" htmlType="submit" loading={submitting}>保存</Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      {/* 删除确认弹窗（6秒防误触）*/}
      <Modal
        title={
          <span style={{ color: '#ff4d4f' }}>
            <ExclamationCircleOutlined style={{ marginRight: 8 }} />
            危险操作
          </span>
        }
        open={!!deleteTarget}
        onCancel={() => { deleteCD.stop(); setDeleteTarget(null) }}
        footer={
          <Space>
            <Button onClick={() => { deleteCD.stop(); setDeleteTarget(null) }}>取消</Button>
            <Button
              danger
              type="primary"
              disabled={deleteCD.countdown > 0}
              onClick={handleDelete}
              icon={<DeleteOutlined />}
            >
              {deleteCD.countdown > 0
                ? `确认删除（${deleteCD.countdown} 秒后可操作）`
                : '确认删除'}
            </Button>
          </Space>
        }
      >
        <div
          style={{
            background: '#fff2f0',
            border: '1px solid #ffccc7',
            borderRadius: 8,
            padding: '16px 20px',
            marginBottom: 16,
          }}
        >
          <div style={{ color: '#ff4d4f', fontWeight: 600, fontSize: 15, marginBottom: 8 }}>
            <WarningOutlined style={{ marginRight: 6 }} />
            您即将删除以下用户账户，此操作不可撤销！
          </div>
          <div style={{ paddingLeft: 8 }}>
            <Text strong>用户名：</Text>{deleteTarget?.username}<br />
            <Text strong>角色：</Text>
            <Tag color={deleteTarget?.role === 'ADMIN' ? 'purple' : 'blue'} style={{ marginTop: 4 }}>
              {deleteTarget?.role === 'ADMIN' ? '管理员' : '普通用户'}
            </Tag>
          </div>
        </div>
        <div style={{ color: '#8c8c8c', fontSize: 13 }}>
          为防止误操作，确认按钮将在 <Text type="danger" strong>{deleteCD.countdown}</Text> 秒后启用。
          请仔细核对上方信息。
        </div>
      </Modal>

      {/* 重置密码弹窗（6秒防误触）*/}
      <Modal
        title={
          <span style={{ color: '#fa8c16' }}>
            <ExclamationCircleOutlined style={{ marginRight: 8 }} />
            重置密码
          </span>
        }
        open={!!resetTarget}
        onCancel={() => { resetCD.stop(); setResetTarget(null); resetForm.resetFields() }}
        footer={null}
        destroyOnClose
      >
        <div
          style={{
            background: '#fff7e6',
            border: '1px solid #ffd591',
            borderRadius: 8,
            padding: '12px 16px',
            marginBottom: 16,
          }}
        >
          <Text strong style={{ color: '#fa8c16' }}>
            <WarningOutlined style={{ marginRight: 4 }} />
            即将重置用户「{resetTarget?.username}」的登录密码
          </Text>
        </div>
        <Form form={resetForm} layout="vertical" onFinish={handleResetPassword}>
          <Form.Item
            name="newPassword"
            label="新密码"
            rules={[{ required: true, min: 6, max: 64, message: '6-64个字符' }]}
          >
            <Input.Password placeholder="输入新密码" maxLength={64} />
          </Form.Item>
          <Form.Item
            name="confirmPassword"
            label="确认密码"
            rules={[
              { required: true, min: 6, message: '请再次输入新密码' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('newPassword') === value) return Promise.resolve()
                  return Promise.reject(new Error('两次输入的密码不一致'))
                },
              }),
            ]}
          >
            <Input.Password placeholder="再次输入新密码" maxLength={64} />
          </Form.Item>
          <div style={{ color: '#8c8c8c', fontSize: 13, marginBottom: 16 }}>
            确认按钮将在 <Text type="warning" strong>{resetCD.countdown}</Text> 秒后启用。
          </div>
          <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
            <Space>
              <Button onClick={() => { resetCD.stop(); setResetTarget(null); resetForm.resetFields() }}>
                取消
              </Button>
              <Button
                type="primary"
                danger
                htmlType="submit"
                disabled={resetCD.countdown > 0}
                icon={<KeyOutlined />}
              >
                {resetCD.countdown > 0
                  ? `确认重置（${resetCD.countdown} 秒后可操作）`
                  : '确认重置'}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      {/* 禁用/启用确认弹窗（6秒防误触）*/}
      <Modal
        title={
          <span style={{ color: statusTarget?.next === 'DISABLED' ? '#ff4d4f' : '#52c41a' }}>
            <ExclamationCircleOutlined style={{ marginRight: 8 }} />
            {statusTarget?.next === 'DISABLED' ? '禁用用户' : '启用用户'}
          </span>
        }
        open={!!statusTarget}
        onCancel={() => { statusCD.stop(); setStatusTarget(null) }}
        footer={
          <Space>
            <Button onClick={() => { statusCD.stop(); setStatusTarget(null) }}>取消</Button>
            <Button
              type="primary"
              danger={statusTarget?.next === 'DISABLED'}
              disabled={statusCD.countdown > 0}
              onClick={handleStatusToggle}
            >
              {statusCD.countdown > 0
                ? `确认${statusTarget?.next === 'DISABLED' ? '禁用' : '启用'}（${statusCD.countdown} 秒后可操作）`
                : `确认${statusTarget?.next === 'DISABLED' ? '禁用' : '启用'}`}
            </Button>
          </Space>
        }
      >
        <div
          style={{
            background: statusTarget?.next === 'DISABLED' ? '#fff2f0' : '#f6ffed',
            border: `1px solid ${statusTarget?.next === 'DISABLED' ? '#ffccc7' : '#b7eb8f'}`,
            borderRadius: 8,
            padding: '16px 20px',
            marginBottom: 16,
          }}
        >
          <div style={{
            color: statusTarget?.next === 'DISABLED' ? '#ff4d4f' : '#52c41a',
            fontWeight: 600, fontSize: 15, marginBottom: 8
          }}>
            <WarningOutlined style={{ marginRight: 6 }} />
            您即将{statusTarget?.next === 'DISABLED' ? '禁用' : '启用'}以下用户账户
          </div>
          <div style={{ paddingLeft: 8 }}>
            <Text strong>用户名：</Text>{statusTarget?.user.username}<br />
            <Text strong>角色：</Text>
            <Tag color={statusTarget?.user.role === 'ADMIN' ? 'purple' : 'blue'} style={{ marginTop: 4 }}>
              {statusTarget?.user.role === 'ADMIN' ? '管理员' : '普通用户'}
            </Tag>
          </div>
        </div>
        <div style={{ color: '#8c8c8c', fontSize: 13 }}>
          为防止误操作，确认按钮将在 <Text type="danger" strong>{statusCD.countdown}</Text> 秒后启用。
          请仔细核对上方信息。
        </div>
      </Modal>
    </>
  )
}

// ===== 反馈管理（保持不变） =====
function FeedbackManager() {
  const [list, setList] = useState<FeedbackResponse[]>([])
  const [loading, setLoading] = useState(false)
  const [replyModal, setReplyModal] = useState<{ open: boolean; item: FeedbackResponse | null }>({
    open: false,
    item: null,
  })
  const [replyText, setReplyText] = useState('')
  const [sending, setSending] = useState(false)

  useEffect(() => {
    load()
  }, [])

  const load = async () => {
    setLoading(true)
    try {
      const data = await getAllFeedbacks()
      setList(data)
    } catch {
      message.error('加载反馈列表失败')
    } finally {
      setLoading(false)
    }
  }

  const handleReply = async () => {
    if (!replyModal.item || !replyText.trim()) return
    setSending(true)
    try {
      await replyFeedback(replyModal.item.id, replyText.trim())
      message.success('回复成功')
      setReplyModal({ open: false, item: null })
      setReplyText('')
      load()
    } catch {
      message.error('回复失败')
    } finally {
      setSending(false)
    }
  }

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: '用户', dataIndex: 'username', width: 100 },
    { title: '内容', dataIndex: 'content', ellipsis: true },
    {
      title: '状态', dataIndex: 'status', width: 100,
      render: (s: string) => (
        <Tag color={s === 'PENDING' ? 'orange' : 'green'}>
          {s === 'PENDING' ? '待处理' : '已回复'}
        </Tag>
      ),
    },
    { title: '回复', dataIndex: 'reply', ellipsis: true, render: (r: string) => r || '-' },
    { title: '时间', dataIndex: 'createdAt', width: 140, render: (t: string) => dayjs(t).format('MM-DD HH:mm') },
    {
      title: '操作', width: 80,
      render: (_: any, record: FeedbackResponse) =>
        record.status === 'PENDING' ? (
          <Button size="small" type="primary"
            onClick={() => { setReplyModal({ open: true, item: record }); setReplyText('') }}>
            回复
          </Button>
        ) : null,
    },
  ]

  return (
    <>
      <div style={{ marginBottom: 12, textAlign: 'right' }}>
        <Button icon={<ReloadOutlined />} onClick={load}>刷新</Button>
      </div>
      {loading ? (
        <div style={{ textAlign: 'center', padding: 40 }}><Spin /></div>
      ) : list.length === 0 ? (
        <Empty description="暂无反馈" />
      ) : (
        <Table rowKey="id" dataSource={list} columns={columns} size="middle" pagination={{ pageSize: 10 }} scroll={{ x: 700 }} />
      )}
      <Modal title="回复反馈" open={replyModal.open} onOk={handleReply}
        onCancel={() => setReplyModal({ open: false, item: null })}
        confirmLoading={sending} okText="发送回复" cancelText="取消">
        <div style={{ marginBottom: 8 }}><strong>用户反馈：</strong> {replyModal.item?.content}</div>
        <TextArea rows={4} value={replyText} onChange={(e) => setReplyText(e.target.value)}
          placeholder="输入回复内容……" maxLength={500} showCount />
      </Modal>
    </>
  )
}

// ===== 拟写通知（整合原发送通知 + 定时通知） =====
function MergedNotifier() {
  const [list, setList] = useState<ScheduledNotification[]>([])
  const [loading, setLoading] = useState(false)
  const [users, setUsers] = useState<{ id: number; username: string }[]>([])
  const [submitting, setSubmitting] = useState(false)
  const [form] = Form.useForm()
  const [editForm] = Form.useForm()
  const [editModalOpen, setEditModalOpen] = useState(false)
  const [editing, setEditing] = useState<ScheduledNotification | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const data = await getScheduledNotifications()
      // 过滤掉「仅一次」已发送的记录（frequency=ONCE && enabled=false），仅展示待定时的
      setList(data.filter((sn) => !(sn.frequency === 'ONCE' && !sn.enabled)))
    } catch {
      message.error('加载定时通知失败')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { load(); getAllUsers().then(setUsers).catch(() => {}) }, [load])

  // 提交拟写通知（ONCE 频率由后端立即派发，其他频率进入定时调度）
  const handleSubmit = async (values: any) => {
    setSubmitting(true)
    try {
      const req: ScheduledNotificationRequest = {
        title: values.title,
        content: values.content,
        frequency: values.frequency || 'ONCE',
        sendTime: values.sendTime ? values.sendTime.format('HH:mm:ss') : '00:00:00',
        sendDate: values.sendDate ? values.sendDate.format('YYYY-MM-DD') : null,
        type: values.type || 'ADMIN',
        targetUserId: values.scope === 'specific' ? values.targetUserId : null,
      }
      await createScheduledNotification(req)
      message.success(values.frequency === 'ONCE' ? '通知已立即发送' : '定时通知已创建')
      form.resetFields()
      load()
    } catch {
      message.error('操作失败')
    } finally {
      setSubmitting(false)
    }
  }

  // 编辑定时通知
  const openEdit = (record: ScheduledNotification) => {
    setEditing(record)
    editForm.setFieldsValue({
      title: record.title,
      content: record.content,
      frequency: record.frequency,
      sendTime: record.sendTime ? dayjs(record.sendTime, 'HH:mm:ss') : undefined,
      sendDate: record.sendDate ? dayjs(record.sendDate) : undefined,
      type: record.type,
    })
    setEditModalOpen(true)
  }

  const handleEdit = async () => {
    try {
      const values = await editForm.validateFields()
      const req: ScheduledNotificationRequest = {
        title: values.title,
        content: values.content,
        frequency: values.frequency,
        sendTime: values.sendTime ? values.sendTime.format('HH:mm:ss') : '09:00:00',
        sendDate: values.sendDate ? values.sendDate.format('YYYY-MM-DD') : null,
        type: values.type || 'DAILY',
        targetUserId: editing?.targetUserId || null,
      }
      await updateScheduledNotification(editing!.id, req)
      message.success('已更新')
      setEditModalOpen(false)
      load()
    } catch {
      // validation error
    }
  }

  const handleToggle = async (record: ScheduledNotification) => {
    try {
      await toggleScheduledNotification(record.id)
      message.success(record.enabled ? '已禁用' : '已启用')
      load()
    } catch { message.error('操作失败') }
  }

  const handleDelete = async (id: number) => {
    try {
      await deleteScheduledNotification(id)
      message.success('已删除')
      load()
    } catch { message.error('删除失败') }
  }

  const freqLabel = (f: string, r: ScheduledNotification) => {
    if (f === 'ONCE') return <Tag color="green">仅一次（已发送）</Tag>
    if (f === 'DAILY') return <Tag color="blue">每日 {r.sendTime?.slice(0, 5)}</Tag>
    if (f === 'SPECIFIC_DATE') return <Tag color="orange">{r.sendDate} {r.sendTime?.slice(0, 5)}</Tag>
    return <Tag>{f}</Tag>
  }

  const columns = [
    { title: '标题', dataIndex: 'title', key: 'title' },
    { title: '频率', dataIndex: 'frequency', key: 'frequency', render: freqLabel },
    {
      title: '类型', dataIndex: 'type', key: 'type',
      render: (t: string) => {
        const map: Record<string, { color: string; text: string }> = {
          DAILY: { color: 'blue', text: '每日' }, HOLIDAY: { color: 'orange', text: '节假日' },
          WELCOME: { color: 'green', text: '欢迎' }, ADMIN: { color: 'purple', text: '系统' },
        }
        const m = map[t] || { color: 'default', text: t }
        return <Tag color={m.color}>{m.text}</Tag>
      },
    },
    {
      title: '状态', dataIndex: 'enabled', key: 'enabled',
      render: (v: boolean, r: ScheduledNotification) =>
        r.frequency === 'ONCE' ? <Tag color="default">已发送</Tag> : <Switch checked={v} onChange={() => handleToggle(r)} />,
    },
    { title: '上次发送', dataIndex: 'lastFireDate', key: 'lastFireDate', render: (d: string) => d || '从未' },
    {
      title: '操作', key: 'actions',
      render: (_: unknown, r: ScheduledNotification) =>
        r.frequency === 'ONCE' ? null : (
          <Space>
            <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(r)}>编辑</Button>
            <Popconfirm title="确定删除？" onConfirm={() => handleDelete(r.id)}>
              <Button size="small" danger icon={<DeleteOutlined />}>删除</Button>
            </Popconfirm>
          </Space>
        ),
    },
  ]

  return (
    <>
      {/* 拟写通知表单 */}
      <Card title="拟写通知" style={{ marginBottom: 16 }}>
        <Form form={form} layout="vertical" onFinish={handleSubmit} style={{ maxWidth: 500 }}>
          <Form.Item name="title" label="标题" rules={[{ required: true, message: '请输入标题' }]}>
            <Input placeholder="如：系统维护公告" maxLength={100} />
          </Form.Item>
          <Form.Item name="content" label="内容" rules={[{ required: true, message: '请输入内容' }]}>
            <TextArea rows={3} placeholder="通知内容" maxLength={1000} showCount />
          </Form.Item>
          <Form.Item name="frequency" label="发送方式" initialValue="ONCE" rules={[{ required: true }]}>
            <Select>
              <Select.Option value="ONCE">仅一次（立即发送）</Select.Option>
              <Select.Option value="DAILY">每日定时</Select.Option>
              <Select.Option value="SPECIFIC_DATE">指定日期</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item name="scope" label="发送范围" initialValue="all" rules={[{ required: true }]}>
            <Select options={[
              { label: '全体用户', value: 'all' },
              { label: '指定用户', value: 'specific' },
            ]} />
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(prev, cur) => prev.scope !== cur.scope}>
            {({ getFieldValue }) =>
              getFieldValue('scope') === 'specific' ? (
                <Form.Item name="targetUserId" label="选择用户" rules={[{ required: true, message: '请选择目标用户' }]}>
                  <Select showSearch placeholder="搜索并选择用户"
                    filterOption={(input, option) =>
                      (option?.label as string)?.toLowerCase().includes(input.toLowerCase())
                    }
                    options={users.map((u) => ({ label: u.username, value: u.id }))} />
                </Form.Item>
              ) : null
            }
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(prev, cur) => prev.frequency !== cur.frequency}>
            {({ getFieldValue }) => {
              const freq = getFieldValue('frequency')
              if (freq === 'ONCE') return null
              return (
                <>
                  <Form.Item name="sendTime" label="发送时间"
                    rules={[{ required: true, message: '请选择时间' }]}>
                    <TimePicker format="HH:mm" style={{ width: '100%' }} />
                  </Form.Item>
                  {freq === 'SPECIFIC_DATE' && (
                    <Form.Item name="sendDate" label="发送日期"
                      rules={[{ required: true, message: '请选择日期' }]}>
                      <Input type="date" />
                    </Form.Item>
                  )}
                </>
              )
            }}
          </Form.Item>
          <Form.Item name="type" label="通知类型" initialValue="ADMIN">
            <Select>
              <Select.Option value="DAILY">每日</Select.Option>
              <Select.Option value="HOLIDAY">节假日</Select.Option>
              <Select.Option value="ADMIN">系统通知</Select.Option>
              <Select.Option value="WELCOME">欢迎</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" icon={<SendOutlined />} loading={submitting} block>
              发送
            </Button>
          </Form.Item>
        </Form>
      </Card>

      {/* 定时通知列表 */}
      <Card title="定时通知管理">
        <div style={{ marginBottom: 12, textAlign: 'right' }}>
          <Button icon={<ReloadOutlined />} onClick={load}>刷新</Button>
        </div>
        <Table columns={columns} dataSource={list} rowKey="id" loading={loading}
          locale={{ emptyText: <Empty description="暂无定时通知" /> }} />
      </Card>

      {/* 编辑弹窗 */}
      <Modal title="编辑定时通知" open={editModalOpen}
        onCancel={() => setEditModalOpen(false)} onOk={handleEdit} destroyOnClose>
        <Form form={editForm} layout="vertical">
          <Form.Item name="title" label="标题" rules={[{ required: true }]}>
            <Input placeholder="如：每日记账提醒" />
          </Form.Item>
          <Form.Item name="content" label="内容" rules={[{ required: true }]}>
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="frequency" label="发送方式" rules={[{ required: true }]}>
            <Select>
              <Select.Option value="DAILY">每日定时</Select.Option>
              <Select.Option value="SPECIFIC_DATE">指定日期</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item name="sendTime" label="发送时间" rules={[{ required: true }]}>
            <TimePicker format="HH:mm" style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item shouldUpdate noStyle>
            {({ getFieldValue }) =>
              getFieldValue('frequency') === 'SPECIFIC_DATE' ? (
                <Form.Item name="sendDate" label="发送日期" rules={[{ required: true }]}>
                  <Input type="date" />
                </Form.Item>
              ) : null
            }
          </Form.Item>
          <Form.Item name="type" label="通知类型">
            <Select>
              <Select.Option value="DAILY">每日</Select.Option>
              <Select.Option value="HOLIDAY">节假日</Select.Option>
              <Select.Option value="ADMIN">系统通知</Select.Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}

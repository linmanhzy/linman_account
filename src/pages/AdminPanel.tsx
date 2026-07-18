import { useState, useEffect } from 'react'
import {
  Card, Tabs, Table, Button, Modal, Input, Form, Select, message, Tag, Spin, Empty, Popconfirm
} from 'antd'
import { SendOutlined, ReloadOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { getAllFeedbacks, replyFeedback, sendNotification, getAllUsers } from '../api/admin'
import type { FeedbackResponse } from '../types'

const { TextArea } = Input

export default function AdminPanel() {
  const [activeTab, setActiveTab] = useState('feedback')

  return (
    <div style={{ maxWidth: 960, margin: '0 auto' }}>
      <Card style={{ borderRadius: 12 }}>
        <Tabs activeKey={activeTab} onChange={setActiveTab} size="large">
          <Tabs.TabPane tab="反馈管理" key="feedback">
            <FeedbackManager />
          </Tabs.TabPane>
          <Tabs.TabPane tab="发送通知" key="notify">
            <NotifySender />
          </Tabs.TabPane>
        </Tabs>
      </Card>
    </div>
  )
}

// ===== 反馈管理 =====
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
    // eslint-disable-next-line react-hooks/exhaustive-deps
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
    {
      title: '内容',
      dataIndex: 'content',
      ellipsis: true,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (s: string) => (
        <Tag color={s === 'PENDING' ? 'orange' : 'green'}>
          {s === 'PENDING' ? '待处理' : '已回复'}
        </Tag>
      ),
    },
    {
      title: '回复',
      dataIndex: 'reply',
      ellipsis: true,
      render: (r: string) => r || '-',
    },
    {
      title: '时间',
      dataIndex: 'createdAt',
      width: 140,
      render: (t: string) => dayjs(t).format('MM-DD HH:mm'),
    },
    {
      title: '操作',
      width: 80,
      render: (_: any, record: FeedbackResponse) =>
        record.status === 'PENDING' ? (
          <Button
            size="small"
            type="primary"
            onClick={() => {
              setReplyModal({ open: true, item: record })
              setReplyText('')
            }}
          >
            回复
          </Button>
        ) : null,
    },
  ]

  return (
    <>
      <div style={{ marginBottom: 12, textAlign: 'right' }}>
        <Button icon={<ReloadOutlined />} onClick={load}>
          刷新
        </Button>
      </div>
      {loading ? (
        <div style={{ textAlign: 'center', padding: 40 }}>
          <Spin />
        </div>
      ) : list.length === 0 ? (
        <Empty description="暂无反馈" />
      ) : (
        <Table
          rowKey="id"
          dataSource={list}
          columns={columns}
          size="middle"
          pagination={{ pageSize: 10 }}
          scroll={{ x: 700 }}
        />
      )}

      <Modal
        title="回复反馈"
        open={replyModal.open}
        onOk={handleReply}
        onCancel={() => setReplyModal({ open: false, item: null })}
        confirmLoading={sending}
        okText="发送回复"
        cancelText="取消"
      >
        <div style={{ marginBottom: 8 }}>
          <strong>用户反馈：</strong> {replyModal.item?.content}
        </div>
        <TextArea
          rows={4}
          value={replyText}
          onChange={(e) => setReplyText(e.target.value)}
          placeholder="输入回复内容……"
          maxLength={500}
          showCount
        />
      </Modal>
    </>
  )
}

// ===== 发送通知 =====
function NotifySender() {
  const [form] = Form.useForm()
  const [users, setUsers] = useState<{ id: number; username: string }[]>([])
  const [sending, setSending] = useState(false)

  useEffect(() => {
    getAllUsers()
      .then(setUsers)
      .catch(() => message.error('加载用户列表失败'))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const handleSend = async (values: {
    title: string
    content: string
    scope: 'all' | 'specific'
    targetUserId?: number
  }) => {
    setSending(true)
    try {
      await sendNotification({
        title: values.title,
        content: values.content,
        targetUserId: values.scope === 'specific' ? values.targetUserId : null,
      })
      message.success('通知发送成功')
      form.resetFields()
    } catch {
      message.error('发送失败')
    } finally {
      setSending(false)
    }
  }

  return (
    <Form form={form} layout="vertical" onFinish={handleSend} style={{ maxWidth: 500 }}>
      <Form.Item
        name="title"
        label="通知标题"
        rules={[{ required: true, message: '请输入标题' }]}
      >
        <Input placeholder="如：系统维护公告" maxLength={100} />
      </Form.Item>

      <Form.Item
        name="content"
        label="通知内容"
        rules={[{ required: true, message: '请输入内容' }]}
      >
        <TextArea rows={4} placeholder="通知正文……" maxLength={1000} showCount />
      </Form.Item>

      <Form.Item
        name="scope"
        label="发送范围"
        initialValue="all"
        rules={[{ required: true }]}
      >
        <Select
          options={[
            { label: '全体用户', value: 'all' },
            { label: '指定用户', value: 'specific' },
          ]}
        />
      </Form.Item>

      <Form.Item
        noStyle
        shouldUpdate={(prev, cur) => prev.scope !== cur.scope}
      >
        {({ getFieldValue }) =>
          getFieldValue('scope') === 'specific' ? (
            <Form.Item
              name="targetUserId"
              label="选择用户"
              rules={[{ required: true, message: '请选择目标用户' }]}
            >
              <Select
                showSearch
                placeholder="搜索并选择用户"
                filterOption={(input, option) =>
                  (option?.label as string)?.toLowerCase().includes(input.toLowerCase())
                }
                options={users.map((u) => ({ label: u.username, value: u.id }))}
              />
            </Form.Item>
          ) : null
        }
      </Form.Item>

      <Form.Item>
        <Button
          type="primary"
          htmlType="submit"
          icon={<SendOutlined />}
          loading={sending}
          block
        >
          发送通知
        </Button>
      </Form.Item>
    </Form>
  )
}

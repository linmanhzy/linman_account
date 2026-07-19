import { useState, useEffect } from 'react'
import { Card, List, Button, Tag, message, Spin, Empty, Popconfirm } from 'antd'
import { BellOutlined, CheckCircleOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { getNotifications, markRead, markAllRead, getUnreadCount } from '../api/notifications'
import type { NotificationResponse } from '../types'

export default function NotificationCenter() {
  const [list, setList] = useState<NotificationResponse[]>([])
  const [loading, setLoading] = useState(false)
  const [unreadCount, setUnreadCount] = useState(0)

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const load = async () => {
    setLoading(true)
    try {
      const [data, unread] = await Promise.all([
        getNotifications(),
        getUnreadCount(),
      ])
      setList(data)
      setUnreadCount(unread.count)
    } catch {
      message.error('加载通知失败')
    } finally {
      setLoading(false)
    }
  }

  const handleMarkRead = async (id: number) => {
    try {
      await markRead(id)
      setList((prev) =>
        prev.map((n) => (n.id === id ? { ...n, isRead: true } : n))
      )
    } catch {
      message.error('操作失败')
    }
  }

  const handleMarkAll = async () => {
    try {
      await markAllRead()
      setList((prev) => prev.map((n) => ({ ...n, isRead: true })))
      message.success('已全部标记为已读')
    } catch {
      message.error('操作失败')
    }
  }

  return (
    <div style={{ maxWidth: 720, margin: '0 auto' }}>
      <Card
        title={
          <span>
            <BellOutlined style={{ marginRight: 8 }} />
            通知中心
          </span>
        }
        extra={
          unreadCount > 0 ? (
            <Popconfirm
              title="确定将所有通知标记为已读？"
              onConfirm={handleMarkAll}
              okText="确定"
              cancelText="取消"
            >
              <Button size="small" icon={<CheckCircleOutlined />}>
                全部已读
              </Button>
            </Popconfirm>
          ) : null
        }
        style={{ borderRadius: 12 }}
      >
        {loading ? (
          <div style={{ textAlign: 'center', padding: 40 }}>
            <Spin />
          </div>
        ) : list.length === 0 ? (
          <Empty description="暂无通知" />
        ) : (
          <List
            dataSource={list}
            renderItem={(item) => (
              <List.Item
                key={item.id}
                style={{
                  background: item.isRead ? '#fff' : '#e6f4ff',
                  borderRadius: 8,
                  padding: '12px 16px',
                  marginBottom: 8,
                  flexDirection: 'column',
                  alignItems: 'flex-start',
                }}
              >
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    width: '100%',
                    marginBottom: 6,
                  }}
                >
                  <span style={{ fontWeight: item.isRead ? 400 : 600, fontSize: 15 }}>
                    {!item.isRead && (
                      <Tag color="blue" style={{ marginRight: 8 }}>
                        新
                      </Tag>
                    )}
                    {item.title}
                  </span>
                  <span style={{ fontSize: 12, color: '#999' }}>
                    {dayjs(item.createdAt).format('MM-DD HH:mm')}
                  </span>
                </div>
                <div style={{ fontSize: 14, color: '#555', marginBottom: 8, lineHeight: 1.6 }}>
                  {item.content}
                </div>
                {!item.isRead && (
                  <Button
                    size="small"
                    type="link"
                    onClick={() => handleMarkRead(item.id)}
                    style={{ padding: 0 }}
                  >
                    标记已读
                  </Button>
                )}
              </List.Item>
            )}
          />
        )}
      </Card>
    </div>
  )
}

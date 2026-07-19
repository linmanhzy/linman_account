import React, { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { List, Avatar, Tag, Badge } from 'antd'
import {
  AppstoreOutlined,
  BellOutlined,
  MessageOutlined,
  PlayCircleOutlined,
  TeamOutlined,
  LogoutOutlined,
  RightOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { useAuth } from '../auth/AuthContext'
import { getUnreadCount } from '../api/notifications'

type Entry = {
  key: string
  label: string
  icon: React.ReactNode
  onClick: () => void
  danger?: boolean
}

/** 移动端「我的」聚合页：用户信息 + 各功能入口。 */
const MobileProfile: React.FC = () => {
  const { username, role, logout } = useAuth()
  const navigate = useNavigate()
  const isAdmin = role === 'ADMIN'
  const [unread, setUnread] = useState(0)

  useEffect(() => {
    const fetchUnread = () =>
      getUnreadCount()
        .then((r) => setUnread(r.count))
        .catch(() => {})
    fetchUnread()
    const timer = setInterval(fetchUnread, 30_000)
    return () => clearInterval(timer)
  }, [])

  const go = (path: string) => navigate(path)

  const items: Entry[] = [
    { key: 'categories', label: '分类管理', icon: <AppstoreOutlined />, onClick: () => go('/categories') },
    {
      key: 'notifications',
      label: '通知中心',
      icon: (
        <Badge count={unread} size="small" offset={[2, -2]}>
          <BellOutlined />
        </Badge>
      ),
      onClick: () => go('/notifications'),
    },
    { key: 'feedback', label: '反馈建议', icon: <MessageOutlined />, onClick: () => go('/feedback') },
    { key: 'snake', label: '贪吃蛇', icon: <PlayCircleOutlined />, onClick: () => go('/snake') },
  ]

  if (isAdmin) {
    items.push({ key: 'admin', label: '用户管理', icon: <TeamOutlined />, onClick: () => go('/admin') })
  }

  items.push({
    key: 'logout',
    label: '退出登录',
    icon: <LogoutOutlined />,
    danger: true,
    onClick: () => {
      logout()
      navigate('/login')
    },
  })

  return (
    <div>
      <div
        style={{
          background: 'linear-gradient(135deg, #1677FF 0%, #8B5CF6 100%)',
          padding: '36px 20px 26px',
          color: '#fff',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
          <Avatar
            size={56}
            icon={<UserOutlined />}
            style={{ background: 'rgba(255,255,255,0.22)', border: '2px solid rgba(255,255,255,0.6)' }}
          />
          <div>
            <div style={{ fontSize: 18, fontWeight: 600 }}>{username || '用户'}</div>
            <Tag
              color="processing"
              style={{ marginTop: 6, borderColor: 'rgba(255,255,255,0.6)', color: '#fff' }}
            >
              {isAdmin ? '管理员' : '普通用户'}
            </Tag>
          </div>
        </div>
      </div>

      <div style={{ padding: 12 }}>
        <List
          dataSource={items}
          renderItem={(it) => (
            <List.Item
              key={it.key}
              onClick={it.onClick}
              style={{
                cursor: 'pointer',
                padding: '14px 16px',
                background: '#fff',
                borderRadius: 12,
                marginBottom: 10,
              }}
            >
              <List.Item.Meta
                avatar={
                  <span style={{ fontSize: 20, color: it.danger ? '#ff4d4f' : '#1677ff' }}>{it.icon}</span>
                }
                title={<span style={{ color: it.danger ? '#ff4d4f' : undefined }}>{it.label}</span>}
              />
              <RightOutlined style={{ color: '#bfbfbf' }} />
            </List.Item>
          )}
        />
      </div>
    </div>
  )
}

export default MobileProfile

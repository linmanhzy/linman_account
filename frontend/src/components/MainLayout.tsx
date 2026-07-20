import React, { useState, useEffect, useRef } from 'react'
import { Layout, Menu, Avatar, Dropdown, Button, Drawer, Grid, Badge, Switch, Tooltip, Tag, ConfigProvider, theme, Popover, List, Typography } from 'antd'
import {
  HomeOutlined,
  PlusCircleOutlined,
  UnorderedListOutlined,
  SettingOutlined,
  PlayCircleOutlined,
  BarChartOutlined,
  MenuOutlined,
  LogoutOutlined,
  UserOutlined,
  MessageOutlined,
  BellOutlined,
  SafetyCertificateOutlined,
  TeamOutlined,
  SwapOutlined,
  CheckCircleOutlined,
} from '@ant-design/icons'
import { Outlet, useNavigate, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { getUnreadCount, getUnreadNotifications, markRead, markAllRead } from '../api/notifications'
import { getVersion } from '../api/version'
import { isMobileView } from '../utils/platform'
import MobileTabBar from './MobileTabBar'
import dayjs from 'dayjs'
import type { NotificationResponse } from '../types'

const { Text } = Typography

const { Sider, Header, Content } = Layout
const { useBreakpoint } = Grid

const userMenuItems = [
  { key: '/dashboard', icon: <HomeOutlined />, label: '收支概览' },
  { key: '/add', icon: <PlusCircleOutlined />, label: '记一笔' },
  { key: '/list', icon: <UnorderedListOutlined />, label: '收支明细' },
  { key: '/categories', icon: <SettingOutlined />, label: '分类管理' },
  { key: '/report', icon: <BarChartOutlined />, label: '报表' },
  { key: '/snake', icon: <PlayCircleOutlined />, label: '贪吃蛇' },
  { key: '/feedback', icon: <MessageOutlined />, label: '反馈建议' },
]

const adminMenuItems = [
  { key: '/admin?tab=users', icon: <TeamOutlined />, label: '用户管理' },
  { key: '/admin?tab=feedback', icon: <MessageOutlined />, label: '反馈管理' },
  { key: '/admin?tab=notify', icon: <BellOutlined />, label: '拟写通知' },
]

const MainLayout: React.FC = () => {
  const navigate = useNavigate()
  const location = useLocation()
  const { username, role, logout } = useAuth()
  const screens = useBreakpoint()
  const isMobile = !screens.lg
  const isAdmin = role === 'ADMIN'
  const mobileView = isMobileView()
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [unreadCount, setUnreadCount] = useState(0)
  const [unreadList, setUnreadList] = useState<NotificationResponse[]>([])
  const [bellOpen, setBellOpen] = useState(false)
  const bellFetching = useRef(false)
  const [adminMode, setAdminMode] = useState(isAdmin)
  const [appVersion, setAppVersion] = useState('')

  // 挂载时请求版本号（后端 /api/version 公开接口），缓存后不再重复请求
  useEffect(() => {
    getVersion()
      .then(setAppVersion)
      .catch(() => {})
  }, [])

  // 轮询未读通知数（移动端由 MobileProfile 负责红点，这里仅桌面端轮询，避免双份请求）
  useEffect(() => {
    if (mobileView) return
    const fetchUnread = () => {
      getUnreadCount()
        .then((r) => setUnreadCount(r.count))
        .catch(() => {})
    }
    fetchUnread()
    const timer = setInterval(fetchUnread, 30_000)
    return () => clearInterval(timer)
  }, [mobileView])

  // 打开铃铛弹窗时加载未读列表
  const fetchUnreadList = async () => {
    if (bellFetching.current) return
    bellFetching.current = true
    try {
      const data = await getUnreadNotifications()
      setUnreadList(data)
    } catch {
      // ignore
    } finally {
      bellFetching.current = false
    }
  }

  const handleBellOpen = (visible: boolean) => {
    setBellOpen(visible)
    if (visible) {
      fetchUnreadList()
    }
  }

  const handleBellMarkRead = async (id: number) => {
    try {
      await markRead(id)
      setUnreadList((prev) => prev.filter((n) => n.id !== id))
      setUnreadCount((c) => Math.max(0, c - 1))
    } catch {
      // ignore
    }
  }

  const handleBellMarkAll = async () => {
    try {
      await markAllRead()
      setUnreadList([])
      setUnreadCount(0)
    } catch {
      // ignore
    }
  }

  // 铃铛弹窗内容
  const bellContent = (
    <div style={{ width: 320, maxHeight: 360, overflow: 'hidden' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
        <Text strong>未读通知</Text>
        {unreadList.length > 0 && (
          <Button type="link" size="small" onClick={handleBellMarkAll}>
            全部已读
          </Button>
        )}
      </div>
      {unreadList.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '20px 0', color: '#999' }}>
          <CheckCircleOutlined style={{ fontSize: 28, marginBottom: 8 }} />
          <div>暂无未读通知</div>
        </div>
      ) : (
        <List
          style={{ maxHeight: 300, overflow: 'auto' }}
          dataSource={unreadList.slice(0, 10)}
          renderItem={(item) => (
            <List.Item
              style={{ padding: '8px 0', cursor: 'pointer' }}
              onClick={() => handleBellMarkRead(item.id)}
            >
              <List.Item.Meta
                avatar={
                  <Tag color={
                    item.type === 'WELCOME' ? 'green' :
                    item.type === 'DAILY' ? 'blue' :
                    item.type === 'HOLIDAY' ? 'orange' : 'purple'
                  } style={{ margin: 0, fontSize: 10, lineHeight: '18px' }}>
                    {item.type === 'WELCOME' ? '欢迎' :
                     item.type === 'DAILY' ? '每日' :
                     item.type === 'HOLIDAY' ? '节日' : '系统'}
                  </Tag>
                }
                title={item.title}
                description={
                  <div>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      {item.content.length > 40 ? item.content.slice(0, 40) + '...' : item.content}
                    </Text>
                    <br />
                    <Text type="secondary" style={{ fontSize: 11 }}>
                      {dayjs(item.createdAt).format('MM-DD HH:mm')}
                    </Text>
                  </div>
                }
              />
            </List.Item>
          )}
        />
      )}
      <div style={{ textAlign: 'center', borderTop: '1px solid #f0f0f0', paddingTop: 8, marginTop: 4 }}>
        <Button type="link" size="small" onClick={() => { setBellOpen(false); navigate('/notifications') }}>
          查看全部通知
        </Button>
      </div>
    </div>
  )

  // 公共服务入口：通知中心（反馈建议已移至 userMenuItems / 反馈管理移至 adminMenuItems）
  const commonItems = [
    {
      key: '/notifications',
      icon: <BellOutlined />,
      label: (
        <Badge count={unreadCount} size="small" offset={[8, 0]}>
          通知中心
        </Badge>
      ),
    },
  ]

  // 管理员模式：仅显示管理 + 通知入口
  // 用户模式：显示完整用户菜单 + 通知/反馈
  const menuItems = isAdmin && adminMode
    ? [...adminMenuItems, ...commonItems]
    : isAdmin
      ? [...userMenuItems, ...commonItems]
      : [...userMenuItems, ...commonItems]

  // 选中文案：/admin?tab=xxx 匹配 query param；其他路径用 startsWith
  const selectedKey = menuItems.find((m) => {
    if (location.pathname === '/admin') {
      const tab = new URLSearchParams(location.search).get('tab') || 'users'
      return m.key === `/admin?tab=${tab}`
    }
    return location.pathname.startsWith(m.key)
  })?.key || (isAdmin && adminMode ? '/admin?tab=users' : '/dashboard')

  const go = (key: string) => {
    navigate(key)
    setDrawerOpen(false)
  }

  const userMenu = {
    items: [{ key: 'logout', icon: <LogoutOutlined />, label: '退出登录' }],
    onClick: ({ key }: { key: string }) => {
      if (key === 'logout') {
        logout()
        navigate('/login')
      }
    },
  }

  const sidebarBg = isAdmin && adminMode ? '#141414' : '#fff'
  const sidebarBorder = isAdmin && adminMode ? '1px solid #1f1f1f' : '1px solid #f0f0f0'
  const brandColor = isAdmin && adminMode ? '#b37feb' : '#1677ff'
  const menuTheme = isAdmin && adminMode ? 'dark' : 'light'

  // 管理模式下强制菜单项为浅色，解决黑底黑字对比度不足的问题
  const adminTheme = {
    algorithm: theme.darkAlgorithm,
    components: {
      Menu: {
        itemColor: 'rgba(255,255,255,0.85)',
        itemSelectedColor: '#ffffff',
        itemHoverColor: '#ffffff',
        itemSelectedBg: '#1677ff',
        itemActiveBg: 'rgba(255,255,255,0.15)',
      },
    },
  }

  const sidebarContent = (
    <>
      <div
        style={{
          paddingTop: 12,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: 2,
        }}
      >
        <div
          style={{
            fontSize: 18,
            fontWeight: 700,
            color: brandColor,
            letterSpacing: 2,
          }}
        >
          {isAdmin && adminMode ? '管理控制台' : '记账大王'}
        </div>
        {appVersion && (
          <Text type="secondary" style={{ fontSize: 11, lineHeight: 1 }}>
            v{appVersion}
          </Text>
        )}
      </div>

      {isAdmin && (
        <div
          style={{
            padding: '8px 16px 0',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <Tag
            color={adminMode ? 'purple' : 'blue'}
            style={{ margin: 0, fontSize: 11, borderRadius: 10 }}
          >
            {adminMode ? '管理模式' : '用户模式'}
          </Tag>
          <Tooltip title={adminMode ? '切换为普通用户视图' : '切换为管理视图'} placement="right">
            <Switch
              size="small"
              checked={!adminMode}
              checkedChildren={<SwapOutlined />}
              unCheckedChildren={<SafetyCertificateOutlined />}
              onChange={(checked) => setAdminMode(!checked)}
            />
          </Tooltip>
        </div>
      )}

      <Menu
        mode="inline"
        theme={menuTheme as any}
        selectedKeys={[selectedKey]}
        items={menuItems}
        onClick={({ key }) => go(key)}
        style={{ borderRight: 0, marginTop: 12, background: 'transparent' }}
      />
    </>
  )

  // 移动端视图（Tauri Android 或浏览器 ?mobile=1）：底部 Tab Bar，不渲染桌面 Sider/Drawer
  const mobile = isMobileView()
  if (mobile) {
    return (
      <div className="mobile-shell">
        <main className="mobile-content">
          <Outlet />
        </main>
        <MobileTabBar />
      </div>
    )
  }

  return (
    <Layout style={{ minHeight: '100vh' }}>
      {!isMobile && (
        <Sider
          width={210}
          style={{ background: sidebarBg, borderRight: sidebarBorder }}
        >
          {isAdmin && adminMode ? (
            <ConfigProvider theme={adminTheme}>{sidebarContent}</ConfigProvider>
          ) : (
            sidebarContent
          )}
        </Sider>
      )}

      <Layout>
        <Header
          style={{
            background: '#fff',
            padding: '0 16px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            borderBottom: '1px solid #f0f0f0',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            {isMobile && (
              <Button type="text" icon={<MenuOutlined />} onClick={() => setDrawerOpen(true)} />
            )}
            <Tooltip title="记一笔">
              <Button type="text" icon={<PlusCircleOutlined />} onClick={() => navigate('/add')} />
            </Tooltip>
            <Tooltip title="报表">
              <Button type="text" icon={<BarChartOutlined />} onClick={() => navigate('/report')} />
            </Tooltip>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <Popover
              content={bellContent}
              trigger="click"
              open={bellOpen}
              onOpenChange={handleBellOpen}
              placement="bottomRight"
              title={false}
            >
              <Badge count={unreadCount} size="small">
                <BellOutlined style={{ fontSize: 18, cursor: 'pointer', color: '#555' }} />
              </Badge>
            </Popover>
            <Dropdown menu={userMenu} placement="bottomRight">
              <div style={{ cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 8 }}>
                <Avatar
                  size="small"
                  icon={<UserOutlined />}
                  style={{ background: isAdmin && adminMode ? '#722ed1' : '#1677ff' }}
                />
                <span>{username || '用户'}</span>
              </div>
            </Dropdown>
          </div>
        </Header>

        <Content style={{ padding: isMobile ? 12 : 24, background: '#f5f7fa' }}>
          <Outlet />
        </Content>
      </Layout>

      <Drawer
        title={isAdmin && adminMode ? '管理控制台' : '记账大王'}
        placement="left"
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        styles={{ body: { padding: 0 } }}
      >
        {isAdmin && adminMode ? (
          <ConfigProvider theme={adminTheme}>
            <Menu
              mode="inline"
              selectedKeys={[selectedKey]}
              items={menuItems}
              onClick={({ key }) => go(key)}
              style={{ borderRight: 0 }}
            />
          </ConfigProvider>
        ) : (
          <Menu
            mode="inline"
            selectedKeys={[selectedKey]}
            items={menuItems}
            onClick={({ key }) => go(key)}
            style={{ borderRight: 0 }}
          />
        )}
      </Drawer>
    </Layout>
  )
}

export default MainLayout

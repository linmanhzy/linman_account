import React, { useState, useEffect } from 'react'
import { Layout, Menu, Avatar, Dropdown, Button, Drawer, Grid, Badge, Switch, Tooltip, Tag, ConfigProvider, theme } from 'antd'
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
} from '@ant-design/icons'
import { Outlet, useNavigate, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { getUnreadCount } from '../api/notifications'
import { isMobileView } from '../utils/platform'
import MobileTabBar from './MobileTabBar'

const { Sider, Header, Content } = Layout
const { useBreakpoint } = Grid

const userMenuItems = [
  { key: '/dashboard', icon: <HomeOutlined />, label: '收支概览' },
  { key: '/add', icon: <PlusCircleOutlined />, label: '记一笔' },
  { key: '/list', icon: <UnorderedListOutlined />, label: '收支明细' },
  { key: '/categories', icon: <SettingOutlined />, label: '分类管理' },
  { key: '/report', icon: <BarChartOutlined />, label: '报表' },
  { key: '/snake', icon: <PlayCircleOutlined />, label: '贪吃蛇' },
]

const adminMenuItems = [
  { key: '/admin', icon: <TeamOutlined />, label: '用户管理' },
  { key: '/feedback', icon: <MessageOutlined />, label: '反馈管理' },
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
  const [adminMode, setAdminMode] = useState(isAdmin)

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

  // 管理员默认进入管理模式，切换用户时出现「通知 + 反馈」公共入口
  const commonItems = isAdmin
    ? [
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
    : [
        {
          key: '/notifications',
          icon: <BellOutlined />,
          label: (
            <Badge count={unreadCount} size="small" offset={[8, 0]}>
              通知中心
            </Badge>
          ),
        },
        { key: '/feedback', icon: <MessageOutlined />, label: '反馈建议' },
      ]

  // 管理员模式：仅显示管理 + 通知入口
  // 用户模式：显示完整用户菜单 + 通知/反馈
  const menuItems = isAdmin && adminMode
    ? [...adminMenuItems, ...commonItems]
    : isAdmin
      ? [...userMenuItems, ...commonItems]
      : [...userMenuItems, ...commonItems]

  const selectedKey =
    menuItems.find((m) => location.pathname.startsWith(m.key))?.key ||
    (isAdmin && adminMode ? '/admin' : '/dashboard')

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
          height: 56,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 8,
          fontSize: 18,
          fontWeight: 700,
          color: brandColor,
          letterSpacing: 2,
        }}
      >
        {isAdmin && adminMode ? '管理控制台' : '记账大王'}
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
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            {isMobile && (
              <Button type="text" icon={<MenuOutlined />} onClick={() => setDrawerOpen(true)} />
            )}
            <span style={{ fontSize: 18, fontWeight: 700, color: '#1677ff' }}>记账大王</span>
            {isAdmin && (
              <Tag color={adminMode ? 'purple' : 'blue'} style={{ margin: 0, borderRadius: 10 }}>
                {adminMode ? '管理模式' : '用户模式'}
              </Tag>
            )}
          </div>
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

import React, { useState } from 'react'
import { Layout, Menu, Avatar, Dropdown, Button, Drawer, Grid } from 'antd'
import {
  HomeOutlined,
  PlusCircleOutlined,
  UnorderedListOutlined,
  SettingOutlined,
  PlayCircleOutlined,
  MenuOutlined,
  LogoutOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { Outlet, useNavigate, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

const { Sider, Header, Content } = Layout
const { useBreakpoint } = Grid

const menuItems = [
  { key: '/dashboard', icon: <HomeOutlined />, label: '收支概览' },
  { key: '/add', icon: <PlusCircleOutlined />, label: '记一笔' },
  { key: '/list', icon: <UnorderedListOutlined />, label: '收支明细' },
  { key: '/categories', icon: <SettingOutlined />, label: '分类管理' },
  { key: '/snake', icon: <PlayCircleOutlined />, label: '贪吃蛇' },
]

const MainLayout: React.FC = () => {
  const navigate = useNavigate()
  const location = useLocation()
  const { username, logout } = useAuth()
  const screens = useBreakpoint()
  const isMobile = !screens.lg
  const [drawerOpen, setDrawerOpen] = useState(false)

  // 根据当前路径高亮菜单
  const selectedKey =
    menuItems.find((m) => location.pathname.startsWith(m.key))?.key || '/dashboard'

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

  return (
    <Layout style={{ minHeight: '100vh' }}>
      {!isMobile && (
        <Sider
          width={210}
          style={{ background: '#fff', borderRight: '1px solid #f0f0f0' }}
        >
          <div
            style={{
              height: 56,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 20,
              fontWeight: 700,
              color: '#1677ff',
              letterSpacing: 2,
            }}
          >
            林蛮记账
          </div>
          <Menu
            mode="inline"
            selectedKeys={[selectedKey]}
            items={menuItems}
            onClick={({ key }) => go(key)}
            style={{ borderRight: 0 }}
          />
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
            <span style={{ fontSize: 18, fontWeight: 700, color: '#1677ff' }}>林蛮记账</span>
          </div>
          <Dropdown menu={userMenu} placement="bottomRight">
            <div style={{ cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 8 }}>
              <Avatar size="small" icon={<UserOutlined />} style={{ background: '#1677ff' }} />
              <span>{username || '用户'}</span>
            </div>
          </Dropdown>
        </Header>

        <Content style={{ padding: isMobile ? 12 : 24, background: '#f5f7fa' }}>
          <Outlet />
        </Content>
      </Layout>

      <Drawer
        title="林蛮记账"
        placement="left"
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        styles={{ body: { padding: 0 } }}
      >
        <Menu
          mode="inline"
          selectedKeys={[selectedKey]}
          items={menuItems}
          onClick={({ key }) => go(key)}
          style={{ borderRight: 0 }}
        />
      </Drawer>
    </Layout>
  )
}

export default MainLayout

import React from 'react'
import { Layout, Menu } from 'antd'
import {
  HomeOutlined,
  PlusCircleOutlined,
  UnorderedListOutlined
} from '@ant-design/icons'
import Dashboard from './pages/Dashboard'
import AddRecord from './pages/AddRecord'
import RecordList from './pages/RecordList'

const { Sider, Content } = Layout

type PageKey = 'dashboard' | 'add' | 'list'

const menuItems = [
  { key: 'dashboard', icon: <HomeOutlined />, label: '收支概览' },
  { key: 'add', icon: <PlusCircleOutlined />, label: '记一笔' },
  { key: 'list', icon: <UnorderedListOutlined />, label: '收支明细' }
]

const App: React.FC = () => {
  const [currentPage, setCurrentPage] = React.useState<PageKey>('dashboard')

  const renderPage = () => {
    switch (currentPage) {
      case 'dashboard':
        return <Dashboard />
      case 'add':
        return <AddRecord onSuccess={() => setCurrentPage('list')} />
      case 'list':
        return <RecordList />
    }
  }

  return (
    <Layout style={{ height: '100vh' }}>
      <Sider
        width={200}
        style={{
          background: '#fff',
          borderRight: '1px solid #f0f0f0',
          paddingTop: 16
        }}
      >
        <div
          style={{
            height: 48,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: 20,
            fontWeight: 700,
            color: '#1677ff',
            marginBottom: 16,
            letterSpacing: 2
          }}
        >
          林蛮记账
        </div>
        <Menu
          mode="inline"
          selectedKeys={[currentPage]}
          items={menuItems}
          onClick={({ key }) => setCurrentPage(key as PageKey)}
          style={{ borderRight: 0 }}
        />
      </Sider>
      <Content style={{ padding: 24, overflow: 'auto', background: '#f5f5f5' }}>
        {renderPage()}
      </Content>
    </Layout>
  )
}

export default App

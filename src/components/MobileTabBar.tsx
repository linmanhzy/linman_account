import React from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import {
  HomeOutlined,
  PlusCircleOutlined,
  UnorderedListOutlined,
  BarChartOutlined,
  UserOutlined,
} from '@ant-design/icons'

const tabs = [
  { key: '/dashboard', label: '概览', icon: <HomeOutlined /> },
  { key: '/add', label: '记一笔', icon: <PlusCircleOutlined /> },
  { key: '/list', label: '明细', icon: <UnorderedListOutlined /> },
  { key: '/report', label: '报表', icon: <BarChartOutlined /> },
  { key: '/profile', label: '我的', icon: <UserOutlined /> },
]

/** 移动端底部 Tab Bar：固定定位、单手操作、安全区留白。 */
const MobileTabBar: React.FC = () => {
  const navigate = useNavigate()
  const location = useLocation()

  return (
    <nav className="mobile-tabbar" aria-label="主导航">
      {tabs.map((t) => {
        const isActive = location.pathname === t.key
        return (
          <button
            key={t.key}
            type="button"
            className={`mobile-tabbar__item${isActive ? ' mobile-tabbar__item--active' : ''}`}
            onClick={() => navigate(t.key)}
            aria-current={isActive ? 'page' : undefined}
          >
            <span className="mobile-tabbar__icon">{t.icon}</span>
            <span>{t.label}</span>
          </button>
        )
      })}
    </nav>
  )
}

export default MobileTabBar

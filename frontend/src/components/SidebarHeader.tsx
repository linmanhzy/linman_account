import React from 'react'
import { Tag, Switch, Tooltip, Typography } from 'antd'
import { SwapOutlined, SafetyCertificateOutlined } from '@ant-design/icons'

const { Text } = Typography

interface SidebarHeaderProps {
  isAdmin: boolean
  adminMode: boolean
  brandColor: string
  appVersion: string
  /** 切换管理模式 / 用户模式（传入目标 adminMode 值） */
  onToggleAdminMode: (nextAdminMode: boolean) => void
}

/**
 * 侧边栏（Sider）与抽屉（Drawer）共用的头部：
 * 品牌名 + 版本号 + 管理员「管理模式 / 用户模式」切换控件。
 */
const SidebarHeader: React.FC<SidebarHeaderProps> = ({
  isAdmin,
  adminMode,
  brandColor,
  appVersion,
  onToggleAdminMode,
}) => {
  return (
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
              onChange={(checked) => onToggleAdminMode(!checked)}
            />
          </Tooltip>
        </div>
      )}
    </>
  )
}

export default SidebarHeader

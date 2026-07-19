import React, { useState } from 'react'
import { Card, Form, Input, Button, Tabs, message, Typography } from 'antd'
import { UserOutlined, LockOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

const { Title, Text } = Typography

const Login: React.FC = () => {
  const { login, register } = useAuth()
  const navigate = useNavigate()
  const [loading, setLoading] = useState(false)
  const [tab, setTab] = useState('login')
  const [form] = Form.useForm()

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()
      setLoading(true)
      if (tab === 'login') {
        await login(values.username, values.password)
      } else {
        await register(values.username, values.password)
      }
      message.success(tab === 'login' ? '登录成功' : '注册成功，已自动登录')
      navigate('/')
    } catch (err: any) {
      if (err && err.errorFields) return
      message.error(err?.message || '操作失败')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div
      style={{
        minHeight: '100dvh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'linear-gradient(135deg, #1677ff 0%, #0958d9 50%, #061178 100%)',
        padding: 16,
      }}
    >
      <Card
        style={{
          width: 380,
          maxWidth: '100%',
          borderRadius: 16,
          boxShadow: '0 12px 40px rgba(0,0,0,0.2)',
        }}
        styles={{ body: { padding: 32 } }}
      >
        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          <Title level={3} style={{ marginBottom: 4, color: '#1677ff' }}>
            记账大王
          </Title>
          <Text type="secondary">联网记账 · 数据多端同步</Text>
        </div>

        <Tabs
          activeKey={tab}
          onChange={setTab}
          centered
          items={[
            { key: 'login', label: '登录' },
            { key: 'register', label: '注册' },
          ]}
        />

        <Form form={form} layout="vertical" size="large">
          <Form.Item
            name="username"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input prefix={<UserOutlined />} placeholder="用户名" />
          </Form.Item>
          <Form.Item
            name="password"
            rules={[{ required: true, message: '请输入密码' }, { min: 6, message: '密码至少 6 位' }]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="密码（至少 6 位）" />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0 }}>
            <Button type="primary" block onClick={handleSubmit} loading={loading}>
              {tab === 'login' ? '登 录' : '注 册'}
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  )
}

export default Login

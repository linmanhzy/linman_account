import React, { useEffect, useState } from 'react'
import { Card, Row, Col, Statistic, Tag, message } from 'antd'
import { ArrowUpOutlined, ArrowDownOutlined, WalletOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { getMonthlyStats } from '../api/records'

const Dashboard: React.FC = () => {
  const [stats, setStats] = useState({ income: 0, expense: 0, balance: 0 })
  const [loading, setLoading] = useState(false)
  const currentMonth = dayjs().format('YYYY-MM')

  useEffect(() => {
    loadStats()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentMonth])

  const loadStats = async () => {
    setLoading(true)
    try {
      const result = await getMonthlyStats(currentMonth)
      setStats({
        income: Number(result.income) || 0,
        expense: Number(result.expense) || 0,
        balance: Number(result.balance) || 0,
      })
    } catch (err: any) {
      console.error('加载统计数据失败:', err)
      message.error(err?.message || '加载收支数据失败')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div>
      <h2 style={{ marginBottom: 24, fontSize: 22 }}>
        收支概览
        <Tag color="blue" style={{ marginLeft: 12, fontSize: 14 }}>
          {dayjs().format('YYYY年M月')}
        </Tag>
      </h2>

      <Row gutter={[16, 16]}>
        <Col xs={24} md={8}>
          <Card className="stat-card" style={{ borderTop: '3px solid #52c41a', borderRadius: 12 }} loading={loading}>
            <Statistic
              title="本月收入"
              value={stats.income}
              precision={2}
              prefix={<ArrowUpOutlined />}
              suffix="元"
              valueStyle={{ color: '#52c41a', fontSize: 28 }}
            />
          </Card>
        </Col>
        <Col xs={24} md={8}>
          <Card className="stat-card" style={{ borderTop: '3px solid #ff4d4f', borderRadius: 12 }} loading={loading}>
            <Statistic
              title="本月支出"
              value={stats.expense}
              precision={2}
              prefix={<ArrowDownOutlined />}
              suffix="元"
              valueStyle={{ color: '#ff4d4f', fontSize: 28 }}
            />
          </Card>
        </Col>
        <Col xs={24} md={8}>
          <Card className="stat-card" style={{ borderTop: '3px solid #1677ff', borderRadius: 12 }} loading={loading}>
            <Statistic
              title="本月结余"
              value={stats.balance}
              precision={2}
              prefix={<WalletOutlined />}
              suffix="元"
              valueStyle={{ color: stats.balance >= 0 ? '#1677ff' : '#ff4d4f', fontSize: 28 }}
            />
          </Card>
        </Col>
      </Row>

      <Card style={{ marginTop: 24, borderRadius: 12 }}>
        <p style={{ color: '#999', fontSize: 14, margin: 0 }}>
          💡 提示：点击左侧「记一笔」开始记录你的收支，点击「收支明细」查看所有记录。
        </p>
      </Card>
    </div>
  )
}

export default Dashboard

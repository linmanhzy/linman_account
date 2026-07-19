import React, { useEffect, useState } from 'react'
import { Card, Row, Col, Statistic, Tag, message, Alert } from 'antd'
import { ArrowUpOutlined, ArrowDownOutlined, WalletOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { getMonthlyStats, getRecordQuota } from '../api/records'
import { isMobileView } from '../utils/platform'

const Dashboard: React.FC = () => {
  const [stats, setStats] = useState({ income: 0, expense: 0, balance: 0 })
  const [loading, setLoading] = useState(false)
  const [quota, setQuota] = useState({ count: 0, max: 50000 })
  const currentMonth = dayjs().format('YYYY-MM')
  const mobile = isMobileView()

  useEffect(() => {
    loadStats()
    loadQuota()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentMonth])

  const loadQuota = async () => {
    try {
      const q = await getRecordQuota()
      setQuota(q)
    } catch {
      // 配额加载失败不影响主流程
    }
  }

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

      {quota.count >= quota.max * 0.8 && quota.count < quota.max && (
        <Alert
          type="warning"
          showIcon
          message="记录即将达到上限"
          description={`你的记账记录已达 ${quota.count.toLocaleString()} / ${quota.max.toLocaleString()} 条，请及时在「报表 → 导出账本」中导出数据，避免影响后续记账。`}
          style={{ marginTop: 16 }}
        />
      )}
      {quota.count >= quota.max && (
        <Alert
          type="error"
          showIcon
          message="记录已达上限"
          description={`你已有 ${quota.count.toLocaleString()} 条记录（上限 ${quota.max.toLocaleString()} 条），无法再新增。请导出数据后删除旧记录再继续记账。`}
          style={{ marginTop: 16 }}
        />
      )}

      <Card style={{ marginTop: 24, borderRadius: 12 }}>
        <p style={{ color: '#999', fontSize: 14, margin: 0 }}>
          💡 提示：{mobile ? '点击下方「记一笔」Tab' : '点击左侧「记一笔」'}开始记录你的收支，
          {mobile ? '点击下方「明细」Tab' : '点击「收支明细」'}查看所有记录。
        </p>
      </Card>
    </div>
  )
}

export default Dashboard

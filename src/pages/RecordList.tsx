import React, { useEffect, useState } from 'react'
import { Card, Table, Tag, Segmented, DatePicker, Popconfirm, Button, message } from 'antd'
import { DeleteOutlined } from '@ant-design/icons'
import dayjs, { Dayjs } from 'dayjs'
import { getRecords, deleteRecord } from '../api/records'
import type { RecordItem } from '../types'

const RecordList: React.FC = () => {
  const [records, setRecords] = useState<RecordItem[]>([])
  const [filterType, setFilterType] = useState<string>('all')
  const [filterMonth, setFilterMonth] = useState<Dayjs>(dayjs())
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    loadRecords()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filterType, filterMonth])

  const loadRecords = async () => {
    setLoading(true)
    try {
      const params: { type?: string; month?: string } = {}
      if (filterType !== 'all') params.type = filterType
      params.month = filterMonth.format('YYYY-MM')
      const result = await getRecords(params)
      setRecords(result)
    } catch (err: any) {
      console.error('加载记录失败:', err)
      message.error(err?.message || '加载记录失败')
    } finally {
      setLoading(false)
    }
  }

  const handleDelete = async (id: number) => {
    try {
      await deleteRecord(id)
      message.success('删除成功')
      loadRecords()
    } catch (err: any) {
      console.error('删除记录失败:', err)
      message.error(err?.message || '删除失败')
    }
  }

  const columns = [
    {
      title: '日期',
      dataIndex: 'recordDate',
      key: 'recordDate',
      width: 120,
      render: (date: string) => <span style={{ fontWeight: 500 }}>{date}</span>,
    },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      width: 80,
      render: (type: string) =>
        type === 'expense' ? <Tag color="error">支出</Tag> : <Tag color="success">收入</Tag>,
    },
    {
      title: '分类',
      key: 'category',
      width: 180,
      render: (_: any, record: RecordItem) => (
        <span>
          {record.categoryL1} · {record.categoryL2}
        </span>
      ),
    },
    {
      title: '金额',
      dataIndex: 'amount',
      key: 'amount',
      width: 120,
      render: (amount: number, record: RecordItem) => (
        <span
          style={{
            fontWeight: 600,
            fontSize: 16,
            color: record.type === 'expense' ? '#ff4d4f' : '#52c41a',
          }}
        >
          {record.type === 'expense' ? '-' : '+'}¥{Number(amount).toFixed(2)}
        </span>
      ),
    },
    {
      title: '备注',
      dataIndex: 'note',
      key: 'note',
      ellipsis: true,
      render: (note: string) => (
        <span style={{ color: note ? '#333' : '#ccc' }}>{note || '无'}</span>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 80,
      render: (_: any, record: RecordItem) => (
        <Popconfirm
          title="确定删除这条记录吗？"
          onConfirm={() => handleDelete(record.id)}
          okText="确定"
          cancelText="取消"
        >
          <Button type="link" danger icon={<DeleteOutlined />} size="small">
            删除
          </Button>
        </Popconfirm>
      ),
    },
  ]

  const totalIncome = records.filter((r) => r.type === 'income').reduce((s, r) => s + Number(r.amount), 0)
  const totalExpense = records.filter((r) => r.type === 'expense').reduce((s, r) => s + Number(r.amount), 0)

  return (
    <div>
      <h2 style={{ marginBottom: 24, fontSize: 22 }}>收支明细</h2>

      <Card style={{ borderRadius: 12 }}>
        <div style={{ display: 'flex', gap: 16, marginBottom: 16, flexWrap: 'wrap', alignItems: 'center' }}>
          <Segmented
            value={filterType}
            onChange={(val) => setFilterType(val as string)}
            options={[
              { label: '全部', value: 'all' },
              { label: '🔴 支出', value: 'expense' },
              { label: '🟢 收入', value: 'income' },
            ]}
          />
          <DatePicker
            picker="month"
            value={filterMonth}
            onChange={(date) => setFilterMonth(date || dayjs())}
            allowClear={false}
          />
          <Button onClick={loadRecords} type="default">
            刷新
          </Button>
        </div>

        <div
          style={{
            display: 'flex',
            gap: 24,
            marginBottom: 16,
            padding: '12px 16px',
            background: '#fafafa',
            borderRadius: 8,
            flexWrap: 'wrap',
          }}
        >
          <span>
            🟢 收入合计：<strong style={{ color: '#52c41a', fontSize: 16 }}>¥{totalIncome.toFixed(2)}</strong>
          </span>
          <span>
            🔴 支出合计：<strong style={{ color: '#ff4d4f', fontSize: 16 }}>¥{totalExpense.toFixed(2)}</strong>
          </span>
          <span>
            📊 结余：
            <strong style={{ color: totalIncome - totalExpense >= 0 ? '#1677ff' : '#ff4d4f', fontSize: 16 }}>
              ¥{(totalIncome - totalExpense).toFixed(2)}
            </strong>
          </span>
        </div>

        <Table
          columns={columns}
          dataSource={records}
          rowKey="id"
          loading={loading}
          locale={{ emptyText: '暂无记录，快去记一笔吧！' }}
          pagination={{ pageSize: 20, showSizeChanger: false, showTotal: (total) => `共 ${total} 条记录` }}
        />
      </Card>
    </div>
  )
}

export default RecordList

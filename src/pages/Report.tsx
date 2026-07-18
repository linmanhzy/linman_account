import React, { useEffect, useState } from 'react'
import { Card, Row, Col, Segmented, Select, Button, Space, message } from 'antd'
import { DownloadOutlined } from '@ant-design/icons'
import ReactECharts from 'echarts-for-react'
import dayjs from 'dayjs'
import { getTrend, getCategoryProportion, exportRecords } from '../api/reports'
import type { TrendPoint, CategoryProportion } from '../types'

const Report: React.FC = () => {
  const [months, setMonths] = useState<number>(12)
  const [trend, setTrend] = useState<TrendPoint[]>([])
  const [proportion, setProportion] = useState<CategoryProportion[]>([])
  const [pieMonth, setPieMonth] = useState<string | undefined>(undefined)
  const [loadingTrend, setLoadingTrend] = useState(false)
  const [loadingPie, setLoadingPie] = useState(false)

  const loadTrend = async () => {
    setLoadingTrend(true)
    try {
      const data = await getTrend(months)
      setTrend(data)
    } catch (err: any) {
      message.error(err?.message || '加载趋势数据失败')
    } finally {
      setLoadingTrend(false)
    }
  }

  const loadPie = async () => {
    setLoadingPie(true)
    try {
      const data = await getCategoryProportion(pieMonth ? { month: pieMonth } : {})
      setProportion(data)
    } catch (err: any) {
      message.error(err?.message || '加载分类占比失败')
    } finally {
      setLoadingPie(false)
    }
  }

  useEffect(() => {
    loadTrend()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [months])

  useEffect(() => {
    loadPie()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pieMonth])

  const trendOption = {
    tooltip: { trigger: 'axis' },
    legend: { data: ['收入', '支出'] },
    grid: { left: 48, right: 24, top: 40, bottom: 40 },
    xAxis: { type: 'category', boundaryGap: false, data: trend.map((t) => t.month) },
    yAxis: { type: 'value' },
    series: [
      {
        name: '收入',
        type: 'line',
        smooth: true,
        showSymbol: false,
        data: trend.map((t) => Number(t.income)),
        itemStyle: { color: '#52c41a' },
        areaStyle: { opacity: 0.08 },
      },
      {
        name: '支出',
        type: 'line',
        smooth: true,
        showSymbol: false,
        data: trend.map((t) => Number(t.expense)),
        itemStyle: { color: '#ff4d4f' },
        areaStyle: { opacity: 0.08 },
      },
    ],
  }

  const pieOption = {
    tooltip: { trigger: 'item', formatter: '{b}: {c} 元 ({d}%)' },
    legend: { bottom: 0, type: 'scroll' },
    series: [
      {
        name: '支出占比',
        type: 'pie',
        radius: ['40%', '68%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
        label: { formatter: '{b}\n{d}%' },
        data: proportion.map((p) => ({
          name: p.category,
          value: Number(p.amount),
          pct: Number(p.percentage),
        })),
      },
    ],
  }

  // 最近 12 个月，供饼图筛选
  const monthOptions = Array.from({ length: 12 }, (_, i) => {
    const d = dayjs().subtract(i, 'month')
    return { value: d.format('YYYY-MM'), label: d.format('YYYY年M月') }
  })

  const onExport = async (format: 'excel' | 'csv') => {
    const hide = message.loading({ content: '正在生成文件…', key: 'export' })
    try {
      await exportRecords(format)
      message.success({ content: '导出成功，已开始下载', key: 'export' })
    } catch (err: any) {
      hide()
      message.error({ content: err?.message || '导出失败', key: 'export' })
    }
  }

  return (
    <div>
      <h2 style={{ marginBottom: 24, fontSize: 22 }}>报表与导出</h2>
      <Row gutter={[16, 16]}>
        <Col xs={24}>
          <Card
            title="消费趋势"
            loading={loadingTrend}
            extra={
              <Segmented
                options={[
                  { label: '近 6 个月', value: 6 },
                  { label: '近 12 个月', value: 12 },
                ]}
                value={months}
                onChange={(v) => setMonths(v as number)}
              />
            }
          >
            <ReactECharts option={trendOption} style={{ height: 320 }} notMerge />
          </Card>
        </Col>

        <Col xs={24}>
          <Card
            title="支出分类占比"
            loading={loadingPie}
            extra={
              <Select
                placeholder="全部时间"
                allowClear
                value={pieMonth}
                onChange={(v) => setPieMonth(v)}
                options={monthOptions}
                style={{ width: 150 }}
              />
            }
          >
            {proportion.length === 0 && !loadingPie ? (
              <div style={{ textAlign: 'center', color: '#999', padding: '80px 0' }}>
                暂无支出数据
              </div>
            ) : (
              <ReactECharts option={pieOption} style={{ height: 320 }} notMerge />
            )}
          </Card>
        </Col>

        <Col xs={24}>
          <Card title="导出账本">
            <p style={{ color: '#999', marginBottom: 16 }}>
              导出「你自己的全部收支记录」，方便备份或做账。Excel 用 Excel/WPS 打开，CSV 可用任意表格软件打开。
            </p>
            <Space>
              <Button type="primary" icon={<DownloadOutlined />} onClick={() => onExport('excel')}>
                导出 Excel
              </Button>
              <Button icon={<DownloadOutlined />} onClick={() => onExport('csv')}>
                导出 CSV
              </Button>
            </Space>
          </Card>
        </Col>
      </Row>
    </div>
  )
}

export default Report

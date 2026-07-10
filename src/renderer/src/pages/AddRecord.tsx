import React, { useState } from 'react'
import { Card, Form, InputNumber, DatePicker, Input, Button, Segmented, message } from 'antd'
import dayjs from 'dayjs'
import CategorySelect from '../components/CategorySelect'

interface Props {
  onSuccess?: () => void
}

const AddRecord: React.FC<Props> = ({ onSuccess }) => {
  const [form] = Form.useForm()
  const [type, setType] = useState<'expense' | 'income'>('expense')
  const [category, setCategory] = useState<{ l1: string; l2: string }>({ l1: '', l2: '' })
  const [loading, setLoading] = useState(false)

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()

      if (!category.l1 || !category.l2) {
        message.warning('请选择分类')
        return
      }

      setLoading(true)
      await window.electronAPI.addRecord({
        type,
        amount: values.amount,
        date: values.date.format('YYYY-MM-DD'),
        categoryL1: category.l1,
        categoryL2: category.l2,
        note: values.note || ''
      })

      message.success('记录成功！')
      form.resetFields()
      setCategory({ l1: '', l2: '' })

      if (onSuccess) {
        onSuccess()
      }
    } catch (err) {
      // 表单校验失败
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{ maxWidth: 600 }}>
      <h2 style={{ marginBottom: 24, fontSize: 22 }}>记一笔</h2>

      <Card style={{ borderRadius: 12 }}>
        <Form
          form={form}
          layout="vertical"
          initialValues={{
            date: dayjs()
          }}
        >
          {/* 收支类型 */}
          <Form.Item label="类型">
            <Segmented
              block
              size="large"
              value={type}
              onChange={(val) => {
                setType(val as 'expense' | 'income')
                setCategory({ l1: '', l2: '' })
              }}
              options={[
                { label: '🔴 支出', value: 'expense' },
                { label: '🟢 收入', value: 'income' }
              ]}
            />
          </Form.Item>

          {/* 金额 */}
          <Form.Item
            label="金额（元）"
            name="amount"
            rules={[{ required: true, message: '请输入金额' }]}
          >
            <InputNumber
              style={{ width: '100%' }}
              placeholder="例如：12.50"
              min={0.01}
              precision={2}
              size="large"
              prefix="¥"
            />
          </Form.Item>

          {/* 日期 */}
          <Form.Item
            label="日期"
            name="date"
            rules={[{ required: true, message: '请选择日期' }]}
          >
            <DatePicker style={{ width: '100%' }} size="large" />
          </Form.Item>

          {/* 分类 */}
          <Form.Item label="分类">
            <CategorySelect
              type={type}
              value={category}
              onChange={setCategory}
            />
          </Form.Item>

          {/* 备注 */}
          <Form.Item label="备注（可选）" name="note">
            <Input.TextArea
              placeholder="写点什么..."
              rows={2}
              maxLength={200}
              showCount
            />
          </Form.Item>

          {/* 提交 */}
          <Form.Item>
            <Button
              type="primary"
              size="large"
              block
              loading={loading}
              onClick={handleSubmit}
              style={{ height: 44, fontSize: 16, borderRadius: 8 }}
            >
              保存记录
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  )
}

export default AddRecord

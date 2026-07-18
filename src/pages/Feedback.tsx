import { useState, useEffect } from 'react'
import { Card, Form, Input, Button, List, Tag, message, Spin, Empty } from 'antd'
import { MessageOutlined, SendOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { submitFeedback, getMyFeedbacks } from '../api/feedback'
import type { FeedbackResponse } from '../types'

const { TextArea } = Input

const statusMap: Record<string, { color: string; text: string }> = {
  PENDING: { color: 'orange', text: '处理中' },
  REPLIED: { color: 'green', text: '已回复' },
}

export default function FeedbackPage() {
  const [form] = Form.useForm()
  const [submitting, setSubmitting] = useState(false)
  const [feedbacks, setFeedbacks] = useState<FeedbackResponse[]>([])
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    loadFeedbacks()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const loadFeedbacks = async () => {
    setLoading(true)
    try {
      const list = await getMyFeedbacks()
      setFeedbacks(list)
    } catch {
      message.error('加载反馈列表失败')
    } finally {
      setLoading(false)
    }
  }

  const handleSubmit = async (values: { content: string }) => {
    setSubmitting(true)
    try {
      const fb = await submitFeedback(values.content)
      setFeedbacks([fb, ...feedbacks])
      form.resetFields()
      message.success('反馈提交成功')
    } catch {
      message.error('提交失败，请重试')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div style={{ maxWidth: 720, margin: '0 auto' }}>
      <Card
        title={
          <span>
            <MessageOutlined style={{ marginRight: 8 }} />
            提交反馈
          </span>
        }
        style={{ borderRadius: 12, marginBottom: 24 }}
      >
        <Form form={form} onFinish={handleSubmit} layout="vertical">
          <Form.Item
            name="content"
            rules={[
              { required: true, message: '请输入反馈内容' },
              { min: 5, message: '至少 5 个字符' },
              { max: 500, message: '最多 500 个字符' },
            ]}
          >
            <TextArea
              rows={4}
              placeholder="请描述你的问题、建议或投诉……"
              maxLength={500}
              showCount
            />
          </Form.Item>
          <Form.Item>
            <Button
              type="primary"
              htmlType="submit"
              icon={<SendOutlined />}
              loading={submitting}
              block
            >
              提交反馈
            </Button>
          </Form.Item>
        </Form>
      </Card>

      <Card
        title="反馈历史"
        style={{ borderRadius: 12 }}
      >
        {loading ? (
          <div style={{ textAlign: 'center', padding: 40 }}>
            <Spin />
          </div>
        ) : feedbacks.length === 0 ? (
          <Empty description="还没有提交过反馈" />
        ) : (
          <List
            dataSource={feedbacks}
            renderItem={(item) => (
              <List.Item
                key={item.id}
                style={{ flexDirection: 'column', alignItems: 'flex-start' }}
              >
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    width: '100%',
                    marginBottom: 8,
                  }}
                >
                  <span style={{ fontWeight: 500, fontSize: 15 }}>{item.content}</span>
                  <Tag color={statusMap[item.status]?.color || 'default'}>
                    {statusMap[item.status]?.text || item.status}
                  </Tag>
                </div>
                <div style={{ fontSize: 12, color: '#999' }}>
                  {dayjs(item.createdAt).format('YYYY-MM-DD HH:mm')}
                </div>
                {item.reply && (
                  <Card
                    size="small"
                    style={{
                      marginTop: 8,
                      width: '100%',
                      background: '#f6ffed',
                      border: '1px solid #b7eb8f',
                    }}
                  >
                    <div style={{ fontSize: 13, color: '#389e0d' }}>
                      <strong>管理员回复：</strong>
                      {item.reply}
                    </div>
                  </Card>
                )}
              </List.Item>
            )}
          />
        )}
      </Card>
    </div>
  )
}

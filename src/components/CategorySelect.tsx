import React from 'react'
import { Select, Space } from 'antd'
import { useCategoryContext } from '../context/CategoryContext'
import type { CategoryL1 } from '../data/categories'

interface Props {
  type: 'expense' | 'income'
  value?: { l1: string; l2: string }
  onChange?: (value: { l1: string; l2: string }) => void
}

const CategorySelect: React.FC<Props> = ({ type, value, onChange }) => {
  const { expenseCategories, incomeCategories } = useCategoryContext()
  const categories: CategoryL1[] = type === 'expense' ? expenseCategories : incomeCategories

  const selectedL1 = categories.find(c => c.name === value?.l1)

  const handleL1Change = (l1: string) => {
    const cat = categories.find(c => c.name === l1)
    const defaultL2 = cat?.children[0] || ''
    onChange?.({ l1, l2: defaultL2 })
  }

  const handleL2Change = (l2: string) => {
    onChange?.({ l1: value?.l1 || '', l2 })
  }

  return (
    <Space.Compact style={{ width: '100%' }}>
      <Select
        placeholder="选择大类"
        value={value?.l1 || undefined}
        onChange={handleL1Change}
        style={{ width: '50%' }}
        options={categories.map(c => ({
          label: `${c.icon} ${c.name}`,
          value: c.name
        }))}
      />
      <Select
        placeholder="选择小类"
        value={value?.l2 || undefined}
        onChange={handleL2Change}
        style={{ width: '50%' }}
        disabled={!value?.l1}
        options={selectedL1?.children.map(child => ({
          label: child,
          value: child
        }))}
      />
    </Space.Compact>
  )
}

export default CategorySelect

// 支出和收入的二级分类数据

export interface CategoryL1 {
  name: string
  icon: string
  children: string[]
}

// 支出分类
export const expenseCategories: CategoryL1[] = [
  {
    name: '餐饮饮食',
    icon: '🍜',
    children: ['早餐', '午餐', '晚餐', '零食饮品', '聚餐请客']
  },
  {
    name: '交通出行',
    icon: '🚗',
    children: ['公交地铁', '打车拼车', '加油充电', '停车费', '车辆保养']
  },
  {
    name: '购物消费',
    icon: '🛒',
    children: ['服装鞋帽', '日用品', '数码电子', '家居装饰', '护肤美妆']
  },
  {
    name: '居住住房',
    icon: '🏠',
    children: ['房租', '水电燃气', '物业费', '维修保养', '网费话费']
  },
  {
    name: '医疗健康',
    icon: '💊',
    children: ['看病挂号', '药品购买', '体检检查', '医疗器材']
  },
  {
    name: '休闲娱乐',
    icon: '🎮',
    children: ['电影演出', '游戏充值', '旅游出行', '运动健身', '宠物开销']
  },
  {
    name: '教育学习',
    icon: '📚',
    children: ['课程培训', '书籍资料', '文具用品', '考试报名']
  },
  {
    name: '人情往来',
    icon: '🎁',
    children: ['份子红包', '孝敬长辈', '请客送礼', '慈善捐款']
  },
  {
    name: '其他支出',
    icon: '📦',
    children: ['快递邮费', '金融服务费', '其他杂项']
  }
]

// 收入分类
export const incomeCategories: CategoryL1[] = [
  {
    name: '工资薪酬',
    icon: '💼',
    children: ['基本工资', '绩效奖金', '加班补贴', '年终奖金']
  },
  {
    name: '副业兼职',
    icon: '💰',
    children: ['自由职业', '咨询服务', '稿费版税', '设计制作']
  },
  {
    name: '投资理财',
    icon: '📈',
    children: ['股票基金收益', '银行利息', '房屋租金', '分红']
  },
  {
    name: '红包礼金',
    icon: '🎁',
    children: ['节日红包', '生日礼金', '婚礼份子回收', '长辈给的钱']
  },
  {
    name: '退款返利',
    icon: '🔄',
    children: ['购物退款', '返利提现', '押金退还', '保险理赔']
  },
  {
    name: '其他收入',
    icon: '📦',
    children: ['二手出售', '报销款', '意外所得', '其他']
  }
]

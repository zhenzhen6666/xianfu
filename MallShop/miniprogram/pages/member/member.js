const api = require('../../utils/api')
const adapters = require('../../utils/adapters')

Page({
  data: {
    user: { id: 0, nickname: '鲜福铺用户', memberLevelName: '普通会员', memberLevelCode: 'NORMAL', points: 0, totalSpent: 0 },
    pointsBalance: 0,
    nextLevelText: '继续消费可提升会员等级',
    rights: [{ icon: '价', name: '会员专价', desc: '精选商品更省' }, { icon: '×2', name: '积分加速', desc: '等级越高越划算' }, { icon: '券', name: '会员券包', desc: '好券服务端发放' }, { icon: '礼', name: '生日福利', desc: '专属惊喜礼' }],
    coupons: []
  },
  onShow() { this.loadMember() },
  async loadMember() {
    try {
      const [user, points, coupons] = await Promise.all([api.profile(), api.points(), api.coupons('ALL')])
      const targets = { NORMAL: 500, SILVER: 2000, GOLD: 5000, DIAMOND: 5000 }
      const target = targets[user.memberLevelCode] || 500
      const difference = Math.max(0, target - Number(user.totalSpent || 0))
      this.setData({
        user,
        pointsBalance: points.balance || 0,
        nextLevelText: user.memberLevelCode === 'DIAMOND' ? '已达到最高会员等级' : `距离下一级还需消费 ¥${adapters.money(difference)}`,
        coupons: coupons.map(item => ({
          id: item.id,
          code: item.code,
          name: item.name,
          amount: Number(item.amount),
          condition: Number(item.minimumAmount) > 0 ? `满 ${item.minimumAmount} 元可用` : '无门槛可用',
          status: item.status,
          date: ({ AVAILABLE: '可使用', USED: '已使用', EXPIRED: '已过期', NOT_STARTED: '未开始' })[item.status] || item.status
        }))
      })
    } catch (error) { wx.showToast({ title: error.message, icon: 'none' }) }
  },
  useCoupon(e) {
    if (e.currentTarget.dataset.status !== 'AVAILABLE') {
      return wx.showToast({ title: '该优惠券当前不可使用', icon: 'none' })
    }
    wx.navigateTo({ url: '/pages/checkout/checkout' })
  },
  goPoints() { wx.showToast({ title: `当前可用 ${this.data.pointsBalance} 积分`, icon: 'none' }) }
})

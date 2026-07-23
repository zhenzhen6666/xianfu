Page({
  data: {
    rights: [{ icon: '价', name: '会员专价', desc: '精选商品更省' }, { icon: '×2', name: '双倍积分', desc: '消费积分加速' }, { icon: '券', name: '每月券包', desc: '至高 30 元' }, { icon: '礼', name: '生日福利', desc: '专属惊喜礼' }],
    coupons: [{ amount: 8, condition: '满 59 元可用', date: '本月有效' }, { amount: 5, condition: '满 39 元可用', date: '本月有效' }]
  },
  getCoupon() { wx.showToast({ title: '优惠券已放入卡包', icon: 'success' }) },
  goPoints() { wx.showToast({ title: '积分商城即将开放', icon: 'none' }) }
})

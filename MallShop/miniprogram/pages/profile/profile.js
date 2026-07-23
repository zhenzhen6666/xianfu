Page({
  data: {
    menus: [
      { icon: '券', name: '优惠券', value: '3 张', action: 'coupon' }, { icon: '分', name: '积分', value: '2,680', action: 'member' },
      { icon: '址', name: '收货地址', value: '', action: 'address' }, { icon: '员', name: '会员中心', value: '', action: 'member' },
      { icon: '藏', name: '商品收藏', value: '8', action: 'favorite' }, { icon: '服', name: '在线客服', value: '', action: 'service' },
      { icon: '邀', name: '邀请有礼', value: '', action: 'share' }, { icon: '设', name: '系统设置', value: '', action: 'settings' }
    ]
  },
  goMember() { wx.navigateTo({ url: '/pages/member/member' }) },
  goOrders(e) {
    const status = e.currentTarget.dataset.status
    if (status) wx.setStorageSync('xianfupu_order_tab', status)
    wx.switchTab({ url: '/pages/orders/orders' })
  },
  menuTap(e) {
    const action = e.currentTarget.dataset.action
    if (action === 'member') return this.goMember()
    if (action === 'service') return wx.showModal({ title: '鲜福铺在线客服', content: '售后咨询：工作日 08:00—21:00\n商家电话：400-888-6699', confirmText: '知道了', showCancel: false, confirmColor: '#168A51' })
    if (action === 'share') return wx.showShareMenu({ withShareTicket: true })
    wx.showToast({ title: '功能入口已就绪', icon: 'none' })
  },
  onShareAppMessage() { return { title: '鲜福铺新人礼，三公里鲜到家', path: '/pages/home/home' } }
})

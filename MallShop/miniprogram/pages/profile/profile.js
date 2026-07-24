const api = require('../../utils/api')

Page({
  data: {
    user: { nickname: '鲜福铺用户', memberLevelName: '普通会员', points: 0, totalSpent: 0 },
    avatarText: '鲜',
    couponCount: 0,
    favoriteCount: 0,
    menus: []
  },
  onShow() { this.loadProfile() },
  async loadProfile() {
    try {
      const [user, coupons, points, favorites] = await Promise.all([
        api.profile(), api.coupons('AVAILABLE'), api.points(), api.favorites()
      ])
      const menus = [
        { icon: '券', name: '优惠券', value: `${coupons.length} 张`, action: 'coupon' },
        { icon: '分', name: '积分', value: String(points.balance || 0), action: 'member' },
        { icon: '址', name: '收货地址', value: '', action: 'address' },
        { icon: '员', name: '会员中心', value: '', action: 'member' },
        { icon: '藏', name: '商品收藏', value: String(favorites.length), action: 'favorite' },
        { icon: '服', name: '在线客服', value: '', action: 'service' },
        { icon: '邀', name: '邀请有礼', value: '', action: 'share' },
        { icon: '设', name: '接口设置', value: '', action: 'settings' }
      ]
      this.setData({ user, avatarText: (user.nickname || '鲜').slice(0, 1), couponCount: coupons.length, favoriteCount: favorites.length, menus })
    } catch (error) { wx.showToast({ title: error.message, icon: 'none', duration: 3000 }) }
  },
  goMember() { wx.navigateTo({ url: '/pages/member/member' }) },
  goOrders(e) {
    const status = e.currentTarget.dataset.status
    if (status) wx.setStorageSync('xianfupu_order_tab', status)
    wx.switchTab({ url: '/pages/orders/orders' })
  },
  menuTap(e) {
    const action = e.currentTarget.dataset.action
    if (action === 'member' || action === 'coupon') return this.goMember()
    if (action === 'service') return wx.showModal({ title: '鲜福铺在线客服', content: '售后咨询：工作日 08:00—21:00\n商家电话：400-888-6699', confirmText: '知道了', showCancel: false, confirmColor: '#168A51' })
    if (action === 'share') return wx.showShareMenu({ withShareTicket: true })
    if (action === 'settings') {
      return wx.showModal({ title: '当前后端接口', content: getApp().globalData.apiBaseUrl, showCancel: false, confirmColor: '#168A51' })
    }
    wx.showToast({ title: '功能入口已就绪', icon: 'none' })
  },
  onShareAppMessage() { return { title: '鲜福铺新人礼，三公里鲜到家', path: '/pages/home/home' } }
})

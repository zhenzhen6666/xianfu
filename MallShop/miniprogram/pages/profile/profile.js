const api = require('../../utils/api')
const config = require('../../utils/config')

Page({
  data: {
    user: { nickname: '鲜福铺用户', memberLevelName: '普通会员', points: 0, totalSpent: 0 },
    avatarText: '鲜',
    avatarUrl: '',
    mobileText: '',
    nicknameEditing: false,
    nicknameDraft: '',
    avatarUploading: false,
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
        { icon: '分', name: '积分', value: String(points.balance || 0), action: 'points' },
        { icon: '址', name: '收货地址', value: '', action: 'address' },
        { icon: '员', name: '会员中心', value: '', action: 'member' },
        { icon: '藏', name: '商品收藏', value: String(favorites.length), action: 'favorite' },
        { icon: '服', name: '在线客服', value: '', action: 'service' },
        { icon: '邀', name: '邀请有礼', value: '', action: 'share' },
        { icon: '设', name: '接口设置', value: '', action: 'settings' }
      ]
      this.applyUser(user)
      this.setData({ couponCount: coupons.length, favoriteCount: favorites.length, menus })
    } catch (error) { wx.showToast({ title: error.message, icon: 'none', duration: 3000 }) }
  },
  applyUser(user) {
    const mobile = user.mobile || ''
    wx.setStorageSync(config.USER_KEY, user)
    this.setData({
      user,
      avatarText: (user.nickname || '鲜').slice(0, 1),
      avatarUrl: config.resolveApiFileUrl(user.avatarUrl),
      mobileText: mobile ? `${mobile.slice(0, 3)}****${mobile.slice(-4)}` : '',
      nicknameDraft: user.nickname || ''
    })
  },
  openNicknameEdit() {
    this.setData({ nicknameEditing: true, nicknameDraft: this.data.user.nickname || '' })
  },
  closeNicknameEdit() { this.setData({ nicknameEditing: false }) },
  nicknameInput(e) { this.setData({ nicknameDraft: e.detail.value }) },
  async saveNickname() {
    const nickname = this.data.nicknameDraft.trim()
    if (!nickname) return wx.showToast({ title: '昵称不能为空', icon: 'none' })
    try {
      const user = await api.updateProfile({ nickname, avatarUrl: this.data.user.avatarUrl || '' })
      this.applyUser(user)
      this.setData({ nicknameEditing: false })
      wx.showToast({ title: '昵称已保存', icon: 'success' })
    } catch (error) { wx.showToast({ title: error.message, icon: 'none' }) }
  },
  async chooseAvatar(e) {
    const filePath = e.detail.avatarUrl
    if (!filePath || this.data.avatarUploading) return
    this.setData({ avatarUploading: true })
    wx.showLoading({ title: '上传头像' })
    try {
      const uploaded = await api.uploadProfile(filePath)
      const user = await api.updateProfile({
        nickname: this.data.user.nickname || '鲜福铺用户',
        avatarUrl: uploaded.url
      })
      this.applyUser(user)
      wx.showToast({ title: '头像已更新', icon: 'success' })
    } catch (error) {
      wx.showToast({ title: error.message || '头像上传失败', icon: 'none' })
    } finally {
      wx.hideLoading()
      this.setData({ avatarUploading: false })
    }
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
    if (action === 'coupon') return wx.navigateTo({ url: '/pages/coupons/coupons' })
    if (action === 'favorite') return wx.navigateTo({ url: '/pages/favorites/favorites' })
    if (action === 'points') return wx.navigateTo({ url: '/pages/points/points' })
    if (action === 'address') return wx.navigateTo({ url: '/pages/addresses/addresses' })
    if (action === 'service') return wx.showModal({ title: '鲜福铺在线客服', content: '售后咨询：工作日 08:00—21:00\n商家电话：400-888-6699', confirmText: '知道了', showCancel: false, confirmColor: '#168A51' })
    if (action === 'share') return wx.showShareMenu({ withShareTicket: true })
    if (action === 'settings') {
      return wx.showModal({ title: '当前后端接口', content: getApp().globalData.apiBaseUrl, showCancel: false, confirmColor: '#168A51' })
    }
    wx.showToast({ title: '功能入口已就绪', icon: 'none' })
  },
  async bindMobile(e) {
    if (!e.detail.code) return wx.showToast({ title: '未授权获取手机号', icon: 'none' })
    try {
      const user = await api.bindWechatMobile(e.detail.code)
      this.applyUser(user)
      wx.showToast({ title: '手机号绑定成功', icon: 'success' })
    } catch (error) { wx.showToast({ title: error.message, icon: 'none' }) }
  },
  goAfterSales() { wx.navigateTo({ url: '/pages/after-sales/after-sales' }) },
  onShareAppMessage() { return { title: '鲜福铺新人礼，三公里鲜到家', path: '/pages/home/home' } }
})

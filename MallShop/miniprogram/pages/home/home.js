const api = require('../../utils/api')
const adapters = require('../../utils/adapters')

Page({
  data: {
    loading: true,
    showGuide: false,
    searchValue: '',
    cartCount: 0,
    products: [],
    delivery: { freeThreshold: 30, baseFee: 3, maxDistanceKm: 3, promiseMinutes: 30, announcement: '城区 3 公里配送' },
    quicks: [
      { icon: '¥0.01', name: '一分专享', theme: 'mint', type: 'penny' },
      { icon: '券', name: '新人福利', theme: 'orange', type: 'new' },
      { icon: '净', name: '绿捷净菜', theme: 'pink', type: 'clean' },
      { icon: '砸', name: '欢乐金蛋', theme: 'yellow', type: 'egg' }
    ]
  },

  onLoad() {
    this.loadHome()
    if (!wx.getStorageSync('xianfupu_guided')) {
      setTimeout(() => this.setData({ showGuide: true }), 620)
    }
  },

  onShow() { this.refreshCart() },

  async loadHome() {
    this.setData({ loading: true })
    try {
      const result = await api.home()
      this.setData({
        products: (result.recommendedProducts || []).map(adapters.product),
        delivery: result.delivery || this.data.delivery,
        loading: false
      })
    } catch (error) {
      this.setData({ loading: false })
      wx.showToast({ title: error.message, icon: 'none', duration: 3000 })
    }
  },

  async onPullDownRefresh() {
    await Promise.all([this.loadHome(), this.refreshCart()])
    wx.stopPullDownRefresh()
  },
  onShareAppMessage() { return { title: '鲜福铺 · 三公里鲜到家', path: '/pages/home/home' } },

  async refreshCart() {
    try {
      const result = await api.cart()
      this.setData({ cartCount: (result.items || []).reduce((sum, item) => sum + item.count, 0) })
    } catch (error) {
      console.warn('购物车数量加载失败：', error.message)
    }
  },
  noop() {},
  bindSearch(e) { this.setData({ searchValue: e.detail.value }) },
  doSearch() {
    wx.setStorageSync('xianfupu_search', this.data.searchValue.trim())
    wx.switchTab({ url: '/pages/category/category' })
  },
  goCategory() { wx.switchTab({ url: '/pages/category/category' }) },
  goCart() { wx.navigateTo({ url: '/pages/cart/cart' }) },
  goActivity(e) { wx.navigateTo({ url: `/pages/activity/activity?type=${e.currentTarget.dataset.type}` }) },
  async addCart(e) {
    try {
      await api.addCart(e.currentTarget.dataset.id, 1)
      await this.refreshCart()
      wx.showToast({ title: '已放入购物车', icon: 'success' })
    } catch (error) {
      wx.showToast({ title: error.message, icon: 'none' })
    }
  },
  closeGuide() {
    wx.setStorageSync('xianfupu_guided', true)
    this.setData({ showGuide: false })
  }
})

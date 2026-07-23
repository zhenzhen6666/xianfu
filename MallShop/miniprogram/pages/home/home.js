const store = require('../../utils/store')

Page({
  data: {
    loading: true,
    showGuide: false,
    searchValue: '',
    cartCount: 0,
    products: store.PRODUCTS.slice(0, 6),
    quicks: [
      { icon: '¥0.01', name: '一分专享', theme: 'mint', type: 'penny' },
      { icon: '券', name: '新人福利', theme: 'orange', type: 'new' },
      { icon: '净', name: '绿捷净菜', theme: 'pink', type: 'clean' },
      { icon: '砸', name: '欢乐金蛋', theme: 'yellow', type: 'egg' }
    ]
  },

  onLoad() {
    setTimeout(() => this.setData({ loading: false }), 360)
    if (!wx.getStorageSync('xianfupu_guided')) {
      setTimeout(() => this.setData({ showGuide: true }), 620)
    }
  },

  onShow() { this.refreshCart() },
  onPullDownRefresh() {
    this.setData({ loading: true })
    setTimeout(() => { this.setData({ loading: false }); wx.stopPullDownRefresh() }, 500)
  },
  onShareAppMessage() { return { title: '鲜福铺 · 三公里鲜到家', path: '/pages/home/home' } },

  refreshCart() {
    const cartCount = store.getCart().reduce((sum, item) => sum + item.count, 0)
    this.setData({ cartCount })
  },
  noop() {},
  bindSearch(e) { this.setData({ searchValue: e.detail.value }) },
  doSearch() {
    const value = this.data.searchValue.trim()
    wx.setStorageSync('xianfupu_search', value)
    wx.switchTab({ url: '/pages/category/category' })
  },
  goCategory() { wx.switchTab({ url: '/pages/category/category' }) },
  goCart() { wx.navigateTo({ url: '/pages/cart/cart' }) },
  goActivity(e) { wx.navigateTo({ url: `/pages/activity/activity?type=${e.currentTarget.dataset.type}` }) },
  addCart(e) {
    store.addToCart(e.currentTarget.dataset.id)
    this.refreshCart()
    wx.showToast({ title: '已放入购物车', icon: 'success' })
  },
  closeGuide() {
    wx.setStorageSync('xianfupu_guided', true)
    this.setData({ showGuide: false })
  }
})

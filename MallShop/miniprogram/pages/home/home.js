const api = require('../../utils/api')
const adapters = require('../../utils/adapters')
const { resolveApiFileUrl } = require('../../utils/config')

const DEFAULT_QUICKS = [
  { icon: '¥0.01', name: '一分专享', theme: 'mint', type: 'penny' },
  { icon: '券', name: '新人福利', theme: 'orange', type: 'new' },
  { icon: '净', name: '绿捷净菜', theme: 'pink', type: 'clean' },
  { icon: '砸', name: '欢乐金蛋', theme: 'yellow', type: 'egg' }
]
const DEFAULT_ZONES = [
  { kicker: '省心搭配', title: '在家做饭', linkText: '去看看 →', image: '/assets/products/mushroom.jpg', theme: 'pink', type: 'clean' },
  { kicker: '本周爆款', title: '9.9 特惠', linkText: '限时抢 →', image: '/assets/products/orange.jpg', theme: 'yellow', type: 'deal' }
]

Page({
  data: {
    loading: true,
    showGuide: false,
    searchValue: '',
    cartCount: 0,
    products: [],
    delivery: { freeThreshold: 30, baseFee: 3, maxDistanceKm: 3, promiseMinutes: 30, announcement: '城区 3 公里配送' },
    showNotice: true,
    notice: { icon: '闪', primaryText: '城区 3 公里配送', secondaryText: '30 分钟送货上门' },
    quicks: DEFAULT_QUICKS,
    zones: DEFAULT_ZONES
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
      const modules = result.modules || []
      const announcement = modules.find(item => item.moduleType === 'ANNOUNCEMENT')
      const quickEntry = modules.find(item => item.moduleType === 'QUICK_ENTRY')
      const activityZone = modules.find(item => item.moduleType === 'ACTIVITY_ZONE')
      const delivery = result.delivery || this.data.delivery
      const noticeContent = announcement && announcement.content
      const quickItems = quickEntry && quickEntry.content && quickEntry.content.items
      const zoneItems = activityZone && activityZone.content && activityZone.content.items
      this.setData({
        products: (result.recommendedProducts || []).map(adapters.product),
        delivery,
        showNotice: Boolean(announcement),
        notice: noticeContent ? {
          icon: noticeContent.icon || '闪',
          primaryText: noticeContent.primaryText || noticeContent.text || `城区 ${delivery.maxDistanceKm} 公里配送`,
          secondaryText: noticeContent.secondaryText || `${delivery.promiseMinutes} 分钟送货上门`
        } : {
          icon: '闪',
          primaryText: `城区 ${delivery.maxDistanceKm} 公里配送`,
          secondaryText: `${delivery.promiseMinutes} 分钟送货上门`
        },
        quicks: !quickEntry ? [] : Array.isArray(quickItems)
          ? quickItems.map((item, index) => Object.assign({}, DEFAULT_QUICKS[index % DEFAULT_QUICKS.length], item))
          : DEFAULT_QUICKS,
        zones: !activityZone ? [] : Array.isArray(zoneItems)
          ? zoneItems.map((item, index) => {
            const zone = Object.assign({}, DEFAULT_ZONES[index % DEFAULT_ZONES.length], item)
            zone.image = resolveApiFileUrl(zone.image)
            return zone
          })
          : DEFAULT_ZONES,
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
  goProduct(e) { wx.navigateTo({ url: `/pages/product-detail/product-detail?id=${e.currentTarget.dataset.id}` }) },
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

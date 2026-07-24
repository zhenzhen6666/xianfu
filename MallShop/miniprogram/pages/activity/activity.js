const api = require('../../utils/api')
const adapters = require('../../utils/adapters')

const META = {
  penny: { label: '新人限定 · 每人限购 1 件', color: 'orange' },
  new: { label: '新人好券 · 自动发到卡包', color: 'green' },
  clean: { label: '净菜专区 · 最快 30 分钟到家', color: 'pink' },
  egg: { label: '优惠券 · 实物好礼 · 每日可玩', color: 'yellow' },
  deal: { label: '限时限量 · 售完即止', color: 'orange' }
}

Page({
  data: {
    type: 'new',
    config: { title: '新人福利站', subtitle: '注册即领新人券', label: '', color: 'green', products: [] },
    cartCount: 0,
    eggOpened: false,
    loading: true
  },
  onLoad(options) {
    const type = options.type || 'new'
    this.setData({ type })
    this.loadActivity(type)
  },
  onShow() { this.refreshCart() },
  async loadActivity(type) {
    try {
      const detail = await api.activity(type)
      let products = (detail.products || []).map(adapters.product)
      if (!products.length) {
        const page = await api.products({ sort: type === 'new' ? 'default' : 'sales', page: 0, size: 6 })
        products = (page.items || []).slice(0, type === 'new' ? 4 : 6).map(adapters.product)
      }
      const meta = META[type] || META.new
      const config = {
        title: detail.activity.name,
        subtitle: detail.activity.description,
        label: meta.label,
        color: meta.color,
        products
      }
      this.setData({ config, loading: false })
      wx.setNavigationBarTitle({ title: config.title })
    } catch (error) {
      this.setData({ loading: false })
      wx.showToast({ title: error.message, icon: 'none', duration: 3000 })
    }
  },
  async addCart(e) {
    try {
      const activityCode = ['penny', 'clean', 'deal'].includes(this.data.type) ? this.data.type : undefined
      await api.addCart(e.currentTarget.dataset.id, 1, activityCode)
      await this.refreshCart()
      wx.showToast({ title: activityCode === 'penny' ? '新人价已锁定' : '已加入购物车', icon: 'success' })
    } catch (error) { wx.showToast({ title: error.message, icon: 'none', duration: 3000 }) }
  },
  async refreshCart() {
    try {
      const result = await api.cart()
      this.setData({ cartCount: (result.items || []).reduce((sum, item) => sum + item.count, 0) })
    } catch (error) { console.warn(error.message) }
  },
  async openEgg() {
    try {
      const result = await api.drawEgg()
      this.setData({ eggOpened: true })
      wx.showModal({ title: '砸蛋成功', content: result.message, showCancel: false, confirmColor: '#168A51' })
    } catch (error) { wx.showToast({ title: error.message, icon: 'none', duration: 3000 }) }
  },
  goCart() { wx.navigateTo({ url: '/pages/cart/cart' }) }
})

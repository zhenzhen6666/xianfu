const api = require('../../utils/api')
const config = require('../../utils/config')
Page({
  data: { items: [], loading: true },
  onShow() { this.load() },
  async load() {
    this.setData({ loading: true })
    try {
      const items = (await api.afterSales()).map(item => Object.assign({}, item, {
        images: (item.images || []).map(config.resolveApiFileUrl)
      }))
      this.setData({ items, loading: false })
    }
    catch (error) { this.setData({ loading: false }); wx.showToast({ title: error.message, icon: 'none' }) }
  },
  detail(e) { wx.navigateTo({ url: `/pages/after-sale-detail/after-sale-detail?no=${e.currentTarget.dataset.no}` }) }
})

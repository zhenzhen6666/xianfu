const api = require('../../utils/api')
Page({
  data: { items: [], loading: true },
  onShow() { this.load() },
  async load() {
    this.setData({ loading: true })
    try { this.setData({ items: await api.afterSales(), loading: false }) }
    catch (error) { this.setData({ loading: false }); wx.showToast({ title: error.message, icon: 'none' }) }
  }
})

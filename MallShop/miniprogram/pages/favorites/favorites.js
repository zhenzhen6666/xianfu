const api = require('../../utils/api')
const adapters = require('../../utils/adapters')
Page({
  data: { items: [], loading: true },
  onShow() { this.load() },
  async load() {
    this.setData({ loading: true })
    try { this.setData({ items: (await api.favorites()).map(adapters.product), loading: false }) }
    catch (error) { this.setData({ loading: false }); wx.showToast({ title: error.message, icon: 'none' }) }
  },
  async addCart(e) {
    try { await api.addCart(e.currentTarget.dataset.id); wx.showToast({ title: '已加入购物车', icon: 'success' }) }
    catch (error) { wx.showToast({ title: error.message, icon: 'none' }) }
  },
  async remove(e) {
    try { await api.unfavorite(e.currentTarget.dataset.id); await this.load() }
    catch (error) { wx.showToast({ title: error.message, icon: 'none' }) }
  }
})

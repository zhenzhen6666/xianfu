const api = require('../../utils/api')
Page({
  data: { balance: 0, records: [], loading: true },
  onShow() { this.load() },
  async load() {
    this.setData({ loading: true })
    try { const result = await api.points(); this.setData({ balance: result.balance, records: result.records || [], loading: false }) }
    catch (error) { this.setData({ loading: false }); wx.showToast({ title: error.message, icon: 'none' }) }
  }
})

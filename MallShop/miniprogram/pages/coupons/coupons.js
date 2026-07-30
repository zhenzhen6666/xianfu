const api = require('../../utils/api')
Page({
  data: { tabs: ['ALL','AVAILABLE','USED','EXPIRED'], labels: ['全部','可使用','已使用','已过期'], active: 0, items: [], loading: true },
  onShow() { this.load() },
  select(e) { this.setData({ active: Number(e.currentTarget.dataset.index) }, () => this.load()) },
  async load() {
    this.setData({ loading: true })
    try { this.setData({ items: await api.coupons(this.data.tabs[this.data.active]), loading: false }) }
    catch (error) { this.setData({ loading: false }); wx.showToast({ title: error.message, icon: 'none' }) }
  }
})

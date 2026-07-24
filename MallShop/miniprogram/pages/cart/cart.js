const api = require('../../utils/api')
const adapters = require('../../utils/adapters')

Page({
  data: { items: [], selectedCount: 0, total: '0.00', deliveryFee: '0.00', allSelected: false, remaining: '30.00', progress: 0, loading: false },
  onShow() { this.refresh() },
  async refresh() {
    this.setData({ loading: true })
    try {
      const view = adapters.cart(await api.cart())
      const allSelected = !!view.items.length && view.items.every(item => item.selected)
      const threshold = Number(view.subtotal) + Number(view.remainingForFreeDelivery)
      this.setData({
        items: view.items,
        selectedCount: view.selectedCount,
        total: view.subtotal,
        deliveryFee: view.deliveryFee,
        allSelected,
        remaining: view.remainingForFreeDelivery,
        progress: threshold ? Math.min(100, Math.round(Number(view.subtotal) / threshold * 100)) : 0,
        loading: false
      })
    } catch (error) {
      this.setData({ loading: false })
      wx.showToast({ title: error.message, icon: 'none', duration: 3000 })
    }
  },
  async toggleItem(e) {
    const item = this.data.items.find(row => row.id === e.currentTarget.dataset.id)
    if (!item) return
    try { await api.updateCart(item.id, { selected: !item.selected }); await this.refresh() }
    catch (error) { wx.showToast({ title: error.message, icon: 'none' }) }
  },
  async toggleAll() {
    try { await api.selectAll(!this.data.allSelected); await this.refresh() }
    catch (error) { wx.showToast({ title: error.message, icon: 'none' }) }
  },
  async changeCount(e) {
    const { id, delta } = e.currentTarget.dataset
    const item = this.data.items.find(row => row.id === id)
    if (!item) return
    try { await api.updateCart(id, { quantity: Math.max(1, item.count + Number(delta)) }); await this.refresh() }
    catch (error) { wx.showToast({ title: error.message, icon: 'none' }) }
  },
  removeOne(e) {
    const id = e.currentTarget.dataset.id
    wx.showModal({
      title: '移除商品', content: '确定不买这件商品了吗？', confirmColor: '#168A51',
      success: async result => {
        if (!result.confirm) return
        try { await api.removeCart(id); await this.refresh() }
        catch (error) { wx.showToast({ title: error.message, icon: 'none' }) }
      }
    })
  },
  removeSelected() {
    if (!this.data.items.some(item => item.selected)) return wx.showToast({ title: '请先选择商品', icon: 'none' })
    wx.showModal({
      title: '批量删除', content: '确定删除选中的商品吗？', confirmColor: '#168A51',
      success: async result => {
        if (!result.confirm) return
        try { await api.removeSelected(); await this.refresh() }
        catch (error) { wx.showToast({ title: error.message, icon: 'none' }) }
      }
    })
  },
  goShopping() { wx.switchTab({ url: '/pages/category/category' }) },
  checkout() {
    if (!this.data.selectedCount) return wx.showToast({ title: '请先选择商品', icon: 'none' })
    wx.navigateTo({ url: '/pages/checkout/checkout' })
  }
})

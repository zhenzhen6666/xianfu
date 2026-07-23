const store = require('../../utils/store')

Page({
  data: { items: [], selectedCount: 0, total: '0.00', deliveryFee: '0.00', allSelected: false, remaining: '30.00', progress: 0 },
  onShow() { this.refresh() },
  refresh() {
    const items = store.cartDetails()
    const selected = items.filter(item => item.selected)
    const totalValue = selected.reduce((sum, item) => sum + item.price * item.count, 0)
    const deliveryFee = selected.length && totalValue < 30 ? 3 : 0
    this.setData({
      items,
      selectedCount: selected.reduce((sum, item) => sum + item.count, 0),
      total: store.money(totalValue),
      deliveryFee: store.money(deliveryFee),
      allSelected: !!items.length && selected.length === items.length,
      remaining: store.money(Math.max(0, 30 - totalValue)),
      progress: Math.min(100, Math.round(totalValue / 30 * 100))
    })
  },
  toggleItem(e) {
    const item = this.data.items.find(i => i.id === e.currentTarget.dataset.id)
    store.updateCart(item.id, { selected: !item.selected }); this.refresh()
  },
  toggleAll() {
    const next = !this.data.allSelected
    this.data.items.forEach(item => store.updateCart(item.id, { selected: next }))
    this.refresh()
  },
  changeCount(e) {
    const { id, delta } = e.currentTarget.dataset
    const item = this.data.items.find(i => i.id === id)
    const count = Math.max(1, item.count + Number(delta))
    store.updateCart(id, { count }); this.refresh()
  },
  removeOne(e) {
    const id = e.currentTarget.dataset.id
    wx.showModal({ title: '移除商品', content: '确定不买这件商品了吗？', confirmColor: '#168A51', success: res => { if (res.confirm) { store.removeCart([id]); this.refresh() } } })
  },
  removeSelected() {
    const ids = this.data.items.filter(item => item.selected).map(item => item.id)
    if (!ids.length) return wx.showToast({ title: '请先选择商品', icon: 'none' })
    wx.showModal({ title: '批量删除', content: `确定删除选中的 ${ids.length} 件商品吗？`, confirmColor: '#168A51', success: res => { if (res.confirm) { store.removeCart(ids); this.refresh() } } })
  },
  goShopping() { wx.switchTab({ url: '/pages/category/category' }) },
  checkout() {
    if (!this.data.selectedCount) return wx.showToast({ title: '请先选择商品', icon: 'none' })
    wx.navigateTo({ url: '/pages/checkout/checkout' })
  }
})

const store = require('../../utils/store')

Page({
  data: {
    tabs: [{ key: 'all', label: '全部' }, { key: 'wait_pay', label: '待支付' }, { key: 'shipping', label: '待配送' }, { key: 'complete', label: '已完成' }, { key: 'cancel', label: '已取消' }],
    active: 'all', orders: []
  },
  onShow() {
    const requested = wx.getStorageSync('xianfupu_order_tab')
    if (requested && this.data.tabs.some(tab => tab.key === requested)) this.setData({ active: requested })
    wx.removeStorageSync('xianfupu_order_tab')
    this.refresh()
  },
  refresh() {
    const saved = store.getOrders()
    const source = saved.length ? saved : this.demoOrders()
    const orders = this.data.active === 'all' ? source : source.filter(item => item.status === this.data.active)
    this.setData({ orders })
  },
  demoOrders() {
    return [
      { id: 'XF202607201028', createdAt: '07-20 10:28', status: 'shipping', statusText: '待配送', items: [Object.assign({}, store.PRODUCTS[1], { count: 2 }), Object.assign({}, store.PRODUCTS[6], { count: 1 }), Object.assign({}, store.PRODUCTS[7], { count: 1 })], subtotal: '39.30', deliveryFee: '0.00', total: '34.30' },
      { id: 'XF202607181635', createdAt: '07-18 16:35', status: 'complete', statusText: '已完成', items: [Object.assign({}, store.PRODUCTS[2], { count: 1 }), Object.assign({}, store.PRODUCTS[3], { count: 1 })], subtotal: '29.80', deliveryFee: '3.00', total: '27.80' }
    ]
  },
  selectTab(e) { this.setData({ active: e.currentTarget.dataset.key }, () => this.refresh()) },
  primaryAction(e) {
    const { id, action } = e.currentTarget.dataset
    if (action === 'pay') return this.updateOrder(id, 'shipping', '待配送', '模拟支付成功')
    if (action === 'confirm') return this.updateOrder(id, 'complete', '已完成', '已确认收货，积分已到账')
    if (action === 'reorder') {
      const order = this.data.orders.find(item => item.id === id)
      order.items.forEach(item => store.addToCart(item.id, item.count))
      wx.showToast({ title: '已加入购物车', icon: 'success' })
      return setTimeout(() => wx.navigateTo({ url: '/pages/cart/cart' }), 600)
    }
  },
  updateOrder(id, status, statusText, toast) {
    const saved = store.getOrders()
    const target = saved.find(item => item.id === id)
    if (!target) return wx.showToast({ title: '演示订单不可修改', icon: 'none' })
    target.status = status; target.statusText = statusText; wx.setStorageSync('xianfupu_orders', saved)
    wx.showToast({ title: toast, icon: 'none' }); this.refresh()
  },
  viewProgress() { wx.showModal({ title: '配送进度', content: '骑手正在门店取货\n预计 18 分钟后送达', showCancel: false, confirmText: '知道了', confirmColor: '#168A51' }) },
  afterSale() { wx.showToast({ title: '售后入口已为您打开', icon: 'none' }) },
  goShopping() { wx.switchTab({ url: '/pages/category/category' }) }
})

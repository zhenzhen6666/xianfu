const api = require('../../utils/api')
const adapters = require('../../utils/adapters')

Page({
  data: {
    tabs: [{ key: 'all', label: '全部' }, { key: 'wait_pay', label: '待支付' }, { key: 'fulfilling', label: '履约中' }, { key: 'complete', label: '已完成' }, { key: 'cancel', label: '已取消' }],
    active: 'all', orders: [], loading: false
  },
  onShow() {
    const requested = wx.getStorageSync('xianfupu_order_tab')
    if (requested && this.data.tabs.some(tab => tab.key === requested)) this.setData({ active: requested })
    wx.removeStorageSync('xianfupu_order_tab')
    this.refresh()
  },
  async refresh() {
    this.setData({ loading: true })
    try {
      const summaries = await api.orders(this.data.active === 'all' ? 'ALL' : this.data.active)
      const details = await Promise.all(summaries.map(item => api.order(item.orderNo)))
      this.setData({ orders: details.map(adapters.order), loading: false })
    } catch (error) {
      this.setData({ loading: false })
      wx.showToast({ title: error.message, icon: 'none', duration: 3000 })
    }
  },
  selectTab(e) { this.setData({ active: e.currentTarget.dataset.key }, () => this.refresh()) },
  async primaryAction(e) {
    const { id, action } = e.currentTarget.dataset
    try {
      if (action === 'pay') {
        const payment = await api.createPayment(id)
        await api.mockPay(payment.paymentNo)
        wx.showToast({ title: '模拟支付成功', icon: 'success' })
      } else if (action === 'confirm') {
        await api.confirmOrder(id)
        wx.showToast({ title: '已确认收货，积分已到账', icon: 'none' })
      } else if (action === 'reorder') {
        const order = this.data.orders.find(item => item.id === id)
        for (const item of order.items) await api.addCart(item.id, item.count)
        wx.showToast({ title: '已加入购物车', icon: 'success' })
        return setTimeout(() => wx.navigateTo({ url: '/pages/cart/cart' }), 500)
      }
      await this.refresh()
    } catch (error) { wx.showToast({ title: error.message, icon: 'none', duration: 3000 }) }
  },
  cancelOrder(e) {
    const id = e.currentTarget.dataset.id
    wx.showModal({
      title: '取消订单', content: '取消后库存、优惠券和积分将由后端自动返还。', confirmColor: '#168A51',
      success: async result => {
        if (!result.confirm) return
        try { await api.cancelOrder(id, '用户在小程序取消'); wx.showToast({ title: '订单已取消', icon: 'success' }); await this.refresh() }
        catch (error) { wx.showToast({ title: error.message, icon: 'none' }) }
      }
    })
  },
  viewProgress() { wx.showModal({ title: '配送进度', content: '订单已进入配送流程\n请以门店最新履约状态为准', showCancel: false, confirmText: '知道了', confirmColor: '#168A51' }) },
  afterSale(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({ url: `/pages/after-sale-apply/after-sale-apply?orderNo=${id}` })
  },
  goShopping() { wx.switchTab({ url: '/pages/category/category' }) }
})

const store = require('../../utils/store')

Page({
  data: {
    items: [], subtotal: '0.00', deliveryFee: '0.00', coupon: 5, pointsEnabled: false, pointsDeduction: '0.00', payTotal: '0.00',
    note: '', showAddress: false,
    address: { name: '林女士', phone: '13866686688', detail: '幸福里社区 8 栋 1202 室' },
    addressDraft: { name: '', phone: '', detail: '' }
  },
  onLoad() {
    const saved = wx.getStorageSync('xianfupu_address')
    if (saved) this.setData({ address: saved })
  },
  onShow() {
    const items = store.cartDetails().filter(item => item.selected)
    this.setData({ items }, () => this.calculate())
  },
  calculate() {
    const subtotalValue = this.data.items.reduce((sum, item) => sum + item.price * item.count, 0)
    const delivery = subtotalValue < 30 && subtotalValue > 0 ? 3 : 0
    const coupon = subtotalValue >= 29 ? this.data.coupon : 0
    const points = this.data.pointsEnabled ? Math.min(2.5, Math.max(0, subtotalValue + delivery - coupon)) : 0
    const total = Math.max(0, subtotalValue + delivery - coupon - points)
    this.setData({ subtotal: store.money(subtotalValue), deliveryFee: store.money(delivery), coupon, pointsDeduction: store.money(points), payTotal: store.money(total) })
  },
  chooseCoupon(e) { this.setData({ coupon: Number(e.currentTarget.dataset.value) }, () => this.calculate()) },
  togglePoints(e) { this.setData({ pointsEnabled: e.detail.value }, () => this.calculate()) },
  bindNote(e) { this.setData({ note: e.detail.value }) },
  noop() {},
  openAddress() { this.setData({ showAddress: true, addressDraft: Object.assign({}, this.data.address) }) },
  closeAddress() { this.setData({ showAddress: false }) },
  bindAddress(e) { this.setData({ [`addressDraft.${e.currentTarget.dataset.field}`]: e.detail.value }) },
  saveAddress() {
    const address = this.data.addressDraft
    if (!address.name || !address.phone || !address.detail) return wx.showToast({ title: '请填写完整地址', icon: 'none' })
    wx.setStorageSync('xianfupu_address', address)
    this.setData({ address, showAddress: false })
  },
  submitOrder() {
    if (!this.data.items.length) return wx.showToast({ title: '订单里还没有商品', icon: 'none' })
    const now = new Date()
    const order = {
      id: `XF${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}${String(now.getTime()).slice(-6)}`,
      createdAt: `${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')} ${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`,
      status: 'wait_pay', statusText: '待支付', items: this.data.items, subtotal: this.data.subtotal, deliveryFee: this.data.deliveryFee, total: this.data.payTotal, address: this.data.address, note: this.data.note
    }
    store.addOrder(order)
    store.clearSelectedCart()
    wx.showModal({
      title: '模拟收款接口',
      content: `订单已提交，请支付 ¥${this.data.payTotal}。当前为纯前端演示，不会产生真实扣款。`,
      confirmText: '模拟成功', cancelText: '暂不支付', confirmColor: '#168A51',
      success: res => {
        const orders = store.getOrders()
        const current = orders.find(item => item.id === order.id)
        if (res.confirm && current) { current.status = 'shipping'; current.statusText = '待配送'; wx.setStorageSync('xianfupu_orders', orders); wx.showToast({ title: '支付成功', icon: 'success' }) }
        else wx.showToast({ title: '订单等待支付', icon: 'none' })
        setTimeout(() => wx.switchTab({ url: '/pages/orders/orders' }), 650)
      }
    })
  }
})

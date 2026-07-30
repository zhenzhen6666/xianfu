const api = require('../../utils/api')
const adapters = require('../../utils/adapters')

Page({
  data: {
    items: [], subtotal: '0.00', deliveryFee: '0.00', coupon: '0.00', couponName: '暂无可用优惠券',
    pointsEnabled: false, pointsBalance: 0, pointsDeduction: '0.00', payTotal: '0.00', promiseMinutes: 30,
    note: '', showAddress: false, loading: true, selectedCouponId: null,
    address: { id: 0, name: '鲜福铺用户', phone: '13866686688', detail: '幸福里社区 8 栋 1202 室', distanceKm: 1 },
    addressDraft: { name: '', phone: '', detail: '' },
    availableCoupons: [], idempotencyKey: '', submitting: false
  },
  onLoad() {
    this.setData({ idempotencyKey: `${Date.now()}-${Math.random().toString(36).slice(2)}` })
    this.loadCheckout()
  },

  addressPayload(address) {
    return {
      name: address.name,
      phone: address.phone,
      detail: address.detail,
      longitude: address.longitude == null ? null : address.longitude,
      latitude: address.latitude == null ? null : address.latitude,
      distanceKm: address.distanceKm == null ? 1 : address.distanceKm,
      isDefault: true
    }
  },

  async ensureAddress() {
    const addresses = await api.addresses()
    if (addresses.length) return addresses.find(item => item.isDefault) || addresses[0]
    const saved = wx.getStorageSync('xianfupu_address') || this.data.address
    return api.createAddress(this.addressPayload(saved))
  },

  async loadCheckout() {
    this.setData({ loading: true })
    try {
      const [cart, address, coupons, points] = await Promise.all([
        api.cart(), this.ensureAddress(), api.coupons('AVAILABLE'), api.points()
      ])
      const eligibleCoupons = coupons
        .filter(item => Number(cart.subtotal) >= Number(item.minimumAmount))
        .sort((a, b) => Number(b.amount) - Number(a.amount))
      const eligible = eligibleCoupons[0]
      this.setData({
        address,
        availableCoupons: coupons,
        selectedCouponId: eligible ? eligible.id : null,
        couponName: eligible ? this.couponLabel(eligible) : '暂无达到门槛的优惠券',
        pointsBalance: points.balance || 0
      })
      await this.refreshPreview()
    } catch (error) {
      this.setData({ loading: false })
      wx.showToast({ title: error.message, icon: 'none', duration: 3500 })
    }
  },

  couponLabel(coupon) {
    const rule = Number(coupon.minimumAmount) > 0
      ? `满${coupon.minimumAmount}减${coupon.amount}`
      : `无门槛减${coupon.amount}`
    return `${coupon.name}（${rule}）`
  },

  orderPayload() {
    return {
      addressId: this.data.address.id,
      userCouponId: this.data.selectedCouponId || null,
      pointsToUse: this.data.pointsEnabled ? Math.min(250, this.data.pointsBalance) : 0,
      note: this.data.note,
      idempotencyKey: this.data.idempotencyKey
    }
  },

  async refreshPreview() {
    if (!this.data.address.id) return
    try {
      const preview = await api.previewOrder(this.orderPayload())
      this.setData({
        items: (preview.items || []).map(item => Object.assign({}, adapters.product(item), { count: item.count })),
        subtotal: adapters.money(preview.subtotal),
        deliveryFee: adapters.money(preview.deliveryFee),
        coupon: adapters.money(preview.couponDiscount),
        pointsDeduction: adapters.money(preview.pointsDiscount),
        payTotal: adapters.money(preview.payAmount),
        promiseMinutes: preview.promiseMinutes,
        loading: false
      })
    } catch (error) {
      if (error.code === 'COUPON_THRESHOLD_NOT_MET' && this.data.selectedCouponId) {
        this.setData({ selectedCouponId: null, couponName: '暂无达到门槛的优惠券' })
        return this.refreshPreview()
      }
      throw error
    }
  },

  chooseCoupon(e) {
    const useCoupon = Number(e.currentTarget.dataset.value) > 0
    if (!useCoupon) {
      return this.setData({
        selectedCouponId: null,
        couponName: '本单不使用优惠券'
      }, () => this.refreshPreview().catch(error => wx.showToast({ title: error.message, icon: 'none' })))
    }
    const eligible = this.data.availableCoupons
      .filter(item => Number(this.data.subtotal) >= Number(item.minimumAmount))
      .sort((a, b) => Number(b.amount) - Number(a.amount))
      .slice(0, 6)
    if (!eligible.length) return wx.showToast({ title: '暂无达到门槛的优惠券', icon: 'none' })
    wx.showActionSheet({
      itemList: eligible.map(item => this.couponLabel(item)),
      success: result => {
        const coupon = eligible[result.tapIndex]
        this.setData({
          selectedCouponId: coupon.id,
          couponName: this.couponLabel(coupon)
        }, () => this.refreshPreview().catch(error => wx.showToast({ title: error.message, icon: 'none' })))
      }
    })
  },
  togglePoints(e) {
    this.setData({ pointsEnabled: e.detail.value }, () => this.refreshPreview().catch(error => wx.showToast({ title: error.message, icon: 'none' })))
  },
  bindNote(e) { this.setData({ note: e.detail.value }) },
  noop() {},
  openAddress() {
    this.setData({
      showAddress: true,
      addressDraft: { name: this.data.address.name, phone: this.data.address.phone, detail: this.data.address.detail }
    })
  },
  closeAddress() { this.setData({ showAddress: false }) },
  bindAddress(e) { this.setData({ [`addressDraft.${e.currentTarget.dataset.field}`]: e.detail.value }) },
  async saveAddress() {
    const draft = this.data.addressDraft
    if (!draft.name || !/^1[3-9]\d{9}$/.test(draft.phone) || !draft.detail) {
      return wx.showToast({ title: '请填写正确的姓名、手机号和地址', icon: 'none' })
    }
    try {
      const payload = this.addressPayload(Object.assign({}, this.data.address, draft))
      const address = this.data.address.id
        ? await api.updateAddress(this.data.address.id, payload)
        : await api.createAddress(payload)
      wx.setStorageSync('xianfupu_address', address)
      this.setData({ address, showAddress: false })
      await this.refreshPreview()
    } catch (error) { wx.showToast({ title: error.message, icon: 'none' }) }
  },

  async submitOrder() {
    if (!this.data.items.length) return wx.showToast({ title: '订单里还没有商品', icon: 'none' })
    if (this.data.submitting) return
    this.setData({ submitting: true })
    wx.showLoading({ title: '正在创建订单' })
    try {
      const order = await api.createOrder(this.orderPayload())
      this.setData({ idempotencyKey: `${Date.now()}-${Math.random().toString(36).slice(2)}` })
      wx.hideLoading()
      wx.showModal({
        title: '本地模拟支付',
        content: `订单 ${order.orderNo} 已提交，请支付 ¥${adapters.money(order.payAmount)}。是否通过后端 MOCK 渠道模拟支付成功？`,
        confirmText: '模拟支付', cancelText: '稍后支付', confirmColor: '#168A51',
        success: async result => {
          try {
            if (result.confirm) {
              const payment = await api.createPayment(order.orderNo)
              await api.mockPay(payment.paymentNo)
              wx.showToast({ title: '支付成功', icon: 'success' })
            } else {
              wx.showToast({ title: '订单等待支付', icon: 'none' })
            }
            setTimeout(() => wx.switchTab({ url: '/pages/orders/orders' }), 650)
          } catch (error) { wx.showToast({ title: error.message, icon: 'none', duration: 3000 }) }
        }
      })
    } catch (error) {
      wx.hideLoading()
      wx.showToast({ title: error.message, icon: 'none', duration: 3500 })
    } finally {
      this.setData({ submitting: false })
    }
  }
})

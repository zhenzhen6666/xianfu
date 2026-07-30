const api = require('../../utils/api')
const blank = () => ({ id: 0, name: '', phone: '', detail: '', distanceKm: 1, isDefault: false })
Page({
  data: { items: [], editing: false, form: blank(), selectMode: false, loading: true },
  onLoad(options) { this.setData({ selectMode: options.select === '1' }) },
  onShow() { this.load() },
  async load() {
    try { this.setData({ items: await api.addresses(), loading: false }) }
    catch (error) { this.setData({ loading: false }); wx.showToast({ title: error.message, icon: 'none' }) }
  },
  add() { this.setData({ editing: true, form: blank() }) },
  edit(e) {
    const item = this.data.items.find(row => row.id === Number(e.currentTarget.dataset.id))
    this.setData({ editing: true, form: Object.assign({}, item) })
  },
  bind(e) { this.setData({ [`form.${e.currentTarget.dataset.field}`]: e.detail.value }) },
  noop() {},
  close() { this.setData({ editing: false }) },
  async save() {
    const form = this.data.form
    if (!form.name || !/^1[3-9]\d{9}$/.test(form.phone) || !form.detail) return wx.showToast({ title: '请填写完整且正确的信息', icon: 'none' })
    const payload = { name: form.name, phone: form.phone, detail: form.detail, distanceKm: form.distanceKm || 1, longitude: form.longitude || null, latitude: form.latitude || null, isDefault: Boolean(form.isDefault) }
    try {
      if (form.id) await api.updateAddress(form.id, payload)
      else await api.createAddress(payload)
      this.setData({ editing: false }); await this.load()
    } catch (error) { wx.showToast({ title: error.message, icon: 'none' }) }
  },
  async setDefault(e) {
    const item = this.data.items.find(row => row.id === Number(e.currentTarget.dataset.id))
    await api.updateAddress(item.id, Object.assign({}, item, { isDefault: true }))
    await this.load()
  },
  remove(e) {
    const id = Number(e.currentTarget.dataset.id)
    wx.showModal({ title: '删除地址', content: '确定删除这个收货地址吗？', success: async result => { if (result.confirm) { await api.removeAddress(id); await this.load() } } })
  },
  select(e) {
    if (!this.data.selectMode) return
    wx.setStorageSync('xianfupu_checkout_address_id', Number(e.currentTarget.dataset.id))
    wx.navigateBack()
  }
})

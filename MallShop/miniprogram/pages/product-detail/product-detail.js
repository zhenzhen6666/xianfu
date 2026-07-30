const api = require('../../utils/api')
const adapters = require('../../utils/adapters')

Page({
  data: { product: null, skus: [], selected: null, quantity: 1, favorite: false, loading: true },
  onLoad(options) { this.productCode = options.id; this.load() },
  async load() {
    try {
      const [detail, favorites] = await Promise.all([api.product(this.productCode), api.favorites()])
      const product = adapters.product(detail.product)
      const skus = (detail.skus || []).map(item => Object.assign({}, item, {
        salePriceText: adapters.money(item.salePrice),
        originalPriceText: adapters.money(item.originalPrice),
        memberPriceText: adapters.money(item.memberPrice)
      }))
      this.setData({
        product, skus, selected: skus.find(item => item.stock > 0) || skus[0],
        favorite: favorites.some(item => (item.id || item.productCode) === this.productCode), loading: false
      })
    } catch (error) { this.setData({ loading: false }); wx.showToast({ title: error.message, icon: 'none' }) }
  },
  chooseSku(e) {
    const selected = this.data.skus.find(item => item.skuCode === e.currentTarget.dataset.code)
    if (selected && selected.stock > 0) this.setData({ selected, quantity: 1 })
  },
  quantity(e) {
    const next = Math.max(1, Math.min(this.data.selected.stock, this.data.quantity + Number(e.currentTarget.dataset.delta)))
    this.setData({ quantity: next })
  },
  async toggleFavorite() {
    try {
      if (this.data.favorite) await api.unfavorite(this.productCode)
      else await api.favorite(this.productCode)
      this.setData({ favorite: !this.data.favorite })
    } catch (error) { wx.showToast({ title: error.message, icon: 'none' }) }
  },
  async addCart() {
    if (!this.data.selected || this.data.selected.stock < 1) return wx.showToast({ title: '该规格已售罄', icon: 'none' })
    try {
      await api.addCart(this.productCode, this.data.quantity, null, this.data.selected.skuCode)
      wx.showToast({ title: '已加入购物车', icon: 'success' })
    } catch (error) { wx.showToast({ title: error.message, icon: 'none' }) }
  },
  goCart() { wx.navigateTo({ url: '/pages/cart/cart' }) }
})

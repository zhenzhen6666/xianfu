const api = require('../../utils/api')
const adapters = require('../../utils/adapters')

Page({
  data: {
    categories: ['推荐'],
    activeCategory: '推荐',
    sort: 'default',
    specialOnly: false,
    memberOnly: false,
    searchValue: '',
    products: [],
    cartCount: 0,
    selectedProduct: null,
    loading: false
  },

  async onLoad() {
    try {
      const list = await api.categories()
      this.setData({ categories: ['推荐'].concat(list.map(item => item.name)) })
    } catch (error) {
      wx.showToast({ title: error.message, icon: 'none' })
    }
  },

  onShow() {
    const searchValue = wx.getStorageSync('xianfupu_search') || ''
    if (searchValue) wx.removeStorageSync('xianfupu_search')
    this.setData({ searchValue }, () => this.loadProducts())
    this.refreshCart()
  },
  bindSearch(e) {
    this.setData({ searchValue: e.detail.value })
    clearTimeout(this.searchTimer)
    this.searchTimer = setTimeout(() => this.loadProducts(), 280)
  },
  clearSearch() { this.setData({ searchValue: '' }, () => this.loadProducts()) },
  noop() {},
  selectCategory(e) { this.setData({ activeCategory: e.currentTarget.dataset.name }, () => this.loadProducts()) },
  setSort(e) {
    const sort = e.currentTarget.dataset.sort
    this.setData({ sort: this.data.sort === sort && sort !== 'default' ? 'default' : sort }, () => this.loadProducts())
  },
  toggleSpecial() { this.setData({ specialOnly: !this.data.specialOnly }, () => this.loadProducts()) },
  toggleMember() { this.setData({ memberOnly: !this.data.memberOnly }, () => this.loadProducts()) },
  async loadProducts() {
    this.setData({ loading: true })
    try {
      const result = await api.products({
        category: this.data.activeCategory === '推荐' ? undefined : this.data.activeCategory,
        keyword: this.data.searchValue.trim() || undefined,
        sort: this.data.sort,
        specialOnly: this.data.specialOnly,
        memberOnly: this.data.memberOnly,
        page: 0,
        size: 100
      })
      this.setData({ products: (result.items || []).map(adapters.product), loading: false })
    } catch (error) {
      this.setData({ loading: false })
      wx.showToast({ title: error.message, icon: 'none' })
    }
  },
  showProduct(e) { this.setData({ selectedProduct: this.data.products.find(item => item.id === e.currentTarget.dataset.id) }) },
  closeProduct() { this.setData({ selectedProduct: null }) },
  async addCart(e) {
    try {
      await api.addCart(e.currentTarget.dataset.id, 1)
      await this.refreshCart()
      wx.showToast({ title: '已放入购物车', icon: 'success' })
    } catch (error) { wx.showToast({ title: error.message, icon: 'none' }) }
  },
  addFromModal() {
    const id = this.data.selectedProduct && this.data.selectedProduct.id
    if (!id) return
    this.addCart({ currentTarget: { dataset: { id } } })
    this.closeProduct()
  },
  async refreshCart() {
    try {
      const result = await api.cart()
      this.setData({ cartCount: (result.items || []).reduce((sum, item) => sum + item.count, 0) })
    } catch (error) { console.warn(error.message) }
  },
  goCart() { wx.navigateTo({ url: '/pages/cart/cart' }) }
})

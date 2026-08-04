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
    searchHistory: [],
    hotKeywords: [],
    products: [],
    cartCount: 0,
    selectedProduct: null,
    loading: false,
    loaded: false,
    errorMessage: ''
  },

  async onLoad() {
    this.loadSearchSuggestions()
    try {
      const list = await api.categories()
      const payload = list && list.data ? list.data : list
      const categories = Array.isArray(payload) ? payload : []
      this.setData({ categories: ['推荐'].concat(categories.map(item => item.name).filter(Boolean)) })
    } catch (error) {
      wx.showToast({ title: error.message, icon: 'none' })
    }
  },

  onShow() {
    const searchValue = wx.getStorageSync('xianfupu_search') || ''
    if (searchValue) wx.removeStorageSync('xianfupu_search')
    this.setData({ searchValue }, () => this.loadProducts(Boolean(searchValue)))
    this.refreshCart()
  },
  bindSearch(e) {
    this.setData({ searchValue: e.detail.value })
    clearTimeout(this.searchTimer)
    this.searchTimer = setTimeout(() => this.loadProducts(), 280)
  },
  confirmSearch() {
    clearTimeout(this.searchTimer)
    this.loadProducts(true)
  },
  chooseKeyword(e) {
    const keyword = e.currentTarget.dataset.keyword || ''
    this.setData({ searchValue: keyword }, () => this.loadProducts(true))
  },
  clearSearch() { this.setData({ searchValue: '' }, () => this.loadProducts()) },
  async clearHistory() {
    try {
      await api.clearSearchHistory()
      this.setData({ searchHistory: [] })
    } catch (error) { wx.showToast({ title: error.message, icon: 'none' }) }
  },
  noop() {},
  selectCategory(e) { this.setData({ activeCategory: e.currentTarget.dataset.name }, () => this.loadProducts()) },
  setSort(e) {
    const sort = e.currentTarget.dataset.sort
    this.setData({ sort: this.data.sort === sort && sort !== 'default' ? 'default' : sort }, () => this.loadProducts())
  },
  toggleSpecial() { this.setData({ specialOnly: !this.data.specialOnly }, () => this.loadProducts()) },
  toggleMember() { this.setData({ memberOnly: !this.data.memberOnly }, () => this.loadProducts()) },
  async loadProducts(recordSearch = false) {
    const requestId = (this.productRequestId || 0) + 1
    this.productRequestId = requestId
    this.setData({ loading: true, errorMessage: '' })
    try {
      const params = {
        sort: this.data.sort,
        specialOnly: this.data.specialOnly,
        memberOnly: this.data.memberOnly,
        page: 0,
        size: 100
      }
      if (this.data.activeCategory !== '推荐') params.category = this.data.activeCategory
      const keyword = this.data.searchValue.trim()
      if (keyword) params.keyword = keyword

      const result = await api.products(params)
      if (requestId !== this.productRequestId) return
      const payload = result && result.data ? result.data : result
      const items = Array.isArray(payload)
        ? payload
        : ((payload && (payload.items || payload.content)) || [])
      this.setData({
        products: items.map(adapters.product),
        loading: false,
        loaded: true,
        errorMessage: ''
      })
      if (recordSearch && keyword) {
        try {
          await api.recordSearch(keyword)
          this.loadSearchSuggestions()
        } catch (searchError) {
          console.warn('搜索记录保存失败：', searchError.message)
        }
      }
    } catch (error) {
      if (requestId !== this.productRequestId) return
      this.setData({
        products: [],
        loading: false,
        loaded: true,
        errorMessage: error.message || '商品加载失败'
      })
      wx.showToast({ title: error.message, icon: 'none' })
    }
  },
  async loadSearchSuggestions() {
    try {
      const [history, hot] = await Promise.all([api.searchHistory(), api.hotSearches()])
      this.setData({
        searchHistory: (history || []).map(item => item.keyword),
        hotKeywords: (hot || []).map(item => item.keyword)
      })
    } catch (error) { console.warn('搜索推荐加载失败：', error.message) }
  },
  retryProducts() { this.loadProducts() },
  showProduct(e) { wx.navigateTo({ url: `/pages/product-detail/product-detail?id=${e.currentTarget.dataset.id}` }) },
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

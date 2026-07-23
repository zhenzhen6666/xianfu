const store = require('../../utils/store')

Page({
  data: {
    categories: ['推荐'].concat(store.CATEGORIES),
    activeCategory: '推荐',
    sort: 'default',
    specialOnly: false,
    memberOnly: false,
    searchValue: '',
    products: store.PRODUCTS,
    cartCount: 0,
    selectedProduct: null
  },

  onShow() {
    const searchValue = wx.getStorageSync('xianfupu_search') || ''
    if (searchValue) wx.removeStorageSync('xianfupu_search')
    this.setData({ searchValue }, () => this.applyFilters())
    this.refreshCart()
  },
  bindSearch(e) { this.setData({ searchValue: e.detail.value }, () => this.applyFilters()) },
  clearSearch() { this.setData({ searchValue: '' }, () => this.applyFilters()) },
  noop() {},
  selectCategory(e) { this.setData({ activeCategory: e.currentTarget.dataset.name }, () => this.applyFilters()) },
  setSort(e) {
    const sort = e.currentTarget.dataset.sort
    this.setData({ sort: this.data.sort === sort && sort !== 'default' ? 'default' : sort }, () => this.applyFilters())
  },
  toggleSpecial() { this.setData({ specialOnly: !this.data.specialOnly }, () => this.applyFilters()) },
  toggleMember() { this.setData({ memberOnly: !this.data.memberOnly }, () => this.applyFilters()) },
  applyFilters() {
    const { activeCategory, searchValue, sort, specialOnly, memberOnly } = this.data
    let products = store.PRODUCTS.filter(product => {
      const categoryOK = activeCategory === '推荐' || product.category === activeCategory
      const searchOK = !searchValue || `${product.name}${product.subtitle}${product.category}`.includes(searchValue)
      const specialOK = !specialOnly || product.original - product.price >= 2
      const memberOK = !memberOnly || product.member < product.price
      return categoryOK && searchOK && specialOK && memberOK
    })
    if (sort === 'asc') products.sort((a, b) => a.price - b.price)
    if (sort === 'desc') products.sort((a, b) => b.price - a.price)
    if (sort === 'sales') products.sort((a, b) => b.sales - a.sales)
    this.setData({ products })
  },
  showProduct(e) { this.setData({ selectedProduct: store.PRODUCTS.find(p => p.id === e.currentTarget.dataset.id) }) },
  closeProduct() { this.setData({ selectedProduct: null }) },
  addCart(e) {
    const id = e.currentTarget.dataset.id
    store.addToCart(id)
    this.refreshCart()
    wx.showToast({ title: '已放入购物车', icon: 'success' })
  },
  addFromModal() { this.addCart({ currentTarget: { dataset: { id: this.data.selectedProduct.id } } }); this.closeProduct() },
  refreshCart() { this.setData({ cartCount: store.getCart().reduce((sum, item) => sum + item.count, 0) }) },
  goCart() { wx.navigateTo({ url: '/pages/cart/cart' }) }
})

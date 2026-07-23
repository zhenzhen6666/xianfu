const store = require('../../utils/store')

const CONFIG = {
  penny: { title: '一分钱专享', subtitle: '新朋友的第一口新鲜', label: '新人限定 · 每人限购 1 件', color: 'orange', products: [store.PRODUCTS[0], store.PRODUCTS[1]] },
  new: { title: '新人福利站', subtitle: '注册即领 18 元券包', label: '三张好券 · 自动发到卡包', color: 'green', products: store.PRODUCTS.slice(0, 4) },
  clean: { title: '今晚在家做饭', subtitle: '洗切搭配好，下锅就开饭', label: '净菜专区 · 最快 30 分钟到家', color: 'pink', products: [store.PRODUCTS[6], store.PRODUCTS[5], store.PRODUCTS[0]] },
  egg: { title: '欢乐砸金蛋', subtitle: '20 积分一次，惊喜等你砸', label: '优惠券 · 实物好礼 · 每日可玩', color: 'yellow', products: store.PRODUCTS.slice(2, 6) },
  deal: { title: '9.9 特惠专区', subtitle: '好价格，也要好新鲜', label: '限时限量 · 售完即止', color: 'orange', products: store.PRODUCTS.filter(p => p.price < 16) }
}

Page({
  data: { config: CONFIG.new, cartCount: 0, eggOpened: false },
  onLoad(options) { this.setData({ config: CONFIG[options.type] || CONFIG.new }); wx.setNavigationBarTitle({ title: (CONFIG[options.type] || CONFIG.new).title }) },
  onShow() { this.refreshCart() },
  addCart(e) {
    const pennyPrice = this.data.config.title === '一分钱专享' ? 0.01 : undefined
    store.addToCart(e.currentTarget.dataset.id, 1, pennyPrice)
    this.refreshCart()
    wx.showToast({ title: pennyPrice ? '新人价已锁定' : '已加入购物车', icon: 'success' })
  },
  refreshCart() { this.setData({ cartCount: store.getCart().reduce((sum, item) => sum + item.count, 0) }) },
  openEgg() { this.setData({ eggOpened: true }); wx.showModal({ title: '恭喜获得 5 元券', content: '已放入您的优惠券卡包', showCancel: false, confirmColor: '#168A51' }) },
  goCart() { wx.navigateTo({ url: '/pages/cart/cart' }) }
})

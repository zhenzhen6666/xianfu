const { request, ensureLogin } = require('./request')

const get = (path, data, auth = true) => request(path, { data, auth })
const post = (path, data, auth = true) => request(path, { method: 'POST', data, auth })
const put = (path, data) => request(path, { method: 'PUT', data })
const patch = (path, data) => request(path, { method: 'PATCH', data })
const remove = path => request(path, { method: 'DELETE' })

module.exports = {
  ensureLogin,
  home: () => get('/home', null, false),
  categories: () => get('/catalog/categories', null, false),
  products: params => get('/catalog/products', params, false),
  product: id => get(`/catalog/products/${id}`, null, false),
  activities: () => get('/activities', null, false),
  activity: code => get(`/activities/${code}`, null, false),
  delivery: () => get('/config/delivery', null, false),
  cart: () => get('/cart'),
  addCart: (productId, quantity = 1, activityCode) => post('/cart/items', { productId, quantity, activityCode }),
  updateCart: (id, data) => patch(`/cart/items/${id}`, data),
  selectAll: selected => put('/cart/selection', { selected }),
  removeCart: id => remove(`/cart/items/${id}`),
  removeSelected: () => remove('/cart/selected'),
  addresses: () => get('/addresses'),
  createAddress: data => post('/addresses', data),
  updateAddress: (id, data) => put(`/addresses/${id}`, data),
  coupons: (status = 'ALL') => get('/users/me/coupons', { status }),
  points: () => get('/users/me/points'),
  profile: () => get('/users/me/profile'),
  favorites: () => get('/users/me/favorites'),
  favorite: code => post(`/users/me/favorites/${code}`),
  unfavorite: code => remove(`/users/me/favorites/${code}`),
  bindWechatMobile: code => post('/users/me/mobile/wechat', { code }),
  drawEgg: () => post('/users/me/activities/egg/draw'),
  previewOrder: data => post('/orders/preview', data),
  createOrder: data => post('/orders', data),
  orders: (status = 'ALL') => get('/orders', { status, page: 0, size: 50 }),
  order: orderNo => get(`/orders/${orderNo}`),
  cancelOrder: (orderNo, reason) => post(`/orders/${orderNo}/cancel`, { reason }),
  confirmOrder: orderNo => post(`/orders/${orderNo}/confirm`),
  createPayment: orderNo => post(`/orders/${orderNo}/pay`, { channel: 'MOCK' }),
  mockPay: paymentNo => post('/payments/mock/callback', {
    paymentNo,
    success: true,
    providerTradeNo: `LOCAL-${Date.now()}`,
    callbackSecret: 'local-mock-callback'
  }, false),
  createAfterSale: data => post('/after-sales', data),
  afterSales: () => get('/after-sales'),
  afterSale: no => get(`/after-sales/${no}`)
}

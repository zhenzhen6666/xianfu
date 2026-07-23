const PRODUCTS = [
  { id: 'p1', category: '蔬菜', name: '上海青', subtitle: '清甜脆嫩，当日采摘', spec: '约 400g/份', price: 3.99, original: 5.8, member: 3.59, sales: 1260, stock: 86, image: '/assets/products/bok-choy.jpg', badge: '今日鲜采' },
  { id: 'p2', category: '蔬菜', name: '沙瓤番茄', subtitle: '自然熟，酸甜多汁', spec: '约 500g/份', price: 6.8, original: 8.8, member: 6.2, sales: 983, stock: 64, image: '/assets/products/tomato.jpg', badge: '特价' },
  { id: 'p3', category: '水果', name: '奶油草莓', subtitle: '颗颗精选，香甜软糯', spec: '250g/盒', price: 16.9, original: 22.9, member: 15.9, sales: 842, stock: 32, image: '/assets/products/strawberry.jpg', badge: '人气' },
  { id: 'p4', category: '水果', name: '阳光甜橙', subtitle: '果肉饱满，汁水充足', spec: '约 1kg/份', price: 12.9, original: 16.8, member: 11.9, sales: 734, stock: 59, image: '/assets/products/orange.jpg', badge: '9.9专区' },
  { id: 'p5', category: '肉禽水产', name: '大西洋三文鱼', subtitle: '鲜切冷藏，肉质细嫩', spec: '200g/盒', price: 29.9, original: 36.9, member: 27.9, sales: 418, stock: 25, image: '/assets/products/salmon.jpg', badge: '会员价' },
  { id: 'p6', category: '肉禽水产', name: '优选鸡胸肉', subtitle: '低脂高蛋白，冷鲜配送', spec: '500g/盒', price: 13.9, original: 16.9, member: 12.5, sales: 671, stock: 41, image: '/assets/products/chicken.jpg', badge: '放心肉' },
  { id: 'p7', category: '净菜', name: '菌菇鲜拼', subtitle: '免洗搭配，下锅即烹', spec: '350g/盒', price: 9.9, original: 12.9, member: 8.9, sales: 592, stock: 38, image: '/assets/products/mushroom.jpg', badge: '省心菜' },
  { id: 'p8', category: '鲜切果盒', name: '缤纷鲜切果盒', subtitle: '现切现装，开盒即食', spec: '400g/盒', price: 15.8, original: 19.9, member: 14.2, sales: 1102, stock: 44, image: '/assets/products/fruit-cup.jpg', badge: '鲜切' }
]

const CATEGORIES = ['蔬菜', '水果', '肉禽水产', '零食百货', '净菜', '鲜切果盒']
const CART_KEY = 'xianfupu_cart'
const ORDER_KEY = 'xianfupu_orders'

function getCart() { return wx.getStorageSync(CART_KEY) || [] }
function saveCart(cart) { wx.setStorageSync(CART_KEY, cart); return cart }
function addToCart(id, count = 1, priceOverride) {
  const cart = getCart()
  const line = cart.find(item => item.id === id)
  if (line) {
    line.count = typeof priceOverride === 'number' ? 1 : line.count + count
    if (typeof priceOverride === 'number') line.priceOverride = priceOverride
  } else {
    const next = { id, count, selected: true }
    if (typeof priceOverride === 'number') next.priceOverride = priceOverride
    cart.push(next)
  }
  return saveCart(cart)
}
function updateCart(id, patch) {
  const cart = getCart().map(item => item.id === id ? Object.assign({}, item, patch) : item)
  return saveCart(cart)
}
function removeCart(ids) { return saveCart(getCart().filter(item => !ids.includes(item.id))) }
function clearSelectedCart() { return saveCart(getCart().filter(item => !item.selected)) }
function cartDetails() {
  return getCart().map(line => {
    const item = Object.assign({}, PRODUCTS.find(p => p.id === line.id), line)
    if (typeof line.priceOverride === 'number') item.price = line.priceOverride
    return item
  }).filter(item => item.name)
}
function money(value) { return Number(value || 0).toFixed(2) }
function getOrders() { return wx.getStorageSync(ORDER_KEY) || [] }
function addOrder(order) {
  const orders = getOrders()
  orders.unshift(order)
  wx.setStorageSync(ORDER_KEY, orders)
  return order
}

module.exports = { PRODUCTS, CATEGORIES, getCart, addToCart, updateCart, removeCart, clearSelectedCart, cartDetails, money, getOrders, addOrder }

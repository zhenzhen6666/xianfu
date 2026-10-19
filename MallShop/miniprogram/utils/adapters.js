const { resolveApiFileUrl } = require('./config')

function money(value) { return Number(value || 0).toFixed(2) }

function product(item) {
  if (!item) return null
  return Object.assign({}, item, {
    id: item.id || item.productCode,
    image: resolveApiFileUrl(item.image || item.imageUrl || ''),
    price: money(item.price),
    original: money(item.originalPrice != null ? item.originalPrice : item.original),
    member: money(item.memberPrice != null ? item.memberPrice : item.member),
    sales: Number(item.sales || 0),
    stock: Number(item.stock || 0),
    badge: item.badge || '鲜选'
  })
}

function cart(view) {
  const items = (view.items || []).map(item => Object.assign({}, product(item), {
    count: Number(item.count || 0),
    selected: Boolean(item.selected),
    priceOverride: item.priceOverride
  }))
  return Object.assign({}, view, {
    items,
    subtotal: money(view.subtotal),
    deliveryFee: money(view.deliveryFee),
    remainingForFreeDelivery: money(view.remainingForFreeDelivery),
    payable: money(view.payable)
  })
}

const STATUS = {
  WAIT_PAY: { key: 'wait_pay', text: '待支付' },
  PAID: { key: 'paid', text: '已支付' },
  ACCEPTED: { key: 'accepted', text: '已接单' },
  PREPARING: { key: 'preparing', text: '备货中' },
  SHIPPING: { key: 'shipping', text: '配送中' },
  DELIVERED: { key: 'delivered', text: '已送达' },
  COMPLETED: { key: 'complete', text: '已完成' },
  CANCELLED: { key: 'cancel', text: '已取消' },
  REFUNDED: { key: 'cancel', text: '已退款' }
}

function formatDate(value) {
  if (!value) return ''
  const date = new Date(value)
  const pad = number => String(number).padStart(2, '0')
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function order(detail) {
  const status = STATUS[detail.status] || { key: String(detail.status || '').toLowerCase(), text: detail.statusText || detail.status }
  return Object.assign({}, detail, {
    id: detail.orderNo,
    status: status.key,
    statusText: detail.statusText || status.text,
    isFulfilling: ['PAID', 'ACCEPTED', 'PREPARING', 'SHIPPING', 'DELIVERED'].includes(detail.status),
    createdAt: formatDate(detail.createdAt),
    items: (detail.items || []).map(item => Object.assign({}, product(item), { count: item.count })),
    subtotal: money(detail.subtotal),
    deliveryFee: money(detail.deliveryFee),
    total: money(detail.payAmount)
  })
}

module.exports = { money, product, cart, order, formatDate }

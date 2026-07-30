export interface UserProfile {
  id: number
  nickname: string
  avatarUrl?: string
  mobile?: string
  role: string
  status: string
}

export interface LoginResult {
  accessToken: string
  refreshToken: string
  sessionId: string
  accessExpiresAt: string
  refreshExpiresAt: string
  tokenType: string
  user: UserProfile
}

export interface HotProduct {
  productCode: string
  name: string
  sales: number
  stock: number
}

export interface Dashboard {
  todayOrders: number
  todaySales: number
  newUsers: number
  hotProducts: HotProduct[]
}

export interface Product {
  productCode: string
  categoryCode: string
  categoryName: string
  name: string
  subtitle?: string
  description?: string
  spec: string
  imageUrl?: string
  originalPrice: number
  salePrice: number
  memberPrice?: number
  stock: number
  sales: number
  badge?: string
  status: string
  sortOrder: number
  updatedAt: string
}

export type ProductPayload = Omit<Product, 'productCode' | 'categoryName' | 'sales' | 'updatedAt'>

export interface Order {
  orderNo: string
  status: string
  nickname: string
  mobile?: string
  payAmount: number
  deliveryFee: number
  receiverName: string
  receiverMobile: string
  receiverAddress: string
  createdAt: string
  paidAt?: string
}

export interface OrderItem {
  id: string
  name: string
  spec?: string
  image?: string
  price: number
  count: number
  lineAmount: number
}

export interface OrderDetail {
  orderNo: string
  status: string
  statusText: string
  items: OrderItem[]
  subtotal: number
  deliveryFee: number
  couponDiscount: number
  memberDiscount: number
  pointsUsed: number
  pointsDiscount: number
  payAmount: number
  receiverName: string
  receiverMobile: string
  receiverAddress: string
  note?: string
  payDeadline?: string
  paidAt?: string
  acceptedAt?: string
  preparingAt?: string
  shippedAt?: string
  deliveredAt?: string
  completedAt?: string
  cancelledAt?: string
  cancelReason?: string
  createdAt: string
}

export interface OrderNotification {
  orderNo: string
  status: 'UNREAD' | 'READ' | 'ACCEPTED'
  nickname: string
  mobile?: string
  payAmount: number
  receiverName: string
  receiverMobile: string
  receiverAddress: string
  paidAt: string
  createdAt: string
}

export interface DeliveryConfig {
  id: number
  name: string
  freeThreshold: number
  baseFee: number
  maxDistanceKm: number
  promiseMinutes: number
  announcement: string
  updatedAt: string
}

export interface AdminUser {
  id: number
  nickname: string
  mobile?: string
  status: string
  points: number
  totalSpent: number
  memberLevelName: string
  createdAt: string
}

export interface AfterSale {
  afterSaleNo: string
  orderNo: string
  nickname: string
  reason: string
  description?: string
  status: string
  refundAmount: number
  refundStatus: string
  refundedAmount: number
  refundError?: string
  merchantReply?: string
  createdAt: string
}

export interface Category {
  code: string
  name: string
  parentCode?: string
  iconUrl?: string
  sortOrder: number
  enabled: boolean
}

export interface HomeModule {
  id: number
  moduleType: string
  title?: string
  contentJson: string
  sortOrder: number
  enabled: boolean
  publishedAt?: string
  updatedAt: string
}

export interface Activity {
  code: string
  name: string
  activityType: string
  description?: string
  rulesJson: string
  startsAt: string
  endsAt: string
  enabled: boolean
}

export interface Coupon {
  id: number
  code: string
  name: string
  amount: number
  minimumAmount: number
  totalStock: number
  claimedCount: number
  remainingStock: number
  perUserLimit: number
  startsAt: string
  endsAt: string
  enabled: boolean
  status: 'ACTIVE' | 'NOT_STARTED' | 'EXPIRED' | 'DISABLED' | 'OUT_OF_STOCK'
  createdAt: string
}

export interface CouponPayload {
  name: string
  amount: number
  minimumAmount: number
  totalStock: number
  startsAt: string
  endsAt: string
  enabled: boolean
}

export interface CouponIssue {
  userCouponId: number
  couponCode: string
  couponName: string
  userId: number
  nickname: string
  mobile: string
  status: string
  issuedAt: string
}

export interface ApiError {
  status?: number
  code?: string
  message?: string
}

export interface ErrorLog {
  id: number; requestId?: string; level: string; errorCode?: string; message: string
  requestMethod?: string; requestPath?: string; userId?: number; stackTrace?: string; createdAt: string
}
export interface BusinessAlert {
  id: number; alertType: string; severity: string; title: string; message: string
  targetType?: string; targetId?: string; status: string; createdAt: string
}
export interface PaymentCallbackLog {
  id: number; paymentNo?: string; channel?: string; success: boolean; signatureValid: boolean
  providerTradeNo?: string; resultCode?: string; message?: string; payload?: string; createdAt: string
}
export interface AuthSession {
  id: string; userId: number; nickname: string; mobile?: string; role: string; revoked: boolean
  expiresAt: string; lastSeenAt: string; ipAddress?: string; userAgent?: string; createdAt: string
}

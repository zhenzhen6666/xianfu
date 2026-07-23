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

export interface ApiError {
  status?: number
  code?: string
  message?: string
}

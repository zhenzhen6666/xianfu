<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Bell,
  Box,
  DataAnalysis,
  Fold,
  Goods,
  Grid,
  Menu as MenuIcon,
  Operation,
  Promotion,
  Service,
  Setting,
  Tickets,
  User,
} from '@element-plus/icons-vue'
import { ElNotification } from 'element-plus'
import 'element-plus/es/components/notification/style/css'
import { adminApi } from '../api/admin'
import { http } from '../api/http'
import { useAuthStore } from '../stores/auth'
import type { OrderNotification } from '../types/api'
import { dateTime, money } from '../utils/format'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const collapsed = ref(false)
const mobileOpen = ref(false)
const orderAlerts = ref<OrderNotification[]>([])
const unreadOrderCount = ref(0)
let pollTimer: number | undefined
let reconnectTimer: number | undefined
let titleTimer: number | undefined
let streamAbort: AbortController | null = null
let alertsInitialized = false
let knownOrderNos = new Set<string>()

const title = computed(() => String(route.meta.title || '经营管理'))
const eyebrow = computed(() => String(route.meta.eyebrow || '鲜福铺运营中心'))

const menuItems = [
  { path: '/dashboard', label: '经营看板', icon: DataAnalysis },
  { path: '/products', label: '商品管理', icon: Goods },
  { path: '/categories', label: '分类管理', icon: Grid },
  { path: '/orders', label: '订单管理', icon: Box },
  { path: '/users', label: '会员用户', icon: User },
  { path: '/coupons', label: '优惠券发放', icon: Tickets },
  { path: '/after-sales', label: '售后处理', icon: Service },
  { path: '/marketing', label: '营销装修', icon: Promotion },
  { path: '/delivery', label: '配送设置', icon: Setting },
  { path: '/operations', label: '系统运维', icon: Operation },
]

function navigate(path: string) {
  mobileOpen.value = false
  router.push(path)
}

function logout() {
  stopOrderAlerts()
  auth.logout()
  router.replace('/login')
}

function playOrderTone() {
  try {
    const AudioContextClass = window.AudioContext || (window as typeof window & {
      webkitAudioContext: typeof AudioContext
    }).webkitAudioContext
    if (!AudioContextClass) return
    const context = new AudioContextClass()
    ;[0, 0.22].forEach((delay) => {
      const oscillator = context.createOscillator()
      const gain = context.createGain()
      oscillator.type = 'sine'
      oscillator.frequency.value = 880
      gain.gain.setValueAtTime(0.0001, context.currentTime + delay)
      gain.gain.exponentialRampToValueAtTime(0.18, context.currentTime + delay + 0.02)
      gain.gain.exponentialRampToValueAtTime(0.0001, context.currentTime + delay + 0.16)
      oscillator.connect(gain)
      gain.connect(context.destination)
      oscillator.start(context.currentTime + delay)
      oscillator.stop(context.currentTime + delay + 0.18)
    })
    window.setTimeout(() => context.close(), 700)
  } catch {
    // 浏览器禁止自动播放时仍保留弹窗和角标提醒。
  }
}

function flashTitle() {
  if (titleTimer) window.clearInterval(titleTimer)
  let visible = false
  let times = 0
  titleTimer = window.setInterval(() => {
    document.title = visible ? `${title.value} · 鲜福铺` : '【新订单】请及时处理'
    visible = !visible
    times += 1
    if (times >= 10 && titleTimer) {
      window.clearInterval(titleTimer)
      titleTimer = undefined
      document.title = `${title.value} · 鲜福铺`
    }
  }, 700)
}

function showNewOrderAlert(item: OrderNotification) {
  playOrderTone()
  flashTitle()
  ElNotification({
    title: '新订单已支付',
    message: `${item.receiverName} · ${money(item.payAmount)}，点击查看订单`,
    type: 'success',
    duration: 0,
    position: 'top-right',
    onClick: () => openOrderAlert(item),
  })
}

function upsertOrderAlert(item: OrderNotification, notify: boolean) {
  const existed = knownOrderNos.has(item.orderNo)
  knownOrderNos.add(item.orderNo)
  const index = orderAlerts.value.findIndex(alert => alert.orderNo === item.orderNo)
  if (index >= 0) orderAlerts.value.splice(index, 1, item)
  else orderAlerts.value.unshift(item)
  if (item.status === 'UNREAD' && !existed) unreadOrderCount.value += 1
  if (notify && !existed) showNewOrderAlert(item)
}

async function refreshOrderAlerts(notifyMissed = true) {
  try {
    const [items, count] = await Promise.all([
      adminApi.orderNotifications(),
      adminApi.unreadOrderNotificationCount(),
    ])
    if (alertsInitialized && notifyMissed) {
      items.slice().reverse().forEach(item => {
        if (item.status === 'UNREAD' && !knownOrderNos.has(item.orderNo)) showNewOrderAlert(item)
      })
    } else if (!alertsInitialized && count.count > 0) {
      playOrderTone()
      ElNotification({
        title: `有 ${count.count} 个未读订单`,
        message: '请打开右上角订单提醒及时处理',
        type: 'warning',
        duration: 8000,
      })
    }
    orderAlerts.value = items
    unreadOrderCount.value = count.count
    knownOrderNos = new Set(items.map(item => item.orderNo))
    alertsInitialized = true
  } catch {
    // 实时连接与下一次轮询会继续重试。
  }
}

function handleSseBlock(block: string) {
  let eventName = 'message'
  const dataLines: string[] = []
  block.split('\n').forEach((line) => {
    if (line.startsWith('event:')) eventName = line.slice(6).trim()
    if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
  })
  if (eventName !== 'new-order' || !dataLines.length) return
  try {
    upsertOrderAlert(JSON.parse(dataLines.join('\n')) as OrderNotification, true)
  } catch {
    refreshOrderAlerts(false)
  }
}

async function connectOrderStream() {
  if (!auth.token || streamAbort) return
  streamAbort = new AbortController()
  const activeController = streamAbort
  try {
    const baseUrl = String(http.defaults.baseURL || '/api').replace(/\/$/, '')
    const response = await fetch(`${baseUrl}/admin/order-events`, {
      headers: { Authorization: `Bearer ${auth.token}`, Accept: 'text/event-stream' },
      signal: activeController.signal,
    })
    if (!response.ok || !response.body) throw new Error(`SSE ${response.status}`)
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true }).replaceAll('\r\n', '\n')
      let boundary = buffer.indexOf('\n\n')
      while (boundary >= 0) {
        handleSseBlock(buffer.slice(0, boundary))
        buffer = buffer.slice(boundary + 2)
        boundary = buffer.indexOf('\n\n')
      }
    }
  } catch {
    // 网络抖动后自动重连，30 秒轮询同时兜底。
  } finally {
    if (streamAbort === activeController) streamAbort = null
    if (!activeController.signal.aborted && auth.token) {
      reconnectTimer = window.setTimeout(connectOrderStream, 5000)
    }
  }
}

async function openOrderAlert(item: OrderNotification) {
  if (item.status === 'UNREAD') {
    try {
      const updated = await adminApi.readOrderNotification(item.orderNo)
      item.status = updated.status
      unreadOrderCount.value = Math.max(0, unreadOrderCount.value - 1)
    } catch {
      return
    }
  }
  await router.push({ path: '/orders', query: { orderNo: item.orderNo } })
}

async function acceptOrderAlert(item: OrderNotification) {
  await adminApi.updateOrderStatus(item.orderNo, 'ACCEPTED')
  await adminApi.acceptOrderNotification(item.orderNo)
  orderAlerts.value = orderAlerts.value.filter(alert => alert.orderNo !== item.orderNo)
  knownOrderNos.delete(item.orderNo)
  if (item.status === 'UNREAD') unreadOrderCount.value = Math.max(0, unreadOrderCount.value - 1)
}

function startOrderAlerts() {
  refreshOrderAlerts(false)
  connectOrderStream()
  pollTimer = window.setInterval(() => refreshOrderAlerts(true), 30000)
}

function stopOrderAlerts() {
  if (pollTimer) window.clearInterval(pollTimer)
  if (reconnectTimer) window.clearTimeout(reconnectTimer)
  if (titleTimer) window.clearInterval(titleTimer)
  streamAbort?.abort()
  streamAbort = null
}

onMounted(startOrderAlerts)
onBeforeUnmount(stopOrderAlerts)
</script>

<template>
  <div class="admin-shell" :class="{ 'is-collapsed': collapsed }">
    <div v-if="mobileOpen" class="mobile-mask" @click="mobileOpen = false" />
    <aside class="side-panel" :class="{ 'mobile-open': mobileOpen }">
      <div class="brand-block">
        <div class="brand-mark"><span>鲜</span></div>
        <div class="brand-copy">
          <strong>鲜福铺</strong>
          <small>FRESH MART</small>
        </div>
      </div>

      <div class="store-card">
        <span class="live-dot" />
        <div><strong>中心门店</strong><small>线上营业中</small></div>
      </div>

      <nav class="side-nav" aria-label="管理菜单">
        <button
          v-for="item in menuItems"
          :key="item.path"
          type="button"
          :class="{ active: route.path === item.path }"
          :title="item.label"
          @click="navigate(item.path)"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
          <b v-if="item.path === '/orders' && unreadOrderCount" class="menu-order-badge">{{ unreadOrderCount > 99 ? '99+' : unreadOrderCount }}</b>
        </button>
      </nav>

      <div class="side-foot">
        <div class="delivery-pill"><el-icon><Operation /></el-icon><span>3 公里鲜到家</span></div>
        <button type="button" class="collapse-btn" @click="collapsed = !collapsed">
          <el-icon><Fold /></el-icon><span>收起导航</span>
        </button>
      </div>
    </aside>

    <main class="main-panel">
      <header class="topbar">
        <button class="mobile-menu" type="button" aria-label="打开菜单" @click="mobileOpen = true">
          <el-icon><MenuIcon /></el-icon>
        </button>
        <div class="page-heading">
          <span>{{ eyebrow }}</span>
          <h1>{{ title }}</h1>
        </div>
        <div class="top-actions">
          <el-dropdown trigger="click" placement="bottom-end" :hide-on-click="false">
            <button class="order-alert-button" type="button" title="新订单提醒">
              <el-icon><Bell /></el-icon>
              <span v-if="unreadOrderCount">{{ unreadOrderCount > 99 ? '99+' : unreadOrderCount }}</span>
            </button>
            <template #dropdown>
              <el-dropdown-menu class="order-alert-menu">
                <div class="order-alert-head"><strong>订单提醒</strong><small>{{ unreadOrderCount }} 个未读</small></div>
                <el-empty v-if="!orderAlerts.length" description="暂无待处理提醒" :image-size="58" />
                <div v-for="item in orderAlerts.slice(0, 10)" :key="item.orderNo" class="order-alert-item" :class="{ unread: item.status === 'UNREAD' }" @click="openOrderAlert(item)">
                  <span class="alert-dot" />
                  <div><strong>{{ item.receiverName }} · {{ money(item.payAmount) }}</strong><small>{{ item.orderNo }}</small><small>{{ dateTime(item.paidAt) }}</small></div>
                  <el-button size="small" type="success" plain @click.stop="acceptOrderAlert(item)">接单</el-button>
                </div>
                <button v-if="orderAlerts.length" class="all-orders-link" type="button" @click="navigate('/orders')">查看全部订单</button>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <div class="date-chip">{{ new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', weekday: 'short' }).format(new Date()) }}</div>
          <el-dropdown trigger="click">
            <button class="profile-button" type="button">
              <span class="avatar">{{ auth.user?.nickname?.slice(0, 1) || '管' }}</span>
              <span class="profile-copy"><strong>{{ auth.user?.nickname || '管理员' }}</strong><small>门店管理员</small></span>
            </button>
            <template #dropdown>
              <el-dropdown-menu><el-dropdown-item @click="logout">退出登录</el-dropdown-item></el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <section class="page-body"><router-view /></section>
    </main>
  </div>
</template>

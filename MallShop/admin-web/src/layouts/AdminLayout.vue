<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
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
  User,
} from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const collapsed = ref(false)
const mobileOpen = ref(false)

const title = computed(() => String(route.meta.title || '经营管理'))
const eyebrow = computed(() => String(route.meta.eyebrow || '鲜福铺运营中心'))

const menuItems = [
  { path: '/dashboard', label: '经营看板', icon: DataAnalysis },
  { path: '/products', label: '商品管理', icon: Goods },
  { path: '/categories', label: '分类管理', icon: Grid },
  { path: '/orders', label: '订单管理', icon: Box },
  { path: '/users', label: '会员用户', icon: User },
  { path: '/after-sales', label: '售后处理', icon: Service },
  { path: '/marketing', label: '营销装修', icon: Promotion },
  { path: '/delivery', label: '配送设置', icon: Setting },
]

function navigate(path: string) {
  mobileOpen.value = false
  router.push(path)
}

function logout() {
  auth.logout()
  router.replace('/login')
}
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

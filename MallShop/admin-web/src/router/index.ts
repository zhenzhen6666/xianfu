import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { public: true, title: '管理员登录' } },
    {
      path: '/',
      component: () => import('../layouts/AdminLayout.vue'),
      children: [
        { path: '', redirect: '/dashboard' },
        { path: 'dashboard', component: () => import('../views/DashboardView.vue'), meta: { title: '经营看板', eyebrow: '今日经营概览' } },
        { path: 'products', component: () => import('../views/ProductsView.vue'), meta: { title: '商品管理', eyebrow: '商品、库存与上下架' } },
        { path: 'categories', component: () => import('../views/CategoriesView.vue'), meta: { title: '分类管理', eyebrow: '商品分类与展示顺序' } },
        { path: 'orders', component: () => import('../views/OrdersView.vue'), meta: { title: '订单管理', eyebrow: '履约、退款与订单状态' } },
        { path: 'users', component: () => import('../views/UsersView.vue'), meta: { title: '会员用户', eyebrow: '会员资产与账号状态' } },
        { path: 'coupons', component: () => import('../views/CouponsView.vue'), meta: { title: '优惠券发放', eyebrow: '创建优惠券并按手机号发放' } },
        { path: 'after-sales', component: () => import('../views/AfterSalesView.vue'), meta: { title: '售后处理', eyebrow: '售后审核与商家回复' } },
        { path: 'marketing', component: () => import('../views/MarketingView.vue'), meta: { title: '营销装修', eyebrow: '首页组件与营销活动' } },
        { path: 'delivery', component: () => import('../views/DeliveryView.vue'), meta: { title: '配送设置', eyebrow: '配送半径、运费与时效' } },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
  ],
})

router.beforeEach((to) => {
  document.title = `${String(to.meta.title || '管理系统')} · 鲜福铺`
  const token = localStorage.getItem('xianfupu_admin_token')
  if (!to.meta.public && !token) return { path: '/login', query: { redirect: to.fullPath } }
  if (to.path === '/login' && token) return '/dashboard'
})

export default router

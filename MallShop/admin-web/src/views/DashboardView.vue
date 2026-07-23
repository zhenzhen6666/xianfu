<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { Box, Goods, ShoppingCart, User } from '@element-plus/icons-vue'
import * as echarts from 'echarts/core'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { adminApi } from '../api/admin'
import type { Dashboard, Order, Product } from '../types/api'
import StatusTag from '../components/StatusTag.vue'
import { dateTime, money } from '../utils/format'

echarts.use([BarChart, GridComponent, TooltipComponent, CanvasRenderer])

const loading = ref(true)
const dashboard = ref<Dashboard>({ todayOrders: 0, todaySales: 0, newUsers: 0, hotProducts: [] })
const orders = ref<Order[]>([])
const products = ref<Product[]>([])
const chartEl = ref<HTMLDivElement>()
let chart: echarts.ECharts | undefined

const lowStock = computed(() => products.value.filter((item) => item.stock < 30).length)
const pendingOrders = computed(() => orders.value.filter((item) => ['PAID', 'PENDING_PAYMENT'].includes(item.status)).length)

async function load() {
  loading.value = true
  try {
    const [summary, latestOrders, allProducts] = await Promise.all([
      adminApi.dashboard(), adminApi.orders('ALL', 0, 6), adminApi.products(),
    ])
    dashboard.value = summary
    orders.value = latestOrders
    products.value = allProducts
    await nextTick()
    renderChart()
  } finally {
    loading.value = false
  }
}

function renderChart() {
  if (!chartEl.value) return
  chart ||= echarts.init(chartEl.value)
  const list = dashboard.value.hotProducts.slice(0, 6).reverse()
  chart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 12, right: 20, top: 8, bottom: 8, containLabel: true },
    xAxis: { type: 'value', splitLine: { lineStyle: { color: '#edf0eb' } }, axisLabel: { color: '#839087' } },
    yAxis: { type: 'category', data: list.map((item) => item.name), axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: '#445149', width: 100, overflow: 'truncate' } },
    series: [{ type: 'bar', data: list.map((item) => item.sales), barWidth: 13, itemStyle: { color: '#1e9a61', borderRadius: [0, 8, 8, 0] } }],
  })
}

function resize() { chart?.resize() }
onMounted(() => { load(); window.addEventListener('resize', resize) })
onBeforeUnmount(() => { window.removeEventListener('resize', resize); chart?.dispose() })
</script>

<template>
  <div v-loading="loading" class="dashboard-page">
    <section class="welcome-strip">
      <div><span class="section-kicker">GOOD MORNING</span><h2>今天也要把新鲜准时送达。</h2><p>门店经营数据已更新，关注待配送订单与低库存商品。</p></div>
      <div class="welcome-badge"><span>今日门店状态</span><strong><i /> 营业中</strong></div>
    </section>

    <section class="metric-grid">
      <article class="metric-card"><span class="metric-icon green"><el-icon><ShoppingCart /></el-icon></span><div><small>今日订单</small><strong>{{ dashboard.todayOrders }}</strong><span>笔新订单</span></div></article>
      <article class="metric-card"><span class="metric-icon orange"><el-icon><Goods /></el-icon></span><div><small>今日销售额</small><strong>{{ money(dashboard.todaySales) }}</strong><span>已支付订单</span></div></article>
      <article class="metric-card"><span class="metric-icon blue"><el-icon><User /></el-icon></span><div><small>今日新增会员</small><strong>{{ dashboard.newUsers }}</strong><span>位新会员</span></div></article>
      <article class="metric-card"><span class="metric-icon red"><el-icon><Box /></el-icon></span><div><small>经营提醒</small><strong>{{ pendingOrders + lowStock }}</strong><span>{{ pendingOrders }} 待处理 · {{ lowStock }} 低库存</span></div></article>
    </section>

    <section class="dashboard-grid">
      <article class="content-card chart-card">
        <header><div><span class="section-kicker">TOP PRODUCTS</span><h3>热销商品排行</h3></div><router-link to="/products">查看商品 →</router-link></header>
        <div v-if="dashboard.hotProducts.length" ref="chartEl" class="sales-chart" />
        <el-empty v-else description="暂无销售数据" :image-size="90" />
      </article>

      <article class="content-card stock-card">
        <header><div><span class="section-kicker">STOCK ALERT</span><h3>库存关注</h3></div><router-link to="/products">管理库存 →</router-link></header>
        <div class="stock-list">
          <div v-for="item in [...products].sort((a, b) => a.stock - b.stock).slice(0, 5)" :key="item.productCode" class="stock-row">
            <span class="product-thumb">{{ item.name.slice(0, 1) }}</span>
            <div><strong>{{ item.name }}</strong><small>{{ item.spec }}</small></div>
            <span :class="['stock-number', { danger: item.stock < 30 }]">{{ item.stock }} 件</span>
          </div>
        </div>
      </article>
    </section>

    <article class="content-card recent-card">
      <header><div><span class="section-kicker">LATEST ORDERS</span><h3>最近订单</h3></div><router-link to="/orders">全部订单 →</router-link></header>
      <el-table :data="orders" class="clean-table">
        <el-table-column prop="orderNo" label="订单编号" min-width="185" />
        <el-table-column label="顾客" min-width="120"><template #default="{ row }"><strong>{{ row.nickname }}</strong></template></el-table-column>
        <el-table-column label="收货信息" min-width="220"><template #default="{ row }"><div class="two-line"><span>{{ row.receiverName }} · {{ row.receiverMobile }}</span><small>{{ row.receiverAddress }}</small></div></template></el-table-column>
        <el-table-column label="实付" width="110"><template #default="{ row }"><strong>{{ money(row.payAmount) }}</strong></template></el-table-column>
        <el-table-column label="下单时间" width="160"><template #default="{ row }">{{ dateTime(row.createdAt) }}</template></el-table-column>
        <el-table-column label="状态" width="110"><template #default="{ row }"><StatusTag :status="row.status" /></template></el-table-column>
      </el-table>
    </article>
  </div>
</template>

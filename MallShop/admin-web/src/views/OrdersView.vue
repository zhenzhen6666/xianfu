<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Refresh, Search, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import 'element-plus/es/components/message-box/style/css'
import { adminApi } from '../api/admin'
import PageToolbar from '../components/PageToolbar.vue'
import StatusTag from '../components/StatusTag.vue'
import type { Order, OrderDetail } from '../types/api'
import { dateTime, money } from '../utils/format'

const loading = ref(false)
const status = ref('ALL')
const keyword = ref('')
const orders = ref<Order[]>([])
const detailOpen = ref(false)
const detailLoading = ref(false)
const selectedOrder = ref<Order | null>(null)
const detail = ref<OrderDetail | null>(null)
const refundOpen = ref(false)
const refunding = ref(false)
const refundForm = reactive({ orderNo: '', amount: 0, reason: '' })
const filtered = computed(() => {
  const needle = keyword.value.trim().toLowerCase()
  return orders.value.filter((row) => !needle || [row.orderNo, row.nickname, row.mobile, row.receiverName, row.receiverMobile].some((value) => value?.toLowerCase().includes(needle)))
})

async function load() { loading.value = true; try { orders.value = await adminApi.orders(status.value, 0, 100) } finally { loading.value = false } }
async function openDetail(row: Order) {
  selectedOrder.value = row
  detail.value = null
  detailOpen.value = true
  detailLoading.value = true
  try { detail.value = await adminApi.order(row.orderNo) }
  finally { detailLoading.value = false }
}
async function updateStatus(row: Order, target: string) {
  const labels: Record<string, string> = { SHIPPING: '开始配送', COMPLETED: '完成订单', CANCELLED: '取消订单' }
  await ElMessageBox.confirm(`确定要对订单 ${row.orderNo} 执行“${labels[target]}”吗？`, '确认订单操作', { type: target === 'CANCELLED' ? 'warning' : 'info' })
  await adminApi.updateOrderStatus(row.orderNo, target)
  ElMessage.success(`订单已${labels[target]}`); await load()
}
function openRefund(row: Order) { Object.assign(refundForm, { orderNo: row.orderNo, amount: Number(row.payAmount), reason: '' }); refundOpen.value = true }
async function refund() {
  if (refundForm.amount <= 0 || !refundForm.reason.trim()) return ElMessage.warning('请填写退款金额和原因')
  refunding.value = true
  try { await adminApi.refund(refundForm.orderNo, refundForm.amount, refundForm.reason.trim()); ElMessage.success('退款申请已提交'); refundOpen.value = false; await load() }
  finally { refunding.value = false }
}
onMounted(load)
</script>

<template>
  <div>
    <PageToolbar title="订单列表" description="处理待配送订单、确认履约状态并发起退款"><el-button :icon="Refresh" @click="load">刷新</el-button></PageToolbar>
    <div class="filter-bar">
      <el-input v-model="keyword" :prefix-icon="Search" clearable placeholder="搜索订单、顾客或手机号" class="search-input" />
      <el-select v-model="status" style="width: 160px" @change="load"><el-option label="全部状态" value="ALL" /><el-option label="待支付" value="WAIT_PAY" /><el-option label="待配送" value="SHIPPING" /><el-option label="已完成" value="COMPLETED" /><el-option label="已取消" value="CANCELLED" /><el-option label="已退款" value="REFUNDED" /></el-select>
      <span class="result-count">共 {{ filtered.length }} 笔订单</span>
    </div>
    <article class="content-card table-card" v-loading="loading">
      <el-table :data="filtered" class="clean-table" @row-dblclick="openDetail">
        <el-table-column label="订单信息" min-width="205"><template #default="{ row }"><div class="two-line"><el-button class="order-number" link type="primary" @click.stop="openDetail(row)">{{ row.orderNo }}</el-button><small>{{ dateTime(row.createdAt) }}</small></div></template></el-table-column>
        <el-table-column label="顾客" width="130"><template #default="{ row }"><div class="two-line"><strong>{{ row.nickname }}</strong><small>{{ row.mobile || '未留手机号' }}</small></div></template></el-table-column>
        <el-table-column label="配送信息" min-width="275"><template #default="{ row }"><div class="two-line"><strong>{{ row.receiverName }} · {{ row.receiverMobile }}</strong><small>{{ row.receiverAddress }}</small></div></template></el-table-column>
        <el-table-column label="金额" width="125"><template #default="{ row }"><div class="two-line"><strong>{{ money(row.payAmount) }}</strong><small>配送费 {{ money(row.deliveryFee) }}</small></div></template></el-table-column>
        <el-table-column label="状态" width="110"><template #default="{ row }"><StatusTag :status="row.status" /></template></el-table-column>
        <el-table-column label="操作" width="290" fixed="right">
          <template #default="{ row }">
            <el-button size="small" plain type="primary" :icon="View" @click="openDetail(row)">查看详情</el-button>
            <el-button v-if="row.status === 'SHIPPING'" link type="success" @click="updateStatus(row, 'COMPLETED')">确认完成</el-button>
            <el-button v-if="row.status === 'WAIT_PAY'" link type="danger" @click="updateStatus(row, 'CANCELLED')">取消</el-button>
            <el-button v-if="['SHIPPING', 'COMPLETED'].includes(row.status)" link type="warning" @click="openRefund(row)">退款</el-button>
            <span v-if="['CANCELLED', 'REFUNDED'].includes(row.status)" class="muted-text">无需操作</span>
          </template>
        </el-table-column>
      </el-table>
    </article>
    <el-dialog v-model="detailOpen" title="订单详情" width="880px" destroy-on-close>
      <div v-loading="detailLoading" class="order-detail">
        <template v-if="detail">
          <div class="detail-heading">
            <div><small>订单编号</small><strong>{{ detail.orderNo }}</strong></div>
            <StatusTag :status="detail.status" />
          </div>

          <el-descriptions title="顾客与配送信息" :column="2" border>
            <el-descriptions-item label="下单顾客">{{ selectedOrder?.nickname || '—' }}</el-descriptions-item>
            <el-descriptions-item label="顾客手机">{{ selectedOrder?.mobile || '未留手机号' }}</el-descriptions-item>
            <el-descriptions-item label="收货人">{{ detail.receiverName }}</el-descriptions-item>
            <el-descriptions-item label="收货手机">{{ detail.receiverMobile }}</el-descriptions-item>
            <el-descriptions-item label="收货地址" :span="2">{{ detail.receiverAddress }}</el-descriptions-item>
            <el-descriptions-item label="订单备注" :span="2">{{ detail.note || '无备注' }}</el-descriptions-item>
          </el-descriptions>

          <section class="detail-section">
            <h3>商品明细 <span>共 {{ detail.items.reduce((sum, item) => sum + item.count, 0) }} 件</span></h3>
            <el-table :data="detail.items" border>
              <el-table-column label="商品" min-width="250">
                <template #default="{ row }"><div class="detail-product"><span>{{ row.name.slice(0, 1) }}</span><div><strong>{{ row.name }}</strong><small>{{ row.spec || '默认规格' }}</small></div></div></template>
              </el-table-column>
              <el-table-column label="单价" width="120"><template #default="{ row }">{{ money(row.price) }}</template></el-table-column>
              <el-table-column prop="count" label="数量" width="90" />
              <el-table-column label="小计" width="130" align="right"><template #default="{ row }"><strong>{{ money(row.lineAmount) }}</strong></template></el-table-column>
            </el-table>
          </section>

          <div class="detail-bottom">
            <el-descriptions title="订单时间" :column="1" border>
              <el-descriptions-item label="创建时间">{{ dateTime(detail.createdAt) }}</el-descriptions-item>
              <el-descriptions-item label="支付截止">{{ dateTime(detail.payDeadline) }}</el-descriptions-item>
              <el-descriptions-item label="支付时间">{{ dateTime(detail.paidAt) }}</el-descriptions-item>
              <el-descriptions-item label="配送时间">{{ dateTime(detail.shippedAt) }}</el-descriptions-item>
              <el-descriptions-item label="完成时间">{{ dateTime(detail.completedAt) }}</el-descriptions-item>
              <el-descriptions-item v-if="detail.cancelledAt" label="取消时间">{{ dateTime(detail.cancelledAt) }}</el-descriptions-item>
              <el-descriptions-item v-if="detail.cancelReason" label="取消原因">{{ detail.cancelReason }}</el-descriptions-item>
            </el-descriptions>
            <div class="amount-card">
              <div><span>商品金额</span><strong>{{ money(detail.subtotal) }}</strong></div>
              <div><span>配送费</span><strong>{{ money(detail.deliveryFee) }}</strong></div>
              <div><span>优惠券</span><strong>-{{ money(detail.couponDiscount) }}</strong></div>
              <div><span>会员优惠</span><strong>-{{ money(detail.memberDiscount) }}</strong></div>
              <div><span>积分抵扣（{{ detail.pointsUsed }} 分）</span><strong>-{{ money(detail.pointsDiscount) }}</strong></div>
              <div class="pay-total"><span>实付金额</span><strong>{{ money(detail.payAmount) }}</strong></div>
            </div>
          </div>
        </template>
      </div>
      <template #footer><el-button @click="detailOpen = false">关闭</el-button></template>
    </el-dialog>
    <el-dialog v-model="refundOpen" title="订单退款" width="520px">
      <div class="dialog-notice">退款订单：<strong>{{ refundForm.orderNo }}</strong></div>
      <el-form label-position="top"><el-form-item label="退款金额"><el-input-number v-model="refundForm.amount" :min="0.01" :precision="2" style="width:100%" /></el-form-item><el-form-item label="退款原因"><el-input v-model="refundForm.reason" type="textarea" :rows="4" maxlength="255" show-word-limit /></el-form-item></el-form>
      <template #footer><el-button @click="refundOpen = false">取消</el-button><el-button type="danger" :loading="refunding" @click="refund">确认退款</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.order-detail { min-height: 260px; }
.order-number { align-self: flex-start; height: auto; padding: 0; font-weight: 700; }
.detail-heading { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px; padding: 16px 18px; background: #f3f7f3; border-radius: 12px; }
.detail-heading > div { display: flex; flex-direction: column; gap: 5px; }
.detail-heading small { color: #87948c; }
.detail-heading strong { color: #203028; font-size: 17px; }
.detail-section { margin-top: 24px; }
.detail-section h3 { margin: 0 0 12px; font-size: 15px; }
.detail-section h3 span { margin-left: 8px; color: #87948c; font-size: 12px; font-weight: 400; }
.detail-product { display: flex; align-items: center; gap: 12px; }
.detail-product > span { display: grid; width: 38px; height: 38px; place-items: center; flex: 0 0 auto; color: #168653; font-weight: 700; background: #eaf6ee; border-radius: 10px; }
.detail-product > div { display: flex; flex-direction: column; gap: 4px; }
.detail-product small { color: #87948c; }
.detail-bottom { display: grid; grid-template-columns: minmax(0, 1.35fr) minmax(280px, .65fr); gap: 20px; margin-top: 24px; }
.amount-card { align-self: start; padding: 17px 20px; background: #fafbf9; border: 1px solid #e6ebe5; border-radius: 12px; }
.amount-card > div { display: flex; justify-content: space-between; padding: 7px 0; color: #68766e; font-size: 12px; }
.amount-card strong { color: #2b3931; }
.amount-card .pay-total { margin-top: 8px; padding-top: 15px; border-top: 1px solid #dfe6df; font-size: 14px; }
.amount-card .pay-total strong { color: #168653; font-size: 20px; }
@media (max-width: 720px) { .detail-bottom { grid-template-columns: 1fr; } }
</style>

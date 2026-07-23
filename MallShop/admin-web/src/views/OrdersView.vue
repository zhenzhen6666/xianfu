<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import 'element-plus/es/components/message-box/style/css'
import { adminApi } from '../api/admin'
import PageToolbar from '../components/PageToolbar.vue'
import StatusTag from '../components/StatusTag.vue'
import type { Order } from '../types/api'
import { dateTime, money } from '../utils/format'

const loading = ref(false)
const status = ref('ALL')
const keyword = ref('')
const orders = ref<Order[]>([])
const refundOpen = ref(false)
const refunding = ref(false)
const refundForm = reactive({ orderNo: '', amount: 0, reason: '' })
const filtered = computed(() => {
  const needle = keyword.value.trim().toLowerCase()
  return orders.value.filter((row) => !needle || [row.orderNo, row.nickname, row.mobile, row.receiverName, row.receiverMobile].some((value) => value?.toLowerCase().includes(needle)))
})

async function load() { loading.value = true; try { orders.value = await adminApi.orders(status.value, 0, 100) } finally { loading.value = false } }
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
      <el-select v-model="status" style="width: 160px" @change="load"><el-option label="全部状态" value="ALL" /><el-option label="待支付" value="PENDING_PAYMENT" /><el-option label="待配送" value="PAID" /><el-option label="配送中" value="SHIPPING" /><el-option label="已完成" value="COMPLETED" /><el-option label="已取消" value="CANCELLED" /></el-select>
      <span class="result-count">共 {{ filtered.length }} 笔订单</span>
    </div>
    <article class="content-card table-card" v-loading="loading">
      <el-table :data="filtered" class="clean-table">
        <el-table-column label="订单信息" min-width="205"><template #default="{ row }"><div class="two-line"><strong>{{ row.orderNo }}</strong><small>{{ dateTime(row.createdAt) }}</small></div></template></el-table-column>
        <el-table-column label="顾客" width="130"><template #default="{ row }"><div class="two-line"><strong>{{ row.nickname }}</strong><small>{{ row.mobile || '未留手机号' }}</small></div></template></el-table-column>
        <el-table-column label="配送信息" min-width="275"><template #default="{ row }"><div class="two-line"><strong>{{ row.receiverName }} · {{ row.receiverMobile }}</strong><small>{{ row.receiverAddress }}</small></div></template></el-table-column>
        <el-table-column label="金额" width="125"><template #default="{ row }"><div class="two-line"><strong>{{ money(row.payAmount) }}</strong><small>配送费 {{ money(row.deliveryFee) }}</small></div></template></el-table-column>
        <el-table-column label="状态" width="110"><template #default="{ row }"><StatusTag :status="row.status" /></template></el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PAID'" link type="primary" @click="updateStatus(row, 'SHIPPING')">开始配送</el-button>
            <el-button v-if="row.status === 'SHIPPING'" link type="success" @click="updateStatus(row, 'COMPLETED')">确认完成</el-button>
            <el-button v-if="['PENDING_PAYMENT', 'PAID'].includes(row.status)" link type="danger" @click="updateStatus(row, 'CANCELLED')">取消</el-button>
            <el-button v-if="['PAID', 'SHIPPING', 'COMPLETED'].includes(row.status)" link type="warning" @click="openRefund(row)">退款</el-button>
            <span v-if="['CANCELLED', 'REFUNDED'].includes(row.status)" class="muted-text">无需操作</span>
          </template>
        </el-table-column>
      </el-table>
    </article>
    <el-dialog v-model="refundOpen" title="订单退款" width="520px">
      <div class="dialog-notice">退款订单：<strong>{{ refundForm.orderNo }}</strong></div>
      <el-form label-position="top"><el-form-item label="退款金额"><el-input-number v-model="refundForm.amount" :min="0.01" :precision="2" style="width:100%" /></el-form-item><el-form-item label="退款原因"><el-input v-model="refundForm.reason" type="textarea" :rows="4" maxlength="255" show-word-limit /></el-form-item></el-form>
      <template #footer><el-button @click="refundOpen = false">取消</el-button><el-button type="danger" :loading="refunding" @click="refund">确认退款</el-button></template>
    </el-dialog>
  </div>
</template>

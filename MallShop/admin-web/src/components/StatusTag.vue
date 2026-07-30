<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ status: string }>()
const map: Record<string, { label: string; type: 'success' | 'warning' | 'info' | 'danger' | 'primary' }> = {
  ACTIVE: { label: '正常', type: 'success' },
  ENABLED: { label: '启用', type: 'success' },
  ON_SALE: { label: '销售中', type: 'success' },
  WAIT_PAY: { label: '待支付', type: 'warning' },
  PAID: { label: '已支付', type: 'warning' },
  ACCEPTED: { label: '已接单', type: 'primary' },
  PREPARING: { label: '备货中', type: 'primary' },
  PENDING: { label: '待处理', type: 'warning' },
  PENDING_PAYMENT: { label: '待支付', type: 'warning' },
  SHIPPING: { label: '配送中', type: 'primary' },
  DELIVERED: { label: '已送达', type: 'success' },
  PROCESSING: { label: '处理中', type: 'primary' },
  COMPLETED: { label: '已完成', type: 'success' },
  APPROVED: { label: '已同意', type: 'success' },
  REJECTED: { label: '已拒绝', type: 'danger' },
  CANCELLED: { label: '已取消', type: 'info' },
  OFF_SHELF: { label: '已下架', type: 'info' },
  BLOCKED: { label: '已停用', type: 'danger' },
  REFUNDED: { label: '已退款', type: 'info' },
}
const value = computed(() => map[props.status] || { label: props.status, type: 'info' as const })
</script>

<template><el-tag :type="value.type" effect="light" round>{{ value.label }}</el-tag></template>

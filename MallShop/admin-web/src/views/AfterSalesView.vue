<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '../api/admin'
import { http } from '../api/http'
import PageToolbar from '../components/PageToolbar.vue'
import StatusTag from '../components/StatusTag.vue'
import type { AfterSale, AfterSaleDetail } from '../types/api'
import { dateTime, money } from '../utils/format'

const loading = ref(false)
const saving = ref(false)
const status = ref('ALL')
const dialogOpen = ref(false)
const items = ref<AfterSale[]>([])
const detail = ref<AfterSaleDetail | null>(null)
const form = reactive({ afterSaleNo: '', status: 'PROCESSING', reply: '', reason: '', description: '', refundAmount: 0 })
async function load() { loading.value = true; try { items.value = await adminApi.afterSales(status.value) } finally { loading.value = false } }
async function handle(row: AfterSale) {
  Object.assign(form, { afterSaleNo: row.afterSaleNo, status: row.status === 'PENDING' ? 'PROCESSING' : row.status, reply: row.merchantReply || '', reason: row.reason, description: row.description || '', refundAmount: Number(row.refundAmount) })
  detail.value = await adminApi.afterSale(row.afterSaleNo)
  dialogOpen.value = true
}
function imageUrl(value: string) {
  if (/^https?:\/\//.test(value)) return value
  const base = String(http.defaults.baseURL || '/api').replace(/\/api\/?$/, '')
  return `${base}${value}`
}
async function save() {
  saving.value = true
  try { await adminApi.updateAfterSale(form.afterSaleNo, form.status, form.reply, form.status === 'APPROVED' ? form.refundAmount : undefined); ElMessage.success('售后处理结果已保存'); dialogOpen.value = false; await load() }
  finally { saving.value = false }
}
onMounted(load)
</script>

<template>
  <div>
    <PageToolbar title="售后申请" description="审核退款退货诉求并向顾客同步处理结果"><el-button :icon="Refresh" @click="load">刷新</el-button></PageToolbar>
    <div class="filter-bar"><el-select v-model="status" style="width:160px" @change="load"><el-option label="全部状态" value="ALL" /><el-option label="待处理" value="PENDING" /><el-option label="处理中" value="PROCESSING" /><el-option label="已同意" value="APPROVED" /><el-option label="已拒绝" value="REJECTED" /></el-select><span class="result-count">共 {{ items.length }} 条申请</span></div>
    <article class="content-card table-card" v-loading="loading">
      <el-table :data="items" class="clean-table">
        <el-table-column label="售后编号" min-width="175"><template #default="{ row }"><div class="two-line"><strong>{{ row.afterSaleNo }}</strong><small>订单 {{ row.orderNo }}</small></div></template></el-table-column>
        <el-table-column label="申请人" width="120"><template #default="{ row }"><strong>{{ row.nickname }}</strong></template></el-table-column>
        <el-table-column label="售后原因" min-width="240"><template #default="{ row }"><div class="two-line"><strong>{{ row.reason }}</strong><small>{{ row.description || '无补充说明' }} · {{ row.imageCount }} 张凭证</small></div></template></el-table-column>
        <el-table-column label="退款金额" width="120"><template #default="{ row }"><strong>{{ money(row.refundAmount) }}</strong></template></el-table-column>
        <el-table-column label="退款进度" width="150"><template #default="{ row }"><div class="two-line"><strong>{{ row.refundStatus }}</strong><small v-if="row.refundError" style="color:#c84834">{{ row.refundError }}</small><small v-else>已退 {{ money(row.refundedAmount) }}</small></div></template></el-table-column>
        <el-table-column label="申请时间" width="165"><template #default="{ row }">{{ dateTime(row.createdAt) }}</template></el-table-column>
        <el-table-column label="状态" width="110"><template #default="{ row }"><StatusTag :status="row.status" /></template></el-table-column>
        <el-table-column label="操作" width="100"><template #default="{ row }"><el-button link type="primary" @click="handle(row)">处理申请</el-button></template></el-table-column>
      </el-table>
    </article>
    <el-dialog v-model="dialogOpen" title="处理售后申请" width="580px">
      <div class="dialog-notice"><strong>{{ form.reason }}</strong><p>{{ form.description || '顾客未填写补充说明' }}</p></div>
      <div v-if="detail?.images.length" class="evidence"><strong>图片凭证</strong><div><el-image v-for="image in detail.images" :key="image" :src="imageUrl(image)" :preview-src-list="detail.images.map(imageUrl)" fit="cover"/></div></div>
      <el-timeline v-if="detail?.history.length" class="progress"><el-timeline-item v-for="item in detail.history" :key="item.createdAt" :timestamp="dateTime(item.createdAt)" placement="top"><strong>{{ item.message }}</strong><small>{{ item.status }} · {{ item.refundStatus }}</small></el-timeline-item></el-timeline>
      <el-form label-position="top"><el-form-item label="处理状态"><el-radio-group v-model="form.status"><el-radio-button value="PROCESSING">处理中</el-radio-button><el-radio-button value="APPROVED">同意并退款</el-radio-button><el-radio-button value="REJECTED">拒绝</el-radio-button></el-radio-group></el-form-item><el-form-item v-if="form.status === 'APPROVED'" label="退款金额（支持部分退款）"><el-input-number v-model="form.refundAmount" :min="0.01" :precision="2" style="width:100%" /></el-form-item><el-form-item label="商家回复"><el-input v-model="form.reply" type="textarea" :rows="5" maxlength="1000" show-word-limit placeholder="请向顾客说明处理方案" /></el-form-item></el-form>
      <template #footer><el-button @click="dialogOpen = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存处理结果</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.evidence{margin:18px 0}.evidence>div{display:flex;gap:10px;margin-top:10px}.evidence :deep(.el-image){width:92px;height:92px;border-radius:10px}.progress{margin:20px 0}.progress strong,.progress small{display:block}.progress small{margin-top:5px;color:#87928b}
</style>

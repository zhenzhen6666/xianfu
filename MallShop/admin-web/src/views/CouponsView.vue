<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Plus, Refresh, Tickets } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../api/admin'
import PageToolbar from '../components/PageToolbar.vue'
import type { Coupon, CouponIssueRecord, CouponPayload } from '../types/api'
import { dateTime, money } from '../utils/format'

const loading = ref(false)
const saving = ref(false)
const formOpen = ref(false)
const issueOpen = ref(false)
const recordsOpen = ref(false)
const editingCode = ref('')
const coupons = ref<Coupon[]>([])
const records = ref<CouponIssueRecord[]>([])
const selected = ref<Coupon | null>(null)
const issueMode = ref<'single' | 'batch'>('single')
const issueText = ref('')

function localDateTime(date: Date) {
  const offset = date.getTimezoneOffset() * 60000
  return new Date(date.getTime() - offset).toISOString().slice(0, 19)
}
function defaultForm(): CouponPayload {
  const now = new Date(); const end = new Date(now); end.setMonth(end.getMonth() + 1)
  return { name: '', amount: 5, minimumAmount: 0, totalStock: 100, startsAt: localDateTime(now), endsAt: localDateTime(end), enabled: true }
}
const form = reactive<CouponPayload>(defaultForm())
const activeCount = computed(() => coupons.value.filter(item => item.status === 'ACTIVE').length)
const issuedCount = computed(() => coupons.value.reduce((sum, item) => sum + item.claimedCount, 0))
const statusMeta: Record<string, { label: string; type: '' | 'success' | 'warning' | 'info' | 'danger' }> = {
  ACTIVE: { label: '可发放', type: 'success' }, NOT_STARTED: { label: '未开始', type: 'warning' },
  EXPIRED: { label: '已过期', type: 'info' }, DISABLED: { label: '已作废', type: 'info' },
  OUT_OF_STOCK: { label: '已发完', type: 'danger' },
}
async function load() { loading.value = true; try { coupons.value = await adminApi.coupons() } finally { loading.value = false } }
function conditionText(row: Coupon) { return row.minimumAmount > 0 ? `满 ${money(row.minimumAmount)} 减 ${money(row.amount)}` : `无门槛减 ${money(row.amount)}` }
function openCreate() { editingCode.value = ''; Object.assign(form, defaultForm()); formOpen.value = true }
function openEdit(row: Coupon) {
  editingCode.value = row.code
  Object.assign(form, { name: row.name, amount: Number(row.amount), minimumAmount: Number(row.minimumAmount), totalStock: row.totalStock, startsAt: row.startsAt, endsAt: row.endsAt, enabled: row.enabled })
  formOpen.value = true
}
async function save() {
  if (!form.name.trim() || form.amount <= 0 || form.totalStock < 1 || new Date(form.endsAt) <= new Date(form.startsAt)) return ElMessage.warning('请检查优惠券名称、金额、库存和有效期')
  saving.value = true
  try {
    if (editingCode.value) await adminApi.updateCoupon(editingCode.value, { ...form, name: form.name.trim() })
    else await adminApi.createCoupon({ ...form, name: form.name.trim() })
    ElMessage.success(editingCode.value ? '优惠券已更新' : '优惠券已创建'); formOpen.value = false; await load()
  } finally { saving.value = false }
}
async function voidCoupon(row: Coupon) {
  await ElMessageBox.confirm(`作废后，用户账户中尚未使用的“${row.name}”也将不可用。确定继续吗？`, '作废优惠券', { type: 'warning' })
  await adminApi.voidCoupon(row.code); ElMessage.success('优惠券已作废'); await load()
}
function openIssue(row: Coupon) { selected.value = row; issueMode.value = 'single'; issueText.value = ''; issueOpen.value = true }
function mobiles() { return issueText.value.split(/[,\n，;；\\s]+/).map(value => value.trim()).filter(Boolean) }
async function issue() {
  if (!selected.value) return
  const values = mobiles()
  if (!values.length || values.some(value => !/^1[3-9]\d{9}$/.test(value))) return ElMessage.warning('请填写正确的 11 位手机号')
  saving.value = true
  try {
    if (issueMode.value === 'single') {
      const result = await adminApi.issueCoupon(selected.value.code, values[0])
      ElMessage.success(`已发放到 ${result.nickname}（${result.mobile}）`)
    } else {
      const result = await adminApi.batchIssueCoupon(selected.value.code, values)
      ElMessage.success(`批次 ${result.batchNo}：成功 ${result.successCount}，失败 ${result.failedCount}`)
    }
    issueOpen.value = false; await load()
  } finally { saving.value = false }
}
async function openRecords(row?: Coupon) {
  selected.value = row || null; recordsOpen.value = true
  records.value = await adminApi.couponIssueRecords(row?.code)
}
onMounted(load)
</script>

<template>
  <div>
    <PageToolbar title="优惠券管理" description="编辑、作废、按手机号批量发放优惠券并查询完整发放记录">
      <el-button :icon="Refresh" @click="load">刷新</el-button>
      <el-button @click="openRecords()">发放记录</el-button>
      <el-button type="primary" :icon="Plus" @click="openCreate">新建优惠券</el-button>
    </PageToolbar>
    <section class="summary">
      <div><el-icon><Tickets /></el-icon><span>优惠券模板<strong>{{ coupons.length }}</strong></span></div>
      <div><span>当前可发放<strong>{{ activeCount }}</strong></span></div>
      <div><span>累计发放<strong>{{ issuedCount }}</strong></span></div>
    </section>
    <article class="content-card table-card" v-loading="loading">
      <el-table :data="coupons">
        <el-table-column label="优惠券" min-width="230"><template #default="{row}"><div class="coupon"><b>¥{{ Number(row.amount).toFixed(0) }}</b><div><strong>{{ row.name }}</strong><small>{{ row.code }}</small></div></div></template></el-table-column>
        <el-table-column label="优惠规则" min-width="180"><template #default="{row}">{{ conditionText(row) }}</template></el-table-column>
        <el-table-column label="发放进度" width="190"><template #default="{row}"><span>{{ row.claimedCount }} / {{ row.totalStock }}</span><el-progress :percentage="Math.round(row.claimedCount/row.totalStock*100)" :show-text="false" :stroke-width="5"/></template></el-table-column>
        <el-table-column label="有效期" min-width="225"><template #default="{row}"><div class="two-line"><span>{{ dateTime(row.startsAt) }}</span><small>至 {{ dateTime(row.endsAt) }}</small></div></template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="{row}"><el-tag :type="(statusMeta[row.status]||statusMeta.DISABLED).type">{{ (statusMeta[row.status]||statusMeta.DISABLED).label }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="250" fixed="right"><template #default="{row}"><el-button link type="primary" @click="openEdit(row)">编辑</el-button><el-button link type="success" :disabled="!row.enabled||['EXPIRED','OUT_OF_STOCK'].includes(row.status)" @click="openIssue(row)">发放</el-button><el-button link @click="openRecords(row)">记录</el-button><el-button v-if="row.enabled" link type="danger" @click="voidCoupon(row)">作废</el-button></template></el-table-column>
      </el-table>
    </article>

    <el-dialog v-model="formOpen" :title="editingCode ? '编辑优惠券' : '新建优惠券'" width="620px">
      <el-form label-position="top" class="form-grid">
        <el-form-item label="优惠券名称" class="span-2"><el-input v-model="form.name" maxlength="100"/></el-form-item>
        <el-form-item label="优惠金额"><el-input-number v-model="form.amount" :min="0.01" :precision="2"/></el-form-item>
        <el-form-item label="使用门槛（0 为无门槛）"><el-input-number v-model="form.minimumAmount" :min="0" :precision="2"/></el-form-item>
        <el-form-item label="发行数量"><el-input-number v-model="form.totalStock" :min="Math.max(1, coupons.find(i=>i.code===editingCode)?.claimedCount||1)"/></el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.enabled"/></el-form-item>
        <el-form-item label="开始时间"><el-date-picker v-model="form.startsAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss"/></el-form-item>
        <el-form-item label="结束时间"><el-date-picker v-model="form.endsAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss"/></el-form-item>
      </el-form>
      <template #footer><el-button @click="formOpen=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="issueOpen" title="发放优惠券" width="560px">
      <div v-if="selected" class="preview"><b>¥{{ selected.amount }}</b><span>{{ selected.name }}<small>{{ conditionText(selected) }}</small></span></div>
      <el-radio-group v-model="issueMode"><el-radio-button value="single">单个发放</el-radio-button><el-radio-button value="batch">批量发放</el-radio-button></el-radio-group>
      <el-input v-model="issueText" type="textarea" :rows="issueMode==='batch'?8:2" :placeholder="issueMode==='batch'?'每行一个手机号，最多 500 个':'输入用户手机号'" style="margin-top:18px"/>
      <template #footer><el-button @click="issueOpen=false">取消</el-button><el-button type="primary" :loading="saving" @click="issue">确认发放</el-button></template>
    </el-dialog>

    <el-dialog v-model="recordsOpen" :title="selected ? `${selected.name} · 发放记录` : '全部发放记录'" width="900px">
      <el-table :data="records" max-height="560"><el-table-column prop="batchNo" label="批次" width="190"/><el-table-column prop="couponName" label="优惠券" min-width="150"/><el-table-column prop="mobile" label="手机号" width="130"/><el-table-column prop="nickname" label="用户" width="110"/><el-table-column label="结果" width="90"><template #default="{row}"><el-tag :type="row.status==='SUCCESS'?'success':'danger'">{{ row.status==='SUCCESS'?'成功':'失败' }}</el-tag></template></el-table-column><el-table-column prop="failureReason" label="失败原因" min-width="180"/><el-table-column label="时间" width="165"><template #default="{row}">{{ dateTime(row.createdAt) }}</template></el-table-column></el-table>
    </el-dialog>
  </div>
</template>

<style scoped>
.summary{display:grid;grid-template-columns:repeat(3,1fr);gap:16px;margin-bottom:18px}.summary>div{display:flex;align-items:center;gap:14px;padding:20px;background:#fff;border-radius:14px}.summary span{display:flex;flex-direction:column;color:#78857d}.summary strong{color:#24342b;font-size:25px}.coupon,.preview{display:flex;align-items:center;gap:15px}.coupon>b,.preview>b{color:#ef6a42;font-size:25px}.coupon div,.preview span{display:flex;flex-direction:column;gap:4px}.coupon small,.preview small{color:#8a958e}.el-progress{margin-top:8px}@media(max-width:720px){.summary{grid-template-columns:1fr}}
</style>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Plus, Refresh, Tickets } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '../api/admin'
import PageToolbar from '../components/PageToolbar.vue'
import type { Coupon, CouponPayload } from '../types/api'
import { dateTime, money } from '../utils/format'

const loading = ref(false)
const saving = ref(false)
const issuing = ref(false)
const createOpen = ref(false)
const issueOpen = ref(false)
const coupons = ref<Coupon[]>([])
const selectedCoupon = ref<Coupon | null>(null)
const issueForm = reactive({ mobile: '' })

function localDateTime(date: Date) {
  const offset = date.getTimezoneOffset() * 60000
  return new Date(date.getTime() - offset).toISOString().slice(0, 19)
}

function defaultForm(): CouponPayload {
  const now = new Date()
  const end = new Date(now)
  end.setMonth(end.getMonth() + 1)
  return {
    name: '',
    amount: 5,
    minimumAmount: 0,
    totalStock: 100,
    startsAt: localDateTime(now),
    endsAt: localDateTime(end),
    enabled: true,
  }
}

const createForm = reactive<CouponPayload>(defaultForm())
const activeCount = computed(() => coupons.value.filter(item => item.status === 'ACTIVE').length)
const issuedCount = computed(() => coupons.value.reduce((sum, item) => sum + item.claimedCount, 0))

const statusMeta: Record<Coupon['status'], { label: string; type: '' | 'success' | 'warning' | 'info' | 'danger' }> = {
  ACTIVE: { label: '可发放', type: 'success' },
  NOT_STARTED: { label: '未开始', type: 'warning' },
  EXPIRED: { label: '已过期', type: 'info' },
  DISABLED: { label: '已停用', type: 'info' },
  OUT_OF_STOCK: { label: '已发完', type: 'danger' },
}

async function load() {
  loading.value = true
  try { coupons.value = await adminApi.coupons() } finally { loading.value = false }
}

function openCreate() {
  Object.assign(createForm, defaultForm())
  createOpen.value = true
}

function conditionText(coupon: Coupon) {
  return coupon.minimumAmount > 0 ? `满 ${money(coupon.minimumAmount)} 减 ${money(coupon.amount)}` : `无门槛减 ${money(coupon.amount)}`
}

function openIssue(coupon: Coupon) {
  selectedCoupon.value = coupon
  issueForm.mobile = ''
  issueOpen.value = true
}

async function createCoupon() {
  if (!createForm.name.trim()) return ElMessage.warning('请填写优惠券名称')
  if (createForm.amount <= 0 || createForm.minimumAmount < 0 || createForm.totalStock < 1) {
    return ElMessage.warning('请检查优惠金额、使用门槛和发行数量')
  }
  if (!createForm.startsAt || !createForm.endsAt || new Date(createForm.endsAt) <= new Date(createForm.startsAt)) {
    return ElMessage.warning('结束时间必须晚于开始时间')
  }
  saving.value = true
  try {
    await adminApi.createCoupon({ ...createForm, name: createForm.name.trim() })
    ElMessage.success('优惠券创建成功')
    createOpen.value = false
    await load()
  } finally { saving.value = false }
}

async function issueCoupon() {
  if (!selectedCoupon.value) return
  const mobile = issueForm.mobile.trim()
  if (!/^1[3-9]\d{9}$/.test(mobile)) return ElMessage.warning('请输入正确的 11 位手机号')
  issuing.value = true
  try {
    const result = await adminApi.issueCoupon(selectedCoupon.value.code, mobile)
    ElMessage.success(`已发放到 ${result.nickname}（${result.mobile}）`)
    issueOpen.value = false
    await load()
  } finally { issuing.value = false }
}

onMounted(load)
</script>

<template>
  <div>
    <PageToolbar title="优惠券发放" description="设置满减或无门槛优惠券，并直接发放到手机号对应的用户账户">
      <el-button :icon="Refresh" @click="load">刷新</el-button>
      <el-button type="primary" :icon="Plus" @click="openCreate">新建优惠券</el-button>
    </PageToolbar>

    <section class="coupon-summary">
      <div><span class="coupon-summary-icon"><el-icon><Tickets /></el-icon></span><p><small>优惠券模板</small><strong>{{ coupons.length }}</strong></p></div>
      <div><span class="coupon-summary-icon active">发</span><p><small>当前可发放</small><strong>{{ activeCount }}</strong></p></div>
      <div><span class="coupon-summary-icon issued">账</span><p><small>累计到账张数</small><strong>{{ issuedCount }}</strong></p></div>
    </section>

    <article class="content-card table-card" v-loading="loading">
      <el-table :data="coupons" class="clean-table">
        <el-table-column label="优惠券" min-width="220">
          <template #default="{ row }">
            <div class="coupon-name-cell">
              <span>¥<strong>{{ Number(row.amount).toFixed(0) }}</strong></span>
              <div><strong>{{ row.name }}</strong><small>{{ row.code }}</small></div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="优惠规则" min-width="180">
          <template #default="{ row }"><strong>{{ conditionText(row) }}</strong></template>
        </el-table-column>
        <el-table-column label="发放进度" min-width="190">
          <template #default="{ row }">
            <div class="coupon-stock">
              <span>已发 {{ row.claimedCount }} / {{ row.totalStock }}</span>
              <el-progress :percentage="Math.round(row.claimedCount / row.totalStock * 100)" :stroke-width="5" :show-text="false" />
            </div>
          </template>
        </el-table-column>
        <el-table-column label="有效期" min-width="235">
          <template #default="{ row }"><div class="two-line"><span>{{ dateTime(row.startsAt) }}</span><small>至 {{ dateTime(row.endsAt) }}</small></div></template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><el-tag :type="statusMeta[row.status].type" round>{{ statusMeta[row.status].label }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="!row.enabled || row.status === 'EXPIRED' || row.status === 'OUT_OF_STOCK'" @click="openIssue(row)">发放</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !coupons.length" description="还没有优惠券，先创建一张吧" />
    </article>

    <el-dialog v-model="createOpen" title="新建优惠券" width="620px">
      <div class="dialog-notice"><strong>优惠规则</strong><p>使用门槛填 0 即为无门槛券；填大于 0 的金额即为“满多少减多少”。</p></div>
      <el-form label-position="top" class="form-grid">
        <el-form-item label="优惠券名称" class="span-2"><el-input v-model="createForm.name" maxlength="100" placeholder="例如：夏日满 49 减 8 元券" /></el-form-item>
        <el-form-item label="优惠金额（元）"><el-input-number v-model="createForm.amount" :min="0.01" :precision="2" :step="1" /></el-form-item>
        <el-form-item label="使用门槛（元）"><el-input-number v-model="createForm.minimumAmount" :min="0" :precision="2" :step="10" /></el-form-item>
        <el-form-item label="发行数量"><el-input-number v-model="createForm.totalStock" :min="1" :step="10" /></el-form-item>
        <el-form-item label="创建后启用"><el-switch v-model="createForm.enabled" inline-prompt active-text="启用" inactive-text="停用" /></el-form-item>
        <el-form-item label="开始时间"><el-date-picker v-model="createForm.startsAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
        <el-form-item label="结束时间"><el-date-picker v-model="createForm.endsAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="createOpen = false">取消</el-button><el-button type="primary" :loading="saving" @click="createCoupon">创建优惠券</el-button></template>
    </el-dialog>

    <el-dialog v-model="issueOpen" title="按手机号发放" width="500px">
      <div v-if="selectedCoupon" class="issue-coupon-preview">
        <span>¥<strong>{{ Number(selectedCoupon.amount).toFixed(2) }}</strong></span>
        <div><strong>{{ selectedCoupon.name }}</strong><small>{{ conditionText(selectedCoupon) }} · 剩余 {{ selectedCoupon.remainingStock }} 张</small></div>
      </div>
      <el-form label-position="top">
        <el-form-item label="用户手机号">
          <el-input v-model="issueForm.mobile" maxlength="11" inputmode="numeric" placeholder="输入已注册用户的 11 位手机号" @keyup.enter="issueCoupon" />
        </el-form-item>
      </el-form>
      <p class="issue-hint">发放成功后会立即进入该用户的优惠券账户；到达有效期且订单满足门槛时可直接使用。</p>
      <template #footer><el-button @click="issueOpen = false">取消</el-button><el-button type="primary" :loading="issuing" @click="issueCoupon">确认发放</el-button></template>
    </el-dialog>
  </div>
</template>

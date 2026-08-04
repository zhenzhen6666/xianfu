<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Download, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '../api/admin'
import PageToolbar from '../components/PageToolbar.vue'
import type { SearchKeywordStat } from '../types/api'
import { dateTime } from '../utils/format'

type ExportType = 'orders' | 'products' | 'users' | 'coupons'

const exportItems: Array<{ type: ExportType; title: string; description: string }> = [
  { type: 'orders', title: '订单数据', description: '订单金额、状态、收货信息和履约时间' },
  { type: 'products', title: '商品数据', description: '分类、价格、库存、销量和上下架状态' },
  { type: 'users', title: '用户数据', description: '会员等级、积分、消费和账号状态' },
  { type: 'coupons', title: '优惠券数据', description: '发行量、领取量、剩余量和有效期' },
]

const days = ref(30)
const loading = ref(false)
const exporting = ref<ExportType | ''>('')
const stats = ref<SearchKeywordStat[]>([])
const totalSearches = computed(() => stats.value.reduce((sum, item) => sum + item.searchCount, 0))
const noResultSearches = computed(() => stats.value.reduce((sum, item) => sum + item.noResultCount, 0))

async function loadStats() {
  loading.value = true
  try { stats.value = await adminApi.searchKeywordStats(days.value) }
  finally { loading.value = false }
}

async function exportCsv(type: ExportType) {
  exporting.value = type
  try {
    const file = await adminApi.exportData(type)
    const url = URL.createObjectURL(file.blob)
    const link = document.createElement('a')
    link.href = url
    link.download = file.filename
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
    ElMessage.success('CSV 文件已生成')
  } finally { exporting.value = '' }
}

onMounted(loadStats)
</script>

<template>
  <div class="data-page">
    <PageToolbar title="数据中心" description="导出核心经营数据，查看用户真实搜索需求和缺货机会">
      <el-button :icon="Refresh" :loading="loading" @click="loadStats">刷新统计</el-button>
    </PageToolbar>

    <section class="export-grid">
      <article v-for="item in exportItems" :key="item.type" class="export-card">
        <div class="export-icon"><el-icon><Download /></el-icon></div>
        <div class="export-copy"><strong>{{ item.title }}</strong><span>{{ item.description }}</span></div>
        <el-button type="primary" plain :loading="exporting === item.type" @click="exportCsv(item.type)">导出 CSV</el-button>
      </article>
    </section>

    <article class="content-card keyword-card" v-loading="loading">
      <header class="keyword-head">
        <div><h2>搜索关键词统计</h2><p>无结果关键词可以直接用于选品和补货决策</p></div>
        <el-select v-model="days" style="width: 130px" @change="loadStats">
          <el-option label="近 7 天" :value="7" /><el-option label="近 30 天" :value="30" />
          <el-option label="近 90 天" :value="90" /><el-option label="近 365 天" :value="365" />
        </el-select>
      </header>
      <div class="summary-row">
        <div><span>搜索总次数</span><strong>{{ totalSearches }}</strong></div>
        <div><span>关键词数量</span><strong>{{ stats.length }}</strong></div>
        <div><span>无结果搜索</span><strong class="danger">{{ noResultSearches }}</strong></div>
      </div>
      <el-table :data="stats" empty-text="所选周期暂无搜索记录">
        <el-table-column label="关键词" min-width="180">
          <template #default="{ row }"><span class="keyword"><el-icon><Search /></el-icon>{{ row.keyword }}</span></template>
        </el-table-column>
        <el-table-column prop="searchCount" label="搜索次数" width="120" sortable />
        <el-table-column prop="noResultCount" label="无结果次数" width="130" sortable>
          <template #default="{ row }"><el-tag :type="row.noResultCount ? 'danger' : 'success'">{{ row.noResultCount }}</el-tag></template>
        </el-table-column>
        <el-table-column label="无结果率" width="190" sortable :sort-method="(a: SearchKeywordStat, b: SearchKeywordStat) => a.noResultRate - b.noResultRate">
          <template #default="{ row }"><el-progress :percentage="Math.round(row.noResultRate * 100)" :status="row.noResultRate >= 0.5 ? 'exception' : undefined" /></template>
        </el-table-column>
        <el-table-column prop="maxResultCount" label="最多匹配商品" width="140" />
        <el-table-column label="最近搜索" width="180"><template #default="{ row }">{{ dateTime(row.lastSearchedAt) }}</template></el-table-column>
      </el-table>
    </article>
  </div>
</template>

<style scoped>
.data-page{display:flex;flex-direction:column;gap:22px}.export-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}.export-card{display:flex;align-items:center;gap:16px;padding:22px;border:1px solid #e5ebe7;border-radius:20px;background:#fff;box-shadow:0 8px 30px rgba(28,57,42,.04)}.export-icon{display:flex;align-items:center;justify-content:center;width:48px;height:48px;border-radius:15px;background:#eaf7ef;color:#168a51;font-size:22px}.export-copy{display:flex;flex:1;flex-direction:column;gap:6px}.export-copy strong{color:#173126;font-size:16px}.export-copy span,.keyword-head p{color:#829087;font-size:13px}.keyword-card{padding:0;overflow:hidden}.keyword-head{display:flex;align-items:center;justify-content:space-between;padding:24px 26px;border-bottom:1px solid #edf1ee}.keyword-head h2{margin:0;color:#173126;font-size:19px}.keyword-head p{margin:7px 0 0}.summary-row{display:grid;grid-template-columns:repeat(3,1fr);gap:1px;background:#edf1ee;border-bottom:1px solid #edf1ee}.summary-row>div{display:flex;flex-direction:column;gap:8px;padding:18px 26px;background:#f9fbfa}.summary-row span{color:#829087;font-size:13px}.summary-row strong{color:#173126;font-size:24px}.summary-row .danger{color:#e45735}.keyword{display:inline-flex;align-items:center;gap:7px;font-weight:700;color:#294b3b}.el-table{padding:0 12px 14px}@media(max-width:900px){.export-grid{grid-template-columns:1fr}.export-card{align-items:flex-start;flex-wrap:wrap}.export-card .el-button{margin-left:64px}.summary-row{grid-template-columns:1fr}.keyword-head{align-items:flex-start;gap:16px;flex-direction:column}}
</style>

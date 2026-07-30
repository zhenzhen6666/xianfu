<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../api/admin'
import PageToolbar from '../components/PageToolbar.vue'
import StatusTag from '../components/StatusTag.vue'
import type { Category, Product, ProductPayload, ProductSku } from '../types/api'
import { dateTime, money } from '../utils/format'

const loading = ref(false)
const saving = ref(false)
const dialogOpen = ref(false)
const editingCode = ref('')
const status = ref('ALL')
const keyword = ref('')
const products = ref<Product[]>([])
const categories = ref<Category[]>([])
const skuOpen = ref(false)
const skuSaving = ref(false)
const skuProduct = ref<Product | null>(null)
const skus = ref<ProductSku[]>([])

const blank = (): ProductPayload => ({
  categoryCode: '', name: '', subtitle: '', description: '', spec: '', imageUrl: '',
  originalPrice: 0, salePrice: 0, memberPrice: 0, stock: 0, badge: '', status: 'ON_SALE', sortOrder: 0,
})
const form = reactive<ProductPayload>(blank())

const filtered = computed(() => {
  const needle = keyword.value.trim().toLowerCase()
  return products.value.filter((item) => !needle || [item.name, item.productCode, item.categoryName, item.spec].some((value) => value?.toLowerCase().includes(needle)))
})

async function load() {
  loading.value = true
  try {
    const [items, categoryList] = await Promise.all([adminApi.products(status.value), adminApi.categories()])
    products.value = items
    categories.value = categoryList.filter((item) => item.enabled)
  } finally { loading.value = false }
}

function openCreate() {
  editingCode.value = ''
  Object.assign(form, blank(), { categoryCode: categories.value.find((item) => item.parentCode)?.code || categories.value[0]?.code || '' })
  dialogOpen.value = true
}

function openEdit(row: Product) {
  editingCode.value = row.productCode
  Object.assign(form, {
    categoryCode: row.categoryCode, name: row.name, subtitle: row.subtitle || '', description: row.description || '',
    spec: row.spec, imageUrl: row.imageUrl || '', originalPrice: Number(row.originalPrice), salePrice: Number(row.salePrice),
    memberPrice: Number(row.memberPrice || 0), stock: row.stock, badge: row.badge || '', status: row.status, sortOrder: row.sortOrder,
  })
  dialogOpen.value = true
}

async function save() {
  if (!form.categoryCode || !form.name.trim() || !form.spec.trim()) return ElMessage.warning('请填写分类、商品名和规格')
  saving.value = true
  try {
    if (editingCode.value) await adminApi.updateProduct(editingCode.value, { ...form })
    else await adminApi.createProduct({ ...form })
    ElMessage.success(editingCode.value ? '商品已更新' : '商品已创建')
    dialogOpen.value = false
    await load()
  } finally { saving.value = false }
}

async function toggle(row: Product) {
  const payload: ProductPayload = {
    categoryCode: row.categoryCode, name: row.name, subtitle: row.subtitle || '', description: row.description || '', spec: row.spec,
    imageUrl: row.imageUrl || '', originalPrice: Number(row.originalPrice), salePrice: Number(row.salePrice),
    memberPrice: Number(row.memberPrice || 0), stock: row.stock, badge: row.badge || '',
    status: row.status === 'ON_SALE' ? 'OFF_SHELF' : 'ON_SALE', sortOrder: row.sortOrder,
  }
  await adminApi.updateProduct(row.productCode, payload)
  ElMessage.success(payload.status === 'ON_SALE' ? '商品已上架' : '商品已下架')
  await load()
}

async function removeProduct(row: Product) {
  try {
    await ElMessageBox.confirm(
      `确定删除“${row.name}”吗？已有订单记录的商品将无法删除，只能下架。`,
      '删除商品',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
    )
  } catch { return }
  await adminApi.deleteProduct(row.productCode)
  ElMessage.success('商品已删除')
  await load()
}

async function openSkus(row: Product) {
  skuProduct.value = row
  skus.value = (await adminApi.productSkus(row.productCode)).filter(item => item.enabled !== false)
    .map(item => ({ ...item, originalPrice: Number(item.originalPrice), salePrice: Number(item.salePrice), memberPrice: Number(item.memberPrice || 0), stock: Number(item.stock) }))
  skuOpen.value = true
}
function addSku() {
  const row = skuProduct.value
  skus.value.push({ specName: '', originalPrice: Number(row?.originalPrice || 0), salePrice: Number(row?.salePrice || 0), memberPrice: Number(row?.memberPrice || 0), stock: 0 })
}
function removeSku(index: number) {
  if (skus.value.length <= 1) return ElMessage.warning('商品至少需要一个规格')
  skus.value.splice(index, 1)
}
async function saveSkus() {
  if (!skuProduct.value || skus.value.some(item => !item.specName.trim() || item.salePrice < 0 || item.originalPrice < item.salePrice || item.stock < 0)) return ElMessage.warning('请检查规格名称、价格和库存')
  skuSaving.value = true
  try {
    await adminApi.updateProductSkus(skuProduct.value.productCode, skus.value)
    ElMessage.success('商品规格已更新'); skuOpen.value = false; await load()
  } finally { skuSaving.value = false }
}

onMounted(load)
</script>

<template>
  <div>
    <PageToolbar title="商品列表" description="维护售价、会员价、库存和小程序展示状态">
      <el-button :icon="Refresh" @click="load">刷新</el-button><el-button type="primary" :icon="Plus" @click="openCreate">新增商品</el-button>
    </PageToolbar>
    <div class="filter-bar">
      <el-input v-model="keyword" :prefix-icon="Search" clearable placeholder="搜索商品名称、编码或分类" class="search-input" />
      <el-segmented v-model="status" :options="[{ label: '全部', value: 'ALL' }, { label: '销售中', value: 'ON_SALE' }, { label: '已下架', value: 'OFF_SHELF' }]" @change="load" />
      <span class="result-count">共 {{ filtered.length }} 件商品</span>
    </div>
    <article class="content-card table-card" v-loading="loading">
      <el-table :data="filtered" class="clean-table">
        <el-table-column label="商品" min-width="280">
          <template #default="{ row }"><div class="product-cell"><div class="product-image"><img v-if="row.imageUrl" :src="row.imageUrl" alt="" /><span v-else>{{ row.name.slice(0, 1) }}</span></div><div><strong>{{ row.name }}</strong><small>{{ row.productCode }} · {{ row.spec }}</small></div></div></template>
        </el-table-column>
        <el-table-column prop="categoryName" label="分类" width="125" />
        <el-table-column label="价格" width="150"><template #default="{ row }"><div class="two-line"><strong>{{ money(row.salePrice) }}</strong><small>会员 {{ money(row.memberPrice) }}</small></div></template></el-table-column>
        <el-table-column label="库存 / 销量" width="140"><template #default="{ row }"><div class="two-line"><strong :class="{ 'text-danger': row.stock < 30 }">库存 {{ row.stock }}</strong><small>销量 {{ row.sales }}</small></div></template></el-table-column>
        <el-table-column label="状态" width="105"><template #default="{ row }"><StatusTag :status="row.status" /></template></el-table-column>
        <el-table-column label="更新时间" width="165"><template #default="{ row }">{{ dateTime(row.updatedAt) }}</template></el-table-column>
        <el-table-column label="操作" width="270" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openEdit(row)">编辑</el-button><el-button link type="success" @click="openSkus(row)">多规格</el-button><el-button link :type="row.status === 'ON_SALE' ? 'warning' : 'success'" @click="toggle(row)">{{ row.status === 'ON_SALE' ? '下架' : '上架' }}</el-button><el-button link type="danger" @click="removeProduct(row)">删除</el-button></template></el-table-column>
      </el-table>
    </article>

    <el-dialog v-model="dialogOpen" :title="editingCode ? '编辑商品' : '新增商品'" width="720px" destroy-on-close>
      <el-form label-position="top" class="form-grid">
        <el-form-item label="商品名称" class="span-2"><el-input v-model="form.name" maxlength="120" show-word-limit /></el-form-item>
        <el-form-item label="商品分类"><el-select v-model="form.categoryCode" filterable><el-option v-for="item in categories" :key="item.code" :label="item.name" :value="item.code" /></el-select></el-form-item>
        <el-form-item label="规格"><el-input v-model="form.spec" placeholder="例：500g/份" /></el-form-item>
        <el-form-item label="原价"><el-input-number v-model="form.originalPrice" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="销售价"><el-input-number v-model="form.salePrice" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="会员价"><el-input-number v-model="form.memberPrice" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="库存"><el-input-number v-model="form.stock" :min="0" /></el-form-item>
        <el-form-item label="角标"><el-input v-model="form.badge" placeholder="例：今日特价" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="form.status"><el-option label="销售中" value="ON_SALE" /><el-option label="已下架" value="OFF_SHELF" /></el-select></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" /></el-form-item>
        <el-form-item label="副标题" class="span-2"><el-input v-model="form.subtitle" maxlength="255" /></el-form-item>
        <el-form-item label="商品图片 URL" class="span-2"><el-input v-model="form.imageUrl" /></el-form-item>
        <el-form-item label="商品描述" class="span-2"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogOpen = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存商品</el-button></template>
    </el-dialog>
    <el-dialog v-model="skuOpen" :title="`${skuProduct?.name || ''} · 多规格设置`" width="900px">
      <div class="dialog-notice">每个规格可独立设置售价、会员价和库存。已被订单引用的旧规格会保留记录但停止展示。</div>
      <el-table :data="skus" border>
        <el-table-column label="规格名称" min-width="170"><template #default="{row}"><el-input v-model="row.specName" placeholder="例：500g/盒"/></template></el-table-column>
        <el-table-column label="原价" width="145"><template #default="{row}"><el-input-number v-model="row.originalPrice" :min="0" :precision="2" controls-position="right"/></template></el-table-column>
        <el-table-column label="售价" width="145"><template #default="{row}"><el-input-number v-model="row.salePrice" :min="0" :precision="2" controls-position="right"/></template></el-table-column>
        <el-table-column label="会员价" width="145"><template #default="{row}"><el-input-number v-model="row.memberPrice" :min="0" :precision="2" controls-position="right"/></template></el-table-column>
        <el-table-column label="库存" width="130"><template #default="{row}"><el-input-number v-model="row.stock" :min="0" controls-position="right"/></template></el-table-column>
        <el-table-column label="操作" width="75"><template #default="{$index}"><el-button link type="danger" @click="removeSku($index)">删除</el-button></template></el-table-column>
      </el-table>
      <el-button style="margin-top:14px" :icon="Plus" @click="addSku">添加规格</el-button>
      <template #footer><el-button @click="skuOpen=false">取消</el-button><el-button type="primary" :loading="skuSaving" @click="saveSkus">保存规格</el-button></template>
    </el-dialog>
  </div>
</template>

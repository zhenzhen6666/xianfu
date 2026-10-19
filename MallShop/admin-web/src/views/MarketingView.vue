<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Delete, Edit, Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../api/admin'
import PageToolbar from '../components/PageToolbar.vue'
import ImageUpload from '../components/ImageUpload.vue'
import type { Activity, ActivityProduct, ActivityProductPayload, HomeModule, Product } from '../types/api'
import { dateTime, money } from '../utils/format'

interface QuickItem {
  icon: string
  name: string
  theme: string
  type: string
}

interface ZoneItem {
  kicker: string
  title: string
  linkText: string
  image: string
  theme: string
  type: string
}

const activeTab = ref('modules')
const loading = ref(false)
const saving = ref(false)
const uploadingImages = ref(0)

function trackUpload(uploading: boolean) {
  uploadingImages.value += uploading ? 1 : -1
}
const dialogOpen = ref(false)
const dialogType = ref<'module' | 'activity'>('module')
const modules = ref<HomeModule[]>([])
const activities = ref<Activity[]>([])
const productDialogOpen = ref(false)
const productSaving = ref(false)
const productActivity = ref<Activity | null>(null)
const activityProducts = ref<ActivityProduct[]>([])
const allProducts = ref<Product[]>([])
const editingProductCode = ref('')
const productForm = reactive<ActivityProductPayload & { productCode: string }>({
  productCode: '',
  activityPrice: undefined,
  activityStock: undefined,
  perUserLimit: 1,
  sortOrder: 0,
})
const moduleForm = reactive({
  id: 0,
  moduleType: 'BANNER',
  title: '',
  contentJson: '{\n  "items": []\n}',
  sortOrder: 0,
  enabled: true,
})
const activityForm = reactive<Activity>({
  code: '',
  name: '',
  activityType: 'DISCOUNT',
  description: '',
  rulesJson: '{\n  "discount": 0.9\n}',
  startsAt: '',
  endsAt: '',
  enabled: true,
})
const quickItems = ref<QuickItem[]>([])
const zoneItems = ref<ZoneItem[]>([])
const noticeForm = reactive({ icon: '闪', primaryText: '城区 3 公里配送', secondaryText: '30 分钟送货上门' })

const quickDefaults: QuickItem[] = [
  { icon: '¥0.01', name: '一分专享', theme: 'mint', type: 'penny' },
  { icon: '券', name: '新人福利', theme: 'orange', type: 'new' },
  { icon: '净', name: '绿捷净菜', theme: 'pink', type: 'clean' },
  { icon: '砸', name: '欢乐金蛋', theme: 'yellow', type: 'egg' },
]
const zoneDefaults: ZoneItem[] = [
  { kicker: '省心搭配', title: '在家做饭', linkText: '去看看 →', image: '/assets/products/mushroom.jpg', theme: 'pink', type: 'clean' },
  { kicker: '本周爆款', title: '9.9 特惠', linkText: '限时抢 →', image: '/assets/products/orange.jpg', theme: 'yellow', type: 'deal' },
]
const themeOptions = [
  { label: '薄荷绿', value: 'mint' },
  { label: '暖橙色', value: 'orange' },
  { label: '樱花粉', value: 'pink' },
  { label: '奶油黄', value: 'yellow' },
]

async function load() {
  loading.value = true
  try {
    ;[modules.value, activities.value] = await Promise.all([adminApi.homeModules(), adminApi.activities()])
  } finally { loading.value = false }
}

function validJson(value: string) {
  try { JSON.parse(value); return true } catch { ElMessage.warning('JSON 配置格式不正确'); return false }
}

function parseContent(value: string) {
  try { return JSON.parse(value) as Record<string, any> } catch { return {} }
}

function initializeModuleEditor(type = moduleForm.moduleType) {
  if (type === 'QUICK_ENTRY' && !quickItems.value.length) {
    quickItems.value = quickDefaults.map(item => ({ ...item }))
  }
  if (type === 'ACTIVITY_ZONE' && !zoneItems.value.length) {
    zoneItems.value = zoneDefaults.map(item => ({ ...item }))
  }
}

function newModule() {
  Object.assign(moduleForm, {
    id: 0,
    moduleType: 'BANNER',
    title: '',
    contentJson: '{\n  "items": []\n}',
    sortOrder: modules.value.length + 1,
    enabled: true,
  })
  quickItems.value = []
  zoneItems.value = []
  dialogType.value = 'module'
  dialogOpen.value = true
}

function editModule(row: HomeModule) {
  Object.assign(moduleForm, row)
  const content = parseContent(row.contentJson)
  if (row.moduleType === 'QUICK_ENTRY') {
    const items = Array.isArray(content.items) ? content.items : null
    quickItems.value = items
      ? items.map((item: Partial<QuickItem>, index: number) => ({
          ...quickDefaults[index % quickDefaults.length],
          ...item,
        }))
      : quickDefaults.map(item => ({ ...item }))
  } else {
    quickItems.value = []
  }
  if (row.moduleType === 'ACTIVITY_ZONE') {
    const items = Array.isArray(content.items) ? content.items : null
    zoneItems.value = items
      ? items.map((item: Partial<ZoneItem>, index: number) => ({
          ...zoneDefaults[index % zoneDefaults.length],
          ...item,
        }))
      : zoneDefaults.map(item => ({ ...item }))
  } else {
    zoneItems.value = []
  }
  if (row.moduleType === 'ANNOUNCEMENT') {
    Object.assign(noticeForm, {
      icon: content.icon || '闪',
      primaryText: content.primaryText || content.text || '城区 3 公里配送',
      secondaryText: content.secondaryText || '30 分钟送货上门',
    })
  }
  dialogType.value = 'module'
  dialogOpen.value = true
}

function editActivity(row: Activity) {
  Object.assign(activityForm, row)
  dialogType.value = 'activity'
  dialogOpen.value = true
}

function localDateTime(date: Date) {
  const offset = date.getTimezoneOffset() * 60000
  return new Date(date.getTime() - offset).toISOString().slice(0, 19)
}

function newActivity() {
  const now = new Date()
  const end = new Date(now)
  end.setMonth(end.getMonth() + 1)
  Object.assign(activityForm, {
    code: '',
    name: '',
    activityType: 'DISCOUNT',
    description: '',
    rulesJson: '{\n  "discount": 0.9\n}',
    startsAt: localDateTime(now),
    endsAt: localDateTime(end),
    enabled: true,
  })
  dialogType.value = 'activity'
  dialogOpen.value = true
}

function activityPayload(row: Activity) {
  return {
    name: row.name,
    activityType: row.activityType,
    description: row.description,
    rulesJson: row.rulesJson,
    startsAt: row.startsAt,
    endsAt: row.endsAt,
    enabled: row.enabled,
  }
}

async function toggleActivity(row: Activity) {
  await adminApi.updateActivity(row.code, activityPayload({ ...row, enabled: !row.enabled }))
  ElMessage.success(row.enabled ? '营销活动已停用' : '营销活动已启用')
  activities.value = await adminApi.activities()
}

async function deleteActivity(row: Activity) {
  try {
    await ElMessageBox.confirm(
      `确定删除“${row.name}”吗？已有用户参与记录的活动不能删除，只能停用。`,
      '删除营销活动',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
    )
  } catch { return }
  await adminApi.deleteActivity(row.code)
  ElMessage.success('营销活动已删除')
  activities.value = await adminApi.activities()
}

async function openActivityProducts(row: Activity) {
  productActivity.value = row
  ;[activityProducts.value, allProducts.value] = await Promise.all([
    adminApi.activityProducts(row.code),
    adminApi.products('ALL'),
  ])
  productDialogOpen.value = true
}

function availableProducts() {
  const linked = new Set(activityProducts.value.map(item => item.productCode))
  return allProducts.value.filter(item => item.productCode === editingProductCode.value || !linked.has(item.productCode))
}

function resetProductForm() {
  editingProductCode.value = ''
  const candidate = availableProducts()[0]
  Object.assign(productForm, {
    productCode: candidate?.productCode || '',
    activityPrice: candidate ? Number(candidate.salePrice) : undefined,
    activityStock: candidate?.stock || undefined,
    perUserLimit: 1,
    sortOrder: activityProducts.value.length + 1,
  })
}

function editActivityProduct(row: ActivityProduct) {
  editingProductCode.value = row.productCode
  Object.assign(productForm, {
    productCode: row.productCode,
    activityPrice: row.activityPrice == null ? Number(row.salePrice) : Number(row.activityPrice),
    activityStock: row.activityStock == null ? row.productStock : row.activityStock,
    perUserLimit: row.perUserLimit == null ? 1 : row.perUserLimit,
    sortOrder: row.sortOrder,
  })
}

function selectProduct(code: string) {
  if (editingProductCode.value) return
  const product = allProducts.value.find(item => item.productCode === code)
  if (!product) return
  productForm.activityPrice = Number(product.salePrice)
  productForm.activityStock = product.stock
}

async function saveActivityProduct() {
  if (!productActivity.value || !productForm.productCode) return ElMessage.warning('请选择活动商品')
  if (productForm.activityPrice == null || productForm.activityPrice < 0
    || productForm.activityStock == null || productForm.activityStock < 0
    || productForm.perUserLimit == null || productForm.perUserLimit < 1) {
    return ElMessage.warning('请检查活动价、活动库存和每人限购数量')
  }
  productSaving.value = true
  try {
    await adminApi.saveActivityProduct(
      productActivity.value.code,
      productForm.productCode,
      {
        activityPrice: productForm.activityPrice,
        activityStock: productForm.activityStock,
        perUserLimit: productForm.perUserLimit,
        sortOrder: productForm.sortOrder,
      },
    )
    ElMessage.success(editingProductCode.value ? '活动商品已更新' : '活动商品已添加')
    activityProducts.value = await adminApi.activityProducts(productActivity.value.code)
    resetProductForm()
  } finally { productSaving.value = false }
}

async function removeActivityProduct(row: ActivityProduct) {
  if (!productActivity.value) return
  try {
    await ElMessageBox.confirm(
      `确定从“${productActivity.value.name}”中移除“${row.name}”吗？`,
      '移除活动商品',
      { type: 'warning', confirmButtonText: '确认移除', cancelButtonText: '取消' },
    )
  } catch { return }
  await adminApi.deleteActivityProduct(productActivity.value.code, row.productCode)
  ElMessage.success('活动商品已移除')
  activityProducts.value = await adminApi.activityProducts(productActivity.value.code)
  resetProductForm()
}

function moduleLabel(type: string) {
  return ({
    QUICK_ENTRY: '快捷入口',
    ACTIVITY_ZONE: '活动卡片',
    ANNOUNCEMENT: '配送提示条',
    BANNER: '首页横幅',
    PRODUCT: '商品展示区',
  } as Record<string, string>)[type] || type
}

function modulePreview(item: HomeModule) {
  const content = parseContent(item.contentJson)
  if (item.moduleType === 'QUICK_ENTRY') {
    return (content.items || []).map((entry: QuickItem) => entry.name).filter(Boolean).join('、') || '四个首页入口'
  }
  if (item.moduleType === 'ACTIVITY_ZONE') {
    return (content.items || []).map((entry: ZoneItem) => entry.title).filter(Boolean).join('、') || '两张活动卡片'
  }
  if (item.moduleType === 'ANNOUNCEMENT') {
    return [content.primaryText || content.text, content.secondaryText].filter(Boolean).join(' · ') || '配送服务提示'
  }
  return item.title || '首页展示模块'
}

function buildModuleJson() {
  if (moduleForm.moduleType === 'QUICK_ENTRY') {
    if (quickItems.value.some(item => !item.icon.trim() || !item.name.trim() || !item.type.trim())) {
      ElMessage.warning('请完整填写每个快捷入口')
      return null
    }
    return JSON.stringify({ items: quickItems.value }, null, 2)
  }
  if (moduleForm.moduleType === 'ACTIVITY_ZONE') {
    if (zoneItems.value.some(item => !item.kicker.trim() || !item.title.trim() || !item.linkText.trim() || !item.image.trim() || !item.type.trim())) {
      ElMessage.warning('请完整填写每张活动卡片')
      return null
    }
    return JSON.stringify({ items: zoneItems.value }, null, 2)
  }
  if (moduleForm.moduleType === 'ANNOUNCEMENT') {
    if (!noticeForm.icon.trim() || !noticeForm.primaryText.trim() || !noticeForm.secondaryText.trim()) {
      ElMessage.warning('请完整填写配送提示')
      return null
    }
    return JSON.stringify({ ...noticeForm }, null, 2)
  }
  return validJson(moduleForm.contentJson) ? moduleForm.contentJson : null
}

function addQuickItem() {
  const fallback = quickDefaults[quickItems.value.length % quickDefaults.length]
  quickItems.value.push({ ...fallback, icon: '新', name: '新入口', type: '' })
}

function addZoneItem() {
  const fallback = zoneDefaults[zoneItems.value.length % zoneDefaults.length]
  zoneItems.value.push({ ...fallback, kicker: '活动推荐', title: '新活动', type: '' })
}

function removeQuickItem(index: number) {
  quickItems.value.splice(index, 1)
}

function removeZoneItem(index: number) {
  zoneItems.value.splice(index, 1)
}

function moveItem<T>(items: T[], index: number, offset: -1 | 1) {
  const target = index + offset
  if (target < 0 || target >= items.length) return
  const [item] = items.splice(index, 1)
  items.splice(target, 0, item)
}

async function deleteModule(item: HomeModule) {
  try {
    await ElMessageBox.confirm(
      `确定删除“${item.title || moduleLabel(item.moduleType)}”吗？删除后小程序对应区域将不再展示。`,
      '删除首页内容',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
    )
  } catch { return }
  await adminApi.deleteHomeModule(item.id)
  ElMessage.success('首页内容已删除')
  await load()
}

async function save() {
  if (uploadingImages.value) return ElMessage.warning('请等待图片上传完成')
  saving.value = true
  try {
    if (dialogType.value === 'module') {
      if (!moduleForm.moduleType) return
      const contentJson = buildModuleJson()
      if (!contentJson) return
      const payload = {
        moduleType: moduleForm.moduleType,
        title: moduleForm.title,
        contentJson,
        sortOrder: moduleForm.sortOrder,
        enabled: moduleForm.enabled,
      }
      modules.value = moduleForm.id
        ? await adminApi.updateHomeModule(moduleForm.id, payload)
        : await adminApi.createHomeModule(payload)
    } else {
      if (!activityForm.name || !activityForm.startsAt || !activityForm.endsAt || !validJson(activityForm.rulesJson)) return
      const payload = {
        name: activityForm.name,
        activityType: activityForm.activityType,
        description: activityForm.description,
        rulesJson: activityForm.rulesJson,
        startsAt: activityForm.startsAt,
        endsAt: activityForm.endsAt,
        enabled: activityForm.enabled,
      }
      if (activityForm.code) await adminApi.updateActivity(activityForm.code, payload)
      else await adminApi.createActivity(payload)
      activities.value = await adminApi.activities()
    }
    ElMessage.success('营销配置已保存，小程序刷新后生效')
    dialogOpen.value = false
  } finally { saving.value = false }
}

onMounted(load)
</script>

<template>
  <div>
    <PageToolbar title="营销与首页装修" description="可视化修改小程序首页入口、活动卡片和配送提示">
      <el-button :icon="Refresh" @click="load">刷新</el-button>
      <el-button v-if="activeTab === 'modules'" type="primary" :icon="Plus" @click="newModule">新增组件</el-button>
      <el-button v-else type="primary" :icon="Plus" @click="newActivity">新增活动</el-button>
    </PageToolbar>

    <article class="content-card marketing-card" v-loading="loading">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="首页展示内容" name="modules">
          <div class="home-config-tip">截图中的配送提示、快捷入口和活动卡片都在这里管理；支持新增、编辑、排序、隐藏和删除，保存后用户下拉刷新小程序首页即可看到。</div>
          <div class="module-grid">
            <div v-for="item in modules" :key="item.id" class="module-item">
              <div class="module-icon">{{ item.moduleType.slice(0, 1) }}</div>
              <div class="module-main">
                <span>{{ moduleLabel(item.moduleType) }}</span>
                <h3>{{ item.title || moduleLabel(item.moduleType) }}</h3>
                <p>{{ modulePreview(item) }} · 更新于 {{ dateTime(item.updatedAt) }}</p>
              </div>
              <el-tag :type="item.enabled ? 'success' : 'info'" round>{{ item.enabled ? '展示中' : '已隐藏' }}</el-tag>
              <div class="module-actions">
                <el-button circle :icon="Edit" title="编辑" @click="editModule(item)" />
                <el-button circle :icon="Delete" type="danger" plain title="删除" @click="deleteModule(item)" />
              </div>
            </div>
          </div>
          <el-empty v-if="!modules.length" description="暂无首页组件" />
        </el-tab-pane>

        <el-tab-pane label="营销活动" name="activities">
          <el-table :data="activities" class="clean-table">
            <el-table-column label="活动" min-width="240"><template #default="{ row }"><div class="two-line"><strong>{{ row.name }}</strong><small>{{ row.code }} · {{ row.activityType }}</small></div></template></el-table-column>
            <el-table-column prop="description" label="活动说明" min-width="250" show-overflow-tooltip />
            <el-table-column label="活动时间" min-width="260"><template #default="{ row }"><div class="two-line"><span>{{ dateTime(row.startsAt) }}</span><small>至 {{ dateTime(row.endsAt) }}</small></div></template></el-table-column>
            <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'" round>{{ row.enabled ? '已启用' : '已停用' }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="265"><template #default="{ row }"><el-button link type="success" @click="openActivityProducts(row)">活动商品</el-button><el-button link type="primary" @click="editActivity(row)">编辑</el-button><el-button link :type="row.enabled ? 'warning' : 'success'" @click="toggleActivity(row)">{{ row.enabled ? '停用' : '启用' }}</el-button><el-button link type="danger" @click="deleteActivity(row)">删除</el-button></template></el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </article>

    <el-dialog v-model="dialogOpen" :title="dialogType === 'module' ? (moduleForm.id ? `编辑${moduleLabel(moduleForm.moduleType)}` : '新增首页组件') : (activityForm.code ? '编辑营销活动' : '新增营销活动')" width="760px" destroy-on-close :close-on-click-modal="!uploadingImages && !saving" :close-on-press-escape="!uploadingImages && !saving" :show-close="!uploadingImages && !saving">
      <el-form v-if="dialogType === 'module'" label-position="top" :disabled="saving">
        <div class="form-grid">
          <el-form-item label="组件类型">
            <el-select v-model="moduleForm.moduleType" :disabled="Boolean(moduleForm.id) || Boolean(uploadingImages)" @change="initializeModuleEditor">
              <el-option label="快捷入口" value="QUICK_ENTRY" />
              <el-option label="活动卡片" value="ACTIVITY_ZONE" />
              <el-option label="配送提示条" value="ANNOUNCEMENT" />
              <el-option label="首页横幅（高级配置）" value="BANNER" />
              <el-option label="商品区（高级配置）" value="PRODUCT" />
            </el-select>
          </el-form-item>
          <el-form-item label="后台识别名称"><el-input v-model="moduleForm.title" placeholder="仅用于后台识别" /></el-form-item>
          <el-form-item label="展示排序"><el-input-number v-model="moduleForm.sortOrder" :min="0" /></el-form-item>
          <el-form-item label="展示状态"><el-switch v-model="moduleForm.enabled" inline-prompt active-text="展示" inactive-text="隐藏" /></el-form-item>
        </div>

        <template v-if="moduleForm.moduleType === 'QUICK_ENTRY'">
          <div class="visual-editor-head"><el-divider content-position="left">快捷入口（{{ quickItems.length }} 个）</el-divider><el-button type="primary" plain :icon="Plus" @click="addQuickItem">新增入口</el-button></div>
          <el-empty v-if="!quickItems.length" description="暂无快捷入口，可点击新增" :image-size="70" />
          <div class="visual-editor-grid quick-editor-grid">
            <div v-for="(item, index) in quickItems" :key="index" class="visual-item-editor">
              <div class="editor-item-title">
                <span :class="['config-icon-preview', item.theme]">{{ item.icon }}</span><strong>入口 {{ index + 1 }}</strong>
                <div class="editor-item-actions"><el-button link :disabled="index === 0" @click="moveItem(quickItems, index, -1)">上移</el-button><el-button link :disabled="index === quickItems.length - 1" @click="moveItem(quickItems, index, 1)">下移</el-button><el-button link type="danger" @click="removeQuickItem(index)">删除</el-button></div>
              </div>
              <el-form-item label="图标文字"><el-input v-model="item.icon" maxlength="6" placeholder="例如：券" /></el-form-item>
              <el-form-item label="入口名称"><el-input v-model="item.name" maxlength="10" /></el-form-item>
              <el-form-item label="跳转活动"><el-select v-model="item.type" filterable allow-create default-first-option placeholder="选择营销活动"><el-option v-for="activity in activities" :key="activity.code" :label="`${activity.name}（${activity.code}）`" :value="activity.code" /></el-select></el-form-item>
              <el-form-item label="图标颜色"><el-select v-model="item.theme"><el-option v-for="theme in themeOptions" :key="theme.value" :label="theme.label" :value="theme.value" /></el-select></el-form-item>
            </div>
          </div>
        </template>

        <template v-else-if="moduleForm.moduleType === 'ACTIVITY_ZONE'">
          <div class="visual-editor-head"><el-divider content-position="left">活动卡片（{{ zoneItems.length }} 张）</el-divider><el-button type="primary" plain :icon="Plus" :disabled="Boolean(uploadingImages)" @click="addZoneItem">新增卡片</el-button></div>
          <el-empty v-if="!zoneItems.length" description="暂无活动卡片，可点击新增" :image-size="70" />
          <div class="visual-editor-grid">
            <div v-for="(item, index) in zoneItems" :key="index" class="visual-item-editor zone-editor">
              <div class="editor-item-title">
                <span :class="['zone-color-preview', item.theme]" /><strong>活动卡片 {{ index + 1 }}</strong>
                <div class="editor-item-actions"><el-button link :disabled="index === 0 || Boolean(uploadingImages)" @click="moveItem(zoneItems, index, -1)">上移</el-button><el-button link :disabled="index === zoneItems.length - 1 || Boolean(uploadingImages)" @click="moveItem(zoneItems, index, 1)">下移</el-button><el-button link type="danger" :disabled="Boolean(uploadingImages)" @click="removeZoneItem(index)">删除</el-button></div>
              </div>
              <div class="form-grid">
                <el-form-item label="小标题"><el-input v-model="item.kicker" maxlength="12" placeholder="省心搭配" /></el-form-item>
                <el-form-item label="主标题"><el-input v-model="item.title" maxlength="12" placeholder="在家做饭" /></el-form-item>
                <el-form-item label="按钮文字"><el-input v-model="item.linkText" maxlength="12" placeholder="去看看 →" /></el-form-item>
                <el-form-item label="跳转活动"><el-select v-model="item.type" filterable allow-create default-first-option placeholder="选择营销活动"><el-option v-for="activity in activities" :key="activity.code" :label="`${activity.name}（${activity.code}）`" :value="activity.code" /></el-select></el-form-item>
                <el-form-item label="活动图片" class="span-2"><ImageUpload v-model="item.image" :disabled="saving" @uploading="trackUpload" /></el-form-item>
                <el-form-item label="卡片颜色"><el-select v-model="item.theme"><el-option v-for="theme in themeOptions" :key="theme.value" :label="theme.label" :value="theme.value" /></el-select></el-form-item>
              </div>
            </div>
          </div>
        </template>

        <template v-else-if="moduleForm.moduleType === 'ANNOUNCEMENT'">
          <el-divider content-position="left">配送提示条</el-divider>
          <div class="notice-editor-preview">
            <span>{{ noticeForm.icon }}</span><strong>{{ noticeForm.primaryText }}</strong><i>·</i><em>{{ noticeForm.secondaryText }}</em>
          </div>
          <div class="form-grid">
            <el-form-item label="左侧图标文字"><el-input v-model="noticeForm.icon" maxlength="2" placeholder="闪" /></el-form-item>
            <el-form-item label="主要提示"><el-input v-model="noticeForm.primaryText" maxlength="30" placeholder="城区 3 公里配送" /></el-form-item>
            <el-form-item label="补充提示" class="span-2"><el-input v-model="noticeForm.secondaryText" maxlength="40" placeholder="30 分钟送货上门" /></el-form-item>
          </div>
        </template>

        <el-form-item v-else label="内容 JSON">
          <el-input v-model="moduleForm.contentJson" type="textarea" :rows="11" class="code-input" />
        </el-form-item>
      </el-form>

      <el-form v-else label-position="top" class="form-grid">
        <el-form-item v-if="activityForm.code" label="活动编码"><el-input v-model="activityForm.code" disabled /></el-form-item>
        <el-form-item label="活动名称"><el-input v-model="activityForm.name" /></el-form-item>
        <el-form-item label="活动类型"><el-input v-model="activityForm.activityType" /></el-form-item>
        <el-form-item label="启用状态"><el-switch v-model="activityForm.enabled" inline-prompt active-text="启用" inactive-text="停用" /></el-form-item>
        <el-form-item label="开始时间"><el-date-picker v-model="activityForm.startsAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
        <el-form-item label="结束时间"><el-date-picker v-model="activityForm.endsAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
        <el-form-item label="活动说明" class="span-2"><el-input v-model="activityForm.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="活动规则 JSON" class="span-2"><el-input v-model="activityForm.rulesJson" type="textarea" :rows="8" class="code-input" /></el-form-item>
      </el-form>
      <template #footer><el-button :disabled="Boolean(uploadingImages) || saving" @click="dialogOpen = false">取消</el-button><el-button type="primary" :loading="saving" :disabled="Boolean(uploadingImages)" @click="save">保存并发布</el-button></template>
    </el-dialog>

    <el-dialog v-model="productDialogOpen" :title="`${productActivity?.name || ''} · 活动商品`" width="1080px" destroy-on-close @opened="resetProductForm">
      <div class="home-config-tip">这里的商品会直接显示在小程序活动详情页。移除全部商品后，活动页会显示为空，不再自动补充普通商品。</div>
      <el-form class="activity-product-form" label-position="top">
        <el-form-item label="选择商品">
          <el-select v-model="productForm.productCode" filterable :disabled="Boolean(editingProductCode)" placeholder="选择要加入活动的商品" @change="selectProduct">
            <el-option v-for="item in availableProducts()" :key="item.productCode" :label="`${item.name} · ${item.spec}`" :value="item.productCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="活动价"><el-input-number v-model="productForm.activityPrice" :min="0" :precision="2" controls-position="right" /></el-form-item>
        <el-form-item label="剩余活动库存"><el-input-number v-model="productForm.activityStock" :min="0" controls-position="right" /></el-form-item>
        <el-form-item label="每人限购"><el-input-number v-model="productForm.perUserLimit" :min="1" controls-position="right" /></el-form-item>
        <el-form-item label="展示排序"><el-input-number v-model="productForm.sortOrder" :min="0" controls-position="right" /></el-form-item>
        <el-form-item label="操作"><div><el-button v-if="editingProductCode" @click="resetProductForm">取消编辑</el-button><el-button type="primary" :loading="productSaving" :disabled="!productForm.productCode" @click="saveActivityProduct">{{ editingProductCode ? '保存修改' : '添加商品' }}</el-button></div></el-form-item>
      </el-form>
      <el-table :data="activityProducts" border max-height="460">
        <el-table-column label="商品" min-width="230"><template #default="{ row }"><div class="two-line"><strong>{{ row.name }}</strong><small>{{ row.productCode }} · {{ row.spec }}</small></div></template></el-table-column>
        <el-table-column label="原售价" width="110"><template #default="{ row }">{{ money(row.salePrice) }}</template></el-table-column>
        <el-table-column label="活动价" width="110"><template #default="{ row }"><strong class="text-danger">{{ money(row.activityPrice == null ? row.salePrice : row.activityPrice) }}</strong></template></el-table-column>
        <el-table-column label="活动/商品库存" width="145"><template #default="{ row }">{{ row.activityStock ?? row.productStock }} / {{ row.productStock }}</template></el-table-column>
        <el-table-column prop="perUserLimit" label="每人限购" width="100" />
        <el-table-column prop="sortOrder" label="排序" width="75" />
        <el-table-column label="商品状态" width="95"><template #default="{ row }"><el-tag :type="row.productStatus === 'ON_SALE' ? 'success' : 'info'">{{ row.productStatus === 'ON_SALE' ? '销售中' : '已下架' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="120" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="editActivityProduct(row)">编辑</el-button><el-button link type="danger" @click="removeActivityProduct(row)">移除</el-button></template></el-table-column>
      </el-table>
      <el-empty v-if="!activityProducts.length" description="暂无活动商品，请从上方选择商品添加" :image-size="70" />
    </el-dialog>
  </div>
</template>

<style scoped>
.activity-product-form{display:grid;grid-template-columns:2fr repeat(4,1fr) 1.5fr;gap:12px;align-items:end;margin:18px 0}.activity-product-form .el-form-item{margin-bottom:0}.activity-product-form .el-select,.activity-product-form .el-input-number{width:100%}@media(max-width:900px){.activity-product-form{grid-template-columns:1fr 1fr}}
</style>

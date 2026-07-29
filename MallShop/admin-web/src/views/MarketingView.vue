<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Edit, Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '../api/admin'
import PageToolbar from '../components/PageToolbar.vue'
import type { Activity, HomeModule } from '../types/api'
import { dateTime } from '../utils/format'

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
const dialogOpen = ref(false)
const dialogType = ref<'module' | 'activity'>('module')
const modules = ref<HomeModule[]>([])
const activities = ref<Activity[]>([])
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
    const items = Array.isArray(content.items) ? content.items : []
    quickItems.value = quickDefaults.map((fallback, index) => ({ ...fallback, ...(items[index] || {}) }))
  } else {
    quickItems.value = []
  }
  if (row.moduleType === 'ACTIVITY_ZONE') {
    const items = Array.isArray(content.items) ? content.items : []
    zoneItems.value = zoneDefaults.map((fallback, index) => ({ ...fallback, ...(items[index] || {}) }))
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

function moduleLabel(type: string) {
  return ({
    QUICK_ENTRY: '四个快捷入口',
    ACTIVITY_ZONE: '两张活动卡片',
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
      ElMessage.warning('请完整填写四个快捷入口')
      return null
    }
    return JSON.stringify({ items: quickItems.value }, null, 2)
  }
  if (moduleForm.moduleType === 'ACTIVITY_ZONE') {
    if (zoneItems.value.some(item => !item.kicker.trim() || !item.title.trim() || !item.linkText.trim() || !item.image.trim() || !item.type.trim())) {
      ElMessage.warning('请完整填写两张活动卡片')
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

async function save() {
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
      await adminApi.updateActivity(activityForm.code, {
        name: activityForm.name,
        activityType: activityForm.activityType,
        description: activityForm.description,
        rulesJson: activityForm.rulesJson,
        startsAt: activityForm.startsAt,
        endsAt: activityForm.endsAt,
        enabled: activityForm.enabled,
      })
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
    </PageToolbar>

    <article class="content-card marketing-card" v-loading="loading">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="首页展示内容" name="modules">
          <div class="home-config-tip">截图中的配送提示、四个入口和两张活动卡片都在这里修改；保存后用户下拉刷新小程序首页即可看到。</div>
          <div class="module-grid">
            <div v-for="item in modules" :key="item.id" class="module-item">
              <div class="module-icon">{{ item.moduleType.slice(0, 1) }}</div>
              <div class="module-main">
                <span>{{ moduleLabel(item.moduleType) }}</span>
                <h3>{{ item.title || moduleLabel(item.moduleType) }}</h3>
                <p>{{ modulePreview(item) }} · 更新于 {{ dateTime(item.updatedAt) }}</p>
              </div>
              <el-tag :type="item.enabled ? 'success' : 'info'" round>{{ item.enabled ? '展示中' : '已隐藏' }}</el-tag>
              <el-button circle :icon="Edit" @click="editModule(item)" />
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
            <el-table-column label="操作" width="90"><template #default="{ row }"><el-button link type="primary" @click="editActivity(row)">编辑</el-button></template></el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </article>

    <el-dialog v-model="dialogOpen" :title="dialogType === 'module' ? (moduleForm.id ? `编辑${moduleLabel(moduleForm.moduleType)}` : '新增首页组件') : '编辑营销活动'" width="760px">
      <el-form v-if="dialogType === 'module'" label-position="top">
        <div class="form-grid">
          <el-form-item label="组件类型">
            <el-select v-model="moduleForm.moduleType" :disabled="Boolean(moduleForm.id)" @change="initializeModuleEditor">
              <el-option label="四个快捷入口" value="QUICK_ENTRY" />
              <el-option label="两张活动卡片" value="ACTIVITY_ZONE" />
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
          <el-divider content-position="left">四个快捷入口</el-divider>
          <div class="visual-editor-grid quick-editor-grid">
            <div v-for="(item, index) in quickItems" :key="index" class="visual-item-editor">
              <div class="editor-item-title"><span :class="['config-icon-preview', item.theme]">{{ item.icon }}</span><strong>入口 {{ index + 1 }}</strong></div>
              <el-form-item label="图标文字"><el-input v-model="item.icon" maxlength="6" placeholder="例如：券" /></el-form-item>
              <el-form-item label="入口名称"><el-input v-model="item.name" maxlength="10" /></el-form-item>
              <el-form-item label="跳转活动编码"><el-input v-model="item.type" placeholder="例如：new" /></el-form-item>
              <el-form-item label="图标颜色"><el-select v-model="item.theme"><el-option v-for="theme in themeOptions" :key="theme.value" :label="theme.label" :value="theme.value" /></el-select></el-form-item>
            </div>
          </div>
        </template>

        <template v-else-if="moduleForm.moduleType === 'ACTIVITY_ZONE'">
          <el-divider content-position="left">两张活动卡片</el-divider>
          <div class="visual-editor-grid">
            <div v-for="(item, index) in zoneItems" :key="index" class="visual-item-editor zone-editor">
              <div class="editor-item-title"><span :class="['zone-color-preview', item.theme]" /><strong>活动卡片 {{ index + 1 }}</strong></div>
              <div class="form-grid">
                <el-form-item label="小标题"><el-input v-model="item.kicker" maxlength="12" placeholder="省心搭配" /></el-form-item>
                <el-form-item label="主标题"><el-input v-model="item.title" maxlength="12" placeholder="在家做饭" /></el-form-item>
                <el-form-item label="按钮文字"><el-input v-model="item.linkText" maxlength="12" placeholder="去看看 →" /></el-form-item>
                <el-form-item label="跳转活动编码"><el-input v-model="item.type" placeholder="例如：clean" /></el-form-item>
                <el-form-item label="商品图片路径" class="span-2"><el-input v-model="item.image" placeholder="/assets/products/mushroom.jpg" /></el-form-item>
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
        <el-form-item label="活动编码"><el-input v-model="activityForm.code" disabled /></el-form-item>
        <el-form-item label="活动名称"><el-input v-model="activityForm.name" /></el-form-item>
        <el-form-item label="活动类型"><el-input v-model="activityForm.activityType" /></el-form-item>
        <el-form-item label="启用状态"><el-switch v-model="activityForm.enabled" inline-prompt active-text="启用" inactive-text="停用" /></el-form-item>
        <el-form-item label="开始时间"><el-date-picker v-model="activityForm.startsAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
        <el-form-item label="结束时间"><el-date-picker v-model="activityForm.endsAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
        <el-form-item label="活动说明" class="span-2"><el-input v-model="activityForm.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="活动规则 JSON" class="span-2"><el-input v-model="activityForm.rulesJson" type="textarea" :rows="8" class="code-input" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogOpen = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存并发布</el-button></template>
    </el-dialog>
  </div>
</template>

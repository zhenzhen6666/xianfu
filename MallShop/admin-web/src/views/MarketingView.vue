<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Edit, Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '../api/admin'
import PageToolbar from '../components/PageToolbar.vue'
import type { Activity, HomeModule } from '../types/api'
import { dateTime } from '../utils/format'

const activeTab = ref('modules')
const loading = ref(false)
const saving = ref(false)
const dialogOpen = ref(false)
const dialogType = ref<'module' | 'activity'>('module')
const modules = ref<HomeModule[]>([])
const activities = ref<Activity[]>([])
const moduleForm = reactive({ id: 0, moduleType: 'BANNER', title: '', contentJson: '{\n  "items": []\n}', sortOrder: 0, enabled: true })
const activityForm = reactive<Activity>({ code: '', name: '', activityType: 'DISCOUNT', description: '', rulesJson: '{\n  "discount": 0.9\n}', startsAt: '', endsAt: '', enabled: true })

async function load() { loading.value = true; try { [modules.value, activities.value] = await Promise.all([adminApi.homeModules(), adminApi.activities()]) } finally { loading.value = false } }
function validJson(value: string) { try { JSON.parse(value); return true } catch { ElMessage.warning('JSON 配置格式不正确'); return false } }
function newModule() { Object.assign(moduleForm, { id: 0, moduleType: 'BANNER', title: '', contentJson: '{\n  "items": []\n}', sortOrder: modules.value.length + 1, enabled: true }); dialogType.value = 'module'; dialogOpen.value = true }
function editModule(row: HomeModule) { Object.assign(moduleForm, row); dialogType.value = 'module'; dialogOpen.value = true }
function editActivity(row: Activity) { Object.assign(activityForm, row); dialogType.value = 'activity'; dialogOpen.value = true }
async function save() {
  saving.value = true
  try {
    if (dialogType.value === 'module') {
      if (!moduleForm.moduleType || !validJson(moduleForm.contentJson)) return
      const payload = { moduleType: moduleForm.moduleType, title: moduleForm.title, contentJson: moduleForm.contentJson, sortOrder: moduleForm.sortOrder, enabled: moduleForm.enabled }
      modules.value = moduleForm.id ? await adminApi.updateHomeModule(moduleForm.id, payload) : await adminApi.createHomeModule(payload)
    } else {
      if (!activityForm.name || !activityForm.startsAt || !activityForm.endsAt || !validJson(activityForm.rulesJson)) return
      await adminApi.updateActivity(activityForm.code, { name: activityForm.name, activityType: activityForm.activityType, description: activityForm.description, rulesJson: activityForm.rulesJson, startsAt: activityForm.startsAt, endsAt: activityForm.endsAt, enabled: activityForm.enabled })
      activities.value = await adminApi.activities()
    }
    ElMessage.success('营销配置已保存'); dialogOpen.value = false
  } finally { saving.value = false }
}
onMounted(load)
</script>

<template>
  <div>
    <PageToolbar title="营销与首页装修" description="配置小程序首页模块与已有营销活动"><el-button :icon="Refresh" @click="load">刷新</el-button><el-button v-if="activeTab === 'modules'" type="primary" :icon="Plus" @click="newModule">新增组件</el-button></PageToolbar>
    <article class="content-card marketing-card" v-loading="loading">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="首页组件" name="modules">
          <div class="module-grid">
            <div v-for="item in modules" :key="item.id" class="module-item">
              <div class="module-icon">{{ item.moduleType.slice(0, 1) }}</div>
              <div class="module-main"><span>{{ item.moduleType }}</span><h3>{{ item.title || '未命名组件' }}</h3><p>排序 {{ item.sortOrder }} · 更新于 {{ dateTime(item.updatedAt) }}</p></div>
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

    <el-dialog v-model="dialogOpen" :title="dialogType === 'module' ? (moduleForm.id ? '编辑首页组件' : '新增首页组件') : '编辑营销活动'" width="680px">
      <el-form v-if="dialogType === 'module'" label-position="top" class="form-grid">
        <el-form-item label="组件类型"><el-select v-model="moduleForm.moduleType" allow-create filterable><el-option label="轮播图 BANNER" value="BANNER" /><el-option label="快捷入口 NAV" value="NAV" /><el-option label="商品区 PRODUCT" value="PRODUCT" /><el-option label="公告 NOTICE" value="NOTICE" /></el-select></el-form-item>
        <el-form-item label="组件标题"><el-input v-model="moduleForm.title" /></el-form-item>
        <el-form-item label="展示排序"><el-input-number v-model="moduleForm.sortOrder" :min="0" /></el-form-item>
        <el-form-item label="展示状态"><el-switch v-model="moduleForm.enabled" inline-prompt active-text="展示" inactive-text="隐藏" /></el-form-item>
        <el-form-item label="内容 JSON" class="span-2"><el-input v-model="moduleForm.contentJson" type="textarea" :rows="11" class="code-input" /></el-form-item>
      </el-form>
      <el-form v-else label-position="top" class="form-grid">
        <el-form-item label="活动编码"><el-input v-model="activityForm.code" disabled /></el-form-item><el-form-item label="活动名称"><el-input v-model="activityForm.name" /></el-form-item>
        <el-form-item label="活动类型"><el-input v-model="activityForm.activityType" /></el-form-item><el-form-item label="启用状态"><el-switch v-model="activityForm.enabled" inline-prompt active-text="启用" inactive-text="停用" /></el-form-item>
        <el-form-item label="开始时间"><el-date-picker v-model="activityForm.startsAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item><el-form-item label="结束时间"><el-date-picker v-model="activityForm.endsAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
        <el-form-item label="活动说明" class="span-2"><el-input v-model="activityForm.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="活动规则 JSON" class="span-2"><el-input v-model="activityForm.rulesJson" type="textarea" :rows="8" class="code-input" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogOpen = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存配置</el-button></template>
    </el-dialog>
  </div>
</template>

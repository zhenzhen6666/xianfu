<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Delete, Edit, Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../api/admin'
import PageToolbar from '../components/PageToolbar.vue'
import type { Category } from '../types/api'

const loading = ref(false)
const saving = ref(false)
const dialogOpen = ref(false)
const categories = ref<Category[]>([])
const form = reactive<Category>({ code: '', name: '', parentCode: '', iconUrl: '', sortOrder: 0, enabled: true })
const creating = ref(false)

async function load() { loading.value = true; try { categories.value = await adminApi.categories() } finally { loading.value = false } }
function create() {
  creating.value = true
  Object.assign(form, { code: '', name: '', parentCode: '', iconUrl: '', sortOrder: categories.value.length + 1, enabled: true })
  dialogOpen.value = true
}
function edit(row: Category) { creating.value = false; Object.assign(form, { ...row, parentCode: row.parentCode || '', iconUrl: row.iconUrl || '' }); dialogOpen.value = true }
async function save() {
  if (!form.name.trim()) return ElMessage.warning('分类名称不能为空')
  saving.value = true
  try {
    const payload = { name: form.name.trim(), parentCode: form.parentCode || undefined, iconUrl: form.iconUrl || '', sortOrder: form.sortOrder, enabled: form.enabled }
    categories.value = creating.value
      ? await adminApi.createCategory(payload)
      : await adminApi.saveCategory(form.code, payload)
    ElMessage.success(creating.value ? '分类已创建' : '分类已保存'); dialogOpen.value = false
  } finally { saving.value = false }
}
async function toggle(row: Category) {
  categories.value = await adminApi.saveCategory(row.code, {
    name: row.name, parentCode: row.parentCode, iconUrl: row.iconUrl, sortOrder: row.sortOrder, enabled: !row.enabled,
  })
  ElMessage.success(row.enabled ? '分类已停用' : '分类已启用')
}
async function removeCategory(row: Category) {
  try {
    await ElMessageBox.confirm(`确定删除“${row.name}”吗？分类下有商品或子分类时不能删除。`, '删除分类', {
      type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消',
    })
  } catch { return }
  await adminApi.deleteCategory(row.code)
  ElMessage.success('分类已删除')
  await load()
}
onMounted(load)
</script>

<template>
  <div>
    <PageToolbar title="商品分类" description="新增、编辑、停用和删除小程序商品分类"><el-button :icon="Refresh" @click="load">刷新</el-button><el-button type="primary" :icon="Plus" @click="create">新增分类</el-button></PageToolbar>
    <article class="content-card table-card" v-loading="loading">
      <el-table :data="categories" row-key="code" class="clean-table">
        <el-table-column label="分类" min-width="230"><template #default="{ row }"><div class="category-cell"><span>{{ row.name.slice(0, 1) }}</span><div><strong>{{ row.name }}</strong><small>{{ row.code }}</small></div></div></template></el-table-column>
        <el-table-column label="层级" width="150"><template #default="{ row }">{{ row.parentCode ? `子分类 · ${row.parentCode}` : '一级分类' }}</template></el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="100" />
        <el-table-column label="状态" width="120"><template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'" round>{{ row.enabled ? '已启用' : '未启用' }}</el-tag></template></el-table-column>
        <el-table-column prop="iconUrl" label="图标地址" min-width="260" show-overflow-tooltip />
        <el-table-column label="操作" width="225"><template #default="{ row }"><el-button link type="primary" :icon="Edit" @click="edit(row)">编辑</el-button><el-button link :type="row.enabled ? 'warning' : 'success'" @click="toggle(row)">{{ row.enabled ? '停用' : '启用' }}</el-button><el-button link type="danger" :icon="Delete" @click="removeCategory(row)">删除</el-button></template></el-table-column>
      </el-table>
    </article>
    <el-dialog v-model="dialogOpen" :title="creating ? '新增分类' : '编辑分类'" width="560px">
      <el-form label-position="top" class="form-grid">
        <el-form-item v-if="!creating" label="分类编码"><el-input v-model="form.code" disabled /></el-form-item>
        <el-form-item label="分类名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="上级分类"><el-select v-model="form.parentCode" clearable><el-option v-for="item in categories.filter((item) => !item.parentCode && item.code !== form.code)" :key="item.code" :label="item.name" :value="item.code" /></el-select></el-form-item>
        <el-form-item label="展示排序"><el-input-number v-model="form.sortOrder" :min="0" /></el-form-item>
        <el-form-item label="图标 URL" class="span-2"><el-input v-model="form.iconUrl" /></el-form-item>
        <el-form-item label="启用状态"><el-switch v-model="form.enabled" inline-prompt active-text="启用" inactive-text="停用" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogOpen = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>

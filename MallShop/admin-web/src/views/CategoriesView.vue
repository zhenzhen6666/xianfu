<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Edit, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '../api/admin'
import PageToolbar from '../components/PageToolbar.vue'
import type { Category } from '../types/api'

const loading = ref(false)
const saving = ref(false)
const dialogOpen = ref(false)
const categories = ref<Category[]>([])
const form = reactive<Category>({ code: '', name: '', parentCode: '', iconUrl: '', sortOrder: 0, enabled: true })

async function load() { loading.value = true; try { categories.value = await adminApi.categories() } finally { loading.value = false } }
function edit(row: Category) { Object.assign(form, { ...row, parentCode: row.parentCode || '', iconUrl: row.iconUrl || '' }); dialogOpen.value = true }
async function save() {
  if (!form.code.trim() || !form.name.trim()) return ElMessage.warning('分类编码和名称不能为空')
  saving.value = true
  try {
    categories.value = await adminApi.saveCategory(form.code.trim(), { name: form.name.trim(), parentCode: form.parentCode || undefined, iconUrl: form.iconUrl || '', sortOrder: form.sortOrder, enabled: form.enabled })
    ElMessage.success('分类已保存'); dialogOpen.value = false
  } finally { saving.value = false }
}
onMounted(load)
</script>

<template>
  <div>
    <PageToolbar title="商品分类" description="编辑小程序分类名称、层级、排序和启用状态"><el-button :icon="Refresh" @click="load">刷新</el-button></PageToolbar>
    <article class="content-card table-card" v-loading="loading">
      <el-table :data="categories" row-key="code" class="clean-table">
        <el-table-column label="分类" min-width="230"><template #default="{ row }"><div class="category-cell"><span>{{ row.name.slice(0, 1) }}</span><div><strong>{{ row.name }}</strong><small>{{ row.code }}</small></div></div></template></el-table-column>
        <el-table-column label="层级" width="150"><template #default="{ row }">{{ row.parentCode ? `子分类 · ${row.parentCode}` : '一级分类' }}</template></el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="100" />
        <el-table-column label="状态" width="120"><template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'" round>{{ row.enabled ? '已启用' : '未启用' }}</el-tag></template></el-table-column>
        <el-table-column prop="iconUrl" label="图标地址" min-width="260" show-overflow-tooltip />
        <el-table-column label="操作" width="100"><template #default="{ row }"><el-button link type="primary" :icon="Edit" @click="edit(row)">编辑</el-button></template></el-table-column>
      </el-table>
    </article>
    <el-dialog v-model="dialogOpen" title="编辑分类" width="560px">
      <el-form label-position="top" class="form-grid">
        <el-form-item label="分类编码"><el-input v-model="form.code" disabled /></el-form-item>
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

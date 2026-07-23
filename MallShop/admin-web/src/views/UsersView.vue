<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import 'element-plus/es/components/message-box/style/css'
import { adminApi } from '../api/admin'
import PageToolbar from '../components/PageToolbar.vue'
import StatusTag from '../components/StatusTag.vue'
import type { AdminUser } from '../types/api'
import { dateTime, money } from '../utils/format'

const loading = ref(false)
const keyword = ref('')
const users = ref<AdminUser[]>([])
async function load() { loading.value = true; try { users.value = await adminApi.users(keyword.value) } finally { loading.value = false } }
async function toggle(row: AdminUser) {
  const target = row.status === 'ACTIVE' ? 'BLOCKED' : 'ACTIVE'
  await ElMessageBox.confirm(`确定${target === 'BLOCKED' ? '停用' : '恢复'}会员“${row.nickname}”吗？`, '账号状态确认', { type: 'warning' })
  await adminApi.updateUserStatus(row.id, target); ElMessage.success(target === 'BLOCKED' ? '账号已停用' : '账号已恢复'); await load()
}
onMounted(load)
</script>

<template>
  <div>
    <PageToolbar title="会员用户" description="查看会员等级、积分、消费额并管理账号状态"><el-button :icon="Refresh" @click="load">刷新</el-button></PageToolbar>
    <div class="filter-bar"><el-input v-model="keyword" :prefix-icon="Search" clearable placeholder="搜索昵称或手机号" class="search-input" @keyup.enter="load" /><el-button type="primary" @click="load">查询</el-button><span class="result-count">共 {{ users.length }} 位会员</span></div>
    <article class="content-card table-card" v-loading="loading">
      <el-table :data="users" class="clean-table">
        <el-table-column label="会员" min-width="210"><template #default="{ row }"><div class="member-cell"><span>{{ row.nickname.slice(0,1) }}</span><div><strong>{{ row.nickname }}</strong><small>ID {{ row.id }} · {{ row.mobile || '未绑定手机' }}</small></div></div></template></el-table-column>
        <el-table-column prop="memberLevelName" label="会员等级" width="130"><template #default="{ row }"><span class="level-badge">{{ row.memberLevelName }}</span></template></el-table-column>
        <el-table-column label="积分" width="110"><template #default="{ row }"><strong>{{ row.points }}</strong></template></el-table-column>
        <el-table-column label="累计消费" width="140"><template #default="{ row }"><strong>{{ money(row.totalSpent) }}</strong></template></el-table-column>
        <el-table-column label="注册时间" width="165"><template #default="{ row }">{{ dateTime(row.createdAt) }}</template></el-table-column>
        <el-table-column label="状态" width="105"><template #default="{ row }"><StatusTag :status="row.status" /></template></el-table-column>
        <el-table-column label="操作" width="110"><template #default="{ row }"><el-button link :type="row.status === 'ACTIVE' ? 'danger' : 'success'" @click="toggle(row)">{{ row.status === 'ACTIVE' ? '停用账号' : '恢复账号' }}</el-button></template></el-table-column>
      </el-table>
    </article>
  </div>
</template>

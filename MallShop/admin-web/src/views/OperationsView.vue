<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../api/admin'
import PageToolbar from '../components/PageToolbar.vue'
import type { AuthSession, BusinessAlert, ErrorLog, PaymentCallbackLog } from '../types/api'
import { dateTime } from '../utils/format'

const tab = ref('alerts')
const loading = ref(false)
const alerts = ref<BusinessAlert[]>([])
const errors = ref<ErrorLog[]>([])
const callbacks = ref<PaymentCallbackLog[]>([])
const sessions = ref<AuthSession[]>([])
async function load() {
  loading.value = true
  try {
    ;[alerts.value, errors.value, callbacks.value, sessions.value] = await Promise.all([
      adminApi.alerts(), adminApi.errorLogs(), adminApi.paymentCallbacks(), adminApi.sessions(),
    ])
  } finally { loading.value = false }
}
async function acknowledge(id: number) { await adminApi.acknowledgeAlert(id); ElMessage.success('告警已确认'); await load() }
async function revoke(row: AuthSession) {
  await ElMessageBox.confirm(`确定强制退出 ${row.nickname} 的该会话吗？`, '会话管理', { type: 'warning' })
  await adminApi.revokeSession(row.id); ElMessage.success('会话已失效'); await load()
}
onMounted(load)
</script>

<template>
  <div>
    <PageToolbar title="系统运维" description="集中查看业务告警、系统错误、支付回调和管理员会话">
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </PageToolbar>
    <article class="content-card" v-loading="loading">
      <el-tabs v-model="tab">
        <el-tab-pane :label="`业务告警（${alerts.filter(i => i.status === 'OPEN').length}）`" name="alerts">
          <el-table :data="alerts">
            <el-table-column label="级别" width="95"><template #default="{row}"><el-tag :type="row.severity === 'CRITICAL' ? 'danger' : 'warning'">{{ row.severity }}</el-tag></template></el-table-column>
            <el-table-column prop="title" label="告警" min-width="180" />
            <el-table-column prop="message" label="说明" min-width="300" />
            <el-table-column label="时间" width="170"><template #default="{row}">{{ dateTime(row.createdAt) }}</template></el-table-column>
            <el-table-column label="操作" width="90"><template #default="{row}"><el-button v-if="row.status==='OPEN'" link type="primary" @click="acknowledge(row.id)">确认</el-button><span v-else>已确认</span></template></el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane :label="`错误日志（${errors.length}）`" name="errors">
          <el-table :data="errors"><el-table-column prop="errorCode" label="错误码" width="170"/><el-table-column prop="requestMethod" label="方法" width="80"/><el-table-column prop="requestPath" label="请求路径" min-width="230"/><el-table-column prop="message" label="错误信息" min-width="300"/><el-table-column label="时间" width="170"><template #default="{row}">{{ dateTime(row.createdAt) }}</template></el-table-column></el-table>
        </el-tab-pane>
        <el-tab-pane :label="`支付回调（${callbacks.length}）`" name="callbacks">
          <el-table :data="callbacks"><el-table-column prop="paymentNo" label="支付流水" min-width="210"/><el-table-column prop="channel" label="渠道" width="90"/><el-table-column label="签名" width="90"><template #default="{row}"><el-tag :type="row.signatureValid?'success':'danger'">{{ row.signatureValid?'有效':'无效' }}</el-tag></template></el-table-column><el-table-column prop="resultCode" label="结果" width="170"/><el-table-column prop="message" label="说明" min-width="240"/><el-table-column label="时间" width="170"><template #default="{row}">{{ dateTime(row.createdAt) }}</template></el-table-column></el-table>
        </el-tab-pane>
        <el-tab-pane :label="`会话管理（${sessions.filter(i => !i.revoked).length}）`" name="sessions">
          <el-table :data="sessions"><el-table-column prop="nickname" label="用户" width="130"/><el-table-column prop="role" label="角色" width="100"/><el-table-column prop="ipAddress" label="IP" width="140"/><el-table-column prop="userAgent" label="设备" min-width="300" show-overflow-tooltip/><el-table-column label="登录时间" width="170"><template #default="{row}">{{ dateTime(row.createdAt) }}</template></el-table-column><el-table-column label="状态" width="90"><template #default="{row}"><el-tag :type="row.revoked?'info':'success'">{{ row.revoked?'已退出':'有效' }}</el-tag></template></el-table-column><el-table-column label="操作" width="100"><template #default="{row}"><el-button v-if="!row.revoked" link type="danger" @click="revoke(row)">强制退出</el-button></template></el-table-column></el-table>
        </el-tab-pane>
      </el-tabs>
    </article>
  </div>
</template>

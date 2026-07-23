<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Check, Refresh, Van } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '../api/admin'
import PageToolbar from '../components/PageToolbar.vue'
import type { DeliveryConfig } from '../types/api'
import { dateTime, money } from '../utils/format'

const loading = ref(false)
const saving = ref(false)
const config = reactive<DeliveryConfig>({ id: 0, name: '', freeThreshold: 30, baseFee: 3, maxDistanceKm: 3, promiseMinutes: 30, announcement: '', updatedAt: '' })
async function load() { loading.value = true; try { Object.assign(config, await adminApi.delivery()) } finally { loading.value = false } }
async function save() {
  if (!config.name.trim() || !config.announcement.trim()) return ElMessage.warning('请完整填写配送方案与公告')
  saving.value = true
  try { Object.assign(config, await adminApi.updateDelivery({ name: config.name, freeThreshold: config.freeThreshold, baseFee: config.baseFee, maxDistanceKm: config.maxDistanceKm, promiseMinutes: config.promiseMinutes, announcement: config.announcement })); ElMessage.success('配送规则已生效') }
  finally { saving.value = false }
}
onMounted(load)
</script>

<template>
  <div>
    <PageToolbar title="配送规则" description="配置当前生效的运费、服务半径和配送承诺"><el-button :icon="Refresh" @click="load">重新加载</el-button><el-button type="primary" :icon="Check" :loading="saving" @click="save">保存并生效</el-button></PageToolbar>
    <div class="delivery-layout" v-loading="loading">
      <article class="content-card delivery-form-card">
        <div class="card-title"><span class="round-icon"><el-icon><Van /></el-icon></span><div><span class="section-kicker">DELIVERY POLICY</span><h3>当前配送方案</h3></div></div>
        <el-form label-position="top" class="delivery-form">
          <el-form-item label="方案名称"><el-input v-model="config.name" placeholder="例：三公里即时达" /></el-form-item>
          <div class="form-grid">
            <el-form-item label="免配送费门槛（元）"><el-input-number v-model="config.freeThreshold" :min="0" :precision="2" /></el-form-item>
            <el-form-item label="基础配送费（元）"><el-input-number v-model="config.baseFee" :min="0" :precision="2" /></el-form-item>
            <el-form-item label="最大配送距离（公里）"><el-input-number v-model="config.maxDistanceKm" :min="0.1" :precision="1" /></el-form-item>
            <el-form-item label="承诺送达（分钟）"><el-input-number v-model="config.promiseMinutes" :min="1" /></el-form-item>
          </div>
          <el-form-item label="小程序配送公告"><el-input v-model="config.announcement" type="textarea" :rows="4" maxlength="255" show-word-limit /></el-form-item>
        </el-form>
        <p class="updated-note">上次更新：{{ dateTime(config.updatedAt) }}</p>
      </article>
      <aside class="delivery-preview">
        <div class="preview-label">小程序规则预览</div>
        <div class="phone-card">
          <div class="phone-status"><span>09:41</span><span>● ● ▰</span></div>
          <div class="preview-store"><span class="mini-logo">鲜</span><div><strong>鲜福铺中心门店</strong><small>{{ config.name || '即时配送' }}</small></div></div>
          <div class="route-visual"><span class="route-store">店</span><i /><span class="route-home">家</span><div>{{ config.maxDistanceKm }} km</div></div>
          <div class="preview-rule"><span>预计送达</span><strong>{{ config.promiseMinutes }} 分钟</strong></div>
          <div class="preview-rule"><span>配送费</span><strong>{{ money(config.baseFee) }}</strong></div>
          <div class="preview-rule"><span>免配送费</span><strong>满 {{ money(config.freeThreshold) }}</strong></div>
          <p>{{ config.announcement || '配送公告将在这里展示' }}</p>
        </div>
      </aside>
    </div>
  </div>
</template>

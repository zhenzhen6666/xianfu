<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, Check, Lock, User } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const form = reactive({ nickname: '鲜福铺管理员', secret: '' })

async function submit() {
  if (!form.nickname.trim() || !form.secret) return
  loading.value = true
  try {
    await auth.login(form.nickname.trim(), form.secret)
    await router.replace(String(route.query.redirect || '/dashboard'))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-story">
      <div class="login-brand"><span class="brand-mark"><span>鲜</span></span><strong>鲜福铺</strong></div>
      <div class="story-copy">
        <p class="eyebrow">XIANFUPU STORE OS</p>
        <h1>每一份新鲜，<br />都值得被认真经营。</h1>
        <p>从商品上架到最后一公里配送，把门店的每个经营环节放在一处，清晰、可靠、随时可控。</p>
      </div>
      <div class="story-stats">
        <div><strong>3 km</strong><span>核心配送半径</span></div>
        <div><strong>30 min</strong><span>承诺送达时效</span></div>
        <div><strong>24 / 7</strong><span>线上门店运营</span></div>
      </div>
      <div class="produce-art" aria-hidden="true"><span class="leaf leaf-one" /><span class="leaf leaf-two" /><span class="fruit">福</span></div>
    </section>

    <section class="login-panel">
      <div class="login-box">
        <div class="login-title"><span>商家管理端</span><h2>欢迎回来</h2><p>登录后继续管理今日的门店经营</p></div>
        <el-form label-position="top" size="large" @submit.prevent="submit">
          <el-form-item label="管理员名称">
            <el-input v-model="form.nickname" :prefix-icon="User" placeholder="请输入管理员名称" @keyup.enter="submit" />
          </el-form-item>
          <el-form-item label="管理员口令">
            <el-input v-model="form.secret" :prefix-icon="Lock" type="password" show-password placeholder="请输入 DEV_ADMIN_SECRET" @keyup.enter="submit" />
          </el-form-item>
          <button class="login-submit" type="button" :disabled="loading || !form.nickname || !form.secret" @click="submit">
            <span>{{ loading ? '正在验证…' : '进入管理系统' }}</span><el-icon><ArrowRight /></el-icon>
          </button>
        </el-form>
        <div class="login-hint"><el-icon><Check /></el-icon><span>本地开发默认口令：<code>Admin@123456</code></span></div>
      </div>
      <p class="login-footer">鲜福铺门店运营系统 · 数据由 Java 后端实时提供</p>
    </section>
  </main>
</template>

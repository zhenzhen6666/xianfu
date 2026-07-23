import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { adminApi } from '../api/admin'
import type { UserProfile } from '../types/api'

const readUser = (): UserProfile | null => {
  try {
    return JSON.parse(localStorage.getItem('xianfupu_admin_user') || 'null')
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('xianfupu_admin_token') || '')
  const user = ref<UserProfile | null>(readUser())
  const isLoggedIn = computed(() => Boolean(token.value))

  async function login(nickname: string, secret: string) {
    const result = await adminApi.login(nickname, secret)
    token.value = result.accessToken
    user.value = result.user
    localStorage.setItem('xianfupu_admin_token', result.accessToken)
    localStorage.setItem('xianfupu_admin_user', JSON.stringify(result.user))
  }

  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem('xianfupu_admin_token')
    localStorage.removeItem('xianfupu_admin_user')
  }

  return { token, user, isLoggedIn, login, logout }
})

import axios, { AxiosError } from 'axios'
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import type { ApiError } from '../types/api'

const ACCESS_KEY = 'xianfupu_admin_token'
const REFRESH_KEY = 'xianfupu_admin_refresh_token'
let refreshPromise: Promise<string> | null = null

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(ACCESS_KEY)
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiError>) => {
    const status = error.response?.status
    const message = error.response?.data?.message || (error.code === 'ECONNABORTED' ? '请求超时，请稍后重试' : '服务暂时不可用')
    const original = error.config as typeof error.config & { _retry?: boolean }
    const refreshToken = localStorage.getItem(REFRESH_KEY)
    if (status === 401 && original && !original._retry && refreshToken && !original.url?.includes('/auth/refresh')) {
      original._retry = true
      if (!refreshPromise) {
        refreshPromise = axios.post(`${http.defaults.baseURL}/auth/refresh`, { refreshToken })
          .then(({ data }) => {
            localStorage.setItem(ACCESS_KEY, data.accessToken)
            localStorage.setItem(REFRESH_KEY, data.refreshToken)
            localStorage.setItem('xianfupu_admin_user', JSON.stringify(data.user))
            return data.accessToken as string
          }).finally(() => { refreshPromise = null })
      }
      try {
        const token = await refreshPromise
        original.headers.Authorization = `Bearer ${token}`
        return http.request(original)
      } catch {
        // 刷新失败后统一清理登录态。
      }
    }
    if (status === 401 && window.location.pathname !== '/login') {
      localStorage.removeItem(ACCESS_KEY)
      localStorage.removeItem(REFRESH_KEY)
      localStorage.removeItem('xianfupu_admin_user')
      window.location.assign('/login')
    }
    ElMessage.error(message)
    return Promise.reject(error)
  },
)

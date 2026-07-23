import axios, { AxiosError } from 'axios'
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import type { ApiError } from '../types/api'

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('xianfupu_admin_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiError>) => {
    const status = error.response?.status
    const message = error.response?.data?.message || (error.code === 'ECONNABORTED' ? '请求超时，请稍后重试' : '服务暂时不可用')
    if (status === 401 && window.location.pathname !== '/login') {
      localStorage.removeItem('xianfupu_admin_token')
      localStorage.removeItem('xianfupu_admin_user')
      window.location.assign('/login')
    }
    ElMessage.error(message)
    return Promise.reject(error)
  },
)

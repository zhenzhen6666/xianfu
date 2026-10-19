import { http } from '../api/http'

export function imageUrl(value?: string) {
  if (!value) return ''
  if (!value.startsWith('/api/files/')) return value
  const base = String(http.defaults.baseURL || '/api').replace(/\/+$/, '')
  return `${base}${value.slice(4)}`
}

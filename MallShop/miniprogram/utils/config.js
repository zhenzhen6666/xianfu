const API_BASE_URL_KEY = 'xianfupu_api_base_url'
const TOKEN_KEY = 'xianfupu_access_token'
const REFRESH_TOKEN_KEY = 'xianfupu_refresh_token'
const USER_KEY = 'xianfupu_user_profile'

// 微信开发者工具与 Java 服务运行在同一台电脑时使用此地址。
// 真机调试时请在开发者工具控制台执行：
// wx.setStorageSync('xianfupu_api_base_url', 'http://电脑局域网IP:8080/api')
const DEFAULT_API_BASE_URL = 'http://127.0.0.1:8080/api'

function getApiBaseUrl() {
  const value = wx.getStorageSync(API_BASE_URL_KEY) || DEFAULT_API_BASE_URL
  return String(value).replace(/\/+$/, '')
}

function setApiBaseUrl(value) {
  const normalized = String(value || '').trim().replace(/\/+$/, '')
  if (!/^https?:\/\//.test(normalized)) throw new Error('接口地址必须以 http:// 或 https:// 开头')
  wx.setStorageSync(API_BASE_URL_KEY, normalized)
  return normalized
}

function resolveApiFileUrl(value) {
  if (!value || /^https?:\/\//.test(value)) return value || ''
  return `${getApiBaseUrl().replace(/\/api$/, '')}${value}`
}

module.exports = {
  API_BASE_URL_KEY,
  DEFAULT_API_BASE_URL,
  TOKEN_KEY,
  REFRESH_TOKEN_KEY,
  USER_KEY,
  getApiBaseUrl,
  setApiBaseUrl,
  resolveApiFileUrl
}

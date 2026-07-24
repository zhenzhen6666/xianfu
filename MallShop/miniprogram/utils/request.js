const config = require('./config')

let loginPromise = null

function compactQuery(data) {
  if (!data || typeof data !== 'object' || Array.isArray(data)) return data
  return Object.keys(data).reduce((result, key) => {
    const value = data[key]
    if (value !== undefined && value !== null && value !== '') result[key] = value
    return result
  }, {})
}

function rawRequest(path, options = {}) {
  const { method = 'GET', data, auth = true, token } = options
  const requestData = method === 'GET' ? compactQuery(data) : data
  return new Promise((resolve, reject) => {
    const headers = { 'Content-Type': 'application/json' }
    const accessToken = token || wx.getStorageSync(config.TOKEN_KEY)
    if (auth && accessToken) headers.Authorization = `Bearer ${accessToken}`
    wx.request({
      url: `${config.getApiBaseUrl()}${path}`,
      method,
      data: requestData,
      header: headers,
      timeout: 15000,
      success(response) {
        if (response.statusCode >= 200 && response.statusCode < 300) return resolve(response.data)
        const error = new Error((response.data && response.data.message) || `接口请求失败（${response.statusCode}）`)
        error.statusCode = response.statusCode
        error.code = response.data && response.data.code
        reject(error)
      },
      fail(error) {
        const message = error && error.errMsg && error.errMsg.includes('timeout')
          ? '连接后端超时，请确认 Java 服务已启动'
          : `无法连接后端：${config.getApiBaseUrl()}`
        reject(new Error(message))
      }
    })
  })
}

function saveLogin(result) {
  wx.setStorageSync(config.TOKEN_KEY, result.accessToken)
  wx.setStorageSync(config.USER_KEY, result.user)
  return result.user
}

function devLogin() {
  const localProfile = wx.getStorageSync('xianfupu_local_profile') || {}
  return rawRequest('/auth/dev-login', {
    method: 'POST',
    auth: false,
    data: {
      nickname: localProfile.nickname || '鲜福铺用户',
      mobile: localProfile.mobile || '13866686688'
    }
  }).then(saveLogin)
}

function wechatLogin() {
  return new Promise((resolve, reject) => {
    wx.login({ success: resolve, fail: reject })
  }).then(result => rawRequest('/auth/wechat-login', {
    method: 'POST',
    auth: false,
    data: { code: result.code, nickname: '鲜福铺用户', avatarUrl: '' }
  })).then(saveLogin)
}

function ensureLogin(force = false) {
  const token = wx.getStorageSync(config.TOKEN_KEY)
  if (token && !force) return Promise.resolve(wx.getStorageSync(config.USER_KEY) || null)
  if (loginPromise) return loginPromise
  const mode = wx.getStorageSync('xianfupu_login_mode') || 'dev'
  loginPromise = (mode === 'wechat' ? wechatLogin() : devLogin()).finally(() => { loginPromise = null })
  return loginPromise
}

function request(path, options = {}) {
  if (options.auth === false) return rawRequest(path, options)
  return ensureLogin().then(() => rawRequest(path, options)).catch(error => {
    if (error.statusCode !== 401 || options.retried) throw error
    wx.removeStorageSync(config.TOKEN_KEY)
    wx.removeStorageSync(config.USER_KEY)
    return ensureLogin(true).then(() => rawRequest(path, Object.assign({}, options, { retried: true })))
  })
}

function logout() {
  wx.removeStorageSync(config.TOKEN_KEY)
  wx.removeStorageSync(config.USER_KEY)
}

module.exports = { request, ensureLogin, logout }

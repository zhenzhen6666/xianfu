const api = require('./utils/api')
const config = require('./utils/config')

App({
  globalData: {
    brand: '鲜福铺',
    slogan: '三公里鲜到家',
    deliveryThreshold: 30,
    deliveryFee: 3,
    apiBaseUrl: config.getApiBaseUrl()
  },

  onLaunch() {
    api.ensureLogin().catch(error => console.warn('后端登录失败：', error.message))
  }
})

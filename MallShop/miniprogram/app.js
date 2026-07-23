App({
  globalData: {
    brand: '鲜福铺',
    slogan: '三公里鲜到家',
    deliveryThreshold: 30,
    deliveryFee: 3
  },

  onLaunch() {
    if (!wx.getStorageSync('xianfupu_cart')) {
      wx.setStorageSync('xianfupu_cart', [])
    }
  }
})

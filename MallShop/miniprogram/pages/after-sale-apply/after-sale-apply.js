const api = require('../../utils/api')
const config = require('../../utils/config')
Page({
  data: { orderNo: '', reason: '', description: '', images: [], submitting: false },
  onLoad(options) { this.setData({ orderNo: options.orderNo || '' }) },
  bind(e) { this.setData({ [e.currentTarget.dataset.field]: e.detail.value }) },
  chooseImages() {
    wx.chooseMedia({ count: 6 - this.data.images.length, mediaType: ['image'], success: result => {
      this.setData({ images: this.data.images.concat(result.tempFiles.map(item => ({ tempPath: item.tempFilePath, uploadedUrl: '' }))) })
    } })
  },
  remove(e) { const images = this.data.images.slice(); images.splice(Number(e.currentTarget.dataset.index), 1); this.setData({ images }) },
  async submit() {
    if (!this.data.reason.trim()) return wx.showToast({ title: '请填写售后原因', icon: 'none' })
    if (this.data.submitting) return
    this.setData({ submitting: true }); wx.showLoading({ title: '上传并提交' })
    try {
      const imageUrls = []
      for (const image of this.data.images) {
        const uploaded = image.uploadedUrl ? { url: image.uploadedUrl } : await api.uploadAfterSale(image.tempPath)
        imageUrls.push(uploaded.url)
      }
      const result = await api.createAfterSale({ orderNo: this.data.orderNo, reason: this.data.reason.trim(), description: this.data.description.trim(), imageUrls })
      wx.hideLoading(); wx.showToast({ title: '申请已提交', icon: 'success' })
      setTimeout(() => wx.redirectTo({ url: `/pages/after-sale-detail/after-sale-detail?no=${result.afterSaleNo}` }), 600)
    } catch (error) { wx.hideLoading(); wx.showToast({ title: error.message, icon: 'none' }) }
    finally { this.setData({ submitting: false }) }
  }
})

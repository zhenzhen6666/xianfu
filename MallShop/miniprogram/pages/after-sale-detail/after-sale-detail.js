const api = require('../../utils/api')
const config = require('../../utils/config')
Page({
  data: { item: null, loading: true },
  onLoad(options) { this.no = options.no; this.load() },
  async load() {
    try {
      const item = await api.afterSale(this.no)
      item.images = (item.images || []).map(config.resolveApiFileUrl)
      this.setData({ item, loading: false })
    } catch (error) { this.setData({ loading: false }); wx.showToast({ title: error.message, icon: 'none' }) }
  },
  preview(e) { wx.previewImage({ current: e.currentTarget.dataset.url, urls: this.data.item.images }) }
})

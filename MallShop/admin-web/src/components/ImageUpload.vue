<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import { adminApi } from '../api/admin'
import { imageUrl } from '../utils/image'

defineProps<{ modelValue?: string; disabled?: boolean }>()
const emit = defineEmits<{
  'update:modelValue': [value: string]
  uploading: [value: boolean]
}>()
const input = ref<HTMLInputElement>()
const uploading = ref(false)
let controller: AbortController | undefined
let disposed = false

onBeforeUnmount(() => {
  disposed = true
  controller?.abort()
})

async function selectFile(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  target.value = ''
  if (!file || uploading.value) return
  if (!/^image\/(jpeg|png|webp|gif)$/i.test(file.type) || !/\.(jpe?g|png|webp|gif)$/i.test(file.name)) {
    ElMessage.warning('请选择 JPG、PNG、WebP 或 GIF 图片')
    return
  }
  if (!file.size || file.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片不能为空且不能超过 5MB')
    return
  }
  uploading.value = true
  emit('uploading', true)
  controller = new AbortController()
  try {
    const result = await adminApi.uploadImage(file, controller.signal)
    if (!disposed) {
      emit('update:modelValue', result.url)
      ElMessage.success('图片上传成功，保存后生效')
    }
  } catch {
    // 请求层统一提示错误，上传失败时保留原图。
  } finally {
    uploading.value = false
    emit('uploading', false)
    controller = undefined
  }
}
</script>

<template>
  <div class="image-upload">
    <input ref="input" type="file" accept="image/jpeg,image/png,image/webp,image/gif" :disabled="disabled || uploading" hidden @change="selectFile" />
    <div class="image-upload-row">
      <el-image v-if="modelValue" :src="imageUrl(modelValue)" :preview-src-list="[imageUrl(modelValue)]" preview-teleported fit="cover" class="image-preview" />
      <div class="image-upload-actions">
        <el-button :icon="Upload" :loading="uploading" :disabled="disabled || uploading" @click="input?.click()">{{ uploading ? '上传中…' : modelValue ? '替换图片' : '从本地上传' }}</el-button>
        <el-button v-if="modelValue" type="danger" link :disabled="disabled || uploading" @click="emit('update:modelValue', '')">移除图片</el-button>
      </div>
    </div>
    <div class="image-upload-tip">支持 JPG、PNG、WebP、GIF，单张不超过 5MB</div>
  </div>
</template>

<style scoped>
.image-upload { width: 100%; }
.image-upload-row { display: flex; align-items: center; gap: 16px; }
.image-preview { width: 100px; height: 100px; flex-shrink: 0; border: 1px solid var(--el-border-color); border-radius: 10px; }
.image-upload-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; }
.image-upload-actions .el-button { margin-left: 0; }
.image-upload-tip { margin-top: 8px; color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.5; }
</style>

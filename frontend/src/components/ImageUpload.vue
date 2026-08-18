<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadFile, type UploadResponse } from '../api/file'
import { UploadFilled, Document } from '@element-plus/icons-vue'

const props = defineProps<{
  noteId?: number
}>()
const emit = defineEmits<{
  (e: 'uploaded', result: UploadResponse): void
}>()

const uploading = ref(false)

const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/jpg']
const DOC_EXTS = ['txt', 'md', 'markdown', 'pdf', 'docx']

const isSupported = (file: File) => {
  if (IMAGE_TYPES.includes(file.type)) return true
  const name = (file.name || '').toLowerCase()
  return DOC_EXTS.some((ext) => name.endsWith('.' + ext))
}

const beforeUpload = (file: File) => {
  if (!isSupported(file)) {
    ElMessage.error('支持图片（JPG/PNG）与文档（txt/md/pdf/docx）')
    return false
  }
  if (file.size / 1024 / 1024 > 20) {
    ElMessage.error('文件需小于20MB')
    return false
  }
  return true
}

const handleUpload = async (file: File) => {
  if (!file) return
  uploading.value = true
  try {
    const res = await uploadFile(file, props.noteId)
    emit('uploaded', res)
    const isDoc = !IMAGE_TYPES.includes(file.type)
    ElMessage.success(isDoc && res.extractedText ? '文档上传成功，文本已提取' : '上传成功')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

const onChange = (uploadFile: any) => {
  if (uploading.value) return
  if (uploadFile?.status && uploadFile.status !== 'ready') return
  const raw = uploadFile?.raw as File | undefined
  if (!raw) return
  handleUpload(raw)
}
</script>

<template>
  <el-upload
    class="upload-box"
    drag
    :auto-upload="false"
    :show-file-list="false"
    :before-upload="beforeUpload"
    :on-change="onChange"
    :accept="'.jpg,.jpeg,.png,.txt,.md,.markdown,.pdf,.docx'"
  >
    <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
    <div class="el-upload__text">拖拽或点击上传笔记素材</div>
    <div class="el-upload__tip">
      <span class="tip-item"><el-icon><Document /></el-icon> 文档：txt / md / pdf / docx（自动提取文本）</span>
      <span class="tip-item"><el-icon><Picture /></el-icon> 图片：JPG / PNG（OCR 识别）</span>
      <span class="tip-item">≤20MB</span>
    </div>
    <el-button type="primary" :loading="uploading" size="small" style="margin-top: 10px">开始上传</el-button>
  </el-upload>
</template>

<style scoped>
.upload-box {
  width: 100%;
}

.el-upload__tip {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  color: #909399;
  font-size: 12px;
  line-height: 1.7;
}

.tip-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
</style>

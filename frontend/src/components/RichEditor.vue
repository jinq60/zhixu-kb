<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import '@wangeditor/editor/dist/css/style.css'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import { ElMessage } from 'element-plus'
import { uploadFile } from '../api/file'

const props = defineProps<{
  modelValue?: string
  noteId?: number
  fillHeight?: boolean
}>()
const emits = defineEmits<{
  (e: 'update:modelValue', val: string): void
}>()

const editorRef = shallowRef<any>()
const hostRef = ref<HTMLElement | null>(null)
const fallbackFullscreen = ref(false)
const nativeFullscreen = ref(false)

const getFileContentPath = (src?: string | null): string | null => {
  if (!src) return null

  try {
    const url = new URL(src, window.location.origin)
    if (url.origin !== window.location.origin) return null
    const matched = url.pathname.match(/^\/api\/files\/(\d+)\/content$/)
    if (!matched) return null
    return `/api/files/${matched[1]}/content`
  } catch {
    const matched = src.match(/^\/api\/files\/(\d+)\/content(?:\?.*)?$/)
    return matched ? `/api/files/${matched[1]}/content` : null
  }
}

const rewriteImageUrls = (html: string, appendToken: boolean) => {
  if (!html || typeof document === 'undefined') return html

  const root = document.createElement('div')
  root.innerHTML = html

  root.querySelectorAll('img[src]').forEach((img) => {
    const current = img.getAttribute('src')
    const path = getFileContentPath(current)
    if (!path) return
    img.setAttribute('src', path)
  })

  return root.innerHTML
}

const toEditorHtml = (html?: string) => rewriteImageUrls(html || '', true)
const toStorageHtml = (html?: string) => rewriteImageUrls(html || '', false)

const valueHtml = ref(toEditorHtml(props.modelValue || ''))

watch(
  () => props.modelValue,
  (val) => {
    const next = toEditorHtml(val || '')
    if (next !== valueHtml.value) {
      valueHtml.value = next
    }
  }
)

const onChange = (editor: any) => {
  const html = editor.getHtml()
  valueHtml.value = html
  emits('update:modelValue', toStorageHtml(html))
}

const handleCreated = (editor: any) => {
  editorRef.value = editor
}

const editorStyle = computed(() => {
  if (nativeFullscreen.value || fallbackFullscreen.value) {
    return 'height: calc(100vh - 56px); overflow-y: auto'
  }
  if (props.fillHeight) {
    return 'height: calc(100% - 42px); overflow-y: auto'
  }
  return 'height: 400px; overflow-y: auto'
})

const syncNativeFullscreen = () => {
  const host = hostRef.value
  nativeFullscreen.value = !!host && document.fullscreenElement === host
  if (!nativeFullscreen.value && !fallbackFullscreen.value) {
    document.body.style.overflow = ''
  }
}

const enterFallbackFullscreen = () => {
  fallbackFullscreen.value = true
  document.body.style.overflow = 'hidden'
}

const exitFallbackFullscreen = () => {
  fallbackFullscreen.value = false
  if (!nativeFullscreen.value) {
    document.body.style.overflow = ''
  }
}

const enterFullscreen = async () => {
  const host = hostRef.value
  if (!host) return

  if (typeof host.requestFullscreen === 'function') {
    try {
      await host.requestFullscreen()
      return
    } catch {
      // Fallback to CSS fullscreen when browser fullscreen is unavailable.
    }
  }

  enterFallbackFullscreen()
}

const exitFullscreen = async () => {
  if (nativeFullscreen.value && typeof document.exitFullscreen === 'function') {
    try {
      await document.exitFullscreen()
    } catch {
      // Continue cleanup even if fullscreen API fails.
    }
  }

  exitFallbackFullscreen()
}

const isFullscreen = computed(() => nativeFullscreen.value || fallbackFullscreen.value)

const toggleFullscreen = async () => {
  if (isFullscreen.value) {
    await exitFullscreen()
    return
  }

  await enterFullscreen()
}

const onKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Escape' && fallbackFullscreen.value) {
    exitFallbackFullscreen()
  }
}

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
  document.addEventListener('fullscreenchange', syncNativeFullscreen)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  document.removeEventListener('fullscreenchange', syncNativeFullscreen)
  document.body.style.overflow = ''

  if (document.fullscreenElement === hostRef.value && typeof document.exitFullscreen === 'function') {
    document.exitFullscreen().catch(() => undefined)
  }

  const editor = editorRef.value
  if (editor) {
    editor.destroy()
  }
})

const toolbarConfig = {
  excludeKeys: ['fullScreen']
}

const editorConfig = computed(() => ({
  placeholder: '\u5f00\u59cb\u5199\u4f5c\u6216\u7c98\u8d34 OCR \u8bc6\u522b\u7ed3\u679c...',
  MENU_CONF: {
    uploadImage: {
      allowedFileTypes: ['image/*'],
      maxFileSize: 10 * 1024 * 1024,
      customUpload: async (
        file: File,
        insertFn: (url: string, alt?: string, href?: string) => void
      ) => {
        if (!props.noteId) {
          ElMessage.error('\u8bf7\u5148\u4fdd\u5b58\u7b14\u8bb0\u518d\u4e0a\u4f20\u6b63\u6587\u56fe\u7247')
          return
        }

        try {
          const result = await uploadFile(file, props.noteId)
          const saved = result.file
          const rawPath = `/api/files/${saved.id}/content`
          insertFn(rawPath, saved.originalName || file.name, rawPath)
        } catch (e: any) {
          ElMessage.error(e?.response?.data?.message || '\u6b63\u6587\u56fe\u7247\u4e0a\u4f20\u5931\u8d25')
        }
      }
    }
  }
}))
</script>

<template>
  <div
    ref="hostRef"
    class="rich-editor"
    :class="{ 'is-fallback-fullscreen': fallbackFullscreen, 'is-fill-height': fillHeight }"
  >
    <div class="toolbar-wrap">
      <Toolbar :editor="editorRef" :default-config="toolbarConfig" style="border-bottom: 1px solid #ebeef5" />
      <button class="fullscreen-btn" type="button" @click="toggleFullscreen">
        {{ isFullscreen ? '\u9000\u51fa\u5168\u5c4f' : '\u5168\u5c4f' }}
      </button>
    </div>

    <Editor
      v-model="valueHtml"
      :style="editorStyle"
      :default-config="editorConfig"
      @onCreated="handleCreated"
      @onChange="onChange"
    />
  </div>
</template>

<style scoped>
.rich-editor {
  position: relative;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fff;
}

.rich-editor.is-fill-height {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.toolbar-wrap {
  position: relative;
}

.fullscreen-btn {
  position: absolute;
  right: 10px;
  top: 8px;
  z-index: 3;
  height: 28px;
  padding: 0 10px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #fff;
  color: #606266;
  cursor: pointer;
  font-size: 12px;
}

.fullscreen-btn:hover {
  color: #409eff;
  border-color: #409eff;
}

.rich-editor.is-fallback-fullscreen {
  position: fixed;
  inset: 0;
  z-index: 3000;
  border-radius: 0;
  border: none;
}

:deep(button[data-menu-key='fullScreen']) {
  display: none !important;
}

:deep(.w-e-text h1) {
  font-size: 28px;
  margin: 24px 0 14px;
  color: #1a1a1a;
}

:deep(.w-e-text h2) {
  font-size: 24px;
  margin: 22px 0 12px;
  color: #1a1a1a;
}

:deep(.w-e-text h3) {
  font-size: 20px;
  margin: 18px 0 10px;
  color: #252525;
}

:deep(.w-e-text h4) {
  font-size: 18px;
  margin: 16px 0 8px;
  color: #303133;
}

:deep(.w-e-text h5),
:deep(.w-e-text h6) {
  font-size: 16px;
  margin: 14px 0 6px;
  color: #303133;
}

:deep(.w-e-text p) {
  line-height: 1.9;
}
</style>

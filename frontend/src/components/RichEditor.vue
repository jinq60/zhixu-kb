<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import '@wangeditor/editor/dist/css/style.css'
import { createEditor, createToolbar, type IDomEditor } from '@wangeditor/editor'
import { ElMessage } from 'element-plus'
import DOMPurify from 'dompurify'
import { uploadFile } from '../api/file'

const props = defineProps<{
  modelValue?: string
  noteId?: number
  fillHeight?: boolean
}>()
const emits = defineEmits<{
  (e: 'update:modelValue', val: string): void
}>()

const editorRef = ref<IDomEditor | null>(null)
const hostRef = ref<HTMLElement | null>(null)
const toolbarRef = ref<HTMLElement | null>(null)
const editorContainerRef = ref<HTMLElement | null>(null)
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

  // 先通过 DOMPurify 清洗，移除事件处理器等潜在 XSS 载体
  const safeHtml = DOMPurify.sanitize(html, {
    ALLOWED_TAGS: [
      'p', 'br', 'div', 'span', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
      'ul', 'ol', 'li', 'strong', 'b', 'em', 'i', 'u', 's', 'strike',
      'a', 'img', 'table', 'thead', 'tbody', 'tr', 'td', 'th', 'pre', 'code', 'blockquote'
    ],
    ALLOWED_ATTR: ['src', 'alt', 'href', 'title', 'target', 'class', 'style', 'data-*']
  })

  const root = document.createElement('div')
  root.innerHTML = safeHtml

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

const onChange = (editor?: IDomEditor) => {
  if (destroyed) return
  // 编辑器销毁过程中 wangeditor 可能触发无参 change 事件，忽略之
  if (!editor) return
  emits('update:modelValue', toStorageHtml(editor.getHtml()))
}

/** 应用内容：统一使用 setHtml（wangeditor 内部会处理大文档，避免自行分片破坏标签结构） */
let destroyed = false
const applyContent = (html: string) => {
  const editor = editorRef.value
  if (!editor || destroyed) return
  editor.setHtml(html)
}

watch(
  () => props.modelValue,
  (val) => {
    const html = toEditorHtml(val || '')
    const editor = editorRef.value
    if (editor && html !== editor.getHtml()) {
      applyContent(html)
    }
  },
  { immediate: true }
)

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
  const container = editorContainerRef.value
  if (!container) return
  const editor = createEditor({
    selector: container,
    html: '',
    config: editorConfig.value,
    mode: 'default'
  })
  editorRef.value = editor
  editor.on('change', onChange)
  if (toolbarRef.value) {
    createToolbar({ editor, selector: toolbarRef.value, config: toolbarConfig })
  }
  const initial = toEditorHtml(props.modelValue || '')
  if (initial) {
    applyContent(initial)
  }
  window.addEventListener('keydown', onKeydown)
  document.addEventListener('fullscreenchange', syncNativeFullscreen)
})

onBeforeUnmount(() => {
  destroyed = true
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
      <div ref="toolbarRef" class="rich-toolbar"></div>
      <button class="fullscreen-btn" type="button" @click="toggleFullscreen">
        {{ isFullscreen ? '\u9000\u51fa\u5168\u5c4f' : '\u5168\u5c4f' }}
      </button>
    </div>
    <div ref="editorContainerRef" class="rich-editor-body" :style="editorStyle"></div>
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

.rich-toolbar {
  position: relative;
}

.rich-editor-body {
  position: relative;
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

:deep(.rich-editor-body) {
  border-top: 1px solid #ebeef5;
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
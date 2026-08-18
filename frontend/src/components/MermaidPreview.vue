<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'

const props = defineProps<{
  code?: string
}>()

const containerRef = ref<HTMLElement | null>(null)
let renderSeq = 0

const escapeHtml = (raw: string) =>
  raw
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')

const renderDiagram = async () => {
  const container = containerRef.value
  if (!container) return

  const code = props.code?.trim()
  if (!code) {
    container.innerHTML = '<div class="empty">暂无导图数据</div>'
    return
  }

  try {
    const mermaid = (await import('mermaid')).default
    mermaid.initialize({
      startOnLoad: false,
      securityLevel: 'strict',
      theme: 'default'
    })

    const id = `note-mermaid-${renderSeq++}`
    const { svg, bindFunctions } = await mermaid.render(id, code)
    container.innerHTML = svg
    bindFunctions?.(container)
  } catch (error) {
    console.error('Render mermaid failed', error)
    container.innerHTML = `<pre class="fallback">${escapeHtml(code)}</pre>`
  }
}

watch(() => props.code, renderDiagram, { immediate: true })
onMounted(renderDiagram)
</script>

<template>
  <div ref="containerRef" class="mermaid-preview" />
</template>

<style scoped>
.mermaid-preview {
  min-height: 180px;
  padding: 12px;
  overflow-x: auto;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
}

.mermaid-preview :deep(svg) {
  max-width: 100%;
  height: auto;
}

.mermaid-preview :deep(.fallback) {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}

.mermaid-preview :deep(.empty) {
  color: #909399;
  font-size: 13px;
}
</style>

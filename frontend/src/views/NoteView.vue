<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Edit } from '@element-plus/icons-vue'
import DOMPurify from 'dompurify'
import {
  getReadableNote,
  getReadableNoteStructure,
  type PublicNoteDetail,
  type NoteStructureResponse
} from '../api/note'

interface TocItem {
  id: string
  title: string
  level: number
}

const route = useRoute()
const router = useRouter()
const noteId = computed(() => Number(route.params.id))

const loading = ref(false)
const exporting = ref(false)
const note = ref<PublicNoteDetail | null>(null)
const structure = ref<NoteStructureResponse | null>(null)
const exportRef = ref<HTMLElement | null>(null)
const activeTocId = ref('')
const renderedContentHtml = ref('')
const anchoredSectionIds = ref<number[]>([])

const keywordList = computed(() =>
  (note.value?.keywords || '')
    .split(/[,\uFF0C;]/)
    .map((item) => item.trim())
    .filter(Boolean)
)

const hasSummary = computed(() => Boolean(note.value?.summary?.trim()))
const hasKeywords = computed(() => keywordList.value.length > 0)

/** 正文区域最终 HTML：无可见内容时展示友好占位，避免空白/塌陷 */
const safeContentHtml = computed(() => {
  const html = renderedContentHtml.value
  if (!html || !html.trim()) {
    return '<p>\u6682\u65E0\u6B63\u6587\u5185\u5BB9</p>'
  }
  if (typeof document !== 'undefined') {
    const div = document.createElement('div')
    div.innerHTML = html
    if (!div.textContent?.trim() && !div.querySelector('img,table,br,hr')) {
      return '<p>\u6682\u65E0\u6B63\u6587\u5185\u5BB9</p>'
    }
  }
  return html
})

/** 目录优先使用笔记结构表的大纲（AI/手动整理），无结构时从正文标题兜底 */
const tocItems = ref<TocItem[]>([])

const escapeHtml = (raw: string) =>
  raw
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')

const normalizeImageUrls = (html: string) => {
  if (!html || typeof document === 'undefined') return html
  const root = document.createElement('div')
  root.innerHTML = html
  root.querySelectorAll('img[src]').forEach((img) => {
    const src = img.getAttribute('src')
    if (!src) return
    try {
      const url = new URL(src, window.location.origin)
      const matched = url.pathname.match(/^\/api\/files\/(\d+)\/content$/)
      if (!matched) return
      img.setAttribute('src', `/api/files/${matched[1]}/content`)
    } catch {
      const matched = src.match(/^\/api\/files\/(\d+)\/content(?:\?.*)?$/)
      if (!matched) return
      img.setAttribute('src', `/api/files/${matched[1]}/content`)
    }
  })
  return root.innerHTML
}

const textToHtml = (raw?: string) =>
  (raw || '')
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => `<p>${escapeHtml(line)}</p>`)
    .join('')

const looksLikeHtml = (value?: string) => /<[^>]+>/.test(value || '')

const buildRawContentHtml = () => {
  const content = note.value?.content?.trim()
  if (content) {
    return normalizeImageUrls(looksLikeHtml(content) ? content : textToHtml(content))
  }
  return normalizeImageUrls(textToHtml(note.value?.ocrText))
}

const normalizePlainText = (value?: string) =>
  (value || '')
    .replace(/\s+/g, ' ')
    .replace(/[,\uFF0C.\u3002\u00B7\u2014-]/g, '')
    .trim()
    .toLowerCase()

const buildRenderedContent = async () => {
  await nextTick()
  const html = buildRawContentHtml()
  if (!html || typeof document === 'undefined') {
    renderedContentHtml.value = html
    anchoredSectionIds.value = []
    tocItems.value = []
    return
  }

  const root = document.createElement('div')
  root.innerHTML = html

  // 先收集正文中真实存在的标题
  const headings = Array.from(root.querySelectorAll('h1,h2,h3,h4,h5,h6'))
    .filter((element) => normalizePlainText(element.textContent || '').length > 0)

  // 阅读页目录统一从正文真实标题生成，保证与正文 100% 对应、点击必跳转。
  // 结构表中的 AI/清洗大纲仅用于编辑页结构面板与导图，不再作为阅读页目录来源，
  // 避免标题截断、HTML 实体、AI 幻觉等导致目录与正文对不上的问题。
  const nextToc = headings.map((heading, index) => {
    const tag = heading.tagName.toLowerCase()
    const levelMatch = tag.match(/h([1-6])/)
    return {
      id: `toc-heading-${index + 1}`,
      title: (heading.textContent || '').trim(),
      level: Math.min(Math.max(levelMatch ? Number(levelMatch[1]) : 1, 1), 4)
    }
  })

  // 给所有标题设置锚点 ID
  headings.forEach((heading, index) => {
    heading.setAttribute('id', `toc-heading-${index + 1}`)
    heading.setAttribute('data-toc-anchor', 'true')
  })

  renderedContentHtml.value = DOMPurify.sanitize(root.innerHTML)
  anchoredSectionIds.value = []
  tocItems.value = nextToc
}

const PRINT_DOCUMENT_STYLES = `
  @page {
    size: A4 portrait;
    margin: 14mm 12mm;
  }

  * {
    box-sizing: border-box;
  }

  html,
  body {
    margin: 0;
    padding: 0;
    background: #ffffff;
    color: #303133;
    font: 14px/1.9 "Microsoft YaHei", "PingFang SC", sans-serif;
    -webkit-print-color-adjust: exact;
    print-color-adjust: exact;
  }

  body {
    padding: 0;
  }

  .paper {
    width: 100%;
    margin: 0 auto;
  }

  .paper-header {
    border-bottom: 1px solid #ebeef5;
    padding-bottom: 16px;
    margin-bottom: 20px;
  }

  .paper-header h1 {
    margin: 0;
    font-size: 32px;
    line-height: 1.35;
    color: #303133;
  }

  .meta {
    margin-top: 12px;
    display: flex;
    flex-wrap: wrap;
    gap: 8px 16px;
    color: #909399;
    font-size: 13px;
  }

  .summary,
  .keywords,
  .content-section {
    margin-top: 22px;
  }

  .summary h3,
  .keywords h3 {
    margin: 0 0 12px;
    color: #303133;
  }

  .summary p {
    margin: 0;
    white-space: pre-wrap;
  }

  .tag-list {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  .tag-item {
    display: inline-flex;
    align-items: center;
    min-height: 28px;
    padding: 0 10px;
    border: 1px solid #d9ecff;
    border-radius: 999px;
    color: #409eff;
    background: #ecf5ff;
    font-size: 12px;
    line-height: 1;
  }

  .content-section {
    border-top: 1px solid #ebeef5;
    padding-top: 22px;
  }

  .note-content {
    color: #303133;
    word-break: break-word;
  }

  .note-content h1 { font-size: 28px; margin: 24px 0 14px; color: #1a1a1a; }
  .note-content h2 { font-size: 24px; margin: 22px 0 12px; color: #1a1a1a; }
  .note-content h3 { font-size: 20px; margin: 18px 0 10px; color: #252525; }
  .note-content h4 { font-size: 18px; margin: 16px 0 8px; color: #303133; }
  .note-content h5,
  .note-content h6 { font-size: 16px; margin: 14px 0 6px; color: #303133; }

  .note-content p,
  .note-content li,
  .note-content blockquote {
    line-height: 1.9;
  }

  .note-content img {
    max-width: 100%;
    height: auto;
    display: block;
    margin: 12px auto;
  }

  .note-content table {
    width: 100%;
    border-collapse: collapse;
    table-layout: fixed;
  }

  .note-content th,
  .note-content td {
    border: 1px solid #dcdfe6;
    padding: 8px;
    vertical-align: top;
  }

  .paper-header,
  .summary,
  .keywords,
  .note-content h1,
  .note-content h2,
  .note-content h3,
  .note-content h4,
  .note-content h5,
  .note-content h6 {
    break-after: avoid-page;
    page-break-after: avoid;
  }

  .note-content blockquote,
  .note-content pre,
  .note-content table,
  .note-content img,
  .note-content figure {
    break-inside: avoid-page;
    page-break-inside: avoid;
  }

  .note-content p,
  .note-content li {
    orphans: 3;
    widows: 3;
  }
`

const buildPrintableDocument = () => {
  if (!note.value) return ''

  const title = escapeHtml(note.value.title || '\u7B14\u8BB0')
  const authorName = escapeHtml(note.value.authorName || '\u672A\u77E5\u7528\u6237')
  const categoryName = escapeHtml(note.value.categoryName || '\u672A\u5206\u7C7B')
  const updateTime = escapeHtml(note.value.updateTime || note.value.createTime || '-')
  const summaryHtml = hasSummary.value
    ? `
      <section class="summary">
        <h3>\u6458\u8981</h3>
        <p>${escapeHtml(note.value.summary || '')}</p>
      </section>
    `
    : ''
  const keywordsHtml = hasKeywords.value
    ? `
      <section class="keywords">
        <h3>\u5173\u952E\u8BCD</h3>
        <div class="tag-list">
          ${keywordList.value
            .map((item) => `<span class="tag-item">${escapeHtml(item)}</span>`)
            .join('')}
        </div>
      </section>
    `
    : ''
  const contentHtml = safeContentHtml.value

  return `<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>${title}</title>
    <style>${PRINT_DOCUMENT_STYLES}</style>
  </head>
  <body>
    <article class="paper">
      <header class="paper-header">
        <h1>${title}</h1>
        <div class="meta">
          <span>\u4F5C\u8005\uFF1A${authorName}</span>
          <span>\u5206\u7C7B\uFF1A${categoryName}</span>
          <span>\u66F4\u65B0\u65F6\u95F4\uFF1A${updateTime}</span>
        </div>
      </header>
      ${summaryHtml}
      ${keywordsHtml}
      <section class="content-section">
        <div class="note-content">${contentHtml}</div>
      </section>
    </article>
    <script>
      (() => {
        let printed = false
        const triggerPrint = () => {
          if (printed) return
          printed = true
          window.setTimeout(() => {
            window.focus()
            window.print()
          }, 120)
        }

        const imageTasks = Array.from(document.images)
          .filter((image) => !image.complete)
          .map(
            (image) =>
              new Promise((resolve) => {
                image.addEventListener('load', resolve, { once: true })
                image.addEventListener('error', resolve, { once: true })
              })
          )

        const fontTask =
          document.fonts && document.fonts.ready
            ? document.fonts.ready.catch(() => undefined)
            : Promise.resolve()

        Promise.all([Promise.all(imageTasks), fontTask]).finally(triggerPrint)
        window.setTimeout(triggerPrint, 1500)
        window.addEventListener('afterprint', () => window.close(), { once: true })
      })()
    <\/script>
  </body>
</html>`
}

const scrollToAnchor = async (id: string) => {
  await nextTick()
  const element = document.getElementById(id)
  if (!element) return
  activeTocId.value = id
  element.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

const updateActiveToc = () => {
  const ids = tocItems.value.map((item) => item.id)
  let current = ids[0] || ''

  for (const id of ids) {
    const element = document.getElementById(id)
    if (!element) continue
    const top = element.getBoundingClientRect().top
    if (top <= 140) {
      current = id
    } else {
      break
    }
  }

  activeTocId.value = current
}

const fetchNote = async () => {
  // 非法 id（如 /notes/view/abc）不发起 NaN 请求
  if (!Number.isFinite(noteId.value)) {
    ElMessage.error('笔记不存在')
    router.replace('/home')
    return
  }
  loading.value = true
  try {
    const [detail, readableStructure] = await Promise.all([
      getReadableNote(noteId.value),
      getReadableNoteStructure(noteId.value).catch(() => ({
        outline: [],
        sections: [],
        mermaid: ''
      }))
    ])
    note.value = detail
    structure.value = readableStructure
    await buildRenderedContent()
    await nextTick()
    updateActiveToc()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '\u52A0\u8F7D\u7B14\u8BB0\u5931\u8D25')
    router.push('/notes')
  } finally {
    loading.value = false
  }
}

const onExportPdf = async () => {
  if (typeof window === 'undefined' || !exportRef.value || !note.value) return

  exporting.value = true
  try {
    await nextTick()
    const printableDocument = buildPrintableDocument()
    if (!printableDocument) {
      throw new Error('\u65E0\u6CD5\u751F\u6210\u6253\u5370\u6587\u6863')
    }

    const printWindow = window.open('', '_blank', 'width=960,height=1200')
    if (!printWindow) {
      throw new Error('\u8BF7\u5141\u8BB8\u6D4F\u89C8\u5668\u5F39\u7A97\u540E\u91CD\u8BD5')
    }

    printWindow.document.open()
    printWindow.document.write(printableDocument)
    printWindow.document.close()
  } catch (e: any) {
    ElMessage.error(e?.message || '\u5BFC\u51FA PDF \u5931\u8D25')
  } finally {
    exporting.value = false
  }
}

onMounted(() => {
  fetchNote()
  window.addEventListener('scroll', updateActiveToc, { passive: true })
})

watch(noteId, () => {
  note.value = null
  structure.value = null
  renderedContentHtml.value = ''
  anchoredSectionIds.value = []
  fetchNote()
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', updateActiveToc)
})
</script>

<template>
  <div class="viewer-page" v-loading="loading">
    <div class="viewer-layout" v-if="note">
      <aside class="toc-panel">
        <div class="action-card">
          <div class="action-group">
            <el-button class="action-btn" text @click="router.push('/notes')">
              <el-icon><ArrowLeft /></el-icon>
              <span>&#x8FD4;&#x56DE;&#x5DE5;&#x4F5C;&#x53F0;</span>
            </el-button>
            <el-button v-if="note?.editable" class="action-btn" text @click="router.push(`/notes/${noteId}`)">
              <el-icon><Edit /></el-icon>
              <span>&#x8FD4;&#x56DE;&#x7F16;&#x8F91;</span>
            </el-button>
          </div>
          <div class="action-divider"></div>
          <div class="action-status">
            <el-tag :type="note?.published ? 'success' : 'info'" effect="light">
              {{ note?.published ? '\u5DF2\u53D1\u5E03' : '\u8349\u7A3F' }}
            </el-tag>
            <el-button type="primary" :loading="exporting" @click="onExportPdf">
              &#x5BFC;&#x51FA; PDF
            </el-button>
          </div>
        </div>

        <div class="toc-card">
          <div class="toc-title">&#x76EE;&#x5F55;</div>
          <div v-if="tocItems.length" class="toc-tree">
            <button
              v-for="item in tocItems"
              :key="item.id"
              class="toc-link"
              :class="{ active: activeTocId === item.id }"
              type="button"
              :style="{ paddingLeft: `${14 + (item.level - 1) * 16}px` }"
              @click="scrollToAnchor(item.id)"
            >
              {{ item.title }}
            </button>
          </div>
          <div v-else class="toc-empty">&#x6682;&#x65E0;&#x76EE;&#x5F55;</div>
        </div>
      </aside>

      <main ref="exportRef" class="paper">
        <header class="paper-header">
          <h1>{{ note.title }}</h1>
          <div class="meta">
            <span>&#x4F5C;&#x8005;&#xFF1A;{{ note.authorName || '\u672A\u77E5\u7528\u6237' }}</span>
            <span>&#x5206;&#x7C7B;&#xFF1A;{{ note.categoryName || '\u672A\u5206\u7C7B' }}</span>
            <span>&#x66F4;&#x65B0;&#x65F6;&#x95F4;&#xFF1A;{{ note.updateTime || note.createTime || '-' }}</span>
          </div>
        </header>

        <section v-if="hasSummary" id="note-summary" class="summary anchor-section">
          <h3>&#x6458;&#x8981;</h3>
          <p>{{ note.summary }}</p>
        </section>

        <section v-if="hasKeywords" id="note-keywords" class="keywords anchor-section">
          <h3>&#x5173;&#x952E;&#x8BCD;</h3>
          <div class="tag-list">
            <el-tag v-for="item in keywordList" :key="item" effect="plain">{{ item }}</el-tag>
          </div>
        </section>

        <section id="note-content" class="content-section anchor-section">
          <div class="note-content" v-html="safeContentHtml"></div>
        </section>
      </main>
    </div>
  </div>
</template>

<style scoped>
.viewer-page {
  margin: 0 auto;
  padding: 16px 20px;
}

.viewer-layout {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 12px;
  align-items: start;
}

.toc-panel {
  position: sticky;
  top: 76px;
  max-height: calc(100vh - 92px);
  display: flex;
  flex-direction: column;
  gap: 14px;
  overflow: hidden;
}

.action-card {
  flex-shrink: 0;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 12px;
}

.action-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.action-btn {
  justify-content: flex-start;
  width: 100%;
  padding: 8px 10px;
  color: #606266;
}

.action-btn .el-icon {
  margin-right: 6px;
}

.action-divider {
  height: 1px;
  background: #f0f2f5;
  margin: 12px 0;
}

.action-status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.toc-card {
  flex: 1;
  min-height: 0;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 14px 0;
  overflow-y: auto;
}

.toc-title {
  padding: 0 14px 10px;
  margin-bottom: 4px;
  font-size: 15px;
  font-weight: 700;
  color: #303133;
  border-bottom: 1px solid #f0f2f5;
}

.toc-tree {
  padding: 6px 0;
}

.toc-empty {
  padding: 20px 14px;
  text-align: center;
  color: #909399;
  font-size: 13px;
}

.toc-link {
  width: 100%;
  display: block;
  padding: 8px 14px;
  border: none;
  border-left: 2px solid transparent;
  background: transparent;
  text-align: left;
  color: #606266;
  cursor: pointer;
  line-height: 1.5;
  font-size: 13px;
  transition: all 0.18s ease;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.toc-link:hover {
  color: #2563eb;
  background: #f5faff;
}

.toc-link.active {
  color: #2563eb;
  background: #f0f7ff;
  border-left-color: #2563eb;
  font-weight: 600;
}

.paper {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 36px 40px;
  max-width: 100%;
}

.paper-header {
  border-bottom: 1px solid #ebeef5;
  padding-bottom: 16px;
  margin-bottom: 20px;
}

.paper-header h1 {
  margin: 0;
  font-size: 32px;
  color: #303133;
}

.meta {
  margin-top: 12px;
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  color: #909399;
  font-size: 13px;
}

.anchor-section {
  scroll-margin-top: 80px;
}

.summary,
.keywords,
.content-section {
  margin-top: 22px;
}

.summary h3,
.keywords h3 {
  margin-bottom: 12px;
  color: #303133;
}

.summary p {
  color: #606266;
  line-height: 1.9;
  white-space: pre-wrap;
}

.tag-list {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.content-section {
  border-top: 1px solid #ebeef5;
  padding-top: 22px;
  min-height: 160px;
}

.note-content {
  color: #303133;
  line-height: 1.9;
  word-break: break-word;
}

.note-content :deep(h1) {
  font-size: 28px;
  margin: 24px 0 14px;
  color: #1a1a1a;
}

.note-content :deep(h2) {
  font-size: 24px;
  margin: 22px 0 12px;
  color: #1a1a1a;
}

.note-content :deep(h3) {
  font-size: 20px;
  margin: 18px 0 10px;
  color: #252525;
}

.note-content :deep(h4) {
  font-size: 18px;
  margin: 16px 0 8px;
  color: #303133;
}

.note-content :deep(h5),
.note-content :deep(h6) {
  font-size: 16px;
  margin: 14px 0 6px;
  color: #303133;
}

.note-content :deep([data-toc-anchor='true']) {
  scroll-margin-top: 80px;
}

.note-content :deep(img) {
  max-width: 100%;
  height: auto;
  display: block;
  margin: 12px auto;
}

.note-content :deep(p),
.note-content :deep(li),
.note-content :deep(blockquote) {
  line-height: 1.9;
}

.note-content :deep(table) {
  width: 100%;
  border-collapse: collapse;
}

.note-content :deep(table td),
.note-content :deep(table th) {
  border: 1px solid #dcdfe6;
  padding: 8px;
}

@media (max-width: 1080px) {
  .viewer-layout {
    grid-template-columns: 1fr;
  }

  .toc-panel {
    position: static;
    max-height: none;
  }

  .paper {
    padding: 24px;
  }
}

</style>

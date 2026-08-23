<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createNote, deleteNote, getMyNoteStats, listNotes, searchNotes, type Note, type NoteStats } from '../api/note'
import { listCategories, type Category } from '../api/category'
import { useRouter } from 'vue-router'

const router = useRouter()
const loading = ref(false)
const statsLoading = ref(false)
const categories = ref<Category[]>([])
const createDialog = ref(false)
const newNoteTitle = ref('新建笔记')
const searchKeyword = ref('')
const stats = ref<NoteStats>({
  totalCount: 0,
  draftCount: 0,
  publishedCount: 0
})

const state = reactive({
  page: 1,
  size: 10,
  total: 0,
  categoryId: undefined as number | undefined,
  data: [] as Note[]
})

const selectedCategoryName = computed(
  () => categories.value.find((item) => item.id === state.categoryId)?.name || '全部分类'
)

const currentResultLabel = computed(() => {
  if (searchKeyword.value.trim() && state.categoryId) {
    return '当前为关键词 + 分类联合筛选结果'
  }
  if (searchKeyword.value.trim()) {
    return '当前为关键词搜索结果'
  }
  if (state.categoryId) {
    return '当前为分类筛选结果'
  }
  return '展示当前账号下的全部笔记'
})

const summaryCards = computed(() => [
  {
    label: '全部笔记',
    value: stats.value.totalCount,
    desc: '当前账号下的所有笔记'
  },
  {
    label: '草稿笔记',
    value: stats.value.draftCount,
    desc: '仍在整理、尚未发布'
  },
  {
    label: '已发布笔记',
    value: stats.value.publishedCount,
    desc: '已进入公开大厅'
  },
  {
    label: '当前结果',
    value: state.total,
    desc: currentResultLabel.value
  }
])

const stripHtml = (value?: string) =>
  (value || '')
    .replace(/<[^>]+>/g, ' ')
    .replace(/&nbsp;/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()

const buildExcerpt = (row: Note) => {
  const source = row.summary?.trim() || stripHtml(row.content) || row.keywords?.trim() || ''
  if (!source) return '暂无摘要，点击编辑后继续完善内容。'
  return source.length > 52 ? `${source.slice(0, 52)}...` : source
}

const formatKeywords = (value?: string) =>
  (value || '')
    .split(/[，,、；;]/)
    .map((item) => item.trim())
    .filter(Boolean)
    .slice(0, 3)

const fetchStats = async () => {
  statsLoading.value = true
  try {
    stats.value = await getMyNoteStats()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载统计信息失败')
  } finally {
    statsLoading.value = false
  }
}

const fetchCategories = async () => {
  try {
    categories.value = await listCategories()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载分类失败')
  }
}

const fetchData = async () => {
  loading.value = true
  try {
    const keyword = searchKeyword.value.trim()
    const res = keyword
      ? await searchNotes({ keyword, page: state.page, size: state.size, categoryId: state.categoryId })
      : await listNotes({ page: state.page, size: state.size, categoryId: state.categoryId })
    state.data = res.records || []
    state.total = res.total || 0

    if (!state.data.length && state.page > 1) {
      state.page -= 1
      await fetchData()
      return
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载笔记失败')
  } finally {
    loading.value = false
  }
}

const onPageChange = (page: number) => {
  state.page = page
  fetchData()
}

const onSearch = () => {
  state.page = 1
  fetchData()
}

const onCategoryChange = () => {
  state.page = 1
  fetchData()
}

const onReset = () => {
  searchKeyword.value = ''
  state.categoryId = undefined
  state.page = 1
  fetchData()
}

const onDelete = async (row: Note) => {
  try {
    await ElMessageBox.confirm(`确定删除笔记“${row.title}”吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    await deleteNote(row.id!)
    await Promise.all([fetchData(), fetchStats()])
    ElMessage.success('删除成功')
  } catch (e: any) {
    if (e === 'cancel' || e === 'close' || e?.action === 'cancel' || e?.action === 'close') {
      return
    }
    ElMessage.error(e?.response?.data?.message || '删除失败')
  }
}

const onCreate = async () => {
  try {
    const title = newNoteTitle.value.trim() || '未命名笔记'
    createDialog.value = false
    const note = await createNote({ title, status: 0 })
    if (!note?.id) {
      ElMessage.error('创建笔记失败：未返回笔记 ID')
      return
    }
    ElMessage.success('已创建草稿')
    await router.push(`/notes/edit/${note.id}`)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '创建失败')
  }
}

onMounted(() => {
  fetchCategories()
  fetchStats()
  fetchData()
})
</script>

<template>
  <div class="console-page">
    <section class="console-hero">
      <div>
        <div class="eyebrow">Note Console</div>
        <h1>笔记管理</h1>
        <p>
          在这里统一管理草稿、已发布内容和公开查看入口。页面重点是快速检索、筛选、进入编辑和查看发布状态。
        </p>
      </div>

      <div class="hero-actions">
        <el-button type="primary" size="large" @click="createDialog = true">新建笔记</el-button>
        <el-button size="large" @click="router.push('/categories')">分类管理</el-button>
      </div>
    </section>

    <section class="stats-grid" v-loading="statsLoading">
      <article v-for="item in summaryCards" :key="item.label" class="stat-card">
        <div class="stat-label">{{ item.label }}</div>
        <div class="stat-value">{{ item.value }}</div>
        <div class="stat-desc">{{ item.desc }}</div>
      </article>
    </section>

    <section class="panel toolbar-panel">
      <div class="toolbar-heading">
        <div>
          <h2>筛选与搜索</h2>
          <p>当前分类：{{ selectedCategoryName }}</p>
        </div>
      </div>

      <div class="toolbar">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索标题、摘要、正文、关键词"
          clearable
          @keyup.enter="onSearch"
          @clear="onSearch"
        />
        <el-select
          v-model="state.categoryId"
          placeholder="按分类筛选"
          clearable
          @change="onCategoryChange"
        >
          <el-option v-for="c in categories" :key="c.id ?? c.name" :label="c.name" :value="c.id!" />
        </el-select>
        <el-button type="primary" @click="onSearch">搜索</el-button>
        <el-button @click="onReset">重置</el-button>
      </div>
    </section>

    <section class="panel table-panel">
      <div class="table-header">
        <div>
          <h2>笔记列表</h2>
          <p>{{ currentResultLabel }}</p>
        </div>
        <div class="table-count">共 {{ state.total }} 条</div>
      </div>

      <el-table :data="state.data" stripe v-loading="loading">
        <el-table-column label="笔记信息" min-width="360">
          <template #default="{ row }">
            <div class="note-cell">
              <div class="note-title">{{ row.title }}</div>
              <div class="note-excerpt">{{ buildExcerpt(row) }}</div>
              <div class="note-keywords" v-if="formatKeywords(row.keywords).length">
                <el-tag
                  v-for="keyword in formatKeywords(row.keywords)"
                  :key="keyword"
                  size="small"
                  effect="plain"
                >
                  {{ keyword }}
                </el-tag>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="分类" min-width="180">
          <template #default="{ row }">
            <span>{{ categories.find((c) => c.id === row.categoryId)?.name || '未分类' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="更新时间" prop="updateTime" min-width="180" />

        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="light">
              {{ row.status === 1 ? '已发布' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button type="primary" link @click="router.push(`/notes/view/${row.id}`)">查看</el-button>
              <el-button type="primary" link @click="router.push(`/notes/edit/${row.id}`)">编辑</el-button>
              <el-button type="danger" link @click="onDelete(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager" v-if="state.total > state.size">
        <el-pagination
          background
          layout="prev, pager, next, jumper"
          :current-page="state.page"
          :page-size="state.size"
          :total="state.total"
          @current-change="onPageChange"
        />
      </div>
    </section>

    <el-dialog v-model="createDialog" title="新建笔记" width="420px">
      <el-input v-model="newNoteTitle" placeholder="输入笔记标题" />
      <div class="dialog-tip">新建后会先保存为草稿，你可以在编辑页继续整理后再发布。</div>
      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" @click="onCreate">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.eyebrow {
  background: rgba(64, 158, 255, 0.12);
  color: #409eff;
}

.note-cell {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.note-title {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
}

.note-excerpt {
  color: #606266;
  line-height: 1.7;
  font-size: 13px;
}

.note-keywords,
.row-actions {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}

.dialog-tip {
  margin-top: 12px;
  font-size: 13px;
  color: #606266;
  line-height: 1.7;
}

:deep(.el-table) {
  border-radius: 12px;
  overflow: hidden;
}

:deep(.el-table th.el-table__cell) {
  background: #f8fafc;
  color: #475467;
}
</style>

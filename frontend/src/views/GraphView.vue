<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { listNotes, type Note } from '../api/note'
import { listCategories, type Category } from '../api/category'
import {
  getNoteGraph,
  buildNoteGraph,
  deleteNoteGraph,
  getCategoryGraph,
  buildCategoryGraph,
  getGlobalGraph,
  buildGlobalGraph,
  searchGraph,
  type GraphData,
  type GraphNode
} from '../api/graph'
import KnowledgeGraph from '../components/KnowledgeGraph.vue'

type GraphMode = 'note' | 'category' | 'global'

const notes = ref<Note[]>([])
const categories = ref<Category[]>([])
// note ID 为雪花 ID，保持字符串；分类 ID 仍为自增数值
const selectedNoteId = ref<string | null>(null)
const selectedCategoryId = ref<number | null>(null)
const mode = ref<GraphMode>('category')
const graphData = ref<GraphData | null>(null)
const loading = ref(false)
const building = ref(false)
const searchKeyword = ref('')
const searchResults = ref<GraphNode[]>([])
const searching = ref(false)

const loadNotes = async () => {
  try {
    const result = await listNotes({ page: 1, size: 1000 })
    notes.value = result.records
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载笔记列表失败')
    notes.value = []
  }
}

const loadCategories = async () => {
  try {
    categories.value = await listCategories()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载分类失败')
    categories.value = []
  }
}

const selectNote = async (noteId: string) => {
  selectedNoteId.value = noteId
  await loadNoteGraph(noteId)
}

const loadNoteGraph = async (noteId: string | number) => {
  loading.value = true
  try {
    graphData.value = await getNoteGraph(noteId)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载图谱失败')
  } finally {
    loading.value = false
  }
}

const handleBuildNote = async () => {
  if (!selectedNoteId.value) return
  building.value = true
  try {
    const result = await buildNoteGraph(selectedNoteId.value)
    ElMessage[result.entityCount > 0 ? 'success' : 'warning'](result.message)
    await loadNoteGraph(selectedNoteId.value)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '图谱构建失败')
  } finally {
    building.value = false
  }
}

const handleDeleteNote = async () => {
  if (!selectedNoteId.value) return
  try {
    await deleteNoteGraph(selectedNoteId.value)
    ElMessage.success('图谱已删除')
    graphData.value = null
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '图谱删除失败')
  }
}

const handleLoadByMode = async () => {
  if (mode.value === 'note') {
    if (selectedNoteId.value) await loadNoteGraph(selectedNoteId.value)
  } else if (mode.value === 'category') {
    if (selectedCategoryId.value) await loadCategoryGraph(selectedCategoryId.value)
  } else {
    await loadGlobalGraph()
  }
}

const loadCategoryGraph = async (categoryId: number) => {
  loading.value = true
  try {
    graphData.value = await getCategoryGraph(categoryId)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载分类图谱失败')
  } finally {
    loading.value = false
  }
}

const handleBuildCategory = async () => {
  if (!selectedCategoryId.value) return
  building.value = true
  try {
    const result = await buildCategoryGraph(selectedCategoryId.value)
    ElMessage[result.entityCount > 0 ? 'success' : 'warning'](result.message)
    await loadCategoryGraph(selectedCategoryId.value)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '分类图谱构建失败')
  } finally {
    building.value = false
  }
}

const loadGlobalGraph = async () => {
  loading.value = true
  try {
    graphData.value = await getGlobalGraph()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载全局图谱失败')
  } finally {
    loading.value = false
  }
}

const handleBuildGlobal = async () => {
  building.value = true
  try {
    const result = await buildGlobalGraph()
    ElMessage[result.entityCount > 0 ? 'success' : 'warning'](result.message)
    await loadGlobalGraph()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '全局图谱构建失败')
  } finally {
    building.value = false
  }
}

const handleSearch = async () => {
  const keyword = searchKeyword.value.trim()
  if (!keyword) {
    searchResults.value = []
    return
  }
  searching.value = true
  try {
    searchResults.value = await searchGraph(keyword)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '图谱搜索失败')
  } finally {
    searching.value = false
  }
}

watch(mode, () => {
  graphData.value = null
  searchResults.value = []
  searchKeyword.value = ''
})

onMounted(async () => {
  await Promise.all([loadNotes(), loadCategories()])
})
</script>

<template>
  <div class="graph-page">
    <div class="page-header">
      <h2>知识图谱</h2>
      <p>从笔记中抽取实体与关系，构建个人知识图谱（Neo4j）</p>
    </div>

    <div class="graph-grid">
      <aside class="graph-sidebar">
        <el-card shadow="never" class="panel-card">
          <template #header><span class="card-title">图谱视角</span></template>

          <div class="sidebar-body">
            <el-radio-group v-model="mode" size="default" class="mode-tabs">
              <el-radio-button label="category">分类体系</el-radio-button>
              <el-radio-button label="note">单篇笔记</el-radio-button>
              <el-radio-button label="global">全局体系</el-radio-button>
            </el-radio-group>

            <div v-if="mode === 'note'" class="control-group">
              <el-select
                v-model="selectedNoteId"
                placeholder="选择要查看的笔记"
                filterable
                class="full-width"
                @change="selectNote"
              >
                <el-option v-for="note in notes" :key="note.id" :label="note.title" :value="String(note.id)" />
              </el-select>

              <div class="graph-actions">
                <el-button type="primary" :loading="building" :disabled="!selectedNoteId" class="full-btn" @click="handleBuildNote">
                  构建 / 更新
                </el-button>
                <el-button type="danger" plain :disabled="!selectedNoteId" class="full-btn" @click="handleDeleteNote">
                  删除图谱
                </el-button>
              </div>
            </div>

            <div v-else-if="mode === 'category'" class="control-group">
              <el-select
                v-model="selectedCategoryId"
                placeholder="选择要整理的知识分类"
                filterable
                class="full-width"
                @change="(id: number) => loadCategoryGraph(id)"
              >
                <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="Number(c.id)" />
              </el-select>
              <p class="scope-tip">将该分类下的所有笔记合并抽取，交叉知识点会自动关联。</p>
              <el-button type="primary" :loading="building" :disabled="!selectedCategoryId" class="full-btn" @click="handleBuildCategory">
                构建 / 更新分类体系
              </el-button>
            </div>

            <div v-else class="control-group">
              <p class="scope-tip">基于当前账号下所有笔记，抽取并合并成完整的个人知识体系。</p>
              <el-button type="primary" :loading="building" class="full-btn" @click="handleBuildGlobal">
                构建 / 更新全局体系
              </el-button>
            </div>

            <el-divider />

            <div class="search-box">
              <el-input v-model="searchKeyword" placeholder="全局实体搜索" clearable @keyup.enter="handleSearch">
                <template #append>
                  <el-button :loading="searching" @click="handleSearch">搜索</el-button>
                </template>
              </el-input>
              <div v-if="searchResults.length" class="search-results">
                <div v-for="node in searchResults" :key="node.name" class="search-item">
                  <el-tag size="small" type="info">{{ node.type }}</el-tag>
                  <span class="search-name">{{ node.name }}</span>
                </div>
              </div>
              <el-empty v-else-if="searchKeyword && !searching" description="无匹配实体" :image-size="60" />
            </div>
          </div>
        </el-card>
      </aside>

      <section class="graph-main">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="graph-head">
              <span class="card-title">{{ graphData?.noteTitle || '图谱预览' }}</span>
              <span v-if="graphData" class="graph-meta">
                {{ graphData.nodes.length }} 个节点 · {{ graphData.edges.length }} 条关系
              </span>
            </div>
          </template>
          <div class="graph-canvas" v-loading="loading">
            <KnowledgeGraph :data="graphData" height="100%" />
          </div>
        </el-card>
      </section>
    </div>
  </div>
</template>

<style scoped>
.graph-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header h2 {
  font-size: 20px;
  color: #111827;
}

.page-header p {
  margin-top: 6px;
  color: #909399;
  font-size: 13px;
}

.graph-grid {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 16px;
  align-items: stretch;
}

.graph-sidebar,
.graph-main {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.panel-card {
  border-radius: 14px;
  border: 1px solid #ebeef5;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.panel-card :deep(.el-card__body) {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.card-title {
  font-weight: 600;
  color: #111827;
}

.sidebar-body {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
}

.mode-tabs {
  display: flex;
  width: 100%;
}

.mode-tabs :deep(.el-radio-button) {
  flex: 1;
}

.mode-tabs :deep(.el-radio-button__inner) {
  width: 100%;
  padding: 8px 4px;
  font-size: 13px;
  text-align: center;
  white-space: nowrap;
}

.control-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 100%;
}

.control-group > * {
  width: 100%;
}

.scope-tip {
  margin: 0;
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}

.full-width {
  width: 100%;
}

.graph-actions {
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
  width: 100%;
}

.full-btn {
  width: 100%;
  margin: 0 !important;
}

.search-box {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
}

.search-results {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 260px;
  overflow-y: auto;
}

.search-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  background: #f9fafb;
  border-radius: 8px;
}

.search-name {
  font-size: 13px;
  color: #303133;
  word-break: break-all;
}

.graph-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.graph-meta {
  color: #909399;
  font-size: 13px;
  flex-shrink: 0;
}

.graph-canvas {
  flex: 1 1 auto;
  min-height: 360px;
  height: 560px;
}

@media (max-width: 1100px) {
  .graph-grid {
    grid-template-columns: 1fr;
  }

  .graph-canvas {
    height: 420px;
  }

  .graph-sidebar {
    max-width: none;
  }
}

@media (max-width: 768px) {
  .graph-canvas {
    height: 320px;
  }
}
</style>

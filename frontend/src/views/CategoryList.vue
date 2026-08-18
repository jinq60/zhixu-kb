<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createCategory, deleteCategory, pageCategories, updateCategory, type Category } from '../api/category'

const loading = ref(false)
const dialogVisible = ref(false)
const editing = ref<Category | null>(null)
const keyword = ref('')

const state = reactive({
  page: 1,
  size: 10,
  total: 0,
  data: [] as Category[]
})

const form = reactive<Category>({
  name: '',
  description: '',
  sortOrder: 0
})

const stats = computed(() => [
  {
    label: '当前页条数',
    value: state.data.length,
    desc: '当前页展示的分类条目'
  },
  {
    label: '总数',
    value: state.total,
    desc: keyword.value.trim() ? '关键词筛选后的分类' : '全部分类'
  }
])

const fetchData = async () => {
  loading.value = true
  try {
    const res = await pageCategories({ page: state.page, size: state.size, keyword: keyword.value.trim() || undefined })
    state.data = res.records || []
    state.total = res.total || 0

    if (!state.data.length && state.page > 1) {
      state.page -= 1
      await fetchData()
      return
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载分类失败')
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

const onCreate = () => {
  editing.value = null
  Object.assign(form, { name: '', description: '', sortOrder: 0 })
  dialogVisible.value = true
}

const onEdit = (item: Category) => {
  editing.value = item
  Object.assign(form, {
    name: item.name,
    description: item.description || '',
    sortOrder: item.sortOrder || 0
  })
  dialogVisible.value = true
}

const onSave = async () => {
  const name = form.name?.trim()
  if (!name) {
    ElMessage.warning('分类名称不能为空')
    return
  }

  try {
    const payload: Category = {
      name,
      description: form.description?.trim() || '',
      sortOrder: Number(form.sortOrder || 0)
    }

    if (editing.value?.id) {
      await updateCategory(editing.value.id, payload)
      ElMessage.success('分类已更新')
    } else {
      await createCategory(payload)
      ElMessage.success('分类已创建')
    }

    dialogVisible.value = false
    await fetchData()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存分类失败')
  }
}

const onDelete = async (item: Category) => {
  try {
    await ElMessageBox.confirm(
      `删除分类“${item.name}”后，该分类下的笔记会自动转为未分类，是否继续？`,
      '删除确认',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消'
      }
    )
    await deleteCategory(item.id!)
    await fetchData()
    ElMessage.success('分类已删除')
  } catch (e: any) {
    if (e === 'cancel' || e === 'close' || e?.action === 'cancel' || e?.action === 'close') {
      return
    }
    ElMessage.error(e?.response?.data?.message || '删除分类失败')
  }
}

onMounted(fetchData)
</script>

<template>
  <div class="console-page">
    <section class="console-hero">
      <div>
        <div class="eyebrow">Category Console</div>
        <h1>分类管理</h1>
        <p>
          维护笔记分类名称、说明与排序。
        </p>
      </div>

      <div class="hero-actions">
        <el-button type="primary" size="large" @click="onCreate">新建分类</el-button>
      </div>
    </section>

    <section class="stats-grid">
      <article v-for="item in stats" :key="item.label" class="stat-card">
        <div class="stat-label">{{ item.label }}</div>
        <div class="stat-value">{{ item.value }}</div>
        <div class="stat-desc">{{ item.desc }}</div>
      </article>
    </section>

    <section class="panel toolbar-panel">
      <div class="toolbar-heading">
        <div>
          <h2>筛选与维护</h2>
          <p>支持按名称或描述快速筛选分类。</p>
        </div>
      </div>

      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索分类名称或描述"
          clearable
          @keyup.enter="onSearch"
          @clear="onSearch"
        />
        <el-button type="primary" @click="onSearch">搜索</el-button>
        <el-button @click="keyword = ''; onSearch()">重置</el-button>
      </div>
    </section>

    <section class="panel table-panel">
      <div class="table-header">
        <div>
          <h2>分类列表</h2>
          <p>用于组织笔记内容，供笔记管理页快速筛选。</p>
        </div>
        <div class="table-count">共 {{ state.total }} 条</div>
      </div>

      <el-table :data="state.data" stripe v-loading="loading">
        <el-table-column label="分类信息" min-width="320">
          <template #default="{ row }">
            <div class="category-cell">
              <div class="category-name">{{ row.name }}</div>
              <div class="category-desc">{{ row.description || '暂无描述，可在编辑时补充用途说明。' }}</div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="排序值" width="120" align="center">
          <template #default="{ row }">
            <el-tag effect="plain">{{ row.sortOrder || 0 }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button type="primary" link @click="onEdit(row)">编辑</el-button>
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

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑分类' : '新建分类'" width="460px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="form.name" placeholder="输入分类名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="4" placeholder="输入分类用途说明" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :step="1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.eyebrow {
  background: rgba(103, 194, 58, 0.12);
  color: #67c23a;
}

.category-cell {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.category-name {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
}

.category-desc {
  color: #606266;
  line-height: 1.7;
  font-size: 13px;
}

.row-actions {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}

:deep(.el-table) {
  border-radius: 12px;
  overflow: hidden;
}

:deep(.el-table th.el-table__cell) {
  background: #f8fafc;
  color: #475467;
}

.pager {
  justify-content: center;
}
</style>

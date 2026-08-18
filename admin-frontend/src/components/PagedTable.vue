<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    data: any[]
    loading?: boolean
    pageSize?: number
    pageSizes?: number[]
    maxHeight?: string
  }>(),
  {
    loading: false,
    pageSize: 10,
    pageSizes: () => [10, 20, 50],
    maxHeight: ''
  }
)

const emit = defineEmits<{
  (e: 'page-change', page: number, size: number): void
}>()

const page = ref(1)
const size = ref(props.pageSize)

watch(
  () => props.data,
  () => {
    // 数据变化时当前页超出范围则回退
    const maxPage = Math.max(1, Math.ceil(props.data.length / size.value))
    if (page.value > maxPage) {
      page.value = maxPage
    }
  }
)

// 前端分页切片
const pagedData = computed(() => {
  const start = (page.value - 1) * size.value
  return props.data.slice(start, start + size.value)
})

const handleCurrentChange = (p: number) => {
  page.value = p
  emit('page-change', p, size.value)
}

const handleSizeChange = (s: number) => {
  size.value = s
  page.value = 1
  emit('page-change', 1, s)
}
</script>

<template>
  <div class="paged-table">
    <el-table
      :data="pagedData"
      v-loading="loading"
      stripe
      :max-height="maxHeight || undefined"
      :row-key="undefined"
    >
      <slot />
      <template #empty>
        <slot name="empty" />
      </template>
    </el-table>

    <div v-if="data.length > pageSize" class="pager-wrap">
      <el-pagination
        layout="total, sizes, prev, pager, next"
        :total="data.length"
        :page-sizes="pageSizes"
        :current-page="page"
        :page-size="size"
        background
        @current-change="handleCurrentChange"
        @size-change="handleSizeChange"
      />
    </div>
  </div>
</template>

<style scoped>
.paged-table {
  width: 100%;
}

.pager-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
</style>

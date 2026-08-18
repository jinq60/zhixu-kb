<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import type { GraphData } from '../api/graph'

const props = defineProps<{
  data: GraphData | null
  height?: string
}>()

const container = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null
let resizeObserver: ResizeObserver | null = null

const escapeHtml = (raw: string): string =>
  raw
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')

const TYPE_COLORS: Record<string, string> = {
  笔记: '#2563eb',
  概念: '#409eff',
  法律: '#f56c6c',
  人物: '#67c23a',
  机构: '#e6a23c',
  方法: '#909399',
  其他: '#b0b8c4'
}

const showChart = computed(
  () => !!props.data && !!props.data.nodes && props.data.nodes.length > 0
)

const buildOption = (data: GraphData) => {
  const nodes = data.nodes.map((node) => ({
    id: node.id || node.name,
    name: node.name,
    symbolSize: node.type === '笔记' ? 34 : node.type === '法律' ? 46 : node.type === '概念' ? 30 : 24,
    itemStyle: {
      color: TYPE_COLORS[node.type] || '#b0b8c4'
    },
    category: node.type || '其他',
    description: node.description || node.type || ''
  }))
  const edges = data.edges.map((edge) => ({
    source: edge.source,
    target: edge.target,
    label: {
      show: true,
      formatter: edge.relation,
      fontSize: 10,
      color: '#8c8c8c'
    }
  }))
  const categories = Array.from(new Set(nodes.map((n) => n.category))).map((name) => ({ name }))

  return {
    tooltip: {
      formatter: (params: any) => {
        if (params.dataType === 'node') {
          const name = escapeHtml(String(params.data.name || ''))
          const category = escapeHtml(String(params.data.category || ''))
          const description = escapeHtml(String(params.data.description || ''))
          return `${name}<br/><span style="color:#999">${category} · ${description}</span>`
        }
        const source = escapeHtml(String(params.data.source || ''))
        const target = escapeHtml(String(params.data.target || ''))
        const relation = escapeHtml(String(params.data.label?.formatter || ''))
        return `${source} —${relation}→ ${target}`
      }
    },
    legend: {
      data: categories.map((c) => c.name),
      bottom: 0,
      textStyle: { fontSize: 11 }
    },
    series: [
      {
        type: 'graph',
        layout: 'force',
        data: nodes,
        links: edges,
        categories,
        roam: true,
        draggable: true,
        force: {
          repulsion: 320,
          edgeLength: [60, 140],
          gravity: 0.08
        },
        label: {
          show: true,
          position: 'bottom',
          fontSize: 11,
          color: '#333',
          formatter: (params: any) =>
            params.data.name.length > 8 ? params.data.name.slice(0, 8) + '…' : params.data.name
        },
        lineStyle: {
          color: '#a8b2c1',
          width: 1.2,
          curveness: 0.15
        },
        emphasis: {
          focus: 'adjacency',
          lineStyle: { width: 3 }
        }
      }
    ]
  }
}

const render = async () => {
  if (!showChart.value) {
    return
  }
  // 等待 v-if 分支真实挂载后再初始化，避免容器未就绪
  await nextTick()
  if (!container.value) {
    return
  }
  if (!chart) {
    chart = echarts.init(container.value)
  }
  chart.setOption(buildOption(props.data!), true)
}

const handleResize = () => chart?.resize()

onMounted(() => {
  render()
  window.addEventListener('resize', handleResize)
  // 容器从隐藏（tab 未激活）变为可见时自动重绘
  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver((entries) => {
      for (const entry of entries) {
        if (entry.contentRect.width > 0 || entry.contentRect.height > 0) {
          handleResize()
        }
      }
    })
    const el = container.value
    if (el) {
      resizeObserver.observe(el)
    }
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  resizeObserver?.disconnect()
  resizeObserver = null
  chart?.dispose()
  chart = null
})

watch(() => props.data, render)
</script>

<template>
  <!-- echarts 容器与 Vue 管理的占位分支分离：容器内部无 Vue 子节点，
       避免 echarts 插入 canvas 与 Vue 锚点管理冲突 -->
  <div v-if="showChart" ref="container" class="knowledge-graph" :style="{ height: height || '460px' }"></div>
  <el-empty
    v-else
    description="暂无图谱数据，请先在笔记中构建知识图谱"
    :image-size="80"
  />
</template>

<style scoped>
.knowledge-graph {
  width: 100%;
}
</style>

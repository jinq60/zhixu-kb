<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
// ECharts 按需引入：仅注册知识图谱用到的模块（graph 图 + tooltip/legend + canvas 渲染），
// 相比整包 import * as echarts 显著减小构建包体
import { init, use } from 'echarts/core'
import type { EChartsType } from 'echarts/core'
import { GraphChart } from 'echarts/charts'
import { LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { GraphData, GraphEdge, GraphNode } from '../api/graph'
import { View, Hide, Pointer, Operation, FullScreen, Close } from '@element-plus/icons-vue'

use([GraphChart, TooltipComponent, LegendComponent, CanvasRenderer])

const props = defineProps<{
  data: GraphData | null
  height?: string
}>()

const container = ref<HTMLDivElement>()
const fullscreenContainer = ref<HTMLDivElement>()
let chart: EChartsType | null = null
let fullscreenChart: EChartsType | null = null
let resizeObserver: ResizeObserver | null = null
let fullscreenResizeObserver: ResizeObserver | null = null

/** 交互控制状态 */
const showEdgeLabels = ref(false)
const showAllLabels = ref(false)
const focusMode = ref(false)
const isFullscreen = ref(false)

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

const computeDegrees = (nodes: GraphNode[], edges: GraphEdge[]) => {
  const degrees = new Map<string, number>()
  nodes.forEach((n) => degrees.set(n.id || n.name, 0))
  edges.forEach((e) => {
    degrees.set(e.source, (degrees.get(e.source) || 0) + 1)
    degrees.set(e.target, (degrees.get(e.target) || 0) + 1)
  })
  return degrees
}

/** 聚焦模式：大图仅保留核心节点，减少视觉混乱 */
const filteredData = computed(() => {
  let nodes = props.data?.nodes || []
  const edges = props.data?.edges || []
  const degrees = computeDegrees(nodes, edges)

  if (focusMode.value && nodes.length > 40) {
    const sorted = [...nodes].sort(
      (a, b) => (degrees.get(b.id || b.name) || 0) - (degrees.get(a.id || a.name) || 0)
    )
    const keepCount = Math.max(24, Math.floor(nodes.length * 0.4))
    const kept = new Set(sorted.slice(0, keepCount).map((n) => n.id || n.name))
    nodes = nodes.filter((n) => kept.has(n.id || n.name))
  }

  return { nodes, edges, degrees }
})

const buildOption = () => {
  const { nodes, edges, degrees } = filteredData.value
  const categories = Array.from(new Set(nodes.map((n) => n.type))).map((name) => ({ name }))
  const isLarge = nodes.length > 80

  const eNodes = nodes.map((node) => {
    const id = node.id || node.name
    const degree = degrees.get(id) || 0
    const baseSize = node.type === '笔记' ? 36 : node.type === '法律' ? 44 : node.type === '概念' ? 30 : 24
    const size = baseSize + Math.min(degree * 2.2, 30)
    const showLabel =
      showAllLabels.value || degree >= 2 || node.type === '笔记' || node.type === '法律' || nodes.length <= 30

    return {
      id,
      name: node.name,
      symbolSize: size,
      itemStyle: {
        color: TYPE_COLORS[node.type] || '#b0b8c4',
        borderColor: '#fff',
        borderWidth: 1.5,
        shadowBlur: degree >= 4 ? 12 : 0,
        shadowColor: 'rgba(0,0,0,0.15)'
      },
      category: node.type || '其他',
      description: node.description || node.type || '',
      fullName: node.name,
      label: {
        show: showLabel,
        formatter: node.name.length > 10 ? node.name.slice(0, 10) + '…' : node.name
      },
      emphasis: {
        label: {
          show: true,
          formatter: node.name
        }
      },
      degree
    }
  })

  const eEdges = edges.map((edge) => ({
    source: edge.source,
    target: edge.target,
    value: edge.relation,
    label: {
      show: showEdgeLabels.value,
      formatter: edge.relation,
      fontSize: 10,
      color: '#8c8c8c'
    }
  }))

  return {
    tooltip: {
      // 固定到画布右上角，避免跟随鼠标遮挡节点
      position: (point: number[], _params: any, _dom: any, _rect: any, size: any) => {
        const viewW = size.viewSize[0]
        const contentW = size.contentSize[0]
        return [viewW - contentW - 12, 12]
      },
      backgroundColor: 'rgba(255,255,255,0.92)',
      borderColor: '#e4e7ed',
      borderWidth: 1,
      padding: [8, 12],
      textStyle: { fontSize: 12, color: '#333' },
      extraCssText: 'box-shadow: 0 4px 16px rgba(0,0,0,0.12); border-radius: 8px; max-width: 280px;',
      formatter: (params: any) => {
        if (params.dataType === 'node') {
          const name = escapeHtml(String(params.data.fullName || params.data.name || ''))
          const category = escapeHtml(String(params.data.category || ''))
          const description = escapeHtml(String(params.data.description || ''))
          const degree = params.data.degree || 0
          return `<div style="font-weight:600;margin-bottom:4px">${name}</div><div style="color:#666;font-size:11px;line-height:1.5">${category} · 关联 ${degree} 条<br/>${description}</div>`
        }
        const source = escapeHtml(String(params.data.source || ''))
        const target = escapeHtml(String(params.data.target || ''))
        const relation = escapeHtml(String(params.data.value || ''))
        return `<div style="color:#666;font-size:11px">${source} <span style="color:#999">—${relation}→</span> ${target}</div>`
      }
    },
    legend: {
      data: categories.map((c) => c.name),
      type: 'scroll',
      bottom: 0,
      left: 'center',
      width: '92%',
      itemGap: 20,
      itemWidth: 18,
      itemHeight: 12,
      padding: [8, 12],
      textStyle: { fontSize: 12, color: '#4b5563' }
    },
    series: [
      {
        type: 'graph',
        layout: 'force',
        data: eNodes,
        links: eEdges,
        categories,
        roam: true,
        draggable: true,
        scaleLimit: { min: 0.2, max: 5 },
        zoom: 0.9,
        force: {
          repulsion: isLarge ? 1200 : 600,
          edgeLength: isLarge ? [60, 180] : [80, 220],
          gravity: isLarge ? 0.03 : 0.05,
          // 关闭持续的力导向动画，布局稳定后不再转动
          layoutAnimation: false
        },
        label: {
          position: 'bottom',
          fontSize: 11,
          color: '#333'
        },
        lineStyle: {
          color: '#a8b2c1',
          width: 1.2,
          curveness: 0.2
        },
        emphasis: {
          focus: 'adjacency',
          lineStyle: { width: 3 },
          label: { show: true }
        },
        edgeLabel: {
          fontSize: 10
        },
        autoCurveness: true
      }
    ]
  }
}

const render = async () => {
  if (!showChart.value) {
    return
  }
  await nextTick()
  if (container.value) {
    if (!chart) {
      chart = init(container.value)
    }
    chart.setOption(buildOption(), true)
  }
  if (isFullscreen.value && fullscreenContainer.value) {
    if (!fullscreenChart) {
      fullscreenChart = init(fullscreenContainer.value)
    }
    fullscreenChart.setOption(buildOption(), true)
  }
}

const handleResize = () => {
  chart?.resize()
  fullscreenChart?.resize()
}

const resetView = () => {
  chart?.dispatchAction({ type: 'restore' })
  fullscreenChart?.dispatchAction({ type: 'restore' })
}

const openFullscreen = () => {
  isFullscreen.value = true
  document.body.style.overflow = 'hidden'
  render()
}

const closeFullscreen = () => {
  isFullscreen.value = false
  document.body.style.overflow = ''
  if (fullscreenChart) {
    fullscreenChart.dispose()
    fullscreenChart = null
  }
  fullscreenResizeObserver?.disconnect()
  fullscreenResizeObserver = null
  // 退出全屏后普通画布需要重绘，避免尺寸/数据不同步
  nextTick(() => {
    chart?.resize()
  })
}

const setupResizeObserver = (el: HTMLElement, isFs = false) => {
  if (typeof ResizeObserver === 'undefined') return
  const ro = new ResizeObserver((entries) => {
    for (const entry of entries) {
      if (entry.contentRect.width > 0 || entry.contentRect.height > 0) {
        handleResize()
      }
    }
  })
  ro.observe(el)
  if (isFs) {
    fullscreenResizeObserver = ro
  } else {
    resizeObserver = ro
  }
}

onMounted(() => {
  render()
  window.addEventListener('resize', handleResize)
  if (container.value) {
    setupResizeObserver(container.value)
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  resizeObserver?.disconnect()
  resizeObserver = null
  fullscreenResizeObserver?.disconnect()
  fullscreenResizeObserver = null
  chart?.dispose()
  chart = null
  fullscreenChart?.dispose()
  fullscreenChart = null
})

watch(() => props.data, () => {
  // 数据切换时重置交互状态，避免旧筛选导致新图为空
  showEdgeLabels.value = false
  showAllLabels.value = false
  focusMode.value = false
  render()
})

watch([showEdgeLabels, showAllLabels, focusMode], render, { deep: true })

watch(isFullscreen, (val) => {
  if (val) {
    render()
    nextTick(() => {
      if (fullscreenContainer.value) {
        setupResizeObserver(fullscreenContainer.value, true)
      }
    })
  }
})
</script>

<template>
  <div v-if="showChart" class="knowledge-graph-wrapper">
    <div class="graph-toolbar">
      <div class="toolbar-left">
        <el-button size="small" text :type="focusMode ? 'primary' : 'default'" @click="focusMode = !focusMode">
          <el-icon><Pointer /></el-icon>
          {{ focusMode ? '退出聚焦' : '聚焦核心' }}
        </el-button>
        <el-button size="small" text :type="showAllLabels ? 'primary' : 'default'" @click="showAllLabels = !showAllLabels">
          <el-icon><Operation /></el-icon>
          {{ showAllLabels ? '精简标签' : '显示全部标签' }}
        </el-button>
        <el-button size="small" text :type="showEdgeLabels ? 'primary' : 'default'" @click="showEdgeLabels = !showEdgeLabels">
          <el-icon><Hide /></el-icon>
          {{ showEdgeLabels ? '隐藏关系' : '显示关系' }}
        </el-button>
        <el-button size="small" text @click="resetView">
          <el-icon><View /></el-icon>
          重置视图
        </el-button>
        <el-button size="small" text type="primary" @click="openFullscreen">
          <el-icon><FullScreen /></el-icon>
          全屏查看
        </el-button>
      </div>
      <div class="toolbar-right">
        <span class="legend-hint">点击底部图例可筛选类型</span>
      </div>
    </div>
    <div ref="container" class="knowledge-graph" :style="{ height: height || '460px' }"></div>
  </div>

  <!-- 全屏画布：固定覆盖整个视口，拥有独立 ECharts 实例 -->
  <div v-if="isFullscreen" class="graph-fullscreen-overlay">
    <div class="graph-fullscreen-toolbar">
      <div class="toolbar-left">
        <el-button size="small" text :type="focusMode ? 'primary' : 'default'" @click="focusMode = !focusMode">
          <el-icon><Pointer /></el-icon>
          {{ focusMode ? '退出聚焦' : '聚焦核心' }}
        </el-button>
        <el-button size="small" text :type="showAllLabels ? 'primary' : 'default'" @click="showAllLabels = !showAllLabels">
          <el-icon><Operation /></el-icon>
          {{ showAllLabels ? '精简标签' : '显示全部标签' }}
        </el-button>
        <el-button size="small" text :type="showEdgeLabels ? 'primary' : 'default'" @click="showEdgeLabels = !showEdgeLabels">
          <el-icon><Hide /></el-icon>
          {{ showEdgeLabels ? '隐藏关系' : '显示关系' }}
        </el-button>
        <el-button size="small" text @click="resetView">
          <el-icon><View /></el-icon>
          重置视图
        </el-button>
      </div>
      <div class="toolbar-right">
        <span class="legend-hint">点击底部图例可筛选类型</span>
        <el-button size="small" text type="primary" class="close-fullscreen-btn" @click="closeFullscreen">
          <el-icon><Close /></el-icon>
          退出全屏
        </el-button>
      </div>
    </div>
    <div ref="fullscreenContainer" class="graph-fullscreen-canvas"></div>
  </div>

  <el-empty
    v-else
    description="暂无图谱数据，请先在笔记中构建知识图谱"
    :image-size="80"
  />
</template>

<style scoped>
.knowledge-graph-wrapper {
  width: 100%;
  display: flex;
  flex-direction: column;
}

.graph-toolbar,
.graph-fullscreen-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
  padding: 8px 0;
  margin-bottom: 8px;
}

.graph-fullscreen-toolbar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 101;
  padding: 12px 20px;
  margin: 0;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid #eef2f7;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.legend-hint {
  font-size: 12px;
  color: #909399;
}

.knowledge-graph {
  width: 100%;
  flex: 1 1 auto;
  min-height: 360px;
}

.graph-fullscreen-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 100;
  background: #fff;
  display: flex;
  flex-direction: column;
  padding: 64px 20px 20px;
}

.graph-fullscreen-canvas {
  flex: 1 1 auto;
  width: 100%;
  min-height: 0;
}

.close-fullscreen-btn {
  margin-left: 12px;
}

@media (max-width: 768px) {
  .graph-toolbar,
  .graph-fullscreen-toolbar {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import {
  SwitchButton,
  Notebook,
  ChatDotRound,
  Share,
  FolderOpened,
  MagicStick
} from '@element-plus/icons-vue'
import { useAuthStore } from './stores/auth'
import AppHeader from './components/AppHeader.vue'
import LoginModal from './components/LoginModal.vue'
import TaskCenterView from './views/TaskCenterView.vue'
import { getActiveTasks, getRecentTasks, type ActiveTask, type RecentTask } from './api/file'
import { listAIAnalysisTasks, type AiAnalysisTaskItem } from './api/note'
import { listGraphTasks, type GraphTaskItem } from './api/graph'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const isLoggedIn = computed(() => auth.isLoggedIn)
const isBlankLayout = computed(() => route.meta.layout === 'blank')
/** 工作台布局是否展示（登录 + 非空白页）：任务轮询与悬浮球只在这里生效 */
const isWorkspaceView = computed(() => auth.isLoggedIn && !isBlankLayout.value)

/* ---------- 任务中心业务（工作台专属，原在导航栏，现已解耦至此） ---------- */
const activeTasks = ref<ActiveTask[]>([])
const recentTasks = ref<RecentTask[]>([])
const aiActiveTasks = ref<AiAnalysisTaskItem[]>([])
const aiRecentTasks = ref<AiAnalysisTaskItem[]>([])
const graphActiveTasks = ref<GraphTaskItem[]>([])
const graphRecentTasks = ref<GraphTaskItem[]>([])
const taskDialogVisible = ref(false)
let taskTimer: ReturnType<typeof setInterval> | null = null
let taskLoading = false

const loadActiveTasks = async () => {
  if (!isWorkspaceView.value) return
  // 上一次轮询未结束时跳过，防止慢网络下轮询请求堆积
  if (taskLoading) return
  taskLoading = true
  try {
    activeTasks.value = await getActiveTasks()
    recentTasks.value = await getRecentTasks()
  } catch {
    // 忽略轮询失败
  }
  try {
    const ai = await listAIAnalysisTasks()
    aiActiveTasks.value = ai.active || []
    aiRecentTasks.value = ai.recent || []
  } catch {
    // AI 整理任务列表轮询失败不影响文档任务
  }
  try {
    const graph = await listGraphTasks()
    graphActiveTasks.value = graph.active || []
    graphRecentTasks.value = graph.recent || []
  } catch {
    // 图谱任务列表轮询失败不影响其他任务
  }
  taskLoading = false
}

/** 进行中的任务总数（文档 + AI 整理 + 知识图谱） */
const activeCount = computed(() => activeTasks.value.length + aiActiveTasks.value.length + graphActiveTasks.value.length)
/** 是否存在最近失败的任务（红点提醒） */
const hasFailedTasks = computed(
  () =>
    recentTasks.value.some((t) => t.status === 'FAILED') ||
    aiRecentTasks.value.some((t) => !!t.error) ||
    graphRecentTasks.value.some((t) => !!t.error)
)

watch(
  isWorkspaceView,
  (inWorkspace) => {
    if (inWorkspace) {
      loadActiveTasks()
      if (!taskTimer) {
        taskTimer = setInterval(loadActiveTasks, 8000)
      }
    } else {
      // 离开工作台（含登出）：无条件清理，防止跨账号数据残留
      if (taskTimer) {
        clearInterval(taskTimer)
        taskTimer = null
      }
      // 登出必须连同 recent 一起清空：否则换账号登录后，
      // 红点（hasFailedTasks）会显示上一个账号的失败任务
      activeTasks.value = []
      recentTasks.value = []
      aiActiveTasks.value = []
      aiRecentTasks.value = []
      graphActiveTasks.value = []
      graphRecentTasks.value = []
      taskDialogVisible.value = false
    }
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  if (taskTimer) {
    clearInterval(taskTimer)
    taskTimer = null
  }
})

// 新任务出现时自动打开任务中心弹窗，让用户第一时间看到进度
let lastActiveCount = 0
watch(activeCount, (count) => {
  if (count > lastActiveCount && !taskDialogVisible.value) {
    taskDialogVisible.value = true
  }
  lastActiveCount = count
})

/** 需要缓存组件的页面（避免重复初始化 wangeditor 编辑器等重组件） */
const CACHED_PAGES = ['NoteEdit']

interface NavItem {
  path: string
  label: string
  icon: typeof Notebook
}

const navItems = computed<NavItem[]>(() => {
  const items: NavItem[] = [
    { path: '/notes', label: '知识笔记', icon: Notebook },
    { path: '/ask', label: '知识问答', icon: ChatDotRound },
    { path: '/graph', label: '知识图谱', icon: Share },
    { path: '/categories', label: '分类管理', icon: FolderOpened },
    { path: '/settings/ai', label: 'AI 设置', icon: MagicStick }
  ]
  return items
})

const isActive = (path: string) => route.path === path || route.path.startsWith(`${path}/`)
</script>

<template>
  <el-config-provider>
    <!-- 全局顶部导航：落地页、控制台均保留 -->
    <AppHeader />

    <!-- 全局登录弹窗 -->
    <LoginModal />

    <!-- 空白布局：落地页、公开阅读页等 -->
    <div v-if="isBlankLayout" class="blank-layout">
      <RouterView />
    </div>

    <!-- 用户工作台布局：左侧边栏导航 -->
    <div v-else-if="isLoggedIn" class="workspace">
      <aside class="sidebar">
        <nav class="sidebar-nav">
          <button
            v-for="item in navItems"
            :key="item.path"
            type="button"
            class="sidebar-item"
            :class="{ active: isActive(item.path) }"
            @click="router.push(item.path)"
          >
            <el-icon class="sidebar-icon"><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </button>
        </nav>

        <div class="sidebar-footer">
          <div class="user-block" @click="router.push('/settings/account')">
            <div class="user-avatar">{{ (auth.displayName || 'U').slice(0, 1).toUpperCase() }}</div>
            <div class="user-meta">
              <span class="user-name">{{ auth.displayName }}</span>
              <span class="user-tip">当前账号</span>
            </div>
          </div>
          <el-button text class="logout-btn" @click="auth.logout()">
            <el-icon><SwitchButton /></el-icon>
            退出登录
          </el-button>
        </div>
      </aside>

      <main class="workspace-main">
        <RouterView v-slot="{ Component, route: viewRoute }">
          <KeepAlive :include="CACHED_PAGES" :max="5">
            <component :is="Component" :key="viewRoute.fullPath" />
          </KeepAlive>
        </RouterView>
      </main>

      <!-- 任务中心悬浮球：右下角常驻，点击打开任务中心弹窗 -->
      <div class="task-fab">
        <button
          class="task-btn"
          :title="activeCount > 0 ? `有 ${activeCount} 个任务进行中` : '任务中心'"
          @click="taskDialogVisible = true"
        >
          <span v-if="activeCount > 0" class="task-btn-spinner" />
          <span v-if="activeCount > 0" class="task-btn-count">{{ activeCount }}</span>
          <span v-if="hasFailedTasks" class="task-btn-fail-dot" />
          {{ activeCount > 0 ? '任务进行中' : '任务中心' }}
        </button>
      </div>

      <!-- 任务中心弹窗：细粒度进度 + 失败重试 + 删除/清空任务记录 -->
      <el-dialog
        v-model="taskDialogVisible"
        title="任务中心"
        width="760px"
        :destroy-on-close="true"
        :append-to-body="true"
        :close-on-click-modal="true"
        align-center
        class="task-dialog"
      >
        <TaskCenterView embedded />
      </el-dialog>
    </div>
  </el-config-provider>
</template>

<style>
/* ---------- 知序设计 token（落地页 + 控制台共享） ---------- */
:root {
  --zx-paper: #fffefa;
  --zx-ink: #17202f;
  --zx-brand: #f2641e;
  --zx-brand-ink: #c25018;
  --zx-brand-soft: #fef0e9;
  --zx-brand-ring: rgba(242, 100, 30, 0.16);
  --zx-iris: #7a5af8;
  --zx-teal: #0ca789;
  --zx-amber: #d9930d;
  --zx-night: #111a2e;
  --zx-display:
    'Baloo 2', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
  /* 中文展示衬线：只给大标题与引言，克制使用 */
  --zx-serif:
    'Noto Serif SC', 'Songti SC', 'SimSun', serif;
  --zx-mono:
    ui-monospace, 'JetBrains Mono', 'SF Mono', Menlo, Consolas, monospace;
  /* 落地页用柿色；工作台（.workspace 内）覆盖回 Element 默认蓝，见下方 */
  --el-color-primary: var(--zx-brand);
  --el-color-primary-light-3: #f69361;
  --el-color-primary-light-5: #f9b18f;
  --el-color-primary-light-7: #fbd1bc;
  --el-color-primary-light-8: #fce0d2;
  --el-color-primary-light-9: #fef0e9;
  --el-color-primary-dark-2: #c25018;
}

/* ---------- 工作台恢复蓝白：Element 组件回到默认蓝 ---------- */
.workspace {
  --el-color-primary: #409eff;
  --el-color-primary-light-3: #79bbff;
  --el-color-primary-light-5: #a0cfff;
  --el-color-primary-light-7: #c6e2ff;
  --el-color-primary-light-8: #d9ecff;
  --el-color-primary-light-9: #ecf5ff;
  --el-color-primary-dark-2: #337ecc;
}

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html,
body,
#app {
  min-height: 100%;
  font-family:
    'Helvetica Neue',
    Helvetica,
    'PingFang SC',
    'Hiragino Sans GB',
    'Microsoft YaHei',
    Arial,
    sans-serif;
  background: #f2f4f8;
  color: #1f2937;
}

body {
  overflow-y: scroll;
}

::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

::-webkit-scrollbar-thumb {
  background-color: #d0d5dd;
  border-radius: 999px;
}

::-webkit-scrollbar-thumb:hover {
  background-color: #b8c0cc;
}

::-webkit-scrollbar-track {
  background-color: transparent;
}

/* ---------- 工作台布局 ---------- */
.workspace {
  display: flex;
  min-height: 100vh;
}

.sidebar {
  position: fixed;
  top: 68px;
  left: 0;
  bottom: 0;
  width: 224px;
  display: flex;
  flex-direction: column;
  background: #ffffff;
  border-right: 1px solid #e8ebf1;
  z-index: 30;
}

.sidebar-nav {
  flex: 1;
  padding: 16px 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  overflow-y: auto;
}

.sidebar-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px 12px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: #4b5563;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.18s ease;
  text-align: left;
}

.sidebar-item:hover {
  background: #f4f7fd;
  color: #2563eb;
}

.sidebar-item.active {
  background: rgba(37, 99, 235, 0.1);
  color: #2563eb;
  font-weight: 600;
}

.sidebar-icon {
  font-size: 17px;
  flex-shrink: 0;
}

.sidebar-footer {
  padding: 12px;
  border-top: 1px solid #f0f2f6;
}

.user-block {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 8px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.18s ease;
}

.user-block:hover {
  background: #f4f7fd;
}

.user-avatar {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #2563eb;
  color: #fff;
  font-size: 15px;
  font-weight: 700;
  flex-shrink: 0;
}

.user-meta {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.user-name {
  font-size: 13px;
  color: #303133;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-tip {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  color: #909399;
}

.tip-icon {
  font-size: 11px;
}


.workspace-main {
  flex: 1;
  margin-left: 224px;
  margin-top: 68px;
  padding: 20px 24px;
  min-width: 0;
}

.blank-layout {
  padding-top: 68px;
  min-height: 100vh;
}

.booting {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
}

.booting-logo {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #2563eb;
  color: #fff;
  font-size: 18px;
  font-weight: 800;
  box-shadow: 0 12px 28px rgba(37, 99, 235, 0.2);
}

.booting-text {
  color: #909399;
  font-size: 14px;
}

/* ---------- 任务中心悬浮球（工作台蓝白） ---------- */
.task-dialog {
  max-width: calc(100vw - 32px) !important;
}

.task-fab {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 90;
  cursor: pointer;
  filter: drop-shadow(0 10px 24px rgba(23, 32, 47, 0.16));
}

@media (max-width: 768px) {
  .task-fab {
    right: 16px;
    bottom: 16px;
  }
}

.task-fab .task-btn {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  border: 1px solid #dbe3f0;
  border-radius: 999px;
  padding: 11px 18px;
  background: rgba(255, 255, 255, 0.96);
  color: #2563eb;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    transform 0.2s ease;
}

.task-fab .task-btn:hover {
  border-color: #2563eb;
  transform: translateY(-2px);
}

.task-fab .task-btn:focus-visible {
  outline: 2px solid #2563eb;
  outline-offset: 2px;
}

.task-btn-count {
  background: #2563eb;
  color: #fff;
  border-radius: 10px;
  font-size: 11px;
  line-height: 1;
  padding: 3px 6px;
}

.task-btn-fail-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #f56c6c;
  flex-shrink: 0;
}

.task-btn-spinner {
  width: 10px;
  height: 10px;
  border: 2px solid #c0c4cc;
  border-top-color: #2563eb;
  border-radius: 50%;
  animation: task-spin 0.8s linear infinite;
}

@keyframes task-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .task-fab .task-btn:hover {
    transform: none;
  }

  .task-btn-spinner {
    animation: none;
  }
}

/* ---------- 身份管理弹窗 ---------- */
.logout-btn {
  width: 100%;
  margin-top: 8px;
  color: #6b7280;
  justify-content: flex-start;
}

.logout-btn .el-icon {
  margin-right: 4px;
}

@media (max-width: 900px) {
  .sidebar {
    top: 68px;
    width: 64px;
  }
  .sidebar-item span,
  .user-meta,
  .logout-btn span {
    display: none;
  }
  .sidebar-item {
    justify-content: center;
    padding: 12px 0;
  }
  .user-block {
    justify-content: center;
    padding: 6px 0;
  }
  .logout-btn {
    justify-content: center;
    padding: 8px 0;
  }
  .workspace-main {
    margin-left: 64px;
    margin-top: 68px;
    padding: 16px;
  }
}
</style>

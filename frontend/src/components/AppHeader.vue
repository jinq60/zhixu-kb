<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { getActiveTasks, getRecentTasks, type ActiveTask, type RecentTask } from '../api/file'
import { listAIAnalysisTasks, type AiAnalysisTaskItem } from '../api/note'
import { listGraphTasks, type GraphTaskItem } from '../api/graph'
import TaskCenterView from '../views/TaskCenterView.vue'
import {
  ArrowRight,
  ArrowDown,
  Download,
  Document,
  OfficeBuilding,
  User,
  Phone,
  Monitor,
  Menu,
  Notebook,
  SetUp,
  DataLine,
  Link
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const mobileMenuOpen = ref(false)

/** 全局任务中心角标数据（文档处理 + AI 整理，登录且在工作台时轮询） */
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
  if (!auth.isLoggedIn || isHome.value) return
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

// 注意：以下 computed 必须在 watch 之前声明——watch(immediate) 会在 setup 阶段同步调用
// loadActiveTasks()，其中引用了 isHome，声明顺序颠倒会触发 TDZ 错误（Cannot access before initialization）
const isHome = computed(() => route.path === '/' || route.path === '/home')
const isWorkspace = computed(() => !isHome.value)
/** 进行中的任务总数（文档 + AI 整理 + 知识图谱） */
const activeCount = computed(() => activeTasks.value.length + aiActiveTasks.value.length + graphActiveTasks.value.length)
/** 是否存在最近失败的任务（红点提醒） */
const hasFailedTasks = computed(
  () =>
    recentTasks.value.some((t) => t.status === 'FAILED') ||
    aiRecentTasks.value.some((t) => !!t.error) ||
    graphRecentTasks.value.some((t) => !!t.error)
)

// 登录后启动全局任务轮询（3s），退出登录停止
watch(
  () => auth.isLoggedIn,
  (loggedIn) => {
    if (loggedIn) {
      loadActiveTasks()
      if (!taskTimer) {
        taskTimer = setInterval(loadActiveTasks, 8000)
      }
    } else {
      // 未登录（含登出、首页未起轮询的场景）：无条件清理，防止跨账号数据残留
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

const enterWeb = () => {
  if (auth.isLoggedIn) {
    router.push('/notes')
  } else {
    auth.openLoginModal()
  }
}

const goDownload = () => {
  if (auth.isLoggedIn) {
    goHomeHash('#download')
  } else {
    auth.openLoginModal()
  }
}

const products = [
  { path: '/notes', label: '知序智能知识库', desc: 'OCR + AI 整理 + 知识图谱', icon: Notebook, color: '#f2641e' },
  { path: '/home', label: '知序 AI 工作台', desc: '面向团队的智能协作平台', icon: Monitor, coming: true, color: '#7a5af8' },
  { path: '/home', label: '知序 OCR 工具箱', desc: '本地离线 OCR 识别套件', icon: SetUp, coming: true, color: '#0ca789' },
  { path: '/home', label: '知序数据同步助手', desc: '多端知识库同步工具', icon: DataLine, coming: true, color: '#d9930d' }
]

const navLinks = [
  { label: '产品', type: 'dropdown' },
  { label: '下载', hash: '#download' },
  { label: '解决方案', hash: '#solutions' },
  { label: '客户案例', hash: '#cases' },
  { label: '文档', hash: '#docs' },
  { label: '关于我们', hash: '#about' },
  { label: '联系我们', hash: '#contact' }
]

const goHomeHash = (hash: string) => {
  mobileMenuOpen.value = false
  if (isHome.value) {
    const el = document.querySelector(hash)
    if (el) el.scrollIntoView({ behavior: 'smooth' })
  } else {
    router.push({ path: '/home', hash }).then(() => {
      setTimeout(() => {
        const el = document.querySelector(hash)
        if (el) el.scrollIntoView({ behavior: 'smooth' })
      }, 100)
    })
  }
}
</script>

<template>
  <header class="app-header">
    <div class="header-inner">
      <div class="header-brand" @click="router.push('/home')">
        <div class="brand-logo">知序</div>
        <div class="brand-text">
          <span class="brand-name">知序</span>
          <span class="brand-slogan">ZhiXu Tech</span>
        </div>
      </div>

      <nav class="header-nav">
        <el-dropdown
          v-for="link in navLinks"
          :key="link.label"
          placement="bottom"
          :show-timeout="120"
          :hide-timeout="150"
        >
          <span
            v-if="link.type === 'dropdown'"
            class="nav-item"
          >
            {{ link.label }}
            <el-icon class="nav-arrow"><ArrowDown /></el-icon>
          </span>
          <span
            v-else
            class="nav-item"
            @click="goHomeHash(link.hash!)"
          >
            {{ link.label }}
          </span>
          <template v-if="link.type === 'dropdown'" #dropdown>
            <el-dropdown-menu class="product-menu">
              <el-dropdown-item
                v-for="p in products"
                :key="p.label"
                @click="p.coming ? router.push('/home') : router.push(p.path)"
              >
                <div class="product-item">
                  <div
                    class="product-icon"
                    :class="{ coming: p.coming }"
                    :style="{ '--p': p.color }"
                  >
                    <el-icon><component :is="p.icon" /></el-icon>
                  </div>
                  <div class="product-info">
                    <div class="product-name">
                      {{ p.label }}
                      <el-tag v-if="p.coming" size="small" type="info" effect="plain">即将上线</el-tag>
                    </div>
                    <div class="product-desc">{{ p.desc }}</div>
                  </div>
                </div>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </nav>

      <div class="header-actions">
        <el-button
          text
          class="download-link"
          @click="goDownload"
        >
          <el-icon><Download /></el-icon>
          下载
        </el-button>
        <el-button
          type="primary"
          class="console-btn"
          @click="enterWeb"
        >
          {{ isWorkspace ? '工作台' : 'Web 体验' }}
          <el-icon class="btn-icon"><ArrowRight /></el-icon>
        </el-button>

        <!-- 任务中心角标（登录 + 工作台时始终显示，点击打开任务中心弹窗） -->
        <div
          v-if="isWorkspace && auth.isLoggedIn"
          class="task-badge"
        >
          <button class="task-btn" @click="taskDialogVisible = true">
            <span v-if="activeCount > 0" class="task-btn-spinner" />
            <span v-if="activeCount > 0" class="task-btn-count">{{ activeCount }}</span>
            <span v-if="hasFailedTasks" class="task-btn-fail-dot" />
            {{ activeCount > 0 ? '任务进行中' : '任务中心' }}
          </button>
        </div>
      </div>

      <div class="mobile-toggle" @click="mobileMenuOpen = !mobileMenuOpen">
        <el-icon><Menu /></el-icon>
      </div>
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

    <div v-show="mobileMenuOpen" class="mobile-menu">
      <a class="mobile-link" @click="router.push('/home'); mobileMenuOpen = false">首页</a>
      <a class="mobile-link" @click="goHomeHash('#products')">产品</a>
      <a class="mobile-link" @click="goHomeHash('#download')">下载</a>
      <a class="mobile-link" @click="goHomeHash('#solutions')">解决方案</a>
      <a class="mobile-link" @click="goHomeHash('#cases')">客户案例</a>
      <a class="mobile-link" @click="goHomeHash('#docs')">文档</a>
      <a class="mobile-link" @click="goHomeHash('#about')">关于我们</a>
      <a class="mobile-link" @click="goHomeHash('#contact')">联系我们</a>
      <el-button type="primary" class="mobile-console-btn" @click="enterWeb">
        Web 体验
      </el-button>
    </div>
  </header>
</template>

<style scoped>
.app-header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  height: 68px;
  background: rgba(255, 254, 250, 0.92);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-bottom: 1px solid #f3e9dc;
}

.app-header button:focus-visible,
.app-header a:focus-visible,
.app-header .nav-item:focus-visible {
  outline: 2px solid var(--zx-brand);
  outline-offset: 2px;
  border-radius: 8px;
}

.header-inner {
  max-width: 1280px;
  height: 100%;
  margin: 0 auto;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
}

.brand-logo {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f2641e 0%, #d94f0e 100%);
  color: #fff;
  font-size: 15px;
  font-weight: 800;
  box-shadow: 0 6px 16px var(--zx-brand-ring);
}

.brand-text {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.brand-name {
  font-family: var(--zx-display);
  font-size: 19px;
  font-weight: 800;
  color: var(--zx-ink);
  line-height: 1.1;
  letter-spacing: 0.5px;
}

.brand-slogan {
  font-family: var(--zx-mono);
  font-size: 10px;
  color: #a8a29e;
  letter-spacing: 1.5px;
  text-transform: uppercase;
  line-height: 1.2;
}

.header-nav {
  display: flex;
  align-items: center;
  gap: 6px;
}

.nav-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 8px 14px;
  border-radius: 8px;
  color: #4b5563;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.nav-item:hover {
  color: var(--zx-brand-ink);
  background: var(--zx-brand-soft);
}

.nav-arrow {
  font-size: 12px;
}

.product-menu {
  width: 320px;
  padding: 8px;
  border-radius: 16px;
}

.product-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 8px 6px;
  border-radius: 12px;
}

.product-icon {
  --p: var(--zx-brand);
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fef0e9;
  background: color-mix(in srgb, var(--p) 12%, white);
  color: var(--p);
  font-size: 19px;
  flex-shrink: 0;
}

.product-icon.coming {
  background: #f5f3ef;
  color: #a8a29e;
}

.product-info {
  flex: 1;
  min-width: 0;
}

.product-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 700;
  color: #111827;
  margin-bottom: 2px;
}

.product-desc {
  font-size: 12px;
  color: #6b7280;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.download-link {
  color: #4b5563;
  font-weight: 500;
}

.download-link .el-icon {
  margin-right: 4px;
}

.console-btn {
  font-weight: 600;
}

.btn-icon {
  margin-left: 4px;
}

.mobile-toggle {
  display: none;
  font-size: 22px;
  color: #4b5563;
  cursor: pointer;
  padding: 8px;
}

.mobile-menu {
  display: none;
  position: absolute;
  top: 68px;
  left: 0;
  right: 0;
  background: #fff;
  border-bottom: 1px solid #eef2f7;
  padding: 16px 24px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.08);
}

.mobile-link {
  display: block;
  padding: 12px 0;
  color: #4b5563;
  font-size: 15px;
  border-bottom: 1px solid #f3f4f6;
  cursor: pointer;
}

.mobile-console-btn {
  width: 100%;
  margin-top: 16px;
}

@media (max-width: 1024px) {
  .header-nav,
  .download-link {
    display: none;
  }

  .mobile-toggle {
    display: block;
  }

  .mobile-menu {
    display: block;
  }
}
.task-dialog {
  max-width: calc(100vw - 32px) !important;
}

.task-badge {
  position: relative;
  margin-left: 12px;
  cursor: pointer;
}

.task-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid #f0ddd0;
  border-radius: 8px;
  padding: 6px 12px;
  background: var(--zx-brand-soft);
  color: var(--zx-brand-ink);
  font-size: 13px;
  cursor: pointer;
}

.task-btn:hover {
  border-color: var(--zx-brand);
}

.task-btn-count {
  background: var(--zx-brand);
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
  border: 2px solid #e8c9b8;
  border-top-color: var(--zx-brand);
  border-radius: 50%;
  animation: task-spin 0.8s linear infinite;
}

@keyframes task-spin {
  to {
    transform: rotate(360deg);
  }
}

</style>

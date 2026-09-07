<script setup lang="ts">
import { computed } from 'vue'
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

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const isLoggedIn = computed(() => auth.isLoggedIn)
const isBlankLayout = computed(() => route.meta.layout === 'blank')

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
  --zx-mono:
    ui-monospace, 'JetBrains Mono', 'SF Mono', Menlo, Consolas, monospace;
  /* Element Plus 主色同步（控制台协调换肤的关键一行） */
  --el-color-primary: var(--zx-brand);
  --el-color-primary-light-3: #f69361;
  --el-color-primary-light-5: #f9b18f;
  --el-color-primary-light-7: #fbd1bc;
  --el-color-primary-light-8: #fce0d2;
  --el-color-primary-light-9: #fef0e9;
  --el-color-primary-dark-2: #c25018;
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
  background: var(--zx-brand-soft);
  color: var(--zx-brand-ink);
}

.sidebar-item.active {
  background: var(--zx-brand-soft);
  color: var(--zx-brand-ink);
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
  background: var(--zx-brand-soft);
}

.user-avatar {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--zx-brand);
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
  background: var(--zx-brand);
  color: #fff;
  font-size: 18px;
  font-weight: 800;
  box-shadow: 0 12px 28px var(--zx-brand-ring);
}

.booting-text {
  color: #909399;
  font-size: 14px;
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

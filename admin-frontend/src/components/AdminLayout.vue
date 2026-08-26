<script setup lang="ts">
import { computed } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

interface NavItem {
  path: string
  label: string
  icon: string
  /** 外部链接（如 Kibana 日志中心）时直接新窗口打开，不走路由 */
  external?: string
}

const navItems = computed<NavItem[]>(() => [
  { path: '/admin/ops', label: '系统总览', icon: 'Odometer' },
  { path: '/admin/runtime', label: '运行状态', icon: 'Monitor' },
  { path: '/admin/ai/endpoints', label: 'AI 端点管理', icon: 'Connection' },
  { path: '/admin/users', label: '用户治理', icon: 'UserFilled' },
  { path: '/admin/logs', label: '系统日志', icon: 'Document' }
])

const isActive = (path: string) => route.path === path || route.path.startsWith(`${path}/`)

const handleNav = (item: NavItem) => {
  if (item.external) {
    window.open(item.external, '_blank', 'noopener')
    return
  }
  router.push(item.path)
}
</script>

<template>
  <div class="admin-workspace">
    <aside class="admin-sidebar">
      <div class="admin-brand" @click="router.push('/admin/ops')">
        <div class="admin-logo">序</div>
        <div class="admin-brand-text">
          <strong>知序管理后台</strong>
          <span>Platform Admin</span>
        </div>
      </div>

      <nav class="admin-nav">
        <button
          v-for="item in navItems"
          :key="item.path"
          type="button"
          class="admin-nav-item"
          :class="{ active: !item.external && isActive(item.path) }"
          @click="handleNav(item)"
        >
          <el-icon class="admin-nav-icon"><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
          <el-icon v-if="item.external" class="admin-nav-external"><TopRight /></el-icon>
        </button>
      </nav>

      <div class="admin-footer">
        <div class="admin-user">
          <div class="admin-avatar">{{ (auth.username || 'A').slice(0, 1).toUpperCase() }}</div>
          <div class="admin-user-meta">
            <span class="admin-user-name">{{ auth.username }}</span>
            <span class="admin-user-tip">管理员</span>
          </div>
        </div>
        <el-button class="admin-logout-btn" text @click="auth.logout()">
          <el-icon><SwitchButton /></el-icon>
          <span>退出登录</span>
        </el-button>
      </div>
    </aside>

    <main class="admin-main">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.admin-workspace {
  display: flex;
  min-height: 100vh;
}

.admin-sidebar {
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  width: 224px;
  display: flex;
  flex-direction: column;
  background: #1f2733;
  color: #cfd6e0;
  z-index: 30;
}

.admin-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px 16px;
  cursor: pointer;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.admin-logo {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #409eff;
  color: #fff;
  font-size: 16px;
  font-weight: 800;
  flex-shrink: 0;
}

.admin-brand-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.admin-brand-text strong {
  font-size: 14px;
  color: #fff;
}

.admin-brand-text span {
  font-size: 11px;
  color: #8b96a5;
}

.admin-nav {
  flex: 1;
  padding: 14px 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  overflow-y: auto;
}

.admin-nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 11px 12px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: #aeb8c5;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.18s ease;
  text-align: left;
}

.admin-nav-item:hover {
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
}

.admin-nav-item.active {
  background: rgba(64, 158, 255, 0.22);
  color: #fff;
  font-weight: 600;
}

.admin-nav-icon {
  font-size: 17px;
  flex-shrink: 0;
}

.admin-nav-external {
  margin-left: auto;
  font-size: 12px;
  opacity: 0.55;
}

.admin-footer {
  padding: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.admin-user {
  display: flex;
  align-items: center;
  gap: 10px;
}

.admin-avatar {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #409eff;
  color: #fff;
  font-size: 15px;
  font-weight: 700;
  flex-shrink: 0;
}

.admin-user-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.admin-user-name {
  font-size: 13px;
  color: #fff;
  font-weight: 600;
}

.admin-user-tip {
  font-size: 11px;
  color: #8b96a5;
}

.admin-logout-btn {
  justify-content: flex-start;
  color: #8b96a5;
  font-size: 13px;
  padding: 6px 8px;
}

.admin-logout-btn:hover {
  color: #f56c6c;
}

.admin-main {
  flex: 1;
  margin-left: 224px;
  padding: 20px 24px;
  min-width: 0;
  background: #f2f4f8;
}
</style>

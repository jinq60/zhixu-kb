import { createRouter, createWebHistory, type RouteLocationNormalized, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes: RouteRecordRaw[] = [
  // 公开阅读页（分享链接直达）
  { path: '/notes/view/:id', component: () => import('../views/NoteView.vue'), meta: { public: true, layout: 'blank' } },

  // OAuth 回调页
  { path: '/oauth-callback', component: () => import('../views/OAuthCallbackView.vue'), meta: { public: true, layout: 'blank' } },

  // 用户工作台（需要登录）
  { path: '/notes', component: () => import('../views/NoteList.vue'), meta: { requiresAuth: true } },
  { path: '/notes/edit/:id', component: () => import('../views/NoteEdit.vue'), meta: { requiresAuth: true } },
  { path: '/categories', component: () => import('../views/CategoryList.vue'), meta: { requiresAuth: true } },
  { path: '/ask', component: () => import('../views/ConsultView.vue'), meta: { requiresAuth: true } },
  { path: '/graph', component: () => import('../views/GraphView.vue'), meta: { requiresAuth: true } },
  { path: '/settings/ai', component: () => import('../views/AiSettingsView.vue'), meta: { requiresAuth: true } },
  { path: '/settings/account', component: () => import('../views/AccountSettingsView.vue'), meta: { requiresAuth: true } },

  // 官网/落地页
  { path: '/', component: () => import('../views/HomeView.vue'), meta: { public: true, layout: 'blank' } },
  { path: '/home', component: () => import('../views/HomeView.vue'), meta: { public: true, layout: 'blank' } },
  { path: '/:pathMatch(.*)*', redirect: '/home' }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to) {
    if (to.hash) {
      return { el: to.hash, behavior: 'smooth' }
    }
    return { top: 0 }
  }
})

/**
 * 全局认证守卫（导出以便单测复用真实实现，避免测试复制逻辑导致实现漂移）：
 * - 已登录访问 OAuth 回调页无意义，直接进工作台
 * - 未登录访问需要权限的页面，唤起登录弹窗并回到首页（携带 redirect/login 参数）
 */
export function authGuard(to: RouteLocationNormalized) {
  const auth = useAuthStore()

  if (auth.isLoggedIn && to.path === '/oauth-callback') {
    return '/notes'
  }

  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    auth.openLoginModal()
    return { path: '/home', query: { ...to.query, redirect: to.fullPath, login: '1' } }
  }

  return true
}

router.beforeEach(authGuard)

export default router

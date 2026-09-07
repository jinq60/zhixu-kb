import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes: RouteRecordRaw[] = [
  { path: '/login', component: () => import('../views/LoginView.vue'), meta: { public: true } },
  {
    path: '/admin',
    component: () => import('../components/AdminLayout.vue'),
    children: [
      { path: 'ops', component: () => import('../views/AdminOverviewView.vue') },
      { path: 'runtime', component: () => import('../views/AdminRuntimeView.vue') },
      { path: 'ai/endpoints', component: () => import('../views/AdminAiEndpointsView.vue') },
      { path: 'users', component: () => import('../views/AdminUsersView.vue') },
      { path: 'logs', component: () => import('../views/AdminLogsView.vue') }
    ]
  },
  { path: '/', redirect: '/admin/ops' },
  { path: '/:pathMatch(.*)*', redirect: '/admin/ops' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.meta.public) {
    return true
  }
  if (!auth.isLoggedIn) {
    return '/login'
  }
  // P0-6 修复：管理鉴权以服务端角色为准，不信任 localStorage roles；
  // 每次进后台都刷新一次服务端角色，401/非 admin 一律回登录页
  const roles = await auth.refreshRoles()
  if (!roles) {
    await auth.logout()
    return '/login'
  }
  if (!roles.some((r) => String(r).toLowerCase() === 'admin')) {
    await auth.logout()
    return '/login'
  }
  return true
})

export default router

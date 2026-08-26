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
  if (!auth.isAdmin) {
    return '/login'
  }
  return true
})

export default router

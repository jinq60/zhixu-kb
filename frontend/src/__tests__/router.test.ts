import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

// 与 src/router/index.ts 一致的守卫逻辑（登录制 + 公开页直达）
function createTestRouter() {
  const routes = [
    { path: '/notes/view/:id', component: { template: '<div>NoteView</div>' }, meta: { public: true } },
    { path: '/oauth-callback', component: { template: '<div>OAuth</div>' }, meta: { public: true } },
    { path: '/notes', component: { template: '<div>Notes</div>' }, meta: { requiresAuth: true } },
    { path: '/notes/:id', component: { template: '<div>NoteEdit</div>' }, meta: { requiresAuth: true } },
    { path: '/categories', component: { template: '<div>Categories</div>' }, meta: { requiresAuth: true } },
    { path: '/', component: { template: '<div>Home</div>' }, meta: { public: true } },
    { path: '/home', component: { template: '<div>Home</div>' }, meta: { public: true } },
    { path: '/:pathMatch(.*)*', redirect: '/home' }
  ]

  const router = createRouter({
    history: createWebHistory(),
    routes
  })

  router.beforeEach((to) => {
    const auth = useAuthStore()

    // 已登录用户访问 OAuth 回调页无意义，直接进工作台
    if (auth.isLoggedIn && to.path === '/oauth-callback') {
      return '/notes'
    }

    // 未登录访问需要权限的页面，唤起登录弹窗并回到首页
    if (to.meta.requiresAuth && !auth.isLoggedIn) {
      auth.openLoginModal()
      return { path: '/home', query: { ...to.query, redirect: to.fullPath, login: '1' } }
    }

    return true
  })

  return router
}

describe('Router Guards (login required)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('未登录访问受保护页面时重定向到 /home 并携带 login 参数', async () => {
    const auth = useAuthStore()
    const openSpy = vi.spyOn(auth, 'openLoginModal')
    const router = createTestRouter()
    await router.push('/notes')
    expect(router.currentRoute.value.path).toBe('/home')
    expect(router.currentRoute.value.query.login).toBe('1')
    expect(openSpy).toHaveBeenCalled()
  })

  it('已登录（有 token）时直接放行受保护页面', async () => {
    const auth = useAuthStore()
    auth.setToken('valid-token')
    const router = createTestRouter()
    await router.push('/categories')
    expect(router.currentRoute.value.path).toBe('/categories')
  })

  it('公开分享页未登录可直达', async () => {
    const auth = useAuthStore()
    const router = createTestRouter()
    await router.push('/notes/view/1')
    expect(router.currentRoute.value.path).toBe('/notes/view/1')
  })

  it('已登录访问 OAuth 回调页重定向到工作台', async () => {
    const auth = useAuthStore()
    auth.setToken('valid-token')
    const router = createTestRouter()
    await router.push('/oauth-callback')
    expect(router.currentRoute.value.path).toBe('/notes')
  })

  it('根路径放行首页', async () => {
    const auth = useAuthStore()
    const router = createTestRouter()
    await router.push('/')
    expect(router.currentRoute.value.path).toBe('/')
  })

  it('未知路径重定向到 /home', async () => {
    const auth = useAuthStore()
    const router = createTestRouter()
    await router.push('/no-such-page')
    expect(router.currentRoute.value.path).toBe('/home')
  })
})

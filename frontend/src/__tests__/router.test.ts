import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

// 与 src/router/index.ts 一致的守卫逻辑（免登录访客模式）
function createTestRouter() {
  const routes = [
    { path: '/notes/view/:id', component: { template: '<div>NoteView</div>' } },
    { path: '/notes', component: { template: '<div>Notes</div>' } },
    { path: '/notes/:id', component: { template: '<div>NoteEdit</div>' } },
    { path: '/categories', component: { template: '<div>Categories</div>' } },
    { path: '/', redirect: '/notes' },
    { path: '/:pathMatch(.*)*', redirect: '/notes' }
  ]

  const router = createRouter({
    history: createWebHistory(),
    routes
  })

  router.beforeEach(async (to) => {
    const auth = useAuthStore()
    if (!auth.isLoggedIn) {
      await auth.ensureGuestLogin()
    }
    return true
  })

  return router
}

describe('Router Guards (guest mode)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('未登录时自动调用 ensureGuestLogin 并放行', async () => {
    const auth = useAuthStore()
    const spy = vi.spyOn(auth, 'ensureGuestLogin').mockResolvedValue(true)
    const router = createTestRouter()
    await router.push('/notes')
    expect(spy).toHaveBeenCalled()
    expect(router.currentRoute.value.path).toBe('/notes')
  })

  it('已登录（有 token）时不再调用 ensureGuestLogin', async () => {
    const auth = useAuthStore()
    auth.setToken('valid-token')
    const spy = vi.spyOn(auth, 'ensureGuestLogin')
    const router = createTestRouter()
    await router.push('/categories')
    expect(spy).not.toHaveBeenCalled()
    expect(router.currentRoute.value.path).toBe('/categories')
  })

  it('ensureGuestLogin 失败时仍放行（页面自行处理错误态）', async () => {
    const auth = useAuthStore()
    vi.spyOn(auth, 'ensureGuestLogin').mockResolvedValue(false)
    const router = createTestRouter()
    await router.push('/notes')
    expect(router.currentRoute.value.path).toBe('/notes')
  })

  it('根路径重定向到 /notes', async () => {
    const auth = useAuthStore()
    auth.setToken('valid-token')
    const router = createTestRouter()
    await router.push('/')
    expect(router.currentRoute.value.path).toBe('/notes')
  })

  it('未知路径重定向到 /notes', async () => {
    const auth = useAuthStore()
    auth.setToken('valid-token')
    const router = createTestRouter()
    await router.push('/no-such-page')
    expect(router.currentRoute.value.path).toBe('/notes')
  })

  it('公开分享页可直达', async () => {
    const auth = useAuthStore()
    auth.setToken('valid-token')
    const router = createTestRouter()
    await router.push('/notes/view/1')
    expect(router.currentRoute.value.path).toBe('/notes/view/1')
  })
})

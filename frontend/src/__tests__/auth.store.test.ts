import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from '../stores/auth'

const { pushMock } = vi.hoisted(() => {
  return { pushMock: vi.fn() }
})

const apiLogin = vi.hoisted(() => vi.fn())
const apiFetchUserInfo = vi.hoisted(() => vi.fn())

vi.mock('../router', () => ({
  default: { push: pushMock }
}))

vi.mock('../api/auth', () => ({
  login: apiLogin,
  emailCodeLogin: vi.fn(),
  sendEmailCode: vi.fn(),
  smsCodeLogin: vi.fn(),
  sendSmsCode: vi.fn(),
  oauthAuthorizeUrl: vi.fn(),
  fetchUserInfo: apiFetchUserInfo,
  fetchUserProfile: vi.fn(),
  updatePassword: vi.fn(),
  sendBindEmailCode: vi.fn(),
  bindEmail: vi.fn()
}))

describe('AuthStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    pushMock.mockClear()
    apiLogin.mockClear()
    // Cookie 会话模式：登录接口只返回用户摘要（无 token），会话由浏览器 Cookie 承载
    apiLogin.mockResolvedValue({ userId: 1, username: 'user-1', roles: ['user'] })
    apiFetchUserInfo.mockResolvedValue({ id: 1, username: 'user-1', email: '', roles: ['user'] })
  })

  it('initial state should be logged out', () => {
    const auth = useAuthStore()
    expect(auth.user).toBeNull()
    expect(auth.isLoggedIn).toBe(false)
  })

  it('should not hold any token (cookie session holds the credential)', () => {
    const auth = useAuthStore()
    expect('token' in auth.$state).toBe(false)
  })

  it('setUser should update user info', () => {
    const auth = useAuthStore()
    const user = { id: 1, username: 'testuser', email: 'test@example.com', roles: ['user'] }
    auth.setUser(user)
    expect(auth.user).toEqual(user)
    expect(auth.user!.username).toBe('testuser')
  })

  it('logout should clear state and redirect to /home', async () => {
    const auth = useAuthStore()
    auth.setUser({ id: 1, username: 'testuser', email: 'test@example.com', roles: ['user'] })

    await auth.logout()

    expect(auth.user).toBeNull()
    expect(auth.isLoggedIn).toBe(false)
    expect(pushMock).toHaveBeenCalledWith('/home')
  })

  it('isLoggedIn should be false without user', () => {
    const auth = useAuthStore()
    expect(auth.isLoggedIn).toBe(false)
  })

  it('login should set user and refresh user info', async () => {
    const auth = useAuthStore()
    const ok = await auth.login({ username: 'u', password: 'p' })
    expect(ok).toBe(true)
    expect(auth.user?.username).toBe('user-1')
    expect(apiLogin).toHaveBeenCalledWith({ username: 'u', password: 'p' })
  })

  it('login failure should keep logged out', async () => {
    apiLogin.mockRejectedValueOnce(new Error('bad credentials'))
    const auth = useAuthStore()
    const ok = await auth.login({ username: 'u', password: 'wrong' })
    expect(ok).toBe(false)
    expect(auth.user).toBeNull()
  })

  it('oauthRefresh should confirm session via user info', async () => {
    const auth = useAuthStore()
    const ok = await auth.oauthRefresh()
    expect(ok).toBe(true)
    expect(auth.user?.username).toBe('user-1')
  })
})

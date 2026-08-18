import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from '../stores/auth'

const { pushMock } = vi.hoisted(() => {
  return { pushMock: vi.fn() }
})

const apiGuestLogin = vi.hoisted(() => vi.fn())
const apiUpdateDisplayName = vi.hoisted(() => vi.fn())
const apiFetchUserInfo = vi.hoisted(() => vi.fn())

vi.mock('../router', () => ({
  default: { push: pushMock }
}))

vi.mock('../api/auth', () => ({
  guestLogin: apiGuestLogin,
  updateDisplayName: apiUpdateDisplayName,
  fetchUserInfo: apiFetchUserInfo
}))

describe('AuthStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    pushMock.mockClear()
    apiGuestLogin.mockClear()
    apiGuestLogin.mockResolvedValue('token-x')
    apiFetchUserInfo.mockResolvedValue({ id: 1, username: 'guest-1', email: '', roles: ['user'] })
  })

  it('initial state should be logged out', () => {
    const auth = useAuthStore()
    expect(auth.token).toBeNull()
    expect(auth.user).toBeNull()
    expect(auth.isLoggedIn).toBe(false)
  })

  it('setToken should update token and isLoggedIn', () => {
    const auth = useAuthStore()
    auth.setToken('test-jwt-token')
    expect(auth.token).toBe('test-jwt-token')
    expect(auth.isLoggedIn).toBe(true)
  })

  it('setUser should update user info', () => {
    const auth = useAuthStore()
    const user = { id: 1, username: 'testuser', email: 'test@example.com', roles: ['user'] }
    auth.setUser(user)
    expect(auth.user).toEqual(user)
    expect(auth.user!.username).toBe('testuser')
  })

  it('logout should clear state and redirect to /notes', () => {
    const auth = useAuthStore()
    auth.setToken('test-jwt-token')
    auth.setUser({ id: 1, username: 'testuser', email: 'test@example.com', roles: ['user'] })

    auth.logout()

    expect(auth.token).toBeNull()
    expect(auth.user).toBeNull()
    expect(auth.isLoggedIn).toBe(false)
    expect(pushMock).toHaveBeenCalledWith('/notes')
  })

  it('isLoggedIn should be false when token is empty string', () => {
    const auth = useAuthStore()
    auth.setToken('')
    expect(auth.isLoggedIn).toBe(false)
  })

  it('ensureGuestLogin 并发调用只触发一次后端请求', async () => {
    const auth = useAuthStore()
    const [a, b] = await Promise.all([auth.ensureGuestLogin(), auth.ensureGuestLogin()])
    expect(a).toBe(true)
    expect(b).toBe(true)
    expect(apiGuestLogin).toHaveBeenCalledTimes(1)
    expect(auth.token).toBe('token-x')
  })

  it('ensureGuestLogin 已登录时直接返回不再请求', async () => {
    const auth = useAuthStore()
    auth.setToken('existing')
    const ok = await auth.ensureGuestLogin()
    expect(ok).toBe(true)
    expect(apiGuestLogin).not.toHaveBeenCalled()
  })
})

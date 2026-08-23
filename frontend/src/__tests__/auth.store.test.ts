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
  oauthExchange: vi.fn(),
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
    apiLogin.mockResolvedValue('token-x')
    apiFetchUserInfo.mockResolvedValue({ id: 1, username: 'user-1', email: '', roles: ['user'] })
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

  it('logout should clear state and redirect to /home', () => {
    const auth = useAuthStore()
    auth.setToken('test-jwt-token')
    auth.setUser({ id: 1, username: 'testuser', email: 'test@example.com', roles: ['user'] })

    auth.logout()

    expect(auth.token).toBeNull()
    expect(auth.user).toBeNull()
    expect(auth.isLoggedIn).toBe(false)
    expect(pushMock).toHaveBeenCalledWith('/home')
  })

  it('isLoggedIn should be false when token is empty string', () => {
    const auth = useAuthStore()
    auth.setToken('')
    expect(auth.isLoggedIn).toBe(false)
  })

  it('login should set token and refresh user info', async () => {
    const auth = useAuthStore()
    const ok = await auth.login({ username: 'u', password: 'p' })
    expect(ok).toBe(true)
    expect(auth.token).toBe('token-x')
    expect(auth.user?.username).toBe('user-1')
    expect(apiLogin).toHaveBeenCalledWith({ username: 'u', password: 'p' })
  })

  it('login failure should keep logged out', async () => {
    apiLogin.mockRejectedValueOnce(new Error('bad credentials'))
    const auth = useAuthStore()
    const ok = await auth.login({ username: 'u', password: 'wrong' })
    expect(ok).toBe(false)
    expect(auth.token).toBeNull()
  })

  it('oauthLogin should set token and refresh user info', async () => {
    const auth = useAuthStore()
    const ok = await auth.oauthLogin('oauth-jwt')
    expect(ok).toBe(true)
    expect(auth.token).toBe('oauth-jwt')
  })
})

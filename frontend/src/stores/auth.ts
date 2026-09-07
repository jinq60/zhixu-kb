import { defineStore } from 'pinia'
import 'pinia-plugin-persistedstate'
import router from '../router'
import {
  login as apiLogin,
  emailCodeLogin as apiEmailCodeLogin,
  sendEmailCode as apiSendEmailCode,
  smsCodeLogin as apiSmsCodeLogin,
  sendSmsCode as apiSendSmsCode,
  fetchUserInfo,
  logout as apiLogout
} from '../api/auth'

export interface UserInfo {
  id: number
  username: string
  email: string
  avatar?: string
  roles: string[]
}

interface AuthState {
  user: UserInfo | null
  showLoginModal: boolean
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    user: null,
    showLoginModal: false
  }),
  // Cookie 会话模式：只缓存用户信息（登录态以服务端 Cookie 为准），不再存任何 token
  persist: {
    paths: ['user'],
    storage: sessionStorage
  },
  getters: {
    isLoggedIn: (state) => !!state.user,
    displayName: (state): string => state.user?.username || ''
  },
  actions: {
    setUser(user: UserInfo) {
      this.user = user
    },
    openLoginModal() {
      this.showLoginModal = true
    },
    closeLoginModal() {
      this.showLoginModal = false
    },
    /**
     * 账号密码登录（账号不存在时自动注册）：会话由 HttpOnly Cookie 承载
     */
    async login(form: { username: string; password: string }): Promise<boolean> {
      try {
        const summary = await apiLogin(form)
        this.user = {
          id: summary.userId,
          username: summary.username,
          email: '',
          roles: summary.roles || []
        }
        await this.refreshUser()
        return true
      } catch {
        return false
      }
    },
    /**
     * 邮箱验证码登录（首次登录自动注册）
     */
    async emailCodeLogin(form: { email: string; code: string }): Promise<boolean> {
      try {
        const summary = await apiEmailCodeLogin(form)
        this.user = {
          id: summary.userId,
          username: summary.username,
          email: '',
          roles: summary.roles || []
        }
        await this.refreshUser()
        return true
      } catch {
        return false
      }
    },
    async sendEmailCode(email: string): Promise<boolean> {
      try {
        await apiSendEmailCode(email)
        return true
      } catch {
        return false
      }
    },
    /**
     * 短信验证码登录（首次登录自动注册）
     */
    async smsCodeLogin(form: { phone: string; code: string }): Promise<boolean> {
      try {
        const summary = await apiSmsCodeLogin(form)
        this.user = {
          id: summary.userId,
          username: summary.username,
          email: '',
          roles: summary.roles || []
        }
        await this.refreshUser()
        return true
      } catch {
        return false
      }
    },
    async sendSmsCode(phone: string): Promise<boolean> {
      try {
        await apiSendSmsCode(phone)
        return true
      } catch {
        return false
      }
    },
    /**
     * OAuth 回调确认：Cookie 已由后端 302 响应种下，这里只向服务端确认会话有效
     */
    async oauthRefresh(): Promise<boolean> {
      try {
        await this.refreshUser()
        return !!this.user
      } catch {
        return false
      }
    },
    async refreshUser() {
      try {
        this.user = await fetchUserInfo()
      } catch {
        // 用户信息拉取失败不阻塞
      }
    },
    async logout() {
      // 先通知后端撤销会话并清除 Cookie，再清理本地状态
      try {
        await apiLogout()
      } catch {
        // 后端撤销失败不影响本地登出
      }
      this.user = null
      router.push('/home')
    }
  }
})

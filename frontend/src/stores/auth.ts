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
  token: string | null
  user: UserInfo | null
  showLoginModal: boolean
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    token: null,
    user: null,
    showLoginModal: false
  }),
  // P0-5 修复：Token 改存 sessionStorage（关闭标签即失效），降低 XSS 持久接管面；
  // 长期应迁移 HttpOnly Cookie，此为过渡期最小加固
  persist: {
    paths: ['token', 'user'],
    storage: sessionStorage
  },
  getters: {
    isLoggedIn: (state) => !!state.token,
    displayName: (state): string => state.user?.username || ''
  },
  actions: {
    setToken(token: string) {
      this.token = token
    },
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
     * 账号密码登录（账号不存在时自动注册）
     */
    async login(form: { username: string; password: string }): Promise<boolean> {
      try {
        const token = await apiLogin(form)
        this.token = token
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
        const token = await apiEmailCodeLogin(form)
        this.token = token
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
        const token = await apiSmsCodeLogin(form)
        this.token = token
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
     * OAuth 弹窗登录成功后设置 token
     */
    async oauthLogin(token: string): Promise<boolean> {
      try {
        this.token = token
        await this.refreshUser()
        return true
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
      // 先通知后端撤销 Token，再清理本地状态，避免会话在有效期内仍可用
      try {
        await apiLogout()
      } catch {
        // 后端撤销失败不影响本地登出
      }
      this.token = null
      this.user = null
      router.push('/home')
    }
  }
})

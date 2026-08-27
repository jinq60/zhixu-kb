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
  fetchAppConfig,
  fetchDesktopToken,
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
  /** 应用形态：desktop=桌面版 exe / server=在线服务 */
  appMode: 'desktop' | 'server'
  /** 桌面版是否已完成官网设备验证 */
  appActivated: boolean
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    token: null,
    user: null,
    showLoginModal: false,
    appMode: 'server',
    appActivated: true
  }),
  persist: {
    paths: ['token', 'user']
  },
  getters: {
    isLoggedIn: (state) => !!state.token,
    displayName: (state): string => state.user?.username || ''
  },
  actions: {
    setToken(token: string) {
      this.token = token
    },
    /**
     * 应用启动时调用：探测运行形态；桌面版自动建立本地单用户会话（免登录）。
     * 服务器版无任何副作用。
     */
    async initDesktopSession(): Promise<void> {
      // 桌面版已有会话则跳过；否则（含首次激活完成后）探测并建立会话
      if (this.appMode === 'desktop' && this.token) return
      try {
        const config = await fetchAppConfig()
        this.appMode = config.mode === 'desktop' ? 'desktop' : 'server'
        this.appActivated = config.activated !== false
        if (this.appMode === 'desktop' && this.appActivated && !this.token) {
          const { token } = await fetchDesktopToken()
          this.token = token
          await this.refreshUser()
        }
      } catch {
        // 探测失败按服务器版处理（不影响在线服务）
        this.appMode = 'server'
      }
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

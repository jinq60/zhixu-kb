import { defineStore } from 'pinia'
import router from '../router'
import http from '../api/client'

export interface AdminUser {
  id: number
  username: string
  roles: string[]
}

interface AuthState {
  username: string | null
  roles: string[]
}

function loadRoles(): string[] {
  try {
    // 只缓存角色用于首屏渲染，真实鉴权每次进后台都向服务端刷新（路由守卫）
    const raw = sessionStorage.getItem('zhixu_admin_roles')
    if (!raw) return []
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed.filter((r) => typeof r === 'string') : []
  } catch {
    return []
  }
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    // Cookie 会话模式：凭证只在 HttpOnly Cookie，不再存任何 token
    username: sessionStorage.getItem('zhixu_admin_username'),
    roles: loadRoles()
  }),
  getters: {
    isLoggedIn: (state) => state.roles.length > 0,
    isAdmin: (state) => state.roles.some((r) => r.toLowerCase() === 'admin')
  },
  actions: {
    async login(username: string, password: string) {
      await http.post('/api/auth/login', { username, password })
      this.username = username
      // 读取角色（服务端权威）
      try {
        const me = await http.get('/api/auth/me')
        this.roles = me.data.data.roles || ['user']
      } catch {
        this.roles = ['user']
      }
      sessionStorage.setItem('zhixu_admin_username', this.username || '')
      sessionStorage.setItem('zhixu_admin_roles', JSON.stringify(this.roles))
    },
    /** P0-6/P1 修复：服务端角色刷新（路由守卫用），401 时返回 null */
    async refreshRoles(): Promise<string[] | null> {
      if (this.roles.length === 0 && !this.username) return null
      try {
        const me = await http.get('/api/auth/me')
        const roles = me.data.data.roles || ['user']
        this.roles = roles
        sessionStorage.setItem('zhixu_admin_roles', JSON.stringify(roles))
        return roles
      } catch {
        return null
      }
    },
    async logout() {
      // 先调后端撤销会话并清除 Cookie，再清本地
      try {
        await http.post('/api/auth/logout')
      } catch { /* ignore */ }
      this.username = null
      this.roles = []
      sessionStorage.removeItem('zhixu_admin_username')
      sessionStorage.removeItem('zhixu_admin_roles')
      router.push('/login')
    }
  }
})

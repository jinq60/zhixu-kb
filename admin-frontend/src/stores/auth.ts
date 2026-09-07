import { defineStore } from 'pinia'
import router from '../router'
import http from '../api/client'

export interface AdminUser {
  id: number
  username: string
  roles: string[]
}

interface AuthState {
  token: string | null
  username: string | null
  roles: string[]
}

function loadRoles(): string[] {
  try {
    // P0-5 修复：改存 sessionStorage，关标签即失效
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
    token: sessionStorage.getItem('zhixu_admin_token'),
    username: sessionStorage.getItem('zhixu_admin_username'),
    roles: loadRoles()
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
    isAdmin: (state) => state.roles.some((r) => r.toLowerCase() === 'admin')
  },
  actions: {
    async login(username: string, password: string) {
      const { data } = await http.post('/api/auth/login', { username, password })
      const token = data.data.token
      this.token = token
      this.username = username
      sessionStorage.setItem('zhixu_admin_token', token)
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
      if (!this.token) return null
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
      // P1 修复：先调后端撤销 Token，再清本地
      try {
        await http.post('/api/auth/logout')
      } catch { /* ignore */ }
      this.token = null
      this.username = null
      this.roles = []
      sessionStorage.removeItem('zhixu_admin_token')
      sessionStorage.removeItem('zhixu_admin_username')
      sessionStorage.removeItem('zhixu_admin_roles')
      router.push('/login')
    }
  }
})

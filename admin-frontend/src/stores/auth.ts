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
    const raw = sessionStorage.getItem('zhixu_admin_roles')
    if (!raw) return []
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
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
      const token = data?.data?.token
      if (!token) throw new Error('登录未返回 token')
      this.token = token
      this.username = username
      sessionStorage.setItem('zhixu_admin_token', token)
      // 读取角色（以服务端为准，本地仅缓存）
      try {
        const me = await http.get('/api/auth/me')
        const roles = me?.data?.data?.roles
        this.roles = Array.isArray(roles) ? roles.filter(Boolean) : ['user']
        if (this.roles.length === 0) this.roles = ['user']
      } catch {
        this.roles = ['user']
      }
      sessionStorage.setItem('zhixu_admin_username', this.username || '')
      sessionStorage.setItem('zhixu_admin_roles', JSON.stringify(this.roles))
    },
    logout() {
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

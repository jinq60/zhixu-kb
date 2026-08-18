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
    const raw = localStorage.getItem('zhixu_admin_roles')
    if (!raw) return []
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    token: localStorage.getItem('zhixu_admin_token'),
    username: localStorage.getItem('zhixu_admin_username'),
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
      localStorage.setItem('zhixu_admin_token', token)
      // 读取角色
      try {
        const me = await http.get('/api/auth/me')
        this.roles = me.data.data.roles || ['user']
      } catch {
        this.roles = ['user']
      }
      localStorage.setItem('zhixu_admin_username', this.username || '')
      localStorage.setItem('zhixu_admin_roles', JSON.stringify(this.roles))
    },
    logout() {
      this.token = null
      this.username = null
      this.roles = []
      localStorage.removeItem('zhixu_admin_token')
      localStorage.removeItem('zhixu_admin_username')
      localStorage.removeItem('zhixu_admin_roles')
      router.push('/login')
    }
  }
})

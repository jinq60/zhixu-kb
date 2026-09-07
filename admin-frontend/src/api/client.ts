import axios from 'axios'
import { useAuthStore } from '../stores/auth'

const instance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '',
  timeout: 60000,
  // Cookie 会话模式：携带 HttpOnly 会话 Cookie，不再手写 Authorization 头
  withCredentials: true
})

instance.interceptors.response.use(
  (res) => res,
  (error) => {
    if (error.response && error.response.status === 401) {
      const auth = useAuthStore()
      // 仅在仍有登录态时才登出：避免多个并行请求同时收到 401 时
      // 重复执行 logout/push('/login')（与用户端 http.ts 同一防护策略）
      if (auth.isLoggedIn) {
        auth.logout()
      }
    }
    return Promise.reject(error)
  }
)

export default instance

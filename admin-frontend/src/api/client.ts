import axios from 'axios'
import { useAuthStore } from '../stores/auth'

const instance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '',
  timeout: 60000
})

instance.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers = config.headers || {}
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

instance.interceptors.response.use(
  (res) => res,
  (error) => {
    if (error.response && error.response.status === 401) {
      const auth = useAuthStore()
      // 仅在确实持有 token 时才登出：避免多个并行请求同时收到 401 时
      // 重复执行 logout/push('/login')（与用户端 http.ts 同一防护策略）
      if (auth.token) {
        auth.logout()
      }
    }
    return Promise.reject(error)
  }
)

export default instance
